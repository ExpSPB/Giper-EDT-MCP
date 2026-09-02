/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.proxy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

/**
 * Unit tests for {@link SessionManager}: session lifecycle ({@code create}/{@code isValid}/
 * {@code close}), rejection of unknown or {@code null} ids, idempotent close, the
 * active-session count, and the {@link SessionManager#MAX_SESSIONS} hard cap (issue #253
 * hardening).
 */
public class SessionManagerTest
{
    @Test
    public void testCreateIssuesAValidSession()
    {
        SessionManager sessions = new SessionManager();

        String id = sessions.create();

        assertNotNull(id);
        assertTrue(sessions.isValid(id));
        assertEquals(1, sessions.activeCount());
    }

    @Test
    public void testCreateIssuesDistinctIds()
    {
        SessionManager sessions = new SessionManager();

        String first = sessions.create();
        String second = sessions.create();

        assertNotEquals(first, second);
        assertEquals(2, sessions.activeCount());
    }

    @Test
    public void testCloseInvalidatesTheSession()
    {
        SessionManager sessions = new SessionManager();
        String id = sessions.create();

        sessions.close(id);

        assertFalse(sessions.isValid(id));
        assertEquals(0, sessions.activeCount());
    }

    @Test
    public void testCloseIsIdempotent()
    {
        SessionManager sessions = new SessionManager();
        String id = sessions.create();

        sessions.close(id);
        sessions.close(id); // must not throw and must not go negative

        assertFalse(sessions.isValid(id));
        assertEquals(0, sessions.activeCount());
    }

    @Test
    public void testCloseNullIsIgnored()
    {
        SessionManager sessions = new SessionManager();
        sessions.create();

        sessions.close(null); // must not throw

        assertEquals(1, sessions.activeCount());
    }

    @Test
    public void testCloseUnknownIdIsIgnored()
    {
        SessionManager sessions = new SessionManager();
        sessions.create();

        sessions.close("never-issued"); //$NON-NLS-1$

        assertEquals(1, sessions.activeCount());
    }

    @Test
    public void testIsValidRejectsNullAndUnknownIds()
    {
        SessionManager sessions = new SessionManager();
        sessions.create();

        assertFalse(sessions.isValid(null));
        assertFalse(sessions.isValid("not-a-real-session")); //$NON-NLS-1$
    }

    @Test
    public void testActiveCountReflectsMultipleSessionsIndependently()
    {
        SessionManager sessions = new SessionManager();
        String a = sessions.create();
        String b = sessions.create();
        String c = sessions.create();

        sessions.close(b);

        assertEquals(2, sessions.activeCount());
        assertTrue(sessions.isValid(a));
        assertFalse(sessions.isValid(b));
        assertTrue(sessions.isValid(c));
    }

    @Test
    public void testActiveCountStartsAtZero()
    {
        SessionManager sessions = new SessionManager();

        assertEquals(0, sessions.activeCount());
    }

    /**
     * Fills the session set exactly to {@link SessionManager#MAX_SESSIONS}, then proves the
     * next {@code create()} is rejected ({@code null}, no growth) while every session created
     * under the cap remains valid — the hard cap must not evict or corrupt existing sessions.
     */
    @Test
    public void testCreateReturnsNullPastCapAndExistingSessionsStayValid()
    {
        SessionManager sessions = new SessionManager();
        String first = sessions.create();
        for (int i = 1; i < SessionManager.MAX_SESSIONS; i++)
        {
            assertNotNull("create must succeed under the cap (i=" + i + ")", sessions.create());
        }
        assertEquals(SessionManager.MAX_SESSIONS, sessions.activeCount());

        String rejected = sessions.create();

        assertNull("create must return null once MAX_SESSIONS is reached", rejected);
        assertEquals("a rejected create must not grow the session set",
            SessionManager.MAX_SESSIONS, sessions.activeCount());
        assertTrue("sessions created under the cap must remain valid", sessions.isValid(first));
    }

