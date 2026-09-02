/**
 * MCP Server for EDT - Tests
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

/**
 * Tests the server-initiated SSE broadcast mechanism (the channel that delivers
 * {@code notifications/tools/list_changed}). Exercised without HTTP: a registered
 * {@link java.io.OutputStream} receives the SSE frame; a stream that fails to write
 * is dropped. The full client round-trip (open a GET SSE stream, receive the push
 * after enable_toolset) is verified in the live loop with progressive disclosure on.
 */
public class SseStreamRegistryTest
{
    @Test
    public void notifyWritesToolsListChangedAsAnSseEventFrame()
    {
        SseStreamRegistry reg = SseStreamRegistry.getInstance();
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        SseStreamRegistry.SseStream stream = reg.register(sink);
        try
        {
            int delivered = reg.notifyToolsListChanged();
            assertTrue("at least the registered stream is notified", delivered >= 1); //$NON-NLS-1$

            String frame = sink.toString(StandardCharsets.UTF_8);
            assertTrue("must be an SSE event frame", frame.contains("event: message")); //$NON-NLS-1$ //$NON-NLS-2$
            assertTrue("must carry an event id", frame.contains("id: ")); //$NON-NLS-1$ //$NON-NLS-2$
            assertTrue("payload on a data line", frame.contains("data: ")); //$NON-NLS-1$ //$NON-NLS-2$
            assertTrue("the JSON-RPC method is the list_changed notification", //$NON-NLS-1$
                frame.contains("notifications/tools/list_changed")); //$NON-NLS-1$
            assertTrue("frame is terminated by a blank line", frame.endsWith("\n\n")); //$NON-NLS-1$ //$NON-NLS-2$
        }
        finally
        {
            reg.unregister(stream);
        }
    }

    @Test
    public void deadStreamIsDroppedOnBroadcastFailure()
    {
        SseStreamRegistry reg = SseStreamRegistry.getInstance();
        OutputStream throwing = new OutputStream()
        {
            @Override
            public void write(int b) throws IOException
            {
                throw new IOException("client gone"); //$NON-NLS-1$
            }
        };
        SseStreamRegistry.SseStream stream = reg.register(throwing);
        int before = reg.activeStreamCount();
        try
        {
            reg.broadcast("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/tools/list_changed\"}"); //$NON-NLS-1$
            // The failing stream is removed; the count drops by exactly that one.
            assertEquals("a stream that fails to accept the write is dropped", //$NON-NLS-1$
                before - 1, reg.activeStreamCount());
        }
        finally
        {
            reg.unregister(stream); // idempotent if already removed
        }
    }

    @Test
    public void broadcastWithNoStreamsOrNullMessageDeliversNothing()
    {
        SseStreamRegistry reg = SseStreamRegistry.getInstance();
        assertEquals(0, reg.broadcast(null));
    }

    @Test
    public void listChangedIsDeliveredOncePerSessionAndIsolatedByPath()
    {
        SseStreamRegistry reg = SseStreamRegistry.getInstance();
        ByteArrayOutputStream first = new ByteArrayOutputStream();
        ByteArrayOutputStream second = new ByteArrayOutputStream();
        ByteArrayOutputStream otherPath = new ByteArrayOutputStream();
        SseStreamRegistry.SseStream a = reg.register(first, "same-session", "/mcp/profiles/zz-sse-once"); //$NON-NLS-1$ //$NON-NLS-2$
        SseStreamRegistry.SseStream b = reg.register(second, "same-session", "/mcp/profiles/zz-sse-once"); //$NON-NLS-1$ //$NON-NLS-2$
        SseStreamRegistry.SseStream c = reg.register(otherPath, "other-session", "/mcp"); //$NON-NLS-1$ //$NON-NLS-2$
        try
        {
            assertEquals(1, reg.notifyToolsListChanged("/mcp/profiles/zz-sse-once")); //$NON-NLS-1$
            assertEquals(1, (first.size() > 0 ? 1 : 0) + (second.size() > 0 ? 1 : 0));
            assertEquals(0, otherPath.size());
        }
        finally
        {
            reg.unregister(a);
            reg.unregister(b);
            reg.unregister(c);
        }
    }

