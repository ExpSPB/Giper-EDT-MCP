/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.proxy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * One MCP client session against a single {@code (backend, requestedProfileId)} pair.
 * Owns URI, handshake, request ids, stale-session retry and resolution metadata.
 */
public final class BackendProfileChannel
{
    static final String PROTOCOL_VERSION = Backend.PROTOCOL_VERSION;

    private static final String HEADER_SESSION_ID = "Mcp-Session-Id"; //$NON-NLS-1$
    private static final String HEADER_PROTOCOL_VERSION = "MCP-Protocol-Version"; //$NON-NLS-1$
    private static final String CONTENT_TYPE_JSON = "application/json; charset=utf-8"; //$NON-NLS-1$
    private static final String ACCEPT_JSON_AND_SSE = "application/json, text/event-stream"; //$NON-NLS-1$
    private static final int HANDSHAKE_TIMEOUT_SECONDS = 10;

    private final Backend backend;
    private final ProfileEndpoint endpoint;
    private final HttpClient client;
    private final int timeoutSeconds;
    private final URI mcpUri;
    private final AtomicLong requestId = new AtomicLong(0);

    private boolean handshakeDone;
    private String sessionId;
    private volatile ChannelResolution resolution;
    private volatile Thread notificationThread;
    private volatile boolean stopNotifications;
    private final AtomicReference<InputStream> notificationStream = new AtomicReference<>();

    BackendProfileChannel(Backend backend, ProfileEndpoint endpoint, HttpClient client, int timeoutSeconds)
    {
        this.backend = backend;
        this.endpoint = endpoint;
        this.client = client;
        this.timeoutSeconds = timeoutSeconds;
        this.mcpUri = URI.create("http://127.0.0.1:" + backend.getPort() + endpoint.canonicalPath()); //$NON-NLS-1$
    }

    public Backend getBackend()
    {
        return backend;
    }

    public ProfileEndpoint getEndpoint()
    {
        return endpoint;
    }

    public URI getUri()
    {
        return mcpUri;
    }

    public ChannelResolution getResolution()
    {
        return resolution;
    }

    /**
     * Ensures an MCP session on this profile URL. HTTP 404 later invalidates only this channel.
     *
     * @return the backend-issued session id, or {@code null} when the backend is session-less
     */
    public synchronized String ensureSession() throws IOException, InterruptedException
    {
        if (handshakeDone)
        {
            return sessionId;
        }

        JsonObject clientInfo = new JsonObject();
        clientInfo.addProperty("name", "edt-mcp-proxy"); //$NON-NLS-1$ //$NON-NLS-2$
        clientInfo.addProperty("version", "1"); //$NON-NLS-1$ //$NON-NLS-2$
        JsonObject params = new JsonObject();
        params.addProperty("protocolVersion", PROTOCOL_VERSION); //$NON-NLS-1$
        params.add("capabilities", new JsonObject()); //$NON-NLS-1$
        params.add("clientInfo", clientInfo); //$NON-NLS-1$

        HttpRequest initialize =
            newPostRequest(buildJsonRpcRequest("initialize", params).body, null, HANDSHAKE_TIMEOUT_SECONDS); //$NON-NLS-1$
        HttpResponse<String> response = client.send(initialize, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200)
        {
            throw new IOException("initialize against backend :" + backend.getPort() + endpoint.canonicalPath() //$NON-NLS-1$
                + " failed with HTTP " + response.statusCode()); //$NON-NLS-1$
        }
        String issued = response.headers().firstValue(HEADER_SESSION_ID).orElse(null);

        String initialized = "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\",\"params\":{}}"; //$NON-NLS-1$
        client.send(newPostRequest(initialized, issued, HANDSHAKE_TIMEOUT_SECONDS),
            HttpResponse.BodyHandlers.discarding());

        sessionId = issued;
        handshakeDone = true;
        return sessionId;
    }

