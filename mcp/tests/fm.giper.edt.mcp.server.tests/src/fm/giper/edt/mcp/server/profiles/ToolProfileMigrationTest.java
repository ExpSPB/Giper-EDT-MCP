/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server.profiles;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.jface.preference.PreferenceStore;
import org.junit.After;
import org.junit.Test;

import fm.giper.edt.mcp.server.preferences.PreferenceConstants;
import fm.giper.edt.mcp.server.preferences.ToolPreset;
import fm.giper.edt.mcp.server.preferences.ToolSettingsService;

/**
 * One-shot migration from the legacy disabled-set, including the downgrade mirror.
 */
public class ToolProfileMigrationTest
{
    @After
    public void tearDown()
    {
        ToolSettingsService.setRepositoryOverrideForTest(null);
        ToolSettingsService.setStoreOverrideForTest(null);
    }

    @Test
    public void migratesExactLegacySurfaceAndIsIdempotent()
    {
        PreferenceStore store = new PreferenceStore();
        store.setValue(PreferenceConstants.PREF_DISABLED_TOOLS, "git,ask_workmate,write_module_source"); //$NON-NLS-1$
        store.setValue(PreferenceConstants.PREF_TOOL_PREFS_MIGRATION,
            PreferenceConstants.TOOL_PREFS_MIGRATION_VERSION);
        PreferenceToolProfileRepository repo = new PreferenceToolProfileRepository(store);
        ToolSettingsService.setRepositoryOverrideForTest(repo);
        ToolSettingsService.setStoreOverrideForTest(store);

        Set<String> catalog = Set.of("git", "ask_workmate", "write_module_source", "list_projects", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            "get_server_status"); //$NON-NLS-1$
        ReplaceResult first = ToolProfileMigration.migrateIfNeeded(repo, catalog,
            ToolSettingsService.getInstance());
        assertEquals(ReplaceResult.Status.ACCEPTED, first.getStatus());
        assertEquals(Set.of("get_server_status", "list_projects"), //$NON-NLS-1$ //$NON-NLS-2$
            repo.getSnapshot().getDefault().getAllowedTools());

        ReplaceResult second = ToolProfileMigration.migrateIfNeeded(repo, catalog,
            ToolSettingsService.getInstance());
        assertEquals(ReplaceResult.Status.NO_OP, second.getStatus());
        assertEquals(first.getSnapshot().getDocumentRevision(), repo.getSnapshot().getDocumentRevision());
    }

    @Test
    public void refusesToMigrateOverAMalformedDocument()
    {
        PreferenceStore store = new PreferenceStore();
        store.setValue(PreferenceConstants.PREF_TOOL_PROFILES_JSON, "{broken"); //$NON-NLS-1$
        PreferenceToolProfileRepository repo = new PreferenceToolProfileRepository(store);
        assertEquals(ProfileDocumentState.MALFORMED, repo.getDocumentState());

        ReplaceResult result = ToolProfileMigration.migrateIfNeeded(repo,
            Set.of("list_projects", "get_server_status"), ToolSettingsService.getInstance()); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals(ReplaceResult.Status.REJECTED, result.getStatus());
        assertEquals(ProfileDocumentState.MALFORMED, repo.getDocumentState());
        assertEquals("{broken", store.getString(PreferenceConstants.PREF_TOOL_PROFILES_JSON)); //$NON-NLS-1$
    }

    @Test
    public void refusesToRunBeforeCatalogueIsRegistered()
    {
        PreferenceStore store = new PreferenceStore();
        PreferenceToolProfileRepository repo = new PreferenceToolProfileRepository(store);

        ReplaceResult result = ToolProfileMigration.migrateIfNeeded(repo, Set.of(),
            ToolSettingsService.getInstance());
        assertEquals(ReplaceResult.Status.REJECTED, result.getStatus());
        assertEquals(ProfileDocumentState.ABSENT, repo.getDocumentState());
    }

    @Test
    public void newRegisteredToolDoesNotJoinExistingAllowlist()
    {
        PreferenceStore store = new PreferenceStore();
        store.setValue(PreferenceConstants.PREF_DISABLED_TOOLS, "git"); //$NON-NLS-1$
        store.setValue(PreferenceConstants.PREF_TOOL_PREFS_MIGRATION,
            PreferenceConstants.TOOL_PREFS_MIGRATION_VERSION);
        PreferenceToolProfileRepository repo = new PreferenceToolProfileRepository(store);
        ToolSettingsService.setRepositoryOverrideForTest(repo);
        ToolSettingsService.setStoreOverrideForTest(store);

        ToolProfileMigration.migrateIfNeeded(repo, Set.of("git", "list_projects"), //$NON-NLS-1$ //$NON-NLS-2$
            ToolSettingsService.getInstance());
        Set<String> releaseTools = Set.of("infobase_sessions", "debug_pause", //$NON-NLS-1$ //$NON-NLS-2$
            "import_project_from_file", "export_configuration_to_file"); //$NON-NLS-1$ //$NON-NLS-2$
        Set<String> updatedCatalog = new HashSet<>(releaseTools);
        updatedCatalog.addAll(Set.of("git", "list_projects")); //$NON-NLS-1$ //$NON-NLS-2$
        long documentRevision = repo.getSnapshot().getDocumentRevision();
        ReplaceResult again = ToolProfileMigration.migrateIfNeeded(repo,
            updatedCatalog, ToolSettingsService.getInstance());
        assertEquals(ReplaceResult.Status.NO_OP, again.getStatus());
        assertEquals(documentRevision, repo.getSnapshot().getDocumentRevision());
        assertEquals(Set.of("list_projects"), repo.getSnapshot().getDefault().getAllowedTools()); //$NON-NLS-1$
        assertTrue(Collections.disjoint(releaseTools,
            repo.getSnapshot().getDefault().getAllowedTools()));
        assertTrue("the default mirror must also see every new tool as disabled", //$NON-NLS-1$
            ToolSettingsService.getInstance().getDisabledTools().containsAll(releaseTools));
    }

