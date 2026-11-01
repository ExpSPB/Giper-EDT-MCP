/**
 * MCP Server for EDT - Tests
 * Copyright (C) 2026 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */
package fm.giper.edt.mcp.server.utils;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.Status;
import org.junit.Test;

import com._1c.g5.v8.dt.platform.services.core.infobases.IInfobaseAccessManager;
import com._1c.g5.v8.dt.platform.services.core.infobases.IInfobaseManager;
import com._1c.g5.v8.dt.platform.services.model.InfobaseReference;
import com.e1c.g5.dt.applications.IApplication;
import com.e1c.g5.dt.applications.IApplicationManager;
import fm.giper.edt.mcp.server.protocol.ToolResult;

public class LauncherDefaultPreservationTest
{
    private static final long WAIT_MS = 20_000;

    @Test
    public void duplicateRefusalPreservesBothProjectsAndUsesFreshObjects() throws Exception
    {
        Fixture f = new Fixture();
        IApplication before = f.live.get(f.a).get(f.idA);
        try (var scope = f.scope(null, WAIT_MS))
        {
            String refusal = InfobaseRegistration.add(f.infobases, f.duplicate());
            assertTrue(refusal.contains("already registered")); //$NON-NLS-1$
            assertTrue(f.raw.isEmpty()); // Nested add must not wait/recover under the outer lease.
            verify(f.applications, never()).getApplication(any(), anyString());
        }
        f.assertDefaults();
        assertNotSame(before, f.selected.get(f.a));
        assertSame(f.live.get(f.a).get(f.idA), f.selected.get(f.a));
        verify(f.infobases, never()).add(any(), any());
        verify(f.applications, never()).getDefaultApplication(any());
    }

