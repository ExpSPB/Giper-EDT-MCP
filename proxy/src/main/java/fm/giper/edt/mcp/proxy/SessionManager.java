/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.proxy;

import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Tracks the proxy's own client-facing MCP sessions.
 *
 * <p>All map operations are serialized on one lock. In particular, expiry cleanup, the hard-cap
 * check and insertion form one atomic create operation, so concurrent initialize requests cannot
 * oversubscribe the cap. Sessions are removed lazily by create/lookup and may also be swept by the
 * proxy lifecycle scheduler.</p>
 */
public final class SessionManager
{
    enum CloseResult
    {
        CLOSED,
        UNKNOWN,
        PATH_MISMATCH
    }

    /** Hard cap on concurrently open sessions; {@link #create()} returns {@code null} past it. */
    static final int MAX_SESSIONS = 10_000;

    static final Duration DEFAULT_TTL = Duration.ofMinutes(30);

    private final Object lock = new Object();
    private final Map<String, SessionContext> sessions = new HashMap<>();
    private final Duration ttl;
    private final Clock clock;
    private final int maxSessions;

    private ScheduledFuture<?> cleanupTask;
    private boolean shutdown;

    public SessionManager()
    {
        this(DEFAULT_TTL, Clock.systemUTC(), MAX_SESSIONS);
    }

    /** Creates a manager with a caller-selected idle TTL and the system UTC clock. */
    public SessionManager(Duration ttl)
    {
        this(ttl, Clock.systemUTC(), MAX_SESSIONS);
    }

    SessionManager(Duration ttl, Clock clock)
    {
        this(ttl, clock, MAX_SESSIONS);
    }

    SessionManager(Duration ttl, Clock clock, int maxSessions)
    {
        this.ttl = Objects.requireNonNull(ttl, "ttl"); //$NON-NLS-1$
        this.clock = Objects.requireNonNull(clock, "clock"); //$NON-NLS-1$
        if (ttl.isZero() || ttl.isNegative() || ttl.toMillis() == 0)
        {
            throw new IllegalArgumentException("Session TTL must be at least one millisecond"); //$NON-NLS-1$
        }
        if (maxSessions <= 0)
        {
            throw new IllegalArgumentException("Session cap must be positive"); //$NON-NLS-1$
        }
        this.maxSessions = maxSessions;
    }

    public String create()
    {
        return create(ProfileEndpoint.legacyDefault(), Backend.PROTOCOL_VERSION, true);
    }

    public String create(boolean allowsStructuredContent)
    {
        return create(ProfileEndpoint.legacyDefault(), Backend.PROTOCOL_VERSION, allowsStructuredContent);
    }

    public String create(ProfileEndpoint endpoint, String protocolVersion, boolean allowsStructuredContent)
    {
        Objects.requireNonNull(endpoint, "endpoint"); //$NON-NLS-1$
        synchronized (lock)
        {
            if (shutdown)
            {
                return null;
            }
            long now = clock.millis();
            cleanupExpiredLocked(now);
            if (sessions.size() >= maxSessions)
            {
                return null;
            }
            String sessionId;
            do
            {
                sessionId = UUID.randomUUID().toString();
            }
            while (sessions.containsKey(sessionId));
            sessions.put(sessionId,
                new SessionContext(sessionId, endpoint, protocolVersion, allowsStructuredContent, now));
            return sessionId;
        }
    }

    /**
     * Looks a session up. Expired, missing and path-mismatched sessions are all reported as unknown.
     * The access timestamp is advanced only after both the id and path have been accepted.
     */
    public SessionContext lookup(String sessionId, String canonicalPath)
    {
        if (sessionId == null)
        {
            return null;
        }
        synchronized (lock)
        {
            if (shutdown)
            {
                return null;
            }
            long now = clock.millis();
            cleanupExpiredLocked(now);
            SessionContext context = sessions.get(sessionId);
            if (context == null || canonicalPath != null && !context.matchesPath(canonicalPath))
            {
                return null;
            }
            context.touch(now);
            return context;
        }
    }