    /**
     * After a rejection at the cap, closing one existing session must free exactly one slot -
     * proving the cap check is a live size comparison, not a one-shot latch.
     */
    @Test
    public void testCreateSucceedsAgainAfterClosingASessionAtTheCap()
    {
        SessionManager sessions = new SessionManager();
        String toClose = sessions.create();
        for (int i = 1; i < SessionManager.MAX_SESSIONS; i++)
        {
            sessions.create();
        }
        assertNull("must be rejected while at the cap", sessions.create());

        sessions.close(toClose);
        String afterClose = sessions.create();

        assertNotNull("create must succeed again once a slot is freed", afterClose);
        assertEquals(SessionManager.MAX_SESSIONS, sessions.activeCount());
    }

    /**
     * The structuredContent capability belongs to the SESSION: several clients share one proxy, so
     * one client's opt-out must not change what another client receives.
     */
    @Test
    public void testStructuredContentCapabilityIsPerSession()
    {
        SessionManager sessions = new SessionManager();

        String optedOut = sessions.create(false);
        String normal = sessions.create(true);

        assertFalse("the opted-out session must report its opt-out", //$NON-NLS-1$
            sessions.allowsStructuredContent(optedOut));
        assertTrue("another client's session must be unaffected", //$NON-NLS-1$
            sessions.allowsStructuredContent(normal));
    }

    /**
     * Everything that is not an explicit opt-out keeps the permissive default - including a session
     * created by the capability-less {@link SessionManager#create()}, an unknown id (a call whose
     * session was already closed) and a missing header.
     */
    @Test
    public void testUnknownAndDefaultSessionsAllowStructuredContent()
    {
        SessionManager sessions = new SessionManager();

        assertTrue("create() must default to allowing structuredContent", //$NON-NLS-1$
            sessions.allowsStructuredContent(sessions.create()));
        assertTrue("an unknown session must not suppress the field", //$NON-NLS-1$
            sessions.allowsStructuredContent("no-such-session")); //$NON-NLS-1$
        assertTrue("a missing session header must not suppress the field", //$NON-NLS-1$
            sessions.allowsStructuredContent(null));
    }

    /**
     * Validation and the capability must come from ONE lookup: a session closed in between would
     * otherwise pass validation and then read back as an unknown - i.e. permissive - session.
     */
    @Test
    public void testCapabilityOfDistinguishesClosedFromOptedOut()
    {
        SessionManager sessions = new SessionManager();
        String optedOut = sessions.create(false);

        assertEquals(Boolean.FALSE, sessions.capabilityOf(optedOut));

        sessions.close(optedOut);

        assertNull("a closed session must be reported as unknown, not as permissive", //$NON-NLS-1$
            sessions.capabilityOf(optedOut));
        assertNull("a null id must be reported as unknown", sessions.capabilityOf(null)); //$NON-NLS-1$
    }

    @Test
    public void testSessionIsBoundToTheEndpointPath()
    {
        SessionManager sessions = new SessionManager();
        ProfileEndpoint review = new ProfileEndpoint("/mcp/profiles/review", "review", false); //$NON-NLS-1$ //$NON-NLS-2$
        String id = sessions.create(review, "2025-11-25", true); //$NON-NLS-1$

        assertTrue(sessions.isValid(id, "/mcp/profiles/review")); //$NON-NLS-1$
        assertFalse("replay on another path must be unknown", sessions.isValid(id, "/mcp")); //$NON-NLS-1$
        assertNotNull(sessions.lookup(id, "/mcp/profiles/review")); //$NON-NLS-1$
        assertNull(sessions.lookup(id, "/mcp")); //$NON-NLS-1$
        assertEquals("review", sessions.lookup(id, null).getEndpoint().getRequestedProfileId()); //$NON-NLS-1$
        assertEquals("2025-11-25", sessions.lookup(id, null).getProtocolVersion()); //$NON-NLS-1$
    }

