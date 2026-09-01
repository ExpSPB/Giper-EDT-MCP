/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server.profiles;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Immutable published document: schema version, document revision and every profile.
 * Readers always see a complete old or complete new snapshot.
 */
public final class ToolProfileSnapshot
{
    public static final int CURRENT_SCHEMA_VERSION = 1;

    private final int schemaVersion;
    private final long documentRevision;
    private final List<ToolProfile> listed;
    private final Map<String, ToolProfile> profiles;

    private ToolProfileSnapshot(int schemaVersion, long documentRevision, List<ToolProfile> listed,
        Map<String, ToolProfile> profiles)
    {
        this.schemaVersion = schemaVersion;
        this.documentRevision = documentRevision;
        this.listed = listed;
        this.profiles = profiles;
    }

    public static ToolProfileSnapshot of(long documentRevision, Collection<ToolProfile> profiles)
    {
        return of(CURRENT_SCHEMA_VERSION, documentRevision, profiles);
    }

    public static ToolProfileSnapshot of(int schemaVersion, long documentRevision,
        Collection<ToolProfile> profiles)
    {
        List<ToolProfile> listed = new ArrayList<>();
        TreeMap<String, ToolProfile> sorted = new TreeMap<>();
        if (profiles != null)
        {
            for (ToolProfile profile : profiles)
            {
                if (profile == null || profile.getId() == null)
                {
                    continue;
                }
                listed.add(profile);
                sorted.put(profile.getId(), profile);
            }
        }
        return new ToolProfileSnapshot(schemaVersion, documentRevision, List.copyOf(listed),
            Collections.unmodifiableMap(new LinkedHashMap<>(sorted)));
    }

    public int getSchemaVersion()
    {
        return schemaVersion;
    }

    public long getDocumentRevision()
    {
        return documentRevision;
    }

    public Map<String, ToolProfile> asMap()
    {
        return profiles;
    }

    public List<ToolProfile> asList()
    {
        return listed;
    }

    public ToolProfile get(String id)
    {
        return id == null ? null : profiles.get(id);
    }

    public ToolProfile getDefault()
    {
        return profiles.get(ToolProfile.DEFAULT_ID);
    }

    public boolean contains(String id)
    {
        return id != null && profiles.containsKey(id);
    }

    public List<ToolProfile> enabledProfiles()
    {
        List<ToolProfile> enabled = new ArrayList<>();
        for (ToolProfile profile : profiles.values())
        {
            if (profile.isEnabled())
            {
                enabled.add(profile);
            }
        }
        return List.copyOf(enabled);
    }

    public ToolProfileSnapshot withDocumentRevision(long revision)
    {
        if (revision == documentRevision)
        {
            return this;
        }
        return new ToolProfileSnapshot(schemaVersion, revision, listed, profiles);
    }

    /**
     * Content equality that ignores {@link #documentRevision} and per-profile revisions.
     */
    public boolean sameContent(ToolProfileSnapshot other)
    {
        if (other == null || profiles.size() != other.profiles.size())
        {
            return false;
        }
        for (Map.Entry<String, ToolProfile> entry : profiles.entrySet())
        {
            ToolProfile theirs = other.profiles.get(entry.getKey());
            if (theirs == null || !entry.getValue().sameContent(theirs))
            {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
        {
            return true;
        }
        if (!(obj instanceof ToolProfileSnapshot))
        {
            return false;
        }
        ToolProfileSnapshot other = (ToolProfileSnapshot) obj;
        return schemaVersion == other.schemaVersion
            && documentRevision == other.documentRevision
            && listed.equals(other.listed);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(schemaVersion, documentRevision, listed);
    }

    @Override
    public String toString()
    {
        return "ToolProfileSnapshot[rev=" + documentRevision + ", profiles=" + profiles.keySet() + "]"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }
}
