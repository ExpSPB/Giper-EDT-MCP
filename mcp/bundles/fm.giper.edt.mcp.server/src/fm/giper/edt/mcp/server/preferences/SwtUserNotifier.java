/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server.preferences;

import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.widgets.Shell;

import fm.giper.edt.mcp.server.Activator;
import fm.giper.edt.mcp.server.profiles.UserNotifier;

/**
 * SWT message dialogs for profile export/import feedback.
 */
public final class SwtUserNotifier implements UserNotifier
{
    private final Shell shell;

    public SwtUserNotifier(Shell shell)
    {
        this.shell = shell;
    }

    @Override
    public boolean confirmOverwrite(Path resolvedPath)
    {
        return MessageDialog.openQuestion(shell, Messages.ToolsTab_ExportOverwriteTitle,
            NLS.bind(Messages.ToolsTab_ExportOverwriteMessage, resolvedPath.toString()));
    }

    @Override
    public void exportFailed(String detail)
    {
        Activator.logError("Profile export failed: " + detail, null); //$NON-NLS-1$
        MessageDialog.openError(shell, Messages.ToolsTab_ExportFailedTitle,
            NLS.bind(Messages.ToolsTab_ExportFailedMessage, detail));
    }

    @Override
    public void importFailed(String detail)
    {
        Activator.logError("Profile import failed: " + detail, null); //$NON-NLS-1$
        MessageDialog.openError(shell, Messages.ToolsTab_ImportFailedTitle,
            NLS.bind(Messages.ToolsTab_ImportFailedMessage, detail));
    }

    @Override
    public void unknownTools(Set<String> names)
    {
        String joined = names.stream().sorted().collect(Collectors.joining(", ")); //$NON-NLS-1$
        MessageDialog.openInformation(shell, Messages.ToolsTab_UnknownToolsTitle,
            NLS.bind(Messages.ToolsTab_UnknownToolsMessage, joined));
    }
}
