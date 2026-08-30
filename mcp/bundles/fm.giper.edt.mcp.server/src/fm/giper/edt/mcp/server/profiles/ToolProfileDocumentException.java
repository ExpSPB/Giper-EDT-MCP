/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server.profiles;

/**
 * Base type for a stored profile document that cannot be used as-is.
 */
public abstract class ToolProfileDocumentException extends Exception
{
    private static final long serialVersionUID = 1L;

    protected ToolProfileDocumentException(String message)
    {
        super(message);
    }

    protected ToolProfileDocumentException(String message, Throwable cause)
    {
        super(message, cause);
    }
}
