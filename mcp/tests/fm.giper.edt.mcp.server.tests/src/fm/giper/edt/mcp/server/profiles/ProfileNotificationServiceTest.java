/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server.profiles;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import fm.giper.edt.mcp.server.SseStreamRegistry;
import fm.giper.edt.mcp.server.protocol.ClientCapabilities;
import fm.giper.edt.mcp.server.tools.IMcpTool;
import fm.giper.edt.mcp.server.transport.McpSessionRegistry;

/**
 * Apply kicks only the sessions/streams whose effective surface changed.
 */
public class ProfileNotificationServiceTest
{
    private SseStreamRegistry streams;
    private McpSessionRegistry sessions;
    private ProfileNotificationService service;
    private SseStreamRegistry.SseStream mcpStream;
    private SseStreamRegistry.SseStream reviewStream;
    private ByteArrayOutputStream mcpSink;
    private ByteArrayOutputStream reviewSink;
    private final AtomicBoolean legacyRequired = new AtomicBoolean(false);
    private final AtomicBoolean legacyArmedBeforeKick = new AtomicBoolean(false);

    @Before
    public void setUp()
    {
        streams = SseStreamRegistry.getInstance();
        sessions = new McpSessionRegistry();
        service = new ProfileNotificationService(ProfileNotificationServiceTest::catalog, streams,
            () -> sessions, required -> {
                if (required && streams.activeRequestedPaths().contains("/mcp")) //$NON-NLS-1$
                {
                    legacyArmedBeforeKick.set(true);
                }
                legacyRequired.set(required);
            });
        mcpSink = new ByteArrayOutputStream();
        reviewSink = new ByteArrayOutputStream();
        mcpStream = streams.register(mcpSink, "mcp-session", "/mcp"); //$NON-NLS-1$ //$NON-NLS-2$
        reviewStream = streams.register(reviewSink, "review-session", "/mcp/profiles/review"); //$NON-NLS-1$ //$NON-NLS-2$
        sessions.create("/mcp", "2025-11-25", ClientCapabilities.ABSENT); //$NON-NLS-1$ //$NON-NLS-2$
        sessions.create("/mcp/profiles/review", "2025-11-25", ClientCapabilities.ABSENT); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @After
    public void tearDown()
    {
        streams.unregister(mcpStream);
        streams.unregister(reviewStream);
        sessions.shutdown();
        legacyRequired.set(false);
        legacyArmedBeforeKick.set(false);
    }

    @Test
    public void emptyChangeSetDoesNotKick()
    {
        ToolProfileSnapshot snapshot = snapshot(defaultProfile(Set.of("list_projects")), //$NON-NLS-1$
            reviewProfile(true, Set.of("write_module_source"))); //$NON-NLS-1$
        service.onProfilesChanged(snapshot, snapshot, ToolProfileChangeSet.empty());
        assertTrue(streams.activeRequestedPaths().contains("/mcp")); //$NON-NLS-1$
        assertTrue(streams.activeRequestedPaths().contains("/mcp/profiles/review")); //$NON-NLS-1$
        assertEquals(2, sessions.size());
        assertFalse(legacyRequired.get());
    }

    @Test
    public void displayNameOnlyChangeDoesNotKick()
    {
        ToolProfileSnapshot previous = snapshot(defaultProfile(Set.of("list_projects")), //$NON-NLS-1$
            reviewProfile(true, Set.of("write_module_source"))); //$NON-NLS-1$
        ToolProfileSnapshot next = snapshot(defaultProfile(Set.of("list_projects")), //$NON-NLS-1$
            ToolProfile.builder().id("review").displayName("Review 2") //$NON-NLS-1$ //$NON-NLS-2$
                .allowedTools(Set.of("write_module_source")).build()); //$NON-NLS-1$
        service.onProfilesChanged(previous, next, ToolProfileChangeSet.between(previous, next));
        assertTrue(streams.activeRequestedPaths().contains("/mcp")); //$NON-NLS-1$
        assertTrue(streams.activeRequestedPaths().contains("/mcp/profiles/review")); //$NON-NLS-1$
        assertEquals(2, sessions.size());
        assertFalse(legacyRequired.get());
    }

    @Test
    public void reviewAllowlistChangeKicksOnlyThatPath()
    {
        ToolProfileSnapshot previous = snapshot(defaultProfile(Set.of("list_projects")), //$NON-NLS-1$
            reviewProfile(true, Set.of("write_module_source"))); //$NON-NLS-1$
        ToolProfileSnapshot next = snapshot(defaultProfile(Set.of("list_projects")), //$NON-NLS-1$
            reviewProfile(true, Set.of("write_module_source", "list_projects"))); //$NON-NLS-1$ //$NON-NLS-2$
        service.onProfilesChanged(previous, next, ToolProfileChangeSet.between(previous, next));
        assertTrue(streams.activeRequestedPaths().contains("/mcp")); //$NON-NLS-1$
        assertFalse(streams.activeRequestedPaths().contains("/mcp/profiles/review")); //$NON-NLS-1$
        assertEquals(1, sessions.size());
        assertTrue(sessions.activeRequestedPaths().contains("/mcp")); //$NON-NLS-1$
        assertFalse(legacyRequired.get());
    }

    @Test
    public void defaultChangeKicksLegacyAndFallbackPaths()
    {
        ToolProfileSnapshot previous = snapshot(defaultProfile(Set.of("list_projects")), //$NON-NLS-1$
            reviewProfile(true, Set.of("write_module_source"))); //$NON-NLS-1$
        ToolProfileSnapshot next = snapshot(defaultProfile(Set.of("list_projects", "write_module_source")), //$NON-NLS-1$ //$NON-NLS-2$
            reviewProfile(true, Set.of("write_module_source"))); //$NON-NLS-1$
        ByteArrayOutputStream missingSink = new ByteArrayOutputStream();
        SseStreamRegistry.SseStream missing = streams.register(missingSink, "missing-session", //$NON-NLS-1$
            "/mcp/profiles/zz-missing"); //$NON-NLS-1$
        sessions.create("/mcp/profiles/zz-missing", "2025-11-25", ClientCapabilities.ABSENT); //$NON-NLS-1$ //$NON-NLS-2$
        try
        {
            service.onProfilesChanged(previous, next, ToolProfileChangeSet.between(previous, next));
            assertFalse(streams.activeRequestedPaths().contains("/mcp")); //$NON-NLS-1$
            assertFalse(streams.activeRequestedPaths().contains("/mcp/profiles/zz-missing")); //$NON-NLS-1$
            assertTrue(streams.activeRequestedPaths().contains("/mcp/profiles/review")); //$NON-NLS-1$
            assertTrue(legacyRequired.get());
            assertTrue("legacy session requirement must be armed before /mcp streams are kicked", //$NON-NLS-1$
                legacyArmedBeforeKick.get());
            assertEquals(1, sessions.size());
            assertTrue(sessions.activeRequestedPaths().contains("/mcp/profiles/review")); //$NON-NLS-1$
        }
        finally
        {
            streams.unregister(missing);
        }
    }

    @Test
    public void firstDefaultPersistDoesNotDropLegacyStream()
    {
        ToolProfileSnapshot previous = DefaultToolProfileFactory.createSafeSnapshot();
        ToolProfileSnapshot next = snapshot(defaultProfile(Set.of("list_projects"))); //$NON-NLS-1$
        service.onProfilesChanged(previous, next, ToolProfileChangeSet.between(previous, next));
        assertTrue("first persist of default must keep sessionless /mcp", //$NON-NLS-1$
            streams.activeRequestedPaths().contains("/mcp")); //$NON-NLS-1$
        assertEquals(2, sessions.size());
        assertFalse("first persist must not arm the legacy session latch", legacyRequired.get()); //$NON-NLS-1$
    }

    @Test
    public void disableAndEnableReviewKickThatPath()
    {
        ToolProfileSnapshot enabled = snapshot(defaultProfile(Set.of("list_projects")), //$NON-NLS-1$
            reviewProfile(true, Set.of("write_module_source"))); //$NON-NLS-1$
        ToolProfileSnapshot disabled = snapshot(defaultProfile(Set.of("list_projects")), //$NON-NLS-1$
            reviewProfile(false, Set.of("write_module_source"))); //$NON-NLS-1$
        service.onProfilesChanged(enabled, disabled, ToolProfileChangeSet.between(enabled, disabled));
        assertFalse(streams.activeRequestedPaths().contains("/mcp/profiles/review")); //$NON-NLS-1$
        assertTrue(streams.activeRequestedPaths().contains("/mcp")); //$NON-NLS-1$
        assertFalse(legacyRequired.get());

        reviewStream = streams.register(reviewSink, "review-session-2", "/mcp/profiles/review"); //$NON-NLS-1$ //$NON-NLS-2$
        sessions.create("/mcp/profiles/review", "2025-11-25", ClientCapabilities.ABSENT); //$NON-NLS-1$ //$NON-NLS-2$
        service.onProfilesChanged(disabled, enabled, ToolProfileChangeSet.between(disabled, enabled));
        assertFalse(streams.activeRequestedPaths().contains("/mcp/profiles/review")); //$NON-NLS-1$
        assertTrue(streams.activeRequestedPaths().contains("/mcp")); //$NON-NLS-1$
    }

    private static ToolProfileSnapshot snapshot(ToolProfile... profiles)
    {
        return ToolProfileSnapshot.of(1L, List.of(profiles));
    }

    private static ToolProfile defaultProfile(Set<String> allowed)
    {
        return DefaultToolProfileFactory.createDefault(allowed);
    }

    private static ToolProfile reviewProfile(boolean enabled, Set<String> allowed)
    {
        return ToolProfile.builder()
            .id("review") //$NON-NLS-1$
            .displayName("Review") //$NON-NLS-1$
            .enabled(enabled)
            .allowedTools(allowed)
            .build();
    }

    private static List<IMcpTool> catalog()
    {
        return List.of(stub("get_server_status"), stub("list_projects"), //$NON-NLS-1$ //$NON-NLS-2$
            stub("write_module_source"), stub("enable_toolset")); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private static IMcpTool stub(String name)
    {
        return new IMcpTool()
        {
            @Override
            public String getName()
            {
                return name;
            }

            @Override
            public String getDescription()
            {
                return name;
            }

            @Override
            public String getInputSchema()
            {
                return "{\"type\":\"object\"}"; //$NON-NLS-1$
            }

            @Override
            public String execute(Map<String, String> params)
            {
                return "{}"; //$NON-NLS-1$
            }
        };
    }
}
