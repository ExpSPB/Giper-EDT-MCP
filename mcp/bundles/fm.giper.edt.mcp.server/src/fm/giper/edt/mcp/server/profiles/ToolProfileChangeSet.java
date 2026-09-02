/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server.profiles;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Diff between two published snapshots: which profile IDs changed, appeared,
 * disappeared, or flipped enabled/disabled.
 */
public final class ToolProfileChangeSet
{
    private final Set<String> createdIds;
    private final Set<String> changedIds;
    private final Set<String> enabledIds;
    private final Set<String> disabledIds;
    private final Set<String> deletedIds;

    public ToolProfileChangeSet(Set<String> createdIds, Set<String> changedIds, Set<String> enabledIds,
        Set<String> disabledIds, Set<String> deletedIds)
    {
        this.createdIds = copySorted(createdIds);
        this.changedIds = copySorted(changedIds);
        this.enabledIds = copySorted(enabledIds);
        this.disabledIds = copySorted(disabledIds);
        this.deletedIds = copySorted(deletedIds);
    }

    public static ToolProfileChangeSet empty()
    {
        return new ToolProfileChangeSet(Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
    }

    public static ToolProfileChangeSet between(ToolProfileSnapshot previous, ToolProfileSnapshot next)
    {
        if (previous == null || next == null)
        {
            return empty();
        }
        Set<String> created = new TreeSet<>();
        Set<String> changed = new TreeSet<>();
        Set<String> enabled = new TreeSet<>();
        Set<String> disabled = new TreeSet<>();
        Set<String> deleted = new TreeSet<>();

        for (ToolProfile after : next.asList())
        {
            ToolProfile before = previous.get(after.getId());
            if (before == null)
            {
                created.add(after.getId());
                if (after.isEnabled())
                {
                    enabled.add(after.getId());
                }
                continue;
            }
            if (!before.sameContent(after))
            {
                changed.add(after.getId());
            }
            if (!before.isEnabled() && after.isEnabled())
            {
                enabled.add(after.getId());
            }
            else if (before.isEnabled() && !after.isEnabled())
            {
                disabled.add(after.getId());
            }
        }
        for (ToolProfile before : previous.asList())
        {
            if (!next.contains(before.getId()))
            {
                deleted.add(before.getId());
            }
        }
        return new ToolProfileChangeSet(created, changed, enabled, disabled, deleted);
    }

    public boolean isEmpty()
    {
        return createdIds.isEmpty()
            && changedIds.isEmpty()
            && enabledIds.isEmpty()
            && disabledIds.isEmpty()
            && deletedIds.isEmpty();
    }

    public Set<String> getCreatedIds()
    {
        return createdIds;
    }

    public Set<String> getChangedIds()
    {
        return changedIds;
    }

    public Set<String> getEnabledIds()
    {
        return enabledIds;
    }

    public Set<String> getDisabledIds()
    {
        return disabledIds;
    }

    public Set<String> getDeletedIds()
    {
        return deletedIds;
    }

    public Set<String> affectedIds()
    {
        Set<String> all = new TreeSet<>();
        all.addAll(createdIds);
        all.addAll(changedIds);
        all.addAll(enabledIds);
        all.addAll(disabledIds);
        all.addAll(deletedIds);
        return Collections.unmodifiableSet(new LinkedHashSet<>(all));
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
        {
            return true;
        }
        if (!(obj instanceof ToolProfileChangeSet))
        {
            return false;
        }
        ToolProfileChangeSet other = (ToolProfileChangeSet) obj;
        return createdIds.equals(other.createdIds)
            && changedIds.equals(other.changedIds)
            && enabledIds.equals(other.enabledIds)
            && disabledIds.equals(other.disabledIds)
            && deletedIds.equals(other.deletedIds);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(createdIds, changedIds, enabledIds, disabledIds, deletedIds);
    }

    private static Set<String> copySorted(Set<String> ids)
    {
        if (ids == null || ids.isEmpty())
        {
            return Set.of();
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(new TreeSet<>(ids)));
    }
}
