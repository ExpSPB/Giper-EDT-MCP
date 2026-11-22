/**
 * MCP Server for EDT
 * Adapted from AI-EDT, Copyright (C) 2026 Desko77 (https://github.com/Desko77)
 * Licensed under AGPL-3.0-or-later
 */
package fm.giper.edt.mcp.server.tools.impl;

import java.util.Map;
import java.util.Set;

import fm.giper.edt.mcp.server.protocol.JsonSchemaBuilder;
import fm.giper.edt.mcp.server.protocol.JsonUtils;
import fm.giper.edt.mcp.server.protocol.ToolResult;
import fm.giper.edt.mcp.server.tools.IMcpTool;
import fm.giper.edt.mcp.server.tools.reference.DependencyGraph.Options;
import fm.giper.edt.mcp.server.tools.reference.DependencyGraph.Result;
import fm.giper.edt.mcp.server.tools.reference.MetadataAnalysis;
import fm.giper.edt.mcp.server.tools.reference.MetadataDependencyGraph;
import fm.giper.edt.mcp.server.utils.PlatformFailures;

public class DependencyGraphTool implements IMcpTool
{
    public static final String NAME = "dependency_graph"; //$NON-NLS-1$

    @FunctionalInterface
    interface GraphReader
    {
        Result read(String project, String fqn, Options options);
    }

    private final GraphReader graph;

    public DependencyGraphTool()
    {
        this(MetadataDependencyGraph::read);
    }

    DependencyGraphTool(GraphReader graph)
    {
        this.graph = graph;
    }

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public String getDescription()
    {
        return "Trace direct and transitive metadata dependencies and cycles from one object. " //$NON-NLS-1$
            + "Parameters and examples: get_tool_guide('dependency_graph')."; //$NON-NLS-1$
    }

    @Override
    public String getInputSchema()
    {
        return JsonSchemaBuilder.object().stringProperty("projectName", "EDT project name", true) //$NON-NLS-1$ //$NON-NLS-2$
            .stringProperty(MetadataAnalysis.OBJECT_FQN, MetadataAnalysis.FQN_DESCRIPTION, true)
            .enumProperty("direction", "Incoming, outgoing, or both (default both)", "in", "out", "both") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
            .integerProperty("depth", "Maximum distance in edges from the root, 1-5 (default 2)") //$NON-NLS-1$ //$NON-NLS-2$
            .integerProperty("maxNodes", "Vertex limit including the root, 1-2000 (default 200)") //$NON-NLS-1$ //$NON-NLS-2$
            .integerProperty("maxEdges", "Unique directed edge limit, 1-5000 (default 500)").build(); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Override
    public String getOutputSchema()
    {
        return JsonSchemaBuilder.object().booleanProperty("success", "Whether the root was read", true) //$NON-NLS-1$ //$NON-NLS-2$
            .stringProperty("projectName", "EDT project name") //$NON-NLS-1$ //$NON-NLS-2$
            .stringProperty("root", "Canonical FQN of the root") //$NON-NLS-1$ //$NON-NLS-2$
            .enumProperty("direction", "Traversal direction", "in", "out", "both") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
            .integerProperty("depth", "Requested depth") //$NON-NLS-1$ //$NON-NLS-2$
            .property("nodes", "Visited vertices and their distances", """
                {"type":"array","items":{"type":"object","required":["fqn","depth"],
                "properties":{"fqn":{"type":"string"},"depth":{"type":"integer"}}}}""") //$NON-NLS-1$ //$NON-NLS-2$
            .property("edges", "Directed links and their occurrence counts", """
                {"type":"array","items":{"type":"object","required":["from","to","via","count"],
                "properties":{"from":{"type":"string"},"to":{"type":"string"},"via":{"type":"string"},
                "count":{"type":"integer"}}}}""") //$NON-NLS-1$ //$NON-NLS-2$
            .property("cycles", "Strongly connected groups of FQNs that contain a cycle", """
                {"type":"array","items":{"type":"array","items":{"type":"string"}}}""") //$NON-NLS-1$ //$NON-NLS-2$
            .booleanProperty("partial", "Whether the requested search was incomplete") //$NON-NLS-1$ //$NON-NLS-2$
            .stringArrayProperty("partialReasons", "Reasons why the requested search was incomplete").build(); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Override
    public ResponseType getResponseType()
    {
        return ResponseType.JSON;
    }

    @Override
    public String execute(Map<String, String> params)
    {
        String missing = JsonUtils.requireArguments(params, "projectName", MetadataAnalysis.OBJECT_FQN); //$NON-NLS-1$
        if (missing != null)
        {
            return missing;
        }
        try
        {
            String project = JsonUtils.extractStringArgument(params, "projectName"); //$NON-NLS-1$
            String fqn = JsonUtils.extractStringArgument(params, "objectFqn"); //$NON-NLS-1$
            String direction = JsonUtils.extractStringArgument(params, "direction"); //$NON-NLS-1$
            if (direction == null)
            {
                direction = "both"; //$NON-NLS-1$
            }
            if (!Set.of("in", "out", "both").contains(direction)) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            {
                return ToolResult.error("Unknown direction '" + direction + "'. Use in, out, or both.").toJson(); //$NON-NLS-1$ //$NON-NLS-2$
            }
            var options = new Options(direction, JsonUtils.extractStrictInt(params, "depth", 2, 1, 5), //$NON-NLS-1$
                JsonUtils.extractStrictInt(params, "maxNodes", 200, 1, 2000), //$NON-NLS-1$
                JsonUtils.extractStrictInt(params, "maxEdges", 500, 1, 5000)); //$NON-NLS-1$
            Result result = graph.read(project, fqn, options);
            return ToolResult.success().put("projectName", project).put("root", result.root()) //$NON-NLS-1$ //$NON-NLS-2$
                .put("direction", direction).put("depth", options.depth()).put("nodes", result.nodes()) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .put("edges", result.edges()).put("cycles", result.cycles()) //$NON-NLS-1$ //$NON-NLS-2$
                .put("partial", !result.partialReasons().isEmpty()).put("partialReasons", result.partialReasons()) //$NON-NLS-1$ //$NON-NLS-2$
                .toJson();
        }
        catch (RuntimeException e)
        {
            return ToolResult.error(PlatformFailures.describe(e)).toJson();
        }
    }
}
