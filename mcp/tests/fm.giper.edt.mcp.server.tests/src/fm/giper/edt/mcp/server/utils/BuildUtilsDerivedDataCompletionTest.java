/**
 * MCP Server for EDT - Tests
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */
package fm.giper.edt.mcp.server.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.Test;

import com._1c.g5.v8.derived.DerivedDataStatus;
import com._1c.g5.v8.derived.IDerivedDataManager;
import com._1c.g5.v8.derived.pipeline.DerivedDataSegmentBucket;
import com._1c.g5.v8.derived.pipeline.PipelineStatus;
import fm.giper.edt.mcp.server.utils.BuildUtils.DerivedDataCompletion;

/** Tests the checked SDK completion boundary, without a workspace or fabricated SDK state. */
public class BuildUtilsDerivedDataCompletionTest
{
    private static final long TIMEOUT_MS = 1234L;

    @Test
    public void testUnavailableDiagnosticGettersDoNotReplaceObservedCompletion() throws Exception
    {
        IDerivedDataManager manager = mock(IDerivedDataManager.class);
        when(manager.getDerivedDataStatus()).thenThrow(new IllegalStateException("status unavailable")); //$NON-NLS-1$
        when(manager.isIdle()).thenThrow(new IllegalStateException("idle unavailable")); //$NON-NLS-1$
        when(manager.waitAllComputations(TIMEOUT_MS)).thenReturn(true);
        when(manager.isAllComputed()).thenReturn(true);
        String snapshot = BuildUtils.derivedDataSnapshot(manager);
        assertTrue(snapshot, snapshot.contains("status=unobservable(IllegalStateException)")); //$NON-NLS-1$
        assertTrue(snapshot, snapshot.contains("isIdle=unobservable(IllegalStateException)")); //$NON-NLS-1$
        assertTrue(snapshot, snapshot.contains("isAllComputed=true")); //$NON-NLS-1$
        assertEquals(DerivedDataCompletion.COMPLETE,
            BuildUtils.waitForDerivedDataCompletion(manager, TIMEOUT_MS));
    }

    @Test
    public void testOneUnavailableStatusFieldDoesNotHideOtherFields()
    {
        IDerivedDataManager manager = mock(IDerivedDataManager.class);
        DerivedDataStatus status = mock(DerivedDataStatus.class);
        when(manager.getDerivedDataStatus()).thenReturn(status);
        when(status.getReadySegments()).thenThrow(new IllegalStateException("segments unavailable")); //$NON-NLS-1$
        when(status.isModelSyncActive()).thenReturn(true);
        String snapshot = BuildUtils.derivedDataSnapshot(manager);
        assertTrue(snapshot, snapshot.contains("pipelineStatus=unobservable")); //$NON-NLS-1$
        assertTrue(snapshot, snapshot.contains("activeStage=unobservable")); //$NON-NLS-1$
        assertTrue(snapshot, snapshot.contains("readySegments=unobservable(IllegalStateException)")); //$NON-NLS-1$
        assertTrue(snapshot, snapshot.contains("modelSyncActive=true")); //$NON-NLS-1$
        assertTrue(snapshot, snapshot.contains("isAllComputed=false")); //$NON-NLS-1$
    }

    @Test
    public void testMissingManagerIsUnobservable() throws Exception
    {
        assertEquals(DerivedDataCompletion.UNOBSERVABLE,
            BuildUtils.waitForDerivedDataCompletion((IDerivedDataManager) null, TIMEOUT_MS));
    }

    @Test
    public void testNullProjectIsUnobservableWithoutResolvingServices() throws Exception
    {
        assertEquals(DerivedDataCompletion.UNOBSERVABLE, BuildUtils.waitForDerivedDataCompletion(null));
    }

    @Test
    public void testSdkTimeoutCannotBecomeCompletion() throws Exception
    {
        IDerivedDataManager manager = mock(IDerivedDataManager.class);
        when(manager.waitAllComputations(TIMEOUT_MS)).thenReturn(false);
        assertEquals(DerivedDataCompletion.PENDING,
            BuildUtils.waitForDerivedDataCompletion(manager, TIMEOUT_MS));
        verify(manager, never()).isAllComputed();
    }

    @Test
    public void testSdkTrueWhilePipelineIsNotEmptyCannotBecomeCompletion() throws Exception
    {
        IDerivedDataManager manager = mock(IDerivedDataManager.class);
        when(manager.waitAllComputations(TIMEOUT_MS)).thenReturn(true);
        when(manager.isAllComputed()).thenReturn(false);
        assertEquals(DerivedDataCompletion.PENDING,
            BuildUtils.waitForDerivedDataCompletion(manager, TIMEOUT_MS));
    }

