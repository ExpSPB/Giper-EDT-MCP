/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.proxy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.Test;

/** Pure generation tests: synthetic registry states require no HTTP client or loopback socket. */
public class BackendRegistryGenerationTest
{
    @Test
    public void testRoutingViewKeepsOwnerDuplicatesAndGroupInOneGeneration()
    {
        BackendRegistry registry = registry();
        Backend oldOwner = new Backend(8765, null, 5);
        Backend duplicate = new Backend(8766, null, 5);
        Backend newOwner = new Backend(8767, null, 5);
        ProfileEndpoint endpoint = ProfileEndpoint.legacyDefault();
        registry.installStateForTest(List.of(oldOwner, duplicate),
            Map.of("Project", List.of(oldOwner, duplicate))); //$NON-NLS-1$

        BackendRegistry.RoutingView old = registry.routingView(endpoint);
        registry.installStateForTest(List.of(newOwner), Map.of("Project", List.of(newOwner))); //$NON-NLS-1$
        BackendRegistry.RoutingView current = registry.routingView(endpoint);

        assertEquals(List.of(8765, 8766), old.duplicatePorts("Project")); //$NON-NLS-1$
        assertSame(oldOwner, old.owner("Project")); //$NON-NLS-1$
        assertTrue(old.group().contains(oldOwner));
        assertSame(newOwner, current.owner("Project")); //$NON-NLS-1$
        assertTrue(current.group().contains(newOwner));
        assertTrue(current.generation() > old.generation());
    }

    @Test
    public void testBatchProfileGroupsShareOneGeneration()
    {
        BackendRegistry registry = registry();
        Backend backend = new Backend(8765, null, 5);
        registry.installStateForTest(List.of(backend), Map.of());
        ProfileEndpoint review = new ProfileEndpoint("/mcp/profiles/review", "review", false); //$NON-NLS-1$ //$NON-NLS-2$
        ProfileEndpoint prod = new ProfileEndpoint("/mcp/profiles/prod", "prod", false); //$NON-NLS-1$ //$NON-NLS-2$

        long generation = registry.routingView(review).generation();
        Map<String, ProfileGroupSnapshot> groups = registry.groupsForGeneration(generation, List.of(review, prod));

        assertEquals(groups.get(review.canonicalPath()).getGeneration(),
            groups.get(prod.canonicalPath()).getGeneration());
    }

    @Test
    public void testBatchProfileGroupsRejectAStaleSourceGeneration()
    {
        BackendRegistry registry = registry();
        Backend oldBackend = new Backend(8765, null, 5);
        Backend newBackend = new Backend(8766, null, 5);
        ProfileEndpoint review = new ProfileEndpoint("/mcp/profiles/review", "review", false); //$NON-NLS-1$ //$NON-NLS-2$
        registry.installStateForTest(List.of(oldBackend), Map.of());
        long staleGeneration = registry.routingView(review).generation();
        registry.installStateForTest(List.of(newBackend), Map.of());

        org.junit.Assert.assertNull(registry.groupsForGeneration(staleGeneration, List.of(review)));
    }

    private static BackendRegistry registry()
    {
        return new BackendRegistry(ProxyConfig.parse(new String[0], Map.of()), null);
    }
}