    @Test
    public void successfulAddAndUnrelatedRemovalPreserveBothDefaults() throws Exception
    {
        Fixture f = new Fixture();
        var other = new ServerInfobaseTarget("host", "unrelated").reference("other"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        try (var scope = f.scope(null, WAIT_MS))
        {
            assertNull(InfobaseRegistration.add(f.infobases, other));
        }
        f.assertDefaults();
        try (var scope = f.scope(null, WAIT_MS))
        {
            InfobaseRegistration.remove(f.infobases, other);
        }
        f.assertDefaults();
        assertFalse(f.references.contains(other));
    }

    @Test
    public void failedAddNestedCompensationRecoversOnceAfterOutermostUnlock() throws Exception
    {
        Fixture f = new Fixture();
        var other = new ServerInfobaseTarget("host", "failed").reference("failed"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        doAnswer(call -> {
            f.references.add(call.getArgument(0));
            throw new IllegalStateException("save failed"); //$NON-NLS-1$
        }).when(f.infobases).add(eq(other), any());
        try (var scope = f.scope(null, WAIT_MS))
        {
            assertTrue(InfobaseRegistration.add(f.infobases, other).contains("removed.")); //$NON-NLS-1$
            verify(f.applications, never()).getApplication(any(), anyString());
        }
        assertFalse(f.references.contains(other));
        f.assertDefaults();
        verify(f.applications, times(2)).setDefaultApplication(any(), any());
        verify(f.infobases, times(3)).reload(any());
    }

    @Test
    public void aRemovedSelectedIdIsNotResurrectedAndTheOtherProjectIsPreserved() throws Exception
    {
        Fixture f = new Fixture();
        try (var scope = f.scope(null, WAIT_MS))
        {
            InfobaseRegistration.remove(f.infobases, f.refA);
        }
        assertNull(f.raw.get(f.a));
        assertEquals(f.idB, f.raw.get(f.b));
        verify(f.applications, never()).setDefaultApplication(eq(f.a), any());
    }

    @Test
    public void anImplicitSingletonIsNeverMaterialized() throws Exception
    {
        Fixture f = new Fixture();
        f.raw.clear();
        f.references.remove(f.refB);
        f.live.remove(f.b);
        try (var scope = f.scope(null, WAIT_MS))
        {
            InfobaseRegistration.add(f.infobases, f.duplicate());
        }
        assertTrue(f.raw.isEmpty());
        verify(f.applications, never()).getApplication(any(), anyString());
        verify(f.applications, never()).getDefaultApplication(any());
        verify(f.applications, never()).setDefaultApplication(any(), any());
    }

    @Test
    public void aVisibleChoiceMadeDuringLookupIsNotOverwritten() throws Exception
    {
        Fixture f = new Fixture();
        doAnswer(call -> {
            f.raw.put(f.a, "GUI-new-choice"); //$NON-NLS-1$
            return Optional.of(f.live.get(f.a).get(f.idA));
        }).when(f.applications).getApplication(f.a, f.idA);
        try (var scope = f.scope(null, WAIT_MS))
        {
            InfobaseRegistration.add(f.infobases, f.duplicate());
        }
        assertEquals("GUI-new-choice", f.raw.get(f.a)); //$NON-NLS-1$
        assertEquals(f.idB, f.raw.get(f.b));
        verify(f.applications, never()).setDefaultApplication(eq(f.a), any());
    }

    @Test
    public void aNewChoiceBeforeAnotherReloadReplacesTheEarlierCandidate() throws Exception
    {
        Fixture f = new Fixture();
        String newer = f.idB;
        f.live.get(f.a).put(newer, f.application(f.a, newer));
        try (var scope = f.scope(null, WAIT_MS))
        {
            InfobaseRegistration.add(f.infobases, f.duplicate());
            f.raw.put(f.a, newer);
            InfobaseRegistration.add(f.infobases, f.duplicate());
        }
        assertEquals(newer, f.raw.get(f.a));
        assertEquals(f.idB, f.raw.get(f.b));
    }

    @Test
    public void snapshotFailurePreventsNativeReload() throws Exception
    {
        Fixture f = new Fixture();
        var environment = new LauncherDefaultPreservation.Environment()
        {
            public IProject[] projects() { return new IProject[] {f.a}; }
            public String rawId(IProject project) { throw new IllegalStateException("preferences unavailable"); } //$NON-NLS-1$
            public IApplicationManager manager() { return f.applications; }
        };
        try (var scope = InfobaseMutationCoordinator.acquireForTesting(environment, null, WAIT_MS))
        {
            assertTrue(InfobaseRegistration.add(f.infobases, f.duplicate()).contains("no registration was added")); //$NON-NLS-1$
        }
        verify(f.infobases, never()).reload(any());
        f.assertDefaults();
    }

    @Test
    public void missingApplicationAfterFailedLoadIsUnknownRatherThanRemoved() throws Exception
    {
        Fixture f = new Fixture();
        when(f.infobases.getLoadStatus()).thenReturn(Status.CANCEL_STATUS);
        // Avoid executing the fixture's existing answer with matchers' null project while restubbing.
        doReturn(Optional.empty()).when(f.applications).getApplication(any(), anyString());
        var failure = assertThrows(LauncherDefaultPreservation.Failure.class, () -> {
            try (var scope = f.scope(null, WAIT_MS))
            {
                assertTrue(InfobaseRegistration.add(f.infobases, f.duplicate()).contains("no registration was added")); //$NON-NLS-1$
            }
        });
        assertEquals("UNVERIFIED", failure.outcome()); //$NON-NLS-1$
        assertTrue(failure.getMessage().contains("Could not read the current EDT launcher list")); //$NON-NLS-1$
        assertTrue(failure.getMessage().contains("Registration UUID")); //$NON-NLS-1$
        assertTrue(failure.getCause().getMessage().contains("Application absence could not be verified")); //$NON-NLS-1$
        verify(f.infobases, times(1)).reload(any());
        verify(f.infobases, never()).add(any(), any());
        verify(f.applications, never()).setDefaultApplication(any(), any());
    }

    @Test
    public void realBoundedCredentialGuardDefersRecoveryUntilItsOuterLeaseCloses() throws Exception
    {
        Fixture f = new Fixture();
        var access = mock(IInfobaseAccessManager.class);
        InfobaseAccessSupport.clearCredentials(f.refA, write -> {
            try (var scope = f.scope(null, WAIT_MS))
            {
                var current = InfobaseRegistration.findCleanupRegistration(f.infobases,
                    f.refA.getUuid(), f.refA.getName());
                write.run(current);
                assertTrue(f.raw.isEmpty());
                verify(f.applications, never()).getApplication(any(), anyString());
            }
            assertTrue(f.raw.isEmpty()); // The real credential worker still owns one lease.
        }, () -> access, WAIT_MS);
        f.assertDefaults();
        verify(access).updateSettings(eq(f.refA), any());
    }

    @Test
    public void realCredentialDeadlineRejectsUnfinishedRecoveryAfterTheNativeClearCommitted() throws Exception
    {
        Fixture f = new Fixture();
        var access = mock(IInfobaseAccessManager.class);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(call -> {
            entered.countDown();
            assertTrue(release.await(WAIT_MS, TimeUnit.MILLISECONDS));
            return Optional.of(f.live.get(f.a).get(f.idA));
        }).when(f.applications).getApplication(f.a, f.idA);
        AtomicReference<Throwable> outcome = new AtomicReference<>();
        Thread caller = new Thread(() -> {
            try
            {
                InfobaseAccessSupport.clearCredentials(f.refA, write -> {
                    try (var scope = f.scope(null, WAIT_MS))
                    {
                        var current = InfobaseRegistration.findCleanupRegistration(f.infobases,
                            f.refA.getUuid(), f.refA.getName());
                        write.run(current);
                    }
                }, () -> access, 1_000);
            }
            catch (Throwable failure)
            {
                outcome.set(failure);
            }
        }, "test real credential deadline during default recovery"); //$NON-NLS-1$
        caller.start();
        try
        {
            assertTrue(entered.await(WAIT_MS, TimeUnit.MILLISECONDS));
            caller.join(WAIT_MS);
            assertFalse(caller.isAlive());
            assertNotNull("native clear success must not publish an unfinished recovery as success", outcome.get()); //$NON-NLS-1$
            assertTrue(outcome.get().getMessage().contains("TIMED_OUT")); //$NON-NLS-1$
            verify(access).updateSettings(eq(f.refA), any());
        }
        finally
        {
            release.countDown();
            caller.join(WAIT_MS);
        }
        try (var ignored = InfobaseMutationCoordinator.acquire(WAIT_MS))
        {
            verify(f.applications, never()).setDefaultApplication(any(), any());
        }
    }

    @Test
    public void aResolutionFailureAfterVerifiedReloadIsNotTreatedAsRemoval() throws Exception
    {
        Fixture f = new Fixture();
        doThrow(new IllegalStateException("provider unavailable")).when(f.applications) //$NON-NLS-1$
            .getApplication(any(), anyString());
        var failure = assertThrows(LauncherDefaultPreservation.Failure.class, () -> {
            try (var scope = f.scope(null, WAIT_MS))
            {
                InfobaseRegistration.add(f.infobases, f.duplicate());
            }
        });
        assertEquals("UNVERIFIED", failure.outcome()); //$NON-NLS-1$
        assertTrue(failure.getCause().getMessage().contains("provider unavailable")); //$NON-NLS-1$
        verify(f.applications, never()).setDefaultApplication(any(), any());
    }

    @Test
    public void timedOutLookupCannotWriteLaterAndCoordinatorEventuallyReleases() throws Exception
    {
        blockedLookup(false);
    }

    @Test
    public void parentCancellationAlsoClosesTheNestedRecoveryWriteAdmission() throws Exception
    {
        blockedLookup(true);
    }

    private static void blockedLookup(boolean closeParent) throws Exception
    {
        Fixture f = new Fixture();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var parent = new CredentialWriteControl();
        doAnswer(call -> {
            entered.countDown();
            assertTrue(release.await(WAIT_MS, TimeUnit.MILLISECONDS));
            return Optional.of(f.live.get(f.a).get(f.idA));
        }).when(f.applications).getApplication(f.a, f.idA);
        AtomicReference<Throwable> outcome = new AtomicReference<>();
        Thread caller = f.start(outcome, parent, closeParent ? WAIT_MS : 1_000);
        try
        {
            assertTrue(entered.await(WAIT_MS, TimeUnit.MILLISECONDS));
            if (closeParent)
            {
                parent.close();
                release.countDown();
            }
            caller.join(WAIT_MS);
            assertFalse(caller.isAlive());
            assertEquals("UNVERIFIED", LauncherDefaultPreservation.failure(outcome.get()).outcome()); //$NON-NLS-1$
            if (!closeParent)
            {
                assertThrows(InfobaseMutationCoordinator.Unavailable.class,
                    () -> InfobaseMutationCoordinator.acquire(10));
            }
        }
        finally
        {
            parent.close();
            release.countDown();
            caller.join(WAIT_MS);
        }
        try (var ignored = InfobaseMutationCoordinator.acquire(WAIT_MS))
        {
            verify(f.applications, never()).setDefaultApplication(any(), any());
        }
    }

    @Test
    public void admittedNativeSetterIsPendingAndCountsAgainstTheParentAdmission() throws Exception
    {
        Fixture f = new Fixture();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var parent = new CredentialWriteControl();
        doAnswer(call -> {
            entered.countDown();
            assertTrue(release.await(WAIT_MS, TimeUnit.MILLISECONDS));
            f.raw.put(f.a, f.idA);
            return null;
        }).when(f.applications).setDefaultApplication(eq(f.a), any());
        AtomicReference<Throwable> outcome = new AtomicReference<>();
        Thread caller = f.start(outcome, parent, 1_000);
        try
        {
            assertTrue(entered.await(WAIT_MS, TimeUnit.MILLISECONDS));
            parent.close();
            caller.join(WAIT_MS);
            assertFalse(caller.isAlive());
            assertEquals("PENDING", LauncherDefaultPreservation.failure(outcome.get()).outcome()); //$NON-NLS-1$
            assertTrue(parent.writeInProgress());
        }
        finally
        {
            release.countDown();
            caller.join(WAIT_MS);
        }
        try (var ignored = InfobaseMutationCoordinator.acquire(WAIT_MS))
        {
            assertFalse(parent.writeInProgress());
            verify(f.applications, never()).setDefaultApplication(eq(f.b), any());
        }
    }

    @Test
    public void recoveryScopeRejectsRecursiveReloadWithoutLosingTheOriginalFailure() throws Exception
    {
        Fixture f = new Fixture();
        doAnswer(call -> {
            InfobaseMutationCoordinator.beforeReload();
            return Optional.empty();
        }).when(f.applications).getApplication(any(), anyString());
        var original = new IllegalArgumentException("original failure"); //$NON-NLS-1$
        try
        {
            try (var scope = f.scope(null, WAIT_MS))
            {
                InfobaseRegistration.add(f.infobases, f.duplicate());
                throw original;
            }
        }
        catch (IllegalArgumentException failure)
        {
            assertSame(original, failure);
            var preservation = LauncherDefaultPreservation.failure(failure);
            assertNotNull(preservation);
            assertEquals("UNVERIFIED", preservation.outcome()); //$NON-NLS-1$
            assertTrue(preservation.getCause().getMessage().contains("cannot start another launcher reload")); //$NON-NLS-1$
            var json = LauncherDefaultPreservation.decorate(ToolResult.error("original"), failure).toJson(); //$NON-NLS-1$
            assertTrue(json.contains("operationMayHaveCompleted")); //$NON-NLS-1$
        }
        verify(f.infobases, times(1)).reload(any());
    }

    private static final class Fixture implements LauncherDefaultPreservation.Environment
    {
        final IProject a = mock(IProject.class);
        final IProject b = mock(IProject.class);
        final IApplicationManager applications = mock(IApplicationManager.class);
        final IInfobaseManager infobases = mock(IInfobaseManager.class);
        final InfobaseReference refA = new ServerInfobaseTarget("host", "a").reference("a"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        final InfobaseReference refB = new ServerInfobaseTarget("host", "b").reference("b"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        final String idA = refA.getUuid().toString();
        final String idB = refB.getUuid().toString();
        final Map<IProject, String> raw = new ConcurrentHashMap<>();
        final Map<IProject, Map<String, IApplication>> live = new ConcurrentHashMap<>();
        final Map<IProject, IApplication> selected = new ConcurrentHashMap<>();
        final List<InfobaseReference> references = new CopyOnWriteArrayList<>();

        Fixture() throws Exception
        {
            when(a.getName()).thenReturn("current"); //$NON-NLS-1$
            when(b.getName()).thenReturn("unrelated"); //$NON-NLS-1$
            references.addAll(List.of(refA, refB));
            raw.put(a, idA);
            raw.put(b, idB);
            live.put(a, new ConcurrentHashMap<>(Map.of(idA, application(a, idA))));
            live.put(b, new ConcurrentHashMap<>(Map.of(idB, application(b, idB))));
            when(infobases.getLoadStatus()).thenReturn(Status.OK_STATUS);
            when(infobases.getAll()).thenAnswer(call -> new java.util.ArrayList<>(references));
            when(infobases.findInfobaseByUuid(any())).thenAnswer(call -> {
                UUID id = call.getArgument(0);
                return references.stream().filter(ref -> id.equals(ref.getUuid())).findFirst();
            });
            doAnswer(call -> { references.add(call.getArgument(0)); return null; })
                .when(infobases).add(any(), any());
            doAnswer(call -> { references.remove(call.getArgument(0)); return null; })
                .when(infobases).delete(any());
            doAnswer(call -> {
                raw.clear(); // Exact-.5 global DELETED lifecycle effect.
                for (var project : live.entrySet())
                {
                    project.getValue().keySet().removeIf(id -> references.stream()
                        .noneMatch(ref -> ref.getUuid().toString().equals(id)));
                    project.getValue().replaceAll((id, old) -> application(project.getKey(), id));
                }
                return null;
            }).when(infobases).reload(any());
            when(applications.getApplication(any(), anyString())).thenAnswer(call -> {
                IProject project = call.getArgument(0);
                return Optional.ofNullable(live.getOrDefault(project, Map.of()).get(call.getArgument(1)));
            });
            doAnswer(call -> {
                IProject project = call.getArgument(0);
                IApplication app = call.getArgument(1);
                selected.put(project, app);
                raw.put(project, app.getId());
                return null;
            }).when(applications).setDefaultApplication(any(), any());
        }

        IApplication application(IProject project, String id)
        {
            var app = mock(IApplication.class);
            when(app.getProject()).thenReturn(project);
            when(app.getId()).thenReturn(id);
            return app;
        }

        InfobaseReference duplicate()
        {
            return new ServerInfobaseTarget("host", "a").reference("duplicate"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        }

        InfobaseMutationCoordinator.Lease scope(CredentialWriteControl parent, long timeoutMs)
        {
            return InfobaseMutationCoordinator.acquireForTesting(this, parent, timeoutMs);
        }

        Thread start(AtomicReference<Throwable> outcome, CredentialWriteControl parent, long timeoutMs)
        {
            Thread caller = new Thread(() -> {
                try (var scope = scope(parent, timeoutMs))
                {
                    InfobaseRegistration.add(infobases, duplicate());
                }
                catch (Throwable failure)
                {
                    outcome.set(failure);
                }
            }, "test default recovery caller"); //$NON-NLS-1$
            caller.start();
            return caller;
        }

        void assertDefaults()
        {
            assertEquals(idA, raw.get(a));
            assertEquals(idB, raw.get(b));
        }

        public IProject[] projects() { return new IProject[] {a, b}; }
        public String rawId(IProject project) { return raw.get(project); }
        public IApplicationManager manager() { return applications; }
    }
}
