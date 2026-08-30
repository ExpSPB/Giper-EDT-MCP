/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server.preferences;

import java.io.IOException;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import fm.giper.edt.mcp.server.Activator;
import fm.giper.edt.mcp.server.McpServer;
import fm.giper.edt.mcp.server.profiles.ReplaceResult;
import fm.giper.edt.mcp.server.protocol.McpConstants;

/**
 * MCP Server preference page with tabbed layout.
 * Tab 1: General - port, auto-start, checks folder, plain text, tags, updates, server control
 * Tab 2: Tools - tree of tool groups with enable/disable, description, and parameter settings
 * Tab 3: History - request/response history recorder + file-log settings
 * Tab 4: Privacy - PII redaction master toggle, pseudonym salt, and the detection rule table
 */
public class McpServerPreferencePage extends PreferencePage implements IWorkbenchPreferencePage
{
    private GeneralTab generalTab;
    private ToolsTab toolsTab;
    private HistoryTab historyTab;
    private PrivacyTab privacyTab;

    public McpServerPreferencePage()
    {
        setPreferenceStore(Activator.getDefault().getPreferenceStore());
        setDescription(NLS.bind(Messages.McpServerPreferencePage_Description,
            McpConstants.PLUGIN_VERSION, McpConstants.AUTHOR));
    }

    @Override
    public void init(IWorkbench workbench)
    {
        // Initialization
    }

    @Override
    protected Control createContents(Composite parent)
    {
        Composite container = new Composite(parent, SWT.NONE);
        GridLayout containerLayout = new GridLayout(1, false);
        containerLayout.marginWidth = 0;
        containerLayout.marginHeight = 0;
        container.setLayout(containerLayout);
        container.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        CTabFolder tabFolder = new CTabFolder(container, SWT.BORDER);
        tabFolder.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        tabFolder.setSimple(true);

        // Tab 1: General
        CTabItem generalItem = new CTabItem(tabFolder, SWT.NONE);
        generalItem.setText(Messages.McpServerPreferencePage_TabGeneral);
        generalTab = new GeneralTab(tabFolder);
        generalItem.setControl(generalTab.getControl());

        // Tab 2: Tools
        CTabItem toolsItem = new CTabItem(tabFolder, SWT.NONE);
        toolsItem.setText(Messages.McpServerPreferencePage_TabTools);
        toolsTab = new ToolsTab(tabFolder);
        // Link so the per-tool destructive-consent checkbox reflects the PENDING level chosen on the
        // General tab (before Apply), not just the persisted value.
        toolsTab.setGeneralTab(generalTab);
        toolsItem.setControl(toolsTab.getControl());

        // Tab 3: History
        CTabItem historyItem = new CTabItem(tabFolder, SWT.NONE);
        historyItem.setText(Messages.McpServerPreferencePage_TabHistory);
        historyTab = new HistoryTab(tabFolder);
        historyItem.setControl(historyTab.getControl());

        // Tab 4: Privacy
        CTabItem privacyItem = new CTabItem(tabFolder, SWT.NONE);
        privacyItem.setText(Messages.McpServerPreferencePage_TabPrivacy);
        privacyTab = new PrivacyTab(tabFolder);
        privacyItem.setControl(privacyTab.getControl());

        // Select the first tab
        tabFolder.setSelection(0);

        return container;
    }

    @Override
    public boolean performOk()
    {
        IPreferenceStore store = getPreferenceStore();
        int previousPort = store.getInt(PreferenceConstants.PREF_PORT);
        boolean previousRemote = store.getBoolean(PreferenceConstants.PREF_ALLOW_REMOTE_ACCESS);
        String previousToken = store.getString(PreferenceConstants.PREF_AUTH_TOKEN);

        ReplaceResult profiles = toolsTab.saveProfiles();
        if (profiles.getStatus() == ReplaceResult.Status.CONFLICT)
        {
            boolean reload = MessageDialog.openQuestion(getShell(),
                Messages.ToolsTab_ConflictTitle, Messages.ToolsTab_ConflictMessage);
            if (reload)
            {
                toolsTab.reloadProfiles();
            }
            toolsTab.performOk();
            generalTab.performOk();
            historyTab.performOk();
            privacyTab.performOk();
            return false;
        }
        if (profiles.getStatus() == ReplaceResult.Status.REJECTED)
        {
            MessageDialog.openError(getShell(), Messages.ToolsTab_Profile, profiles.getMessage());
            toolsTab.performOk();
            generalTab.performOk();
            historyTab.performOk();
            privacyTab.performOk();
            return false;
        }

        toolsTab.performOk();
        generalTab.performOk();
        historyTab.performOk();
        privacyTab.performOk();

        boolean transportChanged = previousPort != store.getInt(PreferenceConstants.PREF_PORT)
            || previousRemote != store.getBoolean(PreferenceConstants.PREF_ALLOW_REMOTE_ACCESS)
            || !previousToken.equals(store.getString(PreferenceConstants.PREF_AUTH_TOKEN));
        if (transportChanged)
        {
            McpServer server = Activator.getDefault().getMcpServer();
            if (server != null && server.isRunning())
            {
                try
                {
                    server.restart(generalTab.getPort());
                    Activator.logInfo("MCP Server restarted after transport setting change"); //$NON-NLS-1$
                }
                catch (IOException e)
                {
                    Activator.logError("Failed to restart MCP Server after transport change", e); //$NON-NLS-1$
                }
            }
        }

        return super.performOk();
    }

    @Override
    protected void performDefaults()
    {
        generalTab.performDefaults();
        toolsTab.performDefaults();
        historyTab.performDefaults();
        privacyTab.performDefaults();
        super.performDefaults();
    }

    @Override
    public void dispose()
    {
        if (generalTab != null)
        {
            generalTab.dispose();
        }
        if (toolsTab != null)
        {
            toolsTab.dispose();
        }
        if (historyTab != null)
        {
            historyTab.dispose();
        }
        if (privacyTab != null)
        {
            privacyTab.dispose();
        }
        super.dispose();
    }
}
