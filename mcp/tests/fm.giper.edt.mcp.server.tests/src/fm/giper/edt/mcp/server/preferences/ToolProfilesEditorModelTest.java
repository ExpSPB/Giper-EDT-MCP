/**
 * MCP Server for EDT - Tests
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server.preferences;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Set;

import org.eclipse.jface.preference.PreferenceStore;
import org.junit.Test;

import fm.giper.edt.mcp.server.profiles.DefaultToolProfileFactory;
import fm.giper.edt.mcp.server.profiles.PreferenceToolProfileRepository;
import fm.giper.edt.mcp.server.profiles.ReplaceResult;
import fm.giper.edt.mcp.server.profiles.ToolProfile;
import fm.giper.edt.mcp.server.profiles.ToolProfileSnapshot;

/**
 * Headless CRUD, dirty, preset, unknown tools and optimistic apply/reload.
 */
public class ToolProfilesEditorModelTest
{
    private static final Set<String> CATALOG = Set.of(
        "list_projects", //$NON-NLS-1$
        "write_module_source", //$NON-NLS-1$
        "get_server_status", //$NON-NLS-1$
        "debug_launch"); //$NON-NLS-1$

    @Test
    public void addCreatesImmutableIdAndSelectsDraft()
    {
        ToolProfilesEditorModel model = loadDefault();

        assertTrue(model.add("review", "Review", "Read-only", ToolPreset.CODE_REVIEW).isOk()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        assertEquals("review", model.getSelectedId()); //$NON-NLS-1$
        assertEquals("review", model.getSelected().getId()); //$NON-NLS-1$
        assertFalse(model.getSelected().getAllowedTools().isEmpty());
        assertTrue(model.isDirty());
        assertFalse(model.add("review", "Other", "", ToolPreset.ALL_TOOLS).isOk()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        assertFalse(model.add("empty", "Empty", "", ToolPreset.CUSTOM).isOk()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }

    @Test
    public void defaultCannotBeDeletedOrDisabled()
    {
        ToolProfilesEditorModel model = loadDefault();

        assertFalse(model.canDeleteSelected());
        assertFalse(model.canDisableSelected());
        assertFalse(model.deleteSelected().isOk());
        assertFalse(model.setEnabled(false).isOk());
        assertTrue(model.getSelected().isEnabled());
        assertFalse(model.isDirty());
    }

    @Test
    public void duplicateCopiesAllowlistUnderNewId()
    {
        ToolProfilesEditorModel model = loadWithUnknown();
        model.select("review"); //$NON-NLS-1$

        assertTrue(model.duplicate("review-2", "Review copy").isOk()); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("review-2", model.getSelectedId()); //$NON-NLS-1$
        assertEquals(model.getDrafts().stream()
            .filter(p -> "review".equals(p.getId())) //$NON-NLS-1$
            .findFirst().orElseThrow().getAllowedTools(),
            model.getSelected().getAllowedTools());
        assertTrue(model.getSelected().getAllowedTools().contains("legacy_unknown")); //$NON-NLS-1$
        assertNotEquals("review", model.getSelected().getId()); //$NON-NLS-1$
    }

    @Test
    public void renameAndToggleKeepUnknownTools()
    {
        ToolProfilesEditorModel model = loadWithUnknown();
        model.select("review"); //$NON-NLS-1$

        assertTrue(model.renameDisplayName("Code review").isOk()); //$NON-NLS-1$
        assertTrue(model.setDescription("Agents use this as the review role.").isOk()); //$NON-NLS-1$
        assertTrue(model.setToolEnabled("write_module_source", false).isOk()); //$NON-NLS-1$
        assertTrue(model.getSelected().getAllowedTools().contains("legacy_unknown")); //$NON-NLS-1$
        assertFalse(model.getSelected().getAllowedTools().contains("write_module_source")); //$NON-NLS-1$
        assertTrue(model.unknownAllowedTools().contains("legacy_unknown")); //$NON-NLS-1$
    }

    @Test
    public void selectDoesNotSaveOrClearDirty()
    {
        ToolProfilesEditorModel model = loadDefault();
        model.add("dev", "Dev", ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        assertTrue(model.isDirty());

        assertTrue(model.select(ToolProfile.DEFAULT_ID).isOk());
        assertTrue(model.isDirty());
        assertEquals(ToolProfile.DEFAULT_ID, model.getSelectedId());
        assertTrue(model.select("dev").isOk()); //$NON-NLS-1$
        assertEquals("dev", model.getSelectedId()); //$NON-NLS-1$
        assertTrue(model.isDirty());
    }

    @Test
    public void applyPresetKeepsUnknownNames()
    {
        ToolProfilesEditorModel model = loadWithUnknown();
        model.select("review"); //$NON-NLS-1$

        assertTrue(model.applyPreset(ToolPreset.ALL_TOOLS).isOk());
        assertTrue(model.getSelected().getAllowedTools().contains("legacy_unknown")); //$NON-NLS-1$
        assertTrue(model.getSelected().getAllowedTools().contains("list_projects")); //$NON-NLS-1$
        assertEquals(ToolPreset.ALL_TOOLS, model.matchPreset());
    }

    @Test
    public void restoreSelectedRevertsDraftAndDropsUnpublished()
    {
        ToolProfilesEditorModel model = loadWithUnknown();
        model.select("review"); //$NON-NLS-1$
        model.renameDisplayName("Changed"); //$NON-NLS-1$
        assertTrue(model.isDirty());

        assertTrue(model.restoreSelected().isOk());
        assertEquals("Review", model.getSelected().getDisplayName()); //$NON-NLS-1$
        assertFalse(model.isDirty());

        model.add("temp", "Temp", ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        assertTrue(model.restoreSelected().isOk());
        assertEquals(ToolProfile.DEFAULT_ID, model.getSelectedId());
        assertEquals(2, model.getDrafts().size());
    }

    @Test
    public void mandatoryStatusToolCannotBeUnchecked()
    {
        ToolProfilesEditorModel model = loadDefault();

        assertTrue(model.isMandatoryTool("get_server_status")); //$NON-NLS-1$
        assertTrue(model.isToolChecked("get_server_status")); //$NON-NLS-1$
        assertTrue(model.setToolEnabled("get_server_status", false).isOk()); //$NON-NLS-1$
        assertTrue(model.isToolChecked("get_server_status")); //$NON-NLS-1$
        assertFalse(model.disabledCatalogTools().contains("get_server_status")); //$NON-NLS-1$
    }

    @Test
    public void applyPublishesAllDraftsOnce()
    {
        PreferenceToolProfileRepository repo = new PreferenceToolProfileRepository(new PreferenceStore());
        repo.replaceAll(0L, snapshotWithReview());
        ToolProfilesEditorModel model = ToolProfilesEditorModel.load(repo.getSnapshot(), CATALOG);
        model.add("dev", "Dev", "Write"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        model.setToolEnabled("list_projects", true); //$NON-NLS-1$

        ReplaceResult result = model.apply(repo);

        assertEquals(ReplaceResult.Status.ACCEPTED, result.getStatus());
        assertFalse(model.isDirty());
        assertEquals(3, repo.getSnapshot().asList().size());
        assertNotNull(repo.getSnapshot().get("dev")); //$NON-NLS-1$
        assertEquals(repo.getSnapshot().getDocumentRevision(), model.getOriginalRevision());
    }

    @Test
    public void conflictKeepsDraftsUntilReload()
    {
        PreferenceStore store = new PreferenceStore();
        PreferenceToolProfileRepository repo = new PreferenceToolProfileRepository(store);
        repo.replaceAll(0L, snapshotWithReview());

        ToolProfilesEditorModel first = ToolProfilesEditorModel.load(repo.getSnapshot(), CATALOG);
        ToolProfilesEditorModel second = ToolProfilesEditorModel.load(repo.getSnapshot(), CATALOG);
        first.select("review"); //$NON-NLS-1$
        first.renameDisplayName("First window"); //$NON-NLS-1$
        assertEquals(ReplaceResult.Status.ACCEPTED, first.apply(repo).getStatus());

        second.select("review"); //$NON-NLS-1$
        second.renameDisplayName("Second window"); //$NON-NLS-1$
        ReplaceResult conflict = second.apply(repo);

        assertEquals(ReplaceResult.Status.CONFLICT, conflict.getStatus());
        assertTrue(second.isDirty());
        assertEquals("Second window", second.getSelected().getDisplayName()); //$NON-NLS-1$
        assertEquals("First window", repo.getSnapshot().get("review").getDisplayName()); //$NON-NLS-1$ //$NON-NLS-2$

        second.reload(repo.getSnapshot());
        assertFalse(second.isDirty());
        assertEquals("First window", second.getSelected().getDisplayName()); //$NON-NLS-1$
    }

    @Test
    public void resetSelectedDoesNotRemoveOtherProfiles()
    {
        ToolProfilesEditorModel model = loadWithUnknown();
        model.select("review"); //$NON-NLS-1$
        model.setEnabled(false);
        ToolProfile defaultBefore = model.getDrafts().stream()
            .filter(ToolProfile::isDefault)
            .findFirst().orElseThrow();

        assertTrue(model.resetSelectedToShippedDefaults().isOk());
        assertEquals(2, model.getDrafts().size());
        assertEquals(defaultBefore, model.getDrafts().stream()
            .filter(p -> ToolProfile.DEFAULT_ID.equals(p.getId()))
            .findFirst().orElse(null));
        assertEquals("Review", model.getSelected().getDisplayName()); //$NON-NLS-1$
        assertEquals("Review role", model.getSelected().getDescription()); //$NON-NLS-1$
        assertFalse("Restore Defaults changes the allowlist, not enabled state", //$NON-NLS-1$
            model.getSelected().isEnabled());
    }

    @Test
    public void resetSelectedStaysDraftUntilApply()
    {
        PreferenceStore store = new PreferenceStore();
        PreferenceToolProfileRepository repo = new PreferenceToolProfileRepository(store);
        repo.replaceAll(0L, snapshotWithReview());
        ToolProfile before = repo.getSnapshot().get("review"); //$NON-NLS-1$
        ToolProfilesEditorModel model = ToolProfilesEditorModel.load(repo.getSnapshot(), CATALOG);
        model.select("review"); //$NON-NLS-1$

        assertTrue(model.resetSelectedToShippedDefaults().isOk());
        assertNotEquals(before.getAllowedTools(), model.getSelected().getAllowedTools());
        assertEquals("Cancel must not publish the Restore Defaults draft", before, //$NON-NLS-1$
            repo.getSnapshot().get("review")); //$NON-NLS-1$
    }

    @Test
    public void exportUsesSelectedDraft()
    {
        ToolProfilesEditorModel model = loadWithUnknown();
        model.select("review"); //$NON-NLS-1$
        assertTrue(model.renameDisplayName("Dirty review").isOk()); //$NON-NLS-1$

        ToolProfile exportSource = model.getSelected();
        assertNotNull(exportSource);
        assertEquals("review", exportSource.getId()); //$NON-NLS-1$
        assertEquals("Dirty review", exportSource.getDisplayName()); //$NON-NLS-1$
        assertTrue(exportSource.getAllowedTools().contains("legacy_unknown")); //$NON-NLS-1$
    }

    @Test
    public void importOverlaysSelectedOnly()
    {
        ToolProfilesEditorModel model = loadWithUnknown();
        model.select("review"); //$NON-NLS-1$
        ToolProfile neighborBefore = model.getDrafts().stream()
            .filter(ToolProfile::isDefault)
            .findFirst().orElseThrow();
        long revisionBefore = model.getSelected().getRevision();

        ToolProfile imported = ToolProfile.builder()
            .id("foreign-id") //$NON-NLS-1$
            .displayName("Imported review") //$NON-NLS-1$
            .description("From another workspace") //$NON-NLS-1$
            .enabled(true)
            .allowedTools(Set.of("list_projects", "get_server_status")) //$NON-NLS-1$ //$NON-NLS-2$
            .revision(99L)
            .build();
        assertTrue(model.applyImportedProfileToSelected(imported).isOk());

        assertEquals("review", model.getSelectedId()); //$NON-NLS-1$
        assertEquals("review", model.getSelected().getId()); //$NON-NLS-1$
        assertEquals(revisionBefore, model.getSelected().getRevision());
        assertEquals("Imported review", model.getSelected().getDisplayName()); //$NON-NLS-1$
        assertEquals("From another workspace", model.getSelected().getDescription()); //$NON-NLS-1$
        ToolProfile neighborAfter = model.getDrafts().stream()
            .filter(ToolProfile::isDefault)
            .findFirst().orElseThrow();
        assertTrue(neighborBefore.sameContent(neighborAfter));
    }

    @Test
    public void importRejectsDisabledDefault()
    {
        ToolProfilesEditorModel model = loadDefault();
        ToolProfile imported = ToolProfile.builder()
            .id("other") //$NON-NLS-1$
            .displayName("Other") //$NON-NLS-1$
            .enabled(false)
            .allowedTools(Set.of("list_projects")) //$NON-NLS-1$
            .build();

        ToolProfilesEditorModel.OperationResult result = model.applyImportedProfileToSelected(imported);
        assertFalse(result.isOk());
        assertEquals("Profile 'default' cannot be disabled", result.getMessage()); //$NON-NLS-1$
        assertFalse(model.isDirty());
        assertTrue(model.getSelected().isEnabled());
    }

    @Test
    public void importDoesNotTouchRepository()
    {
        PreferenceStore store = new PreferenceStore();
        PreferenceToolProfileRepository repo = new PreferenceToolProfileRepository(store);
        repo.replaceAll(0L, snapshotWithReview());
        ToolProfileSnapshot before = repo.getSnapshot();
        ToolProfilesEditorModel model = ToolProfilesEditorModel.load(before, CATALOG);
        model.select("review"); //$NON-NLS-1$

        ToolProfile imported = ToolProfile.builder()
            .id("foreign") //$NON-NLS-1$
            .displayName("Imported") //$NON-NLS-1$
            .description("Draft only") //$NON-NLS-1$
            .allowedTools(Set.of("debug_launch")) //$NON-NLS-1$
            .build();
        assertTrue(model.applyImportedProfileToSelected(imported).isOk());

        assertEquals(before, repo.getSnapshot());
        assertTrue(model.isDirty());
    }

    @Test
    public void importSetsDirtyAndKeepsOriginalRevision()
    {
        ToolProfilesEditorModel model = loadWithUnknown();
        model.select("review"); //$NON-NLS-1$
        long originalRevision = model.getOriginalRevision();

        ToolProfile imported = ToolProfile.builder()
            .id("other") //$NON-NLS-1$
            .displayName("Changed") //$NON-NLS-1$
            .description("New text") //$NON-NLS-1$
            .allowedTools(Set.of("list_projects")) //$NON-NLS-1$
            .build();
        assertTrue(model.applyImportedProfileToSelected(imported).isOk());

        assertTrue(model.isDirty());
        assertEquals(originalRevision, model.getOriginalRevision());
    }

    @Test
    public void importKeepsUnknownTools()
    {
        ToolProfilesEditorModel model = loadWithUnknown();
        model.select("review"); //$NON-NLS-1$

        ToolProfile imported = ToolProfile.builder()
            .id("other") //$NON-NLS-1$
            .displayName("Imported") //$NON-NLS-1$
            .allowedTools(Set.of("legacy_unknown", "another_unknown", "list_projects")) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            .build();
        assertTrue(model.applyImportedProfileToSelected(imported).isOk());

        assertTrue(model.getSelected().getAllowedTools().contains("legacy_unknown")); //$NON-NLS-1$
        assertTrue(model.getSelected().getAllowedTools().contains("another_unknown")); //$NON-NLS-1$
        assertTrue(model.unknownAllowedTools().contains("legacy_unknown")); //$NON-NLS-1$
        assertTrue(model.unknownAllowedTools().contains("another_unknown")); //$NON-NLS-1$
    }

    @Test
    public void importReplacesAllowlistNotMerge()
    {
        ToolProfilesEditorModel model = loadWithUnknown();
        model.select("review"); //$NON-NLS-1$
        assertTrue(model.getSelected().getAllowedTools().contains("write_module_source")); //$NON-NLS-1$

        ToolProfile imported = ToolProfile.builder()
            .id("other") //$NON-NLS-1$
            .displayName("Imported") //$NON-NLS-1$
            .allowedTools(Set.of("list_projects", "legacy_unknown")) //$NON-NLS-1$ //$NON-NLS-2$
            .build();
        assertTrue(model.applyImportedProfileToSelected(imported).isOk());

        assertFalse(model.getSelected().getAllowedTools().contains("write_module_source")); //$NON-NLS-1$
        assertTrue(model.getSelected().getAllowedTools().contains("list_projects")); //$NON-NLS-1$
        assertTrue(model.getSelected().getAllowedTools().contains("legacy_unknown")); //$NON-NLS-1$
    }

    @Test
    public void importValidationFailureLeavesDraftsUnchanged()
    {
        ToolProfilesEditorModel model = loadWithUnknown();
        model.select("review"); //$NON-NLS-1$
        ToolProfile selectedBefore = model.getSelected();
        ToolProfile neighborBefore = model.getDrafts().stream()
            .filter(ToolProfile::isDefault)
            .findFirst().orElseThrow();

        ToolProfile imported = ToolProfile.builder()
            .id("other") //$NON-NLS-1$
            .displayName("   ") //$NON-NLS-1$
            .allowedTools(Set.of("list_projects")) //$NON-NLS-1$
            .build();
        assertFalse(model.applyImportedProfileToSelected(imported).isOk());
        assertFalse(model.isDirty());
        assertTrue(selectedBefore.sameContent(model.getSelected()));
        assertTrue(neighborBefore.sameContent(model.getDrafts().stream()
            .filter(ToolProfile::isDefault)
            .findFirst().orElseThrow()));
    }

    @Test
    public void applyAfterImportUsesDraftRevisionNotFileRevision()
    {
        PreferenceStore store = new PreferenceStore();
        PreferenceToolProfileRepository repo = new PreferenceToolProfileRepository(store);
        repo.replaceAll(0L, snapshotWithReview());
        ToolProfilesEditorModel model = ToolProfilesEditorModel.load(repo.getSnapshot(), CATALOG);
        model.select("review"); //$NON-NLS-1$
        long draftRevision = model.getSelected().getRevision();
        assertEquals(1L, draftRevision);

        ToolProfile imported = ToolProfile.builder()
            .id("foreign") //$NON-NLS-1$
            .displayName("Imported name") //$NON-NLS-1$
            .description("Imported description") //$NON-NLS-1$
            .allowedTools(Set.of("debug_launch")) //$NON-NLS-1$
            .revision(99L)
            .build();
        assertTrue(model.applyImportedProfileToSelected(imported).isOk());
        assertEquals(draftRevision, model.getSelected().getRevision());

        ReplaceResult result = model.apply(repo);
        assertEquals(ReplaceResult.Status.ACCEPTED, result.getStatus());
        ToolProfile published = repo.getSnapshot().get("review"); //$NON-NLS-1$
        assertEquals(draftRevision + 1L, published.getRevision());
        assertNotEquals(99L, published.getRevision());
        assertEquals("Imported name", published.getDisplayName()); //$NON-NLS-1$
    }

    private static ToolProfilesEditorModel loadDefault()
    {
        return ToolProfilesEditorModel.load(DefaultToolProfileFactory.createSafeSnapshot(), CATALOG);
    }

    private static ToolProfilesEditorModel loadWithUnknown()
    {
        return ToolProfilesEditorModel.load(snapshotWithReview(), CATALOG);
    }

    private static ToolProfileSnapshot snapshotWithReview()
    {
        ToolProfile review = ToolProfile.builder()
            .id("review") //$NON-NLS-1$
            .displayName("Review") //$NON-NLS-1$
            .description("Review role") //$NON-NLS-1$
            .allowedTools(Set.of("list_projects", "write_module_source", "legacy_unknown")) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            .build();
        return ToolProfileSnapshot.of(1L, List.of(
            DefaultToolProfileFactory.createDefault(Set.of("list_projects")), review)); //$NON-NLS-1$
    }
}
