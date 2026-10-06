/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.proxy;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

/**
 * Unit tests for {@link ProxyServer#isLoopbackRequest}: the address-check helper behind
 * {@code POST /admin/shutdown}'s loopback-only guard (issue #253 follow-up, spec section 7/8).
 * A genuinely non-loopback remote socket cannot be faked in an in-process integration test (see
 * {@link AdminShutdownIT} for the loopback-accepted wire behaviour), so the {@code 403} branch
 * is proven directly on this helper instead.
 */
public class ProxyServerTest
{
    @Rule
    public Timeout timeout = Timeout.seconds(60);

    @Test
    public void testSseStreamsDoNotStarveControlRequestsAndBudgetIsBounded() throws Exception
    {
        ProxyConfig cfg = ProxyConfig.parse(new String[] { "--port", "0", "--scan", "2-1" }, Map.of());
        BackendRegistry registry = new BackendRegistry(cfg);
        McpProxyHandler handler = new McpProxyHandler(cfg, registry, new SessionManager());
        ProxyServer server = new ProxyServer(cfg, registry, handler);
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        List<InputStream> streams = new ArrayList<>();
        try
        {
            server.start();
            String session = initialize(client, server).headers().firstValue("Mcp-Session-Id").orElseThrow();
            // Keeping more than all 32 ordinary HTTP workers occupied reproduced the starvation.
            for (int i = 0; i < ProxyServer.MAX_SSE_STREAMS; i++)
            {
                streams.add(openStream(client, server, session));
            }
            assertEquals(ProxyServer.MAX_SSE_STREAMS, handler.activeSseStreamCount());
            assertEquals(200, initialize(client, server).statusCode());
            assertEquals(200, client.send(request(server, "/health").GET().build(),
                HttpResponse.BodyHandlers.ofString()).statusCode());
            assertEquals(403, client.send(request(server, "/mcp").header("Origin", "http://evil.example")
                .POST(HttpRequest.BodyPublishers.ofString(initializeBody())).build(),
                HttpResponse.BodyHandlers.ofString()).statusCode());

            HttpResponse<String> full = client.send(request(server, "/mcp")
                .header("Mcp-Session-Id", session).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(503, full.statusCode());
            assertEquals("2", full.headers().firstValue("Retry-After").orElseThrow());
            // Origin/path/session admission still runs before the stream-capacity check.
            assertEquals(400, client.send(request(server, "/mcp").GET().build(),
                HttpResponse.BodyHandlers.ofString()).statusCode());
            assertEquals(404, client.send(request(server, "/mcp/profiles/default")
                .header("Mcp-Session-Id", session).GET().build(),
                HttpResponse.BodyHandlers.ofString()).statusCode());
            assertEquals(403, client.send(request(server, "/mcp")
                .header("Mcp-Session-Id", session).header("Origin", "http://evil.example").GET().build(),
                HttpResponse.BodyHandlers.ofString()).statusCode());
        }
        finally
        {
            server.stop();
            for (InputStream stream : streams)
            {
                stream.close();
            }
            registry.shutdown();
        }
        awaitStreamsClosed(handler);
    }

    @Test
    public void testStopClosesSseExchangesAndRestartKeepsSessionsWithFreshCapacity() throws Exception
    {
        ProxyConfig cfg = ProxyConfig.parse(new String[] { "--port", "0", "--scan", "2-1" }, Map.of());
        BackendRegistry registry = new BackendRegistry(cfg);
        SessionManager sessions = new SessionManager();
        McpProxyHandler handler = new McpProxyHandler(cfg, registry, sessions);
        ProxyServer server = new ProxyServer(cfg, registry, handler);
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        List<InputStream> streams = new ArrayList<>();
        try
        {
            server.start();
            String session = initialize(client, server).headers().firstValue("Mcp-Session-Id").orElseThrow();
            for (int i = 0; i < 32; i++)
            {
                InputStream stream = openStream(client, server, session);
                streams.add(stream);
                assertEquals(": keep-alive\n\n",
                    new String(stream.readNBytes(14), java.nio.charset.StandardCharsets.UTF_8));
            }
            server.stop();
            awaitStreamsClosed(handler);
            // The server must close live response bodies; client-side closure is not the trigger.
            for (InputStream stream : streams)
            {
                try
                {
                    stream.readAllBytes();
                }
                catch (IOException closed)
                {
                    // stop(0) can truncate a chunked body instead of delivering its final chunk.
                    // A timeout/interruption is not evidence of server-side stream closure.
                    assertFalse(closed instanceof HttpTimeoutException);
                    assertFalse(closed instanceof InterruptedIOException);
                    assertFalse(Thread.currentThread().isInterrupted());
                    String message = String.valueOf(closed.getMessage()).toLowerCase(java.util.Locale.ROOT);
                    assertTrue("Expected a closed or truncated response: " + closed,
                        message.contains("closed") || message.contains("chunked")
                            || message.contains("eof") || message.contains("reset")
                            || message.contains("premature"));
                }
                stream.close();
            }
            streams.clear();
            assertTrue(sessions.lookup(session, "/mcp") != null);
            server.start();
            streams.add(openStream(client, server, session));
            assertEquals(200, initialize(client, server).statusCode());
        }
        finally
        {
            server.stop();
            for (InputStream stream : streams)
            {
                stream.close();
            }
            registry.shutdown();
        }
        awaitStreamsClosed(handler);
    }

    private static void awaitStreamsClosed(McpProxyHandler handler) throws InterruptedException
    {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (handler.activeSseStreamCount() != 0 && System.nanoTime() < deadline)
        {
            Thread.sleep(10);
        }
        assertEquals("Stream tasks must relinquish exchanges after server stop", 0, handler.activeSseStreamCount());
    }

    private static HttpRequest.Builder request(ProxyServer server, String path)
    {
        String host = InetAddress.getLoopbackAddress().getHostAddress();
        if (host.contains(":"))
        {
            host = "[" + host + "]";
        }
        return HttpRequest.newBuilder(URI.create("http://" + host + ":" + server.getPort() + path))
            .timeout(Duration.ofSeconds(5));
    }

    private static HttpResponse<String> initialize(HttpClient client, ProxyServer server) throws Exception
    {
        HttpResponse<String> response = client.send(request(server, "/mcp").header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(initializeBody())).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        return response;
    }

    private static String initializeBody()
    {
        return "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{"
            + "\"protocolVersion\":\"2025-11-25\",\"capabilities\":{},"
            + "\"clientInfo\":{\"name\":\"sse-capacity-test\",\"version\":\"1\"}}}";
    }

    private static InputStream openStream(HttpClient client, ProxyServer server, String session) throws Exception
    {
        HttpResponse<InputStream> response = client.send(request(server, "/mcp")
            .header("Mcp-Session-Id", session).header("Accept", "text/event-stream").GET().build(),
            HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200)
        {
            response.body().close();
        }
        assertEquals(200, response.statusCode());
        return response.body();
    }

    @Test
    public void testNullRemoteAddressIsNotLoopback()
    {
        assertFalse(ProxyServer.isLoopbackRequest(null));
    }

    @Test
    public void testLoopbackIpv4AddressIsAccepted()
    {
        InetSocketAddress loopback = new InetSocketAddress(InetAddress.getLoopbackAddress(), 54321);

        assertTrue(ProxyServer.isLoopbackRequest(loopback));
    }

    @Test
    public void testExplicit127AddressIsAccepted() throws UnknownHostException
    {
        InetAddress explicitLoopback = InetAddress.getByAddress(new byte[] { 127, 0, 0, 1 });

        assertTrue(ProxyServer.isLoopbackRequest(new InetSocketAddress(explicitLoopback, 1)));
    }

    @Test
    public void testNonLoopbackAddressIsRejected() throws UnknownHostException
    {
        InetAddress remote = InetAddress.getByAddress(new byte[] { 8, 8, 8, 8 });

        assertFalse(ProxyServer.isLoopbackRequest(new InetSocketAddress(remote, 54321)));
    }
}
