/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server.profiles;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;
import java.util.Set;

import org.junit.Test;

/**
 * Strict decode and deterministic encode.
 */
public class ToolProfileCodecTest
{
    @Test
    public void roundTripIsDeterministic() throws Exception
    {
        ToolProfileSnapshot snapshot = ToolProfileSnapshot.of(3L, List.of(
            DefaultToolProfileFactory.createDefault(Set.of("list_projects", "get_server_status")), //$NON-NLS-1$ //$NON-NLS-2$
            ToolProfile.builder()
                .id("review") //$NON-NLS-1$
                .displayName("Review") //$NON-NLS-1$
                .description("Read-only review") //$NON-NLS-1$
                .allowedTools(Set.of("get_server_status", "list_projects")) //$NON-NLS-1$ //$NON-NLS-2$
                .revision(2L)
                .build()));

        String first = ToolProfileCodec.encode(snapshot);
        String second = ToolProfileCodec.encode(ToolProfileCodec.decode(first));
        assertEquals(first, second);
        assertTrue(first.contains("\"id\":\"default\"")); //$NON-NLS-1$
        assertTrue(first.indexOf("\"id\":\"default\"") < first.indexOf("\"id\":\"review\"")); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void malformedJsonIsDistinctFromUnsupportedSchema()
    {
        expectMalformed("{not json"); //$NON-NLS-1$
        expectMalformed("[]"); //$NON-NLS-1$
        try
        {
            ToolProfileCodec.decode("{\"schemaVersion\":99,\"documentRevision\":1,\"profiles\":[]}"); //$NON-NLS-1$
            fail("expected unsupported schema"); //$NON-NLS-1$
        }
        catch (UnsupportedToolProfileSchemaException expected)
        {
            assertEquals(99, expected.getSchemaVersion());
        }
        catch (ToolProfileDocumentException e)
        {
            fail("future schema must not be reported as malformed: " + e); //$NON-NLS-1$
        }
    }

    @Test
    public void missingDefaultIsMalformed()
    {
        expectMalformed("{\"schemaVersion\":1,\"documentRevision\":1,\"profiles\":[" //$NON-NLS-1$
            + "{\"id\":\"review\",\"displayName\":\"Review\",\"description\":\"\",\"enabled\":true," //$NON-NLS-1$
            + "\"allowedTools\":[],\"revision\":1}]}"); //$NON-NLS-1$
    }

    private static void expectMalformed(String json)
    {
        try
        {
            ToolProfileCodec.decode(json);
            fail("expected malformed document"); //$NON-NLS-1$
        }
        catch (MalformedToolProfileDocumentException expected)
        {
            assertTrue(expected.getMessage(), expected.getMessage().length() > 0);
        }
        catch (ToolProfileDocumentException e)
        {
            fail("expected malformed, got " + e.getClass().getSimpleName()); //$NON-NLS-1$
        }
    }

    @Test
    public void schemaVersionOutsideIntRangeIsMalformedNotUnchecked()
    {
        String json = "{\"schemaVersion\":9223372036854775807,\"documentRevision\":1,\"profiles\":[" //$NON-NLS-1$
            + "{\"id\":\"default\",\"displayName\":\"Default\",\"description\":\"\"," //$NON-NLS-1$
            + "\"enabled\":true,\"allowedTools\":[\"get_server_status\"],\"revision\":1}]}"; //$NON-NLS-1$
        try
        {
            ToolProfileCodec.decode(json);
            fail("expected malformed document"); //$NON-NLS-1$
        }
        catch (MalformedToolProfileDocumentException expected)
        {
            assertTrue(expected.getMessage(), expected.getMessage().contains("schemaVersion")); //$NON-NLS-1$
        }
        catch (ArithmeticException e)
        {
            fail("Math.toIntExact must not escape decode as ArithmeticException"); //$NON-NLS-1$
        }
        catch (ToolProfileDocumentException e)
        {
            fail("expected malformed, got " + e.getClass().getSimpleName()); //$NON-NLS-1$
        }
    }

    @Test
    public void preservesUnknownToolNames() throws Exception
    {
        ToolProfileSnapshot snapshot = ToolProfileSnapshot.of(1L, List.of(
            DefaultToolProfileFactory.createDefault(Set.of("retired_tool")))); //$NON-NLS-1$
        ToolProfileSnapshot decoded = ToolProfileCodec.decode(ToolProfileCodec.encode(snapshot));
        assertTrue(decoded.getDefault().getAllowedTools().contains("retired_tool")); //$NON-NLS-1$
    }
}
