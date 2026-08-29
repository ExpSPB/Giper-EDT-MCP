/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.transport;

/**
 * The request path is not a valid MCP endpoint. Transport must answer HTTP 400
 * and must not fall back to {@code default}.
 */
public final class InvalidMcpEndpointException extends Exception
{
    private static final long serialVersionUID = 1L;

    public InvalidMcpEndpointException(String message)
    {
        super(message);
    }
}
