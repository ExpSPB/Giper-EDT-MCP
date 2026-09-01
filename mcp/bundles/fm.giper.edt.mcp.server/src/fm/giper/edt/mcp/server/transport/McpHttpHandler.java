/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server.transport;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;

import fm.giper.edt.mcp.server.Activator;
import fm.giper.edt.mcp.server.McpServer;
import fm.giper.edt.mcp.server.SseStreamRegistry;
import fm.giper.edt.mcp.server.profiles.DefaultToolProfileFactory;
import fm.giper.edt.mcp.server.profiles.ProfileResolver;
import fm.giper.edt.mcp.server.profiles.ToolProfileRepository;
import fm.giper.edt.mcp.server.protocol.ClientCapabilities;
import fm.giper.edt.mcp.server.protocol.GsonProvider;
import fm.giper.edt.mcp.server.protocol.JsonUtils;
import fm.giper.edt.mcp.server.protocol.McpConstants;
import fm.giper.edt.mcp.server.protocol.McpProtocolHandler;
import fm.giper.edt.mcp.server.protocol.McpRequestContext;
import fm.giper.edt.mcp.server.protocol.jsonrpc.JsonRpcRequest;
import fm.giper.edt.mcp.server.tools.impl.GetEdtVersionTool;
import fm.giper.edt.mcp.server.transport.McpSessionRegistry.Lookup;
import fm.giper.edt.mcp.server.transport.McpSessionRegistry.LookupStatus;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * MCP request handler. Implements the Streamable HTTP transport per the
 * MCP 2025-11-25 specification: admission control, CORS/Origin admission,
 * POST/OPTIONS/DELETE dispatch, SSE GET streams (offloaded to a dedicated pool),
 * and SSE-vs-plain-JSON response framing.
 *
 * <p>Extracted from {@code McpServer}: the socket lifecycle and execution state
 * stay on {@link McpServer}; this class is the per-request transport logic. It
 * reads the live executors and request/tool-call state through the {@code server}
 * reference, delegates {@code tools/call} to {@link InterruptibleToolExecutor},
 * and everything else to {@link McpProtocolHandler}.
 */
public class McpHttpHandler implements HttpHandler
{
    private static final String CONTENT_TYPE = "Content-Type"; //$NON-NLS-1$
    private static final String TEXT_EVENT_STREAM = "text/event-stream"; //$NON-NLS-1$
    private static final String CONNECTION = "Connection"; //$NON-NLS-1$
    private static final String KEEP_ALIVE = "keep-alive"; //$NON-NLS-1$

    /** Event ID counter for SSE - AtomicLong for thread safety across concurrent SSE streams */
    private final AtomicLong eventIdCounter = new AtomicLong(0);

    private final McpServer server;
    private final McpProtocolHandler protocolHandler;
    private final InterruptibleToolExecutor interruptibleExecutor;

