/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server.profiles;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Headless single-profile file exchange: UTF-8, BOM, size limit, and codec mapping.
 */
public class ToolProfileFileServiceTest
{
    private static final byte[] UTF8_BOM = { (byte)0xEF, (byte)0xBB, (byte)0xBF };

    private Path tmp;

    @Before
    public void setUp() throws IOException
    {
        tmp = Files.createTempDirectory("tool-profile-file"); //$NON-NLS-1$
    }

    @After
    public void tearDown() throws IOException
    {
        if (tmp != null && Files.exists(tmp))
        {
            try (Stream<Path> walk = Files.walk(tmp))
            {
                walk.sorted(Comparator.reverseOrder()).forEach(ToolProfileFileServiceTest::deleteQuietly);
            }
        }
    }

    @Test
    public void roundTripPreservesContentFieldsIncludingCyrillic() throws Exception
    {
        ToolProfile profile = ToolProfile.builder()
            .id("review") //$NON-NLS-1$
            .displayName("\u041a\u043e\u0434-\u0440\u0435\u0432\u044c\u044e") //$NON-NLS-1$
            .description("\u0422\u043e\u043b\u044c\u043a\u043e \u0447\u0442\u0435\u043d\u0438\u0435") //$NON-NLS-1$
            .enabled(true)
            .allowedTools(Set.of("get_server_status", "list_projects", "retired_tool")) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            .revision(4L)
            .build();
        Path file = tmp.resolve("profile.json"); //$NON-NLS-1$

        FileOpResult exported = ToolProfileFileService.exportProfile(file, profile);
        assertTrue(exported.isOk());
        assertEquals(FileOpResult.Status.SUCCESS, exported.getStatus());
        assertEquals(file, exported.getPath());
        assertNull(exported.getProfile());

        FileOpResult imported = ToolProfileFileService.importProfile(file);
        assertTrue(imported.isOk());
        ToolProfile decoded = imported.getProfile();
        assertNotNull(decoded);
        assertEquals(profile.getDisplayName(), decoded.getDisplayName());
        assertEquals(profile.getDescription(), decoded.getDescription());
        assertEquals(profile.isEnabled(), decoded.isEnabled());
        assertEquals(profile.getAllowedTools(), decoded.getAllowedTools());
        assertEquals("review", decoded.getId()); //$NON-NLS-1$
        assertEquals(4L, decoded.getRevision());
    }

    @Test
    public void preservesUnknownToolNames() throws Exception
    {
        ToolProfile profile = ToolProfile.builder()
            .id("review") //$NON-NLS-1$
            .displayName("Review") //$NON-NLS-1$
            .allowedTools(Set.of("legacy_tool_from_old_edt")) //$NON-NLS-1$
            .revision(1L)
            .build();
        Path file = tmp.resolve("unknown-tools.json"); //$NON-NLS-1$
        ToolProfileFileService.exportProfile(file, profile);

        FileOpResult imported = ToolProfileFileService.importProfile(file);
        assertTrue(imported.getProfile().getAllowedTools().contains("legacy_tool_from_old_edt")); //$NON-NLS-1$
    }

    @Test
    public void extraRootKeysAreIgnored() throws Exception
    {
        String json = "{\"schemaVersion\":1,\"profile\":{\"id\":\"review\",\"displayName\":\"Review\"," //$NON-NLS-1$
            + "\"description\":\"\",\"enabled\":true,\"allowedTools\":[],\"revision\":1}," //$NON-NLS-1$
            + "\"note\":\"ignored\"}"; //$NON-NLS-1$
        Path file = tmp.resolve("extra-keys.json"); //$NON-NLS-1$
        Files.writeString(file, json, StandardCharsets.UTF_8);

        FileOpResult imported = ToolProfileFileService.importProfile(file);
        assertTrue(imported.isOk());
        assertEquals("review", imported.getProfile().getId()); //$NON-NLS-1$
    }

    @Test
    public void stripsLeadingBomOnRead() throws Exception
    {
        ToolProfile profile = ToolProfile.builder()
            .id("review") //$NON-NLS-1$
            .displayName("Review") //$NON-NLS-1$
            .revision(1L)
            .build();
        Path file = tmp.resolve("bom.json"); //$NON-NLS-1$
        byte[] body = ToolProfileCodec.encodeSingleProfile(profile).getBytes(StandardCharsets.UTF_8);
        byte[] withBom = new byte[UTF8_BOM.length + body.length];
        System.arraycopy(UTF8_BOM, 0, withBom, 0, UTF8_BOM.length);
        System.arraycopy(body, 0, withBom, UTF8_BOM.length, body.length);
        Files.write(file, withBom);

        FileOpResult imported = ToolProfileFileService.importProfile(file);
        assertTrue(imported.isOk());
        assertEquals("Review", imported.getProfile().getDisplayName()); //$NON-NLS-1$
    }

