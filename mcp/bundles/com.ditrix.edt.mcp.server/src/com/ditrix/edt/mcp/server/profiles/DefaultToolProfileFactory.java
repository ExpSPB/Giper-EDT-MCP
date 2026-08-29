/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.profiles;

import java.util.Collection;
import java.util.Set;

/**
 * Sole factory for the conservative in-memory {@code default} profile used when the
 * stored document is missing, malformed, or written in a newer schema.
 * {@code get_server_status} is added by runtime policy, not forced into this allowlist.
 */
public final class DefaultToolProfileFactory
{
    public static final String SAFE_DEFAULT_DISPLAY_NAME = "Default"; //$NON-NLS-1$

    public static final String SAFE_DEFAULT_DESCRIPTION =
        "Conservative fallback profile used when a requested profile is unknown or disabled."; //$NON-NLS-1$

    private DefaultToolProfileFactory()
    {
        // Utility class
    }

    public static ToolProfile createSafeDefault()
    {
        return createDefault(Set.of());
    }

    /**
     * Builds the reserved {@code default} profile with an explicit allowlist
     * (used by migration from the legacy disabled-set).
     */
    public static ToolProfile createDefault(Collection<String> allowedTools)
    {
        return ToolProfile.builder()
            .id(ToolProfile.DEFAULT_ID)
            .displayName(SAFE_DEFAULT_DISPLAY_NAME)
            .description(SAFE_DEFAULT_DESCRIPTION)
            .enabled(true)
            .allowedTools(allowedTools)
            .revision(1L)
            .build();
    }

    public static ToolProfileSnapshot createSafeSnapshot()
    {
        return ToolProfileSnapshot.of(0L, Set.of(createSafeDefault()));
    }
}
