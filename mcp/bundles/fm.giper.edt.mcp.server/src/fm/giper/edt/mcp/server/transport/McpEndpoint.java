/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server.transport;

import java.net.URI;

import fm.giper.edt.mcp.server.profiles.ToolProfile;

/** Parses MCP routes and gives each a canonical session key. */
record McpEndpoint(String path, String profileId)
{
    static final String LEGACY_PATH = "/mcp"; //$NON-NLS-1$
    private static final String PLAIN_TEXT_PATH = LEGACY_PATH + "/plaintext"; //$NON-NLS-1$
    private static final String PROFILES_PREFIX = LEGACY_PATH + "/profiles/"; //$NON-NLS-1$
    private static final McpEndpoint LEGACY = new McpEndpoint(LEGACY_PATH, null);
    private static final McpEndpoint PLAIN_TEXT = new McpEndpoint(PLAIN_TEXT_PATH, null);

    static McpEndpoint from(URI uri)
    {
        String rawPath = uri.getRawPath();
        if (LEGACY_PATH.equals(rawPath))
        {
            return LEGACY;
        }
        if (PLAIN_TEXT_PATH.equals(rawPath))
        {
            return PLAIN_TEXT;
        }
        if (rawPath != null && rawPath.startsWith(PROFILES_PREFIX))
        {
            // Decode once. The profile ID grammar excludes slash, percent, dot and backslash.
            String id = uri.getPath().substring(PROFILES_PREFIX.length());
            if (ToolProfile.ID_PATTERN.matcher(id).matches())
            {
                return new McpEndpoint(PROFILES_PREFIX + id, id);
            }
        }
        return null;
    }

    Boolean plainTextMode()
    {
        return PLAIN_TEXT_PATH.equals(path) ? Boolean.TRUE : null;
    }

    boolean isLegacy()
    {
        return profileId == null;
    }
}