    public McpHttpHandler(McpServer server, McpProtocolHandler protocolHandler,
        InterruptibleToolExecutor interruptibleExecutor)
    {
        this.server = server;
        this.protocolHandler = protocolHandler;
        this.interruptibleExecutor = interruptibleExecutor;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException // NOSONAR reflective/form or transport god-method; further extraction deferred (reflective code)
    {
        // SSE GET streams are offloaded to a dedicated pool so they never
        // occupy threads in the main request pool or block the dispatcher.
        String method = exchange.getRequestMethod();

        // Optional shared-token auth — applies to every method, including SSE GET.
        // No-op when PREF_AUTH_TOKEN is empty (default), preserving prior behavior.
        if (!HttpTransport.isAuthorized(exchange))
        {
            try
            {
                HttpTransport.sendResponse(exchange, 401, JsonUtils.buildJsonRpcError(
                    McpConstants.ERROR_INVALID_REQUEST, "Unauthorized", null)); //$NON-NLS-1$
            }
            catch (IOException ignored)
            {
                // client already gone
            }
            finally
            {
                exchange.close();
            }
            return;
        }

        McpRequestContext context;
        try
        {
            context = contextOf(exchange);
        }
        catch (InvalidMcpEndpointException e)
        {
            HttpTransport.sendResponse(exchange, 400, JsonUtils.buildSimpleError(e.getMessage()));
            exchange.close();
            return;
        }
        Activator.logDebug("MCP endpoint " + context.getRequestedPath()); //$NON-NLS-1$

        if ("GET".equals(method)) //$NON-NLS-1$
        {
            if (!HttpTransport.addCorsHeaders(exchange))
            {
                HttpTransport.sendResponse(exchange, 403, JsonUtils.buildJsonRpcError(
                    McpConstants.ERROR_INVALID_REQUEST, "Invalid Origin", null)); //$NON-NLS-1$
                exchange.close();
                return;
            }
            SseStreamRegistry registry = SseStreamRegistry.getInstance();
            SseStreamRegistry.RequestedPathAdmission admission = registry
                .admitRequestedPath(context.getRequestedPath());
            McpRequestContext bound = bindSession(exchange, context);
            if (bound == null)
            {
                registry.releaseAdmission(admission);
                exchange.close();
                return;
            }
            handleSseInDedicatedPool(exchange, bound, admission);
            return;
        }

        try
        {
            // Admission control: shed load before doing heavy work.
            // Unbounded queue prevents connection resets (no executor rejection),
            // and this check returns fast 503 to drain the queue under pressure.
            ThreadPoolExecutor mainExecutor = server.getMainExecutor();
            if (mainExecutor != null)
            {
                int queued = mainExecutor.getQueue().size();
                int active = mainExecutor.getActiveCount();
                if (queued + active > 50)
                {
                    Activator.logInfo("Main pool overloaded (active=" + active //$NON-NLS-1$
                        + ", queued=" + queued + "), returning 503"); //$NON-NLS-1$
                    exchange.getResponseHeaders().add("Retry-After", "2"); //$NON-NLS-1$ //$NON-NLS-2$
                    HttpTransport.sendResponse(exchange, 503,
                        JsonUtils.buildSimpleError("Server overloaded, retry later")); //$NON-NLS-1$
                    return;
                }
            }

            // Validate Origin and add CORS headers
            if (!HttpTransport.addCorsHeaders(exchange))
            {
                String origin = exchange.getRequestHeaders().getFirst("Origin"); //$NON-NLS-1$
                Activator.logInfo("Invalid Origin header rejected: " + origin); //$NON-NLS-1$
                HttpTransport.sendResponse(exchange, 403, JsonUtils.buildJsonRpcError(
                    McpConstants.ERROR_INVALID_REQUEST, "Invalid Origin", null)); //$NON-NLS-1$
                return;
            }

            // Handle CORS preflight request
            if ("OPTIONS".equals(method)) //$NON-NLS-1$
            {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if ("POST".equals(method)) //$NON-NLS-1$
            {
                handleMcpRequest(exchange, context);
            }
            else if ("DELETE".equals(method)) //$NON-NLS-1$
            {
                handleSessionDelete(exchange, context);
            }
            else
            {
                HttpTransport.sendResponse(exchange, 405, JsonUtils.buildSimpleError("Method not allowed")); //$NON-NLS-1$
            }
        }
        catch (IOException e)
        {
            // Client disconnected unexpectedly - log and clean up
            Activator.logInfo("Client connection lost: " + e.getMessage()); //$NON-NLS-1$
        }
        catch (Exception e)
        {
            Activator.logError("Unexpected error handling MCP request", e); //$NON-NLS-1$
            try
            {
                HttpTransport.sendResponse(exchange, 500, JsonUtils.buildJsonRpcError(
                    McpConstants.ERROR_INTERNAL, "Internal server error", null)); //$NON-NLS-1$
            }
            catch (IOException ioe)
            {
                // Client already disconnected, nothing to do
                Activator.logInfo("Failed to send error response, client disconnected"); //$NON-NLS-1$
            }
        }
        finally
        {
            try
            {
                exchange.close();
            }
            catch (Exception ignored)
            {
                // Already closed
            }
        }
    }

    /**
     * Offloads SSE GET handling to the dedicated SSE thread pool.
     * The exchange lifecycle (including close) is managed entirely by the SSE thread,
     * so the main pool thread is released immediately.
     */
    private void handleSseInDedicatedPool(HttpExchange exchange, McpRequestContext context,
        SseStreamRegistry.RequestedPathAdmission admission)
    {
        ExecutorService sse = server.getSseExecutor();
        if (sse == null || sse.isShutdown())
        {
            SseStreamRegistry.getInstance().releaseAdmission(admission);
            try
            {
                HttpTransport.sendResponse(exchange, 503,
                    JsonUtils.buildSimpleError("Server is shutting down")); //$NON-NLS-1$
            }
            catch (IOException e)
            {
                // ignore
            }
            finally
            {
                exchange.close();
            }
            return;
        }

        try
        {
            sse.submit(() -> {
                try
                {
                    handleSseStream(exchange, context, admission);
                }
                catch (IOException e)
                {
                    Activator.logInfo("SSE client connection lost: " + e.getMessage()); //$NON-NLS-1$
                }
                catch (Exception e)
                {
                    Activator.logError("Unexpected error in SSE stream", e); //$NON-NLS-1$
                }
                finally
                {
                    SseStreamRegistry.getInstance().releaseAdmission(admission);
                    try
                    {
                        exchange.close();
                    }
                    catch (Exception ignored)
                    {
                        // Already closed
                    }
                }
            });
        }
        catch (RejectedExecutionException e)
        {
            SseStreamRegistry.getInstance().releaseAdmission(admission);
            // SSE pool shutting down
            try
            {
                HttpTransport.sendResponse(exchange, 503,
                    JsonUtils.buildSimpleError("Server overloaded")); //$NON-NLS-1$
            }
            catch (IOException ioe)
            {
                // ignore
            }
            finally
            {
                exchange.close();
            }
        }
    }

    private void handleMcpRequest(HttpExchange exchange, McpRequestContext context) throws IOException
    {
        // The request counter is incremented by McpProtocolHandler, not here: calls also
        // arrive through the in-process bridge, and counting at the transport left the
        // status bar frozen while those ran.

        Activator.logInfo("MCP request received from " + exchange.getRemoteAddress()); //$NON-NLS-1$

        // Read request body
        String requestBody;
        try
        {
            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8)))
            {
                String line;
                while ((line = reader.readLine()) != null)
                {
                    body.append(line);
                }
            }
            requestBody = body.toString();
        }
        catch (IOException e)
        {
            Activator.logInfo("Connection lost while reading request body: " + e.getMessage()); //$NON-NLS-1$
            return;
        }

