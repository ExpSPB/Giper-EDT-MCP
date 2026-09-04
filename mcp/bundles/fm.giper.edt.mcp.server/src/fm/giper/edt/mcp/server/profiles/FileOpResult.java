/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server.profiles;

import java.nio.file.Path;

/**
 * Outcome of {@link ToolProfileFileService#importProfile(Path)} or
 * {@link ToolProfileFileService#exportProfile(Path, ToolProfile)}.
 */
public final class FileOpResult
{
    public enum Status
    {
        SUCCESS,
        FORMAT,
        IO
    }

    private final Status status;
    private final ToolProfile profile;
    private final Path path;
    private final String message;

    private FileOpResult(Status status, ToolProfile profile, Path path, String message)
    {
        this.status = status;
        this.profile = profile;
        this.path = path;
        this.message = message;
    }

    static FileOpResult successExport(Path path)
    {
        return new FileOpResult(Status.SUCCESS, null, path, null);
    }

    static FileOpResult successImport(ToolProfile profile, Path path)
    {
        return new FileOpResult(Status.SUCCESS, profile, path, null);
    }

    static FileOpResult formatError(String message)
    {
        return new FileOpResult(Status.FORMAT, null, null, message);
    }

    static FileOpResult ioError(String message)
    {
        return new FileOpResult(Status.IO, null, null, message);
    }

    public boolean isOk()
    {
        return status == Status.SUCCESS;
    }

    public Status getStatus()
    {
        return status;
    }

    public ToolProfile getProfile()
    {
        return profile;
    }

    public Path getPath()
    {
        return path;
    }

    public String getMessage()
    {
        return message;
    }
}
