/**
 * MCP Server for EDT - Tests
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

/**
 * Wire responses for interrupted calls must remain usable by clients enforcing outputSchema.
 */
public class ActiveToolCallTest
{
    @Test
    public void userSignalResponsesAreErrorsWithoutStructuredContent() throws Exception
    {
        for (UserSignal.SignalType type : UserSignal.SignalType.values())
        {
            // Exercise both supported JSON-RPC id types without changing session or call identity.
            Object requestId = type == UserSignal.SignalType.CANCEL ? "cancel-42" : Integer.valueOf(42); //$NON-NLS-1$
            HttpExchange exchange = mock(HttpExchange.class);
            Headers headers = new Headers();
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            when(exchange.getResponseHeaders()).thenReturn(headers);
            when(exchange.getResponseBody()).thenReturn(body);
            ActiveToolCall call = new ActiveToolCall(exchange, "get_metadata_details", requestId, //$NON-NLS-1$
                "call-42", "session-42"); //$NON-NLS-1$ //$NON-NLS-2$

            assertTrue(call.sendSignalResponse(new UserSignal(type, "stop this call"))); //$NON-NLS-1$

            JsonObject response = JsonParser.parseString(body.toString(StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals("2.0", response.get("jsonrpc").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
            assertEquals(new Gson().toJsonTree(requestId), response.get("id")); //$NON-NLS-1$
            assertFalse("an interrupted tool returns a tool error, not a JSON-RPC error", response.has("error")); //$NON-NLS-1$ //$NON-NLS-2$
            JsonObject result = response.getAsJsonObject("result"); //$NON-NLS-1$
            assertTrue("an interrupted call must explicitly carry isError:true", result.has("isError")); //$NON-NLS-1$ //$NON-NLS-2$
            assertTrue(result.get("isError").getAsJsonPrimitive().isBoolean()); //$NON-NLS-1$
            assertTrue(result.get("isError").getAsBoolean()); //$NON-NLS-1$
            assertFalse("the signal has no output-schema result", result.has("structuredContent")); //$NON-NLS-1$ //$NON-NLS-2$
            assertEquals(1, result.getAsJsonArray("content").size()); //$NON-NLS-1$
            JsonObject content = result.getAsJsonArray("content").get(0).getAsJsonObject(); //$NON-NLS-1$
            assertEquals("text", content.get("type").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
            String text = content.get("text").getAsString(); //$NON-NLS-1$
            assertTrue(text.contains("stop this call")); //$NON-NLS-1$
            assertTrue(text.contains("Signal Type: " + type.name())); //$NON-NLS-1$
            assertTrue(text.contains("Tool: get_metadata_details")); //$NON-NLS-1$
            assertEquals("call-42", call.getCallId()); //$NON-NLS-1$
            assertEquals("session-42", call.getSessionId()); //$NON-NLS-1$

            // A later normal result cannot overwrite the interruption response.
            int responseLength = body.size();
            assertTrue(call.hasResponded());
            assertFalse(call.sendNormalResponse("late result")); //$NON-NLS-1$
            assertEquals(responseLength, body.size());
            verify(exchange).sendResponseHeaders(200, responseLength);
            verify(exchange).close();
        }
    }
}