    @Test
    public void testDeleteClosesOnlyTheMatchingSession()
    {
        SessionManager sessions = new SessionManager();
        ProfileEndpoint review = new ProfileEndpoint("/mcp/profiles/review", "review", false); //$NON-NLS-1$ //$NON-NLS-2$
        String reviewId = sessions.create(review, Backend.PROTOCOL_VERSION, true);
        String defaultId = sessions.create();

        assertFalse(sessions.closeOnPath(reviewId, "/mcp")); //$NON-NLS-1$
        assertTrue(sessions.isValid(reviewId));
        assertTrue(sessions.closeOnPath(reviewId, "/mcp/profiles/review")); //$NON-NLS-1$
        assertFalse(sessions.isValid(reviewId));
        assertTrue(sessions.isValid(defaultId));
        assertEquals(1, sessions.activeCount());
    }

    @Test
    public void testCloseByCanonicalPathLeavesOtherProfiles()
    {
        SessionManager sessions = new SessionManager();
        ProfileEndpoint review = new ProfileEndpoint("/mcp/profiles/review", "review", false); //$NON-NLS-1$ //$NON-NLS-2$
        String reviewId = sessions.create(review, Backend.PROTOCOL_VERSION, true);
        String defaultId = sessions.create();

        assertEquals(1, sessions.closeByCanonicalPath("/mcp/profiles/review")); //$NON-NLS-1$
        assertFalse(sessions.isValid(reviewId));
        assertTrue(sessions.isValid(defaultId));
        assertEquals(0, sessions.closeByCanonicalPath("/mcp/profiles/review")); //$NON-NLS-1$
    }

