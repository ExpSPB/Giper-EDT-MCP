/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server.profiles;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import fm.giper.edt.mcp.server.preferences.EditorModelDraftSink;
import fm.giper.edt.mcp.server.preferences.ToolProfilesEditorModel;
import fm.giper.edt.mcp.server.profiles.ToolProfileExchangeController.Outcome;

/**
 * Headless export/import orchestration through {@link ToolProfileExchangeController}.
 */
public class ToolProfileExchangeControllerTest
{
    private static final Set<String> CATALOG = Set.of(
        "list_projects", //$NON-NLS-1$
        "write_module_source", //$NON-NLS-1$
        "get_server_status", //$NON-NLS-1$
        "debug_launch"); //$NON-NLS-1$

    private final ToolProfileExchangeController controller = new ToolProfileExchangeController();

    private Path tmp;

    @Before
    public void setUp() throws IOException
    {
        tmp = Files.createTempDirectory("tool-profile-exchange"); //$NON-NLS-1$
    }

    @After
    public void tearDown() throws IOException
    {
        if (tmp != null && Files.exists(tmp))
        {
            try (Stream<Path> walk = Files.walk(tmp))
            {
                walk.sorted(Comparator.reverseOrder()).forEach(ToolProfileExchangeControllerTest::deleteQuietly);
            }
        }
    }

    @Test
    public void exportSaveDialogCancelReturnsCancelledWithoutWritingOrNotifier()
    {
        ToolProfile selected = reviewProfile("Review"); //$NON-NLS-1$
        RecordingSink sink = new RecordingSink(selected);
        RecordingChooser chooser = new RecordingChooser();
        chooser.savePath = null;
        RecordingNotifier notifier = new RecordingNotifier();

        Outcome outcome = controller.exportSelected(sink, chooser, notifier);

        assertEquals(Outcome.CANCELLED, outcome);
        assertEquals(1, chooser.saveCalls.size());
        assertEquals("edt-mcp-profile-review.json", chooser.saveCalls.get(0)); //$NON-NLS-1$
        assertFalse(notifier.overwriteAsked);
        assertNull(notifier.exportFailure);
        assertNull(notifier.importFailure);
        assertTrue(notifier.unknownToolNames.isEmpty());
    }

    @Test
    public void exportOverwriteDeclinedLeavesExistingFileUntouched() throws IOException
    {
        Path fooJson = tmp.resolve("foo.json"); //$NON-NLS-1$
        Files.writeString(fooJson, "original", StandardCharsets.UTF_8); //$NON-NLS-1$
        ToolProfile selected = reviewProfile("Review"); //$NON-NLS-1$
        RecordingSink sink = new RecordingSink(selected);
        RecordingChooser chooser = new RecordingChooser();
        chooser.savePath = tmp.resolve("foo"); //$NON-NLS-1$
        RecordingNotifier notifier = new RecordingNotifier();
        notifier.overwriteAllowed = false;

        Outcome outcome = controller.exportSelected(sink, chooser, notifier);

        assertEquals(Outcome.CANCELLED, outcome);
        assertTrue(notifier.overwriteAsked);
        assertEquals(fooJson.normalize(), notifier.overwritePath.normalize());
        assertEquals("original", Files.readString(fooJson, StandardCharsets.UTF_8)); //$NON-NLS-1$
        assertNull(notifier.exportFailure);
    }

    @Test
    public void exportSuccessWritesSelectedDraftRoundTripDisplayName() throws Exception
    {
        ToolProfile selected = reviewProfile("\u041a\u043e\u0434-\u0440\u0435\u0432\u044c\u044e"); //$NON-NLS-1$
        RecordingSink sink = new RecordingSink(selected);
        Path target = tmp.resolve("exported.json"); //$NON-NLS-1$
        RecordingChooser chooser = new RecordingChooser();
        chooser.savePath = target;
        RecordingNotifier notifier = new RecordingNotifier();

        Outcome outcome = controller.exportSelected(sink, chooser, notifier);

        assertEquals(Outcome.COMPLETED, outcome);
        assertTrue(Files.isRegularFile(target));
        FileOpResult imported = ToolProfileFileService.importProfile(target);
        assertTrue(imported.isOk());
        assertEquals(selected.getDisplayName(), imported.getProfile().getDisplayName());
        assertEquals(selected.getId(), imported.getProfile().getId());
        assertFalse(notifier.overwriteAsked);
    }

    @Test
    public void importOpenDialogCancelNeverAppliesImported()
    {
        ToolProfile selected = reviewProfile("Review"); //$NON-NLS-1$
        RecordingSink sink = new RecordingSink(selected);
        RecordingChooser chooser = new RecordingChooser();
        chooser.openPath = null;
        RecordingConfirmation confirmation = new RecordingConfirmation();
        RecordingNotifier notifier = new RecordingNotifier();

        Outcome outcome = controller.importSelected(sink, chooser, confirmation, notifier);

        assertEquals(Outcome.CANCELLED, outcome);
        assertFalse(sink.applyCalled);
        assertFalse(confirmation.confirmCalled);
        assertNull(notifier.importFailure);
    }

