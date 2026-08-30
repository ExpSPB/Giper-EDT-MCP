/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server.transport;

import fm.giper.edt.mcp.server.profiles.ToolProfile;

/**
 * Canonical MCP path: either legacy {@code /mcp} or {@code /mcp/profiles/<id>}.
 */
public final class McpEndpoint
{
    public static final String LEGACY_PATH = "/mcp"; //$NON-NLS-1$
    public static final String PROFILES_PREFIX = "/mcp/profiles/"; //$NON-NLS-1$

    private final String rawPath;
    private final String requestedProfileId;
    private final boolean legacy;

    public McpEndpoint(String rawPath, String requestedProfileId, boolean legacy)
    {
        this.rawPath = rawPath;
        this.requestedProfileId = requestedProfileId;
        this.legacy = legacy;
    }

    public String getRawPath()
    {
        return rawPath;
    }

    public String getRequestedProfileId()
    {
        return requestedProfileId;
    }

    public boolean isLegacy()
    {
        return legacy;
    }

    public String canonicalPath()
    {
        return legacy ? LEGACY_PATH : PROFILES_PREFIX + requestedProfileId;
    }

    public static McpEndpoint legacyDefault()
    {
        return new McpEndpoint(LEGACY_PATH, ToolProfile.DEFAULT_ID, true);
    }
}
