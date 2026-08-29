/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.profiles;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

import com.ditrix.edt.mcp.server.tools.IMcpTool;

/**
 * Published, callable and guide surfaces share one allowlist.
 */
public class ProfileToolPolicyTest
{
    @Test
    public void statusToolIsAlwaysCallableEvenWhenMissingFromAllowlist()
    {
        ProfileToolPolicy policy = policy(false, Set.of("list_projects")); //$NON-NLS-1$
        assertTrue(policy.isCallable("get_server_status")); //$NON-NLS-1$
        assertTrue(policy.isCallable("list_projects")); //$NON-NLS-1$
        assertFalse(policy.isCallable("write_module_source")); //$NON-NLS-1$
        assertEquals(List.of("get_server_status", "list_projects"), names(policy.publishedTools(false))); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void explicitProfileHidesEnableToolset()
    {
        ProfileToolPolicy policy = policy(false, Set.of("enable_toolset", "list_projects")); //$NON-NLS-1$ //$NON-NLS-2$
        assertFalse(policy.isCallable("enable_toolset")); //$NON-NLS-1$
        assertFalse(names(policy.publishedTools(false)).contains("enable_toolset")); //$NON-NLS-1$
        assertTrue(policy.deniedMessage("enable_toolset").contains("review")); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void legacyEndpointKeepsEnableToolsetWhenAllowed()
    {
        ProfileToolPolicy policy = policy(true, Set.of("enable_toolset", "list_projects")); //$NON-NLS-1$ //$NON-NLS-2$
        assertTrue(policy.isCallable("enable_toolset")); //$NON-NLS-1$
    }

    @Test
    public void guideCannotRevealAForbiddenTool()
    {
        ProfileToolPolicy policy = policy(false, Set.of("list_projects")); //$NON-NLS-1$
        assertTrue(policy.isGuideVisible("list_projects")); //$NON-NLS-1$
        assertFalse(policy.isGuideVisible("write_module_source")); //$NON-NLS-1$
    }

    @Test
    public void sameSnapshotYieldsDeterministicSortedPublishedTools()
    {
        ProfileToolPolicy first = policy(false, Set.of("write_module_source", "list_projects")); //$NON-NLS-1$ //$NON-NLS-2$
        ProfileToolPolicy second = policy(false, Set.of("list_projects", "write_module_source")); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals(names(first.publishedTools(false)), names(second.publishedTools(false)));
        assertEquals("get_server_status", names(first.publishedTools(false)).get(0)); //$NON-NLS-1$
    }

    private static ProfileToolPolicy policy(boolean legacy, Set<String> allowed)
    {
        ToolProfileSnapshot snapshot = ToolProfileSnapshot.of(1L, List.of(
            DefaultToolProfileFactory.createSafeDefault(),
            ToolProfile.builder()
                .id("review") //$NON-NLS-1$
                .displayName("Review") //$NON-NLS-1$
                .allowedTools(allowed)
                .build()));
        ProfileResolution resolution = ProfileResolver.resolve("review", legacy, snapshot); //$NON-NLS-1$
        return new ProfileToolPolicy(resolution, List.of(
            stub("get_server_status"), //$NON-NLS-1$
            stub("list_projects"), //$NON-NLS-1$
            stub("write_module_source"), //$NON-NLS-1$
            stub("enable_toolset"))); //$NON-NLS-1$
    }

    private static List<String> names(List<IMcpTool> tools)
    {
        return tools.stream().map(IMcpTool::getName).toList();
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
}
