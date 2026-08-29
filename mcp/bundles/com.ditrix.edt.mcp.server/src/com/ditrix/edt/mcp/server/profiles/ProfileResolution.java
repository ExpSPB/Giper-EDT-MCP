/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.profiles;

import java.util.Objects;

/**
 * Requested vs effective profile for one request, computed from a single snapshot.
 */
public final class ProfileResolution
{
    private final String requestedProfileId;
    private final ToolProfile effectiveProfile;
    private final FallbackReason fallbackReason;
    private final boolean legacyEndpoint;
    private final long documentRevision;

    public ProfileResolution(String requestedProfileId, ToolProfile effectiveProfile,
        FallbackReason fallbackReason, boolean legacyEndpoint, long documentRevision)
    {
        this.requestedProfileId = requestedProfileId;
        this.effectiveProfile = Objects.requireNonNull(effectiveProfile, "effectiveProfile"); //$NON-NLS-1$
        this.fallbackReason = fallbackReason;
        this.legacyEndpoint = legacyEndpoint;
        this.documentRevision = documentRevision;
    }

    public String getRequestedProfileId()
    {
        return requestedProfileId;
    }

    public ToolProfile getEffectiveProfile()
    {
        return effectiveProfile;
    }

    public String getEffectiveProfileId()
    {
        return effectiveProfile.getId();
    }

    public boolean isFallbackApplied()
    {
        return fallbackReason != null;
    }

    public FallbackReason getFallbackReason()
    {
        return fallbackReason;
    }

    public boolean isLegacyEndpoint()
    {
        return legacyEndpoint;
    }

    public boolean isExplicitEndpoint()
    {
        return !legacyEndpoint;
    }

    public long getDocumentRevision()
    {
        return documentRevision;
    }
}
