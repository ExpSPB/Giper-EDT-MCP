/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server.profiles;

import java.nio.file.Path;
import java.util.Set;

/**
 * Headless export/import orchestration. File dialogs and MessageDialog stay
 * behind {@link ProfileFileChooser}, {@link ImportConfirmation}, {@link UserNotifier}.
 */
public final class ToolProfileExchangeController
{
    public enum Outcome
    {
        COMPLETED,
        CANCELLED,
        FAILED
    }

    public Outcome exportSelected(ProfileDraftSink sink, ProfileFileChooser chooser, UserNotifier notifier)
    {
        ToolProfile selected = sink.getSelected();
        if (selected == null)
        {
            return Outcome.CANCELLED;
        }
        Path chosen = chooser.chooseSave("edt-mcp-profile-" + selected.getId() + ".json"); //$NON-NLS-1$ //$NON-NLS-2$
        if (chosen == null)
        {
            return Outcome.CANCELLED;
        }
        String chosenPath = chosen.toString();
        Path exportPath = ToolProfileExportPaths.resolve(chosenPath);
        if (ToolProfileExportPaths.needsOverwriteConfirm(chosenPath, exportPath)
            && !notifier.confirmOverwrite(exportPath))
        {
            return Outcome.CANCELLED;
        }
        FileOpResult result = ToolProfileFileService.exportProfile(exportPath, selected);
        if (!result.isOk())
        {
            notifier.exportFailed(detail(result));
            return Outcome.FAILED;
        }
        return Outcome.COMPLETED;
    }

    public Outcome importSelected(ProfileDraftSink sink, ProfileFileChooser chooser,
        ImportConfirmation confirmation, UserNotifier notifier)
    {
        ToolProfile current = sink.getSelected();
        if (current == null)
        {
            return Outcome.CANCELLED;
        }
        Path chosen = chooser.chooseOpen();
        if (chosen == null)
        {
            return Outcome.CANCELLED;
        }
        FileOpResult fileResult = ToolProfileFileService.importProfile(chosen);
        if (!fileResult.isOk())
        {
            notifier.importFailed(detail(fileResult));
            return Outcome.FAILED;
        }
        ToolProfile imported = fileResult.getProfile();
        if (!confirmation.confirm(ImportPreview.of(current, imported)))
        {
            return Outcome.CANCELLED;
        }
        ProfileDraftApplyResult applyResult = sink.applyImported(imported);
        if (!applyResult.isOk())
        {
            notifier.importFailed(applyResult.getMessage() != null ? applyResult.getMessage() : "apply failed"); //$NON-NLS-1$
            return Outcome.FAILED;
        }
        Set<String> unknown = sink.unknownAllowedTools();
        if (!unknown.isEmpty())
        {
            notifier.unknownTools(unknown);
        }
        return Outcome.COMPLETED;
    }

    private static String detail(FileOpResult result)
    {
        return result.getMessage() != null ? result.getMessage() : result.getStatus().name();
    }
}
