/** Copyright (c) 2026 DitriX; modified by ExpSPB in 2026. */
package fm.giper.edt.mcp.server.preferences;

import static org.junit.Assert.*;
import java.util.List;
import java.util.Set;
import org.eclipse.jface.preference.PreferenceStore;
import org.junit.Before;
import org.junit.Test;
import fm.giper.edt.mcp.server.profiles.DefaultToolProfileFactory;
import fm.giper.edt.mcp.server.profiles.PreferenceToolProfileRepository;
import fm.giper.edt.mcp.server.profiles.ToolProfile;
import fm.giper.edt.mcp.server.profiles.ToolProfileCodec;
import fm.giper.edt.mcp.server.profiles.ToolProfileSnapshot;

/** Pins the real named-profile editor model used by ToolsTab, without native SWT controls. */
public class ToolsTabProvenanceTest
{
    private PreferenceToolProfileRepository repository;
    private ToolProfilesEditorModel model;

    @Before
    public void setUp()
    {
        repository = new PreferenceToolProfileRepository(new PreferenceStore());
        ToolProfile review = ToolProfile.builder().id("review").displayName("Review")
            .allowedTools(ToolPreset.CODE_REVIEW.toAllowlist(ToolGroup.allToolNames()))
            .presetId("code-review").build();
        repository.replaceAll(0L, ToolProfileSnapshot.of(0L,
            List.of(DefaultToolProfileFactory.createDefault(Set.of("list_projects")), review)));
        model = ToolProfilesEditorModel.load(repository.getSnapshot(), ToolGroup.allToolNames());
        model.select("review");
    }

    @Test
    public void manualCheckboxAndGroupEditsRetainProvenanceUntilAtomicSave()
    {
        model.setToolEnabled("get_edt_version", false);
        model.setGroupEnabled(ToolGroup.DEBUG.getToolNames(), true);
        assertEquals("code-review", model.getSelected().getPresetId());
        assertTrue(repository.getSnapshot().get("review").getAllowedTools().contains("get_edt_version"));
        assertTrue(model.apply(repository).isAccepted());
        assertEquals("code-review", repository.getSnapshot().get("review").getPresetId());
        assertFalse(repository.getSnapshot().get("review").getAllowedTools().contains("get_edt_version"));
        assertTrue(repository.getSnapshot().get("review").getAllowedTools().contains("set_breakpoint"));
        assertFalse(model.isDirty());
    }

    @Test
    public void customChangesOnlyProvenanceAndDoesNotAffectOtherProfiles()
    {
        ToolProfile original = model.getSelected();
        ToolProfile fallback = repository.getSnapshot().getDefault();
        model.applyPreset(ToolPreset.CUSTOM);
        assertEquals(original.getAllowedTools(), model.getSelected().getAllowedTools());
        assertEquals(ToolPreset.CUSTOM, model.matchPreset());
        assertTrue(model.isDirty());
        assertTrue(model.apply(repository).isAccepted());
        assertEquals("custom", repository.getSnapshot().get("review").getPresetId());
        assertEquals(original.getRevision() + 1L, repository.getSnapshot().get("review").getRevision());
        assertEquals(fallback.getAllowedTools(), repository.getSnapshot().getDefault().getAllowedTools());
    }

    @Test
    public void presetSelectionResetAndRestoreKeepTheirProvenance()
    {
        model.applyPreset(ToolPreset.DEVELOPMENT);
        assertEquals(ToolPreset.DEVELOPMENT, model.matchPreset());
        assertEquals(ToolPreset.DEVELOPMENT.toAllowlist(ToolGroup.allToolNames()),
            model.getSelected().getAllowedTools());
        model.restoreSelected();
        assertEquals(ToolPreset.CODE_REVIEW, model.matchPreset());
        model.resetSelectedToShippedDefaults();
        assertEquals(ToolPreset.CUSTOM, model.matchPreset());
        assertFalse(model.getSelected().getAllowedTools().contains("get_form_screenshot"));
        model.applyPreset(ToolPreset.ALL_TOOLS);
        assertEquals(ToolPreset.ALL_TOOLS, model.matchPreset());
        assertTrue(model.getSelected().getAllowedTools().contains("get_form_screenshot"));
    }

