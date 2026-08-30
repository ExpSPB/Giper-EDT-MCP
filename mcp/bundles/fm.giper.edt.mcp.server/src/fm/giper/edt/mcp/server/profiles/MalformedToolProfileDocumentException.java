/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server.profiles;

/**
 * The stored JSON is not a usable profile document (broken JSON or invalid fields).
 */
public final class MalformedToolProfileDocumentException extends ToolProfileDocumentException
{
    private static final long serialVersionUID = 1L;

    public MalformedToolProfileDocumentException(String message)
    {
        super(message);
    }

    public MalformedToolProfileDocumentException(String message, Throwable cause)
    {
        super(message, cause);
    }
}
