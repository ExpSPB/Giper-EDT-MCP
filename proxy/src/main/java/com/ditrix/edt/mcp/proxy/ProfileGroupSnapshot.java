/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.proxy;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Fail-closed routing group for one requested client endpoint: donor plus backends that
 * share the same effective id, fallback state and fingerprint.
 */
public final class ProfileGroupSnapshot
{
    public static final class IncompatibleBackend
    {
        public final int port;
        public final String reason;

        public IncompatibleBackend(int port, String reason)
        {
            this.port = port;
            this.reason = reason;
        }
    }

    private final ProfileEndpoint endpoint;
    private final String effectiveProfileId;
    private final String fallbackReason;
    private final String fingerprint;
    private final Backend donor;
    private final List<Backend> compatible;
    private final List<IncompatibleBackend> incompatible;

    public ProfileGroupSnapshot(ProfileEndpoint endpoint, String effectiveProfileId, String fallbackReason,
        String fingerprint, Backend donor, List<Backend> compatible, List<IncompatibleBackend> incompatible)
    {
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint"); //$NON-NLS-1$
        this.effectiveProfileId = effectiveProfileId;
        this.fallbackReason = fallbackReason;
        this.fingerprint = fingerprint;
        this.donor = donor;
        this.compatible = Collections.unmodifiableList(compatible);
        this.incompatible = Collections.unmodifiableList(incompatible);
    }

    public ProfileEndpoint getEndpoint()
    {
        return endpoint;
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

    public Backend getDonor()
    {
        return donor;
    }

    public List<Backend> getCompatible()
    {
        return compatible;
    }

    public List<IncompatibleBackend> getIncompatible()
    {
        return incompatible;
    }

    /**
     * Cache key: requested id + effective id + fallback reason + group fingerprint.
     * A fallback group never uses the explicit {@code /mcp} / {@code /mcp/profiles/default} key.
     *
     * @return the cache key
     */
    public String cacheKey()
    {
        String requested = endpoint.getRequestedProfileId();
        String pathTag = isFallback() ? "fallback:" + endpoint.canonicalPath() : endpoint.canonicalPath(); //$NON-NLS-1$
        return pathTag + "|" + requested + "|" + effectiveProfileId + "|" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            + (isFallback() ? fallbackReason : "NONE") + "|" + fingerprint; //$NON-NLS-1$ //$NON-NLS-2$
    }

    public boolean contains(Backend backend)
    {
        if (backend == null)
        {
            return false;
        }
        for (Backend candidate : compatible)
        {
            if (candidate.getPort() == backend.getPort())
            {
                return true;
            }
        }
        return false;
    }
}
