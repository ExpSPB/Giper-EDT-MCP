/**
 * MCP Server for EDT - Tests
 * Copyright (C) 2026 ExpSPB
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

public class SseProfileNotificationTest
{
    @Test
    public void notificationReachesOnlyStreamsOnRequestedProfile()
    {
        SseStreamRegistry registry = SseStreamRegistry.getInstance();
        ByteArrayOutputStream selectedSink = new ByteArrayOutputStream();
        ByteArrayOutputStream otherSink = new ByteArrayOutputStream();
        SseStreamRegistry.SseStream selected = registry.register(selectedSink,
            "/mcp/profiles/sse_notify_test"); //$NON-NLS-1$
        SseStreamRegistry.SseStream other = registry.register(otherSink,
            "/mcp/profiles/sse_other_test"); //$NON-NLS-1$
        try
        {
            assertEquals(1, registry.notifyToolsListChanged("/mcp/profiles/sse_notify_test")); //$NON-NLS-1$
            assertTrue(selectedSink.toString(StandardCharsets.UTF_8).contains("notifications/tools/list_changed")); //$NON-NLS-1$
            assertTrue(otherSink.toString(StandardCharsets.UTF_8).isEmpty());
            assertEquals("/mcp/profiles/sse_notify_test", selected.getRequestedPath()); //$NON-NLS-1$
        }
        finally
        {
            registry.unregister(selected);
            registry.unregister(other);
        }
    }
}
