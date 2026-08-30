/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server.transport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;

import fm.giper.edt.mcp.server.protocol.ClientCapabilities;
import fm.giper.edt.mcp.server.transport.McpSessionRegistry.LookupStatus;

/**
 * Path binding, TTL, cap and DELETE/shutdown cleanup.
 */
public class McpSessionRegistryTest
{
    @Test
    public void createAndLookupSamePath()
    {
        McpSessionRegistry registry = new McpSessionRegistry();
        McpTransportSession session = registry.create("/mcp/profiles/review", "2025-11-25", //$NON-NLS-1$ //$NON-NLS-2$
            ClientCapabilities.ABSENT);
        assertNotNull(session);
        assertEquals(LookupStatus.OK,
            registry.lookup(session.getId(), "/mcp/profiles/review", true).getStatus()); //$NON-NLS-1$
    }

    @Test
    public void replayOnAnotherPathIsNotFound()
    {
        McpSessionRegistry registry = new McpSessionRegistry();
        McpTransportSession session = registry.create("/mcp/profiles/review", "2025-11-25", //$NON-NLS-1$ //$NON-NLS-2$
            ClientCapabilities.ABSENT);
        assertEquals(LookupStatus.PATH_MISMATCH,
            registry.lookup(session.getId(), "/mcp/profiles/dev", true).getStatus()); //$NON-NLS-1$
        assertEquals(1, registry.size());
    }

    @Test
    public void missingRequiredSessionAndUnknownId()
    {
        McpSessionRegistry registry = new McpSessionRegistry();
        assertEquals(LookupStatus.MISSING, registry.lookup(null, "/mcp/profiles/review", true).getStatus()); //$NON-NLS-1$
        assertEquals(LookupStatus.OK, registry.lookup(null, "/mcp", false).getStatus()); //$NON-NLS-1$
        assertEquals(LookupStatus.UNKNOWN, registry.lookup("no-such", "/mcp", true).getStatus()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void expiredSessionIsUnknown()
    {
        AtomicLong clock = new AtomicLong(1_000L);
        McpSessionRegistry registry = new McpSessionRegistry(10, 100L, clock::get);
        McpTransportSession session = registry.create("/mcp", "2025-11-25", ClientCapabilities.ABSENT); //$NON-NLS-1$ //$NON-NLS-2$
        clock.set(1_250L);
        assertEquals(LookupStatus.UNKNOWN, registry.lookup(session.getId(), "/mcp", true).getStatus()); //$NON-NLS-1$
        assertEquals(0, registry.size());
    }

    @Test
    public void capRefusesNewSessions()
    {
        McpSessionRegistry registry = new McpSessionRegistry(1, 60_000L, System::currentTimeMillis);
        assertNotNull(registry.create("/mcp", "2025-11-25", ClientCapabilities.ABSENT)); //$NON-NLS-1$ //$NON-NLS-2$
        assertNull(registry.create("/mcp/profiles/review", "2025-11-25", ClientCapabilities.ABSENT)); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void deleteAndShutdownReleaseSessions()
    {
        McpSessionRegistry registry = new McpSessionRegistry();
        McpTransportSession first = registry.create("/mcp", "2025-11-25", ClientCapabilities.ABSENT); //$NON-NLS-1$ //$NON-NLS-2$
        McpTransportSession second = registry.create("/mcp/profiles/review", "2025-11-25", //$NON-NLS-1$ //$NON-NLS-2$
            ClientCapabilities.ABSENT);
        assertTrue(registry.close(first.getId()));
        assertFalse(registry.close(first.getId()));
        assertEquals(1, registry.size());
        registry.shutdown();
        assertEquals(0, registry.size());
        assertEquals(LookupStatus.UNKNOWN, registry.lookup(second.getId(), "/mcp/profiles/review", true).getStatus()); //$NON-NLS-1$
    }

    @Test
    public void closeAndExpiryNotifyListener()
    {
        AtomicLong clock = new AtomicLong(1_000L);
        McpSessionRegistry registry = new McpSessionRegistry(10, 100L, clock::get);
        java.util.ArrayList<String> closed = new java.util.ArrayList<>();
        registry.setSessionClosedListener(closed::add);
        McpTransportSession first = registry.create("/mcp", "2025-11-25", ClientCapabilities.ABSENT); //$NON-NLS-1$ //$NON-NLS-2$
        McpTransportSession second = registry.create("/mcp/profiles/review", "2025-11-25", //$NON-NLS-1$ //$NON-NLS-2$
            ClientCapabilities.ABSENT);
        assertTrue(registry.close(first.getId()));
        assertEquals(1, closed.size());
        clock.set(1_250L);
        assertEquals(LookupStatus.UNKNOWN, registry.lookup(second.getId(), "/mcp/profiles/review", true).getStatus()); //$NON-NLS-1$
        assertEquals(2, closed.size());
        assertTrue(closed.contains(second.getId()));
    }
}