    @Test
    public void testObservedCompletionUsesAllComputationsAndExactTimeout() throws Exception
    {
        IDerivedDataManager manager = mock(IDerivedDataManager.class);
        when(manager.waitAllComputations(TIMEOUT_MS)).thenReturn(true);
        when(manager.isAllComputed()).thenReturn(true);
        assertEquals(DerivedDataCompletion.COMPLETE,
            BuildUtils.waitForDerivedDataCompletion(manager, TIMEOUT_MS));
        verify(manager).waitAllComputations(TIMEOUT_MS);
        verify(manager, never()).isIdle();
    }

    @Test(expected = IllegalStateException.class)
    public void testPlatformFailurePropagatesToTheMutationAwareCaller() throws Exception
    {
        IDerivedDataManager manager = mock(IDerivedDataManager.class);
        when(manager.waitAllComputations(TIMEOUT_MS)).thenThrow(new IllegalStateException("SDK failed")); //$NON-NLS-1$
        BuildUtils.waitForDerivedDataCompletion(manager, TIMEOUT_MS);
    }

    @Test(expected = InterruptedException.class)
    public void testInterruptionPropagatesToTheCaller() throws Exception
    {
        IDerivedDataManager manager = mock(IDerivedDataManager.class);
        when(manager.waitAllComputations(TIMEOUT_MS)).thenThrow(new InterruptedException("cancelled")); //$NON-NLS-1$
        BuildUtils.waitForDerivedDataCompletion(manager, TIMEOUT_MS);
    }

    @Test
    public void testNonpositiveCompletionBudgetNeverCallsInfiniteSdkWait() throws Exception
    {
        IDerivedDataManager manager = org.mockito.Mockito.mock(IDerivedDataManager.class);
        assertEquals(BuildUtils.DerivedDataCompletion.PENDING,
            BuildUtils.waitForDerivedDataCompletion(manager, 0L));
        assertEquals(BuildUtils.DerivedDataCompletion.PENDING,
            BuildUtils.waitForDerivedDataCompletion(manager, -1L));
        org.mockito.Mockito.verifyZeroInteractions(manager);
    }


    @Test
    public void testEmptySyncWaitsForActivatedModelAndPostBuildStage() throws Exception
    {
        RebuildFixture fixture = new RebuildFixture();
        DerivedDataStatus early = status(DerivedDataSegmentBucket.SYNC, false);
        when(fixture.manager.getDerivedDataStatus()).thenReturn(early, ready(false));
        assertEquals(DerivedDataCompletion.COMPLETE, fixture.await(100L));
        assertEquals(TimeUnit.MILLISECONDS.toNanos(25L), fixture.clock.get());
    }

    @Test
    public void testEmptySyncNeverBecomesCompleteWithoutStageActivation() throws Exception
    {
        RebuildFixture fixture = new RebuildFixture();
        when(fixture.manager.getDerivedDataStatus()).thenReturn(status(DerivedDataSegmentBucket.SYNC, false));
        assertEquals(DerivedDataCompletion.PENDING, fixture.await(33L));
        assertEquals(TimeUnit.MILLISECONDS.toNanos(33L), fixture.clock.get());
        verify(fixture.manager, never()).isComputed(ProjectStateChecker.modelSegments());
    }

    @Test
    public void testPostBuildStageWithoutMetadataSegmentIsNotComplete() throws Exception
    {
        RebuildFixture fixture = new RebuildFixture();
        DerivedDataStatus incomplete = status(DerivedDataSegmentBucket.AFTER_BUILD, false);
        incomplete.getReadySegments().add(ProjectStateChecker.modelSegments().get(1));
        when(fixture.manager.getDerivedDataStatus()).thenReturn(incomplete);
        assertEquals(DerivedDataCompletion.PENDING, fixture.await(33L));
        verify(fixture.manager, never()).isComputed(ProjectStateChecker.modelSegments());
    }

    @Test
    public void testReadySnapshotCannotReplaceLiveNamedModelPredicate() throws Exception
    {
        RebuildFixture fixture = new RebuildFixture();
        when(fixture.manager.isComputed(ProjectStateChecker.modelSegments())).thenReturn(false);
        assertEquals(DerivedDataCompletion.PENDING, fixture.await(33L));
        verify(fixture.manager, org.mockito.Mockito.atLeastOnce()).isComputed(ProjectStateChecker.modelSegments());
    }

