/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server.preferences;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.widgets.Shell;

import fm.giper.edt.mcp.server.profiles.ImportConfirmation;
import fm.giper.edt.mcp.server.profiles.ImportPreview;
import fm.giper.edt.mcp.server.profiles.ToolProfile;

/**
 * SWT confirmation dialog before overlaying an imported profile onto the selected draft.
 */
public final class SwtImportConfirmation implements ImportConfirmation
{
    private final Shell shell;

    public SwtImportConfirmation(Shell shell)
    {
        this.shell = shell;
    }

    @Override
    public boolean confirm(ImportPreview preview)
    {
        ToolProfile current = preview.getCurrent();
        ToolProfile imported = preview.getImported();
        String message = NLS.bind(Messages.ToolsTab_ImportConfirm, new Object[]{
            current.getId(),
            current.getDisplayName(),
            imported.getDisplayName(),
            enabledStateLabel(current.isEnabled()),
            enabledStateLabel(imported.isEnabled()),
            Integer.toString(current.getAllowedTools().size()),
            Integer.toString(imported.getAllowedTools().size())
        });
        if (preview.fileIdDiffers())
        {
            message += System.lineSeparator() + System.lineSeparator()
                + NLS.bind(Messages.ToolsTab_ImportConfirmFileId, imported.getId());
        }
        return MessageDialog.openQuestion(shell, Messages.ToolsTab_ImportConfirmTitle, message);
    }

    private static String enabledStateLabel(boolean enabled)
    {
        return enabled ? Messages.ToolsTab_Enable : Messages.ToolsTab_Disable;
    }
}
