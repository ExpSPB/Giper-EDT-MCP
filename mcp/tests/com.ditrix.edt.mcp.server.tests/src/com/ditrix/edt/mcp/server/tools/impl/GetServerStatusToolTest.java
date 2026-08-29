/**
 * MCP Server for EDT - Tests
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.tools.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

import com.ditrix.edt.mcp.server.preferences.PreferenceConstants;
import com.ditrix.edt.mcp.server.profiles.DefaultToolProfileFactory;
import com.ditrix.edt.mcp.server.profiles.FallbackReason;
import com.ditrix.edt.mcp.server.profiles.ProfileResolver;
import com.ditrix.edt.mcp.server.profiles.ToolProfile;
import com.ditrix.edt.mcp.server.profiles.ToolProfileSnapshot;
import com.ditrix.edt.mcp.server.protocol.McpRequestContext;
import com.ditrix.edt.mcp.server.tools.IMcpTool;
import com.ditrix.edt.mcp.server.tools.IMcpTool.ResponseType;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Tests for {@link GetServerStatusTool}.
 * <p>
 * Headless surface: null-safe {@code execute()} plus profile discovery. Live
 * port / EDT version / auth are covered by the E2E suite.
 */
public class GetServerStatusToolTest
{
    @Test
    public void testName()
    {
        assertEquals("get_server_status", new GetServerStatusTool().getName()); //$NON-NLS-1$
    }

    @Test
    public void testNameConstant()
    {
        assertEquals(GetServerStatusTool.NAME, new GetServerStatusTool().getName());
    }

    @Test
    public void testResponseTypeJson()
    {
        assertEquals(ResponseType.JSON, new GetServerStatusTool().getResponseType());
    }

    @Test
    public void testDescriptionNotEmpty()
    {
        String desc = new GetServerStatusTool().getDescription();
        assertNotNull(desc);
        assertTrue(desc.length() > 0);
    }

    @Test
    public void testSchemaDeclaresProfileDiscoveryParameters()
    {
        String schema = new GetServerStatusTool().getInputSchema();
        assertNotNull(schema);
        assertTrue(schema.contains("includeProfiles")); //$NON-NLS-1$
        assertTrue(schema.contains("includeProfileTools")); //$NON-NLS-1$
    }

    @Test
    public void testExecuteHeadlessReturnsSuccessJson()
    {
        String json = new GetServerStatusTool().execute(java.util.Collections.emptyMap());
        assertNotNull(json);
        assertTrue(json.contains("\"success\":true")); //$NON-NLS-1$
        assertTrue(json.contains("protocolVersion")); //$NON-NLS-1$
        assertTrue(json.contains("pluginVersion")); //$NON-NLS-1$
        assertTrue(json.contains("totalTools")); //$NON-NLS-1$
        assertTrue(json.contains("enabledTools")); //$NON-NLS-1$
        assertTrue(json.contains("plainTextMode")); //$NON-NLS-1$
        assertTrue(json.contains("checksFolderConfigured")); //$NON-NLS-1$
        assertTrue(json.contains("authEnabled")); //$NON-NLS-1$
        assertTrue(json.contains("formRenderFlags")); //$NON-NLS-1$
        assertTrue(json.contains("activeProfile")); //$NON-NLS-1$
    }

    @Test
    public void testExecuteNeverLeaksChecksFolderPathOrToken()
    {
        String json = new GetServerStatusTool().execute(java.util.Collections.emptyMap());
        assertNotNull(json);
        assertFalse(json.contains("authToken")); //$NON-NLS-1$
        assertFalse(json.contains("checksFolder\"")); //$NON-NLS-1$
    }

    @Test
    public void testDescriptionNamesKeyDiagnostics()
    {
        String desc = new GetServerStatusTool().getDescription();
        assertTrue(new GetServerStatusTool().getGuide().contains("nativeFormBufferedLayoutRender")); //$NON-NLS-1$
        assertTrue(new GetServerStatusTool().getGuide().contains("nativeFormLayoutRender")); //$NON-NLS-1$
        assertTrue(new GetServerStatusTool().getGuide().toLowerCase().contains("plaintextmode")); //$NON-NLS-1$
        assertTrue(desc.contains("includeProfileTools")); //$NON-NLS-1$
    }

