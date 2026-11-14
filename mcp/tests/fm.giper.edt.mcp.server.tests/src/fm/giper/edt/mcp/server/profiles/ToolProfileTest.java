/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server.profiles;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.Test;

/**
 * Canonicalisation and content equality for {@link ToolProfile}.
 */
public class ToolProfileTest
{
    @Test
    public void allowedToolsAreSortedAndDeduplicated()
    {
        ToolProfile profile = ToolProfile.builder()
            .id("review") //$NON-NLS-1$
            .displayName("Review") //$NON-NLS-1$
            .allowedTools(Arrays.asList("write_module_source", "get_server_status", "get_server_status", " ", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                null, "list_projects")) //$NON-NLS-1$
            .build();

        assertEquals(List.of("get_server_status", "list_projects", "write_module_source"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            List.copyOf(profile.getAllowedTools()));
    }

    @Test
    public void unknownToolNamesAreKept()
    {
        ToolProfile profile = ToolProfile.builder()
            .id("default") //$NON-NLS-1$
            .displayName("Default") //$NON-NLS-1$
            .allowedTools(Set.of("retired_tool", "list_projects")) //$NON-NLS-1$ //$NON-NLS-2$
            .build();

        assertTrue(profile.getAllowedTools().contains("retired_tool")); //$NON-NLS-1$
    }

    @Test
    public void sameContentIgnoresRevision()
    {
        ToolProfile first = ToolProfile.builder()
            .id("default") //$NON-NLS-1$
            .displayName("Default") //$NON-NLS-1$
            .allowedTools(Set.of("list_projects")) //$NON-NLS-1$
            .revision(1L)
            .build();
        ToolProfile second = first.withRevision(9L);

        assertTrue(first.sameContent(second));
        assertFalse(first.equals(second));
    }

    @Test
    public void snapshotKeepsProfilesSortedById()
    {
        ToolProfileSnapshot snapshot = ToolProfileSnapshot.of(2L, List.of(
            profile("zeta"), //$NON-NLS-1$
            DefaultToolProfileFactory.createSafeDefault(),
            profile("alpha"))); //$NON-NLS-1$

        assertEquals(List.of("alpha", "default", "zeta"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            snapshot.asList().stream().map(ToolProfile::getId).toList());
        assertEquals(2L, snapshot.getDocumentRevision());
        assertTrue(snapshot.getDefault().isDefault());
    }

    private static ToolProfile profile(String id)
    {
        return ToolProfile.builder().id(id).displayName(id).allowedTools(Set.of()).revision(1L).build();
    }
}