    @Test
    public void importInvalidUtf8FailsWithoutApply() throws IOException
    {
        byte[] prefix = "{\"schemaVersion\":1}".getBytes(StandardCharsets.US_ASCII); //$NON-NLS-1$
        byte[] withBad = new byte[prefix.length + 2];
        System.arraycopy(prefix, 0, withBad, 0, prefix.length);
        withBad[prefix.length] = (byte)0xC0;
        withBad[prefix.length + 1] = (byte)0x80;
        Path file = tmp.resolve("illegal-utf8.json"); //$NON-NLS-1$
        Files.write(file, withBad);

        assertImportFileFailure(file);
    }

    @Test
    public void importWorkspaceDocumentFailsWithoutApply() throws IOException
    {
        Path file = tmp.resolve("workspace.json"); //$NON-NLS-1$
        Files.writeString(file,
            "{\"schemaVersion\":1,\"documentRevision\":1,\"profiles\":[]}", //$NON-NLS-1$
            StandardCharsets.UTF_8);

        assertImportFileFailure(file);
    }

    @Test
    public void importConfirmationDeclinedLeavesDraftUnchanged() throws Exception
    {
        ToolProfilesEditorModel model = loadReviewModel();
        model.select("review"); //$NON-NLS-1$
        ToolProfile before = model.getSelected();
        Path file = writeImportFile(importedProfile("Imported review", Set.of("list_projects"))); //$NON-NLS-1$ //$NON-NLS-2$
        EditorModelDraftSink sink = new EditorModelDraftSink(model);
        RecordingChooser chooser = new RecordingChooser();
        chooser.openPath = file;
        RecordingConfirmation confirmation = new RecordingConfirmation();
        confirmation.allow = false;
        RecordingNotifier notifier = new RecordingNotifier();

        Outcome outcome = controller.importSelected(sink, chooser, confirmation, notifier);

        assertEquals(Outcome.CANCELLED, outcome);
        assertTrue(confirmation.confirmCalled);
        assertTrue(before.sameContent(model.getSelected()));
        assertFalse(model.isDirty());
        assertTrue(notifier.unknownToolNames.isEmpty());
    }

