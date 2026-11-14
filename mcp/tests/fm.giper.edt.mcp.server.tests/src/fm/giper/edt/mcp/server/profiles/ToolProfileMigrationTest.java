/*
 * Licensed under AGPL-3.0-or-later
 */
package fm.giper.edt.mcp.server.profiles;

import static org.junit.Assert.assertEquals;

import java.util.Set;

import org.eclipse.jface.preference.PreferenceStore;
import org.junit.Test;

import fm.giper.edt.mcp.server.preferences.PreferenceConstants;
import fm.giper.edt.mcp.server.preferences.ToolPreset;

public class ToolProfileMigrationTest
{
    @Test
    public void migratesLegacyDefaultsAndKeepsNewToolsOutOfSavedProfiles()
    {
        PreferenceStore store = new PreferenceStore();
        PreferenceToolProfileRepository repository = new PreferenceToolProfileRepository(store);
        ReplaceResult first = ToolProfileMigration.migrateIfNeeded(repository,
            Set.of("get_server_status", "list_projects", "git", "ask_workmate", "get_form_screenshot"), store);
        assertEquals(ReplaceResult.Status.ACCEPTED, first.getStatus());
        assertEquals(Set.of("get_server_status", "list_projects"),
            repository.getSnapshot().getDefault().getAllowedTools());

        PreferenceToolProfileRepository reloaded = new PreferenceToolProfileRepository(store);
        ReplaceResult again = ToolProfileMigration.migrateIfNeeded(reloaded,
            Set.of("get_server_status", "list_projects", "create_metadata"), store);
        assertEquals(ReplaceResult.Status.NO_OP, again.getStatus());
        assertEquals(first.getSnapshot(), reloaded.getSnapshot());
    }

    @Test
    public void runsLegacyMigrationsBeforeCopyingPresetAndNormalizesDisabledAliases()
    {
        PreferenceStore store = new PreferenceStore();
        store.setValue(PreferenceConstants.PREF_DISABLED_TOOLS, "debug_launch");
        store.setValue(PreferenceConstants.PREF_TOOL_PREFS_MIGRATION, 15);
        store.setValue(PreferenceConstants.PREF_TOOL_PRESET_ID, ToolPreset.CODE_REVIEW.getId());
        PreferenceToolProfileRepository repository = new PreferenceToolProfileRepository(store);

        ToolProfileMigration.migrateIfNeeded(repository,
            Set.of("launch", "list_projects", "apply_quick_fix", "edt_dialog", "create_metadata"), store);

        assertEquals(Set.of("list_projects", "apply_quick_fix", "create_metadata"),
            repository.getSnapshot().getDefault().getAllowedTools());
        assertEquals(ToolPreset.CODE_REVIEW.getId(), repository.getSnapshot().getDefault().getPresetId());
        assertEquals(PreferenceConstants.TOOL_PREFS_MIGRATION_VERSION,
            store.getInt(PreferenceConstants.PREF_TOOL_PREFS_MIGRATION));
    }

    @Test
    public void refusesToMigrateOverAnUnreadableDocument()
    {
        for (String json : Set.of("{broken", "{\"schemaVersion\":2}"))
        {
            PreferenceStore store = new PreferenceStore();
            store.setValue(PreferenceConstants.PREF_TOOL_PROFILES_JSON, json);
            PreferenceToolProfileRepository repository = new PreferenceToolProfileRepository(store);
            ReplaceResult result = ToolProfileMigration.migrateIfNeeded(repository, Set.of("list_projects"), store);
            assertEquals(ReplaceResult.Status.REJECTED, result.getStatus());
            assertEquals(json, store.getString(PreferenceConstants.PREF_TOOL_PROFILES_JSON));
        }
    }

    @Test
    public void refusesToMigrateBeforeCatalogueRegistration()
    {
        PreferenceStore store = new PreferenceStore();
        PreferenceToolProfileRepository repository = new PreferenceToolProfileRepository(store);

        assertEquals(ReplaceResult.Status.REJECTED,
            ToolProfileMigration.migrateIfNeeded(repository, Set.of(), store).getStatus());
        assertEquals(ProfileDocumentState.ABSENT, repository.getDocumentState());
    }
}
