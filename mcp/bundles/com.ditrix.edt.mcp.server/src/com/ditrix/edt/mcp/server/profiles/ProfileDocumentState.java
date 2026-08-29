/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.profiles;

/**
 * How the repository interpreted the stored preference value on load.
 */
public enum ProfileDocumentState
{
    /** No JSON document has been saved yet; migration may still run. */
    ABSENT,
    /** A valid document of a supported schema is published. */
    VALID,
    /** The stored string is not usable JSON / fails validation. Original kept + backed up. */
    MALFORMED,
    /** Valid JSON, but schemaVersion is newer than this build. Original kept. */
    UNSUPPORTED_SCHEMA
}
