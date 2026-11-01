/**
 * MCP Server for EDT - Proxy Tests
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.proxy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import fm.giper.edt.mcp.proxy.ProxyRoutingIT.ProxyFixture;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Integration test for the {@code McpProxyHandler} request-body size cap (issue #253
 * hardening, {@code McpProxyHandler.MAX_BODY_BYTES} = 4 MiB).
 *
 * <p>Two ways an oversized body can reach the handler, both proven to be rejected with
 * {@code 413} without the handler ever buffering the whole thing:
 * <ul>
 * <li>a real body sent with a genuine, oversized {@code Content-Length} header - rejected
 * up front, before a single byte of the body is read;</li>
 * <li>a body with NO declared length (chunked transfer, the JDK client's behaviour for
 * {@link HttpRequest.BodyPublishers#ofInputStream}) that only turns out to be oversized while
 * streaming - caught by the bounded read itself.</li>
 * </ul>
 *
 * <p>No backend is needed: the size cap is enforced in {@code McpProxyHandler.readBody}, before
 * any session or routing logic runs, so a bare {@code initialize} POST is enough to exercise it.
 * A payload a few MiB over the cap stands in for an attacker-scale (multi-GB) one; what proves
 * the fix does not OOM on that scale is that the handler caps its buffer at
 * {@code MAX_BODY_BYTES + 1} bytes regardless of how much more the client sends (see
 * {@code McpProxyHandler.readBody}), which these tests exercise the same code path for.
 */
public class RequestBodyLimitIT
{
    /** Mirrors {@code McpProxyHandler.MAX_BODY_BYTES} - kept independent on purpose (a black-box test). */
    private static final int MAX_BODY_BYTES = 4 * 1024 * 1024;

    /** Comfortably over the cap without being an unreasonably slow test payload. */
    private static final int OVERSIZED_BODY_BYTES = MAX_BODY_BYTES + (1024 * 1024);

    /** Hard cap per test so a transport hang fails fast instead of wedging the build. */
    @Rule
    public final Timeout globalTimeout = Timeout.seconds(60);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private ProxyFixture proxy;
    private URI mcpUri;

