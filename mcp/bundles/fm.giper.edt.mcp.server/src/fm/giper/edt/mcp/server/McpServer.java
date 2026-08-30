/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import fm.giper.edt.mcp.server.preferences.PreferenceConstants;
import fm.giper.edt.mcp.server.profiles.ToolProfileMigration;
import fm.giper.edt.mcp.server.SseStreamRegistry;
import fm.giper.edt.mcp.server.protocol.McpProtocolHandler;
import fm.giper.edt.mcp.server.tools.BuiltInToolRegistrar;
import fm.giper.edt.mcp.server.tools.McpToolRegistry;
import fm.giper.edt.mcp.server.transport.HealthHandler;
import fm.giper.edt.mcp.server.transport.InterruptibleToolExecutor;
import fm.giper.edt.mcp.server.transport.McpHttpHandler;
import fm.giper.edt.mcp.server.transport.McpSessionRegistry;
import com.sun.net.httpserver.HttpServer;

/**
 * MCP Server for EDT.
 * Provides HTTP endpoint for MCP clients.
 */
public class McpServer
{
    private HttpServer server;
    private int port;
    private volatile boolean running = false;
    
    /** Request counter - use AtomicLong for thread safety */
    private final AtomicLong requestCount = new AtomicLong(0);
    
    /** Concurrent in-flight tool calls; status UI reads selected-or-last only. */
    private final ActiveToolCallRegistry activeToolCalls = new ActiveToolCallRegistry();

    /** The call being executed on this worker thread, if any. */
    private final ThreadLocal<ActiveToolCall> executingCall = new ThreadLocal<>();

    /** Path-bound MCP sessions for the 2025-11-25 transport. */
    private final McpSessionRegistry sessionRegistry = new McpSessionRegistry();

    /**
     * After the {@code default} profile surface changes, sessionless {@code /mcp}
     * must initialize again instead of silently seeing a new allowlist.
     */
    private volatile boolean legacySessionRequired;

    public McpServer()
    {
        sessionRegistry.setSessionClosedListener(
            id -> SseStreamRegistry.getInstance().unregisterBySession(id));
    }

    /** Main thread pool for POST/OPTIONS/DELETE requests */
    private ThreadPoolExecutor mainExecutor;

    /** Dedicated thread pool for long-lived SSE connections (isolated from main request pool) */
    private ExecutorService sseExecutor;

