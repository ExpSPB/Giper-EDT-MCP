/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server.profiles;

/**
 * Test seam for the import confirmation dialog. {@code false} is cancel / no-op.
 */
public interface ImportConfirmation
{
    boolean confirm(ImportPreview preview);
}
