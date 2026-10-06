/**
 * MCP Server for EDT - Tests
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */
package fm.giper.edt.mcp.server.transport;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.junit.Test;

import fm.giper.edt.mcp.server.protocol.ClientCapabilities;
import fm.giper.edt.mcp.server.protocol.McpProtocolHandler;
import fm.giper.edt.mcp.server.protocol.jsonrpc.JsonRpcRequest;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Both initialize paths must bound retention without dropping an explicit opt-out. */
public class BoundedClientCapabilitiesTest
{
    private static JsonRpcRequest request(JsonElement capabilities)
    {
        JsonRpcRequest request = new JsonRpcRequest();
        request.setParams(Map.of("capabilities", capabilities));
        return request;
    }

    private static JsonObject sizedDeclaration(int size, boolean structured)
    {
        JsonObject declaration = JsonParser.parseString(
            "{\"roots\":{},\"experimental\":{\"structuredContent\":" + structured
                + "},\"padding\":\"\"}").getAsJsonObject();
        declaration.addProperty("padding", "x".repeat(size - declaration.toString().length()));
        return declaration;
    }

    @Test
    public void oversizedOptOutSurvivesBothPathsWithoutRetainingOtherFields()
    {
        JsonRpcRequest request = request(sizedDeclaration(20_000, false));
        for (ClientCapabilities kept : new ClientCapabilities[] {
            McpProtocolHandler.parseClientCapabilities(request), McpHttpHandler.capabilitiesOf(request) })
        {
            assertFalse(kept.allowsStructuredContent());
            assertFalse(kept.has("roots"));
            assertFalse(kept.has("padding"));
            assertTrue(kept.getRaw().toString().length() < 100);
        }
    }

    @Test
    public void exactly4096CharactersAreRetainedAnd4097AreDistilled()
    {
        JsonRpcRequest boundary = request(sizedDeclaration(4096, false));
        assertTrue(McpProtocolHandler.parseClientCapabilities(boundary).has("roots"));
        assertTrue(McpHttpHandler.capabilitiesOf(boundary).has("roots"));
        JsonRpcRequest over = request(sizedDeclaration(4097, false));
        assertFalse(McpProtocolHandler.parseClientCapabilities(over).has("roots"));
        assertFalse(McpHttpHandler.capabilitiesOf(over).has("roots"));
    }

    @Test
    public void anotherClientCannotChangeTheRetainedOptOut()
    {
        ClientCapabilities optedOut = McpHttpHandler.capabilitiesOf(request(sizedDeclaration(6000, false)));
        assertSame(ClientCapabilities.ABSENT,
            McpHttpHandler.capabilitiesOf(request(sizedDeclaration(6000, true))));
        assertTrue(McpProtocolHandler.parseClientCapabilities(null).allowsStructuredContent());
        assertFalse(optedOut.allowsStructuredContent());
    }

    @Test
    public void projectionReferencesNoPartOfTheOriginalTree()
    {
        JsonObject source = sizedDeclaration(6000, false);
        ClientCapabilities kept = ClientCapabilities.distill(source);
        source.getAsJsonObject("experimental").addProperty("structuredContent", true);
        source.addProperty("roots", true);
        assertFalse(kept.allowsStructuredContent());
        assertFalse(kept.has("roots"));
    }

    @Test
    public void malformedAndJsonNullCapabilitiesKeepThePermissiveDefault()
    {
        for (String value : new String[] { "null", "false", "[]", "\"wrong\"" })
        {
            JsonRpcRequest request = request(JsonParser.parseString(value));
            assertSame(ClientCapabilities.ABSENT, McpProtocolHandler.parseClientCapabilities(request));
            assertSame(ClientCapabilities.ABSENT, McpHttpHandler.capabilitiesOf(request));
        }
        assertSame(ClientCapabilities.ABSENT, ClientCapabilities.distill(JsonParser.parseString(
            "{\"experimental\":{\"structuredContent\":\"false\"}}")));
    }
}
