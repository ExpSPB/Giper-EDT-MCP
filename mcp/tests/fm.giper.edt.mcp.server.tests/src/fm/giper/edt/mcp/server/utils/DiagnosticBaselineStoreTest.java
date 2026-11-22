/*
 * MCP Server for EDT - Tests
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package fm.giper.edt.mcp.server.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import fm.giper.edt.mcp.server.utils.DiagnosticBaselineStore.Diff;
import fm.giper.edt.mcp.server.utils.DiagnosticBaselineStore.Finding;
import fm.giper.edt.mcp.server.utils.DiagnosticBaselineStore.Snapshot;

public class DiagnosticBaselineStoreTest
{
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    private static final String PROJECT = "Проект";
    private static final String LOCATION = "Catalog.Товары";
    private static final Finding ORIGINAL = new Finding(LOCATION, null, "rule", "SU1", "plugin", "Message", "ERROR");
    private static final Finding OTHER = new Finding(LOCATION, null, "rule", "SU1", "plugin", "Other", "ERROR");

    private record Row(Finding finding, int line, int offset) {}

    @Test
    public void movedOccurrenceMatchesWhileEqualTotalsCanHideAReplacement()
    {
        Row moved = new Row(ORIGINAL, 95, 1200);
        Row added = new Row(OTHER, 3, 10);
        Diff<Row> diff = DiagnosticBaselineStore.compare(List.of(ORIGINAL, ORIGINAL), List.of(added, moved),
            Row::finding);
        assertEquals(List.of(added), diff.added());
        assertEquals(1, diff.resolved());
    }

    @Test
    public void extraOccurrencesAreReturnedInCurrentRowOrder()
    {
        Row matched = new Row(ORIGINAL, 5, 35);
        Row other = new Row(OTHER, 10, 80);
        Row extra = new Row(ORIGINAL, 12, 110);
        Diff<Row> diff = DiagnosticBaselineStore.compare(List.of(ORIGINAL), List.of(matched, other, extra),
            Row::finding);
        assertEquals(List.of(other, extra), diff.added());
        assertEquals(0, diff.resolved());
    }

    @Test
    public void identityUsesPlaceModuleContributorAndSymbolicCheckWithShortUidFallback()
    {
        Finding reassigned = new Finding(LOCATION, null, "rule", "SU2", "plugin", "Message", "WARNING");
        assertEquals(new Diff<>(List.of(), 0), compare(ORIGINAL, reassigned));
        List<Finding> changed = List.of(
            new Finding(LOCATION + ".Attribute.Code", null, "rule", "SU1", "plugin", "Message", "ERROR"),
            new Finding(LOCATION, "ObjectModule", "rule", "SU1", "plugin", "Message", "ERROR"),
            new Finding(LOCATION, null, "rule", "SU1", "another", "Message", "ERROR"),
            new Finding(LOCATION, null, "another", "SU1", "plugin", "Message", "ERROR"));
        for (Finding finding : changed)
        {
            assertEquals(new Diff<>(List.of(finding), 1), compare(ORIGINAL, finding));
        }
        Finding raw = new Finding("", null, null, "SU1", null, "", "NONE");
        Finding rawOther = new Finding("", null, null, "SU2", null, "", "NONE");
        assertEquals(new Diff<>(List.of(), 0), compare(raw, raw));
        assertEquals(new Diff<>(List.of(rawOther), 1), compare(raw, rawOther));
    }

    private static Diff<Finding> compare(Finding saved, Finding current)
    {
        return DiagnosticBaselineStore.compare(List.of(saved), List.of(current), row -> row);
    }

    @Test
    public void reopenedStorePreservesScopeDuplicatesAndCopiesInputs() throws IOException
    {
        Path directory = temporary.newFolder().toPath();
        DiagnosticBaselineStore store = new DiagnosticBaselineStore(directory);
        List<String> scope = new ArrayList<>(List.of(LOCATION));
        List<String> variants = new ArrayList<>(List.of("catalog.товары", "справочник.товары"));
        List<Finding> findings = new ArrayList<>(List.of(ORIGINAL, OTHER, ORIGINAL));
        Snapshot saved = store.save(PROJECT, scope, variants, findings);
        scope.clear();
        variants.clear();
        findings.clear();
        assertEquals(List.of(ORIGINAL, OTHER, ORIGINAL), saved.findings());
        assertEquals(List.of(LOCATION), saved.scope());
        assertEquals(List.of("catalog.товары", "справочник.товары"), saved.scopeVariants());
        assertEquals(saved, new DiagnosticBaselineStore(directory).load(PROJECT));
    }

    @Test
    public void emptyBaselineIsDistinctFromMissingAndProjectsAreIsolated() throws IOException
    {
        Path directory = temporary.newFolder().toPath();
        DiagnosticBaselineStore store = new DiagnosticBaselineStore(directory);
        String otherProject = "../" + PROJECT;
        assertNull(store.load(PROJECT));
        Snapshot empty = store.save(PROJECT, List.of(), List.of(), List.of());
        Snapshot other = store.save(otherProject, List.of(), List.of(), List.of(ORIGINAL));
        assertEquals(empty, store.load(PROJECT));
        assertTrue(store.clear(PROJECT));
        assertFalse(store.clear(PROJECT));
        assertNull(store.load(PROJECT));
        assertEquals(other, store.load(otherProject));
        assertTrue(store.clear(otherProject));
    }

    @Test
    public void malformedUnsupportedOrStructurallyDamagedSnapshotsFailClosed() throws IOException
    {
        Path directory = temporary.newFolder().toPath();
        DiagnosticBaselineStore store = new DiagnosticBaselineStore(directory);
        store.save(PROJECT, List.of(), List.of(), List.of(ORIGINAL));
        Path file = onlyFile(directory);
        String good = Files.readString(file);
        List<String> invalid = List.of("", "{", "[]", good + " true",
            good.replace("\"version\":1", "\"version\":2"),
            good.replace("\"version\":1", "\"version\":\"1\""),
            good.replace(PROJECT, "wrong project"),
            good.replace("\"scope\":[]", "\"scope\":[12]"),
            good.replace("\"scope\":[]", "\"scope\":[\"Catalog.One\"]"),
            good.replace("\"scope\":[]", "\"scope\":[\"Catalog.One\"]")
                .replace("\"scopeVariants\":[]", "\"scopeVariants\":[\"CATALOG.ONE\"]"),
            good.replace("\"savedAt\":\"", "\"savedAt\":\"invalid-"),
            good.replace("\"findings\"", "\"missing\""),
            good.replace("\"modulePath\":null,", ""),
            good.replace("\"message\":\"Message\"", "\"message\":42"));
        for (String damaged : invalid)
        {
            assertFalse(good.equals(damaged));
            Files.writeString(file, damaged);
            assertThrows(IOException.class, () -> store.load(PROJECT));
        }
    }

    @Test
    public void failedWriteLeavesPriorSnapshotAndNoTemporaryFile() throws IOException
    {
        Path directory = temporary.newFolder().toPath();
        DiagnosticBaselineStore store = new DiagnosticBaselineStore(directory);
        Snapshot previous = store.save(PROJECT, List.of(), List.of(), List.of(ORIGINAL));
        Path file = onlyFile(directory);
        // The UTF-8 encoder rejects the unpaired surrogate while the replacement is being written.
        Finding malformed = new Finding("", null, null, null, null, String.valueOf(Character.MIN_HIGH_SURROGATE),
            "NONE");
        assertThrows(IOException.class, () -> store.save(PROJECT, List.of(), List.of(), List.of(malformed)));
        assertEquals(previous, store.load(PROJECT));
        assertEquals(file, onlyFile(directory));
    }

    private static Path onlyFile(Path directory) throws IOException
    {
        try (Stream<Path> files = Files.list(directory))
        {
            List<Path> entries = files.toList();
            assertEquals(1, entries.size());
            return entries.get(0);
        }
    }

    @Test
    public void concurrentWritersReadersAndClearSeeOnlyCompleteSnapshots() throws Exception
    {
        Path directory = temporary.newFolder().toPath();
        List<Finding> first = Collections.nCopies(30, ORIGINAL);
        List<Finding> second = Collections.nCopies(45, OTHER);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try
        {
            List<Callable<Void>> requests = new ArrayList<>();
            for (int i = 0; i < 24; i++)
            {
                List<Finding> rows = i % 2 == 0 ? first : second;
                requests.add(() -> {
                    new DiagnosticBaselineStore(directory).save(PROJECT, List.of(), List.of(), rows);
                    return null;
                });
                requests.add(() -> {
                    Snapshot loaded = new DiagnosticBaselineStore(directory).load(PROJECT);
                    assertTrue(loaded == null || loaded.findings().equals(first) || loaded.findings().equals(second));
                    return null;
                });
                requests.add(() -> {
                    new DiagnosticBaselineStore(directory).clear(PROJECT);
                    return null;
                });
            }
            for (Future<Void> future : pool.invokeAll(requests))
            {
                future.get();
            }
        }
        finally
        {
            pool.shutdownNow();
        }
        DiagnosticBaselineStore reopened = new DiagnosticBaselineStore(directory);
        Snapshot last = reopened.save(PROJECT, List.of(), List.of(), first);
        assertEquals(last, reopened.load(PROJECT));
        onlyFile(directory);
    }
}