    /**
     * Starts the proxy with an empty (both ports closed) scan range - no backend is needed to
     * exercise the body-size cap, which is enforced before routing.
     */
    @Before
    public void setUp() throws Exception
    {
        int[] deadPorts = ProxyRoutingIT.reserveFreePorts(2); //$NON-NLS-1$
        proxy = new ProxyFixture(deadPorts[0], deadPorts[1]);
        proxy.start();
        mcpUri = URI.create("http://127.0.0.1:" + proxy.port() + "/mcp"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    /** Stops the proxy. */
    @After
    public void tearDown()
    {
        if (proxy != null)
        {
            proxy.stop();
        }
    }

    /**
     * A body sent with a genuine {@code Content-Length} header over the cap must be rejected
     * with {@code 413} - proving the header fast-path: the handler must reject before reading
     * any of the (oversized) body.
     */
    @Test
    public void testOversizedContentLengthBodyRejectedWith413() throws Exception
    {
        byte[] body = oversizedPayload();
        HttpRequest request = HttpRequest.newBuilder(mcpUri)
            .timeout(Duration.ofSeconds(30)) //$NON-NLS-1$
            .header("Content-Type", "application/json") //$NON-NLS-1$ //$NON-NLS-2$
            .header("Accept", "application/json") //$NON-NLS-1$ //$NON-NLS-2$
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertRejectedWith413(response);
    }

    /**
     * A body with NO declared {@code Content-Length} (the JDK client streams it chunked when
     * given {@link HttpRequest.BodyPublishers#ofInputStream}) that exceeds the cap only while
     * being read must ALSO be rejected with {@code 413} - proving the bounded-read fallback
     * catches what the header check cannot see up front.
     */
    @Test
    public void testOversizedChunkedBodyWithNoContentLengthRejectedWith413() throws Exception
    {
        byte[] body = oversizedPayload();
        HttpRequest request = HttpRequest.newBuilder(mcpUri)
            .timeout(Duration.ofSeconds(30)) //$NON-NLS-1$
            .header("Content-Type", "application/json") //$NON-NLS-1$ //$NON-NLS-2$
            .header("Accept", "application/json") //$NON-NLS-1$ //$NON-NLS-2$
            .POST(HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(body)))
            .build();
        // Sanity-check the premise: an unknown-length publisher carries no Content-Length,
        // which is exactly the case the bounded read (not the header fast-path) must catch.
        assertTrue("test premise: the publisher must report an unknown content length", //$NON-NLS-1$
            request.bodyPublisher().orElseThrow().contentLength() <= 0);

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertRejectedWith413(response);
    }

    @Test
    public void testOversizedContentLengthIsRefusedBeforeTheClientSendsItsBody() throws Exception
    {
        try (Socket socket = new Socket("127.0.0.1", proxy.port())) //$NON-NLS-1$
        {
            socket.setSoTimeout(2_000);
            OutputStream out = socket.getOutputStream();
            out.write(("POST /mcp HTTP/1.1\r\nHost: localhost\r\nContent-Type: application/json\r\n" //$NON-NLS-1$
                + "Content-Length: " + OVERSIZED_BODY_BYTES + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII)); //$NON-NLS-1$ //$NON-NLS-2$
            out.flush();
            assertEarly413(socket);
            assertDrainWorkerReleased();
        }
    }

    @Test
    public void testOversizedChunkedStreamIsRefusedBeforeTheTerminatingChunk() throws Exception
    {
        try (Socket socket = new Socket("127.0.0.1", proxy.port())) //$NON-NLS-1$
        {
            socket.setSoTimeout(2_000);
            OutputStream out = socket.getOutputStream();
            out.write(("POST /mcp HTTP/1.1\r\nHost: localhost\r\nContent-Type: application/json\r\n" //$NON-NLS-1$
                + "Transfer-Encoding: chunked\r\n\r\n").getBytes(StandardCharsets.US_ASCII)); //$NON-NLS-1$
            byte[] body = new byte[MAX_BODY_BYTES + 1];
            Arrays.fill(body, (byte)'x');
            out.write((Integer.toHexString(body.length) + "\r\n").getBytes(StandardCharsets.US_ASCII)); //$NON-NLS-1$
            out.write(body);
            out.write("\r\n".getBytes(StandardCharsets.US_ASCII)); //$NON-NLS-1$
            out.flush();
            // No zero-size terminal chunk or socket EOF is sent: rejection must not await it.
            assertEarly413(socket);
            assertDrainWorkerReleased();
        }
    }

    private void assertDrainWorkerReleased() throws Exception
    {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (proxy.handler().activeRequestDrains() != 0 && System.nanoTime() < deadline)
        {
            Thread.sleep(10);
        }
        assertEquals("cancelling the stalled socket read must release its drain worker", //$NON-NLS-1$
            0, proxy.handler().activeRequestDrains());
    }

    @Test
    public void testSaturatedDrainPoolDoesNotQueueOrCreateFallbackWorkers() throws Exception
    {
        assertEquals(32, ProxyServer.MAX_REQUEST_DRAINS);
        ThreadPoolExecutor saturated = new ThreadPoolExecutor(0, 1, 1, TimeUnit.SECONDS,
            new SynchronousQueue<>());
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        saturated.execute(() -> {
            entered.countDown();
            try
            {
                release.await();
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
            }
        });
        try
        {
            assertTrue(entered.await(1, TimeUnit.SECONDS));
            proxy.handler().setRequestDrainPool(saturated);
            try (Socket socket = new Socket("127.0.0.1", proxy.port())) //$NON-NLS-1$
            {
                socket.setSoTimeout(2_000);
                OutputStream out = socket.getOutputStream();
                out.write(("POST /mcp HTTP/1.1\r\nHost: localhost\r\nContent-Length: " //$NON-NLS-1$
                    + OVERSIZED_BODY_BYTES + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII)); //$NON-NLS-1$
                out.flush();
                assertEarly413(socket);
            }
            assertEquals(1, saturated.getLargestPoolSize());
            assertEquals(0, saturated.getQueue().size());
        }
        finally
        {
            proxy.handler().setRequestDrainPool(null);
            release.countDown();
            saturated.shutdownNow();
            assertTrue(saturated.awaitTermination(1, TimeUnit.SECONDS));
        }
    }

    private static void assertEarly413(Socket socket) throws Exception
    {
        BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(),
            StandardCharsets.US_ASCII));
        String status = reader.readLine();
        assertTrue("the refusal must arrive before the upload finishes: " + status, //$NON-NLS-1$
            status != null && status.startsWith("HTTP/1.1 413")); //$NON-NLS-1$
        int contentLength = -1;
        String line;
        while ((line = reader.readLine()) != null && !line.isEmpty())
        {
            if (line.toLowerCase(java.util.Locale.ROOT).startsWith("content-length:")) //$NON-NLS-1$
            {
                contentLength = Integer.parseInt(line.substring(line.indexOf(':') + 1).trim());
            }
        }
        assertTrue("the response has a complete bounded body", contentLength > 0); //$NON-NLS-1$
        char[] body = new char[contentLength];
        int offset = 0;
        while (offset < body.length)
        {
            int read = reader.read(body, offset, body.length - offset);
            assertTrue("the entire refusal must be delivered", read > 0); //$NON-NLS-1$
            offset += read;
        }
        assertTrue(new String(body).contains(String.valueOf(MAX_BODY_BYTES)));
        try
        {
            assertEquals("the rejected exchange must close without waiting for upload EOF", -1, reader.read()); //$NON-NLS-1$
        }
        catch (SocketException closed)
        {
            // A reset after the complete response is also a closed connection, never a stalled worker.
        }
    }

    @Test
    public void testAcceptedRequestsStillReuseOneKeepAliveConnection() throws Exception
    {
        try (Socket socket = new Socket("127.0.0.1", proxy.port())) //$NON-NLS-1$
        {
            socket.setSoTimeout(2_000);
            OutputStream out = socket.getOutputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(),
                StandardCharsets.US_ASCII));
            for (int id = 1; id <= 2; id++)
            {
                String body = "{\"jsonrpc\":\"2.0\",\"id\":" + id //$NON-NLS-1$
                    + ",\"method\":\"initialize\",\"params\":{}}"; //$NON-NLS-1$
                out.write(("POST /mcp HTTP/1.1\r\nHost: localhost\r\nAccept: application/json\r\n" //$NON-NLS-1$
                    + "Content-Type: application/json\r\nContent-Length: " + body.length() //$NON-NLS-1$
                    + "\r\n\r\n" + body).getBytes(StandardCharsets.US_ASCII)); //$NON-NLS-1$
                out.flush();
                String status = reader.readLine();
                assertTrue("accepted request " + id + " must reuse the same socket: " + status, //$NON-NLS-1$ //$NON-NLS-2$
                    status != null && status.startsWith("HTTP/1.1 200")); //$NON-NLS-1$
                int contentLength = -1;
                String line;
                while ((line = reader.readLine()) != null && !line.isEmpty())
                {
                    if (line.toLowerCase(java.util.Locale.ROOT).startsWith("content-length:")) //$NON-NLS-1$
                    {
                        contentLength = Integer.parseInt(line.substring(line.indexOf(':') + 1).trim());
                    }
                }
                assertTrue(contentLength > 0);
                char[] reply = new char[contentLength];
                int offset = 0;
                while (offset < reply.length)
                {
                    int read = reader.read(reply, offset, reply.length - offset);
                    assertTrue(read > 0);
                    offset += read;
                }
                JsonObject envelope = Json.parseObject(new String(reply));
                assertEquals(id, envelope.get("id").getAsInt()); //$NON-NLS-1$
                assertTrue(envelope.has("result")); //$NON-NLS-1$
            }
        }
    }

    /**
     * A body AT the cap (not over it) must still be processed normally - proves the check is
     * "over the cap", not an off-by-one that also rejects legitimate large-but-allowed bodies.
     * An unparseable-but-allowed-size body reaches the existing "malformed JSON-RPC" handling
     * (200 with a JSON-RPC error), never a transport-level 413.
     */
    @Test
    public void testBodyAtTheCapIsNotRejectedByTheSizeCheck() throws Exception
    {
        byte[] body = new byte[MAX_BODY_BYTES];
        Arrays.fill(body, (byte)'x');
        HttpRequest request = HttpRequest.newBuilder(mcpUri)
            .timeout(Duration.ofSeconds(30)) //$NON-NLS-1$
            .header("Content-Type", "application/json") //$NON-NLS-1$ //$NON-NLS-2$
            .header("Accept", "application/json") //$NON-NLS-1$ //$NON-NLS-2$
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals("a body AT the cap must not hit the 413 size guard: " + response.body(), //$NON-NLS-1$
            200, response.statusCode());
    }

    private static byte[] oversizedPayload()
    {
        byte[] body = new byte[OVERSIZED_BODY_BYTES];
        Arrays.fill(body, (byte)'x');
        return body;
    }

    private static void assertRejectedWith413(HttpResponse<String> response)
    {
        assertEquals("an oversized body must be rejected at the transport level: " + response.body(), //$NON-NLS-1$
            413, response.statusCode());
        JsonObject parsed = JsonParser.parseString(response.body()).getAsJsonObject();
        assertTrue("413 body must carry a JSON-RPC error envelope: " + parsed, parsed.has("error")); //$NON-NLS-1$ //$NON-NLS-2$
        String message = parsed.getAsJsonObject("error").get("message").getAsString(); //$NON-NLS-1$ //$NON-NLS-2$
        assertTrue("413 message must name the byte limit: " + message, //$NON-NLS-1$
            message.contains(String.valueOf(MAX_BODY_BYTES)));
    }
}
