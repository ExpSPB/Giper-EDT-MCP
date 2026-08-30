/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server.profiles;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.Test;

/**
 * Unknown and disabled identifiers fall back to {@code default}; {@code default} itself does not.
 */
public class ProfileResolverTest
{
    @Test
    public void defaultAndLegacyEndpointHaveNoFallback()
    {
        ToolProfileSnapshot snapshot = snapshotWithReview(true);
        ProfileResolution legacy = ProfileResolver.resolveDefault(snapshot);
        assertEquals(ToolProfile.DEFAULT_ID, legacy.getRequestedProfileId());
        assertEquals(ToolProfile.DEFAULT_ID, legacy.getEffectiveProfileId());
        assertTrue(legacy.isLegacyEndpoint());
        assertFalse(legacy.isFallbackApplied());
        assertNull(legacy.getFallbackReason());
    }

    @Test
    public void unknownProfileFallsBackToDefault()
    {
        ProfileResolution resolution = ProfileResolver.resolve("missing", false, snapshotWithReview(true)); //$NON-NLS-1$
        assertEquals("missing", resolution.getRequestedProfileId()); //$NON-NLS-1$
        assertEquals(ToolProfile.DEFAULT_ID, resolution.getEffectiveProfileId());
        assertTrue(resolution.isFallbackApplied());
        assertEquals(FallbackReason.UNKNOWN_PROFILE, resolution.getFallbackReason());
        assertTrue(resolution.isExplicitEndpoint());
    }

    @Test
    public void disabledProfileFallsBackToDefault()
    {
        ProfileResolution resolution = ProfileResolver.resolve("review", false, snapshotWithReview(false)); //$NON-NLS-1$
        assertEquals("review", resolution.getRequestedProfileId()); //$NON-NLS-1$
        assertEquals(ToolProfile.DEFAULT_ID, resolution.getEffectiveProfileId());
        assertEquals(FallbackReason.DISABLED_PROFILE, resolution.getFallbackReason());
    }

    @Test
    public void enabledExplicitProfileIsUsedAsIs()
    {
        ProfileResolution resolution = ProfileResolver.resolve("review", false, snapshotWithReview(true)); //$NON-NLS-1$
        assertEquals("review", resolution.getEffectiveProfileId()); //$NON-NLS-1$
        assertFalse(resolution.isFallbackApplied());
        assertTrue(resolution.isExplicitEndpoint());
    }

    private static ToolProfileSnapshot snapshotWithReview(boolean enabled)
    {
        return ToolProfileSnapshot.of(4L, List.of(
            DefaultToolProfileFactory.createDefault(Set.of("list_projects")), //$NON-NLS-1$
            ToolProfile.builder()
                .id("review") //$NON-NLS-1$
                .displayName("Review") //$NON-NLS-1$
                .enabled(enabled)
                .allowedTools(Set.of("get_server_status")) //$NON-NLS-1$
                .build()));
    }
}
