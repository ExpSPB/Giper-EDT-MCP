/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Concurrent in-flight {@link ActiveToolCall} table, keyed by an internal call id.
 * An interrupt or queued {@link UserSignal} belongs to one call and is never
 * consumed by a neighbor. Status UI may show {@link #getSelectedOrLast()};
 * runtime state stays split.
 */
public final class ActiveToolCallRegistry
{
    private final ConcurrentHashMap<String, ActiveToolCall> calls = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1L);
    private final AtomicReference<String> lastCallId = new AtomicReference<>();
    private final AtomicReference<String> selectedCallId = new AtomicReference<>();

    /**
     * Registers {@code call}, assigning an id when it has none.
     *
     * @param call the in-flight call (ignored when {@code null})
     * @return the registered call, or {@code null}
     */
    public ActiveToolCall register(ActiveToolCall call)
    {
        if (call == null)
        {
            return null;
        }
        if (call.getCallId() == null || call.getCallId().isBlank())
        {
            call.assignCallId(newCallId());
        }
        calls.put(call.getCallId(), call);
        lastCallId.set(call.getCallId());
        return call;
    }

    /**
     * @return a fresh internal call id
     */
    public String newCallId()
    {
        return "call-" + nextId.getAndIncrement(); //$NON-NLS-1$
    }

    /**
     * Removes one call. Other in-flight calls are left untouched.
     *
     * @param callId the internal id
     * @return the removed call, or {@code null}
     */
    public ActiveToolCall unregister(String callId)
    {
        if (callId == null)
        {
            return null;
        }
        ActiveToolCall removed = calls.remove(callId);
        selectedCallId.compareAndSet(callId, null);
        lastCallId.compareAndSet(callId, newestRemainingId());
        return removed;
    }

    /**
     * @param callId the internal id
     * @return the live call, or {@code null}
     */
    public ActiveToolCall get(String callId)
    {
        return callId == null ? null : calls.get(callId);
    }

    /**
     * Marks {@code callId} as the status-bar selection when it is still live.
     *
     * @param callId the internal id
     */
    public void select(String callId)
    {
        if (callId != null && calls.containsKey(callId))
        {
            selectedCallId.set(callId);
        }
    }

    /**
     * Status-bar target: the selected live call, else the most recently registered
     * one that is still live.
     *
     * @return the display call, or {@code null} when nothing is in flight
     */
    public ActiveToolCall getSelectedOrLast()
    {
        String selected = selectedCallId.get();
        ActiveToolCall chosen = selected == null ? null : calls.get(selected);
        if (chosen != null)
        {
            return chosen;
        }
        String last = lastCallId.get();
        if (last != null)
        {
            ActiveToolCall live = calls.get(last);
            if (live != null)
            {
                return live;
            }
        }
        return newestRemaining();
    }

    /**
     * @return every live call, in no particular order
     */
    public List<ActiveToolCall> snapshot()
    {
        return new ArrayList<>(calls.values());
    }

    /**
     * @return how many calls are currently registered
     */
    public int size()
    {
        return calls.size();
    }

    /**
     * Sends {@code signal} on exactly one call. A neighbor cannot consume it.
     *
     * @param callId the target call
     * @param signal the user signal
     * @return {@code true} when that call accepted the interrupt
     */
    public boolean interrupt(String callId, UserSignal signal)
    {
        ActiveToolCall call = get(callId);
        if (call == null || signal == null || call.hasResponded())
        {
            return false;
        }
        boolean sent = call.sendSignalResponse(signal);
        if (sent)
        {
            unregister(callId);
        }
        return sent;
    }

    /**
     * Interrupts the status-bar target only.
     *
     * @param signal the user signal
     * @return {@code true} when that call accepted the interrupt
     */
    public boolean interruptSelectedOrLast(UserSignal signal)
    {
        ActiveToolCall call = getSelectedOrLast();
        return call != null && interrupt(call.getCallId(), signal);
    }

    /**
     * Queues {@code signal} on one call so {@link #consumeSignal(String)} later
     * returns it to that call alone.
     *
     * @param callId the target call
     * @param signal the signal
     */
    public void offerSignal(String callId, UserSignal signal)
    {
        ActiveToolCall call = get(callId);
        if (call != null)
        {
            call.offerUserSignal(signal);
        }
    }

    /**
     * Atomically takes the queued signal for one call.
     *
     * @param callId the target call
     * @return the signal, or {@code null}
     */
    public UserSignal consumeSignal(String callId)
    {
        ActiveToolCall call = get(callId);
        return call == null ? null : call.consumeUserSignal();
    }

    private String newestRemainingId()
    {
        ActiveToolCall newest = newestRemaining();
        return newest == null ? null : newest.getCallId();
    }

    private ActiveToolCall newestRemaining()
    {
        ActiveToolCall newest = null;
        for (ActiveToolCall call : calls.values())
        {
            if (newest == null || call.getStartTime() >= newest.getStartTime())
            {
                newest = call;
            }
        }
        return newest;
    }
}