    /**
     * Starts the MCP server on the specified port.
     * 
     * @param port the port number
     * @throws IOException if startup fails
     */
    public synchronized void start(int port) throws IOException
    {
        if (running)
        {
            stop();
        }

        // Register tools
        registerTools();
        
        // Create protocol handler
        McpProtocolHandler protocolHandler = new McpProtocolHandler();

        this.port = port;

        // Configure HTTP server idle interval (seconds) to prevent premature connection drops
        // This sets the time the server waits before closing idle connections
        System.setProperty("sun.net.httpserver.idleInterval", "300"); //$NON-NLS-1$ //$NON-NLS-2$
        // Increase max idle connections to handle concurrent MCP clients
        System.setProperty("sun.net.httpserver.maxIdleConnections", "32"); //$NON-NLS-1$ //$NON-NLS-2$
        // Increase max request time to allow long-running tool operations (10 minutes)
        System.setProperty("sun.net.httpserver.maxReqTime", "600"); //$NON-NLS-1$ //$NON-NLS-2$
        // Increase max response time to allow large responses (10 minutes)
        System.setProperty("sun.net.httpserver.maxRspTime", "600"); //$NON-NLS-1$ //$NON-NLS-2$

        // Bind to loopback only by default: the tool surface includes arbitrary-BSL
        // (evaluate_expression) and destructive tools, so it must not be reachable
        // from the network unless explicitly opted in. Set PREF_ALLOW_REMOTE_ACCESS
        // to expose on all interfaces (pair it with PREF_AUTH_TOKEN).
        boolean allowRemote = false;
        Activator activator = Activator.getDefault();
        if (activator != null)
        {
            allowRemote = activator.getPreferenceStore()
                .getBoolean(PreferenceConstants.PREF_ALLOW_REMOTE_ACCESS);
        }
        InetSocketAddress bindAddress = allowRemote
            ? new InetSocketAddress(port)
            : new InetSocketAddress(InetAddress.getLoopbackAddress(), port);
        server = HttpServer.create(bindAddress, 0);
        Activator.logInfo("MCP Server binding to " //$NON-NLS-1$
            + (allowRemote ? "all interfaces (remote access enabled)" : "loopback only") //$NON-NLS-1$ //$NON-NLS-2$
            + " on port " + port); //$NON-NLS-1$
        if (allowRemote)
        {
            String authToken = activator != null
                ? activator.getPreferenceStore().getString(PreferenceConstants.PREF_AUTH_TOKEN)
                : ""; //$NON-NLS-1$
            if (authToken == null || authToken.isEmpty())
            {
                Activator.logWarning("SECURITY: MCP server is bound to all interfaces with NO auth token. " //$NON-NLS-1$
                    + "Any host that can reach this port can invoke every tool (including arbitrary BSL). " //$NON-NLS-1$
                    + "Set an auth token in MCP preferences."); //$NON-NLS-1$
            }
        }

        // MCP endpoints. The transport handlers read the live executors and
        // execution state back through this server instance.
        InterruptibleToolExecutor interruptibleExecutor =
            new InterruptibleToolExecutor(this, protocolHandler);
        server.createContext("/mcp", new McpHttpHandler(this, protocolHandler, interruptibleExecutor)); //$NON-NLS-1$
        server.createContext("/health", new HealthHandler()); //$NON-NLS-1$

        // Main thread pool for POST/OPTIONS/DELETE requests (finite-duration only).
        // Two-level overload protection:
        //   1. Admission control in handle() at threshold 50 — returns 503 instantly
        //   2. Bounded queue (200) — memory safety net; gap of 150 between admission
        //      threshold and queue capacity ensures admission control always drains
        //      the queue before executor rejection can occur.
        // SSE (the only infinite-duration request type) is handled by the dedicated
        // sseExecutor and never enters this pool.
        // Daemon threads: a worker still handling a request at EDT shutdown
        // must not keep the JVM alive after the workbench has closed (#135).
        AtomicInteger mainThreadCounter = new AtomicInteger();
        mainExecutor = new ThreadPoolExecutor(
            8, 8, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(200),
            r -> {
                Thread t = new Thread(r, "MCP-Main-" + mainThreadCounter.incrementAndGet()); //$NON-NLS-1$
                t.setDaemon(true);
                return t;
            });
        mainExecutor.allowCoreThreadTimeOut(true);
        server.setExecutor(mainExecutor);

        // Dedicated bounded pool for SSE streams (long-lived heartbeat connections).
        // Hard limit of 10 concurrent SSE connections prevents unbounded thread growth.
        // SynchronousQueue ensures immediate handoff; rejection is caught in
        // handleSseInDedicatedPool() and returned as HTTP 503.
        sseExecutor = new ThreadPoolExecutor(
            0, 10, 60L, TimeUnit.SECONDS,
            new SynchronousQueue<>(),
            r -> {
                Thread t = new Thread(r, "MCP-SSE-" + System.currentTimeMillis()); //$NON-NLS-1$
                t.setDaemon(true);
                return t;
            });
        server.start();
        running = true;
        
        Activator.logInfo("MCP Server started on port " + port); //$NON-NLS-1$
    }

    /**
     * Registers all MCP tools. Package-private so Activator can call it early
     * to populate descriptions for the preferences UI even if the server hasn't started.
     */
    void registerTools()
    {
        BuiltInToolRegistrar.registerAll(McpToolRegistry.getInstance());
        ToolProfileMigration.migrateIfNeeded();
    }

    /**
     * Stops the MCP server.
     */
    public synchronized void stop()
    {
        if (server != null)
        {
            server.stop(1);
            server = null;
            running = false;
            if (mainExecutor != null)
            {
                mainExecutor.shutdownNow();
                mainExecutor = null;
            }
            if (sseExecutor != null)
            {
                sseExecutor.shutdownNow();
                sseExecutor = null;
            }
            sessionRegistry.shutdown();
            Activator.logInfo("MCP Server stopped"); //$NON-NLS-1$
        }
    }

