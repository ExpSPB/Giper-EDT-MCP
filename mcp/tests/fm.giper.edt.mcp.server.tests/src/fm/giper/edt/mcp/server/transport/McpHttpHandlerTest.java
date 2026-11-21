/**
 * MCP Server for EDT - Tests
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server.transport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.junit.Test;
import org.mockito.Mockito;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

import fm.giper.edt.mcp.server.protocol.McpConstants;
import fm.giper.edt.mcp.server.protocol.McpProtocolHandler;
import fm.giper.edt.mcp.server.transport.McpHttpHandler.Route;

/**
 * The transport's routing decision. {@code handleMcpRequest} needs a live exchange and the
 * server's executors, so the decision itself lives in {@link Route} and is exercised here on
 * bodies parsed by the same {@link McpProtocolHandler#parse} the handler uses.
 */
public class McpHttpHandlerTest
{
    private final McpProtocolHandler handler = new McpProtocolHandler();

    @Test
    public void concurrentRoutesDeliverTheSamePayloadWithoutSharingTheMode() throws Exception
    {
        var registry = fm.giper.edt.mcp.server.tools.McpToolRegistry.getInstance();
        var saved = registry.getAllTools();
        var preference = fm.giper.edt.mcp.server.Activator.getDefault().getPreferenceStore();
        String key = fm.giper.edt.mcp.server.preferences.PreferenceConstants.PREF_PLAIN_TEXT_MODE;
        boolean previous = preference.getBoolean(key);
        var bothExecuting = new java.util.concurrent.CountDownLatch(2);
        String payload = "{\"value\":\"" + "full result ".repeat(100) + "\"}";
        var tool = new fm.giper.edt.mcp.server.tools.IMcpTool() {
            public String getName() { return "test_delivery"; }
            public String getDescription() { return "Test delivery"; }
            public String getInputSchema() { return "{\"type\":\"object\",\"properties\":{}}"; }
            public ResponseType getResponseType() { return ResponseType.JSON; }
            public String execute(java.util.Map<String, String> params) {
                bothExecuting.countDown();
                try { assertTrue(bothExecuting.await(3, java.util.concurrent.TimeUnit.SECONDS)); }
                catch (InterruptedException e) { throw new AssertionError(e); }
                return payload;
            }
        };
        var workers = java.util.concurrent.Executors.newFixedThreadPool(2);
        try
        {
            preference.setValue(key, false);
            registry.replaceAll(java.util.List.of(tool));
            var server = new fm.giper.edt.mcp.server.McpServer();
            var executor = new InterruptibleToolExecutor(server, handler);
            var transport = new McpHttpHandler(server, handler, executor, false);
            var structured = workers.submit(() -> callRoute(transport, "/mcp"));
            var plain = workers.submit(() -> callRoute(transport, "/mcp/plaintext"));
            var a = structured.get(5, java.util.concurrent.TimeUnit.SECONDS);
            var b = plain.get(5, java.util.concurrent.TimeUnit.SECONDS);
            var expected = com.google.gson.JsonParser.parseString(payload);
            assertEquals(expected, a.get("structuredContent"));
            assertEquals(expected, b.get("structuredContent"));
            String digest = a.getAsJsonArray("content").get(0).getAsJsonObject().get("text").getAsString();
            String fullText = b.getAsJsonArray("content").get(0).getAsJsonObject().get("text").getAsString();
            assertTrue(digest.length() <= 500);
            org.junit.Assert.assertNotEquals(payload, digest);
            assertEquals(expected, com.google.gson.JsonParser.parseString(fullText));
            org.junit.Assert.assertFalse(preference.getBoolean(key));
        }
        finally
        {
            workers.shutdownNow(); registry.replaceAll(saved); preference.setValue(key, previous);
        }
    }

