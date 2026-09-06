/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server.ui;

import static org.junit.Assert.assertTrue;

import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.eclipse.junit4.SWTBotJunit4ClassRunner;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotButton;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotShell;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * SWTBot smoke test for Tools-tab profile exchange buttons (no native file dialogs).
 */
@RunWith(SWTBotJunit4ClassRunner.class)
public class ToolsPreferenceExchangeUiTest
{
    private static final String PREFERENCE_PAGE_NAME = "MCP Server"; //$NON-NLS-1$
    private static final String TOOLS_TAB = "Tools"; //$NON-NLS-1$
    private static final String EXPORT_BUTTON = "Export profile..."; //$NON-NLS-1$
    private static final String IMPORT_BUTTON = "Import profile..."; //$NON-NLS-1$

    private final SWTWorkbenchBot bot = new SWTWorkbenchBot();

    @Before
    public void setUp()
    {
        bot.closeWelcomePage();
    }

    @Test
    public void toolsTabShowsEnabledExportImportButtons()
    {
        openMcpServerToolsTab();
        assertExportImportButtonsEnabled();
        bot.button("Cancel").click(); //$NON-NLS-1$
    }

    @Test
    public void exportImportButtonsStillPresentInSmallerPreferencesShell()
    {
        SWTBotShell prefs = openMcpServerToolsTab();
        Rectangle bounds = prefs.getBounds();
        int width = Math.max(640, bounds.width / 2);
        int height = Math.max(480, bounds.height / 2);
        prefs.resize(width, height);
        assertExportImportButtonsEnabled();
        bot.button("Cancel").click(); //$NON-NLS-1$
    }

    private SWTBotShell openMcpServerToolsTab()
    {
        bot.menu("Window").menu("Preferences").click(); //$NON-NLS-1$ //$NON-NLS-2$
        SWTBotShell prefs = bot.shell("Preferences"); //$NON-NLS-1$
        bot.tree().getTreeItem(PREFERENCE_PAGE_NAME).select();
        bot.tabItem(TOOLS_TAB).select();
        return prefs;
    }

    private void assertExportImportButtonsEnabled()
    {
        SWTBotButton export = bot.button(EXPORT_BUTTON);
        SWTBotButton importBtn = bot.button(IMPORT_BUTTON);
        assertTrue("Export profile button must be enabled", export.isEnabled()); //$NON-NLS-1$
        assertTrue("Import profile button must be enabled", importBtn.isEnabled()); //$NON-NLS-1$
    }
}