    @Test
    public void testOutputSchemaDeclaresAllFields()
    {
        String schema = new GetServerStatusTool().getOutputSchema();
        assertNotNull(schema);
        assertTrue(schema.contains("\"success\"")); //$NON-NLS-1$
        assertTrue(schema.contains("\"port\"")); //$NON-NLS-1$
        assertTrue(schema.contains("\"running\"")); //$NON-NLS-1$
        assertTrue(schema.contains("\"protocolVersion\"")); //$NON-NLS-1$
        assertTrue(schema.contains("\"pluginVersion\"")); //$NON-NLS-1$
        assertTrue(schema.contains("\"edtVersion\"")); //$NON-NLS-1$
        assertTrue(schema.contains("\"enabledTools\"")); //$NON-NLS-1$
        assertTrue(schema.contains("\"totalTools\"")); //$NON-NLS-1$
        assertTrue(schema.contains("\"plainTextMode\"")); //$NON-NLS-1$
        assertTrue(schema.contains("\"checksFolderConfigured\"")); //$NON-NLS-1$
        assertTrue(schema.contains("\"authEnabled\"")); //$NON-NLS-1$
        assertTrue(schema.contains("\"formRenderFlags\"")); //$NON-NLS-1$
        assertTrue(schema.contains("\"activeProfile\"")); //$NON-NLS-1$
        assertTrue(schema.contains("\"availableProfiles\"")); //$NON-NLS-1$
    }

