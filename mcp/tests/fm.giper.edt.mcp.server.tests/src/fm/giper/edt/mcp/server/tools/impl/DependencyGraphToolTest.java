/**
 * MCP Server for EDT
 * Adapted from AI-EDT tests, Copyright (C) 2026 Desko77 (https://github.com/Desko77)
 * Licensed under AGPL-3.0-or-later
 */
package fm.giper.edt.mcp.server.tools.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.google.gson.JsonParser;

import fm.giper.edt.mcp.server.protocol.ToolResult;
import fm.giper.edt.mcp.server.tools.IMcpTool.ResponseType;
import fm.giper.edt.mcp.server.tools.reference.DependencyGraph.Node;
import fm.giper.edt.mcp.server.tools.reference.DependencyGraph.Options;
import fm.giper.edt.mcp.server.tools.reference.DependencyGraph.Result;

public class DependencyGraphToolTest
{
    @Test
    public void readsTheRequestedObjectWithDefaultsAndReturnsStructuredData()
    {
        var tool = new DependencyGraphTool((project, fqn, options) -> {
            assertEquals("Fixture", project);
            assertEquals("Catalog.Root", fqn);
            assertEquals(new Options("both", 2, 200, 500), options);
            return new Result(fqn, List.of(new Node(fqn, 0)), List.of(), List.of(), List.of());
        });
        assertEquals("dependency_graph", tool.getName());
        assertEquals(ResponseType.JSON, tool.getResponseType());
        assertTrue(tool.getDescription().contains("get_tool_guide('dependency_graph')"));
        var result = JsonParser.parseString(tool.execute(Map.of("projectName", "Fixture", "objectFqn", "Catalog.Root")))
            .getAsJsonObject();
        assertEquals("Catalog.Root", result.get("root").getAsString());
        assertEquals(1, result.getAsJsonArray("nodes").size());
        assertFalse(result.get("partial").getAsBoolean());
    }

    @Test
    public void refusesMissingOrInvalidArgumentsBeforeReadingTheModel()
    {
        var tool = new DependencyGraphTool((project, fqn, options) -> {
            fail("Invalid inputs must not read a model");
            throw new AssertionError();
        });
        assertTrue(ToolResult.isErrorPayload(tool.execute(Map.of("projectName", "Fixture"))));
        Map.of("direction", "reverse", "depth", "2.5", "maxNodes", "2001", "maxEdges", "0")
            .forEach((key, value) -> {
                String answer = tool.execute(Map.of("projectName", "Fixture", "objectFqn", "Catalog.Root", key, value));
                assertTrue(ToolResult.isErrorPayload(answer));
                assertTrue(answer.contains(key));
            });
    }
}