    public boolean allowsStructuredContent(String sessionId)
    {
        return !Boolean.FALSE.equals(capabilityOf(sessionId));
    }

    public Boolean capabilityOf(String sessionId)
    {
        SessionContext context = lookup(sessionId, null);
        return context == null ? null : Boolean.valueOf(context.allowsStructuredContent());
    }

    public boolean isValid(String sessionId)
    {
        return lookup(sessionId, null) != null;
    }

    public boolean isValid(String sessionId, String canonicalPath)
    {
        return lookup(sessionId, canonicalPath) != null;
    }

    public void close(String sessionId)
    {
        if (sessionId != null)
        {
            synchronized (lock)
            {
                sessions.remove(sessionId);
            }
        }
    }

    public boolean closeOnPath(String sessionId, String canonicalPath)
    {
        return closeForDelete(sessionId, canonicalPath) == CloseResult.CLOSED;
    }

    CloseResult closeForDelete(String sessionId, String canonicalPath)
    {
        if (sessionId == null || canonicalPath == null)
        {
            return CloseResult.UNKNOWN;
        }
        synchronized (lock)
        {
            cleanupExpiredLocked(clock.millis());
            SessionContext context = sessions.get(sessionId);
            if (context == null)
            {
                return CloseResult.UNKNOWN;
            }
            if (!context.matchesPath(canonicalPath))
            {
                return CloseResult.PATH_MISMATCH;
            }
            sessions.remove(sessionId);
            return CloseResult.CLOSED;
        }
    }

    public int closeByCanonicalPath(String canonicalPath)
    {
        if (canonicalPath == null)
        {
            return 0;
        }
        synchronized (lock)
        {
            cleanupExpiredLocked(clock.millis());
            int before = sessions.size();
            sessions.values().removeIf(context -> context.matchesPath(canonicalPath));
            return before - sessions.size();
        }
    }

    /** Removes every session whose idle time has reached the configured TTL. */
    public int cleanupExpired()
    {
        synchronized (lock)
        {
            return cleanupExpiredLocked(clock.millis());
        }
    }

    /**
     * Attaches idle cleanup to the proxy's lifecycle scheduler. Repeated calls replace the previous
     * task; shutdown cancels the task without owning or stopping the supplied executor.
     */
    public void startPeriodicCleanup(ScheduledExecutorService scheduler)
    {
        Objects.requireNonNull(scheduler, "scheduler"); //$NON-NLS-1$
        synchronized (lock)
        {
            if (shutdown)
            {
                throw new IllegalStateException("Session manager is shut down"); //$NON-NLS-1$
            }
            if (cleanupTask != null)
            {
                cleanupTask.cancel(false);
            }
            long delayMillis = Math.max(1L, Math.min(ttl.toMillis(), Duration.ofMinutes(1).toMillis()));
            cleanupTask = scheduler.scheduleWithFixedDelay(this::cleanupExpired,
                delayMillis, delayMillis, TimeUnit.MILLISECONDS);
        }
    }

    /** Cancels periodic cleanup and permanently drops all client sessions. Idempotent. */
    public void shutdown()
    {
        synchronized (lock)
        {
            shutdown = true;
            if (cleanupTask != null)
            {
                cleanupTask.cancel(false);
                cleanupTask = null;
            }
            sessions.clear();
        }
    }

    public int activeCount()
    {
        synchronized (lock)
        {
            cleanupExpiredLocked(clock.millis());
            return sessions.size();
        }
    }

    boolean hasCleanupTask()
    {
        synchronized (lock)
        {
            return cleanupTask != null && !cleanupTask.isCancelled();
        }
    }

    private int cleanupExpiredLocked(long now)
    {
        int removed = 0;
        for (Iterator<SessionContext> iterator = sessions.values().iterator(); iterator.hasNext();)
        {
            if (iterator.next().isExpired(now, ttl))
            {
                iterator.remove();
                removed++;
            }
        }
        return removed;
    }
}
