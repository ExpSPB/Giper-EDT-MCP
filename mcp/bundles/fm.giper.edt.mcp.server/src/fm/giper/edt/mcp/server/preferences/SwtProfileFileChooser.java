/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server.preferences;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Shell;

import fm.giper.edt.mcp.server.profiles.ProfileFileChooser;

/**
 * SWT {@link FileDialog} binding for profile export/import file pickers.
 */
public final class SwtProfileFileChooser implements ProfileFileChooser
{
    private final Shell shell;

    public SwtProfileFileChooser(Shell shell)
    {
        this.shell = shell;
    }

    @Override
    public Path chooseOpen()
    {
        FileDialog dialog = new FileDialog(shell, SWT.OPEN);
        dialog.setText(Messages.ToolsTab_ImportDialogTitle);
        dialog.setFilterNames(new String[]{Messages.ToolsTab_JsonFilter});
        dialog.setFilterExtensions(new String[]{"*.json"}); //$NON-NLS-1$
        dialog.setFilterPath(new File(System.getProperty("user.home")).getAbsolutePath()); //$NON-NLS-1$
        String chosenPath = dialog.open();
        return chosenPath == null ? null : Paths.get(chosenPath);
    }

    @Override
    public Path chooseSave(String suggestedName)
    {
        FileDialog dialog = new FileDialog(shell, SWT.SAVE);
        dialog.setText(Messages.ToolsTab_ExportDialogTitle);
        dialog.setFilterNames(new String[]{Messages.ToolsTab_JsonFilter});
        dialog.setFilterExtensions(new String[]{"*.json"}); //$NON-NLS-1$
        dialog.setFileName(suggestedName);
        dialog.setFilterPath(new File(System.getProperty("user.home")).getAbsolutePath()); //$NON-NLS-1$
        dialog.setOverwrite(true);
        String chosenPath = dialog.open();
        return chosenPath == null ? null : Paths.get(chosenPath);
    }
}
