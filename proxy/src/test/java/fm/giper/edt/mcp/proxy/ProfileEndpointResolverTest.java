/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.proxy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

/**
 * Strict parser: only {@code /mcp} and {@code /mcp/profiles/<id>}; invalid paths throw.
 */
public class ProfileEndpointResolverTest
{
    @Test
    public void legacyMcpIsDefault() throws Exception
    {
        ProfileEndpoint endpoint = ProfileEndpointResolver.resolve("/mcp"); //$NON-NLS-1$
        assertTrue(endpoint.isLegacy());
        assertEquals("default", endpoint.getRequestedProfileId()); //$NON-NLS-1$
    }

    @Test
    public void explicitProfileIdIsKept() throws Exception
    {
        ProfileEndpoint endpoint = ProfileEndpointResolver.resolve("/mcp/profiles/review"); //$NON-NLS-1$
        assertFalse(endpoint.isLegacy());
        assertEquals("review", endpoint.getRequestedProfileId()); //$NON-NLS-1$
        assertEquals("/mcp/profiles/review", endpoint.canonicalPath()); //$NON-NLS-1$
    }

    @Test
    public void rejectsEmptyIdExtraSegmentTraversalAndBrokenEncoding()
    {
        assertInvalid("/mcp/"); //$NON-NLS-1$
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

    @Test
    public void onceDecodedDoubleEncodingMustStayRejectedLikeTheRawWirePath() throws Exception
    {
        java.net.URI uri = java.net.URI.create("http://127.0.0.1/mcp/profiles/%2570rod"); //$NON-NLS-1$
        assertInvalid(uri.getRawPath());
        ProfileEndpoint singleEncoded = ProfileEndpointResolver.resolve("/mcp/profiles/%70rod"); //$NON-NLS-1$
        assertEquals("prod", singleEncoded.getRequestedProfileId()); //$NON-NLS-1$
    }

    private static void assertInvalid(String path)
    {
        try
        {
            ProfileEndpointResolver.resolve(path);
            fail("expected invalid path: " + path); //$NON-NLS-1$
        }
        catch (InvalidProfileEndpointException expected)
        {
            assertTrue(expected.getMessage(), expected.getMessage().length() > 0);
        }
    }
}
