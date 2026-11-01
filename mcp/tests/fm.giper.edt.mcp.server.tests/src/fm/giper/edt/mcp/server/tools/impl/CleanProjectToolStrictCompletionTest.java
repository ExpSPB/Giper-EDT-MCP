/**
 * MCP Server for EDT - Tests
 * Copyright (C) 2026 ExpSPB (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server.tools.impl;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.core.resources.IProject;
import org.junit.Test;

import com._1c.g5.v8.dt.core.platform.IDtProject;
import fm.giper.edt.mcp.server.tools.impl.CleanProjectTool.CleanCollection;
import fm.giper.edt.mcp.server.tools.impl.CleanProjectTool.ProjectCleanInfo;
import fm.giper.edt.mcp.server.tools.impl.CleanProjectTool.RestartSupport;
import fm.giper.edt.mcp.server.utils.BuildUtils.DerivedDataCompletion;
import fm.giper.edt.mcp.server.utils.LifecycleWaiter.ProjectRestartWaiter;
import fm.giper.edt.mcp.server.utils.RestartJobFailureSignal;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** The 1.0.12 completion guarantees also govern the new dependent-aware production path. */
public class CleanProjectToolStrictCompletionTest
{
    private static final class Platform extends RestartSupport
    {
        final IProject resource = mock(IProject.class);
        final IDtProject target = mock(IDtProject.class);
        final IProject siblingResource = mock(IProject.class);
        final IDtProject sibling = mock(IDtProject.class);
        final ProjectRestartWaiter waiter = mock(ProjectRestartWaiter.class);
        final RestartJobFailureSignal failures = mock(RestartJobFailureSignal.class);
        final AtomicInteger cleans = new AtomicInteger();
        DerivedDataCompletion completion = DerivedDataCompletion.COMPLETE;
        DerivedDataCompletion siblingCompletion = DerivedDataCompletion.COMPLETE;
        boolean includeSibling;
        boolean listenerAvailable = true;
        boolean started = true;
        int derivedWaits;

        Platform()
        {
            super(null, null);
            when(resource.getName()).thenReturn("Target"); //$NON-NLS-1$
            when(resource.exists()).thenReturn(true);
            when(resource.isOpen()).thenReturn(true);
            when(target.getName()).thenReturn("Target"); //$NON-NLS-1$
            when(target.getWorkspaceProject()).thenReturn(resource);
            when(siblingResource.getName()).thenReturn("Sibling"); //$NON-NLS-1$
            when(siblingResource.exists()).thenReturn(true);
            when(siblingResource.isOpen()).thenReturn(true);
            when(sibling.getName()).thenReturn("Sibling"); //$NON-NLS-1$
            when(sibling.getWorkspaceProject()).thenReturn(siblingResource);
            when(waiter.await(anyLong(), any(RestartJobFailureSignal.class))).thenReturn(true);
        }

        @Override Collection<IDtProject> dependents(IDtProject project)
        {
            return includeSibling ? List.of(sibling) : List.of();
        }
        @Override ProjectRestartWaiter prepare(IDtProject project) { return listenerAvailable ? waiter : null; }
        @Override RestartJobFailureSignal watchFailures() { return failures; }
        @Override boolean isStarted(IDtProject project) { return started; }
        @Override DerivedDataCompletion checkedDerivedData(IProject project)
        {
            derivedWaits++;
            return project == siblingResource ? siblingCompletion : completion;
        }

        JsonObject run()
        {
            CleanCollection collection = new CleanCollection();
            collection.projectsToClean.add(new ProjectCleanInfo(resource, target, "Target")); //$NON-NLS-1$
            collection.projectNamesList.add("Target"); //$NON-NLS-1$
            return JsonParser.parseString(CleanProjectTool.runCleanLifecycle(collection, 1_000, 20,
                (info, monitor) -> cleans.incrementAndGet(), this)).getAsJsonObject();
        }
    }

    @Test
    public void unavailableListenerRefusesBeforeSchedulingClean()
    {
        Platform platform = new Platform();
        platform.listenerAvailable = false;
        JsonObject result = platform.run();
        assertFalse(result.get("success").getAsBoolean()); //$NON-NLS-1$
        assertFalse(result.has("mutationCommitted")); //$NON-NLS-1$
        assertEquals(0, platform.cleans.get());
        assertEquals(0, platform.derivedWaits);
        verify(platform.failures).close();
    }

    @Test
    public void expiredListenerCannotBecomeSuccessEvenWhenCurrentStateIsStarted()
    {
        Platform platform = new Platform();
        when(platform.waiter.await(anyLong(), any(RestartJobFailureSignal.class))).thenReturn(false);
        JsonObject result = platform.run();
        assertFalse(result.get("success").getAsBoolean()); //$NON-NLS-1$
        assertTrue(result.get("mutationCommitted").getAsBoolean()); //$NON-NLS-1$
        assertTrue(result.get("error").getAsString().contains("Lifecycle restart did not complete")); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals(1, platform.cleans.get());
        assertEquals(0, platform.derivedWaits);
        verify(platform.waiter).cleanup();
    }

    @Test
    public void pendingOrUnavailableDerivedDataCannotBecomeSuccess()
    {
        for (DerivedDataCompletion completion : List.of(DerivedDataCompletion.PENDING,
            DerivedDataCompletion.UNOBSERVABLE))
        {
            Platform platform = new Platform();
            platform.completion = completion;
            JsonObject result = platform.run();
            assertFalse(result.toString(), result.get("success").getAsBoolean()); //$NON-NLS-1$
            assertTrue(result.get("mutationCommitted").getAsBoolean()); //$NON-NLS-1$
            assertEquals(1, platform.cleans.get());
            assertEquals(1, platform.derivedWaits);
            verify(platform.waiter).cleanup();
        }
    }

    @Test
    public void incompleteDependentPreservesStructuredDetailsThroughTheProductionWait()
    {
        for (DerivedDataCompletion completion : List.of(DerivedDataCompletion.PENDING,
            DerivedDataCompletion.UNOBSERVABLE))
        {
            Platform platform = new Platform();
            platform.includeSibling = true;
            platform.siblingCompletion = completion;
            JsonObject result = platform.run();
            assertFalse(result.toString(), result.get("success").getAsBoolean()); //$NON-NLS-1$
            assertTrue(result.get("mutationCommitted").getAsBoolean()); //$NON-NLS-1$
            assertEquals(1, result.get("projectsCleaned").getAsInt()); //$NON-NLS-1$
            assertEquals("Target", result.getAsJsonArray("projects").get(0).getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
            assertEquals(1, result.getAsJsonArray("dependentsStillBuilding").size()); //$NON-NLS-1$
            assertTrue(result.getAsJsonArray("dependentsStillBuilding").get(0).getAsString() //$NON-NLS-1$
                .startsWith("Sibling (Derived data")); //$NON-NLS-1$
            assertEquals(2, platform.derivedWaits);
        }
    }

    @Test
    public void bothCompletionBarriersPermitSuccess()
    {
        Platform platform = new Platform();
        JsonObject result = platform.run();
        assertTrue(result.toString(), result.get("success").getAsBoolean()); //$NON-NLS-1$
        assertEquals(1, platform.cleans.get());
        assertEquals(1, platform.derivedWaits);
        verify(platform.waiter).cleanup();
    }
}