    public synchronized void invalidateSession()
    {
        handshakeDone = false;
        sessionId = null;
        resolution = null;
    }

    /**
     * Opens a long-lived GET/SSE against this backend path and forwards
     * {@code notifications/tools/list_changed} to {@code hub}.
     */
    public synchronized void ensureNotificationListener(SseNotificationHub hub)
    {
        if (hub == null || notificationThread != null)
        {
            return;
        }
        stopNotifications = false;
        Thread thread = new Thread(() -> listenForNotifications(hub),
            "edt-mcp-proxy-sse-" + backend.getPort() + endpoint.canonicalPath()); //$NON-NLS-1$
        thread.setDaemon(true);
        notificationThread = thread;
        thread.start();
    }

    public void stopNotificationListener()
    {
        stopNotifications = true;
        closeQuietly(notificationStream.getAndSet(null));
        Thread thread = notificationThread;
        if (thread != null)
        {
            thread.interrupt();
        }
    }

    boolean awaitNotificationListenerStopped(long timeoutMillis)
    {
        Thread thread = notificationThread;
        if (thread == null || thread == Thread.currentThread())
        {
            return thread == null;
        }
        if (timeoutMillis <= 0L)
        {
            return false;
        }
        try
        {
            thread.join(timeoutMillis);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
        return notificationThread == null;
    }

    boolean isNotificationListenerRunning()
    {
        Thread thread = notificationThread;
        return thread != null && thread.isAlive();
    }

    private void listenForNotifications(SseNotificationHub hub)
    {
        long backoffMillis = 250L;
        boolean recoveredSession = false;
        try
        {
            while (!stopNotifications && !Thread.currentThread().isInterrupted())
            {
                try
                {
                    String session = ensureSession();
                    HttpRequest.Builder builder = HttpRequest.newBuilder(mcpUri)
                        .timeout(Duration.ofHours(6))
                        .header("Accept", "text/event-stream") //$NON-NLS-1$ //$NON-NLS-2$
                        .header(HEADER_PROTOCOL_VERSION, PROTOCOL_VERSION)
                        .GET();
                    if (session != null)
                    {
                        builder.header(HEADER_SESSION_ID, session);
                    }
                    HttpResponse<InputStream> response = client.send(builder.build(),
                        HttpResponse.BodyHandlers.ofInputStream());
                    InputStream body = response.body();
                    if (response.statusCode() == 404)
                    {
                        closeQuietly(body);
                        invalidateSessionIfCurrent(session);
                        ensureSession();
                        recoveredSession = true;
                        backoffMillis = 250L;
                        continue;
                    }
                    if (response.statusCode() != 200)
                    {
                        closeQuietly(body);
                        sleepQuietly(backoffMillis);
                        backoffMillis = Math.min(backoffMillis * 2L, 4000L);
                        continue;
                    }
                    backoffMillis = 250L;
                    if (!notificationStream.compareAndSet(null, body))
                    {
                        closeQuietly(body);
                        continue;
                    }
                    try (InputStream stream = body;
                        BufferedReader reader = new BufferedReader(
                            new InputStreamReader(stream, StandardCharsets.UTF_8)))
                    {
                        if (recoveredSession)
                        {
                            refreshResolution();
                            hub.onBackendListChanged(endpoint.getRequestedProfileId(),
                                endpoint.canonicalPath());
                            recoveredSession = false;
                        }
                        String line;
                        while (!stopNotifications && (line = reader.readLine()) != null)
                        {
                            if (line.startsWith("data:") && line.contains("list_changed")) //$NON-NLS-1$ //$NON-NLS-2$
                            {
                                hub.onBackendListChanged(endpoint.getRequestedProfileId(),
                                    endpoint.canonicalPath());
                            }
                        }
                    }
                    finally
                    {
                        notificationStream.compareAndSet(body, null);
                    }
                }
                catch (InterruptedException e)
                {
                    Thread.currentThread().interrupt();
                    return;
                }
                catch (IOException | RuntimeException ignored)
                {
                    if (!stopNotifications)
                    {
                        sleepQuietly(backoffMillis);
                        backoffMillis = Math.min(backoffMillis * 2L, 4000L);
                    }
                }
            }
        }
        finally
        {
            closeQuietly(notificationStream.getAndSet(null));
            synchronized (this)
            {
                if (notificationThread == Thread.currentThread())
                {
                    notificationThread = null;
                }
            }
        }
    }

    private static void sleepQuietly(long millis)
    {
        try
        {
            Thread.sleep(millis);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
    }

    private synchronized void invalidateSessionIfCurrent(String staleSession)
    {
        if (handshakeDone && Objects.equals(sessionId, staleSession))
        {
            handshakeDone = false;
            sessionId = null;
            resolution = null;
        }
    }

    public HttpResponse<InputStream> forward(String rawBody) throws IOException, InterruptedException
    {
        return forward(rawBody, null);
    }

    public HttpResponse<InputStream> forward(String rawBody, String acceptHeader)
        throws IOException, InterruptedException
    {
        return sendWithStaleSessionRetry(rawBody, acceptHeader, timeoutSeconds);
    }

    public String callToolBlocking(String toolName, JsonObject arguments) throws IOException, InterruptedException
    {
        return callToolBlocking(toolName, arguments, -1);
    }

    public String callToolBlocking(String toolName, JsonObject arguments, int timeoutSecondsOverride)
        throws IOException, InterruptedException
    {
        return callToolBlockingResponse(toolName, arguments, timeoutSecondsOverride).body;
    }

    private JsonRpcResponse callToolBlockingResponse(String toolName, JsonObject arguments,
        int timeoutSecondsOverride) throws IOException, InterruptedException
    {
        JsonObject params = new JsonObject();
        params.addProperty("name", toolName); //$NON-NLS-1$
        params.add("arguments", arguments == null ? new JsonObject() : arguments); //$NON-NLS-1$

        int effectiveTimeoutSeconds = timeoutSecondsOverride > 0 ? timeoutSecondsOverride : timeoutSeconds;
        JsonRpcRequest request = buildJsonRpcRequest("tools/call", params); //$NON-NLS-1$
        HttpResponse<InputStream> response =
            sendWithStaleSessionRetry(request.body, null, effectiveTimeoutSeconds);
        String body;
        try (InputStream in = response.body())
        {
            // Bounded by the same cap the proxy applies everywhere else it buffers a body
            // (McpProxyHandler.MAX_BODY_BYTES): this response is held whole in memory to strip
            // its SSE framing, and it arrives on a discovery/fan-out worker where an
            // unterminated or huge body would grow unopposed.
            byte[] bytes = in.readNBytes(McpProxyHandler.MAX_BODY_BYTES + 1);
            if (bytes.length > McpProxyHandler.MAX_BODY_BYTES)
            {
                throw new IOException("Response to '" + toolName + "' from backend port " + backend.getPort() //$NON-NLS-1$ //$NON-NLS-2$
                    + " exceeds the " + McpProxyHandler.MAX_BODY_BYTES //$NON-NLS-1$
                    + "-byte limit the proxy can buffer; call that backend directly for a result " //$NON-NLS-1$
                    + "this large."); //$NON-NLS-1$
            }
            body = new String(bytes, StandardCharsets.UTF_8);
        }
        return new JsonRpcResponse(request.id, Backend.stripSseFraming(body));
    }

    /**
     * Loads {@code get_server_status} and {@code tools/list} for this channel and stores
     * {@link ChannelResolution}.
     *
     * @return the resolution (never {@code null} on success)
     */
    public ChannelResolution refreshResolution() throws IOException, InterruptedException
    {
        ensureSession();
        JsonRpcResponse status = callToolBlockingResponse("get_server_status", includeProfilesArgs(), 10); //$NON-NLS-1$
        JsonRpcResponse tools = callToolBlockingList();
        ChannelResolution parsed = parseResolution(endpoint, status.body, Long.valueOf(status.requestId),
            tools.body, Long.valueOf(tools.requestId));
        this.resolution = parsed;
        return parsed;
    }

    private JsonRpcResponse callToolBlockingList() throws IOException, InterruptedException
    {
        JsonRpcRequest request = buildJsonRpcRequest("tools/list", new JsonObject()); //$NON-NLS-1$
        HttpResponse<InputStream> response =
            sendWithStaleSessionRetry(request.body, null, 10);
        try (InputStream in = response.body())
        {
            byte[] bytes = in.readNBytes(McpProxyHandler.MAX_BODY_BYTES + 1);
            if (bytes.length > McpProxyHandler.MAX_BODY_BYTES)
            {
                throw new IOException("The tools/list response from backend port " + backend.getPort() //$NON-NLS-1$
                    + " exceeds the " + McpProxyHandler.MAX_BODY_BYTES //$NON-NLS-1$
                    + "-byte limit the proxy can buffer."); //$NON-NLS-1$
            }
            String body = Backend.stripSseFraming(new String(bytes, StandardCharsets.UTF_8));
            return new JsonRpcResponse(request.id, body);
        }
    }

    private HttpResponse<InputStream> sendWithStaleSessionRetry(String rawBody, String acceptHeader,
        int requestTimeoutSeconds) throws IOException, InterruptedException
    {
        String accept = acceptHeader == null || acceptHeader.isBlank() ? ACCEPT_JSON_AND_SSE : acceptHeader;
        String session = ensureSession();
        HttpResponse<InputStream> response = client.send(
            newPostRequest(rawBody, session, requestTimeoutSeconds, accept), HttpResponse.BodyHandlers.ofInputStream());
        // 404 means the session we presented is gone. 400 with NO session presented means the
        // same thing one step earlier: this handshake was made with a plugin that issued no
        // session id, that plugin has since been upgraded to one that requires it, and the
        // cached handshake is now unusable. A Backend outlives a rescan by design, so without
        // this the proxy would answer 400 to every routed call until someone restarted it.
        // Narrow on purpose: a 400 for a request that DID carry a session is a real bad request
        // and is returned as-is rather than retried.
        if (response.statusCode() == 404 || (response.statusCode() == 400 && session == null))
        {
            closeQuietly(response.body());
            invalidateSessionIfCurrent(session);
            session = ensureSession();
            response = client.send(newPostRequest(rawBody, session, requestTimeoutSeconds, accept),
                HttpResponse.BodyHandlers.ofInputStream());
        }
        return response;
    }

    static ChannelResolution parseResolution(ProfileEndpoint endpoint, String statusRaw, String toolsListRaw)
    {
        return parseResolution(endpoint, statusRaw, null, toolsListRaw, null);
    }

    static ChannelResolution parseResolution(ProfileEndpoint endpoint, String statusRaw, Long expectedStatusId,
        String toolsListRaw, Long expectedToolsListId)
    {
        Objects.requireNonNull(endpoint, "endpoint"); //$NON-NLS-1$
        String fingerprint = fingerprintToolsList(toolsListRaw, expectedToolsListId);
        JsonObject result = requireJsonRpcResult(statusRaw, "get_server_status", expectedStatusId); //$NON-NLS-1$
        Boolean toolError = strictBoolean(result, "isError"); //$NON-NLS-1$
        if (Boolean.TRUE.equals(toolError))
        {
            throw new IllegalArgumentException("backend tool error: get_server_status result.isError=true"); //$NON-NLS-1$
        }
        JsonElement structuredElement = result.get("structuredContent"); //$NON-NLS-1$
        JsonObject status;
        if (structuredElement == null)
        {
            status = result;
        }
        else
        {
            if (!structuredElement.isJsonObject())
            {
                throw malformed("get_server_status structuredContent must be an object"); //$NON-NLS-1$
            }
            status = structuredElement.getAsJsonObject();
        }
        Boolean success = strictBoolean(status, "success"); //$NON-NLS-1$
        if (Boolean.FALSE.equals(success))
        {
            throw new IllegalArgumentException("backend tool error: get_server_status success=false"); //$NON-NLS-1$
        }
        if (!hasAnyProfileField(status))
        {
            if (!endpoint.isLegacy())
            {
                throw malformed("legacy status is allowed only on /mcp"); //$NON-NLS-1$
            }
            return ChannelResolution.legacyDefault(fingerprint);
        }

        JsonObject active = requireObject(status, "activeProfile", "get_server_status"); //$NON-NLS-1$ //$NON-NLS-2$
        String requested = active.has("requestedProfileId") //$NON-NLS-1$
            ? strictString(active, "requestedProfileId") : strictString(status, "requestedProfileId"); //$NON-NLS-1$ //$NON-NLS-2$
        if (requested == null)
        {
            throw malformed("get_server_status requestedProfileId is missing"); //$NON-NLS-1$
        }
        if (!endpoint.getRequestedProfileId().equals(requested))
        {
            throw new IllegalArgumentException("profile mismatch: backend requestedProfileId=" + requested //$NON-NLS-1$
                + " for endpoint " + endpoint.canonicalPath()); //$NON-NLS-1$
        }
        String effective = active.has("id") ? strictString(active, "id") //$NON-NLS-1$ //$NON-NLS-2$
            : strictString(status, "effectiveProfileId"); //$NON-NLS-1$
        if (effective == null)
        {
            throw malformed("get_server_status effective profile id is missing"); //$NON-NLS-1$
        }
        Boolean applied = active.has("fallbackApplied") //$NON-NLS-1$
            ? strictBoolean(active, "fallbackApplied") : strictBoolean(status, "fallbackApplied"); //$NON-NLS-1$ //$NON-NLS-2$
        if (applied == null)
        {
            throw malformed("get_server_status fallbackApplied is missing or not boolean"); //$NON-NLS-1$
        }
        String fallback = active.has("fallbackApplied") //$NON-NLS-1$
            ? strictString(active, "fallbackReason") : strictString(status, "fallbackReason"); //$NON-NLS-1$ //$NON-NLS-2$
        if (applied.booleanValue() && fallback == null)
        {
            throw malformed("fallbackApplied=true requires fallbackReason"); //$NON-NLS-1$
        }
        if (!applied.booleanValue() && fallback != null)
        {
            throw malformed("fallbackApplied=false forbids fallbackReason"); //$NON-NLS-1$
        }
        if (applied.booleanValue() && requested.equals(effective))
        {
            throw new IllegalArgumentException("profile mismatch: fallback did not change effective profile " //$NON-NLS-1$
                + effective);
        }
        if (!applied.booleanValue() && !requested.equals(effective))
        {
            throw new IllegalArgumentException("profile mismatch: effective profile " + effective //$NON-NLS-1$
                + " differs from requested " + requested + " without fallback"); //$NON-NLS-1$
        }
        JsonArray available = new JsonArray();
        JsonElement profiles = status.get("availableProfiles"); //$NON-NLS-1$
        if (profiles != null)
        {
            if (!profiles.isJsonArray())
            {
                throw malformed("get_server_status availableProfiles must be an array"); //$NON-NLS-1$
            }
            available = profiles.getAsJsonArray();
        }
        return ChannelResolution.confirmed(requested, effective, fallback, fingerprint, available);
    }

    static String fingerprintToolsList(String toolsListRaw)
    {
        return fingerprintToolsList(toolsListRaw, null);
    }

    private static String fingerprintToolsList(String toolsListRaw, Long expectedId)
    {
        JsonObject result = requireJsonRpcResult(toolsListRaw, "tools/list", expectedId); //$NON-NLS-1$
        JsonElement toolsElement = result.get("tools"); //$NON-NLS-1$
        if (toolsElement == null || !toolsElement.isJsonArray())
        {
            throw malformed("tools/list result.tools must be an array"); //$NON-NLS-1$
        }
        JsonArray tools = toolsElement.getAsJsonArray();
        List<JsonObject> descriptors = new ArrayList<>();
        for (JsonElement element : tools)
        {
            if (!element.isJsonObject())
            {
                throw malformed("tools/list descriptor must be an object"); //$NON-NLS-1$
            }
            JsonObject descriptor = element.getAsJsonObject();
            String name = strictString(descriptor, "name"); //$NON-NLS-1$
            if (name == null || name.isBlank())
            {
                throw malformed("tools/list descriptor name is missing or blank"); //$NON-NLS-1$
            }
            descriptors.add(descriptor.deepCopy());
        }
        descriptors.sort(Comparator.comparing(o -> {
            String name = Json.str(o, "name"); //$NON-NLS-1$
            return name == null ? "" : name; //$NON-NLS-1$
        }));
        JsonArray canonical = new JsonArray();
        for (JsonObject descriptor : descriptors)
        {
            descriptor.remove("id"); //$NON-NLS-1$
            canonical.add(descriptor);
        }
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256"); //$NON-NLS-1$
            byte[] hash = digest.digest(Json.compact(canonical).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        }
        catch (NoSuchAlgorithmException e)
        {
            return Integer.toHexString(Json.compact(canonical).hashCode());
        }
    }

    private static String firstNonBlank(String first, String second)
    {
        if (first != null && !first.isBlank())
        {
            return first;
        }
        if (second != null && !second.isBlank())
        {
            return second;
        }
        return null;
    }

    private static JsonObject requireJsonRpcResult(String raw, String operation, Long expectedId)
    {
        JsonObject envelope = Json.parseObject(Backend.stripSseFraming(raw));
        if (envelope == null)
        {
            throw malformed(operation + " response is not a JSON object"); //$NON-NLS-1$
        }
        JsonElement version = envelope.get("jsonrpc"); //$NON-NLS-1$
        if (version == null || !version.isJsonPrimitive() || !version.getAsJsonPrimitive().isString()
            || !"2.0".equals(version.getAsString())) //$NON-NLS-1$
        {
            throw malformed(operation + " response has invalid JSON-RPC envelope"); //$NON-NLS-1$
        }
        JsonElement id = envelope.get("id"); //$NON-NLS-1$
        if (!isValidResponseId(id))
        {
            throw malformed(operation + " response id is missing or invalid"); //$NON-NLS-1$
        }
        if (expectedId != null && !matchesNumericRequestId(id, expectedId.longValue()))
        {
            throw malformed(operation + " response id does not match request id " + expectedId); //$NON-NLS-1$
        }
        boolean hasResult = envelope.has("result"); //$NON-NLS-1$
        boolean hasError = envelope.has("error"); //$NON-NLS-1$
        if (hasResult == hasError)
        {
            throw malformed(operation + " response must contain exactly one of result or error"); //$NON-NLS-1$
        }
        if (hasError)
        {
            requireObject(envelope, "error", operation + " response"); //$NON-NLS-1$ //$NON-NLS-2$
            throw new IllegalArgumentException("backend JSON-RPC error: " + operation); //$NON-NLS-1$
        }
        return requireObject(envelope, "result", operation + " response"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private static boolean isValidResponseId(JsonElement id)
    {
        return id != null && !id.isJsonNull() && id.isJsonPrimitive()
            && (id.getAsJsonPrimitive().isString() || id.getAsJsonPrimitive().isNumber());
    }

    private static boolean matchesNumericRequestId(JsonElement id, long expectedId)
    {
        if (!id.getAsJsonPrimitive().isNumber())
        {
            return false;
        }
        try
        {
            return new BigDecimal(id.getAsString()).compareTo(BigDecimal.valueOf(expectedId)) == 0;
        }
        catch (NumberFormatException e)
        {
            return false;
        }
    }

    private static JsonObject requireObject(JsonObject object, String key, String context)
    {
        JsonElement element = object == null ? null : object.get(key);
        if (element == null || !element.isJsonObject())
        {
            throw malformed(context + "." + key + " must be an object"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        return element.getAsJsonObject();
    }

    private static String strictString(JsonObject object, String key)
    {
        JsonElement element = object == null ? null : object.get(key);
        if (element == null)
        {
            return null;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString())
        {
            throw malformed(key + " must be a string"); //$NON-NLS-1$
        }
        String value = element.getAsString();
        return value.isBlank() ? null : value;
    }

    private static Boolean strictBoolean(JsonObject object, String key)
    {
        JsonElement element = object == null ? null : object.get(key);
        if (element == null)
        {
            return null;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean())
        {
            throw malformed(key + " must be boolean"); //$NON-NLS-1$
        }
        return Boolean.valueOf(element.getAsBoolean());
    }

    private static boolean hasAnyProfileField(JsonObject object)
    {
        return object.has("activeProfile") || object.has("requestedProfileId") //$NON-NLS-1$ //$NON-NLS-2$
            || object.has("effectiveProfileId") || object.has("fallbackApplied") //$NON-NLS-1$ //$NON-NLS-2$
            || object.has("fallbackReason") || object.has("availableProfiles"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private static IllegalArgumentException malformed(String message)
    {
        return new IllegalArgumentException("malformed backend contract: " + message); //$NON-NLS-1$
    }

    private static JsonObject includeProfilesArgs()
    {
        JsonObject arguments = new JsonObject();
        arguments.addProperty("includeProfiles", true); //$NON-NLS-1$
        return arguments;
    }

    private JsonRpcRequest buildJsonRpcRequest(String method, JsonObject params)
    {
        long id = requestId.incrementAndGet();
        JsonObject request = new JsonObject();
        request.addProperty("jsonrpc", "2.0"); //$NON-NLS-1$ //$NON-NLS-2$
        request.addProperty("id", id); //$NON-NLS-1$
        request.addProperty("method", method); //$NON-NLS-1$
        request.add("params", params); //$NON-NLS-1$
        return new JsonRpcRequest(id, Json.compact(request));
    }

    private static final class JsonRpcRequest
    {
        final long id;
        final String body;

        JsonRpcRequest(long id, String body)
        {
            this.id = id;
            this.body = body;
        }
    }

    private static final class JsonRpcResponse
    {
        final long requestId;
        final String body;

        JsonRpcResponse(long requestId, String body)
        {
            this.requestId = requestId;
            this.body = body;
        }
    }

    private HttpRequest newPostRequest(String body, String session, int requestTimeoutSeconds)
    {
        return newPostRequest(body, session, requestTimeoutSeconds, ACCEPT_JSON_AND_SSE);
    }

    private HttpRequest newPostRequest(String body, String session, int requestTimeoutSeconds, String acceptHeader)
    {
        HttpRequest.Builder builder = HttpRequest.newBuilder(mcpUri)
            .timeout(Duration.ofSeconds(requestTimeoutSeconds))
            .header("Content-Type", CONTENT_TYPE_JSON) //$NON-NLS-1$
            .header("Accept", acceptHeader) //$NON-NLS-1$
            .header(HEADER_PROTOCOL_VERSION, PROTOCOL_VERSION)
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (session != null)
        {
            builder.header(HEADER_SESSION_ID, session);
        }
        return builder.build();
    }

    private static void closeQuietly(InputStream in)
    {
        if (in == null)
        {
            return;
        }
        try
        {
            in.close();
        }
        catch (IOException ignored)
        {
            // stale-session body is discarded
        }
    }
}
