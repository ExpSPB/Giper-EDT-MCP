/**
 * MCP Server for EDT - Tests
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */
package fm.giper.edt.mcp.server.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.ILogListener;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.IEditorPart;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

/** Exercises the real reflective rebuild/refresh path without creating a workbench or SWT controls. */
public class FormEditorRefreshUtilsTest
{
    /** Same reflective rebuild callback pattern as EditorScreenshotHelperTest.FakeRepresentation. */
    public static final class FakeRepresentation
    {
        int rebuilds;
        Runnable onRebuild = () -> { };

        public void rebuild(boolean full)
        {
            assertTrue("the asynchronous editor refresh requests a full rebuild", full); //$NON-NLS-1$
            rebuilds++;
            onRebuild.run();
        }
    }

    public static final class FakeViewer
    {
        Object wysiwygRepresentation = new FakeRepresentation();
        final Control control = mock(Control.class);
        int refreshes;
        RuntimeException failure;

        public Control getControl()
        {
            return control;
        }

        public void refresh()
        {
            refreshes++;
            if (failure != null)
            {
                throw failure;
            }
        }
    }

    @Test
    public void testClosedViewerAfterRebuildIsNotRefreshed() throws Exception
    {
        FakeViewer viewer = new FakeViewer();
        FakeRepresentation representation = (FakeRepresentation)viewer.wysiwygRepresentation;
        representation.onRebuild = () -> viewer.wysiwygRepresentation = null;

        FormEditorRefreshUtils.refreshCurrentViewer(() -> viewer);

        assertEquals(1, representation.rebuilds);
        assertEquals(0, viewer.refreshes);
    }

    @Test
    public void testReplacementViewerIsNotRefreshedByAnOldRequest() throws Exception
    {
        FakeViewer old = new FakeViewer();
        FakeViewer replacement = new FakeViewer();
        AtomicReference<Object> current = new AtomicReference<>(old);
        FakeRepresentation representation = (FakeRepresentation)old.wysiwygRepresentation;
        representation.onRebuild = () -> current.set(replacement);

        FormEditorRefreshUtils.refreshCurrentViewer(current::get);

        assertEquals(1, representation.rebuilds);
        assertEquals(0, old.refreshes);
        assertEquals(0, replacement.refreshes);
    }

    @Test
    public void testReplacedRepresentationIsNotRefreshedByAnOldRequest() throws Exception
    {
        FakeViewer viewer = new FakeViewer();
        FakeRepresentation original = (FakeRepresentation)viewer.wysiwygRepresentation;
        FakeRepresentation replacement = new FakeRepresentation();
        original.onRebuild = () -> viewer.wysiwygRepresentation = replacement;

        FormEditorRefreshUtils.refreshCurrentViewer(() -> viewer);

        assertEquals(1, original.rebuilds);
        assertEquals(0, replacement.rebuilds);
        assertEquals(0, viewer.refreshes);
    }

    @Test
    public void testDisposedControlAfterRebuildIsNotRefreshed() throws Exception
    {
        FakeViewer viewer = new FakeViewer();
        FakeRepresentation representation = (FakeRepresentation)viewer.wysiwygRepresentation;
        representation.onRebuild = () -> when(viewer.control.isDisposed()).thenReturn(true);

        FormEditorRefreshUtils.refreshCurrentViewer(() -> viewer);

        assertEquals(1, representation.rebuilds);
        assertEquals(0, viewer.refreshes);
    }

    @Test
    public void testMissingRepresentationDoesNotRefreshAnUninitializedViewer() throws Exception
    {
        FakeViewer viewer = new FakeViewer();
        viewer.wysiwygRepresentation = null;

        FormEditorRefreshUtils.refreshCurrentViewer(() -> viewer);

        assertEquals(0, viewer.refreshes);
    }

    @Test
    public void testLiveViewerIsStillRefreshedExactlyOnce() throws Exception
    {
        FakeViewer viewer = new FakeViewer();
        FakeRepresentation representation = (FakeRepresentation)viewer.wysiwygRepresentation;

        FormEditorRefreshUtils.refreshCurrentViewer(() -> viewer);

        assertEquals(1, representation.rebuilds);
        assertEquals(1, viewer.refreshes);
    }

    @Test
    public void testLiveRefreshFailureStillReachesTheExistingBestEffortBoundary() throws Exception
    {
        FakeViewer viewer = failingViewer();
        FakeRepresentation representation = (FakeRepresentation)viewer.wysiwygRepresentation;

        FormEditorRefreshUtils.refreshCurrentViewer(() -> viewer);

        assertEquals(1, representation.rebuilds);
        assertEquals("the guard must not hide a live viewer's failing operation", 1, viewer.refreshes); //$NON-NLS-1$
    }

    @Test
    public void testLiveRefreshFailureRemainsVisibleInThePluginLog() throws Exception
    {
        Bundle bundle = FrameworkUtil.getBundle(Log.class);
        assertNotNull("the canonical log assertion requires its actual OSGi bundle", bundle); //$NON-NLS-1$
        ILog log = Platform.getLog(bundle);
        List<IStatus> recorded = new CopyOnWriteArrayList<>();
        ILogListener listener = (status, plugin) -> recorded.add(status);
        log.addLogListener(listener);
        try
        {
            FakeViewer viewer = failingViewer();
            FormEditorRefreshUtils.refreshCurrentViewer(() -> viewer);
            long warnings = recorded.stream().filter(status -> status.getSeverity() == IStatus.WARNING
                && status.getMessage().startsWith("Failed to refresh WYSIWYG viewer:")).count(); //$NON-NLS-1$
            assertEquals("a live refresh failure must remain visible", 1L, warnings); //$NON-NLS-1$
        }
        finally
        {
            log.removeLogListener(listener);
        }
    }

