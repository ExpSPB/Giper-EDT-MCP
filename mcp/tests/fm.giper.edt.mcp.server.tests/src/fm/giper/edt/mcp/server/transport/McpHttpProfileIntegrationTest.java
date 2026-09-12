/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server.transport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import fm.giper.edt.mcp.server.McpServer;
import fm.giper.edt.mcp.server.protocol.McpConstants;
import fm.giper.edt.mcp.server.protocol.McpProtocolHandler;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;

/**
 * Live HTTP checks for path-bound sessions, fallback and invalid-path 400.
 */
public class McpHttpProfileIntegrationTest
{
    private static final String INIT = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\"," //$NON-NLS-1$
        + "\"params\":{\"protocolVersion\":\"2025-11-25\",\"capabilities\":{}," //$NON-NLS-1$
        + "\"clientInfo\":{\"name\":\"profile-it\",\"version\":\"1.0\"}}}"; //$NON-NLS-1$
    private static final String PING = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"ping\"}"; //$NON-NLS-1$

    private HttpServer http;
    private McpServer mcp;
    private int port;

    @Before
    public void setUp() throws IOException
    {
        mcp = new McpServer();
        McpProtocolHandler protocolHandler = new McpProtocolHandler();
        InterruptibleToolExecutor executor = new InterruptibleToolExecutor(mcp, protocolHandler);
        http = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        http.createContext("/mcp", new McpHttpHandler(mcp, protocolHandler, executor, false)); //$NON-NLS-1$
        http.start();
        port = http.getAddress().getPort();
    }

    @After
    public void tearDown()
    {
        if (http != null)
        {
            http.stop(0);
        }
        if (mcp != null)
        {
            mcp.getSessionRegistry().shutdown();
        }
    }

