/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.transport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

/**
 * Only {@code /mcp} and {@code /mcp/profiles/<id>} are accepted; everything else is 400.
 */
public class McpEndpointResolverTest
{
    @Test
    public void legacyMcpIsDefault() throws Exception
    {
        McpEndpoint endpoint = McpEndpointResolver.resolve("/mcp"); //$NON-NLS-1$
        assertTrue(endpoint.isLegacy());
        assertEquals("default", endpoint.getRequestedProfileId()); //$NON-NLS-1$
    }

    @Test
    public void explicitProfileIdIsKept() throws Exception
    {
        McpEndpoint endpoint = McpEndpointResolver.resolve("/mcp/profiles/review"); //$NON-NLS-1$
        assertFalse(endpoint.isLegacy());
        assertEquals("review", endpoint.getRequestedProfileId()); //$NON-NLS-1$
        assertEquals("/mcp/profiles/review", endpoint.canonicalPath()); //$NON-NLS-1$
    }

    @Test
    public void rejectsEmptyIdExtraSegmentTraversalAndBrokenEncoding()
    {
        assertInvalid("/mcp/profiles/"); //$NON-NLS-1$
        assertInvalid("/mcp/profiles/review/extra"); //$NON-NLS-1$
        assertInvalid("/mcp/profiles/Review"); //$NON-NLS-1$
        assertInvalid("/mcp/profiles/../default"); //$NON-NLS-1$
        assertInvalid("/mcp/profiles/%2e%2e/default"); //$NON-NLS-1$
        assertInvalid("/mcp/profiles/re%2fview"); //$NON-NLS-1$
        assertInvalid("/mcp/profiles/%2572eview"); //$NON-NLS-1$
        assertInvalid("/mcp/profiles/рецензент"); //$NON-NLS-1$
        assertInvalid("/other"); //$NON-NLS-1$
    }

    private static void assertInvalid(String path)
    {
        try
        {
            McpEndpointResolver.resolve(path);
            fail("expected invalid path: " + path); //$NON-NLS-1$
        }
        catch (InvalidMcpEndpointException expected)
        {
            assertTrue(expected.getMessage(), expected.getMessage().length() > 0);
        }
    }
}