    @Test
    public void testExecuteIsWellFormedJsonObject()
    {
        String json = new GetServerStatusTool().execute(java.util.Collections.emptyMap());
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        assertTrue("success must be true", obj.get("success").getAsBoolean()); //$NON-NLS-1$ //$NON-NLS-2$
        assertTrue("port must be a number", obj.get("port").getAsJsonPrimitive().isNumber()); //$NON-NLS-1$ //$NON-NLS-2$
        assertTrue("formRenderFlags must be an object", //$NON-NLS-1$
            obj.get("formRenderFlags").isJsonObject()); //$NON-NLS-1$
        assertTrue("activeProfile must be an object", obj.get("activeProfile").isJsonObject()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testHeadlessPortAndRunningDegradeToDefaults()
    {
        String json = new GetServerStatusTool().execute(java.util.Collections.emptyMap());
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        assertTrue("port must be a non-negative int", obj.get("port").getAsInt() >= 0); //$NON-NLS-1$ //$NON-NLS-2$
        assertFalse("no live server => running must be false", obj.get("running").getAsBoolean()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testFormRenderFlagsReflectSystemProperties()
    {
        String bufferedKey = "nativeFormBufferedLayoutRender"; //$NON-NLS-1$
        String nativeKey = "nativeFormLayoutRender"; //$NON-NLS-1$
        String savedBuffered = System.getProperty(bufferedKey);
        String savedNative = System.getProperty(nativeKey);
        try
        {
            System.setProperty(bufferedKey, "true"); //$NON-NLS-1$
            System.clearProperty(nativeKey);

            JsonObject flags = JsonParser
                .parseString(new GetServerStatusTool().execute(java.util.Collections.emptyMap()))
                .getAsJsonObject().getAsJsonObject("formRenderFlags"); //$NON-NLS-1$
            assertTrue("buffered flag set => true", flags.get(bufferedKey).getAsBoolean()); //$NON-NLS-1$
            assertFalse("native flag absent => false", flags.get(nativeKey).getAsBoolean()); //$NON-NLS-1$
        }
        finally
        {
            restoreProperty(bufferedKey, savedBuffered);
            restoreProperty(nativeKey, savedNative);
        }
    }

    @Test
    public void existingProfileReportsRequestedAndEffectiveMatch()
    {
        GetServerStatusTool tool = wiredTool(snapshot(true));
        JsonObject active = executeActive(tool, context("review", false, snapshot(true)), compactParams()); //$NON-NLS-1$
        assertEquals("review", active.get("requestedProfileId").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("review", active.get("id").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("Review", active.get("displayName").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertFalse(active.get("fallbackApplied").getAsBoolean()); //$NON-NLS-1$
        assertFalse(active.has("fallbackReason")); //$NON-NLS-1$
        assertFalse(active.has("warning")); //$NON-NLS-1$
        assertEquals("http://127.0.0.1:" + PreferenceConstants.DEFAULT_PORT + "/mcp/profiles/review", //$NON-NLS-1$ //$NON-NLS-2$
            active.get("endpoint").getAsString()); //$NON-NLS-1$
        assertEquals(2, active.get("allowedToolCount").getAsInt()); //$NON-NLS-1$
        assertFalse("compact call must not list availableProfiles", //$NON-NLS-1$
            JsonParser.parseString(tool.execute(compactParams(), context("review", false, snapshot(true)))) //$NON-NLS-1$
                .getAsJsonObject().has("availableProfiles")); //$NON-NLS-1$
    }

    @Test
    public void unknownProfileFallsBackAndKeepsRequestedId()
    {
        ToolProfileSnapshot snap = snapshot(true);
        GetServerStatusTool tool = wiredTool(snap);
        JsonObject active = executeActive(tool, context("missing", false, snap), compactParams()); //$NON-NLS-1$
        assertEquals("missing", active.get("requestedProfileId").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals(ToolProfile.DEFAULT_ID, active.get("id").getAsString()); //$NON-NLS-1$
        assertTrue(active.get("fallbackApplied").getAsBoolean()); //$NON-NLS-1$
        assertEquals(FallbackReason.UNKNOWN_PROFILE.name(), active.get("fallbackReason").getAsString()); //$NON-NLS-1$
        assertTrue(active.get("warning").getAsString().contains("missing")); //$NON-NLS-1$ //$NON-NLS-2$
        assertTrue(active.get("endpoint").getAsString().endsWith("/mcp")); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void disabledProfileFallsBackAndIsOmittedFromAvailableProfiles()
    {
        ToolProfileSnapshot snap = snapshot(false);
        GetServerStatusTool tool = wiredTool(snap);
        McpRequestContext ctx = context("review", false, snap); //$NON-NLS-1$
        JsonObject active = executeActive(tool, ctx, compactParams());
        assertEquals("review", active.get("requestedProfileId").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals(ToolProfile.DEFAULT_ID, active.get("id").getAsString()); //$NON-NLS-1$
        assertEquals(FallbackReason.DISABLED_PROFILE.name(), active.get("fallbackReason").getAsString()); //$NON-NLS-1$

        JsonArray available = executeRoot(tool, ctx, discoveryParams(false)).getAsJsonArray("availableProfiles"); //$NON-NLS-1$
        assertEquals(1, available.size());
        assertEquals(ToolProfile.DEFAULT_ID, available.get(0).getAsJsonObject().get("id").getAsString()); //$NON-NLS-1$
    }

    @Test
    public void compactDiscoveryOmitsAllowlistsAndFullDiscoverySortsTools()
    {
        ToolProfileSnapshot snap = snapshotWithWriter();
        GetServerStatusTool tool = wiredTool(snap);
        McpRequestContext ctx = context("review", false, snap); //$NON-NLS-1$

        JsonArray compact = executeRoot(tool, ctx, discoveryParams(false)).getAsJsonArray("availableProfiles"); //$NON-NLS-1$
        assertEquals(3, compact.size());
        assertEquals(ToolProfile.DEFAULT_ID, compact.get(0).getAsJsonObject().get("id").getAsString()); //$NON-NLS-1$
        assertEquals("review", compact.get(1).getAsJsonObject().get("id").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("writer", compact.get(2).getAsJsonObject().get("id").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertFalse(compact.get(0).getAsJsonObject().has("allowedTools")); //$NON-NLS-1$

        JsonArray full = executeRoot(tool, ctx, discoveryParams(true)).getAsJsonArray("availableProfiles"); //$NON-NLS-1$
        JsonArray reviewTools = full.get(1).getAsJsonObject().getAsJsonArray("allowedTools"); //$NON-NLS-1$
        assertEquals(2, reviewTools.size());
        assertEquals("get_server_status", reviewTools.get(0).getAsString()); //$NON-NLS-1$
        assertEquals("list_projects", reviewTools.get(1).getAsString()); //$NON-NLS-1$
        assertEquals(reviewTools.size(), full.get(1).getAsJsonObject().get("allowedToolCount").getAsInt()); //$NON-NLS-1$
        assertTrue("status tool is part of the published surface", //$NON-NLS-1$
            reviewTools.toString().contains("get_server_status")); //$NON-NLS-1$
    }

    @Test
    public void includeProfileToolsWithoutIncludeProfilesIsRejected()
    {
        GetServerStatusTool tool = wiredTool(snapshot(true));
        Map<String, String> params = new HashMap<>();
        params.put("includeProfileTools", "true"); //$NON-NLS-1$ //$NON-NLS-2$
        JsonObject obj = JsonParser.parseString(tool.execute(params, context("review", false, snapshot(true)))) //$NON-NLS-1$
            .getAsJsonObject();
        assertFalse(obj.get("success").getAsBoolean()); //$NON-NLS-1$
        assertTrue(obj.get("error").getAsString().contains("includeProfiles")); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void statusDoesNotChangeRightsOrSwitchContext()
    {
        ToolProfileSnapshot snap = snapshotWithWriter();
        GetServerStatusTool tool = wiredTool(snap);
        McpRequestContext ctx = context("review", false, snap); //$NON-NLS-1$

        JsonObject before = executeActive(tool, ctx, compactParams());
        int reviewCount = before.get("allowedToolCount").getAsInt(); //$NON-NLS-1$
        assertEquals("review", before.get("id").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$

        JsonObject discovered = executeRoot(tool, ctx, discoveryParams(true));
        assertEquals("review", discovered.getAsJsonObject("activeProfile").get("id").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        JsonArray available = discovered.getAsJsonArray("availableProfiles"); //$NON-NLS-1$
        int writerCount = -1;
        for (int i = 0; i < available.size(); i++)
        {
            JsonObject row = available.get(i).getAsJsonObject();
            if ("writer".equals(row.get("id").getAsString())) //$NON-NLS-1$ //$NON-NLS-2$
            {
                writerCount = row.get("allowedToolCount").getAsInt(); //$NON-NLS-1$
            }
        }
        assertTrue("writer surface must be listed", writerCount > reviewCount); //$NON-NLS-1$

        JsonObject after = executeActive(tool, ctx, compactParams());
        assertEquals(reviewCount, after.get("allowedToolCount").getAsInt()); //$NON-NLS-1$
        assertEquals("review", after.get("id").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals(ctx.effectiveProfileId(), after.get("id").getAsString()); //$NON-NLS-1$
        assertEquals("review", ctx.effectiveProfileId()); //$NON-NLS-1$
    }

    @Test
    public void defaultLegacyEndpointUsesMcpPath()
    {
        ToolProfileSnapshot snap = snapshot(true);
        GetServerStatusTool tool = wiredTool(snap);
        JsonObject active = executeActive(tool, McpRequestContext.builder()
            .resolution(ProfileResolver.resolveDefault(snap))
            .requestedPath("/mcp") //$NON-NLS-1$
            .build(), compactParams());
        assertEquals(ToolProfile.DEFAULT_ID, active.get("requestedProfileId").getAsString()); //$NON-NLS-1$
        assertEquals(ToolProfile.DEFAULT_ID, active.get("id").getAsString()); //$NON-NLS-1$
        assertFalse(active.get("fallbackApplied").getAsBoolean()); //$NON-NLS-1$
        assertEquals("http://127.0.0.1:" + PreferenceConstants.DEFAULT_PORT + "/mcp", //$NON-NLS-1$ //$NON-NLS-2$
            active.get("endpoint").getAsString()); //$NON-NLS-1$
    }

    private static GetServerStatusTool wiredTool(ToolProfileSnapshot snapshot)
    {
        GetServerStatusTool tool = new GetServerStatusTool();
        tool.setSnapshotForTests(snapshot);
        tool.setCatalogForTests(List.of(
            stub("get_server_status"), //$NON-NLS-1$
            stub("list_projects"), //$NON-NLS-1$
            stub("write_module_source"), //$NON-NLS-1$
            stub("enable_toolset"))); //$NON-NLS-1$
        return tool;
    }

    private static ToolProfileSnapshot snapshot(boolean reviewEnabled)
    {
        return ToolProfileSnapshot.of(4L, List.of(
            DefaultToolProfileFactory.createDefault(Set.of("list_projects")), //$NON-NLS-1$
            ToolProfile.builder()
                .id("review") //$NON-NLS-1$
                .displayName("Review") //$NON-NLS-1$
                .description("Read-only review surface") //$NON-NLS-1$
                .enabled(reviewEnabled)
                .allowedTools(Set.of("list_projects")) //$NON-NLS-1$
                .revision(2L)
                .build()));
    }

    private static ToolProfileSnapshot snapshotWithWriter()
    {
        return ToolProfileSnapshot.of(5L, List.of(
            DefaultToolProfileFactory.createDefault(Set.of("list_projects")), //$NON-NLS-1$
            ToolProfile.builder()
                .id("writer") //$NON-NLS-1$
                .displayName("Writer") //$NON-NLS-1$
                .enabled(true)
                .allowedTools(Set.of("write_module_source", "list_projects")) //$NON-NLS-1$ //$NON-NLS-2$
                .build(),
            ToolProfile.builder()
                .id("review") //$NON-NLS-1$
                .displayName("Review") //$NON-NLS-1$
                .enabled(true)
                .allowedTools(Set.of("list_projects")) //$NON-NLS-1$
                .build()));
    }

    private static McpRequestContext context(String requestedId, boolean legacy, ToolProfileSnapshot snapshot)
    {
        return McpRequestContext.builder()
            .resolution(ProfileResolver.resolve(requestedId, legacy, snapshot))
            .requestedPath(legacy ? "/mcp" : "/mcp/profiles/" + requestedId) //$NON-NLS-1$ //$NON-NLS-2$
            .build();
    }

    private static Map<String, String> compactParams()
    {
        return new HashMap<>();
    }

    private static Map<String, String> discoveryParams(boolean includeTools)
    {
        Map<String, String> params = new HashMap<>();
        params.put("includeProfiles", "true"); //$NON-NLS-1$ //$NON-NLS-2$
        if (includeTools)
        {
            params.put("includeProfileTools", "true"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        return params;
    }

    private static JsonObject executeRoot(GetServerStatusTool tool, McpRequestContext context,
        Map<String, String> params)
    {
        JsonObject obj = JsonParser.parseString(tool.execute(params, context)).getAsJsonObject();
        if (!obj.get("success").getAsBoolean()) //$NON-NLS-1$
        {
            fail("expected success, got: " + obj); //$NON-NLS-1$
        }
        return obj;
    }

    private static JsonObject executeActive(GetServerStatusTool tool, McpRequestContext context,
        Map<String, String> params)
    {
        return executeRoot(tool, context, params).getAsJsonObject("activeProfile"); //$NON-NLS-1$
    }

    private static IMcpTool stub(String name)
    {
        return new IMcpTool()
        {
            @Override
            public String getName()
            {
                return name;
            }

            @Override
            public String getDescription()
            {
                return name;
            }

            @Override
            public String getInputSchema()
            {
                return "{\"type\":\"object\"}"; //$NON-NLS-1$
            }

            @Override
            public String execute(Map<String, String> params)
            {
                return "{}"; //$NON-NLS-1$
            }
        };
    }

    private static void restoreProperty(String key, String saved)
    {
        if (saved == null)
        {
            System.clearProperty(key);
        }
        else
        {
            System.setProperty(key, saved);
        }
    }
}
