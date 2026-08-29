/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.profiles;

/**
 * Resolves one requested profile id against one snapshot.
 * Unknown or disabled profiles fall back to {@code default}; malformed paths never reach this class.
 */
public final class ProfileResolver
{
    private ProfileResolver()
    {
        // Utility class
    }

    public static ProfileResolution resolveDefault(ToolProfileSnapshot snapshot)
    {
        return resolve(ToolProfile.DEFAULT_ID, true, snapshot);
    }

    public static ProfileResolution resolve(String requestedProfileId, boolean legacyEndpoint,
        ToolProfileSnapshot snapshot)
    {
        ToolProfileSnapshot document = snapshot != null ? snapshot : DefaultToolProfileFactory.createSafeSnapshot();
        String requested = requestedProfileId == null || requestedProfileId.isBlank()
            ? ToolProfile.DEFAULT_ID
            : requestedProfileId;
        ToolProfile fallback = document.getDefault();
        if (fallback == null)
        {
            fallback = DefaultToolProfileFactory.createSafeDefault();
        }

        if (ToolProfile.DEFAULT_ID.equals(requested))
        {
            return new ProfileResolution(requested, fallback, null, legacyEndpoint,
                document.getDocumentRevision());
        }

        ToolProfile requestedProfile = document.get(requested);
        if (requestedProfile == null)
        {
            return new ProfileResolution(requested, fallback, FallbackReason.UNKNOWN_PROFILE, legacyEndpoint,
                document.getDocumentRevision());
        }
        if (!requestedProfile.isEnabled())
        {
            return new ProfileResolution(requested, fallback, FallbackReason.DISABLED_PROFILE, legacyEndpoint,
                document.getDocumentRevision());
        }
        return new ProfileResolution(requested, requestedProfile, null, legacyEndpoint,
            document.getDocumentRevision());
    }
}
