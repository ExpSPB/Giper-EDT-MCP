/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server.profiles;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jface.preference.PreferenceStore;
import org.junit.Test;

import fm.giper.edt.mcp.server.preferences.PreferenceConstants;

/**
 * Atomic publish, optimistic conflict, no-op, malformed and unsupported schema.
 */
public class PreferenceToolProfileRepositoryTest
{
    @Test
    public void absentDocumentPublishesSafeDefaultWithoutPersisting()
    {
        PreferenceStore store = new PreferenceStore();
        PreferenceToolProfileRepository repo = new PreferenceToolProfileRepository(store);

        assertEquals(ProfileDocumentState.ABSENT, repo.getDocumentState());
        assertEquals(ToolProfile.DEFAULT_ID, repo.getSnapshot().getDefault().getId());
        assertEquals("", store.getString(PreferenceConstants.PREF_TOOL_PROFILES_JSON)); //$NON-NLS-1$
    }

    @Test
    public void replaceAllPersistsCanonicalDocumentAndNotifies()
    {
        PreferenceStore store = new PreferenceStore();
        PreferenceToolProfileRepository repo = new PreferenceToolProfileRepository(store);
        AtomicInteger notifications = new AtomicInteger();
        repo.addListener((previous, current, changeSet) -> notifications.incrementAndGet());

        ToolProfileSnapshot next = ToolProfileSnapshot.of(0L, List.of(
            DefaultToolProfileFactory.createDefault(Set.of("list_projects")))); //$NON-NLS-1$
        ReplaceResult result = repo.replaceAll(0L, next);

        assertEquals(ReplaceResult.Status.ACCEPTED, result.getStatus());
        assertEquals(1L, result.getSnapshot().getDocumentRevision());
        assertEquals(ProfileDocumentState.VALID, repo.getDocumentState());
        assertEquals(1, notifications.get());
        assertTrue(store.getString(PreferenceConstants.PREF_TOOL_PROFILES_JSON).contains("list_projects")); //$NON-NLS-1$
    }

    @Test
    public void semanticNoOpDoesNotBumpRevisionOrNotify()
    {
        PreferenceStore store = new PreferenceStore();
        PreferenceToolProfileRepository repo = new PreferenceToolProfileRepository(store);
        repo.replaceAll(0L, ToolProfileSnapshot.of(0L, List.of(
            DefaultToolProfileFactory.createDefault(Set.of("list_projects"))))); //$NON-NLS-1$
        AtomicInteger notifications = new AtomicInteger();
        repo.addListener((previous, current, changeSet) -> notifications.incrementAndGet());

        ReplaceResult result = repo.replaceAll(1L, ToolProfileSnapshot.of(99L, List.of(
            DefaultToolProfileFactory.createDefault(Set.of("list_projects"))))); //$NON-NLS-1$

        assertEquals(ReplaceResult.Status.NO_OP, result.getStatus());
        assertEquals(1L, result.getSnapshot().getDocumentRevision());
        assertEquals(0, notifications.get());
    }

    @Test
    public void reorderingProfilesIsSemanticNoOp()
    {
        PreferenceStore store = new PreferenceStore();
        PreferenceToolProfileRepository repo = new PreferenceToolProfileRepository(store);
        ToolProfile review = ToolProfile.builder()
            .id("review") //$NON-NLS-1$
            .displayName("Review") //$NON-NLS-1$
            .allowedTools(Set.of("list_projects")) //$NON-NLS-1$
            .build();
        ReplaceResult initial = repo.replaceAll(0L, ToolProfileSnapshot.of(0L, List.of(
            DefaultToolProfileFactory.createSafeDefault(), review)));

        ToolProfileSnapshot current = initial.getSnapshot();
        ReplaceResult reordered = repo.replaceAll(current.getDocumentRevision(),
            ToolProfileSnapshot.of(current.getDocumentRevision(), List.of(
                current.get("review"), current.getDefault()))); //$NON-NLS-1$

        assertEquals(ReplaceResult.Status.NO_OP, reordered.getStatus());
        assertEquals(current.getDocumentRevision(), reordered.getSnapshot().getDocumentRevision());
    }

