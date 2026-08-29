/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.proxy;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks the proxy's OWN client-facing MCP sessions.
 *
 * <p>Each session is a {@link SessionContext} bound to one {@link ProfileEndpoint}. Replaying
 * the same {@code Mcp-Session-Id} on another path is a 404; {@code DELETE} closes only that
 * session. StructuredContent capability stays per session because the proxy terminates
 * initialize itself.</p>
 */
public final class SessionManager
{
    /** Hard cap on concurrently open sessions; {@link #create()} returns {@code null} past it. */
    static final int MAX_SESSIONS = 10_000;

    private final Map<String, SessionContext> sessions = new ConcurrentHashMap<>();

    /**
     * Creates a session on the legacy {@code /mcp} path that accepts structuredContent.
     *
     * @return the issued id, or {@code null} when the session cap is reached
     */
    public String create()
    {
        return create(ProfileEndpoint.legacyDefault(), Backend.PROTOCOL_VERSION, true);
    }

    /**
     * Creates a legacy {@code /mcp} session that remembers the client's structuredContent capability.
     *
     * @param allowsStructuredContent whether this client accepts {@code structuredContent}
     * @return the issued id, or {@code null} when the session cap is reached
     */
    public String create(boolean allowsStructuredContent)
    {
        return create(ProfileEndpoint.legacyDefault(), Backend.PROTOCOL_VERSION, allowsStructuredContent);
    }

    /**
     * Creates a path-bound client session.
     *
     * @param endpoint the MCP path this session may be replayed on
     * @param protocolVersion the protocol version echoed at initialize
     * @param allowsStructuredContent whether this client accepts {@code structuredContent}
     * @return the issued id, or {@code null} when the session cap is reached
     */
    public String create(ProfileEndpoint endpoint, String protocolVersion, boolean allowsStructuredContent)
    {
        if (sessions.size() >= MAX_SESSIONS)
        {
            return null;
        }
        String sessionId = UUID.randomUUID().toString();
        sessions.put(sessionId,
            new SessionContext(sessionId, endpoint, protocolVersion, allowsStructuredContent));
        return sessionId;
    }

    /**
     * Looks a session up. A path mismatch is reported as unknown (HTTP 404, no existence leak).
     *
     * @param sessionId the {@code Mcp-Session-Id} header (may be {@code null})
     * @param canonicalPath the request path, or {@code null} to skip the path check
     * @return the context, or {@code null} when missing or bound to another path
     */
    public SessionContext lookup(String sessionId, String canonicalPath)
    {
        if (sessionId == null)
        {
            return null;
        }
        SessionContext context = sessions.get(sessionId);
        if (context == null)
        {
            return null;
        }
        if (canonicalPath != null && !context.matchesPath(canonicalPath))
        {
            return null;
        }
        context.touch();
        return context;
    }

    /**
     * Whether the client behind a session accepts {@code structuredContent}. An unknown or
     * {@code null} session answers {@code true}.
     *
     * @param sessionId the session id (may be {@code null})
     * @return {@code false} only when that session's client explicitly opted out
     */
    public boolean allowsStructuredContent(String sessionId)
    {
        return !Boolean.FALSE.equals(capabilityOf(sessionId));
    }

    /**
     * Looks a session up ONCE, answering both "is it open?" and the capability.
     *
     * @param sessionId the session id (may be {@code null})
     * @return {@code TRUE}/{@code FALSE} for an open session, or {@code null} when unknown
     */
    public Boolean capabilityOf(String sessionId)
    {
        SessionContext context = lookup(sessionId, null);
        return context == null ? null : Boolean.valueOf(context.allowsStructuredContent());
    }

    /**
     * Whether the id identifies an open session (any path).
     *
     * @param sessionId the session id (may be {@code null})
     * @return {@code true} when the id belongs to an open session
     */
    public boolean isValid(String sessionId)
    {
        return lookup(sessionId, null) != null;
    }

    /**
     * Whether the id identifies an open session bound to {@code canonicalPath}.
     *
     * @param sessionId the session id (may be {@code null})
     * @param canonicalPath the request path
     * @return {@code true} when the session exists on that path
     */
    public boolean isValid(String sessionId, String canonicalPath)
    {
        return lookup(sessionId, canonicalPath) != null;
    }

    /**
     * Closes a session. Unknown or {@code null} ids are ignored (idempotent).
     *
     * @param sessionId the session id to close (may be {@code null})
     */
    public void close(String sessionId)
    {
        if (sessionId != null)
        {
            sessions.remove(sessionId);
        }
    }

    /**
     * Closes {@code sessionId} only when it is bound to {@code canonicalPath}.
     *
     * @param sessionId the session to close
     * @param canonicalPath the DELETE path
     * @return {@code true} when a matching session was removed
     */
    public boolean closeOnPath(String sessionId, String canonicalPath)
    {
        if (sessionId == null || canonicalPath == null)
        {
            return false;
        }
        SessionContext context = sessions.get(sessionId);
        if (context == null || !context.matchesPath(canonicalPath))
        {
            return false;
        }
        return sessions.remove(sessionId, context);
    }

    /**
     * @return the open session count
     */
    public int activeCount()
    {
        return sessions.size();
    }
}