    @Test
    public void explicitProfileRequiresSessionAfterInitialize() throws Exception
    {
        Exchange init = post("/mcp/profiles/zz-it-missing-profile", INIT, null, false); //$NON-NLS-1$
        assertEquals(200, init.status);
        assertFalse(init.sessionId.isBlank());
        JsonObject result = JsonParser.parseString(init.body).getAsJsonObject().getAsJsonObject("result"); //$NON-NLS-1$
        assertEquals(McpConstants.SERVER_NAME + "/default", //$NON-NLS-1$
            result.getAsJsonObject("serverInfo").get("name").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertTrue(result.get("instructions").getAsString().contains("UNKNOWN_PROFILE")); //$NON-NLS-1$ //$NON-NLS-2$

        assertEquals(400, post("/mcp/profiles/zz-it-missing-profile", PING, null, false).status); //$NON-NLS-1$
        Exchange ping = post("/mcp/profiles/zz-it-missing-profile", PING, init.sessionId, false); //$NON-NLS-1$
        assertEquals(200, ping.status);
        assertTrue(ping.body.contains("\"result\"")); //$NON-NLS-1$
    }

    @Test
    public void sessionReplayOnAnotherPathIs404() throws Exception
    {
        String review = post("/mcp/profiles/zz-it-review", INIT, null, false).sessionId; //$NON-NLS-1$
        String dev = post("/mcp/profiles/zz-it-dev", INIT, null, false).sessionId; //$NON-NLS-1$
        assertNotEquals(review, dev);
        assertEquals(404, post("/mcp/profiles/zz-it-dev", PING, review, false).status); //$NON-NLS-1$
        assertEquals(200, post("/mcp/profiles/zz-it-dev", PING, dev, false).status); //$NON-NLS-1$
    }

    @Test
    public void deleteReleasesSessionAndUnknownIdIs404() throws Exception
    {
        String sessionId = post("/mcp/profiles/zz-it-missing-profile", INIT, null, false).sessionId; //$NON-NLS-1$
        Exchange deleted = request("DELETE", "/mcp/profiles/zz-it-missing-profile", null, sessionId, false); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals(200, deleted.status);
        assertEquals(404, post("/mcp/profiles/zz-it-missing-profile", PING, sessionId, false).status); //$NON-NLS-1$
        assertEquals(404, post("/mcp/profiles/zz-it-missing-profile", PING, "no-such-session", false).status); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void legacyMcpStaysSessionlessAndInvalidPathHasNoFallback() throws Exception
    {
        Exchange ping = post("/mcp", PING, null, false); //$NON-NLS-1$
        assertEquals(200, ping.status);
        assertTrue(ping.sessionId.isBlank());

        Exchange invalid = post("/mcp/profiles/Review", INIT, null, false); //$NON-NLS-1$
        assertEquals(400, invalid.status);
        assertEquals(0, mcp.getSessionRegistry().size());
    }

    @Test
    public void defaultProfileChangeRequiresInitializeOnSessionlessMcp() throws Exception
    {
        assertEquals(200, post("/mcp", PING, null, false).status); //$NON-NLS-1$
        mcp.setLegacySessionRequired(true);
        Exchange refused = post("/mcp", PING, null, false); //$NON-NLS-1$
        assertEquals(400, refused.status);
        assertTrue(refused.body.contains("initialize")); //$NON-NLS-1$

        Exchange init = post("/mcp", INIT, null, false); //$NON-NLS-1$
        assertEquals(200, init.status);
        assertFalse(init.sessionId.isBlank());
        assertEquals(200, post("/mcp", PING, init.sessionId, false).status); //$NON-NLS-1$
    }

    @Test
    public void failedInitializeDoesNotMintASession() throws Exception
    {
        Exchange failed = post("/mcp/profiles/zz-it-missing-profile", //$NON-NLS-1$
            "{\"jsonrpc\":\"1.0\",\"id\":1,\"method\":\"initialize\"," //$NON-NLS-1$
            + "\"params\":{\"protocolVersion\":\"2025-11-25\",\"capabilities\":{}}}", //$NON-NLS-1$
            null, false);
        assertEquals(200, failed.status);
        assertTrue(failed.body.contains("\"error\"")); //$NON-NLS-1$
        assertTrue(failed.sessionId.isBlank());
        assertEquals(0, mcp.getSessionRegistry().size());
    }

    @Test
    public void sseInitializeKeepsProfileAndSessionHeader() throws Exception
    {
        Exchange init = post("/mcp/profiles/zz-it-missing-profile", INIT, null, true); //$NON-NLS-1$
        assertEquals(200, init.status);
        assertFalse(init.sessionId.isBlank());
        assertTrue(init.contentType.contains("text/event-stream")); //$NON-NLS-1$
        assertTrue(init.body.startsWith("event: message\n")); //$NON-NLS-1$
        assertTrue(init.body.contains("\"serverInfo\"")); //$NON-NLS-1$
        assertTrue(init.body.contains("UNKNOWN_PROFILE")); //$NON-NLS-1$
    }

    private Exchange post(String path, String body, String sessionId, boolean sse) throws IOException
    {
        return request("POST", path, body, sessionId, sse); //$NON-NLS-1$
    }

    private Exchange request(String method, String path, String body, String sessionId, boolean sse)
        throws IOException
    {
        HttpURLConnection connection = (HttpURLConnection) new URL(
            "http://127.0.0.1:" + port + path).openConnection(); //$NON-NLS-1$
        connection.setConnectTimeout(2000);
        connection.setReadTimeout(4000);
        connection.setRequestMethod(method);
        connection.setRequestProperty("Content-Type", "application/json"); //$NON-NLS-1$ //$NON-NLS-2$
        if (sse)
        {
            connection.setRequestProperty("Accept", "text/event-stream"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        if (sessionId != null)
        {
            connection.setRequestProperty(McpConstants.HEADER_SESSION_ID, sessionId);
        }
        if (body != null)
        {
            connection.setDoOutput(true);
            try (OutputStream os = connection.getOutputStream())
            {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }
        int status = connection.getResponseCode();
        InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        String responseBody = ""; //$NON-NLS-1$
        if (stream != null)
        {
            responseBody = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        String minted = connection.getHeaderField(McpConstants.HEADER_SESSION_ID);
        String contentType = connection.getHeaderField("Content-Type"); //$NON-NLS-1$
        connection.disconnect();
        return new Exchange(status, responseBody, minted == null ? "" : minted, //$NON-NLS-1$
            contentType == null ? "" : contentType); //$NON-NLS-1$
    }

    private static final class Exchange
    {
        final int status;
        final String body;
        final String sessionId;
        final String contentType;

        Exchange(int status, String body, String sessionId, String contentType)
        {
            this.status = status;
            this.body = body;
            this.sessionId = sessionId;
            this.contentType = contentType;
        }
    }
}
