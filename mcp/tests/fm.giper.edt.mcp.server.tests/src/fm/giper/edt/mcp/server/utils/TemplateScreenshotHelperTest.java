/**
 * MCP Server for EDT - Tests
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */
package fm.giper.edt.mcp.server.utils;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.eclipse.swt.widgets.Shell;
import org.junit.Test;
import org.mockito.InOrder;

import com._1c.g5.v8.dt.moxel.ui.editor.MoxelControl;

/** Tests standalone SDK listener cleanup; no native UI or BM transaction is constructed. */
public class TemplateScreenshotHelperTest
{
    @Test
    public void childCleanupPrecedesParentDisposal()
    {
        MoxelControl control = mock(MoxelControl.class);
        Shell shell = mock(Shell.class);
        TemplateScreenshotHelper.disposeRenderControls(control, shell);
        InOrder order = inOrder(control, shell);
        order.verify(control).dispose();
        order.verify(shell).dispose();
        order.verifyNoMoreInteractions();
    }

    @Test
    public void alreadyDisposedWidgetStillGetsSdkListenerCleanup()
    {
        MoxelControl control = mock(MoxelControl.class);
        Shell shell = mock(Shell.class);
        when(control.isDisposed()).thenReturn(true);
        TemplateScreenshotHelper.disposeRenderControls(control, shell);
        verify(control).dispose();
        verify(shell).dispose();
    }

    @Test
    public void parentIsDisposedWhenControlConstructionDidNotComplete()
    {
        Shell shell = mock(Shell.class);
        TemplateScreenshotHelper.disposeRenderControls(null, shell);
        verify(shell).dispose();
    }

    @Test
    public void parentIsDisposedAfterSdkCleanupException()
    {
        MoxelControl control = mock(MoxelControl.class);
        Shell shell = mock(Shell.class);
        RuntimeException failure = new IllegalStateException("listener removal failed"); //$NON-NLS-1$
        doThrow(failure).when(control).dispose();
        try
        {
            TemplateScreenshotHelper.disposeRenderControls(control, shell);
            fail("SDK cleanup failure must remain visible"); //$NON-NLS-1$
        }
        catch (RuntimeException actual)
        {
            assertSame(failure, actual);
        }
        verify(shell).dispose();
    }

    @Test
    public void parentIsDisposedAfterSdkCleanupError()
    {
        MoxelControl control = mock(MoxelControl.class);
        Shell shell = mock(Shell.class);
        AssertionError failure = new AssertionError("listener removal failed"); //$NON-NLS-1$
        doThrow(failure).when(control).dispose();
        try
        {
            TemplateScreenshotHelper.disposeRenderControls(control, shell);
            fail("SDK cleanup error must remain visible"); //$NON-NLS-1$
        }
        catch (AssertionError actual)
        {
            assertSame(failure, actual);
        }
        verify(shell).dispose();
    }

    @Test
    public void parentDisposalFailureRemainsVisible()
    {
        MoxelControl control = mock(MoxelControl.class);
        Shell shell = mock(Shell.class);
        RuntimeException failure = new IllegalStateException("parent disposal failed"); //$NON-NLS-1$
        doThrow(failure).when(shell).dispose();
        try
        {
            TemplateScreenshotHelper.disposeRenderControls(control, shell);
            fail("parent disposal failure must remain visible"); //$NON-NLS-1$
        }
        catch (RuntimeException actual)
        {
            assertSame(failure, actual);
        }
        verify(control).dispose();
    }
}
