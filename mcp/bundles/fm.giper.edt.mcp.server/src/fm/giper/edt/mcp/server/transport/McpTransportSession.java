/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server.transport;

import fm.giper.edt.mcp.server.protocol.ClientCapabilities;

/**
 * Path-bound transport session. The effective profile is NOT stored here:
 * it is recomputed from the current snapshot on every request.
 */
public final class McpTransportSession
{
    private final String id;
    private final String requestedPath;
    private final String protocolVersion;
    private final ClientCapabilities capabilities;
    private volatile long lastAccessMillis;

    McpTransportSession(String id, String requestedPath, String protocolVersion,
        ClientCapabilities capabilities, long lastAccessMillis)
    {
        this.id = id;
        this.requestedPath = requestedPath;
        this.protocolVersion = protocolVersion;
        this.capabilities = capabilities == null ? ClientCapabilities.ABSENT : capabilities;
        this.lastAccessMillis = lastAccessMillis;
    }

    public String getId()
    {
        return id;
    }

    public String getRequestedPath()
    {
        return requestedPath;
    }

    public String getProtocolVersion()
    {
        return protocolVersion;
    }

    public ClientCapabilities getCapabilities()
    {
        return capabilities;
    }

    public long getLastAccessMillis()
    {
        return lastAccessMillis;
    }

    void touch(long nowMillis)
    {
        lastAccessMillis = nowMillis;
    }
}