        Activator.logDebug("MCP request body: " + requestBody); //$NON-NLS-1$

        JsonRpcRequest parsed = protocolHandler.parse(requestBody);
        boolean isInitialize = parsed != null && McpConstants.METHOD_INITIALIZE.equals(parsed.getMethod());
        boolean isToolCall = parsed != null && McpConstants.METHOD_TOOLS_CALL.equals(parsed.getMethod());
        if (parsed != null && !isInitialize)
        {
            context = bindSession(exchange, context);
            if (context == null)
            {
                return;
            }
        }

        String response;
        try
        {
            if (isToolCall)
            {
                // Handle tool calls with interruptible execution
                response = interruptibleExecutor.execute(exchange, requestBody, context);
                if (response == null)
                {
                    // Response was already sent (user interrupted)
                    return;
                }
            }
            else
            {
                response = protocolHandler.processRequest(requestBody, context);
            }

            // null response means notification (no response needed)
            if (response == null)
            {
                Activator.logInfo("MCP notification processed, returning 202"); //$NON-NLS-1$
                exchange.sendResponseHeaders(202, -1);
                return;
            }

            Activator.logDebug("MCP response: " + response.substring(0, Math.min(200, response.length())) + "..."); //$NON-NLS-1$ //$NON-NLS-2$
        }
        catch (Exception e)
        {
            Activator.logError("MCP request processing error", e); //$NON-NLS-1$
            response = JsonUtils.buildJsonRpcError(
                McpConstants.ERROR_INTERNAL, e.getMessage(), null);
        }

        String sessionId = mintSessionAfterInitialize(exchange, context, parsed, response, isInitialize);
        if (sessionId == null && isInitialize && isJsonRpcSuccess(response))
        {
            return;
        }

        // Check if client accepts SSE
        String acceptHeader = exchange.getRequestHeaders().getFirst("Accept"); //$NON-NLS-1$
        boolean acceptsSse = acceptHeader != null && acceptHeader.contains(TEXT_EVENT_STREAM);

