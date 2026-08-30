/**
 * MCP Server for EDT - Tests
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import fm.giper.edt.mcp.server.protocol.McpConstants;

/**
 * Headless coverage of the temporary "no update" stub (no GitHub).
 */
public class UpdateCheckerTest
{
    @Test
    public void checkNowLeavesNoUpdateAndDoesNotAdvertiseRemoteLatest()
    {
        assertTrue(UpdateChecker.SUPPRESS_UPDATE_CHECKS);

        UpdateChecker checker = UpdateChecker.getInstance();
        checker.checkNow();

        assertFalse(checker.isUpdateAvailable());
        assertEquals(McpConstants.PLUGIN_VERSION, checker.getLatestVersion());
        assertFalse(checker.getLatestVersion().startsWith("2.")); //$NON-NLS-1$
    }
}
