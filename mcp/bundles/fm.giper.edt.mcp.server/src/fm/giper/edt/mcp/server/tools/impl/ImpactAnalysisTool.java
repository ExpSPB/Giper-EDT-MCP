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
import fm.giper.edt.mcp.server.tools.reference.MetadataAnalysis;
import fm.giper.edt.mcp.server.tools.reference.MetadataReferenceService;
import fm.giper.edt.mcp.server.tools.reference.MetadataReferenceService.ImpactResult;
import fm.giper.edt.mcp.server.utils.MarkdownUtils;
import fm.giper.edt.mcp.server.utils.PlatformFailures;

public class ImpactAnalysisTool implements IMcpTool
{
    public static final String NAME = "impact_analysis"; //$NON-NLS-1$

    @FunctionalInterface
    interface ImpactReader
    {
        ImpactResult read(String project, String fqn, boolean skipBsl, int limit);
    }

    private final ImpactReader references;

    public ImpactAnalysisTool()
    {
        this(new MetadataReferenceService()::analyzeImpact);
    }

    ImpactAnalysisTool(ImpactReader references)
    {
        this.references = references;
    }

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public String getDescription()
    {
        return "Review direct metadata and indexed BSL usages before changing one metadata object. " //$NON-NLS-1$
            + "Parameters and examples: get_tool_guide('impact_analysis')."; //$NON-NLS-1$
    }

    @Override
    public String getInputSchema()
    {
        return JsonSchemaBuilder.object().stringProperty("projectName", "EDT project name", true) //$NON-NLS-1$ //$NON-NLS-2$
            .stringProperty(MetadataAnalysis.OBJECT_FQN, MetadataAnalysis.FQN_DESCRIPTION, true)
            .enumProperty("action", "Planned change (default modify)", "modify", "rename", "delete") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
            .booleanProperty("skipBsl", "Search only metadata when true (default false)") //$NON-NLS-1$ //$NON-NLS-2$
            .integerProperty("limit", "Overall reference limit, 1-500 (default 100)").build(); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Override
    public ResponseType getResponseType()
    {
        return ResponseType.MARKDOWN;
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
            String action = JsonUtils.extractStringArgument(params, "action"); //$NON-NLS-1$
            if (action == null)
            {
                action = "modify"; //$NON-NLS-1$
            }
            if (!Set.of("modify", "rename", "delete").contains(action)) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            {
                return ToolResult.error("Unknown action '" + action + "'. Use modify, rename, or delete.").toJson(); //$NON-NLS-1$ //$NON-NLS-2$
            }
            String bsl = JsonUtils.extractStringArgument(params, "skipBsl"); //$NON-NLS-1$
            if (params.containsKey("skipBsl") && !"true".equalsIgnoreCase(bsl) && !"false".equalsIgnoreCase(bsl)) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            {
                return ToolResult.error("skipBsl must be true or false.").toJson(); //$NON-NLS-1$
            }
            boolean skipBsl = Boolean.parseBoolean(bsl);
            int limit = JsonUtils.extractStrictInt(params, "limit", 100, 1, 500); //$NON-NLS-1$
            return render(project, action, skipBsl, references.read(project, fqn, skipBsl, limit));
        }
        catch (RuntimeException e)
        {
            return ToolResult.error(PlatformFailures.describe(e)).toJson();
        }
    }

    static String render(String project, String action, boolean skipBsl, ImpactResult result)
    {
        StringBuilder report = new StringBuilder("# Impact analysis for ") //$NON-NLS-1$
            .append(MarkdownUtils.inlineCode(result.objectFqn())).append("\n\n") //$NON-NLS-1$
            .append("**Project:** ").append(MarkdownUtils.inlineCode(project)).append('\n') //$NON-NLS-1$
            .append("**Scope:** ").append(skipBsl ? "metadata" : "metadata and indexed BSL").append('\n') //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            .append("**Planned action:** ").append(action).append("\n\n"); //$NON-NLS-1$ //$NON-NLS-2$
        int count = result.references().size();
        if (result.incompleteReasons().isEmpty())
        {
            String severity = count == 0 ? "LOW" : count <= 10 ? "MEDIUM" : "HIGH"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            report.append("**Total references:** ").append(count).append('\n') //$NON-NLS-1$
                .append("**Severity:** ").append(severity).append('\n') //$NON-NLS-1$
                .append("**Recommendation:** ").append(recommendation(action, count)).append('\n'); //$NON-NLS-1$
        }
        else
        {
            report.append("**References found:** at least ").append(count).append('\n') //$NON-NLS-1$
                .append("**Incomplete search:** ").append(String.join(", ", result.incompleteReasons())).append(".\n"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        }

        if (!result.references().isEmpty())
        {
            report.append('\n').append(MarkdownUtils.tableHeader("Kind", "Source", "Feature", "Line")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            for (var reference : result.references())
            {
                String line = reference.line() > 0 ? Integer.toString(reference.line()) : ""; //$NON-NLS-1$
                report.append(MarkdownUtils.tableRow(reference.category(), reference.source(), reference.feature(), line));
            }
        }
        return report.toString();
    }

    private static String recommendation(String action, int count)
    {
        if (count == 0)
        {
            return "Review the stated search scope before proceeding."; //$NON-NLS-1$
        }
        return switch (action)
        {
            case "rename" -> "Use rename_metadata_object and verify the listed usages after refactoring."; //$NON-NLS-1$ //$NON-NLS-2$
            case "delete" -> "Review and migrate the listed usages before deleting the object."; //$NON-NLS-1$ //$NON-NLS-2$
            default -> "Review the listed usages and revalidate the affected objects after the change."; //$NON-NLS-1$
        };
    }
}
