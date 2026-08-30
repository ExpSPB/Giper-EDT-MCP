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
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.JsonObject;

/**
 * One discovered EDT-MCP instance (port + health). MCP sessions live on
 * {@link BackendProfileChannel} keyed by {@code (this, requestedProfileId)}.
 * <p>
 * {@link #ensureSession()}, {@link #forward(String)} and {@link #callToolBlocking} stay on
 * this class as the <em>discovery</em> channel ({@code /mcp}) so existing callers keep working.
 */
public final class Backend
{
    /** MCP protocol version this proxy speaks to its backends. */
    static final String PROTOCOL_VERSION = "2025-11-25"; //$NON-NLS-1$

    private static final int HEALTH_TIMEOUT_SECONDS = 2;

    private final int port;
    private final HttpClient client;
    private final int timeoutSeconds;
    private final URI healthUri;
    private final ConcurrentHashMap<String, BackendProfileChannel> channels = new ConcurrentHashMap<>();

    /**
     * Creates a backend handle for an EDT-MCP instance on {@code 127.0.0.1:<port>}.
     *
     * @param port the backend's HTTP port
     * @param client the shared HTTP client to send requests through
     * @param timeoutSeconds the per-forwarded-call timeout in seconds
     */
    public Backend(int port, HttpClient client, int timeoutSeconds)
    {
        this.port = port;
        this.client = client;
        this.timeoutSeconds = timeoutSeconds;
        this.healthUri = URI.create("http://127.0.0.1:" + port + "/health"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    public int getPort()
    {
        return port;
    }

    /**
     * Channel for the client's requested profile URL (or the discovery {@code /mcp} channel).
     *
     * @param endpoint the requested path
     * @return the channel, created once and reused
     */
    public BackendProfileChannel channel(ProfileEndpoint endpoint)
    {
        ProfileEndpoint key = endpoint == null ? ProfileEndpoint.legacyDefault() : endpoint;
        return channels.computeIfAbsent(key.canonicalPath(),
            path -> new BackendProfileChannel(this, key, client, timeoutSeconds));
    }

    /** Discovery / {@code list_projects} channel: always {@code /mcp}. */
    public BackendProfileChannel discoveryChannel()
    {
        return channel(ProfileEndpoint.legacyDefault());
    }

    public boolean probeHealth()
    {
        HttpRequest request = HttpRequest.newBuilder(healthUri)
            .timeout(Duration.ofSeconds(HEALTH_TIMEOUT_SECONDS))
            .GET()
            .build();
        try
        {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200)
            {
                return false;
            }
            JsonObject body = Json.parseObject(response.body());
            return body != null && "ok".equals(Json.str(body, "status")) //$NON-NLS-1$ //$NON-NLS-2$
                && !"proxy".equals(Json.str(body, "role")); //$NON-NLS-1$ //$NON-NLS-2$
        }
        catch (IOException e)
        {
            return false;
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public String ensureSession() throws IOException, InterruptedException
    {
        return discoveryChannel().ensureSession();
    }

    public void invalidateSession()
    {
        discoveryChannel().invalidateSession();
    }

    /**
     * Starts GET/SSE listeners on the discovery channel and each advertised profile.
     */
    public void ensureNotificationListeners(SseNotificationHub hub)
    {
        try
        {
            discoveryChannel().refreshResolution();
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            return;
        }
        catch (IOException ignored)
        {
            // still attach the discovery stream; profile paths wait for the next refresh
        }
        discoveryChannel().ensureNotificationListener(hub);
        ChannelResolution resolution = discoveryChannel().getResolution();
        if (resolution == null || resolution.getAvailableProfiles() == null)
        {
            return;
        }
        for (com.google.gson.JsonElement element : resolution.getAvailableProfiles())
        {
            if (!element.isJsonObject())
            {
                continue;
            }
            String id = Json.str(element.getAsJsonObject(), "id"); //$NON-NLS-1$
            if (id == null || id.isBlank() || ProfileEndpoint.DEFAULT_PROFILE_ID.equals(id))
            {
                continue;
            }
            channel(new ProfileEndpoint(ProfileEndpoint.PROFILES_PREFIX + id, id, false))
                .ensureNotificationListener(hub);
        }
    }

    public HttpResponse<InputStream> forward(String rawBody) throws IOException, InterruptedException
    {
        return discoveryChannel().forward(rawBody);
    }

    public HttpResponse<InputStream> forward(String rawBody, String acceptHeader)
        throws IOException, InterruptedException
    {
        return discoveryChannel().forward(rawBody, acceptHeader);
    }

    public String callToolBlocking(String toolName, JsonObject arguments) throws IOException, InterruptedException
    {
        return discoveryChannel().callToolBlocking(toolName, arguments);
    }

    public String callToolBlocking(String toolName, JsonObject arguments, int timeoutSecondsOverride)
        throws IOException, InterruptedException
    {
        return discoveryChannel().callToolBlocking(toolName, arguments, timeoutSecondsOverride);
    }

    /**
     * Extracts the JSON-RPC payload from a Streamable-HTTP response body.
     *
     * @param body the raw HTTP response body
     * @return the inner JSON-RPC message, or the trimmed body when no SSE frame is present
     */
    static String stripSseFraming(String body)
    {
        String trimmed = body == null ? "" : body.trim(); //$NON-NLS-1$
        if (trimmed.startsWith("{")) //$NON-NLS-1$
        {
            return trimmed;
        }
        List<String> events = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : trimmed.split("\r?\n", -1)) //$NON-NLS-1$
        {
            if (line.startsWith("data:")) //$NON-NLS-1$
            {
                if (current.length() > 0)
                {
                    current.append('\n');
                }
                current.append(line.substring(5).stripLeading());
            }
            else if (line.isBlank() && current.length() > 0)
            {
                events.add(current.toString());
                current.setLength(0);
            }
        }
        if (current.length() > 0)
        {
            events.add(current.toString());
        }
        if (events.isEmpty())
        {
            return trimmed;
        }
        return events.get(events.size() - 1);
    }
}
