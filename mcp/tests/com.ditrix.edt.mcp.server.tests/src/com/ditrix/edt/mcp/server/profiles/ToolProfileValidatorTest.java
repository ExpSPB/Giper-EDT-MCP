/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.profiles;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.Test;

/**
 * ID, uniqueness and {@code default} constraints.
 */
public class ToolProfileValidatorTest
{
    @Test
    public void validDefaultSnapshotPasses()
    {
        assertTrue(ToolProfileValidator.validate(DefaultToolProfileFactory.createSafeSnapshot()).isValid());
    }

    @Test
    public void rejectsInvalidId()
    {
        ToolProfile profile = ToolProfile.builder()
            .id("Reviewer") //$NON-NLS-1$
            .displayName("Reviewer") //$NON-NLS-1$
            .build();
        assertFalse(ToolProfileValidator.validate(profile).isValid());
    }

    @Test
    public void rejectsEmptyDisplayName()
    {
        ToolProfile profile = ToolProfile.builder()
            .id("review") //$NON-NLS-1$
            .displayName("  ") //$NON-NLS-1$
            .build();
        assertFalse(ToolProfileValidator.validate(profile).isValid());
    }

    @Test
    public void defaultCannotBeDisabled()
    {
        ToolProfile profile = DefaultToolProfileFactory.createSafeDefault().toBuilder().enabled(false).build();
        assertFalse(ToolProfileValidator.validate(profile).isValid());
    }

    @Test
    public void snapshotMustContainDefault()
    {
        ToolProfile review = ToolProfile.builder()
            .id("review") //$NON-NLS-1$
            .displayName("Review") //$NON-NLS-1$
            .build();
        assertFalse(ToolProfileValidator.validate(ToolProfileSnapshot.of(1L, List.of(review))).isValid());
    }

    @Test
    public void acceptsReservedDefaultId()
    {
        assertTrue(ToolProfileValidator.validate(DefaultToolProfileFactory.createSafeDefault()).isValid());
        assertTrue(ToolProfile.ID_PATTERN.matcher(ToolProfile.DEFAULT_ID).matches());
    }

    @Test
    public void unknownToolsDoNotFailValidation()
    {
        ToolProfile profile = DefaultToolProfileFactory.createDefault(Set.of("future_tool")); //$NON-NLS-1$
        assertTrue(ToolProfileValidator.validate(profile).isValid());
    }
}
