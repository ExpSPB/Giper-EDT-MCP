/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server.protocol;

import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The MCP sessions this server has issued.
 *
 * <p>{@code initialize} mints a session and its id travels back in {@code Mcp-Session-Id}; every
 * later request on that connection must carry the id back, and the transport
 * ({@code McpHttpHandler}) refuses one that does not. Before this registry existed the header was
 * generated fresh per {@code initialize} response and then never read again: an id the server had
 * never issued, an id from a terminated session, and no id at all were all accepted, and
 * {@code DELETE /mcp} answered 200 while terminating nothing.</p>
 *
 * <p>Each session also remembers the {@link ClientCapabilities} ITS client declared, because the
 * capabilities are per client and the server can hold more than one at a time (an IDE plus an
 * agent, or two editor windows). Keeping them in one server-wide slot meant the last
 * {@code initialize} decided how everyone else's {@code tools/call} was formatted - structured
 * content sent to a client that opted out of it, and the reverse.</p>
 *
 * <p>Deliberately the same shape as the proxy's {@code SessionManager}, down to the session cap
 * and the "unknown session is permissive" rule for capabilities: the two components implement the
 * same MCP session layer, and where they disagreed the client saw the difference.</p>
 *
 * <p>Thread-safe: backed by a concurrent map, so transport threads can create, validate and close
 * sessions concurrently.</p>
 */
public final class McpSessionRegistry
{
    /**
     * Hard cap on concurrently open sessions; {@link #create(ClientCapabilities)} returns
     * {@code null} past it. Deliberately a cap only, with no idle-eviction sweep - a well-behaved
     * client sends {@code DELETE /mcp}, and reaching this number means sessions are leaking and
     * is worth investigating rather than papering over. Sessions do not survive a server
     * stop/start: the registry belongs to the running listener, and a client whose session
     * vanished is told to initialize again.
     */
    public static final int MAX_SESSIONS = 10_000;

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    /** Immutable routing and client capabilities selected during initialize. */
    public record Session(String endpointPath, String effectiveProfileId, ClientCapabilities capabilities)
    {
    }

    /**
     * Opens a session for a client that has just initialized.
     *
     * @param capabilities the capabilities the client declared; {@code null} is stored as
     *            {@link ClientCapabilities#ABSENT} (the permissive default)
     * @return the freshly issued random UUID session id, or {@code null} when {@link #MAX_SESSIONS}
     *         sessions are already open
     */
    public String create(ClientCapabilities capabilities)
    {
        return create("/mcp", null, capabilities); //$NON-NLS-1$
    }

    public synchronized String create(String endpointPath, String effectiveProfileId, ClientCapabilities capabilities)
    {
        if (sessions.size() >= MAX_SESSIONS)
        {
            return null;
        }
        String sessionId = UUID.randomUUID().toString();
        sessions.put(sessionId, new Session(endpointPath, effectiveProfileId,
            capabilities == null ? ClientCapabilities.ABSENT : capabilities));
        return sessionId;
    }

    /**
     * Looks a session up ONCE, answering both "is it open?" and "what did its client declare?" -
     * so a concurrent {@code DELETE} cannot land between the two questions and turn a session that
     * opted out of a feature into a permissive unknown one.
     *
     * @param sessionId the id from the {@code Mcp-Session-Id} header (may be {@code null})
     * @return that session's capabilities, or {@code null} when the id is absent or unknown
     */
    public ClientCapabilities capabilitiesOf(String sessionId)
    {
        Session session = sessionId == null ? null : sessions.get(sessionId);
        return session == null ? null : session.capabilities();
    }

    /** Returns the session only on the endpoint that issued it. */
    public Session find(String sessionId, String endpointPath)
    {
        Session session = sessionId == null ? null : sessions.get(sessionId);
        return session != null && session.endpointPath().equals(endpointPath) ? session : null;
    }

    /** A request on one endpoint cannot close a session issued on another. */
    public boolean close(String sessionId, String endpointPath)
    {
        Session session = find(sessionId, endpointPath);
        return session != null && sessions.remove(sessionId, session);
    }

    /**
     * Checks whether a session id identifies an open session.
     *
     * @param sessionId the id from the {@code Mcp-Session-Id} header (may be {@code null})
     * @return {@code true} when the id belongs to an open session
     */
    public boolean isValid(String sessionId)
    {
        return sessionId != null && sessions.containsKey(sessionId);
    }

    /**
     * Closes a session. Unknown or {@code null} ids are ignored (closing is idempotent).
     *
     * @param sessionId the session id to close (may be {@code null})
     * @return {@code true} when a session was actually open and is now closed
     */
    public boolean close(String sessionId)
    {
        return sessionId != null && sessions.remove(sessionId) != null;
    }

    /** Endpoint paths represented by active sessions. */
    public Set<String> activeRequestedPaths()
    {
        Set<String> paths = new LinkedHashSet<>();
        for (Session session : sessions.values())
        {
            paths.add(session.endpointPath());
        }
        return Set.copyOf(paths);
    }

    /** Closes sessions issued for exactly this endpoint path. */
    public int closeByRequestedPath(String requestedPath)
    {
        if (requestedPath == null || requestedPath.isBlank())
        {
            return 0;
        }
        int closed = 0;
        for (Map.Entry<String, Session> entry : sessions.entrySet())
        {
            if (requestedPath.equals(entry.getValue().endpointPath())
                && sessions.remove(entry.getKey(), entry.getValue()))
            {
                closed++;
            }
        }
        return closed;
    }

    /**
     * The number of currently open sessions.
     *
     * @return the open session count
     */
    public int activeCount()
    {
        return sessions.size();
    }
}
