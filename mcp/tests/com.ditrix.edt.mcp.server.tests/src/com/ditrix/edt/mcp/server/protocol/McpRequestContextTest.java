/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.protocol;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.ditrix.edt.mcp.server.profiles.DefaultToolProfileFactory;
import com.ditrix.edt.mcp.server.profiles.FallbackReason;
import com.ditrix.edt.mcp.server.profiles.ProfileResolver;
import com.ditrix.edt.mcp.server.profiles.ToolProfile;
import com.ditrix.edt.mcp.server.profiles.ToolProfileSnapshot;
import com.ditrix.edt.mcp.server.tools.McpToolRegistry;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Two handler contexts stay isolated; the old wrapper still resolves to {@code default}.
 */
public class McpRequestContextTest
{
    private McpProtocolHandler handler;

    @Before
    public void setUp()
    {
        McpToolRegistry.getInstance().clear();
        handler = new McpProtocolHandler();
    }

    @After
    public void tearDown()
    {
        McpToolRegistry.getInstance().clear();
    }

    @Test
    public void legacyWrapperInitializeHasNoProfileInstructions()
    {
        String response = handler.processRequest(
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\"," //$NON-NLS-1$
            + "\"params\":{\"protocolVersion\":\"2025-11-25\",\"capabilities\":{}}}"); //$NON-NLS-1$
        JsonObject result = JsonParser.parseString(response).getAsJsonObject().getAsJsonObject("result"); //$NON-NLS-1$
        assertEquals(McpConstants.SERVER_NAME, result.getAsJsonObject("serverInfo").get("name").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertFalse(result.has("instructions")); //$NON-NLS-1$
    }

    @Test
    public void explicitFallbackInitializeWarnsAndNamesDefault()
    {
        ToolProfileSnapshot snapshot = ToolProfileSnapshot.of(1L, List.of(
            DefaultToolProfileFactory.createSafeDefault(),
            ToolProfile.builder().id("review").displayName("Review").enabled(false).build())); //$NON-NLS-1$ //$NON-NLS-2$
        McpRequestContext context = McpRequestContext.builder()
            .resolution(ProfileResolver.resolve("review", false, snapshot)) //$NON-NLS-1$
            .requestedPath("/mcp/profiles/review") //$NON-NLS-1$
            .build();

        String response = handler.processRequest(
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\"," //$NON-NLS-1$
            + "\"params\":{\"protocolVersion\":\"2025-11-25\",\"capabilities\":{}}}", //$NON-NLS-1$
            context);
        JsonObject result = JsonParser.parseString(response).getAsJsonObject().getAsJsonObject("result"); //$NON-NLS-1$
        assertEquals(McpConstants.SERVER_NAME + "/default", //$NON-NLS-1$
            result.getAsJsonObject("serverInfo").get("name").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertTrue(result.get("instructions").getAsString().contains(FallbackReason.DISABLED_PROFILE.name())); //$NON-NLS-1$
    }

    @Test
    public void twoContextsDoNotShareCapabilities()
    {
        ClientCapabilities optOut = ClientCapabilities.from(
            JsonParser.parseString("{\"experimental\":{\"structuredContent\":false}}")); //$NON-NLS-1$
        McpRequestContext first = McpRequestContext.builder()
            .clientCapabilities(optOut)
            .build();
        McpRequestContext second = McpRequestContext.legacyDefault();
        assertNotEquals(first.getClientCapabilities().allowsStructuredContent(),
            second.getClientCapabilities().allowsStructuredContent());
        assertTrue(McpRequestContext.legacyDefault().isLegacyCompatibilityWrapper());
        assertEquals("default", McpRequestContext.legacyDefault().effectiveProfileId()); //$NON-NLS-1$
        assertEquals(Set.of(), DefaultToolProfileFactory.createSafeDefault().getAllowedTools());
    }
}
