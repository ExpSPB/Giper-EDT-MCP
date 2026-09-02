/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server.transport;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

import fm.giper.edt.mcp.server.protocol.ClientCapabilities;

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
    private volatile Consumer<String> sessionClosedListener;

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
    public void setSessionClosedListener(Consumer<String> sessionClosedListener)
    {
        this.sessionClosedListener = sessionClosedListener;
    }

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
        boolean removed = sessionId != null && sessions.remove(sessionId) != null;
        if (removed)
        {
            fireClosed(sessionId);
        }
        return removed;
    }

    /**
     * Closes every session bound to {@code requestedPath}. Used when that profile's
     * published surface changes: the client must initialize again.
     *
     * @param requestedPath canonical MCP path
     * @return how many sessions were closed
     */
    public int closeByRequestedPath(String requestedPath)
    {
        if (requestedPath == null || requestedPath.isBlank())
        {
            return 0;
        }
        List<String> ids = new ArrayList<>();
        for (Map.Entry<String, McpTransportSession> entry : sessions.entrySet())
        {
            if (requestedPath.equals(entry.getValue().getRequestedPath()))
            {
                ids.add(entry.getKey());
            }
        }
        int closed = 0;
        for (String id : ids)
        {
            if (close(id))
            {
                closed++;
            }
        }
        return closed;
    }

    /**
     * Distinct requested paths that currently have at least one open session.
     */
    public Set<String> activeRequestedPaths()
    {
        evictExpired();
        Set<String> paths = new LinkedHashSet<>();
        for (McpTransportSession session : sessions.values())
        {
            paths.add(session.getRequestedPath());
        }
        return Set.copyOf(paths);
    }

    public void shutdown()
    {
        List<String> ids = new ArrayList<>(sessions.keySet());
        sessions.clear();
        for (String id : ids)
        {
            fireClosed(id);
        }
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
            if (sessions.remove(id) != null)
            {
                fireClosed(id);
            }
        }
    }

    private void fireClosed(String sessionId)
    {
        Consumer<String> listener = sessionClosedListener;
        if (listener != null && sessionId != null)
        {
            listener.accept(sessionId);
        }
    }
}
