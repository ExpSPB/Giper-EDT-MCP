/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server.profiles;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Headless export path resolution and overwrite-confirm gating after .json suffix append.
 */
public class ToolProfileExportPathsTest
{
    private Path tmp;

    @Before
    public void setUp() throws IOException
    {
        tmp = Files.createTempDirectory("tool-profile-export-paths"); //$NON-NLS-1$
    }

    @After
    public void tearDown() throws IOException
    {
        if (tmp != null && Files.exists(tmp))
        {
            Files.walk(tmp)
                .sorted(java.util.Comparator.reverseOrder())
                .forEach(ToolProfileExportPathsTest::deleteQuietly);
        }
    }

    @Test
    public void fooWithExistingFooJsonNeedsConfirm() throws IOException
    {
        Path fooJson = tmp.resolve("foo.json"); //$NON-NLS-1$
        Files.writeString(fooJson, "{}"); //$NON-NLS-1$
        String chosen = tmp.resolve("foo").toString(); //$NON-NLS-1$
        Path resolved = ToolProfileExportPaths.resolve(chosen);

        assertEquals(fooJson.normalize(), resolved.normalize());
        assertTrue(ToolProfileExportPaths.needsOverwriteConfirm(chosen, resolved));
    }

    @Test
    public void fooJsonWithExistingFileNeedsNoExtraConfirm() throws IOException
    {
        Path fooJson = tmp.resolve("foo.json"); //$NON-NLS-1$
        Files.writeString(fooJson, "{}"); //$NON-NLS-1$
        String chosen = fooJson.toString();
        Path resolved = ToolProfileExportPaths.resolve(chosen);

        assertEquals(fooJson.normalize(), resolved.normalize());
        assertFalse(ToolProfileExportPaths.needsOverwriteConfirm(chosen, resolved));
    }

    @Test
    public void fooWithoutExistingFileNeedsNoConfirm()
    {
        String chosen = tmp.resolve("foo").toString(); //$NON-NLS-1$
        Path resolved = ToolProfileExportPaths.resolve(chosen);

        assertEquals(tmp.resolve("foo.json").normalize(), resolved.normalize()); //$NON-NLS-1$
        assertFalse(ToolProfileExportPaths.needsOverwriteConfirm(chosen, resolved));
    }

    @Test
    public void jsonSuffixAppendIsCaseInsensitive()
    {
        String chosen = tmp.resolve("profile.JSON").toString(); //$NON-NLS-1$
        Path resolved = ToolProfileExportPaths.resolve(chosen);

        assertEquals(chosen, resolved.toString());
        assertFalse(ToolProfileExportPaths.needsOverwriteConfirm(chosen, resolved));
    }

    private static void deleteQuietly(Path path)
    {
        try
        {
            Files.deleteIfExists(path);
        }
        catch (IOException ignored)
        {
            // best-effort temp cleanup
        }
    }
}
