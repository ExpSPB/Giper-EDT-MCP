/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server.profiles;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * Headless UTF-8 read/write for single-profile exchange files. Maps codec failures to
 * {@link FileOpResult.Status#FORMAT} and expected disk problems to {@link FileOpResult.Status#IO}.
 */
public final class ToolProfileFileService
{
    /** Documented import limit in megabytes (see {@link #MAX_IMPORT_BYTES}). */
    public static final int MAX_IMPORT_MEGABYTES = 5;

    public static final int MAX_IMPORT_BYTES = MAX_IMPORT_MEGABYTES * 1024 * 1024;

    private static final byte[] UTF8_BOM = { (byte)0xEF, (byte)0xBB, (byte)0xBF };

    private ToolProfileFileService()
    {
        // Utility class
    }

    /**
     * Writes one profile to {@code path} using the single-profile envelope. The file is UTF-8
     * without a BOM and is committed atomically (temp file in the same directory, then replace).
     */
    public static FileOpResult exportProfile(Path path, ToolProfile profile)
    {
        if (path == null)
        {
            throw new IllegalArgumentException("path is required"); //$NON-NLS-1$
        }
        if (profile == null)
        {
            throw new IllegalArgumentException("profile is required"); //$NON-NLS-1$
        }
        String json = ToolProfileCodec.encodeSingleProfile(profile);
        try
        {
            writeUtf8Atomically(path, json);
            return FileOpResult.successExport(path);
        }
        catch (IOException e)
        {
            return FileOpResult.ioError("Could not write profile file: " + e.getMessage()); //$NON-NLS-1$
        }
    }

    /**
     * Reads one profile from {@code path}. Leading UTF-8 BOM bytes are stripped. Files larger
     * than {@link #MAX_IMPORT_BYTES} are rejected before decode.
     */
    public static FileOpResult importProfile(Path path)
    {
        if (path == null)
        {
            throw new IllegalArgumentException("path is required"); //$NON-NLS-1$
        }
        if (!Files.isRegularFile(path))
        {
            return FileOpResult.ioError("Profile file not found: " + path); //$NON-NLS-1$
        }
        try
        {
            long size = Files.size(path);
            if (size > MAX_IMPORT_BYTES)
            {
                return FileOpResult.ioError("Profile file exceeds the " + MAX_IMPORT_MEGABYTES //$NON-NLS-1$
                    + " MB import limit"); //$NON-NLS-1$
            }
            String json = readUtf8StripBom(path);
            ToolProfile profile = ToolProfileCodec.decodeSingleProfile(json);
            return FileOpResult.successImport(profile, path);
        }
        catch (ToolProfileDocumentException e)
        {
            return FileOpResult.formatError(e.getMessage());
        }
        catch (IOException e)
        {
            return FileOpResult.ioError("Could not read profile file: " + e.getMessage()); //$NON-NLS-1$
        }
    }

    static String readUtf8StripBom(Path path) throws IOException
    {
        byte[] bytes = Files.readAllBytes(path);
        int offset = 0;
        if (bytes.length >= UTF8_BOM.length
            && bytes[0] == UTF8_BOM[0]
            && bytes[1] == UTF8_BOM[1]
            && bytes[2] == UTF8_BOM[2])
        {
            offset = UTF8_BOM.length;
        }
        String text = new String(bytes, offset, bytes.length - offset, StandardCharsets.UTF_8);
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF')
        {
            return text.substring(1);
        }
        return text;
    }

    private static void writeUtf8Atomically(Path path, String json) throws IOException
    {
        Path parent = path.getParent();
        if (parent == null)
        {
            throw new IOException("Path has no parent directory: " + path); //$NON-NLS-1$
        }
        Files.createDirectories(parent);
        Path temp = Files.createTempFile(parent, ".edt-mcp-profile-", ".tmp"); //$NON-NLS-1$ //$NON-NLS-2$
        try
        {
            Files.writeString(temp, json, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
            try
            {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            }
            catch (AtomicMoveNotSupportedException e)
            {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        catch (IOException e)
        {
            try
            {
                Files.deleteIfExists(temp);
            }
            catch (IOException suppressed)
            {
                e.addSuppressed(suppressed);
            }
            throw e;
        }
    }
}
