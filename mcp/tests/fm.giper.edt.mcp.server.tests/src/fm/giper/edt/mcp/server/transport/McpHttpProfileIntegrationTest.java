/**
 * MCP Server for EDT - Tests
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server.transport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;

import fm.giper.edt.mcp.server.McpServer;
import fm.giper.edt.mcp.server.profiles.ToolProfile;
import fm.giper.edt.mcp.server.profiles.ToolProfileSnapshot;
import fm.giper.edt.mcp.server.protocol.McpConstants;
import fm.giper.edt.mcp.server.protocol.McpProtocolHandler;
import fm.giper.edt.mcp.server.tools.IMcpTool;
import fm.giper.edt.mcp.server.tools.McpToolRegistry;
import fm.giper.edt.mcp.server.tools.impl.GetToolGuideTool;

/** Exercises profile path routing and session binding through a real loopback HTTP socket. */
public class McpHttpProfileIntegrationTest
{
    private static final String TOOL_A = "profile_test_a"; //$NON-NLS-1$
    private static final String TOOL_B = "profile_test_b"; //$NON-NLS-1$
    private static final String GUIDE_TOOL = "profile_test_guide"; //$NON-NLS-1$
    private static final String GUIDE_TEXT = "profile integration guide sentinel"; //$NON-NLS-1$

    private final McpToolRegistry registry = McpToolRegistry.getInstance();
    private CollectionSnapshot savedTools;
    private HttpServer http;
    private java.util.concurrent.ExecutorService httpExecutor;
    private java.util.concurrent.ExecutorService toolExecutor;
    private HttpClient client;
    private URI base;
    private AtomicReference<ToolProfileSnapshot> profiles;
    private AtomicBoolean deniedToolExecuted;

    @Before
    public void setUp() throws Exception
    {
        savedTools = new CollectionSnapshot(registry.getAllTools());
        registry.clear();
        deniedToolExecuted = new AtomicBoolean();
        registry.register(new FakeTool(TOOL_A, "A", "", null)); //$NON-NLS-1$
        registry.register(new FakeTool(TOOL_B, "B", "", deniedToolExecuted)); //$NON-NLS-1$
        registry.register(new FakeTool(GUIDE_TOOL, "Guide", GUIDE_TEXT, null)); //$NON-NLS-1$
        registry.register(new GetToolGuideTool());

        profiles = new AtomicReference<>(snapshot(
            profile("default", Set.of(TOOL_A, TOOL_B, GUIDE_TOOL, GetToolGuideTool.NAME), true), //$NON-NLS-1$
            profile("alpha", Set.of(TOOL_A, GetToolGuideTool.NAME), true), //$NON-NLS-1$
            profile("beta", Set.of(TOOL_B), true), //$NON-NLS-1$
            profile("disabled", Set.of(TOOL_B), false))); //$NON-NLS-1$

        McpServer server = new McpServer();
        McpProtocolHandler protocol = new McpProtocolHandler();
        toolExecutor = Executors.newCachedThreadPool();
        InterruptibleToolExecutor interruptible = new InterruptibleToolExecutor(server, protocol);
        http = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        http.createContext("/", new McpHttpHandler(server, protocol, interruptible, false, profiles::get)); //$NON-NLS-1$
        httpExecutor = Executors.newCachedThreadPool();
        http.setExecutor(httpExecutor);
        http.start();
        base = URI.create("http://127.0.0.1:" + http.getAddress().getPort()); //$NON-NLS-1$
        client = HttpClient.newHttpClient();
    }

    @After
    public void tearDown()
    {
        if (http != null)
        {
            http.stop(0);
        }
        if (httpExecutor != null)
        {
            httpExecutor.shutdownNow();
        }
        if (toolExecutor != null)
        {
            toolExecutor.shutdownNow();
        }
        if (savedTools != null)
        {
            registry.replaceAll(savedTools.tools);
        }
    }

