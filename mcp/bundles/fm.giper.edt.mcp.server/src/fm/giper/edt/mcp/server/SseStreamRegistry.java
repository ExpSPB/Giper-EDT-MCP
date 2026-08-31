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
import java.util.concurrent.atomic.AtomicLong;

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
        SseStream stream = new SseStream(out, sessionId,
            requestedPath == null || requestedPath.isBlank() ? McpEndpoint.LEGACY_PATH : requestedPath);
        streams.add(stream);
        return stream;
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
     * Drops every stream bound to {@code sessionId}. No-op when the id is blank.
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
                streams.remove(stream);
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
        private final Object lock = new Object();

        SseStream(OutputStream out)
        {
            this(out, null, McpEndpoint.LEGACY_PATH);
        }

        SseStream(OutputStream out, String sessionId, String requestedPath)
        {
            this.out = out;
            this.sessionId = sessionId;
            this.requestedPath = requestedPath;
        }

        public String getSessionId()
        {
            return sessionId;
        }

        public String getRequestedPath()
        {
            return requestedPath;
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
