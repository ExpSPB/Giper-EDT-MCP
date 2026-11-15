/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.proxy;

import com.google.gson.JsonArray;

/**
 * Profile resolution metadata read from one backend channel after handshake
 * ({@code get_server_status} + {@code tools/list} fingerprint).
 */
public final class ChannelResolution
{
    public enum Kind
    {
        CONFIRMED,
        LEGACY_DEFAULT
    }

    private final Kind kind;
    private final String requestedProfileId;
    private final String effectiveProfileId;
    private final String fallbackReason;
    private final String fingerprint;
    private final JsonArray availableProfiles;

    private ChannelResolution(Kind kind, String requestedProfileId, String effectiveProfileId,
        String fallbackReason, String fingerprint, JsonArray availableProfiles)
    {
        this.kind = kind;
        this.requestedProfileId = requestedProfileId;
        this.effectiveProfileId = effectiveProfileId;
        this.fallbackReason = fallbackReason;
        this.fingerprint = fingerprint;
        this.availableProfiles = availableProfiles;
    }

    public static ChannelResolution confirmed(String requestedProfileId, String effectiveProfileId,
        String fallbackReason, String fingerprint, JsonArray availableProfiles)
    {
        return new ChannelResolution(Kind.CONFIRMED, requestedProfileId, effectiveProfileId,
            fallbackReason, fingerprint, availableProfiles);
    }

    public static ChannelResolution legacyDefault(String fingerprint)
    {
        return new ChannelResolution(Kind.LEGACY_DEFAULT, ProfileEndpoint.DEFAULT_PROFILE_ID,
            ProfileEndpoint.DEFAULT_PROFILE_ID, null, fingerprint, new JsonArray());
    }

    public Kind getKind()
    {
        return kind;
    }

    public boolean isLegacyDefault()
    {
        return kind == Kind.LEGACY_DEFAULT;
    }

    public String getRequestedProfileId()
    {
        return requestedProfileId;
    }

    public String getEffectiveProfileId()
    {
        return effectiveProfileId;
    }

    public String getFallbackReason()
    {
        return fallbackReason;
    }

    public boolean isFallback()
    {
        return fallbackReason != null && !fallbackReason.isBlank();
    }

    public String getFingerprint()
    {
        return fingerprint;
    }

    public JsonArray getAvailableProfiles()
    {
        return availableProfiles;
    }

    /**
     * Compatibility identity: effective id + fallback state + descriptor fingerprint.
     * Local revision counters are ignored.
     *
     * @return a stable grouping key
     */
    public String compatibilityKey()
    {
        return effectiveProfileId + "|" + (isFallback() ? fallbackReason : "NONE") + "|" + fingerprint; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }
}
