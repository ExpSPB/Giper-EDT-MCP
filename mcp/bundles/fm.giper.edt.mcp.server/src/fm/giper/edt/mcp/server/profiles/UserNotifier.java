/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server.profiles;

import java.nio.file.Path;
import java.util.Set;

/**
 * Test seam for overwrite confirm plus error/info dialogs. SWT binds NLS.
 */
public interface UserNotifier
{
    boolean confirmOverwrite(Path resolvedPath);

    void exportFailed(String detail);

    void importFailed(String detail);

    void unknownTools(Set<String> names);
}
