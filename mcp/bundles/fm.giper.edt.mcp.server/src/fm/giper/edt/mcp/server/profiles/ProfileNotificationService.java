/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server.profiles;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import fm.giper.edt.mcp.server.Activator;
import fm.giper.edt.mcp.server.McpServer;
import fm.giper.edt.mcp.server.SseStreamRegistry;
import fm.giper.edt.mcp.server.tools.IMcpTool;
import fm.giper.edt.mcp.server.tools.McpToolRegistry;
import fm.giper.edt.mcp.server.transport.InvalidMcpEndpointException;
import fm.giper.edt.mcp.server.transport.McpEndpoint;
import fm.giper.edt.mcp.server.transport.McpEndpointResolver;
import fm.giper.edt.mcp.server.transport.McpSessionRegistry;

/**
 * When a profile's published surface changes, closes only the sessions and SSE
 * streams of the affected path. Live {@code tools/list_changed} without a
 * reconnect is not used: the client must initialize again on that URL.
 * Changing {@code default} also kicks fallback clients and sessionless {@code /mcp}.
 */
public final class ProfileNotificationService implements ToolProfileRepository.Listener
{
    private final Supplier<Collection<IMcpTool>> catalog;
    private final SseStreamRegistry streams;
    private final Supplier<McpSessionRegistry> sessions;
    private final Consumer<Boolean> legacySessionRequired;

    public ProfileNotificationService()
    {
        this(() -> McpToolRegistry.getInstance().getAllTools(), SseStreamRegistry.getInstance(),
            ProfileNotificationService::liveSessions, ProfileNotificationService::markLegacySessionRequired);
    }

    ProfileNotificationService(Supplier<Collection<IMcpTool>> catalog, SseStreamRegistry streams)
    {
        this(catalog, streams, () -> null, required -> {
            // unit tests without a live McpServer
        });
    }

    ProfileNotificationService(Supplier<Collection<IMcpTool>> catalog, SseStreamRegistry streams,
        Supplier<McpSessionRegistry> sessions, Consumer<Boolean> legacySessionRequired)
    {
        this.catalog = catalog;
        this.streams = streams;
        this.sessions = sessions;
        this.legacySessionRequired = legacySessionRequired;
    }

    @Override
    public void onProfilesChanged(ToolProfileSnapshot previous, ToolProfileSnapshot current,
        ToolProfileChangeSet changeSet)
    {
        if (previous == null || current == null || changeSet == null || changeSet.isEmpty())
        {
            return;
        }
        if (isInitialSafeDefault(previous))
        {
            return;
        }
        Collection<IMcpTool> tools = catalog.get();
        Set<String> paths = new LinkedHashSet<>();
        paths.addAll(streams.activeRequestedPaths());
        McpSessionRegistry registry = sessions.get();
        if (registry != null)
        {
            paths.addAll(registry.activeRequestedPaths());
        }
        for (String id : changeSet.affectedIds())
        {
            if (ToolProfile.DEFAULT_ID.equals(id))
            {
                paths.add(McpEndpoint.LEGACY_PATH);
            }
            else
            {
                paths.add(McpEndpoint.PROFILES_PREFIX + id);
            }
        }
        for (String path : paths)
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
            if (McpEndpoint.LEGACY_PATH.equals(path))
            {
                // Close the admission window before the path kick: a sessionless
                // legacy GET that starts after this point must initialize again.
                legacySessionRequired.accept(Boolean.TRUE);
            }
            kick(path, registry);
        }
    }

    private void kick(String path, McpSessionRegistry registry)
    {
        if (registry != null)
        {
            registry.closeByRequestedPath(path);
        }
        streams.closeByRequestedPath(path);
    }

    /**
     * The in-memory conservative {@code default} published before the first persist
     * (empty allowlist, document revision 0). Treating that transition as a live
     * surface change would kick sessionless {@code /mcp} on every upgrade.
     */
    static boolean isInitialSafeDefault(ToolProfileSnapshot snapshot)
    {
        if (snapshot == null || snapshot.getDocumentRevision() != 0L)
        {
            return false;
        }
        ToolProfile fallback = snapshot.getDefault();
        return fallback != null && fallback.getAllowedTools().isEmpty() && snapshot.asMap().size() == 1;
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

    private static McpSessionRegistry liveSessions()
    {
        Activator activator = Activator.getDefault();
        McpServer server = activator != null ? activator.getMcpServer() : null;
        return server != null ? server.getSessionRegistry() : null;
    }

    private static void markLegacySessionRequired(boolean required)
    {
        Activator activator = Activator.getDefault();
        McpServer server = activator != null ? activator.getMcpServer() : null;
        if (server != null)
        {
            server.setLegacySessionRequired(required);
        }
    }
}
