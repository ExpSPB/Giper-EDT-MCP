/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server.profiles;

import java.util.Objects;

/**
 * Snapshot shown before overlaying an imported file onto the selected draft.
 */
public final class ImportPreview
{
    private final ToolProfile current;
    private final ToolProfile imported;

    private ImportPreview(ToolProfile current, ToolProfile imported)
    {
        this.current = current;
        this.imported = imported;
    }

    public static ImportPreview of(ToolProfile current, ToolProfile imported)
    {
        return new ImportPreview(Objects.requireNonNull(current, "current"), //$NON-NLS-1$
            Objects.requireNonNull(imported, "imported")); //$NON-NLS-1$
    }

    public ToolProfile getCurrent()
    {
        return current;
    }

    public ToolProfile getImported()
    {
        return imported;
    }

    public boolean fileIdDiffers()
    {
        return !current.getId().equals(imported.getId());
    }
}