    @Test
    public void testStaleSyncActiveFlagWithReadyEmptyModelCanComplete() throws Exception
    {
        RebuildFixture fixture = new RebuildFixture();
        when(fixture.manager.getDerivedDataStatus()).thenReturn(ready(true));
        assertEquals(DerivedDataCompletion.COMPLETE, fixture.await(100L));
        assertEquals(0L, fixture.clock.get());
        verify(fixture.manager).isComputed(ProjectStateChecker.modelSegments());
    }

    @Test
    public void testMissingRebuildManagerIsUnobservable() throws Exception
    {
        AtomicLong clock = new AtomicLong();
        assertEquals(DerivedDataCompletion.UNOBSERVABLE,
            BuildUtils.waitForRebuildCompletion(() -> null, 100L, clock::get, clock::addAndGet));
    }

    @Test
    public void testMissingRebuildStatusIsUnobservable() throws Exception
    {
        RebuildFixture fixture = new RebuildFixture();
        when(fixture.manager.getDerivedDataStatus()).thenReturn(null);
        assertEquals(DerivedDataCompletion.UNOBSERVABLE, fixture.await(100L));
    }

    @Test
    public void testManagerReplacementInvalidatesReadyOldManager() throws Exception
    {
        RebuildFixture fixture = new RebuildFixture();
        IDerivedDataManager replacement = mock(IDerivedDataManager.class);
        AtomicInteger reads = new AtomicInteger();
        fixture.managers = () -> reads.getAndIncrement() < 2 ? fixture.manager : replacement;
        assertEquals(DerivedDataCompletion.UNOBSERVABLE, fixture.await(100L));
        org.mockito.Mockito.verifyZeroInteractions(replacement);
    }

    @Test
    public void testSdkFalseCannotBecomeRebuildCompletion() throws Exception
    {
        RebuildFixture fixture = new RebuildFixture();
        when(fixture.manager.waitAllComputations(org.mockito.ArgumentMatchers.anyLong())).thenReturn(false);
        assertEquals(DerivedDataCompletion.PENDING, fixture.await(100L));
        verify(fixture.manager, never()).getDerivedDataStatus();
    }

    @Test
    public void testSdkNominalTimeoutOverrunCannotBecomeCompletion() throws Exception
    {
        RebuildFixture fixture = new RebuildFixture();
        when(fixture.manager.waitAllComputations(org.mockito.ArgumentMatchers.anyLong())).thenAnswer(call -> {
            fixture.clock.addAndGet(TimeUnit.MILLISECONDS.toNanos(101L));
            return true;
        });
        assertEquals(DerivedDataCompletion.PENDING, fixture.await(100L));
        verify(fixture.manager, never()).getDerivedDataStatus();
    }

    @Test
    public void testRebuildBudgetZeroNeverResolvesManagerOrStartsUnboundedSdkWait() throws Exception
    {
        RebuildFixture fixture = new RebuildFixture();
        fixture.managers = () -> { throw new AssertionError("Manager must not be resolved"); }; //$NON-NLS-1$
        assertEquals(DerivedDataCompletion.PENDING, fixture.await(0L));
        assertEquals(DerivedDataCompletion.PENDING, fixture.await(-1L));
    }

    @Test(expected = InterruptedException.class)
    public void testConditionalStagePollInterruptionPropagates() throws Exception
    {
        RebuildFixture fixture = new RebuildFixture();
        when(fixture.manager.getDerivedDataStatus()).thenReturn(status(DerivedDataSegmentBucket.SYNC, false));
        BuildUtils.waitForRebuildCompletion(fixture.managers, 100L, fixture.clock::get,
            nanos -> { throw new InterruptedException("cancelled"); }); //$NON-NLS-1$
    }

    @Test(expected = IllegalStateException.class)
    public void testRebuildStatusExceptionPropagatesWithoutSuccessFallback() throws Exception
    {
        RebuildFixture fixture = new RebuildFixture();
        when(fixture.manager.getDerivedDataStatus()).thenThrow(new IllegalStateException("SDK failed")); //$NON-NLS-1$
        fixture.await(100L);
    }

    @Test
    public void testStatusRegressionDuringLivePredicatesIsNotComplete() throws Exception
    {
        RebuildFixture fixture = new RebuildFixture();
        DerivedDataStatus early = status(DerivedDataSegmentBucket.SYNC, false);
        when(fixture.manager.getDerivedDataStatus()).thenReturn(ready(false), ready(false), ready(false), early);
        assertEquals(DerivedDataCompletion.PENDING, fixture.await(33L));
    }