        if (acceptsSse)
        {
            sendSseResponse(exchange, response, sessionId);
        }
        else
        {
            if (sessionId != null)
            {
                exchange.getResponseHeaders().add(McpConstants.HEADER_SESSION_ID, sessionId);
            }
            exchange.getResponseHeaders().add(CONTENT_TYPE, "application/json"); //$NON-NLS-1$
            exchange.getResponseHeaders().add(CONNECTION, KEEP_ALIVE);
            HttpTransport.sendResponse(exchange, 200, response);
        }
    }

    /**
     * Sends response as SSE event stream.
     * As per MCP 2025-11-25: should include event ID for reconnection.
     */
    private void sendSseResponse(HttpExchange exchange, String response, String sessionId) throws IOException
    {
        exchange.getResponseHeaders().add(CONTENT_TYPE, TEXT_EVENT_STREAM);
        exchange.getResponseHeaders().add("Cache-Control", "no-cache"); //$NON-NLS-1$ //$NON-NLS-2$
        exchange.getResponseHeaders().add(CONNECTION, KEEP_ALIVE);

        if (sessionId != null)
        {
            exchange.getResponseHeaders().add(McpConstants.HEADER_SESSION_ID, sessionId);
        }

        // Build SSE message with event ID (per 2025-11-25 spec)
        long eventId = eventIdCounter.incrementAndGet();
        StringBuilder sseMessage = new StringBuilder();
        sseMessage.append("event: message\n"); //$NON-NLS-1$
        sseMessage.append("id: ").append(eventId).append("\n"); //$NON-NLS-1$ //$NON-NLS-2$
        sseMessage.append("data: ").append(response).append("\n\n"); //$NON-NLS-1$ //$NON-NLS-2$

        byte[] bytes = sseMessage.toString().getBytes(StandardCharsets.UTF_8);

        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody())
        {
            os.write(bytes);
            os.flush();
        }
    }

    /**
     * Handles GET request for SSE stream.
     * As per MCP Streamable HTTP spec: supports SSE GET for clients like LM Studio
     * that require an established SSE stream before sending POST requests.
     * The server keeps the connection alive with periodic heartbeats.
     */
    private void handleSseStream(HttpExchange exchange, McpRequestContext context,
        SseStreamRegistry.RequestedPathAdmission admission) throws IOException
    {
        String acceptHeader = exchange.getRequestHeaders().getFirst("Accept"); //$NON-NLS-1$

        if (acceptHeader != null && acceptHeader.contains(TEXT_EVENT_STREAM))
        {
            Activator.logInfo("SSE GET request received - opening SSE stream"); //$NON-NLS-1$

            exchange.getResponseHeaders().add(CONTENT_TYPE, TEXT_EVENT_STREAM);
            exchange.getResponseHeaders().add("Cache-Control", "no-cache"); //$NON-NLS-1$ //$NON-NLS-2$
            exchange.getResponseHeaders().add(CONNECTION, KEEP_ALIVE);
            exchange.sendResponseHeaders(200, 0);

            // Register the stream so the server can PUSH notifications to it (e.g.
            // notifications/tools/list_changed), then keep it alive with heartbeat
            // comments. Both heartbeat and broadcast writes go through the registered
            // SseStream, which serializes them so frames never interleave.
            java.io.OutputStream os = exchange.getResponseBody();
            SseStreamRegistry registry = SseStreamRegistry.getInstance();
            SseStreamRegistry.SseStream stream = registry.registerIfCurrent(os,
                context != null ? context.getSessionId() : null,
                    context != null ? context.getRequestedPath() : McpEndpoint.LEGACY_PATH,
                    context != null ? context.getProtocolVersion() : null, admission);
            if (stream == null)
            {
                return;
            }
            String sessionId = context != null ? context.getSessionId() : null;
            if (sessionId != null && server.getSessionRegistry()
                .lookup(sessionId, context.getRequestedPath(), true).getStatus() != LookupStatus.OK)
            {
                registry.unregister(stream);
                stream.closeQuietly();
                return;
            }
            try
            {
                while (!Thread.currentThread().isInterrupted()) // NOSONAR intentional multiple loop exits; restructuring with flags would reduce readability
                {
                    try
                    {
                        stream.write(": keep-alive\n\n"); //$NON-NLS-1$
                        Thread.sleep(5000);
                    }
                    catch (InterruptedException e)
                    {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    catch (IOException e)
                    {
                        // Client disconnected
                        break;
                    }
                }
            }
            finally
            {
                registry.unregister(stream);
                try
                {
                    os.close();
                }
                catch (IOException ignore)
                {
                    // already closing
                }
            }
            Activator.logInfo("SSE stream closed"); //$NON-NLS-1$
        }
        else
        {
            // Return server info for plain GET requests
            String response = JsonUtils.buildServerInfo(
                McpConstants.SERVER_NAME,
                McpConstants.PLUGIN_VERSION,
                GetEdtVersionTool.getEdtVersion(),
                McpConstants.PROTOCOL_VERSION);
            exchange.getResponseHeaders().add(CONTENT_TYPE, "application/json"); //$NON-NLS-1$
            HttpTransport.sendResponse(exchange, 200, response);
        }
    }

    private void handleSessionDelete(HttpExchange exchange, McpRequestContext context) throws IOException
    {
        McpRequestContext bound = bindSession(exchange, context);
        if (bound == null)
        {
            return;
        }
        String sessionId = bound.getSessionId();
        if (sessionId != null)
        {
            server.getSessionRegistry().close(sessionId);
        }
        HttpTransport.sendResponse(exchange, 200, ""); //$NON-NLS-1$
    }

    /**
     * Binds a path-checked session to the request. Returns {@code null} when a
     * 400/404 has already been written. Legacy {@code /mcp} may stay sessionless.
     */
    private McpRequestContext bindSession(HttpExchange exchange, McpRequestContext context)
    {
        boolean legacyPath = isLegacyDefaultPath(context.getRequestedPath());
        boolean required = !legacyPath || server.isLegacySessionRequired();
        String sessionId = exchange.getRequestHeaders().getFirst(McpConstants.HEADER_SESSION_ID);
        Lookup lookup = server.getSessionRegistry().lookup(sessionId, context.getRequestedPath(), required);
        if (lookup.getStatus() == LookupStatus.MISSING)
        {
            sendSessionError(exchange, 400, server.isLegacySessionRequired() && legacyPath
                ? "Session required after the default profile changed; call initialize again." //$NON-NLS-1$
                : "Missing MCP-Session-Id"); //$NON-NLS-1$
            return null;
        }
        if (lookup.getStatus() == LookupStatus.UNKNOWN || lookup.getStatus() == LookupStatus.PATH_MISMATCH)
        {
            sendSessionError(exchange, 404, "Session not found"); //$NON-NLS-1$
            return null;
        }
        McpTransportSession session = lookup.getSession();
        if (session == null)
        {
            return context;
        }
        return context
            .withClientCapabilities(session.getCapabilities())
            .withProtocolVersion(session.getProtocolVersion())
            .withSessionId(session.getId());
    }

    /**
     * Creates a path-bound session only after a successful initialize.
     * Returns {@code null} when the cap is reached (503 already written) or
     * when initialize did not succeed.
     */
    private String mintSessionAfterInitialize(HttpExchange exchange, McpRequestContext context,
        JsonRpcRequest request, String response, boolean initialize) throws IOException
    {
        if (!initialize || !isJsonRpcSuccess(response))
        {
            return null;
        }
        String protocolVersion = negotiatedProtocolVersion(request);
        ClientCapabilities capabilities = capabilitiesOf(request);
        McpTransportSession session = server.getSessionRegistry().create(
            context.getRequestedPath(), protocolVersion, capabilities);
        if (session == null)
        {
            HttpTransport.sendResponse(exchange, 503,
                JsonUtils.buildSimpleError("Session limit reached")); //$NON-NLS-1$
            return null;
        }
        return session.getId();
    }

    private static void sendSessionError(HttpExchange exchange, int status, String message)
    {
        try
        {
            HttpTransport.sendResponse(exchange, status, JsonUtils.buildSimpleError(message));
        }
        catch (IOException e)
        {
            Activator.logInfo("Failed to send session error: " + e.getMessage()); //$NON-NLS-1$
        }
    }

    private static boolean isLegacyDefaultPath(String path)
    {
        return "/mcp".equals(path); //$NON-NLS-1$
    }

    static boolean isJsonRpcSuccess(String response)
    {
        if (response == null || response.isBlank())
        {
            return false;
        }
        try
        {
            JsonObject json = JsonParser.parseString(response).getAsJsonObject();
            return json.has("result") && !json.has("error"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        catch (RuntimeException e)
        {
            return false;
        }
    }

    static String negotiatedProtocolVersion(JsonRpcRequest request)
    {
        String clientVersion = request != null ? request.getStringParam("protocolVersion") : null; //$NON-NLS-1$
        return McpConstants.isSupportedVersion(clientVersion) ? clientVersion : McpConstants.PROTOCOL_VERSION;
    }

    static ClientCapabilities capabilitiesOf(JsonRpcRequest request)
    {
        if (request == null || request.getParams() == null)
        {
            return ClientCapabilities.ABSENT;
        }
        Object capabilities = request.getParams().get("capabilities"); //$NON-NLS-1$
        if (capabilities == null)
        {
            return ClientCapabilities.ABSENT;
        }
        return ClientCapabilities.from(GsonProvider.get().toJsonTree(capabilities));
    }

    static McpRequestContext contextOf(HttpExchange exchange) throws InvalidMcpEndpointException
    {
        McpEndpoint endpoint = McpEndpointResolver.resolve(exchange.getRequestURI().getRawPath());
        Activator activator = Activator.getDefault();
        ToolProfileRepository repository = activator != null ? activator.getToolProfileRepository() : null;
        return McpRequestContext.builder()
            .resolution(ProfileResolver.resolve(endpoint.getRequestedProfileId(), endpoint.isLegacy(),
                repository != null ? repository.getSnapshot() : DefaultToolProfileFactory.createSafeSnapshot()))
            .requestedPath(endpoint.canonicalPath())
            .build();
    }
}
