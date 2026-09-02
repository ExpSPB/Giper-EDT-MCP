/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import fm.giper.edt.mcp.server.transport.McpEndpoint;

/**
 * Registry of the open server-initiated SSE streams (the standalone {@code GET}
 * {@code text/event-stream} connections per the MCP Streamable HTTP transport).
 * Each stream is bound to a requested endpoint and optional session; a
 * list-changed notification is delivered once per session on that path.
 * <p>
 * Thread-safe: streams are added/removed from a concurrent set, and each stream's
 * writes (heartbeat from its own SSE thread, broadcasts from a request thread) are
 * serialized by the per-stream lock in {@link SseStream}.
 */
public final class SseStreamRegistry // NOSONAR intentional singleton (Eclipse service / getInstance); a single instance is by design
{
    private static final String LIST_CHANGED =
        "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/tools/list_changed\"}"; //$NON-NLS-1$

    private static final SseStreamRegistry INSTANCE = new SseStreamRegistry();

    private final Set<SseStream> streams = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, RequestedPathState> requestedPathStates = new ConcurrentHashMap<>();
    private final AtomicLong eventId = new AtomicLong(0);

    private SseStreamRegistry()
    {
        // Singleton
    }

    public static SseStreamRegistry getInstance()
    {
        return INSTANCE;
    }

    /**
     * Registers an open SSE stream on the legacy {@code /mcp} path.
     */
    public SseStream register(OutputStream out)
    {
        return register(out, null, McpEndpoint.LEGACY_PATH);
    }

    /**
     * Registers an open SSE stream bound to a session and requested endpoint.
     *
     * @param out the SSE stream's output stream
     * @param sessionId MCP session id, or {@code null} for a sessionless legacy stream
     * @param requestedPath canonical endpoint path
     * @return the registered handle (used for heartbeat writes and unregister)
     */
    public SseStream register(OutputStream out, String sessionId, String requestedPath)
    {
        return register(out, sessionId, requestedPath, null);
    }

    /**
     * Registers an open SSE stream and remembers the negotiated protocol version
     * so later notifications use that session's wire format.
     */
    public SseStream register(OutputStream out, String sessionId, String requestedPath, String protocolVersion)
    {
        SseStream stream = new SseStream(out, sessionId,
            requestedPath == null || requestedPath.isBlank() ? McpEndpoint.LEGACY_PATH : requestedPath,
            protocolVersion);
        streams.add(stream);
        return stream;
    }

    /**
     * Captures the current close generation for a requested path before an SSE GET
     * is queued on the dedicated executor.
     */
    public RequestedPathAdmission admitRequestedPath(String requestedPath)
    {
        String path = normalizePath(requestedPath);
        RequestedPathAdmission[] admitted = new RequestedPathAdmission[1];
        requestedPathStates.compute(path, (key, state) -> {
            RequestedPathState current = state == null ? new RequestedPathState() : state;
            current.lock.lock();
            try
            {
                current.pendingAdmissions++;
                admitted[0] = new RequestedPathAdmission(path, current, current.generation);
            }
            finally
            {
                current.lock.unlock();
            }
            return current;
        });
        return admitted[0];
    }

    /**
     * Registers a queued SSE GET only if no path-level kick happened since admission.
     * A kick racing with insertion either finds the stream in its scan or makes the
     * post-insert generation check remove and close it.
     *
     * @return the registered stream, or {@code null} when its admission is stale
     */
    public SseStream registerIfCurrent(OutputStream out, String sessionId, String requestedPath,
        String protocolVersion, RequestedPathAdmission admission)
    {
        return registerIfCurrent(out, sessionId, requestedPath, protocolVersion, admission, () -> {
            // production path has no insertion hook
        });
    }

    SseStream registerIfCurrent(OutputStream out, String sessionId, String requestedPath,
        String protocolVersion, RequestedPathAdmission admission, Runnable afterAdd)
    {
        String path = normalizePath(requestedPath);
        try
        {
            if (admission == null || !path.equals(admission.requestedPath))
            {
                closeQuietly(out);
                return null;
            }
            admission.state.lock.lock();
            try
            {
                if (admission.state.generation != admission.generation)
                {
                    closeQuietly(out);
                    return null;
                }
                SseStream stream = new SseStream(out, sessionId, path, protocolVersion);
                streams.add(stream);
                afterAdd.run();
                if (admission.state.generation != admission.generation)
                {
                    if (streams.remove(stream))
                    {
                        stream.closeQuietly();
                    }
                    return null;
                }
                return stream;
            }
            finally
            {
                admission.state.lock.unlock();
            }
        }
        finally
        {
            release(admission);
        }
    }

