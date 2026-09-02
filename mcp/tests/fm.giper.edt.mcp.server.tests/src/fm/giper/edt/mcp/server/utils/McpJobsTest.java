/**
 * MCP Server for EDT - Tests
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.ui.progress.IProgressConstants;
import org.junit.Test;

/**
 * Tests for {@link McpJobs}: a job this server schedules on an MCP caller's behalf must never
 * raise Eclipse's modal "job failed" dialog in the workbench of the human sitting in front of EDT.
 */
public class McpJobsTest
{
    private static Job failingJob()
    {
        return new Job("test") //$NON-NLS-1$
        {
            @Override
            protected IStatus run(IProgressMonitor monitor)
            {
                return new Status(IStatus.ERROR, "fm.giper.edt.mcp.server", "boom");
            }
        };
    }

    @Test
    public void testMarkedJobReportsItsErrorInTheProgressViewInsteadOfADialog()
    {
        Job job = failingJob();
        assertEquals(Boolean.TRUE,
            McpJobs.markNoErrorDialog(job).getProperty(
                IProgressConstants.NO_IMMEDIATE_ERROR_PROMPT_PROPERTY));
    }

    @Test
    public void testMarkReturnsTheSameJobSoItCanBeChained()
    {
        Job job = failingJob();
        assertSame(job, McpJobs.markNoErrorDialog(job));
    }

    @Test
    public void testAnUnmarkedJobIsTheOneEclipseWouldPromptFor()
    {
        // Pins WHY the helper exists: without the property Eclipse's progress manager hands an
        // ERROR result to the status manager with SHOW, which opens the modal error dialog.
        assertEquals(null,
            failingJob().getProperty(IProgressConstants.NO_IMMEDIATE_ERROR_PROMPT_PROPERTY));
    }
}