    @Test
    public void unregisterBySessionDropsOnlyThatSession()
    {
        SseStreamRegistry reg = SseStreamRegistry.getInstance();
        int before = reg.activeStreamCount();
        ByteArrayOutputStream keep = new ByteArrayOutputStream();
        ByteArrayOutputStream drop = new ByteArrayOutputStream();
        SseStreamRegistry.SseStream kept = reg.register(keep, "keep-zz", "/mcp/profiles/zz-keep"); //$NON-NLS-1$ //$NON-NLS-2$
        SseStreamRegistry.SseStream gone = reg.register(drop, "drop-zz", "/mcp/profiles/zz-drop"); //$NON-NLS-1$ //$NON-NLS-2$
        try
        {
            reg.unregisterBySession("drop-zz"); //$NON-NLS-1$
            assertEquals(before + 1, reg.activeStreamCount());
            assertEquals(1, reg.notifyToolsListChanged("/mcp/profiles/zz-keep")); //$NON-NLS-1$
            assertTrue(keep.toString(StandardCharsets.UTF_8).contains("list_changed")); //$NON-NLS-1$
            assertEquals(0, drop.size());
        }
        finally
        {
            reg.unregister(kept);
            reg.unregister(gone);
        }
    }

    @Test
    public void unregisterBySessionClosesTheRemovedOutput()
    {
        SseStreamRegistry reg = SseStreamRegistry.getInstance();
        CloseTrackingOutputStream output = new CloseTrackingOutputStream();
        SseStreamRegistry.SseStream stream = reg.register(output, "close-zz", //$NON-NLS-1$
            "/mcp/profiles/zz-close"); //$NON-NLS-1$
        try
        {
            reg.unregisterBySession("close-zz"); //$NON-NLS-1$

            assertTrue("DELETE/session removal must close the SSE socket, not only forget it", //$NON-NLS-1$
                output.closed);
        }
        finally
        {
            reg.unregister(stream);
        }
    }

    @Test
    public void unregisterBySessionClosesAllStreamsAndContinuesAfterCloseFailure()
    {
        SseStreamRegistry reg = SseStreamRegistry.getInstance();
        int before = reg.activeStreamCount();
        CloseTrackingOutputStream first = new CloseTrackingOutputStream();
        CloseTrackingOutputStream second = new CloseTrackingOutputStream();
        CloseTrackingOutputStream other = new CloseTrackingOutputStream();
        SseStreamRegistry.SseStream a = reg.register(first, "close-all-zz", "/mcp"); //$NON-NLS-1$ //$NON-NLS-2$
        SseStreamRegistry.SseStream b = reg.register(new CloseFailingOutputStream(), "close-all-zz", "/mcp"); //$NON-NLS-1$ //$NON-NLS-2$
        SseStreamRegistry.SseStream c = reg.register(second, "close-all-zz", "/mcp/profiles/default"); //$NON-NLS-1$ //$NON-NLS-2$
        SseStreamRegistry.SseStream kept = reg.register(other, "keep-all-zz", "/mcp"); //$NON-NLS-1$ //$NON-NLS-2$
        try
        {
            reg.unregisterBySession("close-all-zz"); //$NON-NLS-1$

            assertEquals(before + 1, reg.activeStreamCount());
            assertTrue(first.closed);
            assertTrue(second.closed);
            assertTrue("another session must remain open", !other.closed); //$NON-NLS-1$

            // Repeated DELETE/session cleanup is intentionally idempotent.
            reg.unregisterBySession("close-all-zz"); //$NON-NLS-1$
            assertEquals(before + 1, reg.activeStreamCount());
        }
        finally
        {
            reg.unregister(a);
            reg.unregister(b);
            reg.unregister(c);
            reg.unregister(kept);
        }
    }

