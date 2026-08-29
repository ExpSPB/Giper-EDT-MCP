/**
 * MCP Server for EDT - Tests
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

/**
 * Isolation of parallel in-flight calls: a signal belongs to one call id.
 */
public class ActiveToolCallRegistryTest
{
    @Test
    public void registerAssignsIdAndKeepsCallsSplit()
    {
        ActiveToolCallRegistry registry = new ActiveToolCallRegistry();
        ActiveToolCall first = new ActiveToolCall(null, "alpha", 1, null, "sess-a"); //$NON-NLS-1$ //$NON-NLS-2$
        ActiveToolCall second = new ActiveToolCall(null, "beta", 2, null, "sess-b"); //$NON-NLS-1$ //$NON-NLS-2$

        registry.register(first);
        registry.register(second);

        assertNotNull(first.getCallId());
        assertNotNull(second.getCallId());
        assertFalse(first.getCallId().equals(second.getCallId()));
        assertEquals(2, registry.size());
        assertSame(second, registry.getSelectedOrLast());
        assertEquals("sess-a", first.getSessionId()); //$NON-NLS-1$
    }

    @Test
    public void signalOnOneCallIsNotConsumedByNeighbor()
    {
        ActiveToolCallRegistry registry = new ActiveToolCallRegistry();
        ActiveToolCall first = registry.register(new ActiveToolCall(null, "alpha", 1)); //$NON-NLS-1$
        ActiveToolCall second = registry.register(new ActiveToolCall(null, "beta", 2)); //$NON-NLS-1$
        UserSignal signal = new UserSignal(UserSignal.SignalType.CANCEL, "stop alpha"); //$NON-NLS-1$

        registry.offerSignal(first.getCallId(), signal);

        assertNull(registry.consumeSignal(second.getCallId()));
        assertSame(signal, registry.consumeSignal(first.getCallId()));
        assertNull(registry.consumeSignal(first.getCallId()));
    }

    @Test
    public void finishingOneCallDoesNotClearTheOther()
    {
        ActiveToolCallRegistry registry = new ActiveToolCallRegistry();
        ActiveToolCall first = registry.register(new ActiveToolCall(null, "alpha", 1)); //$NON-NLS-1$
        ActiveToolCall second = registry.register(new ActiveToolCall(null, "beta", 2)); //$NON-NLS-1$

        registry.unregister(first.getCallId());

        assertEquals(1, registry.size());
        assertSame(second, registry.get(second.getCallId()));
        assertNull(registry.get(first.getCallId()));
        assertFalse(second.hasResponded());
    }

    @Test
    public void concurrentOfferAndConsumeStayOnOwningCall() throws Exception
    {
        ActiveToolCallRegistry registry = new ActiveToolCallRegistry();
        ActiveToolCall first = registry.register(new ActiveToolCall(null, "alpha", 1)); //$NON-NLS-1$
        ActiveToolCall second = registry.register(new ActiveToolCall(null, "beta", 2)); //$NON-NLS-1$
        UserSignal alpha = new UserSignal(UserSignal.SignalType.CANCEL, "a"); //$NON-NLS-1$
        UserSignal beta = new UserSignal(UserSignal.SignalType.RETRY, "b"); //$NON-NLS-1$
        CountDownLatch started = new CountDownLatch(2);
        CountDownLatch done = new CountDownLatch(2);
        AtomicReference<UserSignal> gotAlpha = new AtomicReference<>();
        AtomicReference<UserSignal> gotBeta = new AtomicReference<>();

        Thread t1 = new Thread(() -> {
            started.countDown();
            await(started);
            registry.offerSignal(first.getCallId(), alpha);
            gotAlpha.set(registry.consumeSignal(first.getCallId()));
            done.countDown();
        });
        Thread t2 = new Thread(() -> {
            started.countDown();
            await(started);
            registry.offerSignal(second.getCallId(), beta);
            gotBeta.set(registry.consumeSignal(second.getCallId()));
            done.countDown();
        });
        t1.start();
        t2.start();
        if (!done.await(5, TimeUnit.SECONDS))
        {
            fail("concurrent signal threads did not finish"); //$NON-NLS-1$
        }

        assertEquals(UserSignal.SignalType.CANCEL, gotAlpha.get().getType());
        assertEquals(UserSignal.SignalType.RETRY, gotBeta.get().getType());
        assertNull(registry.consumeSignal(first.getCallId()));
        assertNull(registry.consumeSignal(second.getCallId()));
    }

    @Test
    public void serverBoundThreadConsumesOnlyItsCall()
    {
        McpServer server = new McpServer();
        ActiveToolCall first = new ActiveToolCall(null, "alpha", 1); //$NON-NLS-1$
        ActiveToolCall second = new ActiveToolCall(null, "beta", 2); //$NON-NLS-1$
        server.setActiveToolCall(first);
        server.setActiveToolCall(second);

        server.bindExecutingCall(first);
        server.setUserSignal(new UserSignal(UserSignal.SignalType.CANCEL, "only-first")); //$NON-NLS-1$
        assertEquals("only-first", server.consumeUserSignal().getMessage()); //$NON-NLS-1$

        server.bindExecutingCall(second);
        assertNull(server.consumeUserSignal());

        server.clearActiveToolCall(first);
        assertTrue(server.isToolExecuting());
        assertEquals("beta", server.getCurrentToolName()); //$NON-NLS-1$
        server.clearActiveToolCall(second);
        assertFalse(server.isToolExecuting());
    }

    private static void await(CountDownLatch latch)
    {
        try
        {
            if (!latch.await(5, TimeUnit.SECONDS))
            {
                fail("start latch timed out"); //$NON-NLS-1$
            }
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            fail("interrupted"); //$NON-NLS-1$
        }
    }
}
