/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.proxy;

import java.time.Duration;
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
        this(sessionId, endpoint, protocolVersion, allowsStructuredContent, System.currentTimeMillis());
    }

    SessionContext(String sessionId, ProfileEndpoint endpoint, String protocolVersion,
        boolean allowsStructuredContent, long createdAtMillis)
    {
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId"); //$NON-NLS-1$
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint"); //$NON-NLS-1$
        this.protocolVersion = protocolVersion == null || protocolVersion.isBlank()
            ? Backend.PROTOCOL_VERSION
            : protocolVersion;
        this.allowsStructuredContent = allowsStructuredContent;
        this.lastAccessMillis = createdAtMillis;
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

    void touch(long nowMillis)
    {
        lastAccessMillis = nowMillis;
    }

    boolean isExpired(long nowMillis, Duration ttl)
    {
        long lastAccess = lastAccessMillis;
        return nowMillis >= lastAccess && nowMillis - lastAccess >= ttl.toMillis();
    }

    boolean matchesPath(String canonicalPath)
    {
        return endpoint.canonicalPath().equals(canonicalPath);
    }
}