    @Test
    public void sessionCloseUnblocksAWriteHoldingTheStreamLock() throws Exception
    {
        SseStreamRegistry reg = SseStreamRegistry.getInstance();
        BlockingUntilClosedOutputStream output = new BlockingUntilClosedOutputStream();
        SseStreamRegistry.SseStream stream = reg.register(output, "blocked-close-zz", "/mcp"); //$NON-NLS-1$ //$NON-NLS-2$
        Thread writer = new Thread(() -> {
            try
            {
                stream.write("blocked"); //$NON-NLS-1$
            }
            catch (IOException expected)
            {
                // Concurrent close is expected to terminate the blocked write.
            }
        });
        writer.start();
        try
        {
            assertTrue("the write must be blocked before DELETE closes it", //$NON-NLS-1$
                output.writeStarted.await(1, TimeUnit.SECONDS));

            reg.unregisterBySession("blocked-close-zz"); //$NON-NLS-1$

            assertTrue(output.closed);
            writer.join(1000L);
            assertTrue("closing the output must release the blocked writer", !writer.isAlive()); //$NON-NLS-1$
        }
        finally
        {
            reg.unregister(stream);
            output.close();
            writer.join(1000L);
        }
    }

    @Test
    public void queuedRegistrationIsRejectedAfterPathKick()
    {
        SseStreamRegistry reg = SseStreamRegistry.getInstance();
        String path = "/mcp/profiles/queued-kick-zz"; //$NON-NLS-1$
        SseStreamRegistry.RequestedPathAdmission admission = reg.admitRequestedPath(path);
        reg.closeByRequestedPath(path);
        CloseTrackingOutputStream output = new CloseTrackingOutputStream();

        SseStreamRegistry.SseStream stale = reg.registerIfCurrent(output, "queued-kick-session-zz", //$NON-NLS-1$
            path, null, admission);

        assertEquals(null, stale);
        assertTrue("a queued GET admitted before kick must have its output closed", output.closed); //$NON-NLS-1$
    }

    @Test
    public void registrationReturnsNullWhenKickRemovesStreamAfterInsertion()
    {
        SseStreamRegistry reg = SseStreamRegistry.getInstance();
        String path = "/mcp/profiles/insert-race-zz"; //$NON-NLS-1$
        SseStreamRegistry.RequestedPathAdmission admission = reg.admitRequestedPath(path);
        CloseTrackingOutputStream output = new CloseTrackingOutputStream();

        SseStreamRegistry.SseStream stale = reg.registerIfCurrent(output, "insert-race-session-zz", //$NON-NLS-1$
            path, null, admission, () -> reg.closeByRequestedPath(path));

        assertEquals(null, stale);
        assertTrue(output.closed);
        assertEquals("completed admissions must not retain path tombstones", //$NON-NLS-1$
            0, reg.pendingAdmissionPathCount());
    }

    private static final class CloseTrackingOutputStream extends ByteArrayOutputStream
    {
        boolean closed;

        @Override
        public void close() throws IOException
        {
            closed = true;
            super.close();
        }
    }

    private static final class CloseFailingOutputStream extends ByteArrayOutputStream
    {
        @Override
        public void close() throws IOException
        {
            throw new IOException("simulated close failure"); //$NON-NLS-1$
        }
    }

    private static final class BlockingUntilClosedOutputStream extends OutputStream
    {
        final CountDownLatch writeStarted = new CountDownLatch(1);
        final CountDownLatch closeSignal = new CountDownLatch(1);
        volatile boolean closed;

        @Override
        public void write(int value) throws IOException
        {
            writeStarted.countDown();
            try
            {
                closeSignal.await();
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted", e); //$NON-NLS-1$
            }
            throw new IOException("closed"); //$NON-NLS-1$
        }

        @Override
        public void close()
        {
            closed = true;
            closeSignal.countDown();
        }
    }
}
