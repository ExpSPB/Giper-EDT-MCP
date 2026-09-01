/**
 * MCP Server for EDT - Proxy Tests
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.proxy;

import static fm.giper.edt.mcp.proxy.ProxyRoutingIT.PROJECT_A;
import static fm.giper.edt.mcp.proxy.ProxyRoutingIT.PROJECT_B;
import static fm.giper.edt.mcp.proxy.ProxyRoutingIT.TOOL_ECHO_PORT;
import static fm.giper.edt.mcp.proxy.ProxyRoutingIT.TOOL_ROUTER_REFRESH;
import static fm.giper.edt.mcp.proxy.ProxyRoutingIT.TOOL_ROUTER_STATUS;
import static fm.giper.edt.mcp.proxy.ProxyRoutingIT.isToolError;
import static fm.giper.edt.mcp.proxy.ProxyRoutingIT.projectArgs;
import static fm.giper.edt.mcp.proxy.ProxyRoutingIT.reserveFreePorts;
import static fm.giper.edt.mcp.proxy.ProxyRoutingIT.stopQuietly;
import static fm.giper.edt.mcp.proxy.ProxyRoutingIT.structuredContent;
import static fm.giper.edt.mcp.proxy.ProxyRoutingIT.toolNames;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import fm.giper.edt.mcp.proxy.ProxyRoutingIT.McpTestClient;
import fm.giper.edt.mcp.proxy.ProxyRoutingIT.ProxyFixture;
import com.google.gson.JsonObject;

/**
 * Profile paths, path-bound sessions, compatibility groups, cache isolation and SSE.
 */
public class ProfileRoutingIT
{
    @Rule
    public final Timeout globalTimeout = Timeout.seconds(60);

    private FakeBackend backendA;
    private FakeBackend backendB;
    private ProxyFixture proxy;

    @After
    public void tearDown()
    {
        if (proxy != null)
        {
            proxy.stop();
        }
        stopQuietly(backendA);
        stopQuietly(backendB);
    }

    @Test
    public void testInvalidProfilePathIsHttp400() throws Exception
    {
        int[] ports = reserveFreePorts(1);
        backendA = new FakeBackend(ports[0], List.of(PROJECT_A));
        backendA.start();
        proxy = new ProxyFixture(ports[0], ports[0]);
        proxy.start();

        McpTestClient client = new McpTestClient(proxy.port(), "/mcp/profiles/Review"); //$NON-NLS-1$
        HttpResponse<String> response = client.postRaw(
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}"); //$NON-NLS-1$
        assertEquals(400, response.statusCode());
    }

    @Test
    public void testDoubleEncodedProfilePathIsHttp400() throws Exception
    {
        int[] ports = reserveFreePorts(1);
        backendA = new FakeBackend(ports[0], List.of(PROJECT_A));
        backendA.start();
        proxy = new ProxyFixture(ports[0], ports[0]);
        proxy.start();

        McpTestClient client = new McpTestClient(proxy.port(), "/mcp/profiles/%2570rod"); //$NON-NLS-1$
        HttpResponse<String> response = client.postRaw(
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}"); //$NON-NLS-1$
        assertEquals(400, response.statusCode());
    }

