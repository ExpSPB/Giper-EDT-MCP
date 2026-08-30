/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server.preferences;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

import fm.giper.edt.mcp.server.profiles.DefaultToolProfileFactory;
import fm.giper.edt.mcp.server.profiles.ProfileToolPolicy;
import fm.giper.edt.mcp.server.profiles.ReplaceResult;
import fm.giper.edt.mcp.server.profiles.ToolProfile;
import fm.giper.edt.mcp.server.profiles.ToolProfileRepository;
import fm.giper.edt.mcp.server.profiles.ToolProfileSnapshot;
import fm.giper.edt.mcp.server.profiles.ToolProfileValidator;

/**
 * Headless editor state for the Tools preference tab: original revision, local
 * drafts, selected id and dirty flag. Apply publishes every draft with one
 * optimistic {@link ToolProfileRepository#replaceAll}.
 */
public final class ToolProfilesEditorModel
{
    public static final String MANDATORY_TOOL = ProfileToolPolicy.STATUS_TOOL;

    private long originalRevision;
    private final Map<String, ToolProfile> originals = new LinkedHashMap<>();
    private final Map<String, ToolProfile> drafts = new LinkedHashMap<>();
    private final Set<String> catalog;
    private String selectedId;
    private boolean dirty;

    private ToolProfilesEditorModel(Set<String> catalog)
    {
        this.catalog = catalog == null ? Set.of() : Set.copyOf(catalog);
    }

    public static ToolProfilesEditorModel load(ToolProfileSnapshot snapshot, Collection<String> catalog)
    {
        Set<String> tools = new HashSet<>();
        if (catalog != null)
        {
            tools.addAll(catalog);
        }
        ToolProfilesEditorModel model = new ToolProfilesEditorModel(tools);
        model.replaceLoaded(snapshot);
        return model;
    }

    public long getOriginalRevision()
    {
        return originalRevision;
    }

    public String getSelectedId()
    {
        return selectedId;
    }

    public ToolProfile getSelected()
    {
        return selectedId == null ? null : drafts.get(selectedId);
    }

    public List<ToolProfile> getDrafts()
    {
        List<ToolProfile> list = new ArrayList<>();
        ToolProfile fallback = drafts.get(ToolProfile.DEFAULT_ID);
        if (fallback != null)
        {
            list.add(fallback);
        }
        TreeMap<String, ToolProfile> rest = new TreeMap<>();
        for (ToolProfile profile : drafts.values())
        {
            if (!profile.isDefault())
            {
                rest.put(profile.getId(), profile);
            }
        }
        list.addAll(rest.values());
        return List.copyOf(list);
    }

    public boolean isDirty()
    {
        return dirty;
    }

