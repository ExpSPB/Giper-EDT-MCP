/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server.profiles;

import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.jface.preference.IPreferenceStore;

import fm.giper.edt.mcp.server.preferences.ToolSettingsService;
import fm.giper.edt.mcp.server.preferences.ToolSettingsService.StoredSelection;
import fm.giper.edt.mcp.server.tools.McpToolRegistry;

/** Creates the initial default profile from workspace settings and the registered catalogue. */
public final class ToolProfileMigration
{
    private ToolProfileMigration()
    {
        // Utility class
    }

    /** Run after catalogue registration, when profile-based enablement is activated. */
    public static ReplaceResult migrateIfNeeded(ToolProfileRepository repository,
        Collection<String> registeredToolNames, IPreferenceStore store)
    {
        ProfileDocumentState state = repository.getDocumentState();
        if (state == ProfileDocumentState.VALID)
        {
            return ReplaceResult.noOp(repository.getSnapshot());
        }
        if (state != ProfileDocumentState.ABSENT)
        {
            return ReplaceResult.rejected(repository.getSnapshot(),
                "Refusing to migrate over a malformed or unsupported profile document"); //$NON-NLS-1$
        }
        if (registeredToolNames.isEmpty())
        {
            return ReplaceResult.rejected(repository.getSnapshot(),
                "Migration cannot run before the tool catalogue is registered"); //$NON-NLS-1$
        }
        StoredSelection selection = ToolSettingsService.getInstance().readStoredSelection(store);
        Set<String> allowed = new TreeSet<>(registeredToolNames);
        for (String disabled : selection.disabledTools())
        {
            allowed.remove(disabled);
            allowed.remove(McpToolRegistry.canonicalToolName(disabled));
        }
        ToolProfile profile = DefaultToolProfileFactory.createDefault(allowed).toBuilder()
            .presetId(selection.presetId()).build();
        return repository.initializeIfAbsent(ToolProfileSnapshot.of(0L, Set.of(profile)));
    }
}
