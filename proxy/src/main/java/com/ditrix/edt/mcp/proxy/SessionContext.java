/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.proxy;

import java.util.Objects;

/**
 * One proxy-issued client session: bound to a single {@link ProfileEndpoint}, plus the
 * handshake protocol version, structuredContent capability and last-access time.
 */
public final class SessionContext
{
    private final String sessionId;
    private final ProfileEndpoint endpoint;
    private final String protocolVersion;
    private final boolean allowsStructuredContent;
    private volatile long lastAccessMillis;

    SessionContext(String sessionId, ProfileEndpoint endpoint, String protocolVersion,
        boolean allowsStructuredContent)
    {
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId"); //$NON-NLS-1$
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint"); //$NON-NLS-1$
        this.protocolVersion = protocolVersion == null || protocolVersion.isBlank()
            ? Backend.PROTOCOL_VERSION
            : protocolVersion;
        this.allowsStructuredContent = allowsStructuredContent;
        this.lastAccessMillis = System.currentTimeMillis();
    }

    public String getSessionId()
    {
        return sessionId;
    }

    public ProfileEndpoint getEndpoint()
    {
        return endpoint;
    }

    public String getProtocolVersion()
    {
        return protocolVersion;
    }

    public boolean allowsStructuredContent()
    {
        return allowsStructuredContent;
    }

    public long getLastAccessMillis()
    {
        return lastAccessMillis;
    }

    void touch()
    {
        lastAccessMillis = System.currentTimeMillis();
    }

    boolean matchesPath(String canonicalPath)
    {
        return endpoint.canonicalPath().equals(canonicalPath);
    }
}
