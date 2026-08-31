/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.proxy;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Client GET/SSE registry keyed by endpoint. A backend {@code notifications/tools/list_changed}
 * invalidates only that profile snapshot; delivery is de-duplicated per client stream.
 */
public final class SseNotificationHub
{
    public interface ProfileInvalidator
    {
        void invalidateRequestedProfile(String requestedProfileId);
    }

    public interface SessionCloser
    {
        void closeByCanonicalPath(String canonicalPath);
    }

    private static final String LIST_CHANGED =
        "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/tools/list_changed\"}"; //$NON-NLS-1$

    private final Map<String, List<ClientStream>> clientsByPath = new ConcurrentHashMap<>();
    private final AtomicLong eventId = new AtomicLong(0);
    private final AtomicInteger delivered = new AtomicInteger(0);
    private volatile ProfileInvalidator invalidator;
    private volatile SessionCloser sessionCloser;

    public void setInvalidator(ProfileInvalidator invalidator)
    {
        this.invalidator = invalidator;
    }

    public void setSessionCloser(SessionCloser sessionCloser)
    }

    public void registerClient(String canonicalPath, OutputStream out)
    {
            .add(new ClientStream(out));
    }

    public void unregisterClient(String canonicalPath, OutputStream out)
    {
        List<ClientStream> streams = clientsByPath.get(canonicalPath);
        if (streams == null)
        {
            return;
        }
        streams.removeIf(stream -> stream.out == out);
    }

    /**
     * Handles a backend list-changed for one requested profile: invalidate that snapshot,
     * then deliver one notification to each client on that endpoint (de-duplicated).
     *
     * @param requestedProfileId the profile whose surface changed
     * @param canonicalPath the client path that should receive the event
     */
    public void onBackendListChanged(String requestedProfileId, String canonicalPath)
    {
        ProfileInvalidator current = invalidator;
        if (current != null)
        {
            current.invalidateRequestedProfile(requestedProfileId);
        }
        SessionCloser closer = sessionCloser;
        if (closer != null && canonicalPath != null)
        {
            closer.closeByCanonicalPath(canonicalPath);
            if (ProfileEndpoint.DEFAULT_PROFILE_ID.equals(requestedProfileId)
                || ProfileEndpoint.LEGACY_PATH.equals(canonicalPath))
            {
                closer.closeByCanonicalPath(ProfileEndpoint.LEGACY_PATH);
            }
        }
        closeClientsOnPath(canonicalPath);
        if (ProfileEndpoint.DEFAULT_PROFILE_ID.equals(requestedProfileId))
        {
            closeClientsOnPath(ProfileEndpoint.LEGACY_PATH);
        }
    }

    /**
     * Closes client GET/SSE streams on {@code canonicalPath} so their heartbeat loop exits.
     */
    public void closeClientsOnPath(String canonicalPath)
    {
        if (canonicalPath == null)
        {
            return;
        }
        List<ClientStream> streams = clientsByPath.remove(canonicalPath);
        if (streams == null)
        {
            return;
        }
        for (ClientStream stream : streams)
        {
            try
            {
                stream.out.close();
            }
            catch (IOException ignored)
            {
                // heartbeat loop exits
            }
        }
    }

    public void publishListChanged(String canonicalPath)
    {
        String frame = "event: message\nid: " + eventId.incrementAndGet() //$NON-NLS-1$
            + "\ndata: " + LIST_CHANGED + "\n\n"; //$NON-NLS-1$ //$NON-NLS-2$
        byte[] bytes = frame.getBytes(StandardCharsets.UTF_8);
        List<ClientStream> streams = clientsByPath.get(canonicalPath);
        if (streams == null)
        {
            return;
        }
        for (ClientStream stream : streams)
        {
            if (stream.deliveredListChanged.compareAndSet(false, true))
            {
                try
                {
                    stream.out.write(bytes);
                    stream.out.flush();
                    delivered.incrementAndGet();
                }
                catch (IOException ignored)
                {
                    // client gone
                }
            }
        }
        for (ClientStream stream : streams)
        {
            stream.deliveredListChanged.set(false);
        }
    }

    int deliveredCount()
    {
        return delivered.get();
    }

    private static final class ClientStream
    {
        final OutputStream out;
        final AtomicBoolean deliveredListChanged = new AtomicBoolean(false);

        ClientStream(OutputStream out)
        {
            this.out = out;
        }
    }
}
