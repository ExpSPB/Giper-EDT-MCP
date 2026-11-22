/**
 * MCP Server for EDT
 * Copyright (C) 2026 ExpSPB (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server.tools.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.stream.Stream;

import org.eclipse.core.resources.IProject;

import com._1c.g5.v8.bm.integration.IBmModel;
import com._1c.g5.v8.dt.core.platform.IDtProject;
import com._1c.g5.v8.dt.core.platform.IDtProjectManager;
import com._1c.g5.v8.dt.validation.marker.IMarkerManager;
import com._1c.g5.v8.dt.validation.marker.Marker;
import com._1c.g5.v8.dt.validation.marker.MarkerFilter;
import com._1c.g5.v8.dt.validation.marker.MarkerSeverity;

import fm.giper.edt.mcp.server.Activator;
import fm.giper.edt.mcp.server.protocol.JsonSchemaBuilder;
import fm.giper.edt.mcp.server.protocol.JsonUtils;
import fm.giper.edt.mcp.server.protocol.ToolResult;
import fm.giper.edt.mcp.server.tools.IMcpTool;
import fm.giper.edt.mcp.server.tools.impl.GetProjectErrorsTool.AddressResolution;
import fm.giper.edt.mcp.server.tools.impl.GetProjectErrorsTool.ErrorInfo;
import fm.giper.edt.mcp.server.utils.BmModelResolver;
import fm.giper.edt.mcp.server.utils.BmTransactions;
import fm.giper.edt.mcp.server.utils.DiagnosticBaselineStore;
import fm.giper.edt.mcp.server.utils.DiagnosticBaselineStore.Diff;
import fm.giper.edt.mcp.server.utils.DiagnosticBaselineStore.Finding;
import fm.giper.edt.mcp.server.utils.DiagnosticBaselineStore.Snapshot;
import fm.giper.edt.mcp.server.utils.MarkerRows;
import fm.giper.edt.mcp.server.utils.PlatformFailures;
import fm.giper.edt.mcp.server.utils.ProjectContext;
import fm.giper.edt.mcp.server.utils.ProjectStateChecker;

/** Captures the EDT marker index for subsequent comparisons in get_project_errors. */
public class DiagnosticBaselineTool implements IMcpTool
{
    public static final String NAME = "diagnostic_baseline"; //$NON-NLS-1$
    static final int COLLECTION_LIMIT = 5_000;
    private static final String OBJECT_FQNS = "objectFqns"; //$NON-NLS-1$
    private static final String SAVE_HINT = "Run diagnostic_baseline with operation=save and this projectName."; //$NON-NLS-1$

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public String getDescription()
    {
        return "Save or clear one project's diagnostic baseline for get_project_errors sinceBaseline. " //$NON-NLS-1$
            + "Full parameters and examples: call get_tool_guide('diagnostic_baseline')."; //$NON-NLS-1$
    }

    @Override
    public String getInputSchema()
    {
        return JsonSchemaBuilder.object()
            .enumProperty("operation", "Save current diagnostics or clear a saved baseline.", true, "save", "clear") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            .stringProperty("projectName", "Exact EDT project name.", true) //$NON-NLS-1$ //$NON-NLS-2$
            .stringArrayProperty(OBJECT_FQNS, "Save only. Exact model addresses; omit or pass [] for the whole project.") //$NON-NLS-1$
            .build();
    }

    @Override
    public String getOutputSchema()
    {
        return JsonSchemaBuilder.object()
            .booleanProperty("success", "Whether the operation succeeded.", true) //$NON-NLS-1$ //$NON-NLS-2$
            .stringProperty("projectName", "Project whose baseline was changed.") //$NON-NLS-1$ //$NON-NLS-2$
            .enumProperty("operation", "Completed operation.", false, "save", "clear") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            .booleanProperty("cleared", "Clear only. Whether a baseline file was removed.") //$NON-NLS-1$ //$NON-NLS-2$
            .stringProperty("savedAt", "Save only. Snapshot timestamp in UTC.") //$NON-NLS-1$ //$NON-NLS-2$
            .stringArrayProperty("scope", "Save only. Requested exact addresses, or [] for the whole project.") //$NON-NLS-1$ //$NON-NLS-2$
            .integerProperty("diagnosticsTotal", "Save only. Number of saved diagnostics, counting duplicates.") //$NON-NLS-1$ //$NON-NLS-2$
            .build();
    }

    @Override
    public ResponseType getResponseType()
    {
        return ResponseType.JSON;
    }

