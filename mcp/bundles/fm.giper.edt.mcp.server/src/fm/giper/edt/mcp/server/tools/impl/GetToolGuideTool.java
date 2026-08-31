/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server.tools.impl;

import java.util.Map;

import fm.giper.edt.mcp.server.protocol.JsonSchemaBuilder;
import fm.giper.edt.mcp.server.protocol.JsonUtils;
import fm.giper.edt.mcp.server.protocol.McpConstants;
import fm.giper.edt.mcp.server.protocol.McpRequestContext;
import fm.giper.edt.mcp.server.protocol.ToolResult;
import fm.giper.edt.mcp.server.profiles.ProfileToolPolicy;
import fm.giper.edt.mcp.server.tools.IMcpTool;
import fm.giper.edt.mcp.server.tools.McpToolRegistry;
import fm.giper.edt.mcp.server.utils.GuideRenderer;

/**
 * Serves the on-demand, full how-to guide for any registered tool: its
 * description, the complete parameter table (type, required, allowed values), and
 * the extended {@link IMcpTool#getGuide() guide} text kept OUT of the
 * always-loaded {@code tools/list} to save client context.
 * <p>
 * The guide is rendered by {@link GuideRenderer} from the target tool's own
 * {@code getDescription()} / {@code getInputSchema()} / {@code getGuide()}, so it
 * stays in sync with the tool automatically. The same content is also reachable as
 * the resource {@code guide://<toolName>}.
 * <p>
 * Read-only: the {@code get_} name prefix lets the central
 * {@code ToolAnnotationClassifier} mark this tool read-only and idempotent.
 */
public class GetToolGuideTool implements IMcpTool
{
    public static final String NAME = McpConstants.TOOL_GET_TOOL_GUIDE;

    /** Input param: exact name of the tool to document, as it appears in tools/list. */
    private static final String KEY_TOOL_NAME = "toolName"; //$NON-NLS-1$

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public String getDescription()
    {
        return "Retrieve detailed instructions for an MCP tool. Parameters and examples: " //$NON-NLS-1$
            + "get_tool_guide('get_tool_guide')."; //$NON-NLS-1$
    }

    @Override
    public ResponseType getResponseType()
    {
        return ResponseType.MARKDOWN;
    }

    @Override
    public String getInputSchema()
    {
        return JsonSchemaBuilder.object()
            .stringProperty(KEY_TOOL_NAME,
                "The exact name of the tool to document, as it appears in tools/list (e.g. 'read_module_source').", //$NON-NLS-1$
                true)
            .build();
    }

    @Override
    public String getResultFileName(Map<String, String> params)
    {
        String toolName = params != null ? params.get(KEY_TOOL_NAME) : null;
        return "guide-" + (toolName != null ? toolName : "tool") + ".md"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }

    @Override
    public String execute(Map<String, String> params)
    {
        return execute(params, McpRequestContext.legacyDefault());
    }

    @Override
    public String execute(Map<String, String> params, McpRequestContext context)
    {
        String err = JsonUtils.requireArgument(params, KEY_TOOL_NAME);
        if (err != null)
        {
            return err;
        }

        String toolName = params.get(KEY_TOOL_NAME);
        McpToolRegistry registry = McpToolRegistry.getInstance();
        IMcpTool tool = registry.getTool(toolName);
        if (tool == null)
        {
            return ToolResult.error("Unknown tool: " + toolName //$NON-NLS-1$
                + ". Call tools/list to see available tool names.").toJson(); //$NON-NLS-1$
        }

        if (context != null && context.getResolution() != null
            && context.getResolution().isExplicitEndpoint())
        {
            ProfileToolPolicy policy = new ProfileToolPolicy(context.getResolution(),
                registry.getAllTools());
            if (!policy.isGuideVisible(toolName))
            {
                return ToolResult.error(policy.deniedMessage(toolName)).toJson();
            }
        }

        return GuideRenderer.render(tool);
    }
}
