/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server.tools;

import java.util.ArrayList;
import java.util.List;

import fm.giper.edt.mcp.server.Activator;
import fm.giper.edt.mcp.server.tools.impl.AdoptMetadataObjectTool;
import fm.giper.edt.mcp.server.tools.impl.ApplyQuickFixTool;
import fm.giper.edt.mcp.server.tools.impl.AskWorkmateTool;
import fm.giper.edt.mcp.server.tools.impl.BuildExternalObjectsTool;
import fm.giper.edt.mcp.server.tools.impl.CancelJobTool;
import fm.giper.edt.mcp.server.tools.impl.CleanProjectTool;
import fm.giper.edt.mcp.server.tools.impl.CreateGitBranchTool;
import fm.giper.edt.mcp.server.tools.impl.CreateInfobaseTool;
import fm.giper.edt.mcp.server.tools.impl.SetInfobaseCredentialsTool;
import fm.giper.edt.mcp.server.tools.impl.CreateLaunchConfigTool;
import fm.giper.edt.mcp.server.tools.impl.CreateMetadataTool;
import fm.giper.edt.mcp.server.tools.impl.CreateProjectTool;
import fm.giper.edt.mcp.server.tools.impl.DebugLaunchTool;
import fm.giper.edt.mcp.server.tools.impl.DebugStatusTool;
import fm.giper.edt.mcp.server.tools.impl.DebugYaxunitTestsTool;
import fm.giper.edt.mcp.server.tools.impl.DeleteInfobaseTool;
import fm.giper.edt.mcp.server.tools.impl.DeleteLaunchConfigTool;
import fm.giper.edt.mcp.server.tools.impl.DeleteMetadataTool;
import fm.giper.edt.mcp.server.tools.impl.DeleteProjectTool;
import fm.giper.edt.mcp.server.tools.impl.DcsTool;
import fm.giper.edt.mcp.server.tools.impl.EnableToolsetTool;
import fm.giper.edt.mcp.server.tools.impl.EvaluateExpressionTool;
import fm.giper.edt.mcp.server.tools.impl.ExportCommonPictureTool;
import fm.giper.edt.mcp.server.tools.impl.ExportConfigurationToXmlTool;
import fm.giper.edt.mcp.server.tools.impl.FindReferencesTool;
import fm.giper.edt.mcp.server.tools.impl.GenerateTranslationStringsTool;
import fm.giper.edt.mcp.server.tools.impl.GetApplicationsTool;
import fm.giper.edt.mcp.server.tools.impl.GetCheckDescriptionTool;
import fm.giper.edt.mcp.server.tools.impl.GetConfigurationPropertiesTool;
import fm.giper.edt.mcp.server.tools.impl.GetContentAssistTool;
import fm.giper.edt.mcp.server.tools.impl.GetEdtVersionTool;
import fm.giper.edt.mcp.server.tools.impl.GetEventLogTool;
import fm.giper.edt.mcp.server.tools.impl.GetFormLayoutSnapshotTool;
import fm.giper.edt.mcp.server.tools.impl.GetFormScreenshotTool;
import fm.giper.edt.mcp.server.tools.impl.GetJobStatusTool;
import fm.giper.edt.mcp.server.tools.impl.GetMarkersTool;
import fm.giper.edt.mcp.server.tools.impl.GetMcpHistoryTool;
import fm.giper.edt.mcp.server.tools.impl.GetMetadataDetailsTool;
import fm.giper.edt.mcp.server.tools.impl.GetMetadataObjectsTool;
import fm.giper.edt.mcp.server.tools.impl.GetMethodCallHierarchyTool;
import fm.giper.edt.mcp.server.tools.impl.GetModuleStructureTool;
import fm.giper.edt.mcp.server.tools.impl.GetObjectsByTagsTool;
import fm.giper.edt.mcp.server.tools.impl.GetOutgoingStructuresTool;
import fm.giper.edt.mcp.server.tools.impl.GetPlatformDocumentationTool;
import fm.giper.edt.mcp.server.tools.impl.GetProblemSummaryTool;
import fm.giper.edt.mcp.server.tools.impl.GetProfilingResultsTool;
import fm.giper.edt.mcp.server.tools.impl.GetProjectErrorsTool;
import fm.giper.edt.mcp.server.tools.impl.GetServerStatusTool;
import fm.giper.edt.mcp.server.tools.impl.GetSubsystemContentTool;
import fm.giper.edt.mcp.server.tools.impl.GetSymbolInfoTool;
import fm.giper.edt.mcp.server.tools.impl.GetTagsTool;
import fm.giper.edt.mcp.server.tools.impl.GetTemplateScreenshotTool;
import fm.giper.edt.mcp.server.tools.impl.GetToolGuideTool;
import fm.giper.edt.mcp.server.tools.impl.GetTranslationProjectInfoTool;
import fm.giper.edt.mcp.server.tools.impl.GetVariablesTool;
import fm.giper.edt.mcp.server.tools.impl.GoToDefinitionTool;
import fm.giper.edt.mcp.server.tools.impl.ImportConfigurationFromXmlTool;
import fm.giper.edt.mcp.server.tools.impl.ListBreakpointsTool;
import fm.giper.edt.mcp.server.tools.impl.ListCommonPicturesTool;
import fm.giper.edt.mcp.server.tools.impl.GitTool;
import fm.giper.edt.mcp.server.tools.impl.ListGitBranchesTool;
import fm.giper.edt.mcp.server.tools.impl.ListConfigurationsTool;
import fm.giper.edt.mcp.server.tools.impl.ListModulesTool;
import fm.giper.edt.mcp.server.tools.impl.ListProjectsTool;
import fm.giper.edt.mcp.server.tools.impl.ListSubsystemsTool;
import fm.giper.edt.mcp.server.tools.impl.ListToolsetsTool;
import fm.giper.edt.mcp.server.tools.impl.ReadMethodSourceTool;
import fm.giper.edt.mcp.server.tools.impl.ReadModuleSourceTool;
import fm.giper.edt.mcp.server.tools.impl.RemoveBreakpointTool;
import fm.giper.edt.mcp.server.tools.impl.RenameMetadataObjectTool;
import fm.giper.edt.mcp.server.tools.impl.ResumeTool;
import fm.giper.edt.mcp.server.tools.impl.ResyncToDiskTool;
import fm.giper.edt.mcp.server.tools.impl.RevalidateObjectsTool;
import fm.giper.edt.mcp.server.tools.impl.RunYaxunitTestsTool;
import fm.giper.edt.mcp.server.tools.impl.SearchInCodeTool;
import fm.giper.edt.mcp.server.tools.impl.SetBranchInfobaseTool;
import fm.giper.edt.mcp.server.tools.impl.SetBreakpointTool;
import fm.giper.edt.mcp.server.tools.impl.SetVariableTool;
import fm.giper.edt.mcp.server.tools.impl.ModifyMetadataTool;
import fm.giper.edt.mcp.server.tools.impl.StartProfilingTool;
import fm.giper.edt.mcp.server.tools.impl.StepTool;
import fm.giper.edt.mcp.server.tools.impl.StopProfilingTool;
import fm.giper.edt.mcp.server.tools.impl.SwitchGitBranchTool;
import fm.giper.edt.mcp.server.tools.impl.TerminateLaunchTool;
import fm.giper.edt.mcp.server.tools.impl.TranslateConfigurationTool;
import fm.giper.edt.mcp.server.tools.impl.UpdateDatabaseTool;
import fm.giper.edt.mcp.server.tools.impl.ValidateQueryTool;
import fm.giper.edt.mcp.server.tools.impl.ValidateXdtoPackageTool;
import fm.giper.edt.mcp.server.tools.impl.WaitForBreakTool;
import fm.giper.edt.mcp.server.tools.impl.WriteModuleSourceTool;

