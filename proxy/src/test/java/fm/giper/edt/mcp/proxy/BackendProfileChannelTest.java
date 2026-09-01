/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.proxy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Production {@code get_server_status} puts fallback flags inside {@code activeProfile}.
 */
public class BackendProfileChannelTest
{
    @Test
    public void parseResolutionReadsFallbackFromActiveProfile()
    {
        String status = envelope("{"
            + "\"activeProfile\":{"
            + "\"id\":\"default\","
            + "\"requestedProfileId\":\"missing\","
            + "\"fallbackApplied\":true,"
            + "\"fallbackReason\":\"UNKNOWN_PROFILE\""
            + "}}");
        String tools = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"tools\":[]}}"; //$NON-NLS-1$
        ProfileEndpoint endpoint = new ProfileEndpoint("/mcp/profiles/missing", "missing", false); //$NON-NLS-1$ //$NON-NLS-2$
        ChannelResolution resolution = BackendProfileChannel.parseResolution(endpoint, status, tools);
        assertEquals("missing", resolution.getRequestedProfileId()); //$NON-NLS-1$
        assertEquals("default", resolution.getEffectiveProfileId()); //$NON-NLS-1$
        assertEquals("UNKNOWN_PROFILE", resolution.getFallbackReason()); //$NON-NLS-1$
        assertTrue(resolution.isFallback());
    }

    @Test
    public void parseResolutionIgnoresRootFallbackWhenActiveProfileSaysNone()
    {
        String status = envelope("{"
            + "\"fallbackApplied\":true,"
            + "\"fallbackReason\":\"STALE_ROOT\","
            + "\"activeProfile\":{"
            + "\"id\":\"review\","
            + "\"requestedProfileId\":\"review\","
            + "\"fallbackApplied\":false"
            + "}}");
        String tools = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"tools\":[]}}"; //$NON-NLS-1$
        ProfileEndpoint endpoint = new ProfileEndpoint("/mcp/profiles/review", "review", false); //$NON-NLS-1$ //$NON-NLS-2$
        ChannelResolution resolution = BackendProfileChannel.parseResolution(endpoint, status, tools);
        assertEquals("review", resolution.getEffectiveProfileId()); //$NON-NLS-1$
        assertNull(resolution.getFallbackReason());
    }

    @Test
    public void parseResolutionDoesNotConfirmRequestedIdWhenStatusHasNoActiveProfile()
    {
        String status = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{" //$NON-NLS-1$
            + "\"content\":[{\"type\":\"text\",\"text\":\"ok\"}]," //$NON-NLS-1$
            + "\"plainTextMode\":true}}"; //$NON-NLS-1$
        String tools = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"tools\":[]}}"; //$NON-NLS-1$
        ProfileEndpoint endpoint = new ProfileEndpoint("/mcp/profiles/missing", "missing", false); //$NON-NLS-1$ //$NON-NLS-2$
        ChannelResolution resolution = BackendProfileChannel.parseResolution(endpoint, status, tools);
        boolean confirmed = "missing".equals(resolution.getEffectiveProfileId()) && !resolution.isFallback(); //$NON-NLS-1$
        assertFalse("text-only status without activeProfile must not confirm the requested id", //$NON-NLS-1$
            confirmed);
    }

    @Test
    public void parseResolutionFallsBackToRootForOldBackends()
    {
        String status = envelope("{"
            + "\"requestedProfileId\":\"gone\","
            + "\"fallbackApplied\":true,"
            + "\"fallbackReason\":\"DISABLED_PROFILE\","
            + "\"activeProfile\":{\"id\":\"default\"}"
            + "}");
        String tools = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"tools\":[]}}"; //$NON-NLS-1$
        ProfileEndpoint endpoint = new ProfileEndpoint("/mcp/profiles/gone", "gone", false); //$NON-NLS-1$ //$NON-NLS-2$
        ChannelResolution resolution = BackendProfileChannel.parseResolution(endpoint, status, tools);
        assertEquals("DISABLED_PROFILE", resolution.getFallbackReason()); //$NON-NLS-1$
        assertEquals("default", resolution.getEffectiveProfileId()); //$NON-NLS-1$
    }

    private static String envelope(String structured)
    {
        return "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"structuredContent\":" //$NON-NLS-1$
            + structured + "}}"; //$NON-NLS-1$
    }
}