    @Override
    public String execute(Map<String, String> params)
    {
        String projectName = JsonUtils.extractStringArgument(params, "projectName"); //$NON-NLS-1$
        String operation = JsonUtils.extractStringArgument(params, "operation"); //$NON-NLS-1$
        List<String> addresses = JsonUtils.extractArrayArgument(params, OBJECT_FQNS);
        if (projectName == null || projectName.isBlank())
        {
            return ToolResult.error("Pass the exact EDT projectName to manage its diagnostic baseline.").toJson(); //$NON-NLS-1$
        }
        if (!"save".equals(operation) && !"clear".equals(operation)) //$NON-NLS-1$ //$NON-NLS-2$
        {
            return ToolResult.error("Use operation=save or operation=clear.").toJson(); //$NON-NLS-1$
        }
        if (addresses != null && addresses.stream().anyMatch(value -> value == null || value.isBlank()))
        {
            return ToolResult.error("objectFqns must contain non-empty exact model addresses.").toJson(); //$NON-NLS-1$
        }
        List<String> scope = addresses == null ? List.of() : addresses.stream().map(String::trim).toList();
        try
        {
            ToolResult result = ToolResult.success().put("projectName", projectName).put("operation", operation); //$NON-NLS-1$ //$NON-NLS-2$
            if ("clear".equals(operation)) //$NON-NLS-1$
            {
                if (!scope.isEmpty())
                {
                    return ToolResult.error("Omit objectFqns when clearing the project's baseline.").toJson(); //$NON-NLS-1$
                }
                return result.put("cleared", store().clear(projectName)).toJson(); //$NON-NLS-1$
            }

            IProject project = readyProject(projectName);
            Activator plugin = Activator.getDefault();
            AddressResolution resolution = resolve(project, scope, plugin);
            if (resolution.error != null)
            {
                return resolution.error;
            }
            if (!resolution.notFound.isEmpty() || !resolution.unsupported.isEmpty()
                || !resolution.incompleteFor.isEmpty())
            {
                return ToolResult.error("Cannot save the baseline scope. Unresolved objectFqns: " //$NON-NLS-1$
                    + resolution.notFound + ". Unsupported: " + resolution.unsupported //$NON-NLS-1$
                    + ". Use get_metadata_objects to verify the addresses and retry.").toJson(); //$NON-NLS-1$
            }
            Set<String> variants = resolvedVariants(resolution, projectName);
            Filters filters = new Filters(variants, Set.of(), false, null, null, Set.of());
            Capture capture = collect(project, plugin, filters);
            if (!capture.complete())
            {
                return ToolResult.error("Cannot save a partial diagnostic baseline. " + capture.warning() //$NON-NLS-1$
                    + " Wait for validation to finish or narrow objectFqns, then retry.").toJson(); //$NON-NLS-1$
            }

            checkInterrupted();
            Snapshot snapshot = store().save(projectName, scope, variants.stream().sorted().toList(),
                capture.diagnostics().stream().map(Diagnostic::finding).toList());
            return result.put("savedAt", snapshot.savedAt().toString()).put("scope", snapshot.scope()) //$NON-NLS-1$ //$NON-NLS-2$
                .put("diagnosticsTotal", snapshot.findings().size()).toJson(); //$NON-NLS-1$
        }
        catch (IOException | RuntimeException e)
        {
            return ToolResult.error("Cannot " + operation + " diagnostic baseline for '" + projectName + "': " //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                + PlatformFailures.describe(e)).toJson();
        }
    }

    static String compare(String projectName, MarkerSeverity severity, String checkId, List<String> objects,
        List<String> objectFqns, List<String> modulePaths, int limit, int offset, boolean detailed)
    {
        if (projectName == null || projectName.isBlank())
        {
            return ToolResult.error("sinceBaseline=true requires the exact EDT projectName.").toJson(); //$NON-NLS-1$
        }
        try
        {
            IProject project = readyProject(projectName);
            Snapshot snapshot = store().load(projectName);
            if (snapshot == null)
            {
                return ToolResult.error("No diagnostic baseline for '" + projectName + "'. " + SAVE_HINT).toJson(); //$NON-NLS-1$ //$NON-NLS-2$
            }
            Activator plugin = Activator.getDefault();
            AddressResolution resolution = resolve(project, objectFqns, plugin);
            if (resolution.error != null)
            {
                return resolution.error;
            }
            if (!objectFqns.isEmpty() && resolution.resolved.isEmpty())
            {
                return ToolResult.error(GetProjectErrorsTool.unscannableRefusal(resolution)).toJson();
            }
            boolean exact = !objectFqns.isEmpty();
            Set<String> requested = exact ? resolvedVariants(resolution, projectName)
                : GetProjectErrorsTool.scanFilterVariants(objects, false);
            Filters filters = new Filters(Set.copyOf(snapshot.scopeVariants()), requested, exact, severity, checkId,
                MarkerRows.modulePathKeys(modulePaths));
            Capture capture = collect(project, plugin, filters);
            List<Finding> before = snapshot.findings().stream().filter(filters::matches).toList();
            Diff<Diagnostic> diff = DiagnosticBaselineStore.compare(before, capture.diagnostics(), Diagnostic::finding);
            List<ErrorInfo> added = diff.added().stream().map(Diagnostic::row).toList();
            StringBuilder notes = new StringBuilder();
            GetProjectErrorsTool.appendModulePathsNotFound(notes, projectName, modulePaths);
            return comparisonPayload(snapshot, added, diff.resolved(), capture.warning(), resolution,
                limit, offset, detailed, notes.toString());
        }
        catch (IOException e)
        {
            return ToolResult.error("Cannot read diagnostic baseline for '" + projectName + "': " //$NON-NLS-1$ //$NON-NLS-2$
                + PlatformFailures.describe(e) + ". " + SAVE_HINT).toJson(); //$NON-NLS-1$
        }
        catch (RuntimeException e)
        {
            return ToolResult.error("Cannot compare diagnostic baseline for '" + projectName + "': " //$NON-NLS-1$ //$NON-NLS-2$
                + PlatformFailures.describe(e)).toJson();
        }
    }

    static String comparisonPayload(Snapshot snapshot, List<ErrorInfo> added, int resolved, String warning,
        AddressResolution resolution, int limit, int offset, boolean detailed, String notes)
    {
        String savedAt = snapshot.savedAt().toString();
        StringBuilder report = new StringBuilder("**Diagnostic baseline:** ").append(savedAt).append('\n'); //$NON-NLS-1$
        if (!snapshot.scope().isEmpty())
        {
            report.append("**Baseline scope:** ").append(String.join(", ", snapshot.scope())).append('\n'); //$NON-NLS-1$ //$NON-NLS-2$
            report.append("The comparison covers only this saved scope and the request filters.\n"); //$NON-NLS-1$
        }
        boolean complete = warning == null && resolution.incompleteFor.isEmpty();
        if (complete)
        {
            report.append("**Resolved since baseline:** ").append(resolved).append('\n'); //$NON-NLS-1$
        }
        else
        {
            report.append("\n> The comparison is incomplete. The resolved count is unknown."); //$NON-NLS-1$
            if (warning != null)
            {
                report.append(' ').append(warning);
            }
            report.append('\n');
        }
        report.append('\n');

        int start = Math.min(offset, added.size());
        List<ErrorInfo> page = added.subList(start, Math.min(start + limit, added.size()));
        int shown = 0;
        if (!added.isEmpty())
        {
            shown = GetProjectErrorsTool.appendProblemsPage(report, page, offset, added.size(), detailed,
                GetProjectErrorsTool.ADDRESS_TABLE_BUDGET_CHARS);
        }
        else
        {
            report.append(complete ? "No new diagnostics in the comparison scope.\n" //$NON-NLS-1$
                : "No new diagnostics among the markers read.\n"); //$NON-NLS-1$
        }
        GetProjectErrorsTool.appendObjectsNotFoundWarning(report, resolution.notFound);
        GetProjectErrorsTool.appendObjectsUnsupportedWarning(report, resolution.unsupported);
        GetProjectErrorsTool.appendIncompleteScopeWarning(report, resolution.incompleteFor);
        report.append(notes);
        ToolResult payload = GetProjectErrorsTool.problemPagePayload(report.toString(), page.subList(0, shown),
            resolution, added.size(), offset).put("baselineSavedAt", savedAt).put("baselineScope", snapshot.scope()) //$NON-NLS-1$ //$NON-NLS-2$
            .put("comparisonComplete", complete); //$NON-NLS-1$
        if (complete)
        {
            payload.put("resolvedSinceBaseline", resolved); //$NON-NLS-1$
        }
        return payload.toJson();
    }

    private static DiagnosticBaselineStore store()
    {
        return new DiagnosticBaselineStore(Activator.getDefault().getStateLocation()
            .append("diagnostic-baselines").toFile().toPath()); //$NON-NLS-1$
    }

    private static IProject readyProject(String projectName)
    {
        ProjectContext context = ProjectContext.of(projectName);
        if (!context.exists())
        {
            throw new IllegalArgumentException(ProjectContext.notFoundMessage(projectName));
        }
        checkReady(context.project());
        return context.project();
    }

    private static void checkReady(IProject project)
    {
        String error = ProjectStateChecker.checkReadyOrError(project);
        if (error != null)
        {
            throw new IllegalStateException(error);
        }
    }

    private static AddressResolution resolve(IProject project, List<String> scope, Activator plugin)
    {
        return scope.isEmpty() ? new AddressResolution()
            : GetProjectErrorsTool.resolveAddresses(scope, List.of(project), plugin.getBmModelManager());
    }

    private static Set<String> resolvedVariants(AddressResolution resolution, String projectName)
    {
        return GetProjectErrorsTool.filterVariantsByProject(resolution.scopeByProject)
            .getOrDefault(projectName, Set.of());
    }

    private static Capture collect(IProject project, Activator plugin, Filters filters)
    {
        IMarkerManager manager = plugin.getMarkerManager();
        IBmModel model = BmModelResolver.resolve(project, plugin.getBmModelManager()).getModel();
        if (manager == null || model == null)
        {
            throw new IllegalStateException("The project's marker index or BM model is unavailable. Wait for EDT and retry."); //$NON-NLS-1$
        }
        IDtProjectManager projects = plugin.getDtProjectManager();
        IDtProject dtProject = projects == null ? null : projects.getDtProject(project);
        Capture capture = BmTransactions.read(model, "CollectDiagnosticBaseline", //$NON-NLS-1$
            (tx, pm) -> scan(project, dtProject, plugin, manager, filters));
        checkReady(project);
        return capture;
    }

    private static Capture scan(IProject project, IDtProject dtProject, Activator plugin,
        IMarkerManager manager, Filters filters)
    {
        var checks = plugin.getCheckRepository();
        var fixes = plugin.getFixRepository();
        List<Diagnostic> diagnostics = new ArrayList<>();
        int[] unreadable = new int[1];
        int failedChecks = 0;
        try (Stream<Marker> stream = manager.markers(MarkerFilter.createProjectFilter(project)))
        {
            Iterator<Marker> markers = stream.iterator();
            while (markers.hasNext())
            {
                checkInterrupted();
                Marker marker = markers.next();
                // Delay filtering by check so a failed symbolic lookup cannot masquerade as no match.
                MarkerRows.Match match = MarkerRows.comparableMatchOf(marker, filters.severity(), filters.saved(),
                    filters.requested(), filters.modulePaths(), unreadable, filters.exact());
                if (match == null)
                {
                    continue;
                }
                ErrorInfo row = MarkerRows.buildComparableRow(marker, match, dtProject, checks, fixes,
                    unreadable, ErrorInfo::new);
                if (row == null)
                {
                    failedChecks++;
                    continue;
                }
                MarkerSeverity severity = marker.getSeverity();
                Finding finding = new Finding(row.objectPresentation, row.modulePath, row.checkId, row.checkCode,
                    row.contributorId, row.message, (severity == null ? MarkerSeverity.NONE : severity).name());
                if (filters.matches(finding))
                {
                    if (diagnostics.size() == COLLECTION_LIMIT)
                    {
                        return new Capture(diagnostics, unreadable[0], failedChecks, true);
                    }
                    diagnostics.add(new Diagnostic(finding, row));
                }
            }
        }
        return new Capture(diagnostics, unreadable[0], failedChecks, false);
    }

    private static void checkInterrupted()
    {
        if (Thread.currentThread().isInterrupted())
        {
            throw new CancellationException("Diagnostic scan was interrupted."); //$NON-NLS-1$
        }
    }

    private record Filters(Set<String> saved, Set<String> requested, boolean exact,
        MarkerSeverity severity, String checkId, Set<String> modulePaths)
    {
        boolean matches(Finding finding)
        {
            return (severity == null || severity.name().equals(finding.severity()))
                && (checkId == null || checkId.isEmpty()
                    || MarkerRows.checkIdMatches(finding.shortUid(), finding.checkId(), checkId))
                && (modulePaths.isEmpty() || MarkerRows.modulePathMatches(finding.modulePath(), modulePaths))
                && matchesLocation(saved, finding.location(), true)
                && matchesLocation(requested, finding.location(), exact);
        }

        private static boolean matchesLocation(Set<String> variants, String location, boolean exact)
        {
            // These detached findings have already resolved a presentation in the BM read.
            return !MarkerRows.excludedByObjectsFilter(variants, true, location, null, exact);
        }
    }

    private record Diagnostic(Finding finding, ErrorInfo row)
    {
    }

    private record Capture(List<Diagnostic> diagnostics, int unreadable, int failedChecks, boolean capped)
    {
        boolean complete()
        {
            return unreadable == 0 && failedChecks == 0 && !capped;
        }

        String warning()
        {
            if (capped)
            {
                return "The scan reached the " + COLLECTION_LIMIT + "-diagnostic collection limit."; //$NON-NLS-1$ //$NON-NLS-2$
            }
            if (failedChecks > 0)
            {
                return "Could not read the registered check for " + failedChecks + " markers."; //$NON-NLS-1$ //$NON-NLS-2$
            }
            return unreadable == 0 ? null : "Could not resolve the location of " + unreadable + " markers."; //$NON-NLS-1$ //$NON-NLS-2$
        }
    }
}