    /**
     * Removes a stream from the registry (on disconnect / close).
     *
     * @param stream the handle returned by {@link #register}
     */
    public void unregister(SseStream stream)
    {
        if (stream != null)
        {
            streams.remove(stream);
        }
    }

    /**
     * Removes and closes every stream bound to {@code sessionId}. No-op when the id
     * is blank. A stream is removed before its output is closed so heartbeat and
     * broadcast iterations cannot discover it again; close failures are isolated to
     * that stream.
     */
    public void unregisterBySession(String sessionId)
    {
        if (sessionId == null || sessionId.isBlank())
        {
            return;
        }
        for (SseStream stream : streams)
        {
            if (sessionId.equals(stream.getSessionId()))
            {
                if (streams.remove(stream))
                {
                    stream.closeQuietly();
                }
            }
        }
    }

    /** Number of currently-open server-initiated SSE streams. */
    public int activeStreamCount()
    {
        return streams.size();
    }

    /**
     * Distinct requested paths that currently have at least one open stream.
     */
    public Set<String> activeRequestedPaths()
    {
        Set<String> paths = new LinkedHashSet<>();
        for (SseStream stream : streams)
        {
            paths.add(stream.getRequestedPath());
        }
        return Set.copyOf(paths);
    }

    /**
     * Drops every stream bound to {@code requestedPath} and closes their sockets
     * so the heartbeat loop exits. Used when that profile's surface changes.
     *
     * @param requestedPath canonical endpoint path
     * @return how many streams were closed
     */
    public int closeByRequestedPath(String requestedPath)
    {
        if (requestedPath == null || requestedPath.isBlank())
        {
            return 0;
        }
        String path = normalizePath(requestedPath);
        RequestedPathState[] selected = new RequestedPathState[1];
        requestedPathStates.compute(path, (key, state) -> {
            RequestedPathState current = state == null ? new RequestedPathState() : state;
            current.kicksInProgress++;
            selected[0] = current;
            return current;
        });
        RequestedPathState state = selected[0];
        int closed = 0;
        state.lock.lock();
        try
        {
            state.generation++;
            for (SseStream stream : streams)
            {
                if (path.equals(stream.getRequestedPath()) && streams.remove(stream))
                {
                    stream.closeQuietly();
                    closed++;
                }
            }
        }
        finally
        {
            state.lock.unlock();
            requestedPathStates.computeIfPresent(path, (key, current) -> {
                if (current == state)
                {
                    current.kicksInProgress--;
                    return current.pendingAdmissions == 0 && current.kicksInProgress == 0 ? null : current;
                }
                return current;
            });
        }
        return closed;
    }

    private void release(RequestedPathAdmission admission)
    {
        if (admission == null || !admission.released.compareAndSet(false, true))
        {
            return;
        }
        requestedPathStates.computeIfPresent(admission.requestedPath, (key, state) -> {
            if (state == admission.state)
            {
                state.pendingAdmissions--;
                return state.pendingAdmissions == 0 && state.kicksInProgress == 0 ? null : state;
            }
            return state;
        });
    }

    /** Releases an admission that never reached stream registration. Idempotent. */
    public void releaseAdmission(RequestedPathAdmission admission)
    {
        release(admission);
    }

    /** Drops queued admission state after the SSE executor has been stopped. */
    public void clearPendingAdmissions()
    {
        requestedPathStates.clear();
    }

    int pendingAdmissionPathCount()
    {
        return requestedPathStates.size();
    }

    private static String normalizePath(String requestedPath)
    {
        return requestedPath == null || requestedPath.isBlank() ? McpEndpoint.LEGACY_PATH : requestedPath;
    }

    private static void closeQuietly(OutputStream out)
    {
        try
        {
            out.close();
        }
        catch (IOException ignored)
        {
            // A stale queued GET has no registry entry; closing is best effort.
        }
    }

    /** Admission token held only while an SSE GET waits for registration. */
    public static final class RequestedPathAdmission
    {
        private final String requestedPath;
        private final RequestedPathState state;
        private final long generation;
        private final AtomicBoolean released = new AtomicBoolean();

