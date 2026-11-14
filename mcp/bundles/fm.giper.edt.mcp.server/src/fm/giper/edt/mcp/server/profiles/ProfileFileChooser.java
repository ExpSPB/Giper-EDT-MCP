/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server.profiles;

import java.nio.file.Path;

/**
 * Test seam for native file dialogs. Implementations return the raw chosen
 * path (suffix not yet resolved) or {@code null} when the user cancels.
 */
public interface ProfileFileChooser
{
    Path chooseOpen();

    Path chooseSave(String suggestedName);
}