    @Test
    public void staleExpectedRevisionDoesNotOverwrite()
    {
        PreferenceStore store = new PreferenceStore();
        PreferenceToolProfileRepository repo = new PreferenceToolProfileRepository(store);
        repo.replaceAll(0L, ToolProfileSnapshot.of(0L, List.of(
            DefaultToolProfileFactory.createDefault(Set.of("list_projects"))))); //$NON-NLS-1$

        ReplaceResult result = repo.replaceAll(0L, ToolProfileSnapshot.of(0L, List.of(
            DefaultToolProfileFactory.createDefault(Set.of("write_module_source"))))); //$NON-NLS-1$

        assertEquals(ReplaceResult.Status.CONFLICT, result.getStatus());
        assertTrue(repo.getSnapshot().getDefault().getAllowedTools().contains("list_projects")); //$NON-NLS-1$
        assertEquals(1L, repo.getSnapshot().getDocumentRevision());
    }

    @Test
    public void unchangedProfileKeepsRevisionWhenSiblingChanges()
    {
        PreferenceStore store = new PreferenceStore();
        PreferenceToolProfileRepository repo = new PreferenceToolProfileRepository(store);
        ToolProfile review = ToolProfile.builder()
            .id("review") //$NON-NLS-1$
            .displayName("Review") //$NON-NLS-1$
            .allowedTools(Set.of("list_projects")) //$NON-NLS-1$
            .build();
        repo.replaceAll(0L, ToolProfileSnapshot.of(0L, List.of(
            DefaultToolProfileFactory.createSafeDefault(), review)));

        long defaultRevision = repo.getSnapshot().getDefault().getRevision();
        long reviewRevision = repo.getSnapshot().get("review").getRevision(); //$NON-NLS-1$

        ToolProfile updatedReview = review.toBuilder().displayName("Code review").build(); //$NON-NLS-1$
        repo.replaceAll(1L, ToolProfileSnapshot.of(1L, List.of(
            repo.getSnapshot().getDefault(), updatedReview)));

        assertEquals(defaultRevision, repo.getSnapshot().getDefault().getRevision());
        assertEquals(reviewRevision + 1L, repo.getSnapshot().get("review").getRevision()); //$NON-NLS-1$
        assertEquals(2L, repo.getSnapshot().getDocumentRevision());
    }

    @Test
    public void malformedDocumentIsBackedUpAndNotOverwritten()
    {
        PreferenceStore store = new PreferenceStore();
        store.setValue(PreferenceConstants.PREF_TOOL_PROFILES_JSON, "{broken"); //$NON-NLS-1$
        PreferenceToolProfileRepository repo = new PreferenceToolProfileRepository(store);

        assertEquals(ProfileDocumentState.MALFORMED, repo.getDocumentState());
        assertEquals("{broken", store.getString(PreferenceConstants.PREF_TOOL_PROFILES_JSON)); //$NON-NLS-1$
        assertEquals("{broken", store.getString(PreferenceConstants.PREF_TOOL_PROFILES_BACKUP)); //$NON-NLS-1$
        assertEquals(ToolProfile.DEFAULT_ID, repo.getSnapshot().getDefault().getId());
    }

    @Test
    public void schemaVersionOutsideIntRangeIsMalformedAndDoesNotAbortLoad()
    {
        PreferenceStore store = new PreferenceStore();
        String oversized = "{\"schemaVersion\":" + Long.MAX_VALUE //$NON-NLS-1$
            + ",\"documentRevision\":1,\"profiles\":[]}"; //$NON-NLS-1$
        store.setValue(PreferenceConstants.PREF_TOOL_PROFILES_JSON, oversized);
        PreferenceToolProfileRepository repo;
        try
        {
            repo = new PreferenceToolProfileRepository(store);
        }
        catch (RuntimeException e)
        {
            fail("constructor must survive an out-of-range schemaVersion, got " //$NON-NLS-1$
                + e.getClass().getName());
            return;
        }
        assertEquals(ProfileDocumentState.MALFORMED, repo.getDocumentState());
        assertEquals(oversized, store.getString(PreferenceConstants.PREF_TOOL_PROFILES_JSON));
        assertEquals(ToolProfile.DEFAULT_ID, repo.getSnapshot().getDefault().getId());
    }

