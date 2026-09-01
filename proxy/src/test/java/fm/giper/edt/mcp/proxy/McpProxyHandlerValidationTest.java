/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.proxy;

import static org.junit.Assert.assertThrows;

import java.util.List;

import org.junit.Test;

/** Pure validation tests: no HTTP client or loopback socket is created. */
public class McpProxyHandlerValidationTest
{
    private static final String VALID = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"tools\":[{\"name\":\"echo\"}]}}"; //$NON-NLS-1$

    @Test
    public void testActualDonorToolsListMustRemainStrictlyValid()
    {
        ProfileGroupSnapshot group = groupFor(VALID);

        assertThrows(IllegalArgumentException.class,
            () -> McpProxyHandler.validateToolsListForGroup(group,
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"tools\":{}}}")); //$NON-NLS-1$
    }

    @Test
    public void testActualDonorToolsListMustMatchConfirmedFingerprint()
    {
        ProfileGroupSnapshot group = groupFor(VALID);

        assertThrows(IllegalArgumentException.class,
            () -> McpProxyHandler.validateToolsListForGroup(group,
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"tools\":[{\"name\":\"changed\"}]}}")); //$NON-NLS-1$
    }

    @Test
    public void testConfirmedUnchangedToolsListIsAccepted()
    {
        McpProxyHandler.validateToolsListForGroup(groupFor(VALID), VALID);
    }

    private static ProfileGroupSnapshot groupFor(String raw)
    {
        Backend donor = new Backend(8765, null, 5);
        return new ProfileGroupSnapshot(ProfileEndpoint.legacyDefault(), ProfileEndpoint.DEFAULT_PROFILE_ID,
            null, BackendProfileChannel.fingerprintToolsList(raw), donor, List.of(donor), List.of());
    }
}
