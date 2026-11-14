/**
 * MCP Server for EDT - Tests
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server.preferences;

import static org.junit.Assert.*;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.osgi.util.NLS;
import org.junit.Test;

/** Verifies that launch presets remain separate from stored workspace selections. */
public class ToolPresetStoredSessionTest extends ToolPresetSessionTestSupport
{
    @Test
    public void launchPresetRemainsSeparateFromWorkspaceSelection() throws Exception
    {
        launch("analysis-only");
        Set<String> storedBefore = service.getStoredDisabledTools();
        String storedPresetBefore = service.getPresetId();
        byte[] before = serializedStore();

        assertEquals("custom", storedPresetBefore);
        assertEquals(storedBefore, service.getStoredDisabledTools());
        assertEquals(ToolPreset.ANALYSIS_ONLY.getDisabledTools(), service.getDisabledTools());
        assertArrayEquals("reading launch and workspace views must preserve stored preference bytes", //$NON-NLS-1$
            before, serializedStore());
    }

    @Test
    public void testStoredViewRoundTripPreservesOriginalSerialization() throws Exception
    {
        store.setValue(PreferenceConstants.PREF_DISABLED_TOOLS,
            "list_projects, git, get_edt_version,ask_workmate");
        store.setValue(PreferenceConstants.PREF_TOOL_PRESET_ID, "development");
        launch("analysis-only");
        byte[] before = serializedStore();
        service.setDisabledTools(storedDisabledView(), ToolPreset.fromId(storedPresetView()));
        assertArrayEquals("unchanged stored-view save must preserve spacing and ordering", before,
            serializedStore());
        assertEquals("development", store.getString(PreferenceConstants.PREF_TOOL_PRESET_ID));
    }

    @Test
    public void profileLaunchNoteUsesLocalizedPresetMessage() throws Exception
    {
        launch("analysis-only");
        assertEquals(NLS.bind(Messages.ToolsTab_ProfileLaunchPreset,
            ToolPreset.ANALYSIS_ONLY.getDisplayName()), ToolsTab.getSessionPresetNote());
    }

    @Test
    public void testStoredDisabledViewMigratesEvenWithLaunchPreset() throws Exception
    {
        store.setValue(PreferenceConstants.PREF_DISABLED_TOOLS, "list_projects");
        store.setValue(PreferenceConstants.PREF_TOOL_PREFS_MIGRATION, 0);
        launch("all-tools");
        Set<String> migrated = storedDisabledView();
        assertEquals("stored view must return the migrated store", storedDisabled(), migrated);
        assertTrue("known migrations must retain the owner selection and add default-off tools",
            migrated.containsAll(Set.of("list_projects", "git", "ask_workmate")));
        assertEquals(PreferenceConstants.TOOL_PREFS_MIGRATION_VERSION,
            store.getInt(PreferenceConstants.PREF_TOOL_PREFS_MIGRATION));
        assertTrue(service.getDisabledTools().isEmpty());
    }

    @Test
    public void testStoredPresetViewMigratesEvenWithLaunchPreset() throws Exception
    {
        store.setValue(PreferenceConstants.PREF_DISABLED_TOOLS,
            ToolSettingsService.serializeDisabledTools(ToolSettingsService.CODE_REVIEW_RECOGNITION_SHAPE));
        store.setValue(PreferenceConstants.PREF_TOOL_PREFS_MIGRATION, 12);
        store.setToDefault(PreferenceConstants.PREF_TOOL_PRESET_ID);
        launch("all-tools");
        assertEquals("code-review", storedPresetView());
        assertEquals("code-review", store.getString(PreferenceConstants.PREF_TOOL_PRESET_ID));
        assertEquals(PreferenceConstants.TOOL_PREFS_MIGRATION_VERSION,
            store.getInt(PreferenceConstants.PREF_TOOL_PREFS_MIGRATION));
    }

    @Test
    public void testPresetIdAccessorIgnoresLaunchPreset() throws Exception
    {
        launch("analysis-only");
        assertEquals("custom", service.getPresetId());
    }

    @Test
    public void testSetToolEnabledAddsOnlyToStoredSelection() throws Exception
    {
        Set<String> expected = new HashSet<>(storedDisabled());
        expected.add("get_markers");
        launch("analysis-only");
        service.setToolEnabled("get_markers", false);
        assertEquals("old stored set plus the edited tool", expected, storedDisabled());
        assertEquals("custom", store.getString(PreferenceConstants.PREF_TOOL_PRESET_ID));
        assertEquals(ToolPreset.ANALYSIS_ONLY.getDisabledTools(), service.getDisabledTools());
    }

    @Test
    public void testSetGroupEnabledAddsOnlyToStoredSelection() throws Exception
    {
        Set<String> expected = new HashSet<>(storedDisabled());
        expected.addAll(ToolGroup.BSL_CODE.getToolNames());
        launch("analysis-only");
        service.setGroupEnabled(ToolGroup.BSL_CODE, false);
        assertEquals(expected, storedDisabled());
        assertEquals(ToolPreset.ANALYSIS_ONLY.getDisabledTools(), service.getDisabledTools());
    }

    @Test
    public void testCustomPresetKeepsStoredSelection() throws Exception
    {
        store.setValue(PreferenceConstants.PREF_TOOL_PRESET_ID, "development");
        Set<String> before = storedDisabled();
        launch("analysis-only");
        service.applyPreset(ToolPreset.CUSTOM);
        assertEquals(before, storedDisabled());
        assertEquals("custom", store.getString(PreferenceConstants.PREF_TOOL_PRESET_ID));
        assertEquals(ToolPreset.ANALYSIS_ONLY.getDisabledTools(), service.getDisabledTools());
    }

}
