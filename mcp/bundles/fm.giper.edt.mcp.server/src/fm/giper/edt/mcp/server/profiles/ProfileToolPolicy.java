/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server.profiles;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Set;

import fm.giper.edt.mcp.server.tools.IMcpTool;
import fm.giper.edt.mcp.server.tools.McpToolRegistry;
import fm.giper.edt.mcp.server.tools.ToolsetState;
import fm.giper.edt.mcp.server.tools.Toolsets;
import fm.giper.edt.mcp.server.preferences.ToolPreset;
import fm.giper.edt.mcp.server.preferences.ToolSettingsService;

/**
 * Single authorization point for published tools, callable tools and guide resources.
 * {@code get_server_status} is always added; {@code enable_toolset} is hidden on explicit endpoints;
 * progressive disclosure applies only to the legacy {@code /mcp} surface.
 */
public final class ProfileToolPolicy
{
    public static final String STATUS_TOOL = "get_server_status"; //$NON-NLS-1$
    public static final String ENABLE_TOOLSET_TOOL = "enable_toolset"; //$NON-NLS-1$

    private final ProfileResolution resolution;
    private final Map<String, IMcpTool> catalog;
    private final ToolPreset launchPreset;

    public ProfileToolPolicy(ProfileResolution resolution, Collection<IMcpTool> catalog)
    {
        this(resolution, catalog, ToolSettingsService.getInstance().getSessionPreset());
    }

    /** Explicit launch decision keeps policy tests independent of process environment. */
    public ProfileToolPolicy(ProfileResolution resolution, Collection<IMcpTool> catalog, ToolPreset launchPreset)
    {
        this.resolution = resolution;
        this.launchPreset = launchPreset;
        TreeMap<String, IMcpTool> tools = new TreeMap<>();
        if (catalog != null)
        {
            for (IMcpTool tool : catalog)
            {
                if (tool != null && tool.getName() != null)
                {
                    tools.put(tool.getName(), tool);
                }
            }
        }
        this.catalog = Map.copyOf(tools);
    }

    public ProfileResolution getResolution()
    {
        return resolution;
    }

    public Set<String> effectiveToolNames()
    {
        Set<String> names = new TreeSet<>();
        if (resolution != null && resolution.getEffectiveProfile() != null)
        {
            names.addAll(resolution.getEffectiveProfile().getAllowedTools());
        }
        if (launchPreset != null && launchPreset.getDisabledTools() != null)
        {
            names.removeAll(launchPreset.getDisabledTools());
        }
        names.add(STATUS_TOOL);
        if (resolution != null && resolution.isExplicitEndpoint())
        {
            names.remove(ENABLE_TOOLSET_TOOL);
        }
        names.retainAll(catalog.keySet());
        if (catalog.containsKey(STATUS_TOOL))
        {
            names.add(STATUS_TOOL);
        }
        return Set.copyOf(names);
    }

    public List<IMcpTool> publishedTools(boolean progressiveDisclosure)
    {
        List<IMcpTool> published = new ArrayList<>();
        for (String name : effectiveToolNames())
        {
            IMcpTool tool = catalog.get(name);
            if (tool == null)
            {
                continue;
            }
            if (progressiveDisclosure && resolution != null && resolution.isLegacyEndpoint()
                && !ToolsetState.getInstance().isVisible(Toolsets.toolsetOf(name)))
            {
                continue;
            }
            published.add(tool);
        }
        published.sort(Comparator.comparing(IMcpTool::getName));
        return List.copyOf(published);
    }

    public boolean isCallable(String toolName)
    {
        if (toolName == null)
        {
            return false;
        }
        Set<String> allowed = effectiveToolNames();
        if (allowed.contains(toolName))
        {
            return true;
        }
        String canonical = McpToolRegistry.canonicalToolName(toolName);
        return canonical != null && allowed.contains(canonical);
    }

    public boolean isGuideVisible(String toolName)
    {
        return isCallable(toolName);
    }

    public String deniedMessage(String toolName)
    {
        String profileId = resolution == null ? ToolProfile.DEFAULT_ID : resolution.getEffectiveProfileId();
        if (ENABLE_TOOLSET_TOOL.equals(toolName) && resolution != null && resolution.isExplicitEndpoint())
        {
            return "Tool '" + toolName + "' is not supported on the explicit profile endpoint '" //$NON-NLS-1$ //$NON-NLS-2$
                + profileId + "'. Connect to /mcp for progressive disclosure, or enable the tool in " //$NON-NLS-1$
                + "EDT Preferences \u2192 MCP Server \u2192 Tools."; //$NON-NLS-1$
        }
        String canonical = McpToolRegistry.canonicalToolName(toolName);
        if (resolution != null && resolution.getEffectiveProfile() != null
            && resolution.getEffectiveProfile().getAllowedTools().contains(canonical)
            && launchPreset != null && launchPreset.getDisabledTools() != null
            && launchPreset.getDisabledTools().contains(canonical))
        {
            return "Tool '" + toolName + "' is disabled by launch preset '" + launchPreset.getId() //$NON-NLS-1$ //$NON-NLS-2$
                + "'. Restart EDT with a different EDT_MCP_TOOL_PRESET to use tools allowed by profile '" //$NON-NLS-1$
                + profileId + "'. Saved profile settings cannot override a launch restriction."; //$NON-NLS-1$
        }
        return "Tool '" + toolName + "' is not allowed by profile '" + profileId + "'. " //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            + "Ask the user to add it to that profile: EDT Preferences \u2192 MCP Server \u2192 Tools."; //$NON-NLS-1$
    }
}
