/**
 * MCP Server for EDT - Tests
 * Copyright (C) 2026 ExpSPB
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server.profiles;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

import fm.giper.edt.mcp.server.SseStreamRegistry;
import fm.giper.edt.mcp.server.tools.IMcpTool;

public class ProfileNotificationServiceTest
{
    @Test
    public void allowlistChangeNotifiesOnlyTheAffectedProfileAndKeepsStreamsOpen() throws Exception
    {
        SseStreamRegistry registry = SseStreamRegistry.getInstance();
        ByteArrayOutputStream reviewSink = new ByteArrayOutputStream();
        ByteArrayOutputStream writerSink = new ByteArrayOutputStream();
        SseStreamRegistry.SseStream review = registry.register(reviewSink, "/mcp/profiles/review"); //$NON-NLS-1$
        SseStreamRegistry.SseStream writer = registry.register(writerSink, "/mcp/profiles/writer"); //$NON-NLS-1$
        try
        {
            ToolProfileSnapshot previous = snapshot(Set.of("list_projects"), Set.of("list_projects")); //$NON-NLS-1$
            ToolProfileSnapshot current = snapshot(Set.of("write_module_source"), Set.of("list_projects")); //$NON-NLS-1$
            ProfileNotificationService service = new ProfileNotificationService(
                () -> List.of(stub("get_server_status"), stub("list_projects"), stub("write_module_source")), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                registry);

            service.onProfilesChanged(previous, current, ToolProfileChangeSet.between(previous, current));
            awaitNotification(reviewSink);

            assertTrue(reviewSink.toString(StandardCharsets.UTF_8).contains("notifications/tools/list_changed")); //$NON-NLS-1$
            assertTrue(writerSink.toString(StandardCharsets.UTF_8).isEmpty());
            assertTrue(registry.activeRequestedPaths().contains("/mcp/profiles/review")); //$NON-NLS-1$
            assertTrue(registry.activeRequestedPaths().contains("/mcp/profiles/writer")); //$NON-NLS-1$
        }
        finally
        {
            registry.unregister(review);
            registry.unregister(writer);
        }
    }

    @Test
    public void effectiveIdChangeClosesStreamsToTheChangedProfile()
    {
        SseStreamRegistry registry = SseStreamRegistry.getInstance();
        SseStreamRegistry.SseStream review = registry.register(new ByteArrayOutputStream(), //$NON-NLS-1$
            "/mcp/profiles/route_change_test"); //$NON-NLS-1$
        try
        {
            ToolProfileSnapshot previous = ToolProfileSnapshot.of(2L, List.of(
                profile("default", true, Set.of("list_projects")), //$NON-NLS-1$
                profile("route_change_test", true, Set.of("list_projects")))); //$NON-NLS-1$
            ToolProfileSnapshot current = ToolProfileSnapshot.of(3L, List.of(
                profile("default", true, Set.of("list_projects")), //$NON-NLS-1$
                profile("route_change_test", false, Set.of("list_projects")))); //$NON-NLS-1$
            ProfileNotificationService service = new ProfileNotificationService(
                () -> List.of(stub("get_server_status"), stub("list_projects")), registry); //$NON-NLS-1$

            service.onProfilesChanged(previous, current, ToolProfileChangeSet.between(previous, current));

            assertFalse(registry.activeRequestedPaths().contains("/mcp/profiles/route_change_test")); //$NON-NLS-1$
        }
        finally
        {
            registry.unregister(review);
        }
    }

    private static void awaitNotification(ByteArrayOutputStream sink) throws InterruptedException
    {
        long deadline = System.nanoTime() + 2_000_000_000L;
        while (sink.size() == 0 && System.nanoTime() < deadline)
        {
            Thread.sleep(5);
        }
    }

    private static ToolProfileSnapshot snapshot(Set<String> review, Set<String> writer)
    {
        return ToolProfileSnapshot.of(1L, List.of(
            profile("default", true, Set.of("list_projects")), //$NON-NLS-1$
            profile("review", true, review), //$NON-NLS-1$
            profile("writer", true, writer))); //$NON-NLS-1$
    }

    private static ToolProfile profile(String id, boolean enabled, Set<String> names)
    {
        return ToolProfile.builder().id(id).displayName(id).enabled(enabled).allowedTools(names).build();
    }

    private static IMcpTool stub(String name)
    {
        return new IMcpTool()
        {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return name; }
            @Override public String getInputSchema() { return "{\"type\":\"object\"}"; } //$NON-NLS-1$
            @Override public String execute(Map<String, String> params) { return "{}"; } //$NON-NLS-1$
        };
    }
}