    @Test
    public void concurrentCreateCannotExceedTheHardCap() throws Exception
    {
        int cap = 32;
        int contenders = 64;
        MutableClock clock = new MutableClock(1_000L);
        SessionManager sessions = new SessionManager(Duration.ofMinutes(5), clock, cap);
        ExecutorService workers = Executors.newFixedThreadPool(contenders);
        CountDownLatch ready = new CountDownLatch(contenders);
        CountDownLatch start = new CountDownLatch(1);
        try
        {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < contenders; i++)
            {
                futures.add(workers.submit(() -> {
                    ready.countDown();
                    start.await();
                    return sessions.create();
                }));
            }
            ready.await();
            start.countDown();
            AtomicInteger created = new AtomicInteger();
            for (Future<String> future : futures)
            {
                if (get(future) != null)
                {
                    created.incrementAndGet();
                }
            }
            assertEquals("exactly the configured capacity may be allocated", cap, created.get()); //$NON-NLS-1$
        }
        finally
        {
            workers.shutdownNow();
        }
        assertEquals("concurrent create must never oversubscribe the cap", cap, sessions.activeCount()); //$NON-NLS-1$
    }

    @Test
    public void expiredSessionsFreeCapacityWithoutDelete()
    {
        MutableClock clock = new MutableClock(10_000L);
        SessionManager sessions = new SessionManager(Duration.ofSeconds(10), clock, 2);
        String first = sessions.create();
        assertNotNull(sessions.create());
        assertNull(sessions.create());

        clock.advance(Duration.ofSeconds(10));
        String replacement = sessions.create();

        assertNotNull("create must lazily reclaim expired capacity", replacement); //$NON-NLS-1$
        assertFalse(sessions.isValid(first));
        assertEquals(1, sessions.activeCount());
    }

    @Test
    public void expiredLookupAndWrongPathReplayAreUnknown()
    {
        MutableClock clock = new MutableClock(20_000L);
        SessionManager sessions = new SessionManager(Duration.ofSeconds(10), clock);
        ProfileEndpoint review = new ProfileEndpoint("/mcp/profiles/review", "review", false); //$NON-NLS-1$ //$NON-NLS-2$
        String id = sessions.create(review, Backend.PROTOCOL_VERSION, true);

        clock.advance(Duration.ofSeconds(9));
        assertNull("wrong-path replay must not touch the session", sessions.lookup(id, "/mcp")); //$NON-NLS-1$ //$NON-NLS-2$
        clock.advance(Duration.ofSeconds(1));

        assertNull("the original path must see an expired session as unknown", //$NON-NLS-1$
            sessions.lookup(id, "/mcp/profiles/review")); //$NON-NLS-1$
        assertEquals(0, sessions.activeCount());
    }

    @Test
    public void wrongPathDeleteDoesNotExtendSessionTtl()
    {
        MutableClock clock = new MutableClock(25_000L);
        SessionManager sessions = new SessionManager(Duration.ofSeconds(10), clock);
        ProfileEndpoint review = new ProfileEndpoint("/mcp/profiles/review", "review", false); //$NON-NLS-1$ //$NON-NLS-2$
        String id = sessions.create(review, Backend.PROTOCOL_VERSION, true);

        clock.advance(Duration.ofSeconds(9));
        assertEquals(SessionManager.CloseResult.PATH_MISMATCH,
            sessions.closeForDelete(id, "/mcp")); //$NON-NLS-1$
        clock.advance(Duration.ofSeconds(1));

        assertFalse("wrong-path DELETE must not touch the valid session", sessions.isValid(id)); //$NON-NLS-1$
    }

    @Test
    public void successfulLookupExtendsOnlyTheValidSession()
    {
        MutableClock clock = new MutableClock(30_000L);
        SessionManager sessions = new SessionManager(Duration.ofSeconds(10), clock);
        String touched = sessions.create();
        String abandoned = sessions.create();

        clock.advance(Duration.ofSeconds(9));
        assertNotNull(sessions.lookup(touched, "/mcp")); //$NON-NLS-1$
        clock.advance(Duration.ofSeconds(2));

        assertTrue("successful lookup must extend the idle deadline", sessions.isValid(touched)); //$NON-NLS-1$
        assertFalse("an untouched peer must expire independently", sessions.isValid(abandoned)); //$NON-NLS-1$
    }

    @Test
    public void explicitCleanupRemovesIdleSessions()
    {
        MutableClock clock = new MutableClock(40_000L);
        SessionManager sessions = new SessionManager(Duration.ofSeconds(5), clock);
        sessions.create();
        sessions.create();
        clock.advance(Duration.ofSeconds(5));

        assertEquals(2, sessions.cleanupExpired());
        assertEquals(0, sessions.activeCount());
    }

    @Test
    public void shutdownClearsSessionsAndCancelsPeriodicCleanup()
    {
        MutableClock clock = new MutableClock(50_000L);
        SessionManager sessions = new SessionManager(Duration.ofSeconds(5), clock);
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try
        {
            sessions.create();
            sessions.startPeriodicCleanup(scheduler);
            assertTrue(sessions.hasCleanupTask());

            sessions.shutdown();
            sessions.shutdown();

            assertFalse(sessions.hasCleanupTask());
            assertEquals(0, sessions.activeCount());
            assertNull("a shut-down manager must not issue new sessions", sessions.create()); //$NON-NLS-1$
        }
        finally
        {
            scheduler.shutdownNow();
        }
    }

    private static String get(Future<String> future) throws Exception
    {
        try
        {
            return future.get();
        }
        catch (ExecutionException e)
        {
            Throwable cause = e.getCause();
            if (cause instanceof Exception)
            {
                throw (Exception)cause;
            }
            throw e;
        }
    }

    private static final class MutableClock extends Clock
    {
        private volatile long millis;
        private final ZoneId zone;

        MutableClock(long millis)
        {
            this(millis, ZoneOffset.UTC);
        }

        private MutableClock(long millis, ZoneId zone)
        {
            this.millis = millis;
            this.zone = zone;
        }

        void advance(Duration duration)
        {
            millis += duration.toMillis();
        }

        @Override
        public ZoneId getZone()
        {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId requestedZone)
        {
            return new MutableClock(millis, requestedZone);
        }

        @Override
        public Instant instant()
        {
            return Instant.ofEpochMilli(millis);
        }

        @Override
        public long millis()
        {
            return millis;
        }
    }
}
