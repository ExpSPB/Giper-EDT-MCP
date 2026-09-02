/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.proxy;

/**
 * The request path is not {@code /mcp} or {@code /mcp/profiles/<id>}. The transport
 * answers HTTP 400 and must not fall back to {@code default}.
 */
public final class InvalidProfileEndpointException extends Exception
{
    private static final long serialVersionUID = 1L;

    public InvalidProfileEndpointException(String message)
    {
        super(message);
    }
}
