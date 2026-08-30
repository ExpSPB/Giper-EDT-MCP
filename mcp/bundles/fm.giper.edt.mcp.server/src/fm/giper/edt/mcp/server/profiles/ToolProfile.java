/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server.profiles;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Immutable named allowlist that backs one MCP profile endpoint.
 */
public final class ToolProfile
{
    /** Reserved identifier of the fallback / compatibility profile. */
    public static final String DEFAULT_ID = "default"; //$NON-NLS-1$

    /** URL-safe profile identifier: {@code [a-z0-9][a-z0-9_-]{0,63}}. */
    public static final Pattern ID_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9_-]{0,63}$"); //$NON-NLS-1$

    private final String id;
    private final String displayName;
    private final String description;
    private final boolean enabled;
    private final Set<String> allowedTools;
    private final long revision;

    private ToolProfile(String id, String displayName, String description, boolean enabled,
        Set<String> allowedTools, long revision)
    {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.enabled = enabled;
        this.allowedTools = allowedTools;
        this.revision = revision;
    }

    public String getId()
    {
        return id;
    }

    public String getDisplayName()
    {
        return displayName;
    }

    public String getDescription()
    {
        return description;
    }

    public boolean isEnabled()
    {
        return enabled;
    }

    /**
     * Sorted, de-duplicated allowlist. Unknown tool names are kept as-is.
     */
    public Set<String> getAllowedTools()
    {
        return allowedTools;
    }

    public long getRevision()
    {
        return revision;
    }

    public boolean isDefault()
    {
        return DEFAULT_ID.equals(id);
    }

    /**
     * Content equality that ignores {@link #revision} so a no-op save can be detected.
     */
    public boolean sameContent(ToolProfile other)
    {
        if (other == null)
        {
            return false;
        }
        return Objects.equals(id, other.id)
            && Objects.equals(displayName, other.displayName)
            && Objects.equals(description, other.description)
            && enabled == other.enabled
            && allowedTools.equals(other.allowedTools);
    }

    public ToolProfile withRevision(long newRevision)
    {
        if (newRevision == revision)
        {
            return this;
        }
        return new ToolProfile(id, displayName, description, enabled, allowedTools, newRevision);
    }

    public Builder toBuilder()
    {
        return builder()
            .id(id)
            .displayName(displayName)
            .description(description)
            .enabled(enabled)
            .allowedTools(allowedTools)
            .revision(revision);
    }

    public static Builder builder()
    {
        return new Builder();
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
        {
            return true;
        }
        if (!(obj instanceof ToolProfile))
        {
            return false;
        }
        ToolProfile other = (ToolProfile) obj;
        return revision == other.revision && sameContent(other);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(id, displayName, description, enabled, allowedTools, revision);
    }

    @Override
    public String toString()
    {
        return "ToolProfile[" + id + ", rev=" + revision + ", tools=" + allowedTools.size() + "]"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
    }

    /**
     * Canonicalises the allowlist: blank names dropped, remaining names unique and sorted.
     */
    static Set<String> canonicalizeTools(Collection<String> tools)
    {
        if (tools == null || tools.isEmpty())
        {
            return Set.of();
        }
        TreeSet<String> sorted = new TreeSet<>();
        for (String tool : tools)
        {
            if (tool == null)
            {
                continue;
            }
            String trimmed = tool.trim();
            if (!trimmed.isEmpty())
            {
                sorted.add(trimmed);
            }
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(sorted));
    }

    public static final class Builder
    {
        private String id;
        private String displayName = ""; //$NON-NLS-1$
        private String description = ""; //$NON-NLS-1$
        private boolean enabled = true;
        private Collection<String> allowedTools = Set.of();
        private long revision = 1L;

        public Builder id(String id)
        {
            this.id = id == null ? null : id.trim();
            return this;
        }

        public Builder displayName(String displayName)
        {
            this.displayName = displayName == null ? "" : displayName.trim(); //$NON-NLS-1$
            return this;
        }

        public Builder description(String description)
        {
            this.description = description == null ? "" : description; //$NON-NLS-1$
            return this;
        }

        public Builder enabled(boolean enabled)
        {
            this.enabled = enabled;
            return this;
        }

        public Builder allowedTools(Collection<String> allowedTools)
        {
            this.allowedTools = allowedTools;
            return this;
        }

        public Builder revision(long revision)
        {
            this.revision = revision;
            return this;
        }

        public ToolProfile build()
        {
            return new ToolProfile(id, displayName, description, enabled,
                canonicalizeTools(allowedTools), revision);
        }
    }
}
