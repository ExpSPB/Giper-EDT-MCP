/*
 * Copyright (C) 2026 ExpSPB
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

public class UpdateCheckerTest
{
    @Test
    public void localBuildSkipsManualAndScheduledChecks()
    {
        UpdateChecker checker = new UpdateChecker(false, () -> {
            throw new AssertionError("Local builds must not fetch releases"); //$NON-NLS-1$
        });

        checker.scheduleCheck();
        checker.checkNow();

        assertFalse(checker.isEnabled());
        assertFalse(checker.isUpdateAvailable());
        assertEquals("", checker.getLatestVersion()); //$NON-NLS-1$
    }

    @Test
    public void releaseBuildReportsNewerForkRelease()
    {
        AtomicInteger fetches = new AtomicInteger();
        String releaseUrl = "https://github.com/ExpSPB/Giper-EDT-MCP/releases/tag/v999.0.0"; //$NON-NLS-1$
        UpdateChecker checker = new UpdateChecker(true, () -> {
            fetches.incrementAndGet();
            return new String[] { "v999.0.0", "Release notes", releaseUrl }; //$NON-NLS-1$ //$NON-NLS-2$
        });
        assertEquals("https://github.com/ExpSPB/Giper-EDT-MCP/releases/latest", checker.getReleaseUrl()); //$NON-NLS-1$

        checker.checkNow();

        assertEquals(1, fetches.get());
        assertTrue(checker.isEnabled());
        assertTrue(checker.isUpdateAvailable());
        assertEquals("999.0.0", checker.getLatestVersion()); //$NON-NLS-1$
        assertEquals("Release notes", checker.getReleaseNotes()); //$NON-NLS-1$
        assertEquals(releaseUrl, checker.getReleaseUrl());
        assertEquals("https://api.github.com/repos/ExpSPB/Giper-EDT-MCP/releases/latest", //$NON-NLS-1$
            UpdateChecker.RELEASES_API_URL);
    }
}
