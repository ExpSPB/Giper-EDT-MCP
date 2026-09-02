/**
 * MCP Server for EDT - Proxy Tests
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.proxy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

/** Real HTTP/SSE lifecycle checks for one backend profile channel. */
public class BackendProfileChannelIT
{
    private static final String REVIEW_PATH = "/mcp/profiles/review"; //$NON-NLS-1$
    private static final String DOCS_PATH = "/mcp/profiles/docs"; //$NON-NLS-1$

    @Rule
    public final Timeout globalTimeout = Timeout.seconds(30);

    private FakeBackend fake;
    private BackendProfileChannel review;
    private BackendProfileChannel docs;

    @After
    public void tearDown()
    {
        if (review != null)
        {
            review.stopNotificationListener();
        }
        if (docs != null)
        {
            docs.stopNotificationListener();
        }
        if (fake != null)
        {
            fake.stop();
        }
    }

    @Test
    public void staleSseSessionReconnectsOnlyAffectedProfileAndReceivesNextEvent() throws Exception
    {
        fake = new FakeBackend();
        fake.putProfile("review", "echo_port"); //$NON-NLS-1$ //$NON-NLS-2$
        fake.putProfile("docs", "echo_port"); //$NON-NLS-1$ //$NON-NLS-2$
        fake.validateSseSessions();
        fake.start();

        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        Backend backend = new Backend(fake.getPort(), client, 5);
        review = backend.channel(new ProfileEndpoint(REVIEW_PATH, "review", false)); //$NON-NLS-1$
        docs = backend.channel(new ProfileEndpoint(DOCS_PATH, "docs", false)); //$NON-NLS-1$
        AtomicInteger reviewInvalidations = new AtomicInteger();
        AtomicInteger docsInvalidations = new AtomicInteger();
        SseNotificationHub hub = new SseNotificationHub();
        hub.setInvalidator(id -> {
            if ("review".equals(id)) //$NON-NLS-1$
            {
                reviewInvalidations.incrementAndGet();
            }
            else if ("docs".equals(id)) //$NON-NLS-1$
            {
                docsInvalidations.incrementAndGet();
            }
        });

        review.ensureNotificationListener(hub);
        docs.ensureNotificationListener(hub);
        assertTrue(await(() -> fake.activeSseStreams(REVIEW_PATH) == 1
            && fake.activeSseStreams(DOCS_PATH) == 1, 5_000L));
        int reviewInitializes = fake.getInitializeCount(REVIEW_PATH);
        int docsInitializes = fake.getInitializeCount(DOCS_PATH);

        fake.invalidateSession(REVIEW_PATH);

        assertTrue("review listener must reconnect after GET/SSE rejects its stale session", //$NON-NLS-1$
            await(() -> fake.getInitializeCount(REVIEW_PATH) == reviewInitializes + 1
                && fake.activeSseStreams(REVIEW_PATH) == 1, 6_000L));
        assertEquals("the neighboring profile session must remain intact", //$NON-NLS-1$
            docsInitializes, fake.getInitializeCount(DOCS_PATH));
        assertEquals(1, fake.activeSseStreams(DOCS_PATH));
        assertTrue("recovery must invalidate only the affected requested profile", //$NON-NLS-1$
            await(() -> reviewInvalidations.get() == 1, 2_000L));
        assertEquals(0, docsInvalidations.get());

        fake.emitListChanged(REVIEW_PATH);
        assertTrue("the reconnected listener must receive the next backend event", //$NON-NLS-1$
            await(() -> reviewInvalidations.get() == 2, 2_000L));
        assertEquals(0, docsInvalidations.get());

        review.stopNotificationListener();
        assertTrue("stop must close the active response stream and terminate the server-side GET", //$NON-NLS-1$
            await(() -> fake.activeSseStreams(REVIEW_PATH) == 0, 2_000L));
        assertEquals(1, fake.activeSseStreams(DOCS_PATH));
    }

    private static boolean await(Check check, long timeoutMillis) throws Exception
    {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline)
        {
            if (check.get())
            {
                return true;
            }
            Thread.sleep(25L);
        }
        return check.get();
    }

    @FunctionalInterface
    private interface Check
    {
        boolean get() throws Exception;
    }
}