    @Test
    public void writeDoesNotIncludeBom() throws Exception
    {
        ToolProfile profile = ToolProfile.builder()
            .id("review") //$NON-NLS-1$
            .displayName("Review") //$NON-NLS-1$
            .revision(1L)
            .build();
        Path file = tmp.resolve("no-bom.json"); //$NON-NLS-1$
        ToolProfileFileService.exportProfile(file, profile);

        byte[] bytes = Files.readAllBytes(file);
        assertFalse("export must not write a UTF-8 BOM", hasUtf8Bom(bytes)); //$NON-NLS-1$
        assertTrue(new String(bytes, StandardCharsets.UTF_8).contains("\"schemaVersion\":1")); //$NON-NLS-1$
    }

    @Test
    public void missingFileReturnsIo()
    {
        Path missing = tmp.resolve("missing.json"); //$NON-NLS-1$
        FileOpResult result = ToolProfileFileService.importProfile(missing);

        assertFalse(result.isOk());
        assertEquals(FileOpResult.Status.IO, result.getStatus());
        assertNull(result.getProfile());
        assertNotNull(result.getMessage());
        assertTrue(result.getMessage().contains("not found")); //$NON-NLS-1$
    }

    @Test
    public void oversizeFileReturnsIoNamingLimit() throws Exception
    {
        Path file = tmp.resolve("huge.json"); //$NON-NLS-1$
        byte[] padding = new byte[ToolProfileFileService.MAX_IMPORT_BYTES + 1];
        Files.write(file, padding);

        FileOpResult result = ToolProfileFileService.importProfile(file);
        assertEquals(FileOpResult.Status.IO, result.getStatus());
        assertNull(result.getProfile());
        assertTrue(result.getMessage().contains("5 MB")); //$NON-NLS-1$
    }

    @Test
    public void workspaceDocumentIsFormatError() throws Exception
    {
        Path file = tmp.resolve("workspace.json"); //$NON-NLS-1$
        Files.writeString(file,
            "{\"schemaVersion\":1,\"documentRevision\":1,\"profiles\":[]}", //$NON-NLS-1$
            StandardCharsets.UTF_8);

        FileOpResult result = ToolProfileFileService.importProfile(file);
        assertEquals(FileOpResult.Status.FORMAT, result.getStatus());
        assertNull(result.getProfile());
        assertTrue(result.getMessage().contains("single-profile file")); //$NON-NLS-1$
    }

    @Test
    public void missingProfileObjectIsFormatError() throws Exception
    {
        Path file = tmp.resolve("no-profile.json"); //$NON-NLS-1$
        Files.writeString(file, "{\"schemaVersion\":1}", StandardCharsets.UTF_8); //$NON-NLS-1$

        FileOpResult result = ToolProfileFileService.importProfile(file);
        assertEquals(FileOpResult.Status.FORMAT, result.getStatus());
        assertTrue(result.getMessage().contains("profile")); //$NON-NLS-1$
    }

    @Test
    public void futureSchemaVersionIsFormatError() throws Exception
    {
        Path file = tmp.resolve("future-schema.json"); //$NON-NLS-1$
        Files.writeString(file,
            "{\"schemaVersion\":99,\"profile\":{\"id\":\"review\",\"displayName\":\"Review\"," //$NON-NLS-1$
                + "\"description\":\"\",\"enabled\":true,\"allowedTools\":[],\"revision\":1}}", //$NON-NLS-1$
            StandardCharsets.UTF_8);

        FileOpResult result = ToolProfileFileService.importProfile(file);
        assertEquals(FileOpResult.Status.FORMAT, result.getStatus());
        assertNull(result.getProfile());
        assertTrue(result.getMessage().contains("Unsupported tool-profile schema")); //$NON-NLS-1$
    }

    @Test
    public void invalidJsonIsFormatError() throws Exception
    {
        Path file = tmp.resolve("broken.json"); //$NON-NLS-1$
        Files.writeString(file, "{not json", StandardCharsets.UTF_8); //$NON-NLS-1$

        FileOpResult result = ToolProfileFileService.importProfile(file);
        assertEquals(FileOpResult.Status.FORMAT, result.getStatus());
    }

    private static boolean hasUtf8Bom(byte[] bytes)
    {
        return bytes.length >= UTF8_BOM.length
            && bytes[0] == UTF8_BOM[0]
            && bytes[1] == UTF8_BOM[1]
            && bytes[2] == UTF8_BOM[2];
    }

    private static void deleteQuietly(Path path)
    {
        try
        {
            Files.deleteIfExists(path);
        }
        catch (IOException ignored)
        {
            // Best-effort cleanup for a temp directory.
        }
    }
}
