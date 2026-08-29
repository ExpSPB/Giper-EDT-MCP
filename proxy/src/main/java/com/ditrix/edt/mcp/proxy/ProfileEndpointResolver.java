/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.proxy;

import java.nio.charset.StandardCharsets;

/**
 * Strict raw-path parser matching the plugin's {@code McpEndpointResolver}.
 * Only {@code /mcp} and {@code /mcp/profiles/<id>} are accepted; everything else is
 * {@link InvalidProfileEndpointException} (HTTP 400, no profile fallback).
 */
public final class ProfileEndpointResolver
{
    private ProfileEndpointResolver()
    {
        // utility
    }

    /**
     * @param rawPath the HTTP request path (may include a query string)
     * @return the accepted endpoint
     * @throws InvalidProfileEndpointException when the path is malformed or not an MCP profile URL
     */
    public static ProfileEndpoint resolve(String rawPath) throws InvalidProfileEndpointException
    {
        if (rawPath == null || rawPath.isEmpty())
        {
            throw new InvalidProfileEndpointException("MCP path is required"); //$NON-NLS-1$
        }
        if (rawPath.indexOf('\\') >= 0 || rawPath.contains("//")) //$NON-NLS-1$
        {
            throw new InvalidProfileEndpointException("MCP path must not contain backslashes or empty segments"); //$NON-NLS-1$
        }
        String path = stripQuery(rawPath);
        if (containsEncodedTraversal(path) || containsDotSegment(path))
        {
            throw new InvalidProfileEndpointException("MCP path must not contain traversal or dot segments"); //$NON-NLS-1$
        }
        if (ProfileEndpoint.LEGACY_PATH.equals(path) || (ProfileEndpoint.LEGACY_PATH + "/").equals(path)) //$NON-NLS-1$
        {
            return ProfileEndpoint.legacyDefault();
        }
        if (!path.startsWith(ProfileEndpoint.PROFILES_PREFIX))
        {
            throw new InvalidProfileEndpointException("MCP path must be /mcp or /mcp/profiles/<id>"); //$NON-NLS-1$
        }
        String encodedId = path.substring(ProfileEndpoint.PROFILES_PREFIX.length());
        if (encodedId.isEmpty() || encodedId.endsWith("/")) //$NON-NLS-1$
        {
            throw new InvalidProfileEndpointException("Profile id is required"); //$NON-NLS-1$
        }
        if (encodedId.indexOf('/') >= 0)
        {
            throw new InvalidProfileEndpointException("MCP profile path must have exactly one id segment"); //$NON-NLS-1$
        }
        if (encodedId.contains("%2f") || encodedId.contains("%2F") || encodedId.contains("%5c") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            || encodedId.contains("%5C")) //$NON-NLS-1$
        {
            throw new InvalidProfileEndpointException("Encoded slashes are not allowed in a profile id"); //$NON-NLS-1$
        }
        String id = decodeOnce(encodedId);
        if (!id.equals(encodedId) && !id.equals(decodeOnce(id)))
        {
            throw new InvalidProfileEndpointException("Ambiguous percent-encoding in profile id"); //$NON-NLS-1$
        }
        if (!ProfileEndpoint.ID_PATTERN.matcher(id).matches())
        {
            throw new InvalidProfileEndpointException(
                "Profile id '" + id + "' must match [a-z0-9][a-z0-9_-]{0,63}"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        return new ProfileEndpoint(path, id, false);
    }

    private static String stripQuery(String path)
    {
        int query = path.indexOf('?');
        return query < 0 ? path : path.substring(0, query);
    }

    private static boolean containsDotSegment(String path)
    {
        for (String segment : path.split("/")) //$NON-NLS-1$
        {
            if (".".equals(segment) || "..".equals(segment)) //$NON-NLS-1$ //$NON-NLS-2$
            {
                return true;
            }
        }
        return false;
    }

    private static boolean containsEncodedTraversal(String path)
    {
        String lower = path.toLowerCase();
        return lower.contains("%2e") || lower.contains("%2f") || lower.contains("%5c"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }

    private static String decodeOnce(String value) throws InvalidProfileEndpointException
    {
        try
        {
            StringBuilder out = new StringBuilder(value.length());
            for (int i = 0; i < value.length(); i++)
            {
                char ch = value.charAt(i);
                if (ch != '%')
                {
                    out.append(ch);
                    continue;
                }
                if (i + 2 >= value.length())
                {
                    throw new InvalidProfileEndpointException("Broken percent-encoding in profile id"); //$NON-NLS-1$
                }
                String hex = value.substring(i + 1, i + 3);
                int decoded = Integer.parseInt(hex, 16);
                if (decoded == '/' || decoded == '\\')
                {
                    throw new InvalidProfileEndpointException("Encoded slashes are not allowed in a profile id"); //$NON-NLS-1$
                }
                out.append((char) decoded);
                i += 2;
            }
            return new String(out.toString().getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
        }
        catch (NumberFormatException e)
        {
            throw new InvalidProfileEndpointException("Broken percent-encoding in profile id"); //$NON-NLS-1$
        }
    }
}
