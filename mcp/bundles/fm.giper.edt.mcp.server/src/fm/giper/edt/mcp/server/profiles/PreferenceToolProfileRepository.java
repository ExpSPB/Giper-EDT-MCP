/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server.profiles;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jface.preference.IPreferenceStore;

import fm.giper.edt.mcp.server.Activator;
import fm.giper.edt.mcp.server.preferences.PreferenceConstants;

/**
 * One JSON preference key and one {@link AtomicReference} snapshot.
 * A malformed or future-schema document is never auto-overwritten.
 */
public final class PreferenceToolProfileRepository implements ToolProfileRepository
{
    private final IPreferenceStore store;
    private final AtomicReference<ToolProfileSnapshot> published =
        new AtomicReference<>(DefaultToolProfileFactory.createSafeSnapshot());
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private final Object writeLock = new Object();

    private volatile ProfileDocumentState documentState = ProfileDocumentState.ABSENT;

    public PreferenceToolProfileRepository(IPreferenceStore store)
    {
        this.store = store;
        reloadFromStore();
    }

    /**
     * Re-reads the preference key. Used on construction and after an explicit UI reload.
     */
    public void reloadFromStore()
    {
        synchronized (writeLock)
        {
            if (store == null)
            {
                documentState = ProfileDocumentState.ABSENT;
                published.set(DefaultToolProfileFactory.createSafeSnapshot());
                return;
            }
            String json = store.getString(PreferenceConstants.PREF_TOOL_PROFILES_JSON);
            if (json == null || json.isBlank())
            {
                documentState = ProfileDocumentState.ABSENT;
                published.set(DefaultToolProfileFactory.createSafeSnapshot());
                return;
            }
            try
            {
                ToolProfileSnapshot snapshot = ToolProfileCodec.decode(json);
                documentState = ProfileDocumentState.VALID;
                published.set(snapshot);
            }
            catch (UnsupportedToolProfileSchemaException e)
            {
                documentState = ProfileDocumentState.UNSUPPORTED_SCHEMA;
                Activator.logError("Tool profile document uses an unsupported schema; "
                    + "keeping the stored value and publishing a safe default", e); //$NON-NLS-1$
                published.set(DefaultToolProfileFactory.createSafeSnapshot());
            }
            catch (ToolProfileDocumentException e)
            {
                documentState = ProfileDocumentState.MALFORMED;
                backupCorruptDocument(json);
                Activator.logError("Tool profile document is malformed; "
                    + "keeping the stored value and publishing a safe default", e); //$NON-NLS-1$
                published.set(DefaultToolProfileFactory.createSafeSnapshot());
            }
        }
    }

    @Override
    public ToolProfileSnapshot getSnapshot()
    {
        return published.get();
    }

    @Override
    public ProfileDocumentState getDocumentState()
    {
        return documentState;
    }

    public boolean hasPersistedDocument()
    {
        return documentState == ProfileDocumentState.VALID;
    }

    @Override
    public ReplaceResult replaceAll(long expectedRevision, ToolProfileSnapshot next)
    {
        synchronized (writeLock)
        {
            ToolProfileSnapshot current = published.get();
            if (current.getDocumentRevision() != expectedRevision)
            {
                return ReplaceResult.conflict(current);
            }
            if (next == null)
            {
                return ReplaceResult.rejected(current, "Snapshot is required"); //$NON-NLS-1$
            }
            ToolProfileSnapshot assigned = assignRevisions(current, next);
            ToolProfileValidator.ValidationResult validation = ToolProfileValidator.validate(assigned);
            if (!validation.isValid())
            {
                return ReplaceResult.rejected(current, validation.firstError());
            }
            if (current.sameContent(assigned))
            {
                return ReplaceResult.noOp(current);
            }
            String encoded = ToolProfileCodec.encode(assigned);
            if (store != null)
            {
                store.setValue(PreferenceConstants.PREF_TOOL_PROFILES_JSON, encoded);
            }
            published.set(assigned);
            documentState = ProfileDocumentState.VALID;
            ToolProfileChangeSet changeSet = ToolProfileChangeSet.between(current, assigned);
            notifyListeners(current, assigned, changeSet);
            return ReplaceResult.accepted(assigned, changeSet);
        }
    }

    @Override
    public void addListener(Listener listener)
    {
        if (listener != null)
        {
            listeners.addIfAbsent(listener);
        }
    }

    @Override
    public void removeListener(Listener listener)
    {
        listeners.remove(listener);
    }

    private void notifyListeners(ToolProfileSnapshot previous, ToolProfileSnapshot current,
        ToolProfileChangeSet changeSet)
    {
        for (Listener listener : listeners)
        {
            try
            {
                listener.onProfilesChanged(previous, current, changeSet);
            }
            catch (RuntimeException e)
            {
                Activator.logError("Tool profile listener failed", e); //$NON-NLS-1$
            }
        }
    }

    private void backupCorruptDocument(String json)
    {
        if (store == null || json == null)
        {
            return;
        }
        store.setValue(PreferenceConstants.PREF_TOOL_PROFILES_BACKUP, json);
    }

    /**
     * Unchanged profiles keep their revision; changed or new ones get {@code old+1} or {@code 1}.
     * The document revision increments exactly once for a real change.
     */
    static ToolProfileSnapshot assignRevisions(ToolProfileSnapshot current, ToolProfileSnapshot next)
    {
        List<ToolProfile> assigned = new ArrayList<>();
        for (ToolProfile incoming : next.asList())
        {
            ToolProfile existing = current.get(incoming.getId());
            if (existing != null && existing.sameContent(incoming))
            {
                assigned.add(existing);
            }
            else if (existing != null)
            {
                assigned.add(incoming.withRevision(existing.getRevision() + 1L));
            }
            else
            {
                assigned.add(incoming.withRevision(1L));
            }
        }
        return ToolProfileSnapshot.of(current.getDocumentRevision() + 1L, assigned);
    }
}