    @Test
    public void unsupportedSchemaIsDiagnosedSeparatelyAndNotBackedUpAsMalformed()
    {
        PreferenceStore store = new PreferenceStore();
        String future = "{\"schemaVersion\":9,\"documentRevision\":1,\"profiles\":[]}"; //$NON-NLS-1$
        store.setValue(PreferenceConstants.PREF_TOOL_PROFILES_JSON, future);
        PreferenceToolProfileRepository repo = new PreferenceToolProfileRepository(store);

        assertEquals(ProfileDocumentState.UNSUPPORTED_SCHEMA, repo.getDocumentState());
        assertEquals(future, store.getString(PreferenceConstants.PREF_TOOL_PROFILES_JSON));
        assertEquals("", store.getString(PreferenceConstants.PREF_TOOL_PROFILES_BACKUP)); //$NON-NLS-1$
    }

    @Test
    public void concurrentReadersSeeWholeOldOrWholeNewSnapshot() throws Exception
    {
        PreferenceStore store = new PreferenceStore();
        PreferenceToolProfileRepository repo = new PreferenceToolProfileRepository(store);
        repo.replaceAll(0L, ToolProfileSnapshot.of(0L, List.of(
            DefaultToolProfileFactory.createDefault(Set.of("alpha"))))); //$NON-NLS-1$

        ToolProfileSnapshot published = repo.getSnapshot();
        CyclicBarrier start = new CyclicBarrier(5);
        CountDownLatch done = new CountDownLatch(4);
        AtomicInteger tornReads = new AtomicInteger();
        List<Thread> readers = new ArrayList<>();
        for (int i = 0; i < 4; i++)
        {
            Thread reader = new Thread(() -> {
                try
                {
                    start.await(2, TimeUnit.SECONDS);
                    for (int n = 0; n < 200; n++)
                    {
                        ToolProfileSnapshot snap = repo.getSnapshot();
                        if (snap.getDefault() == null || snap.asMap().isEmpty())
                        {
                            tornReads.incrementAndGet();
                        }
                    }
                }
                catch (Exception e)
                {
                    tornReads.incrementAndGet();
                }
                finally
                {
                    done.countDown();
                }
            });
            readers.add(reader);
            reader.start();
        }

        start.await(2, TimeUnit.SECONDS);
        ReplaceResult result = repo.replaceAll(published.getDocumentRevision(), ToolProfileSnapshot.of(
            published.getDocumentRevision(), List.of(
                DefaultToolProfileFactory.createDefault(Set.of("omega"))))); //$NON-NLS-1$
        assertEquals(ReplaceResult.Status.ACCEPTED, result.getStatus());
        assertTrue(done.await(2, TimeUnit.SECONDS));
        assertEquals(0, tornReads.get());
        assertTrue(repo.getSnapshot().getDefault().getAllowedTools().contains("omega")); //$NON-NLS-1$
        for (Thread reader : readers)
        {
            reader.join(1000);
        }
    }

    @Test
    public void sameSnapshotInstanceIsPublishedAtomically()
    {
        PreferenceStore store = new PreferenceStore();
        PreferenceToolProfileRepository repo = new PreferenceToolProfileRepository(store);
        AtomicReference<ToolProfileSnapshot> seen = new AtomicReference<>();
        repo.addListener((previous, current, changeSet) -> seen.set(current));

        ReplaceResult result = repo.replaceAll(0L, ToolProfileSnapshot.of(0L, List.of(
            DefaultToolProfileFactory.createDefault(Set.of("list_projects"))))); //$NON-NLS-1$

        assertSame(result.getSnapshot(), repo.getSnapshot());
        assertSame(result.getSnapshot(), seen.get());
        assertNotEquals(0L, result.getSnapshot().getDocumentRevision());
    }
}