    @Test
    public void testSpentPassesClipRemainingSdkTimeoutToPositiveBudget() throws Exception
    {
        RebuildFixture fixture = new RebuildFixture();
        when(fixture.manager.getDerivedDataStatus()).thenReturn(status(DerivedDataSegmentBucket.SYNC, false));
        assertEquals(DerivedDataCompletion.PENDING, fixture.await(33L));
        org.mockito.ArgumentCaptor<Long> waits = org.mockito.ArgumentCaptor.forClass(Long.class);
        verify(fixture.manager, org.mockito.Mockito.times(2)).waitAllComputations(waits.capture());
        assertEquals(java.util.Arrays.asList(33L, 8L), waits.getAllValues());
    }

    @Test
    public void testPreexistingCancellationStartsNoSdkWaitAndRetainsFlag() throws Exception
    {
        RebuildFixture fixture = new RebuildFixture();
        Thread.currentThread().interrupt();
        try
        {
            try
            {
                fixture.await(100L);
                org.junit.Assert.fail("Expected interrupted rebuild wait"); //$NON-NLS-1$
            }
            catch (InterruptedException expected)
            {
                assertTrue(Thread.currentThread().isInterrupted());
                verify(fixture.manager, never()).waitAllComputations(org.mockito.ArgumentMatchers.anyLong());
            }
        }
        finally
        {
            Thread.interrupted();
        }
    }

    @Test
    public void testNamedRebuildPredicateFailurePropagatesExactExceptionWithoutPolling() throws Exception
    {
        RebuildFixture fixture = new RebuildFixture();
        IllegalStateException failure = new IllegalStateException("Named SDK predicate failed"); //$NON-NLS-1$
        when(fixture.manager.isComputed(ProjectStateChecker.modelSegments())).thenThrow(failure);
        try
        {
            fixture.await(100L);
            org.junit.Assert.fail("Expected checked predicate failure"); //$NON-NLS-1$
        }
        catch (IllegalStateException observed)
        {
            org.junit.Assert.assertSame(failure, observed);
            assertEquals(0L, fixture.clock.get());
            verify(fixture.manager).isComputed(ProjectStateChecker.modelSegments());
        }
    }

    @Test
    public void testInnerRebuildStatusFailurePropagatesExactExceptionWithoutPolling() throws Exception
    {
        RebuildFixture fixture = new RebuildFixture();
        IllegalStateException failure = new IllegalStateException("Inner SDK status failed"); //$NON-NLS-1$
        when(fixture.manager.getDerivedDataStatus()).thenReturn(ready(false)).thenThrow(failure);
        try
        {
            fixture.await(100L);
            org.junit.Assert.fail("Expected checked status failure"); //$NON-NLS-1$
        }
        catch (IllegalStateException observed)
        {
            org.junit.Assert.assertSame(failure, observed);
            assertEquals(0L, fixture.clock.get());
            verify(fixture.manager, org.mockito.Mockito.times(2)).getDerivedDataStatus();
            verify(fixture.manager, never()).isComputed(ProjectStateChecker.modelSegments());
        }
    }

    private static DerivedDataStatus status(DerivedDataSegmentBucket stage, boolean syncActive)
    {
        return new DerivedDataStatus(stage, PipelineStatus.EMPTY, syncActive);
    }

    private static DerivedDataStatus ready(boolean syncActive)
    {
        DerivedDataStatus status = status(DerivedDataSegmentBucket.AFTER_BUILD, syncActive);
        status.getReadySegments().addAll(ProjectStateChecker.modelSegments());
        return status;
    }

    private static final class RebuildFixture
    {
        final AtomicLong clock = new AtomicLong();
        final IDerivedDataManager manager = mock(IDerivedDataManager.class);
        Supplier<IDerivedDataManager> managers = () -> manager;

        RebuildFixture() throws InterruptedException
        {
            when(manager.waitAllComputations(org.mockito.ArgumentMatchers.anyLong())).thenReturn(true);
            when(manager.isAllComputed()).thenReturn(true);
            when(manager.isComputed(ProjectStateChecker.modelSegments())).thenReturn(true);
            when(manager.getDerivedDataStatus()).thenReturn(ready(false));
        }

        DerivedDataCompletion await(long timeoutMs) throws InterruptedException
        {
            return BuildUtils.waitForRebuildCompletion(managers, timeoutMs, clock::get, nanos -> {
                assertTrue(nanos > 0L);
                assertTrue(nanos <= TimeUnit.MILLISECONDS.toNanos(25L));
                clock.addAndGet(nanos);
            });
        }
    }

}
