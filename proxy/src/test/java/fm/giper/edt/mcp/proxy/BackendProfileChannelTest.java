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
import static org.junit.Assert.assertThrows;
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
    public void parseResolutionRejectsModernStatusWithoutActiveProfile()
    {
        String status = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{" //$NON-NLS-1$
            + "\"content\":[{\"type\":\"text\",\"text\":\"ok\"}]," //$NON-NLS-1$
            + "\"plainTextMode\":true}}"; //$NON-NLS-1$
        String tools = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"tools\":[]}}"; //$NON-NLS-1$
        ProfileEndpoint endpoint = new ProfileEndpoint("/mcp/profiles/missing", "missing", false); //$NON-NLS-1$ //$NON-NLS-2$
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> BackendProfileChannel.parseResolution(endpoint, status, tools));
        assertTrue(error.getMessage().contains("legacy status is allowed only on /mcp")); //$NON-NLS-1$
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

    @Test
    public void legacyDefaultWithoutProfileFieldsIsNotAnUnknownProfileFallback()
    {
        String status = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{" //$NON-NLS-1$
            + "\"content\":[{\"type\":\"text\",\"text\":\"legacy status\"}]}}"; //$NON-NLS-1$
        String tools = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"tools\":[]}}"; //$NON-NLS-1$

        ChannelResolution resolution = BackendProfileChannel.parseResolution(
            ProfileEndpoint.legacyDefault(), status, tools);

        assertEquals("default", resolution.getRequestedProfileId()); //$NON-NLS-1$
        assertEquals("default", resolution.getEffectiveProfileId()); //$NON-NLS-1$
        assertNull("legacy /mcp must not be reported as UNKNOWN_PROFILE", //$NON-NLS-1$
            resolution.getFallbackReason());
        assertFalse(resolution.isFallback());
        assertTrue(resolution.isLegacyDefault());
    }

    @Test
    public void brokenStatusJsonIsRejected()
    {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> BackendProfileChannel.parseResolution(ProfileEndpoint.legacyDefault(), "broken", validTools())); //$NON-NLS-1$
        assertTrue(error.getMessage().contains("malformed backend contract")); //$NON-NLS-1$
    }

    @Test
    public void statusWithoutResultIsRejected()
    {
        String status = "{\"jsonrpc\":\"2.0\",\"id\":1}"; //$NON-NLS-1$
        assertThrows(IllegalArgumentException.class,
            () -> BackendProfileChannel.parseResolution(ProfileEndpoint.legacyDefault(), status, validTools()));
    }

    @Test
    public void nonObjectStructuredContentIsRejected()
    {
        String status = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"structuredContent\":[]}}"; //$NON-NLS-1$
        assertThrows(IllegalArgumentException.class,
            () -> BackendProfileChannel.parseResolution(ProfileEndpoint.legacyDefault(), status, validTools()));
    }

    @Test
    public void toolsListRequiresArray()
    {
        String tools = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"tools\":\"bad\"}}"; //$NON-NLS-1$
        assertThrows(IllegalArgumentException.class,
            () -> BackendProfileChannel.parseResolution(ProfileEndpoint.legacyDefault(), legacyStatus(), tools));
    }

    @Test
    public void toolsListRequiresNamedObjectDescriptors()
    {
        String notObject = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"tools\":[1]}}"; //$NON-NLS-1$
        String unnamed = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"tools\":[{}]}}"; //$NON-NLS-1$
        assertThrows(IllegalArgumentException.class,
            () -> BackendProfileChannel.parseResolution(ProfileEndpoint.legacyDefault(), legacyStatus(), notObject));
        assertThrows(IllegalArgumentException.class,
            () -> BackendProfileChannel.parseResolution(ProfileEndpoint.legacyDefault(), legacyStatus(), unnamed));
    }

    @Test
    public void fallbackMustBeCompleteAndConsistent()
    {
        String missingReason = envelope("{\"activeProfile\":{" //$NON-NLS-1$
            + "\"id\":\"default\",\"requestedProfileId\":\"missing\",\"fallbackApplied\":true}}"); //$NON-NLS-1$
        String reasonWithoutFallback = envelope("{\"activeProfile\":{" //$NON-NLS-1$
            + "\"id\":\"missing\",\"requestedProfileId\":\"missing\","
            + "\"fallbackApplied\":false,\"fallbackReason\":\"UNKNOWN_PROFILE\"}}"); //$NON-NLS-1$
        ProfileEndpoint endpoint = new ProfileEndpoint("/mcp/profiles/missing", "missing", false); //$NON-NLS-1$ //$NON-NLS-2$
        assertThrows(IllegalArgumentException.class,
            () -> BackendProfileChannel.parseResolution(endpoint, missingReason, validTools()));
        assertThrows(IllegalArgumentException.class,
            () -> BackendProfileChannel.parseResolution(endpoint, reasonWithoutFallback, validTools()));
    }

    @Test
    public void requestedProfileMismatchIsDistinguishedFromMalformedContract()
    {
        String status = envelope("{\"activeProfile\":{" //$NON-NLS-1$
            + "\"id\":\"other\",\"requestedProfileId\":\"other\",\"fallbackApplied\":false}}"); //$NON-NLS-1$
        ProfileEndpoint endpoint = new ProfileEndpoint("/mcp/profiles/review", "review", false); //$NON-NLS-1$ //$NON-NLS-2$
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> BackendProfileChannel.parseResolution(endpoint, status, validTools()));
        assertTrue(error.getMessage().startsWith("profile mismatch:")); //$NON-NLS-1$
    }

    @Test
    public void legacyStatusIsRejectedOnExplicitDefaultEndpoint()
    {
        ProfileEndpoint explicit = new ProfileEndpoint("/mcp/profiles/default", "default", false); //$NON-NLS-1$ //$NON-NLS-2$
        assertThrows(IllegalArgumentException.class,
            () -> BackendProfileChannel.parseResolution(explicit, legacyStatus(), validTools()));
    }

    @Test
    public void legacyDefaultRejectsToolLevelErrorResult()
    {
        String status = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{" //$NON-NLS-1$
            + "\"isError\":true,\"content\":[{\"type\":\"text\",\"text\":\"failed\"}]}}"; //$NON-NLS-1$
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> BackendProfileChannel.parseResolution(ProfileEndpoint.legacyDefault(), status, validTools()));
        assertTrue(error.getMessage().startsWith("backend tool error:")); //$NON-NLS-1$
    }

    @Test
    public void legacyDefaultRejectsStructuredSuccessFalse()
    {
        String status = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{" //$NON-NLS-1$
            + "\"isError\":false,\"structuredContent\":{\"success\":false}}}"; //$NON-NLS-1$
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> BackendProfileChannel.parseResolution(ProfileEndpoint.legacyDefault(), status, validTools()));
        assertTrue(error.getMessage().startsWith("backend tool error:")); //$NON-NLS-1$
    }

    @Test
    public void statusResponseRejectsResultTogetherWithError()
    {
        String status = "{\"jsonrpc\":\"2.0\",\"id\":1,"
            + "\"result\":{\"content\":[]},\"error\":{\"code\":-32603,\"message\":\"failed\"}}"; //$NON-NLS-1$
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> BackendProfileChannel.parseResolution(ProfileEndpoint.legacyDefault(), status, validTools()));
        assertTrue(error.getMessage().contains("get_server_status response must contain exactly one")); //$NON-NLS-1$
    }

    @Test
    public void toolsListResponseRejectsResultTogetherWithError()
    {
        String tools = "{\"jsonrpc\":\"2.0\",\"id\":1,"
            + "\"result\":{\"tools\":[]},\"error\":{\"code\":-32603,\"message\":\"failed\"}}"; //$NON-NLS-1$
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> BackendProfileChannel.parseResolution(ProfileEndpoint.legacyDefault(), legacyStatus(), tools));
        assertTrue(error.getMessage().contains("tools/list response must contain exactly one")); //$NON-NLS-1$
    }

    @Test
    public void statusResponseRequiresValidId()
    {
        String status = "{\"jsonrpc\":\"2.0\",\"result\":{\"content\":[]}}"; //$NON-NLS-1$
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> BackendProfileChannel.parseResolution(ProfileEndpoint.legacyDefault(), status, validTools()));
        assertTrue(error.getMessage().contains("get_server_status response id is missing or invalid")); //$NON-NLS-1$
    }

    @Test
    public void toolsListResponseRequiresValidId()
    {
        String tools = "{\"jsonrpc\":\"2.0\",\"result\":{\"tools\":[]}}"; //$NON-NLS-1$
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> BackendProfileChannel.parseResolution(ProfileEndpoint.legacyDefault(), legacyStatus(), tools));
        assertTrue(error.getMessage().contains("tools/list response id is missing or invalid")); //$NON-NLS-1$
    }

    @Test
    public void resolutionResponsesMustMatchTheirNumericRequestIds()
    {
        IllegalArgumentException statusMismatch = assertThrows(IllegalArgumentException.class,
            () -> BackendProfileChannel.parseResolution(ProfileEndpoint.legacyDefault(), legacyStatus(),
                Long.valueOf(2L), validTools(), Long.valueOf(1L)));
        assertTrue(statusMismatch.getMessage().contains("get_server_status response id does not match")); //$NON-NLS-1$

        IllegalArgumentException toolsMismatch = assertThrows(IllegalArgumentException.class,
            () -> BackendProfileChannel.parseResolution(ProfileEndpoint.legacyDefault(), legacyStatus(),
                Long.valueOf(1L), validTools(), Long.valueOf(2L)));
        assertTrue(toolsMismatch.getMessage().contains("tools/list response id does not match")); //$NON-NLS-1$
    }

    private static String envelope(String structured)
    {
        return "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"structuredContent\":" //$NON-NLS-1$
            + structured + "}}"; //$NON-NLS-1$
    }

    private static String legacyStatus()
    {
        return "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{" //$NON-NLS-1$
            + "\"content\":[{\"type\":\"text\",\"text\":\"legacy status\"}]}}"; //$NON-NLS-1$
    }

    private static String validTools()
    {
        return "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"tools\":[]}}"; //$NON-NLS-1$
    }
}
