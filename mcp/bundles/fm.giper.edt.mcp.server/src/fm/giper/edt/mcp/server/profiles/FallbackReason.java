/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server.profiles;

/**
 * Why a requested profile was replaced by {@code default}. Absent when the requested
 * profile is the one that is actually in force.
 */
public enum FallbackReason
{
    UNKNOWN_PROFILE,
    DISABLED_PROFILE
}