    @Test
    public void duplicateAndExchangePreserveProvenanceButImportKeepsTargetIdentity() throws Exception
    {
        model.duplicate("review-copy", "Copy");
        assertEquals("code-review", model.getSelected().getPresetId());
        ToolProfile exported = ToolProfileCodec.decodeSingleProfile(
            ToolProfileCodec.encodeSingleProfile(model.getSelected()));
        assertEquals("code-review", exported.getPresetId());
        model.select("review");
        long revision = model.getSelected().getRevision();
        model.applyImportedProfileToSelected(exported.toBuilder().presetId("development").build());
        assertEquals("review", model.getSelected().getId());
        assertEquals(revision, model.getSelected().getRevision());
        assertEquals("development", model.getSelected().getPresetId());
    }

    @Test
    public void oldImportWithoutProvenanceDoesNotInheritTargetPreset() throws Exception
    {
        ToolProfile imported = ToolProfile.builder().id("old").displayName("Old")
            .allowedTools(Set.of("list_projects", "unknown_future_tool")).build();
        ToolProfile decoded = ToolProfileCodec.decodeSingleProfile(ToolProfileCodec.encodeSingleProfile(imported));
        assertNull(decoded.getPresetId());
        model.applyImportedProfileToSelected(decoded);
        assertEquals(ToolPreset.CUSTOM, model.matchPreset());
        assertEquals(imported.getAllowedTools(), model.getSelected().getAllowedTools());
    }

    @Test
    public void oldExactAllToolsSelectionIsInitializedWithoutMutatingSnapshot()
    {
        ToolProfile old = ToolProfile.builder().id("default").displayName("Default")
            .allowedTools(ToolGroup.allToolNames()).build();
        ToolProfileSnapshot snapshot = ToolProfileSnapshot.of(4L, List.of(old));
        ToolProfilesEditorModel loaded = ToolProfilesEditorModel.load(snapshot, ToolGroup.allToolNames());
        assertEquals(ToolPreset.ALL_TOOLS, loaded.matchPreset());
        assertEquals(old.getAllowedTools(), loaded.getSelected().getAllowedTools());
        assertNull(snapshot.getDefault().getPresetId());
        assertFalse(loaded.isDirty());
    }

    @Test
    public void oldProfileNoOpSaveKeepsBytesRevisionsAndSavedExceptions()
    {
        PreferenceStore store = new PreferenceStore();
        ToolProfile old = ToolProfile.builder().id("default").displayName("Default")
            .allowedTools(Set.of("list_projects", "clean_project", "get_form_screenshot"))
            .build();
        store.setValue(PreferenceConstants.PREF_TOOL_PROFILES_JSON,
            ToolProfileCodec.encode(ToolProfileSnapshot.of(7L, List.of(old))));
        PreferenceToolProfileRepository repo = new PreferenceToolProfileRepository(store);
        String before = store.getString(PreferenceConstants.PREF_TOOL_PROFILES_JSON);
        ToolProfilesEditorModel loaded = ToolProfilesEditorModel.load(repo.getSnapshot(), ToolGroup.allToolNames());
        loaded.renameDisplayName("Default");
        loaded.setToolEnabled("list_projects", true);
        loaded.apply(repo);
        assertEquals(before, store.getString(PreferenceConstants.PREF_TOOL_PROFILES_JSON));
        assertEquals(7L, repo.getSnapshot().getDocumentRevision());
        assertEquals(old.getAllowedTools(), repo.getSnapshot().getDefault().getAllowedTools());
    }

    @Test
    public void malformedProvenanceTypeIsRejectedAndUnknownStringRemainsCompatible() throws Exception
    {
        ToolProfile profile = model.getSelected().toBuilder().presetId("future-preset").build();
        String encoded = ToolProfileCodec.encodeSingleProfile(profile);
        assertEquals("future-preset", ToolProfileCodec.decodeSingleProfile(encoded).getPresetId());
        model.applyImportedProfileToSelected(ToolProfileCodec.decodeSingleProfile(encoded));
        assertEquals(ToolPreset.CUSTOM, model.matchPreset());
        try
        {
            com.google.gson.JsonObject malformed = com.google.gson.JsonParser.parseString(encoded).getAsJsonObject();
            malformed.getAsJsonObject("profile").addProperty("presetId", 7);
            ToolProfileCodec.decodeSingleProfile(malformed.toString());
            fail("A numeric presetId must not be accepted as a string");
        }
        catch (fm.giper.edt.mcp.server.profiles.MalformedToolProfileDocumentException expected)
        {
            assertTrue(expected.getMessage().contains("presetId"));
        }
    }
}
