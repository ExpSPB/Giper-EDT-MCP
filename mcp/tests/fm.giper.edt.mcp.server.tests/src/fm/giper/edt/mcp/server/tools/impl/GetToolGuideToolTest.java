/**
 * MCP Server for EDT - Tests
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server.tools.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.After;
import org.junit.Test;

import fm.giper.edt.mcp.server.protocol.McpRequestContext;
import fm.giper.edt.mcp.server.profiles.DefaultToolProfileFactory;
import fm.giper.edt.mcp.server.profiles.ProfileResolver;
import fm.giper.edt.mcp.server.profiles.ToolProfile;
import fm.giper.edt.mcp.server.profiles.ToolProfileSnapshot;
import fm.giper.edt.mcp.server.tools.BuiltInToolRegistrar;
import fm.giper.edt.mcp.server.tools.IMcpTool;
import fm.giper.edt.mcp.server.tools.IMcpTool.ResponseType;
import fm.giper.edt.mcp.server.tools.McpToolRegistry;

/**
 * Tests for {@link GetToolGuideTool}.
 * <p>
 * The static contract (name, response type, schema declares {@code toolName}) is
 * headless. The behavioural surface needs the registry: registering the built-in
 * tools lets {@code execute()} resolve a real tool and render its guide; an
 * unknown name must yield a clear, actionable error that names the bad value. The
 * registry is cleared after each test so the singleton does not leak across the
 * suite.
 */
public class GetToolGuideToolTest
{
    @After
    public void tearDown()
    {
        McpToolRegistry.getInstance().clear();
    }

    @Test
    public void testName()
    {
        assertEquals("get_tool_guide", new GetToolGuideTool().getName()); //$NON-NLS-1$
    }

    @Test
    public void testNameConstant()
    {
        assertEquals(GetToolGuideTool.NAME, new GetToolGuideTool().getName());
    }

    @Test
    public void testResponseTypeMarkdown()
    {
        assertEquals(ResponseType.MARKDOWN, new GetToolGuideTool().getResponseType());
    }

    @Test
    public void testDescriptionNotEmpty()
    {
        String desc = new GetToolGuideTool().getDescription();
        assertNotNull(desc);
        assertTrue(desc.length() > 0);
    }

    @Test
    public void testSchemaDeclaresToolName()
    {
        String schema = new GetToolGuideTool().getInputSchema();
        assertNotNull(schema);
        assertTrue(schema.contains("toolName")); //$NON-NLS-1$
        // toolName is required.
        assertTrue(schema.contains("\"required\":[\"toolName\"]")); //$NON-NLS-1$
    }

    @Test
    public void testResultFileNameUsesToolName()
    {
        Map<String, String> params = new HashMap<>();
        params.put("toolName", "read_module_source"); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("guide-read_module_source.md", new GetToolGuideTool().getResultFileName(params)); //$NON-NLS-1$
    }

    @Test
    public void testMissingToolNameErrors()
    {
        String json = new GetToolGuideTool().execute(Collections.emptyMap());
        assertNotNull(json);
        assertTrue(json.contains("\"success\":false")); //$NON-NLS-1$
        assertTrue(json.contains("toolName is required")); //$NON-NLS-1$
    }

    @Test
    public void testUnknownToolErrorNamesBadValue()
    {
        // No registry population needed: an unknown name resolves to null regardless.
        Map<String, String> params = new HashMap<>();
        String bad = "no_such_tool_zzz"; //$NON-NLS-1$
        params.put("toolName", bad); //$NON-NLS-1$
        String json = new GetToolGuideTool().execute(params);
        assertNotNull(json);
        assertTrue(json.contains("\"success\":false")); //$NON-NLS-1$
        // The error must name the bad value and the "Unknown tool" anchor.
        assertTrue(json.contains("Unknown tool")); //$NON-NLS-1$
        assertTrue(json.contains(bad));
    }

    @Test
    public void testRendersGuideForKnownTool()
    {
        // Populate the singleton registry with the real built-in tools so the
        // lookup resolves a registered tool and renders its guide.
        BuiltInToolRegistrar.registerAll(McpToolRegistry.getInstance());

        Map<String, String> params = new HashMap<>();
        params.put("toolName", "read_module_source"); //$NON-NLS-1$ //$NON-NLS-2$
        String md = new GetToolGuideTool().execute(params);
        assertNotNull(md);
        // The rendered guide is the tool's name title plus the parameter section.
        assertTrue(md.contains("# read_module_source")); //$NON-NLS-1$
        assertTrue(md.contains("Parameters")); //$NON-NLS-1$
    }

    @Test
    public void testRendersGuideForItself()
    {
        BuiltInToolRegistrar.registerAll(McpToolRegistry.getInstance());

        Map<String, String> params = new HashMap<>();
        params.put("toolName", GetToolGuideTool.NAME); //$NON-NLS-1$
        String md = new GetToolGuideTool().execute(params);
        assertNotNull(md);
        assertTrue(md.contains("# get_tool_guide")); //$NON-NLS-1$
        // Its own required toolName parameter must appear in the rendered table.
        assertTrue(md.contains("toolName")); //$NON-NLS-1$
    }

    @Test
    public void testDoesNotRevealGuideForAToolForbiddenByTheCurrentProfile()
    {
        McpToolRegistry.getInstance().register(new NamedGuideProbe("list_projects")); //$NON-NLS-1$
        McpToolRegistry.getInstance().register(new NamedGuideProbe("write_module_source")); //$NON-NLS-1$

        McpRequestContext review = McpRequestContext.builder()
            .resolution(ProfileResolver.resolve("review", false, ToolProfileSnapshot.of(1L, List.of( //$NON-NLS-1$
                DefaultToolProfileFactory.createSafeDefault(),
                ToolProfile.builder()
                    .id("review") //$NON-NLS-1$
                    .displayName("Review") //$NON-NLS-1$
                    .allowedTools(Set.of("list_projects")) //$NON-NLS-1$
                    .build()))))
            .requestedPath("/mcp/review") //$NON-NLS-1$
            .legacyCompatibilityWrapper(false)
            .build();

        Map<String, String> forbidden = new HashMap<>();
        forbidden.put("toolName", "write_module_source"); //$NON-NLS-1$ //$NON-NLS-2$
        String denied = new GetToolGuideTool().execute(forbidden, review);
        assertTrue(denied.contains("\"success\":false")); //$NON-NLS-1$
        assertTrue(denied.contains("write_module_source")); //$NON-NLS-1$
        assertTrue(denied.contains("review")); //$NON-NLS-1$
        assertFalse(denied.contains("# write_module_source")); //$NON-NLS-1$

        Map<String, String> allowed = new HashMap<>();
        allowed.put("toolName", "list_projects"); //$NON-NLS-1$ //$NON-NLS-2$
        String ok = new GetToolGuideTool().execute(allowed, review);
        assertTrue(ok.contains("# list_projects")); //$NON-NLS-1$
    }

    private static final class NamedGuideProbe implements IMcpTool
    {
        private final String name;

        private NamedGuideProbe(String name)
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
            return "Guide probe"; //$NON-NLS-1$
        }

        @Override
        public String getInputSchema()
        {
            return "{\"type\":\"object\",\"properties\":{}}"; //$NON-NLS-1$
        }

        @Override
        public String execute(Map<String, String> params)
        {
            return ""; //$NON-NLS-1$
        }

        @Override
        public ResponseType getResponseType()
        {
            return ResponseType.MARKDOWN;
        }
    }
}