    @Test
    public void importSuccessAppliesDraftAndReportsUnknownTools() throws Exception
    {
        ToolProfilesEditorModel model = loadReviewModel();
        model.select("review"); //$NON-NLS-1$
        Path file = writeImportFile(importedProfile("Imported review", //$NON-NLS-1$
            Set.of("list_projects", "legacy_unknown", "another_unknown"))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        EditorModelDraftSink sink = new EditorModelDraftSink(model);
        RecordingChooser chooser = new RecordingChooser();
        chooser.openPath = file;
        RecordingConfirmation confirmation = new RecordingConfirmation();
        confirmation.allow = true;
        RecordingNotifier notifier = new RecordingNotifier();

        Outcome outcome = controller.importSelected(sink, chooser, confirmation, notifier);

        assertEquals(Outcome.COMPLETED, outcome);
        assertEquals("Imported review", model.getSelected().getDisplayName()); //$NON-NLS-1$
        assertTrue(model.isDirty());
        assertTrue(model.getSelected().getAllowedTools().contains("legacy_unknown")); //$NON-NLS-1$
        assertEquals(Set.of("legacy_unknown", "another_unknown"), notifier.unknownToolNames); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void importApplyFailureReportsErrorWithoutUnknownTools() throws Exception
    {
        ToolProfile selected = reviewProfile("Review"); //$NON-NLS-1$
        ToolProfile imported = importedProfile("Imported", Set.of("list_projects", "legacy_unknown")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        RecordingSink sink = new RecordingSink(selected);
        sink.applyResult = ProfileDraftApplyResult.failed("Profile 'default' cannot be disabled"); //$NON-NLS-1$
        sink.unknownAfterApply = Set.of("legacy_unknown"); //$NON-NLS-1$
        Path file = writeImportFile(imported);
        RecordingChooser chooser = new RecordingChooser();
        chooser.openPath = file;
        RecordingConfirmation confirmation = new RecordingConfirmation();
        confirmation.allow = true;
        RecordingNotifier notifier = new RecordingNotifier();

        Outcome outcome = controller.importSelected(sink, chooser, confirmation, notifier);

        assertEquals(Outcome.FAILED, outcome);
        assertTrue(sink.applyCalled);
        assertNotNull(notifier.importFailure);
        assertTrue(notifier.importFailure.contains("disabled")); //$NON-NLS-1$
        assertTrue(notifier.unknownToolNames.isEmpty());
    }

    private void assertImportFileFailure(Path file)
    {
        ToolProfile selected = reviewProfile("Review"); //$NON-NLS-1$
        RecordingSink sink = new RecordingSink(selected);
        RecordingChooser chooser = new RecordingChooser();
        chooser.openPath = file;
        RecordingConfirmation confirmation = new RecordingConfirmation();
        RecordingNotifier notifier = new RecordingNotifier();

        Outcome outcome = controller.importSelected(sink, chooser, confirmation, notifier);

        assertEquals(Outcome.FAILED, outcome);
        assertFalse(sink.applyCalled);
        assertFalse(confirmation.confirmCalled);
        assertNotNull(notifier.importFailure);
    }

    private Path writeImportFile(ToolProfile imported) throws IOException
    {
        Path file = tmp.resolve("import.json"); //$NON-NLS-1$
        FileOpResult exported = ToolProfileFileService.exportProfile(file, imported);
        assertTrue(exported.isOk());
        return file;
    }

    private static ToolProfilesEditorModel loadReviewModel()
    {
        ToolProfile review = ToolProfile.builder()
            .id("review") //$NON-NLS-1$
            .displayName("Review") //$NON-NLS-1$
            .description("Review role") //$NON-NLS-1$
            .allowedTools(Set.of("list_projects", "write_module_source")) //$NON-NLS-1$ //$NON-NLS-2$
            .build();
        ToolProfileSnapshot snapshot = ToolProfileSnapshot.of(1L, List.of(
            DefaultToolProfileFactory.createDefault(Set.of("list_projects")), review)); //$NON-NLS-1$
        return ToolProfilesEditorModel.load(snapshot, CATALOG);
    }

    private static ToolProfile reviewProfile(String displayName)
    {
        return ToolProfile.builder()
            .id("review") //$NON-NLS-1$
            .displayName(displayName)
            .description("Review role") //$NON-NLS-1$
            .allowedTools(Set.of("list_projects", "get_server_status")) //$NON-NLS-1$ //$NON-NLS-2$
            .revision(2L)
            .build();
    }

    private static ToolProfile importedProfile(String displayName, Set<String> allowedTools)
    {
        return ToolProfile.builder()
            .id("foreign-id") //$NON-NLS-1$
            .displayName(displayName)
            .description("From file") //$NON-NLS-1$
            .enabled(true)
            .allowedTools(allowedTools)
            .revision(99L)
            .build();
    }

    private static void deleteQuietly(Path path)
    {
        try
        {
            Files.deleteIfExists(path);
        }
        catch (IOException ignored)
        {
            // best-effort temp cleanup
        }
    }

    private static final class RecordingSink implements ProfileDraftSink
    {
        private final ToolProfile selected;
        private boolean applyCalled;
        private ProfileDraftApplyResult applyResult = ProfileDraftApplyResult.ok();
        private Set<String> unknownAfterApply = Set.of();

        private RecordingSink(ToolProfile selected)
        {
            this.selected = selected;
        }

        @Override
        public ToolProfile getSelected()
        {
            return selected;
        }

        @Override
        public ProfileDraftApplyResult applyImported(ToolProfile imported)
        {
            applyCalled = true;
            return applyResult;
        }

        @Override
        public Set<String> unknownAllowedTools()
        {
            return unknownAfterApply;
        }
    }

    private static final class RecordingChooser implements ProfileFileChooser
    {
        private final List<String> saveCalls = new ArrayList<>();
        private Path openPath;
        private Path savePath;

        @Override
        public Path chooseOpen()
        {
            return openPath;
        }

        @Override
        public Path chooseSave(String suggestedName)
        {
            saveCalls.add(suggestedName);
            return savePath;
        }
    }

    private static final class RecordingConfirmation implements ImportConfirmation
    {
        private boolean allow = true;
        private boolean confirmCalled;

        @Override
        public boolean confirm(ImportPreview preview)
        {
            confirmCalled = true;
            assertNotNull(preview.getCurrent());
            assertNotNull(preview.getImported());
            return allow;
        }
    }

    private static final class RecordingNotifier implements UserNotifier
    {
        private boolean overwriteAsked;
        private boolean overwriteAllowed = true;
        private Path overwritePath;
        private String exportFailure;
        private String importFailure;
        private Set<String> unknownToolNames = Set.of();

        @Override
        public boolean confirmOverwrite(Path resolvedPath)
        {
            overwriteAsked = true;
            overwritePath = resolvedPath;
            return overwriteAllowed;
        }

        @Override
        public void exportFailed(String detail)
        {
            exportFailure = detail;
        }

        @Override
        public void importFailed(String detail)
        {
            importFailure = detail;
        }

        @Override
        public void unknownTools(Set<String> names)
        {
            unknownToolNames = Set.copyOf(names);
        }
    }
}
