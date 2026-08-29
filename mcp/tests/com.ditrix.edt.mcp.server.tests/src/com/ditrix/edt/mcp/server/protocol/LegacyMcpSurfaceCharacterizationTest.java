/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.protocol;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.ditrix.edt.mcp.server.SseStreamRegistry;
import com.ditrix.edt.mcp.server.bridge.EdtMcpBridge;
import com.ditrix.edt.mcp.server.tools.IMcpTool;
import com.ditrix.edt.mcp.server.tools.McpToolRegistry;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Characterization of the pre-multi-profile {@code /mcp} surface.
 * These tests pin today's contract so later profile work can tell an intentional
 * change from a regression. They must not alter production behavior.
 */
public class LegacyMcpSurfaceCharacterizationTest
{
    private McpProtocolHandler handler;
    private McpToolRegistry registry;

    @Before
    public void setUp()
    {
        registry = McpToolRegistry.getInstance();
        registry.clear();
        handler = new McpProtocolHandler();
    }

    @After
    public void tearDown()
    {
        registry.clear();
    }

    @Test
    public void initializeAdvertisesSingleServerNameAndToolsListChanged()
    {
        String response = handler.processRequest(
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\"," //$NON-NLS-1$
            + "\"params\":{\"protocolVersion\":\"2025-11-25\",\"capabilities\":{}," //$NON-NLS-1$
            + "\"clientInfo\":{\"name\":\"baseline\",\"version\":\"1.0\"}}}"); //$NON-NLS-1$
        JsonObject result = JsonParser.parseString(response).getAsJsonObject().getAsJsonObject("result"); //$NON-NLS-1$
        assertEquals(McpConstants.PROTOCOL_VERSION, result.get("protocolVersion").getAsString()); //$NON-NLS-1$
        assertEquals(McpConstants.SERVER_NAME, result.getAsJsonObject("serverInfo").get("name").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertTrue(result.getAsJsonObject("capabilities").getAsJsonObject("tools") //$NON-NLS-1$ //$NON-NLS-2$
            .get("listChanged").getAsBoolean()); //$NON-NLS-1$
        assertFalse("baseline initialize has no profile instructions", //$NON-NLS-1$
            result.has("instructions")); //$NON-NLS-1$
    }

    @Test
    public void toolsListAndResourcesListShareTheVisibleCatalogue()
    {
        registry.register(new BaselineTool("alpha_tool")); //$NON-NLS-1$
        registry.register(new BaselineTool("beta_tool")); //$NON-NLS-1$

        JsonArray tools = JsonParser.parseString(handler.processRequest(
            "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}")) //$NON-NLS-1$
            .getAsJsonObject().getAsJsonObject("result").getAsJsonArray("tools"); //$NON-NLS-1$ //$NON-NLS-2$
        JsonArray resources = JsonParser.parseString(handler.processRequest(
            "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"resources/list\"}")) //$NON-NLS-1$
            .getAsJsonObject().getAsJsonObject("result").getAsJsonArray("resources"); //$NON-NLS-1$ //$NON-NLS-2$

        assertEquals(2, tools.size());
        assertEquals(2, resources.size());
        String uris = resources.toString();
        assertTrue(uris.contains("guide://alpha_tool")); //$NON-NLS-1$
        assertTrue(uris.contains("guide://beta_tool")); //$NON-NLS-1$
    }

    @Test
    public void toolsCallUsesTheSameRegistryEnablementAsList()
    {
        registry.register(new BaselineTool("echo_probe")); //$NON-NLS-1$
        String response = handler.processRequest(
            "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\"," //$NON-NLS-1$
            + "\"params\":{\"name\":\"echo_probe\",\"arguments\":{}}}"); //$NON-NLS-1$
        JsonObject json = JsonParser.parseString(response).getAsJsonObject();
        assertTrue(json.has("result") || json.has("error")); //$NON-NLS-1$ //$NON-NLS-2$
        assertTrue("registered tool must not be reported as unknown", //$NON-NLS-1$
            json.has("result") || !json.getAsJsonObject("error").get("message").getAsString() //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .contains("Unknown tool")); //$NON-NLS-1$
    }

    @Test
    public void bridgeListToolsReturnsEveryRegisteredToolNotOnlyEnabled()
    {
        registry.register(new BaselineTool("bridge_listed")); //$NON-NLS-1$
        String json = new EdtMcpBridge().listTools();
        JsonArray tools = JsonParser.parseString(json).getAsJsonArray();
        assertEquals(1, tools.size());
        assertEquals("bridge_listed", tools.get(0).getAsJsonObject().get("name").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void sessionHeaderNameIsTheMcpSessionIdConstant()
    {
        assertEquals("MCP-Session-Id", McpConstants.HEADER_SESSION_ID); //$NON-NLS-1$
    }

    @Test
    public void sseListChangedUsesEventMessageFraming()
    {
        SseStreamRegistry reg = SseStreamRegistry.getInstance();
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        SseStreamRegistry.SseStream stream = reg.register(sink);
        try
        {
            assertTrue(reg.notifyToolsListChanged() >= 1);
            String frame = sink.toString(StandardCharsets.UTF_8);
            assertTrue(frame.startsWith("event: message\n")); //$NON-NLS-1$
            assertTrue(frame.contains("data: {\"jsonrpc\":\"2.0\",\"method\":\"notifications/tools/list_changed\"}")); //$NON-NLS-1$
            assertTrue(frame.endsWith("\n\n")); //$NON-NLS-1$
        }
        finally
        {
            reg.unregister(stream);
        }
    }

    private static final class BaselineTool implements IMcpTool
    {
        private final String name;

        BaselineTool(String name)
        {
            this.name = name;
        }

        @Override
        public String getName()
        {
            return name;
        }

        @Override
        public String getDescription()
        {
            return "characterization stub"; //$NON-NLS-1$
        }

        @Override
        public String getInputSchema()
        {
            return "{\"type\":\"object\"}"; //$NON-NLS-1$
        }

        @Override
        public String execute(Map<String, String> params)
        {
            return "{\"success\":true}"; //$NON-NLS-1$
        }
    }
}
