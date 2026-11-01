/** Copyright (c) 2026 DitriX; modified by ExpSPB in 2026. */
package fm.giper.edt.mcp.server.preferences;

import static org.junit.Assert.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import fm.giper.edt.mcp.server.profiles.DefaultToolProfileFactory;
import fm.giper.edt.mcp.server.profiles.PreferenceToolProfileRepository;
import fm.giper.edt.mcp.server.profiles.ToolProfile;
import fm.giper.edt.mcp.server.profiles.ToolProfileMigration;
import fm.giper.edt.mcp.server.profiles.ToolProfileSnapshot;

/** Stored selection, named-profile editing and migration remain independent of launch restrictions. */
public class ToolPresetStoredSessionTest extends ToolPresetSessionTestSupport
{
    @Test
    public void storedViewRoundTripPreservesOriginalSerialization() throws Exception
    {
        store.setValue(PreferenceConstants.PREF_DISABLED_TOOLS,
            "list_projects, git, get_edt_version,ask_workmate");
        store.setValue(PreferenceConstants.PREF_TOOL_PRESET_ID, "development");
        launch("analysis-only");
        byte[] before = serializedStore();
        service.setDisabledTools(service.getStoredDisabledTools(), ToolPreset.fromId(service.getStoredPresetId()));
        assertArrayEquals(before, serializedStore());
        assertEquals("development", service.getStoredPresetId());
    }

    @Test
    public void namedProfileSaveUsesStoredAllowlistAndPreservesOtherProfiles() throws Exception
    {
        PreferenceToolProfileRepository repository = new PreferenceToolProfileRepository(store);
        ToolProfile writer = ToolProfile.builder().id("writer").displayName("Writer")
            .allowedTools(Set.of("list_projects", "write_module_source", "unknown_future_tool"))
            .presetId("development").build();
        repository.replaceAll(0L, ToolProfileSnapshot.of(0L,
            List.of(DefaultToolProfileFactory.createDefault(Set.of("list_projects")), writer)));
        ToolSettingsService.setRepositoryOverrideForTest(repository);
        try
        {
            String before = store.getString(PreferenceConstants.PREF_TOOL_PROFILES_JSON);
            launch("analysis-only");
            assertEquals(before, store.getString(PreferenceConstants.PREF_TOOL_PROFILES_JSON));
            ToolProfilesEditorModel model = ToolProfilesEditorModel.load(repository.getSnapshot(), ToolGroup.allToolNames());
            model.select("writer");
            assertEquals(writer.getAllowedTools(), model.getSelected().getAllowedTools());
            assertEquals(ToolPreset.DEVELOPMENT, model.matchPreset());
            model.setToolEnabled("get_edt_version", true);
            model.apply(repository);
            assertTrue(repository.getSnapshot().get("writer").getAllowedTools().contains("write_module_source"));
            assertTrue(repository.getSnapshot().get("writer").getAllowedTools().contains("unknown_future_tool"));
            assertEquals(Set.of("list_projects"), repository.getSnapshot().getDefault().getAllowedTools());
            assertEquals("development", repository.getSnapshot().get("writer").getPresetId());
            assertTrue(ToolsTab.getSessionPresetNote().contains("restricts each saved profile"));
        }
        finally
        {
            ToolSettingsService.setRepositoryOverrideForTest(null);
        }
    }

    @Test
    public void profileMigrationNeverPersistsLaunchIntersection() throws Exception
    {
        PreferenceToolProfileRepository repository = new PreferenceToolProfileRepository(store);
        launch("analysis-only");
        ToolProfileMigration.migrateIfNeeded(repository, ToolGroup.allToolNames(), service);
        assertTrue(repository.getSnapshot().getDefault().getAllowedTools().contains("write_module_source"));
        assertFalse(repository.getSnapshot().getDefault().getAllowedTools().contains("list_projects"));
        assertEquals("custom", repository.getSnapshot().getDefault().getPresetId());
    }

    @Test
    public void storedDisabledViewMigratesEvenWithLaunchPreset() throws Exception
    {
        store.setValue(PreferenceConstants.PREF_DISABLED_TOOLS, "list_projects");
        store.setValue(PreferenceConstants.PREF_TOOL_PREFS_MIGRATION, 0);
        launch("all-tools");
        Set<String> migrated = service.getStoredDisabledTools();
        assertEquals(storedDisabled(), migrated);
        assertTrue(migrated.containsAll(Set.of("list_projects", "git", "ask_workmate", "get_form_screenshot")));
        assertEquals(PreferenceConstants.TOOL_PREFS_MIGRATION_VERSION,
            store.getInt(PreferenceConstants.PREF_TOOL_PREFS_MIGRATION));
        assertEquals(migrated, service.getDisabledTools());
    }

    @Test
    public void toolAndGroupEditsNeverPersistLaunchDisabledNames() throws Exception
    {
        Set<String> expected = new HashSet<>(storedDisabled());
        expected.add("get_markers");
        launch("analysis-only");
        service.setToolEnabled("get_markers", false);
        assertEquals(expected, storedDisabled());
        assertEquals(effectiveDisabled(ToolPreset.ANALYSIS_ONLY), service.getDisabledTools());
        expected.addAll(ToolGroup.BSL_CODE.getToolNames());
        service.setGroupEnabled(ToolGroup.BSL_CODE, false);
        assertEquals(expected, storedDisabled());
        assertEquals("custom", service.getStoredPresetId());
    }

    @Test
    public void customPresetKeepsStoredSelection() throws Exception
    {
        store.setValue(PreferenceConstants.PREF_TOOL_PRESET_ID, "development");
        Set<String> before = storedDisabled();
        launch("analysis-only");
        service.applyPreset(ToolPreset.CUSTOM);
        assertEquals(before, storedDisabled());
        assertEquals("custom", service.getPresetId());
        assertEquals(effectiveDisabled(ToolPreset.ANALYSIS_ONLY), service.getDisabledTools());
    }
}
