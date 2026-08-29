/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.profiles;

/**
 * The document is structurally valid JSON but uses a newer schema than this build understands.
 * Distinct from {@link MalformedToolProfileDocumentException}.
 */
public final class UnsupportedToolProfileSchemaException extends ToolProfileDocumentException
{
    private static final long serialVersionUID = 1L;

    private final int schemaVersion;

    public UnsupportedToolProfileSchemaException(int schemaVersion)
    {
        super("Unsupported tool-profile schema version " + schemaVersion
            + "; this build understands version " + ToolProfileSnapshot.CURRENT_SCHEMA_VERSION);
        this.schemaVersion = schemaVersion;
    }

    public int getSchemaVersion()
    {
        return schemaVersion;
    }
}
