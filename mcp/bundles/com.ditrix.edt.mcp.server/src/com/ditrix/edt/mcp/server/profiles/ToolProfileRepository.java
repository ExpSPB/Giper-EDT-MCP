/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.profiles;

/**
 * Source of truth for published profile snapshots. Protocol, UI and proxy depend
 * on this contract, not on Eclipse preference keys.
 */
public interface ToolProfileRepository
{
    /**
     * Current published snapshot. Never {@code null}; readers always see a complete document.
     */
    ToolProfileSnapshot getSnapshot();

    /**
     * How the stored preference value was interpreted. {@link ProfileDocumentState#ABSENT}
     * means migration may still create the first document.
     */
    ProfileDocumentState getDocumentState();

    /**
     * Optimistic replace of the whole document.
     * <p>
     * Order: validate → canonical encode → one {@code setValue} → atomic publish → listeners.
     * A semantic no-op does not bump revision and does not notify. A revision conflict
     * leaves the published snapshot untouched.
     */
    ReplaceResult replaceAll(long expectedRevision, ToolProfileSnapshot next);

    void addListener(Listener listener);

    void removeListener(Listener listener);

    @FunctionalInterface
    interface Listener
    {
        void onProfilesChanged(ToolProfileSnapshot previous, ToolProfileSnapshot current,
            ToolProfileChangeSet changeSet);
    }
}
