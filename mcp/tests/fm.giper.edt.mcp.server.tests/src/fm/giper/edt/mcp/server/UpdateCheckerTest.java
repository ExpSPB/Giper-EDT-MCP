/**
 * MCP Server for EDT - Tests
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Проверка URL форка и сравнения версий без обращения к GitHub.
 */
public class UpdateCheckerTest
{
    @Test
    public void releaseEndpointsPointAtThisFork()
    {
        assertTrue(UpdateChecker.RELEASES_API_URL.contains("ExpSPB/Giper-EDT-MCP")); //$NON-NLS-1$
        assertTrue(UpdateChecker.RELEASES_PAGE_URL.contains("ExpSPB/Giper-EDT-MCP")); //$NON-NLS-1$
        assertFalse(UpdateChecker.RELEASES_API_URL.contains("DitriXNew/EDT-MCP")); //$NON-NLS-1$
        assertFalse(UpdateChecker.RELEASES_PAGE_URL.contains("DitriXNew/EDT-MCP")); //$NON-NLS-1$
    }

    @Test
    public void isNewerComparesSemanticTriple()
    {
        assertTrue(UpdateChecker.isNewer("1.0.6", "1.0.5")); //$NON-NLS-1$ //$NON-NLS-2$
        assertFalse(UpdateChecker.isNewer("1.0.5", "1.0.5")); //$NON-NLS-1$ //$NON-NLS-2$
        assertFalse(UpdateChecker.isNewer("1.0.5", "1.0.6")); //$NON-NLS-1$ //$NON-NLS-2$
        assertTrue(UpdateChecker.isNewer("1.0.7", "1.0.6")); //$NON-NLS-1$ //$NON-NLS-2$
        assertTrue(UpdateChecker.isNewer("2.0.0", "1.0.6")); //$NON-NLS-1$ //$NON-NLS-2$
    }
}
