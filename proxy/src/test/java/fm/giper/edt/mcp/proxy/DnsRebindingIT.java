/**
 * MCP Server for EDT - Proxy Tests
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.proxy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import fm.giper.edt.mcp.proxy.ProxyRoutingIT.ProxyFixture;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Wire test for the proxy's DNS-rebinding protection (Origin admission).
 *
 * <p>Mirrors the official conformance scenario {@code dns-rebinding-protection}: a
 * non-localhost {@code Origin} must be HTTP 403; a loopback {@code Origin} and a
 * missing {@code Origin} (CLI / MCP client) must still reach {@code initialize}.
 * The JDK {@link HttpClient} cannot override {@code Host}, so this IT exercises
 * the Origin half of the suite (the half the proxy actually implements, matching
 * the plugin).
 */
public class DnsRebindingIT
{
    private static final String INITIALIZE_BODY =
        "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\"," //$NON-NLS-1$
            + "\"params\":{\"protocolVersion\":\"2025-11-25\",\"capabilities\":{}," //$NON-NLS-1$
            + "\"clientInfo\":{\"name\":\"dns-rebinding-it\",\"version\":\"1.0.0\"}}}"; //$NON-NLS-1$

    @Rule
    public final Timeout globalTimeout = Timeout.seconds(30);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private ProxyFixture proxy;
    private URI mcpUri;

    @Before
    public void setUp() throws Exception
    {
        int[] deadPorts = ProxyRoutingIT.reserveFreePorts(2);
        proxy = new ProxyFixture(deadPorts[0], deadPorts[1]);
        proxy.start();
        mcpUri = URI.create("http://127.0.0.1:" + proxy.port() + "/mcp"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @After
    public void tearDown()
    {
        if (proxy != null)
        {
            proxy.stop();
        }
    }

    @Test
    public void testEvilOriginIsRejectedWith403() throws Exception
    {
        HttpResponse<String> response = postInitialize("http://evil.example.com"); //$NON-NLS-1$

        assertEquals("non-localhost Origin must be 403: " + response.body(), //$NON-NLS-1$
            403, response.statusCode());
        JsonObject parsed = JsonParser.parseString(response.body()).getAsJsonObject();
        assertTrue("403 body must carry a JSON-RPC error: " + parsed, parsed.has("error")); //$NON-NLS-1$ //$NON-NLS-2$
        String message = parsed.getAsJsonObject("error").get("message").getAsString(); //$NON-NLS-1$ //$NON-NLS-2$
        assertTrue("403 message must name Origin: " + message, message.contains("Origin")); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testLoopbackOriginIsAccepted() throws Exception
    {
        HttpResponse<String> response = postInitialize("http://127.0.0.1:" + proxy.port()); //$NON-NLS-1$

        assertEquals("loopback Origin must reach initialize: " + response.body(), //$NON-NLS-1$
            200, response.statusCode());
    }

    @Test
    public void testMissingOriginIsAccepted() throws Exception
    {
        HttpResponse<String> response = postInitialize(null);

        assertEquals("a CLI client with no Origin must still initialize: " + response.body(), //$NON-NLS-1$
            200, response.statusCode());
    }

    private HttpResponse<String> postInitialize(String origin) throws Exception
    {
        HttpRequest.Builder builder = HttpRequest.newBuilder(mcpUri)
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json") //$NON-NLS-1$ //$NON-NLS-2$
            .header("Accept", "application/json") //$NON-NLS-1$ //$NON-NLS-2$
            .POST(HttpRequest.BodyPublishers.ofString(INITIALIZE_BODY, StandardCharsets.UTF_8));
        if (origin != null)
        {
            builder.header("Origin", origin); //$NON-NLS-1$
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}
