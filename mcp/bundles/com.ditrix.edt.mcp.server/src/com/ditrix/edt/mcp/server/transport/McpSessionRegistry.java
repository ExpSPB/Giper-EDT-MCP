/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.transport;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

import com.ditrix.edt.mcp.server.protocol.ClientCapabilities;

/**
 * Size-limited, TTL-bounded registry of path-bound MCP sessions.
 */
public final class McpSessionRegistry
{
    public static final int DEFAULT_MAX_SESSIONS = 10_000;
    public static final long DEFAULT_TTL_MILLIS = TimeUnit.MINUTES.toMillis(30);

    public enum LookupStatus
    {
        OK,
        MISSING,
        UNKNOWN,
        PATH_MISMATCH
    }

    public static final class Lookup
    {
        private final LookupStatus status;
        private final McpTransportSession session;

        Lookup(LookupStatus status, McpTransportSession session)
        {
            this.status = status;
            this.session = session;
        }

        public LookupStatus getStatus()
        {
            return status;
        }

        public McpTransportSession getSession()
        {
            return session;
        }
    }

    private final ConcurrentHashMap<String, McpTransportSession> sessions = new ConcurrentHashMap<>();
    private final int maxSessions;
    private final long ttlMillis;
    private final LongSupplier clock;

    public McpSessionRegistry()
    {
        this(DEFAULT_MAX_SESSIONS, DEFAULT_TTL_MILLIS, System::currentTimeMillis);
    }

    McpSessionRegistry(int maxSessions, long ttlMillis, LongSupplier clock)
    {
        this.maxSessions = maxSessions;
        this.ttlMillis = ttlMillis;
        this.clock = clock;
    }

    /**
     * Creates a session after a successful initialize. Returns {@code null} when the cap is reached.
     */
    public McpTransportSession create(String requestedPath, String protocolVersion,
        ClientCapabilities capabilities)
    {
        evictExpired();
        if (sessions.size() >= maxSessions)
        {
            return null;
        }
        String id = UUID.randomUUID().toString();
        McpTransportSession session = new McpTransportSession(id, requestedPath, protocolVersion,
            capabilities, clock.getAsLong());
        sessions.put(id, session);
        return session;
    }

    public Lookup lookup(String sessionId, String requestedPath, boolean required)
    {
        evictExpired();
        if (sessionId == null || sessionId.isBlank())
        {
            return new Lookup(required ? LookupStatus.MISSING : LookupStatus.OK, null);
        }
        McpTransportSession session = sessions.get(sessionId);
        if (session == null)
        {
            return new Lookup(LookupStatus.UNKNOWN, null);
        }
        if (requestedPath != null && !requestedPath.equals(session.getRequestedPath()))
        {
            return new Lookup(LookupStatus.PATH_MISMATCH, null);
        }
        session.touch(clock.getAsLong());
        return new Lookup(LookupStatus.OK, session);
    }

    public boolean close(String sessionId)
    {
        return sessionId != null && sessions.remove(sessionId) != null;
    }

    public void shutdown()
    {
        sessions.clear();
    }

    public int size()
    {
        evictExpired();
        return sessions.size();
    }

    private void evictExpired()
    {
        long now = clock.getAsLong();
        List<String> expired = new ArrayList<>();
        for (Map.Entry<String, McpTransportSession> entry : sessions.entrySet())
        {
            if (now - entry.getValue().getLastAccessMillis() > ttlMillis)
            {
                expired.add(entry.getKey());
            }
        }
        for (String id : expired)
        {
            sessions.remove(id);
        }
    }
}
