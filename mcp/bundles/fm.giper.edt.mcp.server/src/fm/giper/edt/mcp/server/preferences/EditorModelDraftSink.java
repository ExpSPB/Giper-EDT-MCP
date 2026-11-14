/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server.preferences;

import java.util.Set;

import fm.giper.edt.mcp.server.profiles.ProfileDraftApplyResult;
import fm.giper.edt.mcp.server.profiles.ProfileDraftSink;
import fm.giper.edt.mcp.server.profiles.ToolProfile;

/**
 * Bridges {@link ToolProfilesEditorModel} to headless profile exchange orchestration.
 */
public final class EditorModelDraftSink implements ProfileDraftSink
{
    private final ToolProfilesEditorModel model;

    public EditorModelDraftSink(ToolProfilesEditorModel model)
    {
        this.model = model;
    }

    @Override
    public ToolProfile getSelected()
    {
        return model.getSelected();
    }

    @Override
    public ProfileDraftApplyResult applyImported(ToolProfile imported)
    {
        ToolProfilesEditorModel.OperationResult result = model.applyImportedProfileToSelected(imported);
        if (result.isOk())
        {
            return ProfileDraftApplyResult.ok();
        }
        return ProfileDraftApplyResult.failed(result.getMessage());
    }

    @Override
    public Set<String> unknownAllowedTools()
    {
        return model.unknownAllowedTools();
    }
}