    /**
     * Changes the selected draft. Does not persist the document.
     */
    public OperationResult select(String id)
    {
        if (id == null || !drafts.containsKey(id))
        {
            return OperationResult.failed("Unknown profile '" + id + "'"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        selectedId = id;
        return OperationResult.ok();
    }

    public boolean canDeleteSelected()
    {
        ToolProfile selected = getSelected();
        return selected != null && !selected.isDefault();
    }

    public boolean canDisableSelected()
    {
        return canDeleteSelected();
    }

    public boolean isMandatoryTool(String toolName)
    {
        return MANDATORY_TOOL.equals(toolName);
    }

    /**
     * Checkbox state for a catalog tool. {@link #MANDATORY_TOOL} is always shown enabled.
     */
    public boolean isToolChecked(String toolName)
    {
        if (isMandatoryTool(toolName))
        {
            return true;
        }
        ToolProfile selected = getSelected();
        return selected != null && selected.getAllowedTools().contains(toolName);
    }

    public Set<String> unknownAllowedTools()
    {
        ToolProfile selected = getSelected();
        if (selected == null)
        {
            return Set.of();
        }
        Set<String> unknown = new HashSet<>();
        for (String tool : selected.getAllowedTools())
        {
            if (!catalog.contains(tool))
            {
                unknown.add(tool);
            }
        }
        return Set.copyOf(unknown);
    }

    public OperationResult add(String id, String displayName, String description)
    {
        return add(id, displayName, description, ToolPreset.ALL_TOOLS);
    }

    /**
     * Creates a profile from a built-in preset. {@link ToolPreset#CUSTOM} is rejected.
     */
    public OperationResult add(String id, String displayName, String description, ToolPreset preset)
    {
        if (preset == null || preset == ToolPreset.CUSTOM)
        {
            return OperationResult.failed("Choose a built-in preset (All Tools, Analysis Only, Code Review or Development)"); //$NON-NLS-1$
        }
        String normalizedId = id == null ? "" : id.trim(); //$NON-NLS-1$
        if (drafts.containsKey(normalizedId))
        {
            return OperationResult.failed("Profile id '" + normalizedId + "' already exists"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        ToolProfile created = ToolProfile.builder()
            .id(normalizedId)
            .displayName(displayName)
            .description(description)
            .enabled(true)
            .allowedTools(preset.toAllowlist(catalog))
            .build();
        ToolProfileValidator.ValidationResult validation = ToolProfileValidator.validate(created);
        if (!validation.isValid())
        {
            return OperationResult.failed(validation.firstError());
        }
        drafts.put(created.getId(), created);
        selectedId = created.getId();
        dirty = true;
        return OperationResult.ok();
    }

    public OperationResult duplicate(String newId, String displayName)
    {
        ToolProfile selected = getSelected();
        if (selected == null)
        {
            return OperationResult.failed("No profile is selected"); //$NON-NLS-1$
        }
        String normalizedId = newId == null ? "" : newId.trim(); //$NON-NLS-1$
        if (drafts.containsKey(normalizedId))
        {
            return OperationResult.failed("Profile id '" + normalizedId + "' already exists"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        ToolProfile copy = ToolProfile.builder()
            .id(normalizedId)
            .displayName(displayName == null || displayName.isBlank()
                ? selected.getDisplayName() + " copy" : displayName) //$NON-NLS-1$
            .description(selected.getDescription())
            .enabled(true)
            .allowedTools(selected.getAllowedTools())
            .build();
        ToolProfileValidator.ValidationResult validation = ToolProfileValidator.validate(copy);
        if (!validation.isValid())
        {
            return OperationResult.failed(validation.firstError());
        }
        drafts.put(copy.getId(), copy);
        selectedId = copy.getId();
        dirty = true;
        return OperationResult.ok();
    }

    public OperationResult renameDisplayName(String displayName)
    {
        ToolProfile selected = getSelected();
        if (selected == null)
        {
            return OperationResult.failed("No profile is selected"); //$NON-NLS-1$
        }
        return replaceSelected(selected.toBuilder().displayName(displayName).build());
    }

    public OperationResult setDescription(String description)
    {
        ToolProfile selected = getSelected();
        if (selected == null)
        {
            return OperationResult.failed("No profile is selected"); //$NON-NLS-1$
        }
        return replaceSelected(selected.toBuilder().description(description).build());
    }

    public OperationResult setEnabled(boolean enabled)
    {
        ToolProfile selected = getSelected();
        if (selected == null)
        {
            return OperationResult.failed("No profile is selected"); //$NON-NLS-1$
        }
        if (selected.isDefault() && !enabled)
        {
            return OperationResult.failed("Profile 'default' cannot be disabled"); //$NON-NLS-1$
        }
        return replaceSelected(selected.toBuilder().enabled(enabled).build());
    }

    public OperationResult deleteSelected()
    {
        ToolProfile selected = getSelected();
        if (selected == null)
        {
            return OperationResult.failed("No profile is selected"); //$NON-NLS-1$
        }
        if (selected.isDefault())
        {
            return OperationResult.failed("Profile 'default' cannot be deleted"); //$NON-NLS-1$
        }
        drafts.remove(selected.getId());
        selectedId = ToolProfile.DEFAULT_ID;
        dirty = true;
        return OperationResult.ok();
    }

    /**
     * Applies a named preset to the selected draft. Unknown tool names already on the
     * allowlist are kept. {@link ToolPreset#CUSTOM} is ignored.
     */
    public OperationResult applyPreset(ToolPreset preset)
    {
        ToolProfile selected = getSelected();
        if (selected == null)
        {
            return OperationResult.failed("No profile is selected"); //$NON-NLS-1$
        }
        if (preset == null || preset == ToolPreset.CUSTOM)
        {
            return OperationResult.ok();
        }
        Set<String> allowed = new HashSet<>(preset.toAllowlist(catalog));
        allowed.addAll(unknownAllowedTools());
        return replaceSelected(selected.toBuilder().allowedTools(allowed).build());
    }

    /**
     * Reverts the selected draft to the snapshot loaded at construction or last
     * successful apply. A draft that was never published is removed.
     */
    public OperationResult restoreSelected()
    {
        ToolProfile selected = getSelected();
        if (selected == null)
        {
            return OperationResult.failed("No profile is selected"); //$NON-NLS-1$
        }
        String id = selected.getId();
        ToolProfile original = originals.get(id);
        if (original == null)
        {
            drafts.remove(id);
            selectedId = ToolProfile.DEFAULT_ID;
            dirty = !sameAsOriginals();
            return OperationResult.ok();
        }
        drafts.put(id, original);
        dirty = !sameAsOriginals();
        return OperationResult.ok();
    }

    /**
     * Resets only the selected profile allowlist to the shipped default surface.
     * Other drafts are left untouched.
     */
    public OperationResult resetSelectedToShippedDefaults()
    {
        ToolProfile selected = getSelected();
        if (selected == null)
        {
            return OperationResult.failed("No profile is selected"); //$NON-NLS-1$
        }
        Set<String> allowed = new HashSet<>(catalog);
        allowed.removeAll(ToolSettingsService.parseDisabledTools(PreferenceConstants.DEFAULT_DISABLED_TOOLS));
        allowed.addAll(unknownAllowedTools());
        return replaceSelected(selected.toBuilder()
            .enabled(true)
            .allowedTools(allowed)
            .build());
    }

    public OperationResult setToolEnabled(String toolName, boolean enabled)
    {
        if (toolName == null || toolName.isBlank())
        {
            return OperationResult.failed("Tool name is required"); //$NON-NLS-1$
        }
        if (isMandatoryTool(toolName) && !enabled)
        {
            return OperationResult.ok();
        }
        ToolProfile selected = getSelected();
        if (selected == null)
        {
            return OperationResult.failed("No profile is selected"); //$NON-NLS-1$
        }
        Set<String> allowed = new HashSet<>(selected.getAllowedTools());
        if (enabled)
        {
            allowed.add(toolName);
        }
        else
        {
            allowed.remove(toolName);
        }
        return replaceSelected(selected.toBuilder().allowedTools(allowed).build());
    }

    public OperationResult setGroupEnabled(Collection<String> toolNames, boolean enabled)
    {
        if (toolNames == null)
        {
            return OperationResult.ok();
        }
        for (String toolName : toolNames)
        {
            OperationResult result = setToolEnabled(toolName, enabled);
            if (!result.isOk())
            {
                return result;
            }
        }
        return OperationResult.ok();
    }

    public ToolPreset matchPreset()
    {
        ToolProfile selected = getSelected();
        if (selected == null)
        {
            return ToolPreset.CUSTOM;
        }
        return ToolPreset.matchAllowlist(selected.getAllowedTools(), catalog);
    }

    /**
     * Catalog tools treated as disabled in the tree (mandatory tool is never disabled).
     */
    public Set<String> disabledCatalogTools()
    {
        ToolProfile selected = getSelected();
        Set<String> disabled = new HashSet<>(catalog);
        if (selected != null)
        {
            disabled.removeAll(selected.getAllowedTools());
        }
        disabled.remove(MANDATORY_TOOL);
        return disabled;
    }

    public String profileEndpointPath(String profileId)
    {
        if (profileId == null || profileId.isBlank() || ToolProfile.DEFAULT_ID.equals(profileId))
        {
            return "/mcp"; //$NON-NLS-1$
        }
        return "/mcp/profiles/" + profileId; //$NON-NLS-1$
    }

    /**
     * One optimistic replace of every draft. On accept/no-op the model reloads from
     * the published snapshot. On conflict drafts stay dirty so the page can offer reload.
     */
    public ReplaceResult apply(ToolProfileRepository repository)
    {
        Objects.requireNonNull(repository, "repository"); //$NON-NLS-1$
        ToolProfileSnapshot next = ToolProfileSnapshot.of(originalRevision, drafts.values());
        ToolProfileValidator.ValidationResult validation = ToolProfileValidator.validate(next);
        if (!validation.isValid())
        {
            return ReplaceResult.rejected(repository.getSnapshot(), validation.firstError());
        }
        ReplaceResult result = repository.replaceAll(originalRevision, next);
        if (result.getStatus() == ReplaceResult.Status.ACCEPTED
            || result.getStatus() == ReplaceResult.Status.NO_OP)
        {
            replaceLoaded(result.getSnapshot());
        }
        return result;
    }

    /**
     * Discards local drafts and reloads from a published snapshot (conflict recovery).
     */
    public void reload(ToolProfileSnapshot snapshot)
    {
        replaceLoaded(snapshot);
    }

    public static Set<String> catalogFromGroups()
    {
        Set<String> tools = new HashSet<>();
        for (ToolGroup group : ToolGroup.values())
        {
            tools.addAll(group.getToolNames());
        }
        return Set.copyOf(tools);
    }

    private OperationResult replaceSelected(ToolProfile next)
    {
        ToolProfileValidator.ValidationResult validation = ToolProfileValidator.validate(next);
        if (!validation.isValid())
        {
            return OperationResult.failed(validation.firstError());
        }
        drafts.put(next.getId(), next);
        dirty = !sameAsOriginals();
        return OperationResult.ok();
    }

    private void replaceLoaded(ToolProfileSnapshot snapshot)
    {
        ToolProfileSnapshot source = snapshot == null
            ? DefaultToolProfileFactory.createSafeSnapshot()
            : snapshot;
        originals.clear();
        drafts.clear();
        originalRevision = source.getDocumentRevision();
        for (ToolProfile profile : source.asList())
        {
            originals.put(profile.getId(), profile);
            drafts.put(profile.getId(), profile);
        }
        if (!drafts.containsKey(ToolProfile.DEFAULT_ID))
        {
            ToolProfile fallback = DefaultToolProfileFactory.createSafeDefault();
            originals.put(fallback.getId(), fallback);
            drafts.put(fallback.getId(), fallback);
        }
        if (selectedId == null || !drafts.containsKey(selectedId))
        {
            selectedId = ToolProfile.DEFAULT_ID;
        }
        dirty = false;
    }

    private boolean sameAsOriginals()
    {
        if (originals.size() != drafts.size())
        {
            return false;
        }
        for (Map.Entry<String, ToolProfile> entry : drafts.entrySet())
        {
            ToolProfile original = originals.get(entry.getKey());
            if (original == null || !original.sameContent(entry.getValue()))
            {
                return false;
            }
        }
        return true;
    }

    /**
     * Outcome of a local editor mutation (not a repository publish).
     */
    public static final class OperationResult
    {
        private final boolean ok;
        private final String message;

        private OperationResult(boolean ok, String message)
        {
            this.ok = ok;
            this.message = message;
        }

        public static OperationResult ok()
        {
            return new OperationResult(true, null);
        }

        public static OperationResult failed(String message)
        {
            return new OperationResult(false, message);
        }

        public boolean isOk()
        {
            return ok;
        }

        public String getMessage()
        {
            return message;
        }
    }
}
