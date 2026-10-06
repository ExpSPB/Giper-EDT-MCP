/**
 * MCP Server for EDT - Tests
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server.tools.rename;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;
import com._1c.g5.v8.derived.IDerivedDataManager;
import com._1c.g5.v8.dt.core.platform.IDtProject;

public class RenameCompletionBarrierTest
{
    @Test
    public void testEveryRegisteredProjectIsWaitedBetweenEventDrains() throws Exception
    {
        Fixture f = new Fixture();
        IDtProject other = f.add("unrelated");
        assertNull(f.barrier.await());
        verify(f.firstManager).waitAllComputations(1000L);
        verify(f.manager(other)).waitAllComputations(1000L);
        assertEquals(2, f.drains.get());
    }

    @Test
    public void testMissingProjectServiceFailsClosed() throws Exception
    {
        Fixture f = new Fixture();
        f.projects.set(null);
        assertNotNull(f.barrier.await());
        verifyZeroInteractions(f.firstManager);
    }

    @Test
    public void testEmptyRegisteredSetCannotCertifyPreparedRename() throws Exception
    {
        Fixture f = new Fixture();
        f.projects.set(List.of());
        assertNotNull(f.barrier.await());
        verifyZeroInteractions(f.firstManager);
    }

    @Test
    public void testMissingManagerNeverWaitsOrSucceeds() throws Exception
    {
        Fixture f = new Fixture();
        f.available = false;
        assertNotNull(f.barrier.await());
        verifyZeroInteractions(f.firstManager);
    }

    @Test
    public void testMissingEventServiceNeverWaitsOrSucceeds() throws Exception
    {
        Fixture f = new Fixture();
        f.eventsAvailable = false;
        assertNotNull(f.barrier.await());
        verifyZeroInteractions(f.firstManager);
    }

    @Test
    public void testFalseSdkWaitRefusesEvenWhenPredicateTrue() throws Exception
    {
        Fixture f = new Fixture();
        when(f.firstManager.waitAllComputations(1000L)).thenReturn(false);
        assertNotNull(f.barrier.await());
        assertEquals(1, f.drains.get());
    }

    @Test
    public void testTrueSdkWaitWithFalsePredicateRefuses() throws Exception
    {
        Fixture f = new Fixture();
        when(f.firstManager.isAllComputed()).thenReturn(false);
        assertNotNull(f.barrier.await());
    }

    @Test
    public void testPostDrainNewWorkRequiresAnotherWait() throws Exception
    {
        Fixture f = new Fixture();
        when(f.firstManager.isAllComputed()).thenReturn(true, false, true, true);
        assertNull(f.barrier.await());
        verify(f.firstManager, times(2)).waitAllComputations(1000L);
        assertEquals(4, f.drains.get());
    }

    @Test
    public void testPostDrainNewProjectIsWaitedBeforeSuccess() throws Exception
    {
        Fixture f = new Fixture();
        f.afterSecondDrain = () -> f.add("late");
        assertNull(f.barrier.await());
        verify(f.manager(f.projects.get().get(1))).waitAllComputations(1000L);
        verify(f.firstManager, times(2)).waitAllComputations(1000L);
    }

    @Test
    public void testOneBudgetIsClippedAcrossProjectsAndLaterBarriers() throws Exception
    {
        Fixture f = new Fixture();
        IDtProject other = f.add("other");
        when(f.firstManager.waitAllComputations(1000L)).thenAnswer(i -> {
            f.clock.addAndGet(600_000_000L);
            return true;
        });
        when(f.manager(other).waitAllComputations(400L)).thenReturn(true);
        assertNull(f.barrier.await());
        verify(f.manager(other)).waitAllComputations(400L);
        assertNull(f.barrier.await());
        verify(f.firstManager).waitAllComputations(400L);
    }

    @Test
    public void testSdkElapsedOverrunCannotReturnSuccessOrStartZeroWait() throws Exception
    {
        Fixture f = new Fixture();
        when(f.firstManager.waitAllComputations(1000L)).thenAnswer(i -> {
            f.clock.addAndGet(1_100_000_000L);
            return true;
        });
        assertNotNull(f.barrier.await());
        assertNotNull(f.barrier.await());
        verify(f.firstManager, times(1)).waitAllComputations(1000L);
        verify(f.firstManager, never()).waitAllComputations(0L);
        assertEquals(1, f.drains.get());
    }

    @Test
    public void testSdkExceptionRemainsTheActualFailure() throws Exception
    {
        Fixture f = new Fixture();
        IllegalStateException expected = new IllegalStateException("platform failure");
        when(f.firstManager.waitAllComputations(1000L)).thenThrow(expected);
        try
        {
            f.barrier.await();
            fail("SDK exception must escape to the mutation-aware caller");
        }
        catch (IllegalStateException actual)
        {
            assertSame(expected, actual);
        }
        assertEquals(1, f.drains.get());
    }

    @Test
    public void testEventDrainOverrunRefusesBeforeAnySdkWait() throws Exception
    {
        Fixture f = new Fixture();
        RenameCompletionBarrier barrier = new RenameCompletionBarrier(f.projects::get, f::manager,
            () -> { f.clock.addAndGet(1_100_000_000L); return true; }, f.clock::get, 1000L);
        assertNotNull(barrier.await());
        verifyZeroInteractions(f.firstManager);
    }

    @Test
    public void testManagerReplacementRequiresWaitingTheNewManager() throws Exception
    {
        Fixture f = new Fixture();
        IDerivedDataManager replacement = mock(IDerivedDataManager.class);
        when(replacement.waitAllComputations(1000L)).thenReturn(true);
        when(replacement.isAllComputed()).thenReturn(true);
        f.afterSecondDrain = () -> f.managers.put(f.first, replacement);
        assertNull(f.barrier.await());
        verify(f.firstManager).waitAllComputations(1000L);
        verify(replacement).waitAllComputations(1000L);
    }

    @Test
    public void testFinalEventDrainSettingInterruptCannotReportSuccess() throws Exception
    {
        try
        {
            Fixture f = new Fixture();
            f.afterSecondDrain = () -> Thread.currentThread().interrupt();
            try
            {
                f.barrier.await();
                fail("An interrupted final event drain must not release mutation");
            }
            catch (InterruptedException e)
            {
                assertTrue(Thread.currentThread().isInterrupted());
            }
        }
        finally
        {
            Thread.interrupted();
        }
    }

    static final class Fixture
    {
        final AtomicLong clock = new AtomicLong();
        final AtomicInteger drains = new AtomicInteger();
        final AtomicReference<List<IDtProject>> projects = new AtomicReference<>(new ArrayList<>());
        final java.util.Map<IDtProject, IDerivedDataManager> managers = new java.util.IdentityHashMap<>();
        final IDtProject first;
        final IDerivedDataManager firstManager;
        boolean available = true;
        boolean eventsAvailable = true;
        Runnable afterSecondDrain = () -> { };
        final RenameCompletionBarrier barrier;

        Fixture() throws Exception
        {
            first = add("base");
            firstManager = manager(first);
            barrier = new RenameCompletionBarrier(projects::get,
                p -> available ? manager(p) : null, () -> {
                    if (drains.incrementAndGet() == 2)
                    {
                        afterSecondDrain.run();
                    }
                    return eventsAvailable;
                }, clock::get, 1000L);
        }

        IDtProject add(String id)
        {
            IDtProject p = mock(IDtProject.class);
            when(p.getId()).thenReturn(id);
            when(p.getName()).thenReturn(id);
            IDerivedDataManager m = mock(IDerivedDataManager.class);
            try
            {
                when(m.waitAllComputations(org.mockito.ArgumentMatchers.anyLong())).thenReturn(true);
            }
            catch (InterruptedException e)
            {
                throw new AssertionError(e);
            }
            when(m.isAllComputed()).thenReturn(true);
            managers.put(p, m);
            List<IDtProject> next = new ArrayList<>(projects.get());
            next.add(p);
            projects.set(next);
            return p;
        }

        IDerivedDataManager manager(IDtProject p)
        {
            return managers.get(p);
        }
    }
}