    @Test
    public void initializeFallbacksUnknownAndDisabledProfileToDefault() throws Exception
    {
        for (String id : List.of("missing", "disabled")) //$NON-NLS-1$ //$NON-NLS-2$
        {
            HttpResponse<String> response = post("/mcp/profiles/" + id, initialize()); //$NON-NLS-1$
            assertEquals(response.body(), 200, response.statusCode());
            assertNotNull(response.headers().firstValue(McpConstants.HEADER_SESSION_ID).orElse(null));
            JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result"); //$NON-NLS-1$
            assertEquals("edt-mcp-server/default", result.getAsJsonObject("serverInfo").get("name").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
            assertTrue(result.get("instructions").getAsString().contains("default")); //$NON-NLS-1$
        }
    }

    @Test
    public void sessionsBindToCanonicalPathAndEffectiveIdAndAllowlistRefreshesInPlace() throws Exception
    {
        HttpResponse<String> initialized = post("/mcp/profiles/alpha", initialize()); //$NON-NLS-1$
        assertEquals(200, initialized.statusCode());
        String session = initialized.headers().firstValue(McpConstants.HEADER_SESSION_ID).orElseThrow();
        assertEquals("edt-mcp-server/alpha", JsonParser.parseString(initialized.body()).getAsJsonObject() //$NON-NLS-1$
            .getAsJsonObject("result").getAsJsonObject("serverInfo").get("name").getAsString()); //$NON-NLS-1$

        HttpResponse<String> tools = post("/mcp/profiles/alpha", toolsList(), session); //$NON-NLS-1$
        assertEquals(200, tools.statusCode());
        assertTrue(tools.body(), hasTool(tools.body(), TOOL_A));
        assertFalse(tools.body(), hasTool(tools.body(), TOOL_B));

        HttpResponse<String> otherPath = post("/mcp", toolsList(), session); //$NON-NLS-1$
        assertEquals(404, otherPath.statusCode());
        HttpResponse<String> differentProfile = post("/mcp/profiles/beta", toolsList(), session); //$NON-NLS-1$
        assertEquals(404, differentProfile.statusCode());

        profiles.set(snapshot(
            profile("default", Set.of(TOOL_A, TOOL_B, GUIDE_TOOL, GetToolGuideTool.NAME), true), //$NON-NLS-1$
            profile("alpha", Set.of(TOOL_B, GetToolGuideTool.NAME), true), //$NON-NLS-1$
            profile("beta", Set.of(TOOL_B), true)));
        HttpResponse<String> refreshed = post("/mcp/profiles/alpha", toolsList(), session); //$NON-NLS-1$
        assertEquals(200, refreshed.statusCode());
        assertFalse(refreshed.body(), hasTool(refreshed.body(), TOOL_A));
        assertTrue(refreshed.body(), hasTool(refreshed.body(), TOOL_B));

        profiles.set(snapshot(
            profile("default", Set.of(TOOL_A, TOOL_B, GUIDE_TOOL, GetToolGuideTool.NAME), true), //$NON-NLS-1$
            profile("alpha", Set.of(TOOL_B, GetToolGuideTool.NAME), false), //$NON-NLS-1$
            profile("beta", Set.of(TOOL_B), true), //$NON-NLS-1$
            profile("disabled", Set.of(TOOL_B), false)));
        HttpResponse<String> switched = post("/mcp/profiles/alpha", toolsList(), session); //$NON-NLS-1$
        assertEquals("effective ID change must expire the session", 404, switched.statusCode()); //$NON-NLS-1$

        profiles.set(snapshot(
            profile("default", Set.of(TOOL_A, TOOL_B, GUIDE_TOOL, GetToolGuideTool.NAME), true), //$NON-NLS-1$
            profile("alpha", Set.of(TOOL_B, GetToolGuideTool.NAME), true), //$NON-NLS-1$
            profile("beta", Set.of(TOOL_B), true), //$NON-NLS-1$
            profile("disabled", Set.of(TOOL_B), false)));
        HttpResponse<String> staleAfterReenable = post("/mcp/profiles/alpha", toolsList(), session); //$NON-NLS-1$
        assertEquals("re-enabling a profile must not revive its prior session", 404, staleAfterReenable.statusCode()); //$NON-NLS-1$
    }

    @Test
    public void explicitPathGetAndDeleteRejectForeignOrMissingSessionAndMalformedPaths() throws Exception
    {
        assertEquals(400, get("/mcp/profiles/alpha").statusCode()); //$NON-NLS-1$
        assertEquals(400, delete("/mcp/profiles/alpha", null).statusCode()); //$NON-NLS-1$

        HttpResponse<String> initialized = post("/mcp/profiles/alpha", initialize()); //$NON-NLS-1$
        String session = initialized.headers().firstValue(McpConstants.HEADER_SESSION_ID).orElseThrow();
        assertEquals(200, get("/mcp/profiles/alpha", session).statusCode()); //$NON-NLS-1$
        assertEquals(404, get("/mcp/profiles/beta", session).statusCode()); //$NON-NLS-1$
        assertEquals(404, delete("/mcp/profiles/beta", session).statusCode()); //$NON-NLS-1$
        assertEquals(200, delete("/mcp/profiles/alpha", session).statusCode()); //$NON-NLS-1$

        assertEquals(400, post("/mcp/profiles/%2falpha", initialize()).statusCode()); //$NON-NLS-1$
        assertEquals(400, post("/mcp/profiles/Alpha", initialize()).statusCode()); //$NON-NLS-1$
    }

    @Test
    public void profileFiltersToolsResourcesAndGuideLookup() throws Exception
    {
        HttpResponse<String> initialized = post("/mcp/profiles/alpha", initialize()); //$NON-NLS-1$
        String session = initialized.headers().firstValue(McpConstants.HEADER_SESSION_ID).orElseThrow();
        HttpResponse<String> resources = post("/mcp/profiles/alpha", resourcesList(), session); //$NON-NLS-1$
        assertEquals(200, resources.statusCode());
        assertFalse(resources.body(), resources.body().contains("guide://" + TOOL_B)); //$NON-NLS-1$

        HttpResponse<String> guideCall = post("/mcp/profiles/alpha", toolCall(GetToolGuideTool.NAME, //$NON-NLS-1$
            "{\"toolName\":\"" + GUIDE_TOOL + "\"}"), session); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals(200, guideCall.statusCode());
        assertTrue(guideCall.body(), JsonParser.parseString(guideCall.body()).getAsJsonObject()
            .getAsJsonObject("result").get("isError").getAsBoolean()); //$NON-NLS-1$
        assertFalse("guide for denied tool must not be loaded", guideCall.body().contains(GUIDE_TEXT)); //$NON-NLS-1$

        HttpResponse<String> directCall = post("/mcp/profiles/alpha", toolCall(TOOL_B, "{}"), session); //$NON-NLS-1$
        assertTrue(directCall.body(), JsonParser.parseString(directCall.body()).getAsJsonObject()
            .getAsJsonObject("result").get("isError").getAsBoolean()); //$NON-NLS-1$
        assertFalse("denied tool must not execute", deniedToolExecuted.get()); //$NON-NLS-1$

        HttpResponse<String> resourceRead = post("/mcp/profiles/alpha", //$NON-NLS-1$
            "{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"resources/read\",\"params\":{" //$NON-NLS-1$
                + "\"uri\":\"guide://" + GUIDE_TOOL + "\"}}", session); //$NON-NLS-1$ //$NON-NLS-2$
        assertTrue(resourceRead.body(), JsonParser.parseString(resourceRead.body()).getAsJsonObject()
            .get("error").getAsJsonObject().get("message").getAsString().contains("Unknown guide resource")); //$NON-NLS-1$
    }

    private HttpResponse<String> post(String path, String body) throws Exception
    {
        return post(path, body, null);
    }

    private HttpResponse<String> post(String path, String body, String session) throws Exception
    {
        HttpRequest.Builder request = HttpRequest.newBuilder(base.resolve(path))
            .header("Content-Type", "application/json") //$NON-NLS-1$
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (session != null)
        {
            request.header(McpConstants.HEADER_SESSION_ID, session);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws Exception
    {
        return get(path, null);
    }

    private HttpResponse<String> get(String path, String session) throws Exception
    {
        HttpRequest.Builder request = HttpRequest.newBuilder(base.resolve(path)).GET();
        if (session != null)
        {
            request.header(McpConstants.HEADER_SESSION_ID, session);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> delete(String path, String session) throws Exception
    {
        HttpRequest.Builder request = HttpRequest.newBuilder(base.resolve(path)).DELETE();
        if (session != null)
        {
            request.header(McpConstants.HEADER_SESSION_ID, session);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String initialize()
    {
        return "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{" //$NON-NLS-1$
            + "\"protocolVersion\":\"" + McpConstants.PROTOCOL_VERSION + "\",\"capabilities\":{}," //$NON-NLS-1$ //$NON-NLS-2$
            + "\"clientInfo\":{\"name\":\"profile-test\",\"version\":\"1\"}}}"; //$NON-NLS-1$
    }

    private static String toolsList()
    {
        return "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}"; //$NON-NLS-1$
    }

    private static String resourcesList()
    {
        return "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"resources/list\"}"; //$NON-NLS-1$
    }

    private static String toolCall(String name, String arguments)
    {
        return "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\",\"params\":{" //$NON-NLS-1$
            + "\"name\":\"" + name + "\",\"arguments\":" + arguments + "}}"; //$NON-NLS-1$ //$NON-NLS-2$
    }

    private static ToolProfile profile(String id, Set<String> tools, boolean enabled)
    {
        return ToolProfile.builder().id(id).displayName(id).enabled(enabled).allowedTools(tools).build();
    }

    private static ToolProfileSnapshot snapshot(ToolProfile... profiles)
    {
        return ToolProfileSnapshot.of(1L, List.of(profiles));
    }

    private static boolean hasTool(String body, String name)
    {
        JsonArray tools = JsonParser.parseString(body).getAsJsonObject().getAsJsonObject("result") //$NON-NLS-1$
            .getAsJsonArray("tools"); //$NON-NLS-1$
        return tools.asList().stream().anyMatch(tool -> name.equals(tool.getAsJsonObject().get("name").getAsString())); //$NON-NLS-1$
    }

    private static final class FakeTool implements IMcpTool
    {
        private final String name;
        private final String description;
        private final String guide;
        private final AtomicBoolean executed;

        FakeTool(String name, String description, String guide, AtomicBoolean executed)
        {
            this.name = name;
            this.description = description;
            this.guide = guide;
            this.executed = executed;
        }

        @Override public String getName() { return name; }
        @Override public String getDescription() { return description; }
        @Override public String getInputSchema() { return "{\"type\":\"object\"}"; } //$NON-NLS-1$
        @Override public String execute(Map<String, String> params)
        {
            if (executed != null)
            {
                executed.set(true);
            }
            return "ok"; //$NON-NLS-1$
        }
        @Override public String getGuide() { return guide; }
    }

    private static final class CollectionSnapshot
    {
        private final List<IMcpTool> tools;

        CollectionSnapshot(java.util.Collection<IMcpTool> tools)
        {
            this.tools = List.copyOf(tools);
        }
    }
}
