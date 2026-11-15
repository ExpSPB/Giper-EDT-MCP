/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server.profiles;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import fm.giper.edt.mcp.server.SseStreamRegistry;
import fm.giper.edt.mcp.server.Activator;
import fm.giper.edt.mcp.server.McpServer;
import fm.giper.edt.mcp.server.protocol.McpSessionRegistry;
import fm.giper.edt.mcp.server.tools.IMcpTool;
import fm.giper.edt.mcp.server.tools.McpToolRegistry;

/** Sends profile-scoped list-changed advisories when a published tools surface changes. */
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
        if (previous == null || current == null || changeSet == null || changeSet.isEmpty()
            || isInitialSafeDefault(previous))
        {
            return;
        }
        Collection<IMcpTool> tools = catalog.get();
        Set<String> paths = new LinkedHashSet<>(streams.activeRequestedPaths());
        McpSessionRegistry registry = liveSessions();
        if (registry != null)
        {
            paths.addAll(registry.activeRequestedPaths());
        }
        for (String id : changeSet.affectedIds())
        {
            paths.add(ToolProfile.DEFAULT_ID.equals(id)
                ? "/mcp" : "/mcp/profiles/" + id); //$NON-NLS-1$ //$NON-NLS-2$
        }
        for (String path : paths)
        {
            // /mcp is the legacy preference-backed surface. ToolsTab mirrors default-profile
            // edits there and emits its existing bounded notification from that write path.
            if ("/mcp".equals(path)) //$NON-NLS-1$
            {
                continue;
            }
            ProfileResolution before = resolve(path, previous);
            ProfileResolution after = resolve(path, current);
            if (before == null || after == null)
            {
                continue;
            }
            Set<String> oldNames = new ProfileToolPolicy(before, tools).effectiveToolNames();
            Set<String> newNames = new ProfileToolPolicy(after, tools).effectiveToolNames();
            boolean routeChanged = !Objects.equals(before.getEffectiveProfileId(), after.getEffectiveProfileId());
            boolean surfaceChanged = routeChanged || !oldNames.equals(newNames)
                || !Objects.equals(before.getFallbackReason(), after.getFallbackReason());
            if (!surfaceChanged)
            {
                continue;
            }
            if (routeChanged)
            {
                // An effective routing identity change invalidates the existing session.
                if (registry != null)
                {
                    registry.closeByRequestedPath(path);
                }
                streams.closeByRequestedPath(path);
            }
            else
            {
                // Allowlist changes take effect on the next request; keep established sessions alive.
                streams.notifyToolsListChangedAsync(path);
            }
        }
    }

    static boolean isInitialSafeDefault(ToolProfileSnapshot snapshot)
    {
        if (snapshot.getDocumentRevision() != 0L)
        {
            return false;
        }
        ToolProfile fallback = snapshot.getDefault();
        return fallback != null && fallback.getAllowedTools().isEmpty() && snapshot.asMap().size() == 1;
    }

    private static ProfileResolution resolve(String path, ToolProfileSnapshot snapshot)
    {
        if ("/mcp".equals(path)) //$NON-NLS-1$
        {
            return ProfileResolver.resolve(ToolProfile.DEFAULT_ID, true, snapshot);
        }
        String prefix = "/mcp/profiles/"; //$NON-NLS-1$
        if (path != null && path.startsWith(prefix))
        {
            String id = path.substring(prefix.length());
            if (ToolProfile.ID_PATTERN.matcher(id).matches())
            {
                return ProfileResolver.resolve(id, false, snapshot);
            }
        }
        return null;
    }

    private static McpSessionRegistry liveSessions()
    {
        Activator activator = Activator.getDefault();
        McpServer server = activator != null ? activator.getMcpServer() : null;
        return server != null ? server.getSessionRegistry() : null;
    }
}
