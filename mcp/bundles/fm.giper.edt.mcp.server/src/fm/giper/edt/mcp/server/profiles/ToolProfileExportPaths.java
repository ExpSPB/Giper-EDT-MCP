/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server.profiles;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Resolves export paths chosen in a file dialog (.json suffix) and decides
 * when an extra overwrite confirmation is needed after silent suffix append.
 */
public final class ToolProfileExportPaths
{
    private static final String JSON_SUFFIX = ".json"; //$NON-NLS-1$

    private ToolProfileExportPaths()
    {
    }

    /**
     * Resolves the export path, appending {@code .json} when the chosen name
     * has no case-insensitive {@code .json} suffix.
     *
     * @param chosenPath raw path returned by the file dialog
     * @return resolved export path
     */
    public static Path resolve(String chosenPath)
    {
        Path exportPath = Paths.get(chosenPath);
        if (!hasJsonSuffix(chosenPath))
        {
            exportPath = exportPath.resolveSibling(
                exportPath.getFileName().toString() + JSON_SUFFIX);
        }
        return exportPath;
    }

    /**
     * Returns {@code true} when the resolved path differs from the raw dialog
     * choice and the resolved file already exists (suffix was appended silently).
     *
     * @param chosenPath raw path returned by the file dialog
     * @param resolvedPath path after {@link #resolve(String)}
     * @return whether an extra overwrite confirmation is required
     */
    public static boolean needsOverwriteConfirm(String chosenPath, Path resolvedPath)
    {
        Path chosen = Paths.get(chosenPath).normalize();
        Path resolved = resolvedPath.normalize();
        return !chosen.equals(resolved) && Files.exists(resolved);
    }

    private static boolean hasJsonSuffix(String path)
    {
        return path.toLowerCase().endsWith(JSON_SUFFIX);
    }
}