    @Test
    public void testSessionReplayOnAnotherPathIs404AndDeleteIsPathBound() throws Exception
    {
        int[] ports = reserveFreePorts(1);
        backendA = new FakeBackend(ports[0], List.of(PROJECT_A));
        backendA.putProfile("review", "echo_port"); //$NON-NLS-1$ //$NON-NLS-2$
        backendA.start();
        proxy = new ProxyFixture(ports[0], ports[0]);
        proxy.start();

        McpTestClient review = new McpTestClient(proxy.port(), "/mcp/profiles/review"); //$NON-NLS-1$
        review.handshake();
        McpTestClient legacy = new McpTestClient(proxy.port());
        legacy.handshake();

        McpTestClient stolen = new McpTestClient(proxy.port(), "/mcp"); //$NON-NLS-1$
        stolen.useSession(review.sessionId());
        HttpResponse<String> replay = stolen.requestRaw("ping", new JsonObject()); //$NON-NLS-1$
        assertEquals(404, replay.statusCode());

        assertEquals(200, review.deleteSession().statusCode());
        HttpResponse<String> afterDelete = review.requestRaw("ping", new JsonObject()); //$NON-NLS-1$
        assertEquals(404, afterDelete.statusCode());
        assertNotNull(legacy.request("ping", new JsonObject()).get("result")); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testUnknownProfileFallsBackAndDoesNotShareDefaultCache() throws Exception
    {
        int[] ports = reserveFreePorts(1);
        backendA = new FakeBackend(ports[0], List.of(PROJECT_A));
        backendA.putProfile("default", "echo_port", "fake_tool_one"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        backendA.start();
        proxy = new ProxyFixture(ports[0], ports[0]);
        proxy.start();

        McpTestClient missing = new McpTestClient(proxy.port(), "/mcp/profiles/missing"); //$NON-NLS-1$
        JsonObject init = missing.handshake();
        String instructions = init.getAsJsonObject("result").get("instructions").getAsString(); //$NON-NLS-1$ //$NON-NLS-2$
        assertTrue(instructions, instructions.contains("UNKNOWN_PROFILE")); //$NON-NLS-1$
        assertEquals("edt-mcp-proxy/default", //$NON-NLS-1$
            init.getAsJsonObject("result").getAsJsonObject("serverInfo").get("name").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

        Set<String> fallbackTools = toolNames(missing.request("tools/list", new JsonObject())); //$NON-NLS-1$
        McpTestClient explicitDefault = new McpTestClient(proxy.port(), "/mcp/profiles/default"); //$NON-NLS-1$
        explicitDefault.handshake();
        Set<String> defaultTools = toolNames(explicitDefault.request("tools/list", new JsonObject())); //$NON-NLS-1$
        assertTrue(fallbackTools.contains(TOOL_ROUTER_STATUS));
        assertTrue(defaultTools.contains(TOOL_ROUTER_STATUS));
        assertEquals(fallbackTools, defaultTools);
        assertTrue(proxy.registry().cachedToolsListResponse(proxy.registry()
            .groupFor(ProfileEndpointResolver.resolve("/mcp/profiles/missing"))) != null); //$NON-NLS-1$
    }

    @Test
    public void testMixedFingerprintIsNotASharedSuccessSurface() throws Exception
    {
        int[] ports = reserveFreePorts(2);
        backendA = new FakeBackend(ports[0], List.of(PROJECT_A));
        backendB = new FakeBackend(ports[1], List.of(PROJECT_B));
        backendA.putProfile("review", "echo_port"); //$NON-NLS-1$ //$NON-NLS-2$
        backendB.putProfile("review", "echo_port", "extra_review_tool"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        backendA.start();
        backendB.start();
        proxy = new ProxyFixture(ports[0], ports[1]);
        proxy.start();

        McpTestClient review = new McpTestClient(proxy.port(), "/mcp/profiles/review"); //$NON-NLS-1$
        review.handshake();
        JsonObject status = review.callTool(TOOL_ROUTER_STATUS, new JsonObject());
        assertFalse(isToolError(status));
        assertTrue("donor is the lowest port; the other backend must be incompatible", //$NON-NLS-1$
            structuredContent(status).getAsJsonArray("incompatibleBackends").size() >= 1); //$NON-NLS-1$

        JsonObject routed = review.callTool(TOOL_ECHO_PORT, projectArgs(PROJECT_B));
        assertTrue("a project on an incompatible backend must not be routed: " + routed, isToolError(routed)); //$NON-NLS-1$
    }

    @Test
    public void testDonorIsLowestCompatiblePortAndStaleRetryStaysOnProfileUrl() throws Exception
    {
        int[] ports = reserveFreePorts(2);
        backendA = new FakeBackend(ports[0], List.of(PROJECT_A));
        backendB = new FakeBackend(ports[1], List.of(PROJECT_B));
        backendA.putProfile("review", "echo_port"); //$NON-NLS-1$ //$NON-NLS-2$
        backendB.putProfile("review", "echo_port"); //$NON-NLS-1$ //$NON-NLS-2$
        backendA.start();
        backendB.start();
        proxy = new ProxyFixture(ports[0], ports[1]);
        proxy.start();

        McpTestClient review = new McpTestClient(proxy.port(), "/mcp/profiles/review"); //$NON-NLS-1$
        review.handshake();
        ProfileGroupSnapshot group = proxy.registry()
            .groupFor(ProfileEndpointResolver.resolve("/mcp/profiles/review")); //$NON-NLS-1$
        assertEquals(ports[0], group.getDonor().getPort());
        assertEquals(2, group.getCompatible().size());

        int before = backendA.getInitializeCount();
        backendA.invalidateSessions();
        JsonObject echo = review.callTool(TOOL_ECHO_PORT, projectArgs(PROJECT_A));
        assertFalse(isToolError(echo));
        assertTrue("HTTP 404 must re-handshake only that channel", backendA.getInitializeCount() > before); //$NON-NLS-1$
    }

    @Test
    public void testListChangedInvalidatesOnlyThatProfileAndDedupes() throws Exception
    {
        int[] ports = reserveFreePorts(1);
        backendA = new FakeBackend(ports[0], List.of(PROJECT_A));
        backendA.putProfile("review", "echo_port"); //$NON-NLS-1$ //$NON-NLS-2$
        backendA.start();
        proxy = new ProxyFixture(ports[0], ports[0]);
        proxy.start();
        McpTestClient review = new McpTestClient(proxy.port(), "/mcp/profiles/review"); //$NON-NLS-1$
        review.handshake();
        review.request("tools/list", new JsonObject()); //$NON-NLS-1$

        AtomicInteger delivered = new AtomicInteger();
        proxy.registry().sseHub().setInvalidator(id -> delivered.incrementAndGet());
        proxy.registry().sseHub().onBackendListChanged("review", "/mcp/profiles/review"); //$NON-NLS-1$ //$NON-NLS-2$
        proxy.registry().sseHub().onBackendListChanged("review", "/mcp/profiles/review"); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals(2, delivered.get());
        assertNotNull(TOOL_ROUTER_REFRESH);
    }

    @Test
    public void testPlainTextStatusDoesNotConfirmMissingProfileOrLeakBackendUrls() throws Exception
    {
        int[] ports = reserveFreePorts(1);
        backendA = new FakeBackend(ports[0], List.of(PROJECT_A));
        backendA.setPlainTextMode(true);
        backendA.start();
        proxy = new ProxyFixture(ports[0], ports[0]);
        proxy.start();

        McpTestClient client = new McpTestClient(proxy.port(), "/mcp/profiles/ghost"); //$NON-NLS-1$
        JsonObject init = client.handshake();
        assertTrue("initialize must name UNKNOWN_PROFILE, not a silent hit on 'ghost'", //$NON-NLS-1$
            init.toString().contains("UNKNOWN_PROFILE")); //$NON-NLS-1$

        JsonObject status = client.callTool("get_server_status", includeProfiles()); //$NON-NLS-1$
        String leaked = "http://127.0.0.1:" + backendA.getPort(); //$NON-NLS-1$
        assertFalse("plain-text get_server_status must not leak backend URLs: " + status, //$NON-NLS-1$
            status.toString().contains(leaked));
    }

    @Test
    public void testExplicitInitializeNamesEffectiveProfile() throws Exception
    {
        int[] ports = reserveFreePorts(1);
        backendA = new FakeBackend(ports[0], List.of(PROJECT_A));
        backendA.putProfile("review", "echo_port"); //$NON-NLS-1$ //$NON-NLS-2$
        backendA.start();
        proxy = new ProxyFixture(ports[0], ports[0]);
        proxy.start();

        McpTestClient review = new McpTestClient(proxy.port(), "/mcp/profiles/review"); //$NON-NLS-1$
        JsonObject init = review.handshake();
        assertEquals("edt-mcp-proxy/review", //$NON-NLS-1$
            init.getAsJsonObject("result").getAsJsonObject("serverInfo").get("name").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

        JsonObject denied = review.callTool("get_server_status", includeProfileToolsOnly()); //$NON-NLS-1$
        assertTrue(isToolError(denied));

        JsonObject compact = review.callTool("get_server_status", includeProfiles()); //$NON-NLS-1$
        assertFalse(isToolError(compact));
        JsonObject structured = structuredContent(compact);
        JsonObject active = structured.getAsJsonObject("activeProfile"); //$NON-NLS-1$
        assertEquals("review", active.get("id").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertTrue(active.has("displayName")); //$NON-NLS-1$
        assertTrue(structured.getAsJsonArray("availableProfiles").size() >= 1); //$NON-NLS-1$
        JsonObject first = structured.getAsJsonArray("availableProfiles").get(0).getAsJsonObject(); //$NON-NLS-1$
        assertTrue(first.has("displayName")); //$NON-NLS-1$
        assertTrue(first.get("endpoint").getAsString().contains(":" + proxy.port())); //$NON-NLS-1$ //$NON-NLS-2$
        assertFalse("compact discovery must omit allowedTools", first.has("allowedTools")); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private static JsonObject includeProfiles()
    {
        JsonObject arguments = new JsonObject();
        arguments.addProperty("includeProfiles", true); //$NON-NLS-1$
        return arguments;
    }

    private static JsonObject includeProfileToolsOnly()
    {
        JsonObject arguments = new JsonObject();
        arguments.addProperty("includeProfileTools", true); //$NON-NLS-1$
        return arguments;
    }
}
