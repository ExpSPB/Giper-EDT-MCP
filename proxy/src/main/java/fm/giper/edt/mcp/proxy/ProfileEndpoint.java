/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.proxy;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Immutable MCP path: either legacy {@code /mcp} or {@code /mcp/profiles/<id>}.
 * Matches the plugin's {@code McpEndpoint} wire rules.
 */
public final class ProfileEndpoint
{
    /** Legacy alias of the {@code default} profile. */
    public static final String LEGACY_PATH = "/mcp"; //$NON-NLS-1$

    /** Prefix of an explicit profile path (id follows). */
    public static final String PROFILES_PREFIX = "/mcp/profiles/"; //$NON-NLS-1$

    /** Factory {@code default} profile id (same as the plugin). */
    public static final String DEFAULT_PROFILE_ID = "default"; //$NON-NLS-1$

    /** Profile id grammar copied from the plugin's {@code ToolProfile.ID_PATTERN}. */
    public static final Pattern ID_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9_-]{0,63}$"); //$NON-NLS-1$

    private final String rawPath;
    private final String requestedProfileId;
    private final boolean legacy;

    /**
     * @param rawPath the accepted request path (query already stripped)
     * @param requestedProfileId the requested profile id ({@code default} for {@code /mcp})
     * @param legacy {@code true} when the path is the legacy {@code /mcp} alias
     */
    public ProfileEndpoint(String rawPath, String requestedProfileId, boolean legacy)
    {
        this.rawPath = Objects.requireNonNull(rawPath, "rawPath"); //$NON-NLS-1$
        this.requestedProfileId = Objects.requireNonNull(requestedProfileId, "requestedProfileId"); //$NON-NLS-1$
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

    /**
     * Canonical path used for session binding, backend URIs and cache keys.
     *
     * @return {@code /mcp} or {@code /mcp/profiles/<id>}
     */
    public String canonicalPath()
    {
        return legacy ? LEGACY_PATH : PROFILES_PREFIX + requestedProfileId;
    }

    /**
     * Whether this path is an explicit {@code /mcp} or {@code /mcp/profiles/default} surface.
     * A fallback resolution must never share those caches.
     *
     * @return {@code true} for the two explicit default URLs
     */
    public boolean isExplicitDefaultPath()
    {
        return legacy || DEFAULT_PROFILE_ID.equals(requestedProfileId);
    }

    public static ProfileEndpoint legacyDefault()
    {
        return new ProfileEndpoint(LEGACY_PATH, DEFAULT_PROFILE_ID, true);
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other)
        {
            return true;
        }
        if (!(other instanceof ProfileEndpoint))
        {
            return false;
        }
        ProfileEndpoint that = (ProfileEndpoint) other;
        return legacy == that.legacy && canonicalPath().equals(that.canonicalPath());
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(canonicalPath(), Boolean.valueOf(legacy));
    }

    @Override
    public String toString()
    {
        return canonicalPath();
    }
}
