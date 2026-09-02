/**
 * MCP Server for EDT - Tests
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server.tools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.After;
import org.junit.Test;

import fm.giper.edt.mcp.server.profiles.DefaultToolProfileFactory;
import fm.giper.edt.mcp.server.profiles.ProfileResolver;
import fm.giper.edt.mcp.server.profiles.ProfileToolPolicy;
import fm.giper.edt.mcp.server.profiles.ToolProfileSnapshot;

/**
 * Published tools with progressive disclosure OFF (the headless default) equal
 * the catalog allowlist. Disclosure-ON filtering is verified by the e2e/live loop.
 */
public class McpToolRegistryVisibilityTest
{
    @After
    public void tearDown()
    {
        McpToolRegistry.getInstance().clear();
        ToolsetState.getInstance().reset();
    }

    @Test
    public void publishedEqualsCatalogWhenDisclosureOff()
    {
        McpToolRegistry registry = McpToolRegistry.getInstance();
        BuiltInToolRegistrar.registerAll(registry);
        ToolsetState.getInstance().enable(Toolsets.DEBUG);

        ProfileToolPolicy policy = allowAllPolicy(registry);
        Set<String> catalog = names(registry.getAllTools());
        Set<String> published = names(policy.publishedTools(false));
        assertEquals("published must equal the catalog when disclosure is off", //$NON-NLS-1$
            catalog, published);
    }

    @Test
    public void metaToolsAreRegisteredAndPublished()
    {
        McpToolRegistry registry = McpToolRegistry.getInstance();
        BuiltInToolRegistrar.registerAll(registry);

        Set<String> published = names(allowAllPolicy(registry).publishedTools(false));
        assertTrue("list_toolsets must be published", published.contains("list_toolsets")); //$NON-NLS-1$ //$NON-NLS-2$
        assertTrue("enable_toolset must be published on /mcp", published.contains("enable_toolset")); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private static ProfileToolPolicy allowAllPolicy(McpToolRegistry registry)
    {
        Set<String> names = names(registry.getAllTools());
        ToolProfileSnapshot snapshot = ToolProfileSnapshot.of(1L,
            List.of(DefaultToolProfileFactory.createDefault(names)));
        return new ProfileToolPolicy(ProfileResolver.resolveDefault(snapshot),
            new ArrayList<>(registry.getAllTools()));
    }

    private static Set<String> names(Iterable<IMcpTool> tools)
    {
        Set<String> result = new HashSet<>();
        for (IMcpTool tool : tools)
        {
            result.add(tool.getName());
        }
        return result;
    }
}
