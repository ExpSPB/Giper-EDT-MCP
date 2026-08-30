/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server.profiles;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import fm.giper.edt.mcp.server.Activator;
import fm.giper.edt.mcp.server.SseStreamRegistry;
import fm.giper.edt.mcp.server.tools.IMcpTool;
import fm.giper.edt.mcp.server.tools.McpToolRegistry;
import fm.giper.edt.mcp.server.transport.InvalidMcpEndpointException;
import fm.giper.edt.mcp.server.transport.McpEndpoint;
import fm.giper.edt.mcp.server.transport.McpEndpointResolver;

/**
 * Pushes {@code notifications/tools/list_changed} to the SSE streams whose
 * effective published surface actually changed. Authorization always comes from
 * the new snapshot; a failed delivery cannot keep the old allowlist alive.
 */
public final class ProfileNotificationService implements ToolProfileRepository.Listener
{
    private final Supplier<Collection<IMcpTool>> catalog;
    private final SseStreamRegistry streams;

    public ProfileNotificationService()
    {
        this(() -> McpToolRegistry.getInstance().getAllTools(), SseStreamRegistry.getInstance());
    }

    ProfileNotificationService(Supplier<Collection<IMcpTool>> catalog, SseStreamRegistry streams)
    {
        this.catalog = catalog;
        this.streams = streams;
    }

    @Override
    public void onProfilesChanged(ToolProfileSnapshot previous, ToolProfileSnapshot current,
        ToolProfileChangeSet changeSet)
    {
        if (previous == null || current == null || changeSet == null || changeSet.isEmpty())
        {
            return;
        }
        Collection<IMcpTool> tools = catalog.get();
        for (String path : streams.activeRequestedPaths())
        {
            ProfileResolution before = resolve(path, previous);
            ProfileResolution after = resolve(path, current);
            if (before == null || after == null)
            {
                continue;
            }
            Set<String> oldNames = new ProfileToolPolicy(before, tools).effectiveToolNames();
            Set<String> newNames = new ProfileToolPolicy(after, tools).effectiveToolNames();
            boolean surfaceChanged = !oldNames.equals(newNames)
                || !before.getEffectiveProfileId().equals(after.getEffectiveProfileId())
                || !Objects.equals(before.getFallbackReason(), after.getFallbackReason());
            if (!surfaceChanged)
            {
                continue;
            }
            if (after.isFallbackApplied()
                && !Objects.equals(before.getFallbackReason(), after.getFallbackReason()))
            {
                Activator.logInfo("Profile fallback on " + path + ": requested '" //$NON-NLS-1$
                    + after.getRequestedProfileId() + "' is unavailable (" //$NON-NLS-1$
                    + after.getFallbackReason() + ")"); //$NON-NLS-1$
            }
            streams.notifyToolsListChanged(path);
        }
    }

    private static ProfileResolution resolve(String path, ToolProfileSnapshot snapshot)
    {
        try
        {
            McpEndpoint endpoint = McpEndpointResolver.resolve(path);
            return ProfileResolver.resolve(endpoint.getRequestedProfileId(), endpoint.isLegacy(), snapshot);
        }
        catch (InvalidMcpEndpointException e)
        {
            return null;
        }
    }
}