    /**
     * Restarts the MCP server.
     * 
     * @param port the port number
     * @throws IOException if restart fails
     */
    public void restart(int port) throws IOException
    {
        stop();
        start(port);
    }

    /**
     * Checks if the server is running.
     * 
     * @return true if server is running
     */
    public boolean isRunning()
    {
        return running;
    }

    /**
     * Returns the current port.
     * 
     * @return port number
     */
    public int getPort()
    {
        return port;
    }

    /**
     * Returns the main request thread pool (POST/OPTIONS/DELETE). Used by the
     * transport handler for admission control. May be {@code null} when stopped.
     *
     * @return the main executor or null
     */
    public ThreadPoolExecutor getMainExecutor()
    {
        return mainExecutor;
    }

    /**
     * Path-bound MCP sessions for initialize / POST / GET / DELETE.
     *
     * @return the live session registry
     */
    public McpSessionRegistry getSessionRegistry()
    {
        return sessionRegistry;
    }

    /**
     * Whether legacy {@code /mcp} now requires a session (set when {@code default} changes).
     */
    public boolean isLegacySessionRequired()
    {
        return legacySessionRequired;
    }

    /**
     * Marks sessionless {@code /mcp} as stale after the default profile surface changes.
     */
    public void setLegacySessionRequired(boolean required)
    {
        this.legacySessionRequired = required;
    }

    /**
     * Concurrent in-flight tool calls, keyed by internal call id.
     *
     * @return the live registry
     */
    public ActiveToolCallRegistry getActiveToolCallRegistry()
    {
        return activeToolCalls;
    }

    /**
     * Binds this worker thread to {@code call} so {@link #consumeUserSignal()}
     * and {@link #setCurrentToolName(String)} affect that call only.
     *
     * @param call the call this thread is running, or {@code null} to unbind
     */
    public void bindExecutingCall(ActiveToolCall call)
    {
        if (call == null)
        {
            executingCall.remove();
        }
        else
        {
            executingCall.set(call);
        }
    }

    /**
     * Returns the dedicated SSE thread pool. Used by the transport handler to
     * offload long-lived SSE streams. May be {@code null} when stopped.
     *
     * @return the SSE executor or null
     */
    public ExecutorService getSseExecutor()
    {
        return sseExecutor;
    }

    /**
     * Returns the request count.
     * 
     * @return number of requests processed
     */
    public long getRequestCount()
    {
        return requestCount.get();
    }

    /**
     * Increments the request counter.
     */
    public void incrementRequestCount()
    {
        requestCount.incrementAndGet();
    }

    /**
     * Returns the currently executing tool name.
     * 
     * @return tool name or null if no tool is executing
     */
    private volatile String statusDisplay;

    public String getCurrentToolName()
    {
        ActiveToolCall call = displayCall();
        return call == null ? statusDisplay : formatStatus(call.getToolName(), lastRequestedProfile,
            lastEffectiveProfile, lastFallback);
    }

    private volatile String lastRequestedProfile;
    private volatile String lastEffectiveProfile;
    private volatile boolean lastFallback;

    public void setStatusContext(String toolName, String requestedProfileId, String effectiveProfileId,
        boolean fallbackApplied)
    {
        lastRequestedProfile = requestedProfileId;
        lastEffectiveProfile = effectiveProfileId;
        lastFallback = fallbackApplied;
        statusDisplay = formatStatus(toolName, requestedProfileId, effectiveProfileId, fallbackApplied);
    }

    static String formatStatus(String toolName, String requested, String effective, boolean fallback)
    {
        if (toolName == null)
        {
            return null;
        }
        if (requested == null || requested.isBlank())
        {
            return toolName;
        }
        if (fallback && effective != null && !requested.equals(effective))
        {
            return "[" + requested + " -> " + effective + "]: " + toolName; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        }
        return "[" + (effective == null ? requested : effective) + "]: " + toolName; //$NON-NLS-1$ //$NON-NLS-2$
    }