/**
 * Registers all built-in MCP tools into an {@link McpToolRegistry}.
 * <p>
 * Extracted from the HTTP transport ({@code McpServer}) so that the catalogue of
 * tools is no longer coupled to the transport: adding a tool touches only this
 * class, and the registry can be populated (and tested) without instantiating
 * the server. {@code McpServer.registerTools()} and {@code Activator} both
 * delegate here.
 */
public final class BuiltInToolRegistrar
{
    private BuiltInToolRegistrar()
    {
        // Utility class
    }

    /**
     * Publishes every built-in tool as the registry's catalogue.
     * <p>
     * The catalogue is built first and handed over in ONE step. This runs on every server
     * start, including a restart while clients - and the in-process bridge - are already
     * calling tools: filling the live registry one tool at a time would let them see a
     * half-populated catalogue and be told that a tool which exists is unknown.
     *
     * @param registry the registry to publish into (its previous contents are replaced)
     */
    public static void registerAll(McpToolRegistry registry)
    {
        List<IMcpTool> catalogue = new ArrayList<>();

        // Built-in tools
        catalogue.add(new GetEdtVersionTool());
        catalogue.add(new GetServerStatusTool());
        catalogue.add(new GetToolGuideTool());
        // Progressive tool disclosure meta-tools (core toolset)
        catalogue.add(new ListToolsetsTool());
        catalogue.add(new EnableToolsetTool());
        catalogue.add(new ListProjectsTool());
        catalogue.add(new GetConfigurationPropertiesTool());
        catalogue.add(new CleanProjectTool());
        catalogue.add(new RevalidateObjectsTool());
        catalogue.add(new ResyncToDiskTool());
        catalogue.add(new ExportConfigurationToXmlTool());
        catalogue.add(new ImportConfigurationFromXmlTool());
        catalogue.add(new BuildExternalObjectsTool());
        catalogue.add(new DeleteProjectTool());
        catalogue.add(new CreateProjectTool());
        catalogue.add(new GetProblemSummaryTool());
        catalogue.add(new ApplyQuickFixTool());
        catalogue.add(new GetProjectErrorsTool());
        catalogue.add(new GetMarkersTool());
        catalogue.add(new GetEventLogTool());
        catalogue.add(new GetMcpHistoryTool());
        catalogue.add(new ListGitBranchesTool());
        catalogue.add(new SwitchGitBranchTool());
        catalogue.add(new SetBranchInfobaseTool());
        catalogue.add(new CreateGitBranchTool());
        catalogue.add(new GitTool());
        catalogue.add(new GetCheckDescriptionTool());
        catalogue.add(new GetContentAssistTool());
        catalogue.add(new GetPlatformDocumentationTool());
        catalogue.add(new GetMetadataObjectsTool());
        catalogue.add(new GetMetadataDetailsTool());
        catalogue.add(new DcsTool());
        catalogue.add(new ListCommonPicturesTool());
        catalogue.add(new ExportCommonPictureTool());
        catalogue.add(new ListSubsystemsTool());
        catalogue.add(new GetSubsystemContentTool());
        catalogue.add(new FindReferencesTool());

        // Tag tools
        catalogue.add(new GetTagsTool());
        catalogue.add(new GetObjectsByTagsTool());

        // Application tools
        catalogue.add(new GetApplicationsTool());
        catalogue.add(new CreateInfobaseTool());
        catalogue.add(new SetInfobaseCredentialsTool());
        catalogue.add(new DeleteInfobaseTool());
        catalogue.add(new UpdateDatabaseTool());
        catalogue.add(new DebugLaunchTool());
        catalogue.add(new ListConfigurationsTool());
        catalogue.add(new CreateLaunchConfigTool());
        catalogue.add(new DeleteLaunchConfigTool());
        catalogue.add(new RunYaxunitTestsTool());
        catalogue.add(new AskWorkmateTool());
        catalogue.add(new GetJobStatusTool());
        catalogue.add(new CancelJobTool());
        catalogue.add(new TerminateLaunchTool());

        // Debug inspection tools (breakpoints + suspended state)
        catalogue.add(new SetBreakpointTool());
        catalogue.add(new RemoveBreakpointTool());
        catalogue.add(new ListBreakpointsTool());
        catalogue.add(new WaitForBreakTool());
        catalogue.add(new GetVariablesTool());
        catalogue.add(new SetVariableTool());
        catalogue.add(new StepTool());
        catalogue.add(new ResumeTool());
        catalogue.add(new EvaluateExpressionTool());
        catalogue.add(new DebugYaxunitTestsTool()); // NOSONAR deprecated EDT API used intentionally (no non-deprecated equivalent here)
        catalogue.add(new DebugStatusTool());
        catalogue.add(new StartProfilingTool());
        catalogue.add(new StopProfilingTool());
        catalogue.add(new GetProfilingResultsTool());

        // BSL code analysis tools
        catalogue.add(new ReadModuleSourceTool());
        catalogue.add(new WriteModuleSourceTool());
        catalogue.add(new GetModuleStructureTool());
        catalogue.add(new ListModulesTool());
        catalogue.add(new SearchInCodeTool());
        catalogue.add(new ReadMethodSourceTool());
        catalogue.add(new GetMethodCallHierarchyTool());
        catalogue.add(new GetOutgoingStructuresTool());
        catalogue.add(new GoToDefinitionTool());
        catalogue.add(new GetSymbolInfoTool());
        catalogue.add(new GetFormLayoutSnapshotTool());
        catalogue.add(new GetFormScreenshotTool());
        catalogue.add(new GetTemplateScreenshotTool());
        catalogue.add(new ValidateQueryTool());

        // Metadata refactoring tools (form members are created/edited/removed by their FQNs via
        // create/modify/delete_metadata; the former add_form_*/set_form_item_property/delete_form_item/
        // get_form_structure tools were folded into those + get_metadata_details and removed in F4b).
        catalogue.add(new RenameMetadataObjectTool());
        catalogue.add(new DeleteMetadataTool());
        catalogue.add(new CreateMetadataTool());
        catalogue.add(new ModifyMetadataTool());
        catalogue.add(new AdoptMetadataObjectTool());
        catalogue.add(new ValidateXdtoPackageTool());

        // LanguageTool translation tools
        catalogue.add(new GenerateTranslationStringsTool());
        catalogue.add(new TranslateConfigurationTool());
        catalogue.add(new GetTranslationProjectInfoTool());

        registry.replaceAll(catalogue);
        Activator.logInfo("Registered " + registry.getToolCount() + " MCP tools"); //$NON-NLS-1$ //$NON-NLS-2$
    }
}
