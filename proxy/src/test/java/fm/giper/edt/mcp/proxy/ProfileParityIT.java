package fm.giper.edt.mcp.proxy;

import static fm.giper.edt.mcp.proxy.ProxyRoutingIT.*;
import static org.junit.Assert.*;

import java.util.List;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import com.google.gson.JsonObject;

/** Integration coverage for explicit profile URL routing over the live proxy transport. */
public class ProfileParityIT
{
    @Rule public final Timeout timeout = Timeout.seconds(45);

    private int port;
    private FakeBackend backend;
    private ProxyFixture proxy;

    @Before
    public void start() throws Exception
    {
        port = reserveFreePorts(1)[0];
        backend = new FakeBackend(port, List.of("ERP"));
        backend.putProfile("review", "echo_port");
        backend.start();
        proxy = new ProxyFixture(port, port);
        proxy.start();
    }

    @After
    public void stop()
    {
        if (proxy != null) proxy.stop();
        stopQuietly(backend);
    }

    @Test
    public void profileToolsAndStatusUseProxyCanonicalEndpoint() throws Exception
    {
        McpTestClient client = new McpTestClient(proxy.port(), null, "/mcp/profiles/review");
        client.handshake();
        assertTrue(toolNames(client.request("tools/list", new JsonObject())).contains("echo_port"));

        JsonObject args = new JsonObject();
        args.addProperty("includeProfiles", true);
        JsonObject status = client.callTool("get_server_status", args);
        JsonObject profile = structuredContent(status).getAsJsonObject("activeProfile");
        assertEquals("review", profile.get("id").getAsString());
        assertEquals("http://127.0.0.1:" + proxy.port() + "/mcp/profiles/review",
            profile.get("endpoint").getAsString());
        assertTrue(structuredContent(status).getAsJsonArray("availableProfiles").size() > 0);
    }

    @Test
    public void proxySessionsCannotBeReplayedAcrossProfilePaths() throws Exception
    {
        McpTestClient review = new McpTestClient(proxy.port(), null, "/mcp/profiles/review");
        review.handshake();
        java.net.http.HttpResponse<String> replay = review.requestRaw("/mcp", "ping");
        assertEquals(400, replay.statusCode());
    }

    @Test
    public void unknownProfileFallsBackWithAnExplicitDiagnostic() throws Exception
    {
        McpTestClient missing = new McpTestClient(proxy.port(), null, "/mcp/profiles/missing");
        JsonObject initialize = missing.handshake();
        assertTrue(initialize.getAsJsonObject("result").get("instructions").getAsString()
            .contains("UNKNOWN_PROFILE"));
        assertTrue(toolNames(missing.request("tools/list", new JsonObject())).contains("fake_tool_one"));
    }

    @Test
    public void sessionExpiresWhenRequestedProfileFallsBack() throws Exception
    {
        McpTestClient review = new McpTestClient(proxy.port(), null, "/mcp/profiles/review");
        review.handshake();
        backend.disableProfile("review");

        // Refresh advances the registry generation; the following request resolves the changed
        // effective identity and must expire this session before any operation can use fallback.
        review.callTool("router_refresh", new JsonObject());
        java.net.http.HttpResponse<String> response = review.requestRaw("/mcp/profiles/review", "ping");
        assertEquals(404, response.statusCode());
        assertTrue(response.body().contains("initialize again"));
    }

    @Test
    public void backendListChangedIsDeliveredOnItsProfileStreamWithoutClosingTheSession() throws Exception
    {
        McpTestClient review = new McpTestClient(proxy.port(), null, "/mcp/profiles/review");
        review.handshake();
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        URI uri = URI.create("http://127.0.0.1:" + proxy.port() + "/mcp/profiles/review");
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10))
            .header("Accept", "text/event-stream")
            .header("Mcp-Session-Id", review.sessionId()).GET().build();
        HttpResponse<java.io.InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        assertEquals(200, response.statusCode());
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8)))
        {
            long connectDeadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
            while (backend.activeSseStreams("/mcp/profiles/review") == 0 && System.nanoTime() < connectDeadline)
                Thread.sleep(20L);
            assertTrue("proxy must open the matching backend profile stream",
                backend.activeSseStreams("/mcp/profiles/review") > 0);
            backend.publishListChanged("/mcp/profiles/review");
            java.util.concurrent.Future<Boolean> read = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                try
                {
                    String line;
                    while ((line = reader.readLine()) != null)
                        if (line.contains("notifications/tools/list_changed")) return Boolean.TRUE;
                    return Boolean.FALSE;
                }
                catch (java.io.IOException e) { return Boolean.FALSE; }
            });
            boolean notified;
            try { notified = read.get(5, java.util.concurrent.TimeUnit.SECONDS).booleanValue(); }
            catch (java.util.concurrent.TimeoutException e) { response.body().close(); read.cancel(true); notified = false; }
            assertTrue("profile-scoped list_changed must reach the connected client", notified);
        }
        assertNotNull(review.request("ping", new JsonObject()).get("result"));
    }
}