    @Test
    public void testEditorClosedDuringRebuildDoesNotResolveOrRefreshOldViewerAgain() throws Exception
    {
        IEditorPart expectedEditor = mock(IEditorPart.class);
        AtomicReference<IEditorPart> current = new AtomicReference<>(expectedEditor);
        AtomicInteger editorLookups = new AtomicInteger();
        AtomicInteger viewerResolutions = new AtomicInteger();
        FakeViewer viewer = new FakeViewer();
        FakeRepresentation representation = (FakeRepresentation)viewer.wysiwygRepresentation;
        representation.onRebuild = () -> current.set(null);

        FormEditorRefreshUtils.refreshCurrentEditor(expectedEditor, () -> {
            editorLookups.incrementAndGet();
            return current.get();
        }, () -> {
            viewerResolutions.incrementAndGet();
            return viewer;
        });

        assertEquals(2, editorLookups.get());
        assertEquals(1, viewerResolutions.get());
        assertEquals(1, representation.rebuilds);
        assertEquals(0, viewer.refreshes);
    }

    @Test
    public void testEditorReplacementDuringRebuildDoesNotResolveReplacementViewer() throws Exception
    {
        IEditorPart expectedEditor = mock(IEditorPart.class);
        IEditorPart replacementEditor = mock(IEditorPart.class);
        AtomicReference<IEditorPart> current = new AtomicReference<>(expectedEditor);
        AtomicInteger viewerResolutions = new AtomicInteger();
        FakeViewer oldViewer = new FakeViewer();
        FakeViewer replacementViewer = new FakeViewer();
        FakeRepresentation representation = (FakeRepresentation)oldViewer.wysiwygRepresentation;
        representation.onRebuild = () -> current.set(replacementEditor);

        FormEditorRefreshUtils.refreshCurrentEditor(expectedEditor, current::get, () -> {
            viewerResolutions.incrementAndGet();
            return current.get() == expectedEditor ? oldViewer : replacementViewer;
        });

        assertEquals(1, viewerResolutions.get());
        assertEquals(1, representation.rebuilds);
        assertEquals(0, oldViewer.refreshes);
        assertEquals(0, replacementViewer.refreshes);
    }

    @Test
    public void testAlreadyClosedEditorDoesNotResolveViewerOrRebuild() throws Exception
    {
        IEditorPart expectedEditor = mock(IEditorPart.class);
        AtomicInteger viewerResolutions = new AtomicInteger();
        FakeViewer viewer = new FakeViewer();
        FakeRepresentation representation = (FakeRepresentation)viewer.wysiwygRepresentation;

        FormEditorRefreshUtils.refreshCurrentEditor(expectedEditor, () -> null, () -> {
            viewerResolutions.incrementAndGet();
            return viewer;
        });

        assertEquals(0, viewerResolutions.get());
        assertEquals(0, representation.rebuilds);
        assertEquals(0, viewer.refreshes);
    }

    @Test
    public void testCurrentEditorIsStillRefreshedExactlyOnce() throws Exception
    {
        IEditorPart expectedEditor = mock(IEditorPart.class);
        FakeViewer viewer = new FakeViewer();
        FakeRepresentation representation = (FakeRepresentation)viewer.wysiwygRepresentation;

        FormEditorRefreshUtils.refreshCurrentEditor(expectedEditor, () -> expectedEditor, () -> viewer);

        assertEquals(1, representation.rebuilds);
        assertEquals(1, viewer.refreshes);
    }

    @Test
    public void testCurrentEditorResolverExceptionPropagates() throws Exception
    {
        IEditorPart expectedEditor = mock(IEditorPart.class);
        IllegalStateException failure = new IllegalStateException("deliberate editor lookup failure"); //$NON-NLS-1$

        try
        {
            FormEditorRefreshUtils.refreshCurrentEditor(expectedEditor, () -> {
                throw failure;
            }, () -> null);
        }
        catch (IllegalStateException actual)
        {
            assertSame(failure, actual);
            return;
        }
        throw new AssertionError("the editor lookup failure must propagate"); //$NON-NLS-1$
    }

    @Test
    public void testCurrentViewerResolverExceptionPropagates() throws Exception
    {
        IEditorPart expectedEditor = mock(IEditorPart.class);
        IllegalStateException failure = new IllegalStateException("deliberate viewer lookup failure"); //$NON-NLS-1$

        try
        {
            FormEditorRefreshUtils.refreshCurrentEditor(expectedEditor, () -> expectedEditor, () -> {
                throw failure;
            });
        }
        catch (IllegalStateException actual)
        {
            assertSame(failure, actual);
            return;
        }
        throw new AssertionError("the viewer lookup failure must propagate"); //$NON-NLS-1$
    }

    private static FakeViewer failingViewer()
    {
        FakeViewer viewer = new FakeViewer();
        viewer.failure = new IllegalStateException("deliberate live refresh failure"); //$NON-NLS-1$
        return viewer;
    }
}