    private static JsonObject callRoute(McpHttpHandler transport, String path) throws Exception
    {
        String initialization = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{"
            + "\"protocolVersion\":\"" + McpConstants.PROTOCOL_VERSION + "\",\"capabilities\":{},"
            + "\"clientInfo\":{\"name\":\"delivery-test\",\"version\":\"1\"}}}";
        Headers responseHeaders = new Headers();
        post(transport, path, initialization, new Headers(), responseHeaders);
        String sessionId = responseHeaders.getFirst(McpConstants.HEADER_SESSION_ID);
        assertNotNull(sessionId);

        Headers headers = new Headers();
        headers.set(McpConstants.HEADER_SESSION_ID, sessionId);
        String body = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\","
            + "\"params\":{\"name\":\"test_delivery\",\"arguments\":{}}}";
        return post(transport, path, body, headers, new Headers()).getAsJsonObject("result");
    }

    private static JsonObject post(McpHttpHandler transport, String path, String body, Headers headers,
        Headers responseHeaders) throws Exception
    {
        HttpExchange exchange = Mockito.mock(HttpExchange.class);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Mockito.when(exchange.getRequestMethod()).thenReturn("POST");
        Mockito.when(exchange.getRequestURI()).thenReturn(URI.create(path));
        Mockito.when(exchange.getRequestHeaders()).thenReturn(headers);
        Mockito.when(exchange.getResponseHeaders()).thenReturn(responseHeaders);
        Mockito.when(exchange.getRequestBody()).thenReturn(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
        Mockito.when(exchange.getResponseBody()).thenReturn(output);
        transport.handle(exchange);
        return JsonParser.parseString(output.toString(StandardCharsets.UTF_8)).getAsJsonObject();
    }

    @Test
    public void testAToolCallCarryingTheQuotedWordInitializeStillRoutesAsAToolCall()
    {
        // Exactly the reachable case of #565: an argument whose VALUE is that word - searching
        // the configuration for `initialize`, or naming a method that - serializes as the very
        // token the old probe looked for, and such a call was then answered with a stray
        // Mcp-Session-Id header on both the JSON and the SSE path.
        String body = "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\","
            + "\"params\":{\"name\":\"search_in_code\","
            + "\"arguments\":{\"query\":\"initialize\"}}}";

        assertTrue("the substring probe this replaced must really match this body, "
            + "or the test proves nothing",
            body.contains("\"" + McpConstants.METHOD_INITIALIZE + "\""));
        assertEquals(Route.TOOL_CALL, Route.of(handler.parse(body)));
    }

    @Test
    public void testTheRealInitializeStillRoutesAsInitialize()
    {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\","
            + "\"params\":{\"protocolVersion\":\"2025-11-25\"}}";

        assertEquals(Route.INITIALIZE, Route.of(handler.parse(body)));
    }

    @Test
    public void testEveryOtherMethodTakesThePlainProtocolPath()
    {
        assertEquals(Route.OTHER,
            Route.of(handler.parse("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}")));
        assertEquals(Route.OTHER,
            Route.of(handler.parse("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"ping\"}")));
    }

    @Test
    public void testAnUnparseableBodyTakesThePlainProtocolPath()
    {
        // parse() returns null on a syntax error; the protocol handler answers "invalid request"
        // for it, which is the OTHER path - never a session-issuing initialize.
        assertEquals(Route.OTHER, Route.of(handler.parse("{ not json")));
        assertEquals(Route.OTHER, Route.of(null));
    }

    @Test
    public void testAMethodlessBodyNamingInitializeElsewhereTakesThePlainPath()
    {
        // A notification or a malformed call whose params merely mention the word: no method,
        // so no special path. The substring probe called this one an initialize too.
        String body = "{\"jsonrpc\":\"2.0\",\"params\":{\"text\":\"initialize\"}}";

        assertTrue(body.contains("\"" + McpConstants.METHOD_INITIALIZE + "\""));
        assertEquals(Route.OTHER, Route.of(handler.parse(body)));
    }
}