    @Test
    public void giperVersion6ReadOnlyLegacyStoreExcludesNewReleaseToolsBeforeAllowlistMigration()
    {
        Set<String> releaseTools = Set.of("infobase_sessions", "debug_pause", //$NON-NLS-1$ //$NON-NLS-2$
            "import_project_from_file", "export_configuration_to_file"); //$NON-NLS-1$ //$NON-NLS-2$
        Set<String> historical = new HashSet<>(ToolPreset.CODE_REVIEW.getDisabledTools());
        historical.removeAll(releaseTools);
        historical.remove("set_error_breakpoint"); //$NON-NLS-1$
        PreferenceStore store = new PreferenceStore();
        store.setValue(PreferenceConstants.PREF_DISABLED_TOOLS, String.join(",", historical)); //$NON-NLS-1$
        store.setValue(PreferenceConstants.PREF_TOOL_PREFS_MIGRATION, 6);
        PreferenceToolProfileRepository repo = new PreferenceToolProfileRepository(store);
        ToolSettingsService.setRepositoryOverrideForTest(repo);
        ToolSettingsService.setStoreOverrideForTest(store);
        Set<String> catalog = new HashSet<>(releaseTools);
        catalog.addAll(Set.of("list_projects", "get_server_status", "set_error_breakpoint")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

        ReplaceResult result = ToolProfileMigration.migrateIfNeeded(repo, catalog,
            ToolSettingsService.getInstance());

        assertEquals(ReplaceResult.Status.ACCEPTED, result.getStatus());
        assertEquals(Set.of("list_projects", "get_server_status"), //$NON-NLS-1$ //$NON-NLS-2$
            repo.getSnapshot().getDefault().getAllowedTools());
        assertEquals(PreferenceConstants.TOOL_PREFS_MIGRATION_VERSION,
            store.getInt(PreferenceConstants.PREF_TOOL_PREFS_MIGRATION));
    }

    @Test
    public void previouslyEnabledToolIsNotReDisabled()
    {
        PreferenceStore store = new PreferenceStore();
        store.setValue(PreferenceConstants.PREF_DISABLED_TOOLS, "ask_workmate"); //$NON-NLS-1$
        store.setValue(PreferenceConstants.PREF_TOOL_PREFS_MIGRATION,
            PreferenceConstants.TOOL_PREFS_MIGRATION_VERSION);
        PreferenceToolProfileRepository repo = new PreferenceToolProfileRepository(store);
        ToolSettingsService.setRepositoryOverrideForTest(repo);
        ToolSettingsService.setStoreOverrideForTest(store);

        ToolProfileMigration.migrateIfNeeded(repo, Set.of("git", "ask_workmate", "list_projects"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            ToolSettingsService.getInstance());
        assertTrue(repo.getSnapshot().getDefault().getAllowedTools().contains("git")); //$NON-NLS-1$
        assertFalse(repo.getSnapshot().getDefault().getAllowedTools().contains("ask_workmate")); //$NON-NLS-1$
    }

    @Test
    public void downgradeMirrorWritesLegacyDisabledSetAndIgnoresLaterLegacyEdits()
    {
        PreferenceStore store = new PreferenceStore();
        store.setValue(PreferenceConstants.PREF_DISABLED_TOOLS, "git,ask_workmate"); //$NON-NLS-1$
        store.setValue(PreferenceConstants.PREF_TOOL_PREFS_MIGRATION,
            PreferenceConstants.TOOL_PREFS_MIGRATION_VERSION);
        PreferenceToolProfileRepository repo = new PreferenceToolProfileRepository(store);
        ToolSettingsService.setRepositoryOverrideForTest(repo);
        ToolSettingsService.setStoreOverrideForTest(store);

        ToolProfileMigration.migrateIfNeeded(repo, Set.of("git", "ask_workmate", "list_projects"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            ToolSettingsService.getInstance());
        ToolSettingsService.getInstance().mirrorDefaultToLegacy(store, repo.getSnapshot().getDefault());
        Set<String> mirrored = Set.of(store.getString(PreferenceConstants.PREF_DISABLED_TOOLS).split(",")); //$NON-NLS-1$
        assertTrue(mirrored.contains("git")); //$NON-NLS-1$
        assertTrue(mirrored.contains("ask_workmate")); //$NON-NLS-1$
        assertFalse(mirrored.contains("list_projects")); //$NON-NLS-1$

        store.setValue(PreferenceConstants.PREF_DISABLED_TOOLS, "list_projects"); //$NON-NLS-1$
        assertTrue("direct legacy-key edit must not rewrite the profile", //$NON-NLS-1$
            repo.getSnapshot().getDefault().getAllowedTools().contains("list_projects")); //$NON-NLS-1$
        assertFalse(ToolSettingsService.getInstance().getDisabledTools().contains("list_projects")); //$NON-NLS-1$
        assertTrue(ToolSettingsService.getInstance().getDisabledTools().contains("git")); //$NON-NLS-1$
    }
}
