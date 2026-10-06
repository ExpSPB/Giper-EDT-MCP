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
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.eclipse.core.resources.IResourceStatus;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SafeRunner;
import org.junit.Test;

public class RenameSdkFailureObserverTest
{
    @Test
    public void testScopeRegistersAndRemovesExactlyOnce()
    {
        ILog log = mock(ILog.class);
        RenameSdkFailureObserver observer = RenameSdkFailureObserver.open(log);
        verify(log).addLogListener(observer);
        observer.close();
        observer.close();
        verify(log, times(1)).removeLogListener(observer);
        assertNull(observer.failure());
    }

    @Test
    public void testSameThreadCoreExceptionCannotRemainSuccessful()
    {
        try (RenameSdkFailureObserver observer = RenameSdkFailureObserver.open(mock(ILog.class)))
        {
            observer.logging(resourceError(), "com._1c.g5.v8.dt.core");
            assertNotNull(observer.failure());
        }
    }

    @Test
    public void testOtherThreadCannotContaminateScopedPerform() throws Exception
    {
        try (RenameSdkFailureObserver observer = RenameSdkFailureObserver.open(mock(ILog.class)))
        {
            Thread other = new Thread(() -> observer.logging(resourceError(), "com._1c.g5.v8.dt.core"));
            other.start();
            other.join(5000L);
            assertEquals(false, other.isAlive());
            assertNull(observer.failure());
        }
    }

    @Test
    public void testOtherLoggingBundleIsExcluded()
    {
        try (RenameSdkFailureObserver observer = RenameSdkFailureObserver.open(mock(ILog.class)))
        {
            observer.logging(resourceError(), "unrelated.bundle");
            assertNull(observer.failure());
        }
    }

    @Test
    public void testWarningDoesNotBecomeResourceError()
    {
        try (RenameSdkFailureObserver observer = RenameSdkFailureObserver.open(mock(ILog.class)))
        {
            observer.logging(new Status(IStatus.WARNING, "com._1c.g5.v8.dt.core", "warning",
                new CoreException(Status.OK_STATUS)), "com._1c.g5.v8.dt.core");
            assertNull(observer.failure());
        }
    }

    @Test
    public void testChildCoreExceptionIsFoundWithoutMessageParsing()
    {
        try (RenameSdkFailureObserver observer = RenameSdkFailureObserver.open(mock(ILog.class)))
        {
            MultiStatus status = new MultiStatus("com._1c.g5.v8.dt.core", 0, "wrapper", null);
            status.add(resourceError());
            observer.logging(status, "com._1c.g5.v8.dt.core");
            assertNotNull(observer.failure());
        }
    }

    @Test
    public void testResourceStatusChildWithoutExceptionIsFound()
    {
        try (RenameSdkFailureObserver observer = RenameSdkFailureObserver.open(mock(ILog.class)))
        {
            IResourceStatus child = mock(IResourceStatus.class);
            when(child.getSeverity()).thenReturn(IStatus.ERROR);
            when(child.matches(IStatus.ERROR)).thenReturn(true);
            MultiStatus status = new MultiStatus("com._1c.g5.v8.dt.core", 0, "wrapper", null);
            status.add(child);
            observer.logging(status, "com._1c.g5.v8.dt.core");
            assertNotNull(observer.failure());
        }
    }

    @Test
    public void testCoreExceptionInThrowableCauseIsFound()
    {
        try (RenameSdkFailureObserver observer = RenameSdkFailureObserver.open(mock(ILog.class)))
        {
            observer.logging(new Status(IStatus.ERROR, "com._1c.g5.v8.dt.core", "wrapper",
                new IllegalStateException(new CoreException(Status.OK_STATUS))), "com._1c.g5.v8.dt.core");
            assertNotNull(observer.failure());
        }
    }

    @Test
    public void testRegistrationFailureStillAttemptsRemovalAndPreservesException()
    {
        ILog log = mock(ILog.class);
        RuntimeException expected = new IllegalStateException("registration failed");
        doThrow(expected).when(log).addLogListener(any());
        try
        {
            RenameSdkFailureObserver.open(log);
            fail("Registration failure must refuse before perform");
        }
        catch (RuntimeException actual)
        {
            assertSame(expected, actual);
            verify(log).removeLogListener(any());
        }
    }

    @Test
    public void testRemovalFailureIsVisibleAndNotRetried()
    {
        ILog log = mock(ILog.class);
        RenameSdkFailureObserver observer = RenameSdkFailureObserver.open(log);
        RuntimeException expected = new IllegalStateException("removal failed");
        doThrow(expected).when(log).removeLogListener(observer);
        try
        {
            observer.close();
            fail("Removal failure must remain observable");
        }
        catch (RuntimeException actual)
        {
            assertSame(expected, actual);
        }
        observer.close();
        verify(log, times(1)).removeLogListener(observer);
    }

    @Test
    public void testProbeFailureIsNotSwallowedBySafeRunnerIntoSuccess()
    {
        try (RenameSdkFailureObserver observer = RenameSdkFailureObserver.open(mock(ILog.class)))
        {
            IStatus broken = mock(IStatus.class);
            when(broken.matches(IStatus.ERROR)).thenThrow(new IllegalStateException("status unavailable"));
            observer.logging(broken, "com._1c.g5.v8.dt.core");
            assertNotNull(observer.failure());
        }
    }

    @Test
    public void testClosedScopeIgnoresLaterSameThreadLogs()
    {
        RenameSdkFailureObserver observer = RenameSdkFailureObserver.open(mock(ILog.class));
        observer.close();
        observer.logging(resourceError(), "com._1c.g5.v8.dt.core");
        assertNull(observer.failure());
    }

    @Test
    public void testAssertionErrorAbsorbedBySafeRunnerStillRefusesSuccess()
    {
        ILog log = mock(ILog.class);
        try (RenameSdkFailureObserver observer = RenameSdkFailureObserver.open(log))
        {
            IStatus broken = mock(IStatus.class);
            when(broken.matches(IStatus.ERROR)).thenReturn(true);
            when(broken.getChildren()).thenThrow(new AssertionError("status children unavailable"));
            SafeRunner.run(() -> observer.logging(broken, "com._1c.g5.v8.dt.core"));
            assertNotNull(observer.failure());
        }
        verify(log, times(1)).removeLogListener(any(RenameSdkFailureObserver.class));
    }

    private static IStatus resourceError()
    {
        return new Status(IStatus.ERROR, "com._1c.g5.v8.dt.core", "localized SDK message",
            new CoreException(new Status(IStatus.ERROR, "org.eclipse.core.resources", "file could not be deleted")));
    }
}