        private RequestedPathAdmission(String requestedPath, RequestedPathState state, long generation)
        {
            this.requestedPath = requestedPath;
            this.state = state;
            this.generation = generation;
        }
    }

    private static final class RequestedPathState
    {
        final ReentrantLock lock = new ReentrantLock();
        long generation;
        int pendingAdmissions;
        int kicksInProgress;
    }

    /**
     * Pushes a JSON-RPC message to every open SSE stream as an {@code event: message}
     * frame. Streams that fail to accept the write (client gone) are dropped. Returns
     * how many streams received it.
     *
     * @param jsonRpcMessage the serialized JSON-RPC notification/request
     * @return the number of streams the message was written to
     */
    public int broadcast(String jsonRpcMessage)
    {
        return deliver(jsonRpcMessage, null, false);
    }

    /**
     * Pushes {@code notifications/tools/list_changed} to legacy {@code /mcp} streams,
     * once per session. Used by progressive disclosure ({@code enable_toolset}).
     *
     * @return the number of sessions notified
     */
    public int notifyToolsListChanged()
    {
        return notifyToolsListChanged(McpEndpoint.LEGACY_PATH);
    }

    /**
     * Pushes {@code notifications/tools/list_changed} to streams of one requested path,
     * once per session.
     *
     * @param requestedPath canonical endpoint path
     * @return the number of sessions notified
     */
    public int notifyToolsListChanged(String requestedPath)
    {
        return deliver(LIST_CHANGED, requestedPath, true);
    }

    private int deliver(String jsonRpcMessage, String requestedPath, boolean oncePerSession)
    {
        if (jsonRpcMessage == null || streams.isEmpty())
        {
            return 0;
        }
        long id = eventId.incrementAndGet();
        String frame = "event: message\nid: " + id + "\ndata: " + jsonRpcMessage + "\n\n"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        Set<String> deliveredSessions = new LinkedHashSet<>();
        int delivered = 0;
        for (SseStream stream : streams)
        {
            if (requestedPath != null && !requestedPath.equals(stream.getRequestedPath()))
            {
                continue;
            }
            if (oncePerSession)
            {
                String sessionKey = stream.sessionKey();
                if (!deliveredSessions.add(sessionKey))
                {
                    continue;
                }
            }
            try
            {
                stream.write(frame);
                delivered++;
            }
            catch (IOException e)
            {
                streams.remove(stream);
            }
        }
        return delivered;
    }

    /**
     * One open SSE stream. Writes (heartbeat + broadcast) are serialized by the
     * per-instance lock so frames never interleave.
     */
    public static final class SseStream
    {
        private final OutputStream out;
        private final String sessionId;
        private final String requestedPath;
        private final String protocolVersion;
        private final Object lock = new Object();

        SseStream(OutputStream out)
        {
            this(out, null, McpEndpoint.LEGACY_PATH, null);
        }

        SseStream(OutputStream out, String sessionId, String requestedPath)
        {
            this(out, sessionId, requestedPath, null);
        }

        SseStream(OutputStream out, String sessionId, String requestedPath, String protocolVersion)
        {
            this.out = out;
            this.sessionId = sessionId;
            this.requestedPath = requestedPath;
            this.protocolVersion = protocolVersion;
        }

        public String getSessionId()
        {
            return sessionId;
        }

        public String getRequestedPath()
        {
            return requestedPath;
        }

        public String getProtocolVersion()
        {
            return protocolVersion;
        }

        public void closeQuietly()
        {
            try
            {
                // Do not take the write lock here: close must be able to unblock a
                // network write/flush that is currently stuck while holding it.
                out.close();
            }
            catch (IOException ignored)
            {
                // The stream has already been removed; other streams still close.
            }
        }

        String sessionKey()
        {
            return sessionId != null ? sessionId : ("anon:" + Integer.toHexString(System.identityHashCode(this))); //$NON-NLS-1$
        }

        /**
         * Writes a raw SSE chunk (an {@code event:} frame or a {@code :} heartbeat
         * comment) and flushes. Serialized against concurrent writers.
         *
         * @param chunk the SSE text to write
         * @throws IOException if the client has disconnected
         */
        public void write(String chunk) throws IOException
        {
            byte[] bytes = chunk.getBytes(StandardCharsets.UTF_8);
            synchronized (lock)
            {
                out.write(bytes);
                out.flush();
            }
        }
    }
}
