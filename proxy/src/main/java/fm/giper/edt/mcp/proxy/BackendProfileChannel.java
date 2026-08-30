/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.proxy;

import java.io.IOException;
import java.io.InputStream;
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
            newPostRequest(buildJsonRpcRequest("initialize", params), null, HANDSHAKE_TIMEOUT_SECONDS); //$NON-NLS-1$
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
        JsonObject params = new JsonObject();
        params.addProperty("name", toolName); //$NON-NLS-1$
        params.add("arguments", arguments == null ? new JsonObject() : arguments); //$NON-NLS-1$

        int effectiveTimeoutSeconds = timeoutSecondsOverride > 0 ? timeoutSecondsOverride : timeoutSeconds;
        HttpResponse<InputStream> response =
            sendWithStaleSessionRetry(buildJsonRpcRequest("tools/call", params), null, effectiveTimeoutSeconds); //$NON-NLS-1$
        String body;
        try (InputStream in = response.body())
        {
            body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        return Backend.stripSseFraming(body);
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
        String statusRaw = callToolBlocking("get_server_status", includeProfilesArgs(), 10); //$NON-NLS-1$
        String toolsRaw = callToolBlockingList();
        ChannelResolution parsed = parseResolution(endpoint, statusRaw, toolsRaw);
        this.resolution = parsed;
        return parsed;
    }

    private String callToolBlockingList() throws IOException, InterruptedException
    {
        HttpResponse<InputStream> response =
            sendWithStaleSessionRetry(buildJsonRpcRequest("tools/list", new JsonObject()), null, 10); //$NON-NLS-1$
        try (InputStream in = response.body())
        {
            return Backend.stripSseFraming(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private HttpResponse<InputStream> sendWithStaleSessionRetry(String rawBody, String acceptHeader,
        int requestTimeoutSeconds) throws IOException, InterruptedException
    {
        String accept = acceptHeader == null || acceptHeader.isBlank() ? ACCEPT_JSON_AND_SSE : acceptHeader;
        String session = ensureSession();
        HttpResponse<InputStream> response = client.send(
            newPostRequest(rawBody, session, requestTimeoutSeconds, accept), HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() == 404)
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
        JsonObject status = structuredOrResult(statusRaw);
        String requested = endpoint.getRequestedProfileId();
        String effective = requested;
        String fallback = null;
        JsonArray available = new JsonArray();
        if (status != null)
        {
            String req = Json.str(status, "requestedProfileId"); //$NON-NLS-1$
            if (req != null && !req.isBlank())
            {
                requested = req;
            }
            JsonObject active = Json.obj(status, "activeProfile"); //$NON-NLS-1$
            String activeId = active == null ? Json.str(status, "effectiveProfileId") : Json.str(active, "id"); //$NON-NLS-1$ //$NON-NLS-2$
            if (activeId != null && !activeId.isBlank())
            {
                effective = activeId;
            }
            JsonElement applied = status.get("fallbackApplied"); //$NON-NLS-1$
            if (applied != null && applied.isJsonPrimitive() && applied.getAsBoolean())
            {
                fallback = Json.str(status, "fallbackReason"); //$NON-NLS-1$
                if (fallback == null || fallback.isBlank())
                {
                    fallback = "UNKNOWN_PROFILE"; //$NON-NLS-1$
                }
            }
            else
            {
                fallback = Json.str(status, "fallbackReason"); //$NON-NLS-1$
            }
            JsonElement profiles = status.get("availableProfiles"); //$NON-NLS-1$
            if (profiles != null && profiles.isJsonArray())
            {
                available = profiles.getAsJsonArray();
            }
        }
        else
        {
            effective = endpoint.getRequestedProfileId();
        }
        return new ChannelResolution(requested, effective, fallback, fingerprintToolsList(toolsListRaw), available);
    }

    static String fingerprintToolsList(String toolsListRaw)
    {
        JsonObject envelope = Json.parseObject(toolsListRaw);
        JsonObject result = envelope == null ? null : Json.obj(envelope, "result"); //$NON-NLS-1$
        JsonElement toolsElement = result == null ? null : result.get("tools"); //$NON-NLS-1$
        JsonArray tools = toolsElement != null && toolsElement.isJsonArray() ? toolsElement.getAsJsonArray()
            : new JsonArray();
        List<JsonObject> descriptors = new ArrayList<>();
        for (JsonElement element : tools)
        {
            if (element.isJsonObject())
            {
                descriptors.add(element.getAsJsonObject().deepCopy());
            }
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

    private static JsonObject includeProfilesArgs()
    {
        JsonObject arguments = new JsonObject();
        arguments.addProperty("includeProfiles", true); //$NON-NLS-1$
        return arguments;
    }

    private static JsonObject structuredOrResult(String raw)
    {
        JsonObject envelope = Json.parseObject(Backend.stripSseFraming(raw));
        JsonObject result = Json.obj(envelope, "result"); //$NON-NLS-1$
        if (result == null)
        {
            return null;
        }
        JsonObject structured = Json.obj(result, "structuredContent"); //$NON-NLS-1$
        return structured != null ? structured : result;
    }

    private String buildJsonRpcRequest(String method, JsonObject params)
    {
        JsonObject request = new JsonObject();
        request.addProperty("jsonrpc", "2.0"); //$NON-NLS-1$ //$NON-NLS-2$
        request.addProperty("id", requestId.incrementAndGet()); //$NON-NLS-1$
        request.addProperty("method", method); //$NON-NLS-1$
        request.add("params", params); //$NON-NLS-1$
        return Json.compact(request);
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
