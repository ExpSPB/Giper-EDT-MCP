/**
 * MCP Server for EDT - Tests
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server.preferences;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.eclipse.jface.preference.PreferenceStore;
import org.junit.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import fm.giper.edt.mcp.server.Activator;
import fm.giper.edt.mcp.server.McpServer;

/**
 * Exercises the real endpoint/config helpers without creating SWT controls or a listener.
 * The unconstructed tab has no spinner or token field: a copy for a running listener must be
 * derived from the live server and saved preferences, not pending widget values.
 */
public class GeneralTabEndpointTest
{
    @Test
    public void testTheCopiedEndpointUsesTheRunningPortAndDefaultPath() throws Exception
    {
        withTab("", false, (tab, store, server) -> {
            store.setValue(PreferenceConstants.PREF_PORT, 9202);
            assertEquals("http://localhost:9201/mcp", invoke(tab, "serviceUrl"));
            assertEquals("http://localhost:9201/mcp", copiedServer(tab).get("url").getAsString());
        });
    }

    @Test
    public void testConfigCarriesTheSavedNormalizedTokenAndEscapesJson() throws Exception
    {
        withTab("  saved-\"quote\"-\\slash  ", false, (tab, store, server) -> {
            JsonObject config = copiedServer(tab);
            assertEquals("http", config.get("type").getAsString());
            assertEquals("Bearer saved-\"quote\"-\\slash",
                config.getAsJsonObject("headers").get("Authorization").getAsString());
            assertFalse((Boolean)invoke(tab, "endpointRefusesItsOwnConfiguration"));
        });
    }

    @Test
    public void testWhitespaceTokenOmitsTheAuthHeaderOnLoopback() throws Exception
    {
        withTab(" \t ", false, (tab, store, server) -> {
            assertFalse(copiedServer(tab).has("headers"));
            assertFalse((Boolean)invoke(tab, "endpointRefusesItsOwnConfiguration"));
        });
    }

    @Test
    public void testLockoutUsesTheLiveBindEvenWhenThePreferenceWasUnticked() throws Exception
    {
        withTab("", true, (tab, store, server) -> {
            store.setValue(PreferenceConstants.PREF_ALLOW_REMOTE_ACCESS, false);
            assertTrue((Boolean)invoke(tab, "endpointRefusesItsOwnConfiguration"));
            when(server.isBoundRemotely()).thenReturn(false);
            store.setValue(PreferenceConstants.PREF_ALLOW_REMOTE_ACCESS, true);
            assertFalse((Boolean)invoke(tab, "endpointRefusesItsOwnConfiguration"));
        });
    }

    @Test
    public void testUnpresentableSavedTokenIsReportedByTheRealAuthorizer() throws Exception
    {
        withTab("\u043f\u0430\u0440\u043e\u043b\u044c", false, (tab, store, server) -> {
            assertTrue((Boolean)invoke(tab, "endpointRefusesItsOwnConfiguration"));
            when(server.isRunning()).thenReturn(false);
            assertFalse("a stopped endpoint must not report a live listener lockout",
                (Boolean)invoke(tab, "endpointRefusesItsOwnConfiguration"));
        });
    }

    private static JsonObject copiedServer(GeneralTab tab) throws Exception
    {
        JsonObject config = JsonParser.parseString((String)invoke(tab, "mcpClientConfigJson"))
            .getAsJsonObject();
        JsonObject servers = config.getAsJsonObject("mcpServers");
        assertEquals("copy emits exactly the default endpoint", 1, servers.size());
        return servers.getAsJsonObject("EDT.MCP");
    }

    private static Object invoke(GeneralTab tab, String methodName) throws Exception
    {
        Method method = GeneralTab.class.getDeclaredMethod(methodName);
        method.setAccessible(true);
        return method.invoke(tab);
    }

    @FunctionalInterface
    private interface TabAssertion
    {
        void check(GeneralTab tab, PreferenceStore store, McpServer server) throws Exception;
    }

    private static void withTab(String token, boolean remote, TabAssertion assertion) throws Exception
    {
        PreferenceStore store = new PreferenceStore();
        store.setValue(PreferenceConstants.PREF_AUTH_TOKEN, token);
        McpServer server = mock(McpServer.class);
        when(server.isRunning()).thenReturn(true);
        when(server.getPort()).thenReturn(9201);
        when(server.isBoundRemotely()).thenReturn(remote);
        Activator activator = mock(Activator.class);
        when(activator.getMcpServer()).thenReturn(server);
        when(activator.getPreferenceStore()).thenReturn(store);
        GeneralTab tab = mock(GeneralTab.class, CALLS_REAL_METHODS);
        Field storeField = GeneralTab.class.getDeclaredField("store");
        storeField.setAccessible(true);
        storeField.set(tab, store);
        Field pluginField = Activator.class.getDeclaredField("plugin");
        pluginField.setAccessible(true);
        Object saved = pluginField.get(null);
        pluginField.set(null, activator);
        try
        {
            assertion.check(tab, store, server);
        }
        finally
        {
            pluginField.set(null, saved);
        }
    }
}
