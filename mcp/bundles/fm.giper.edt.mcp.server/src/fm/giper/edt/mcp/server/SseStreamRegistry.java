/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Registry of the open server-initiated SSE streams (the standalone {@code GET}
 * {@code text/event-stream} connections per the MCP Streamable HTTP transport),
 * plus broadcasts that push a JSON-RPC message to all streams or only one endpoint.
 * <p>
 * This is how the server delivers notifications it originates — currently
 * {@code notifications/tools/list_changed}, from the two things that change the
 * {@code tools/list} surface: {@code enable_toolset} revealing a toolset under
 * progressive disclosure, profile changes, and the Tools preference tab enabling/disabling tools. A
 * client only receives it if it keeps a GET SSE stream open; clients that don't fall
 * back to re-requesting {@code tools/list} (the pull path that {@code enable_toolset}
 * points them to).
 * <p>
 * Thread-safe: streams are added/removed from a concurrent set, and each stream's
 * writes (heartbeat from its own SSE thread, broadcasts from a request thread or the
 * preferences' own sender thread) are serialized by the per-stream lock in
 * {@link SseStream}.
 */
public final class SseStreamRegistry // NOSONAR intentional singleton (Eclipse service / getInstance); a single instance is by design
{
    private static final SseStreamRegistry INSTANCE = new SseStreamRegistry();

    private final Set<SseStream> streams = ConcurrentHashMap.newKeySet();
    private final AtomicLong eventId = new AtomicLong(0);
    private final ExecutorService notificationExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "MCP-SSE-notifications"); //$NON-NLS-1$
        thread.setDaemon(true);
        return thread;
    });

    private SseStreamRegistry()
    {
        // Singleton
    }

    public static SseStreamRegistry getInstance()
    {
        return INSTANCE;
    }

    /**
     * Registers an open SSE stream's output. The transport calls this when a GET SSE
     * stream opens and {@link #unregister} when it closes.
     *
     * @param out the SSE stream's output stream
     * @return the registered handle (used for heartbeat writes and unregister)
     */
    public SseStream register(OutputStream out)
    {
        return register(out, "/mcp"); //$NON-NLS-1$
    }

    /** Registers a stream with its request route. */
    public SseStream register(OutputStream out, String requestedPath)
    {
        SseStream stream = new SseStream(out, requestedPath);
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

    /** Number of currently-open server-initiated SSE streams. */
    public int activeStreamCount()
    {
        return streams.size();
    }

    /** Distinct endpoint paths represented by currently open streams. */
    public Set<String> activeRequestedPaths()
    {
        Set<String> paths = new LinkedHashSet<>();
        for (SseStream stream : streams)
        {
            paths.add(stream.getRequestedPath());
        }
        return Set.copyOf(paths);
    }

    /** Closes all streams attached to one endpoint path. */
    public int closeByRequestedPath(String requestedPath)
    {
        if (requestedPath == null || requestedPath.isBlank())
        {
            return 0;
        }
        String path = normalizePath(requestedPath);
        int closed = 0;
        for (SseStream stream : streams)
        {
            if (path.equals(stream.getRequestedPath()) && streams.remove(stream))
            {
                stream.closeQuietly();
                closed++;
            }
        }
        return closed;
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
        if (jsonRpcMessage == null || streams.isEmpty())
        {
            return 0;
        }
        long id = eventId.incrementAndGet();
        String frame = "event: message\nid: " + id + "\ndata: " + jsonRpcMessage + "\n\n"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        int delivered = 0;
        for (SseStream stream : streams)
        {
            try
            {
                stream.write(frame);
                delivered++;
            }
            catch (IOException e)
            {
                // Client disconnected mid-write: drop the stream (its own heartbeat
                // loop will also break and unregister, which is idempotent).
                streams.remove(stream);
            }
        }
        return delivered;
    }

    /**
     * Pushes {@code notifications/tools/list_changed} to legacy {@code /mcp} SSE streams,
     * telling those clients their {@code tools/list} surface changed (e.g. a toolset was revealed).
     *
     * @return the number of streams notified
     */
    public int notifyToolsListChanged()
    {
        fm.giper.edt.mcp.server.transport.ToolsChangedListeners.notifyChanged();
        return broadcastToPath("/mcp", "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/tools/list_changed\"}"); //$NON-NLS-1$
    }

    /** Queues a profile-scoped advisory so preference writes never wait on a slow SSE client. */
    public void notifyToolsListChangedAsync(String requestedPath)
    {
        if (requestedPath != null && !requestedPath.isBlank())
        {
            String path = normalizePath(requestedPath);
            notificationExecutor.execute(() -> notifyToolsListChanged(path));
        }
    }

    /** Sends the advisory only to streams attached to the affected endpoint. */
    public int notifyToolsListChanged(String requestedPath)
    {
        if (requestedPath == null || requestedPath.isBlank())
        {
            return 0;
        }
        String path = normalizePath(requestedPath);
        return broadcastToPath(path, "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/tools/list_changed\"}"); //$NON-NLS-1$
    }

    private int broadcastToPath(String requestedPath, String jsonRpcMessage)
    {
        if (streams.isEmpty())
        {
            return 0;
        }
        long id = eventId.incrementAndGet();
        String frame = "event: message\nid: " + id + "\ndata: " + jsonRpcMessage + "\n\n"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        int delivered = 0;
        for (SseStream stream : streams)
        {
            if (!requestedPath.equals(stream.getRequestedPath()))
            {
                continue;
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

    private static String normalizePath(String path)
    {
        if (path == null || path.isBlank())
        {
            return "/mcp"; //$NON-NLS-1$
        }
        return path.startsWith("/") ? path : "/" + path; //$NON-NLS-1$ //$NON-NLS-2$
    }

    /**
     * One open SSE stream. Writes (heartbeat + broadcast) are serialized by the
     * per-instance lock so frames never interleave.
     */
    public static final class SseStream
    {
        private final OutputStream out;
        private final String requestedPath;
        private final Object lock = new Object();

        SseStream(OutputStream out)
        {
            this(out, "/mcp"); //$NON-NLS-1$
        }

        SseStream(OutputStream out, String requestedPath)
        {
            this.out = out;
            this.requestedPath = normalizePath(requestedPath);
        }

        public String getRequestedPath()
        {
            return requestedPath;
        }

        public void closeQuietly()
        {
            try
            {
                out.close();
            }
            catch (IOException ignored)
            {
                // Closed streams are removed before close failures are ignored.
            }
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