    /**
     * Status-bar hook: does not change which call owns a queued signal.
     * A {@code null} name is ignored so finishing one call cannot wipe a neighbor.
     *
     * @param toolName unused for runtime isolation (kept for the protocol handler)
     */
    public void setCurrentToolName(String toolName)
    {
        // Kept for the protocol handler. Runtime ownership lives on the bound
        // call; the status bar reads selected-or-last from the registry.
    }

    /**
     * Checks if a tool is currently executing.
     * 
     * @return true if a tool is executing
     */
    public boolean isToolExecuting()
    {
        return displayCall() != null;
    }

    /**
     * Returns the elapsed time in seconds since tool execution started.
     * 
     * @return elapsed seconds or 0 if no tool is executing
     */
    public long getToolExecutionSeconds()
    {
        ActiveToolCall call = displayCall();
        return call == null ? 0 : call.getElapsedSeconds();
    }

    /**
     * Queues a user signal on the call bound to this thread, else the
     * selected-or-last call. Never writes a global slot.
     *
     * @param signal the user signal
     */
    public void setUserSignal(UserSignal signal)
    {
        ActiveToolCall target = executingCall.get();
        if (target == null)
        {
            target = displayCall();
        }
        if (target != null)
        {
            activeToolCalls.offerSignal(target.getCallId(), signal);
        }
    }

    /**
     * Gets and clears the user signal for the call bound to this thread.
     * A neighbor's signal is never returned.
     *
     * @return the user signal or null
     */
    public UserSignal consumeUserSignal()
    {
        ActiveToolCall bound = executingCall.get();
        if (bound != null)
        {
            return activeToolCalls.consumeSignal(bound.getCallId());
        }
        return null;
    }

    /**
     * Registers an in-flight call. Replaces the old single-slot setter.
     *
     * @param toolCall the active tool call
     */
    public void setActiveToolCall(ActiveToolCall toolCall)
    {
        activeToolCalls.register(toolCall);
    }

    /**
     * Status-bar / last-call view of the registry. Runtime isolation uses
     * {@link #getActiveToolCallRegistry()}.
     *
     * @return the selected or last live call, or null
     */
    public ActiveToolCall getActiveToolCall()
    {
        return displayCall();
    }

    /**
     * Unregisters the call bound to this thread, else the selected-or-last call.
     * Prefer {@link #clearActiveToolCall(ActiveToolCall)} when the caller knows the id.
     */
    public void clearActiveToolCall()
    {
        ActiveToolCall bound = executingCall.get();
        if (bound != null)
        {
            activeToolCalls.unregister(bound.getCallId());
            return;
        }
        ActiveToolCall display = displayCall();
        if (display != null)
        {
            activeToolCalls.unregister(display.getCallId());
        }
    }

    /**
     * Unregisters exactly one call. Finishing it does not clear a neighbor.
     *
     * @param toolCall the call that finished
     */
    public void clearActiveToolCall(ActiveToolCall toolCall)
    {
        if (toolCall != null)
        {
            activeToolCalls.unregister(toolCall.getCallId());
            if (toolCall.equals(executingCall.get()))
            {
                executingCall.remove();
            }
        }
    }

    /**
     * Interrupts the selected-or-last call only (status-bar action).
     *
     * @param signal the user signal
     * @return true if that call was interrupted successfully
     */
    public boolean interruptToolCall(UserSignal signal)
    {
        return activeToolCalls.interruptSelectedOrLast(signal);
    }

    /**
     * Interrupts one call by internal id.
     *
     * @param callId the target call
     * @param signal the user signal
     * @return true if that call was interrupted
     */
    public boolean interruptToolCall(String callId, UserSignal signal)
    {
        return activeToolCalls.interrupt(callId, signal);
    }

    private ActiveToolCall displayCall()
    {
        ActiveToolCall bound = executingCall.get();
        if (bound != null && activeToolCalls.get(bound.getCallId()) != null)
        {
            return bound;
        }
        return activeToolCalls.getSelectedOrLast();
    }
}
