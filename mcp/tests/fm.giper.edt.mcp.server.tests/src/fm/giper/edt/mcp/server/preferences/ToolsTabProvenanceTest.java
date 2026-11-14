/**
 * MCP Server for EDT
 * Copyright (C) 2026 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server.preferences;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.eclipse.jface.preference.PreferenceStore;
import org.junit.Before;
import org.junit.Test;

import fm.giper.edt.mcp.server.profiles.DefaultToolProfileFactory;
import fm.giper.edt.mcp.server.profiles.PreferenceToolProfileRepository;
import fm.giper.edt.mcp.server.profiles.ReplaceResult;
import fm.giper.edt.mcp.server.profiles.ToolProfile;
import fm.giper.edt.mcp.server.profiles.ToolProfileSnapshot;

/** Profile preset provenance and local editing behavior without SWT internals. */
public class ToolsTabProvenanceTest
{
    private static final String PROFILE_ID = "code-review"; //$NON-NLS-1$

    private PreferenceToolProfileRepository repository;
    private ToolProfilesEditorModel model;

    @Before
    public void setUp()
    {
        PreferenceStore store = new PreferenceStore();
        repository = new PreferenceToolProfileRepository(store);
        Set<String> catalog = ToolProfilesEditorModel.catalogFromGroups();
        ToolProfile review = ToolProfile.builder()
            .id(PROFILE_ID)
            .displayName("Code Review") //$NON-NLS-1$
            .description("Review profile") //$NON-NLS-1$
            .enabled(true)
            .allowedTools(ToolPreset.CODE_REVIEW.toAllowlist(catalog))
            .presetId(ToolPreset.CODE_REVIEW.getId())
            .build();
        ToolProfileSnapshot initial = ToolProfileSnapshot.of(0L, List.of(
            DefaultToolProfileFactory.createSafeDefault(), review));
        assertTrue(repository.replaceAll(0L, initial).isAccepted());
        model = ToolProfilesEditorModel.load(repository.getSnapshot(), catalog);
        assertTrue(model.select(PROFILE_ID).isOk());
    }

    @Test
    public void toolCheckboxEditStaysLocalUntilApplyAndKeepsProfileIdentity()
    {
        ToolProfile published = repository.getSnapshot().get(PROFILE_ID);
        String tool = "get_edt_version"; //$NON-NLS-1$
        boolean originalState = published.getAllowedTools().contains(tool);

        assertTrue(model.setToolEnabled(tool, !originalState).isOk());
        assertTrue(model.isDirty());
        assertEquals("checkbox edits stay in the editor draft", published, //$NON-NLS-1$
            repository.getSnapshot().get(PROFILE_ID));
        assertEquals(PROFILE_ID, model.getSelected().getId());
        assertEquals(ToolPreset.CODE_REVIEW.getId(), model.getSelected().getPresetId());

        ReplaceResult result = model.apply(repository);

        assertEquals(ReplaceResult.Status.ACCEPTED, result.getStatus());
        ToolProfile saved = repository.getSnapshot().get(PROFILE_ID);
        assertEquals(PROFILE_ID, saved.getId());
        assertEquals(ToolPreset.CODE_REVIEW.getId(), saved.getPresetId());
        assertEquals(!originalState, saved.getAllowedTools().contains(tool));
        assertFalse(model.isDirty());
    }

    @Test
    public void returningCheckboxToOriginalStateClearsDirtyWithoutChangingPreset()
    {
        ToolProfile original = model.getSelected();
        String tool = "get_edt_version"; //$NON-NLS-1$
        boolean originalState = original.getAllowedTools().contains(tool);

        assertTrue(model.setToolEnabled(tool, !originalState).isOk());
        assertTrue(model.isDirty());
        assertTrue(model.setToolEnabled(tool, originalState).isOk());

        assertFalse(model.isDirty());
        assertEquals(original.getAllowedTools(), model.getSelected().getAllowedTools());
        assertEquals(ToolPreset.CODE_REVIEW.getId(), model.getSelected().getPresetId());
    }

    @Test
    public void applyingBuiltInOrCustomPresetChangesOnlySelectedDraft()
    {
        ToolProfile defaultBefore = repository.getSnapshot().get(ToolProfile.DEFAULT_ID);

        assertTrue(model.applyPreset(ToolPreset.DEVELOPMENT).isOk());
        assertEquals(ToolPreset.DEVELOPMENT.getId(), model.getSelected().getPresetId());
        assertEquals(ToolPreset.DEVELOPMENT.toAllowlist(ToolProfilesEditorModel.catalogFromGroups()),
            model.getSelected().getAllowedTools());
        assertEquals(defaultBefore, repository.getSnapshot().get(ToolProfile.DEFAULT_ID));
        assertTrue(model.isDirty());

        assertTrue(model.applyPreset(ToolPreset.CUSTOM).isOk());
        assertEquals(ToolPreset.CUSTOM.getId(), model.getSelected().getPresetId());
        assertEquals(ToolPreset.DEVELOPMENT.toAllowlist(ToolProfilesEditorModel.catalogFromGroups()),
            model.getSelected().getAllowedTools());
        assertEquals(ReplaceResult.Status.ACCEPTED, model.apply(repository).getStatus());
        assertEquals(ToolPreset.CUSTOM.getId(), repository.getSnapshot().get(PROFILE_ID).getPresetId());
    }

    @Test
    public void groupChangesAreLocalAndRestoreRevertsTheSelectedProfile()
    {
        ToolProfile original = model.getSelected();
        ToolGroup group = Arrays.stream(ToolGroup.values())
            .filter(candidate -> candidate.getToolNames().stream()
                .anyMatch(name -> !ToolProfilesEditorModel.MANDATORY_TOOL.equals(name)
                    && original.getAllowedTools().contains(name)))
            .findFirst().orElseThrow();

        assertTrue(model.setGroupEnabled(group.getToolNames(), false).isOk());
        assertTrue(model.isDirty());
        assertEquals(original, repository.getSnapshot().get(PROFILE_ID));
        assertTrue(model.restoreSelected().isOk());

        assertFalse(model.isDirty());
        assertEquals(original, model.getSelected());
        assertEquals(original, repository.getSnapshot().get(PROFILE_ID));
    }
}
