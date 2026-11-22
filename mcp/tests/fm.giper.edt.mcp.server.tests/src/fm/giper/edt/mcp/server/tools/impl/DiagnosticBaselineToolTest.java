/**
 * MCP Server for EDT - Tests
 * Copyright (C) 2026 ExpSPB (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server.tools.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.junit.Test;

import com._1c.g5.v8.dt.core.platform.IDtProject;
import com._1c.g5.v8.dt.validation.marker.Marker;
import com._1c.g5.v8.dt.validation.marker.MarkerSeverity;
import com.e1c.g5.v8.dt.check.settings.ICheckRepository;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import fm.giper.edt.mcp.server.protocol.ToolResult;
import fm.giper.edt.mcp.server.tools.IMcpTool.ResponseType;
import fm.giper.edt.mcp.server.tools.impl.GetProjectErrorsTool.AddressResolution;
import fm.giper.edt.mcp.server.tools.impl.GetProjectErrorsTool.ErrorInfo;
import fm.giper.edt.mcp.server.utils.DiagnosticBaselineStore.Snapshot;
import fm.giper.edt.mcp.server.utils.MarkerRows;

public class DiagnosticBaselineToolTest
{
    private static final Snapshot BASELINE =
        new Snapshot(Instant.parse("2026-11-22T09:00:00Z"), List.of(), List.of(), List.of());

    @Test
    public void advertisesTheBaselineLifecycle()
    {
        DiagnosticBaselineTool tool = new DiagnosticBaselineTool();
        assertEquals("diagnostic_baseline", tool.getName());
        assertEquals(ResponseType.JSON, tool.getResponseType());
        assertTrue(tool.getDescription().contains("get_tool_guide('diagnostic_baseline')"));
        JsonObject input = json(tool.getInputSchema());
        assertEquals(2, input.getAsJsonArray("required").size());
        assertTrue(input.getAsJsonObject("properties").has("objectFqns"));
        assertTrue(json(tool.getOutputSchema()).getAsJsonObject("properties").has("cleared"));
    }

    @Test
    public void validatesArgumentsBeforeAccessingTheWorkspace()
    {
        DiagnosticBaselineTool tool = new DiagnosticBaselineTool();
        assertTrue(ToolResult.isErrorPayload(tool.execute(Map.of("operation", "save"))));
        assertTrue(ToolResult.isErrorPayload(tool.execute(Map.of("operation", "delete", "projectName", "Test"))));
        assertTrue(ToolResult.isErrorPayload(tool.execute(Map.of("operation", "save", "projectName", "Test",
            "objectFqns", "[\" \"]"))));
        assertTrue(ToolResult.isErrorPayload(tool.execute(Map.of("operation", "clear", "projectName", "Test",
            "objectFqns", "[\"CommonModule.Example\"]"))));
    }

    @Test
    public void baselineModeHasStructuredResultsAndRequiresOneProject()
    {
        GetProjectErrorsTool tool = new GetProjectErrorsTool();
        assertEquals(ResponseType.MARKDOWN, tool.getResponseType(Map.of("sinceBaseline", "false")));
        assertEquals(ResponseType.JSON, tool.getResponseType(Map.of("sinceBaseline", "true")));
        String error = tool.execute(Map.of("sinceBaseline", "true"));
        assertTrue(ToolResult.isErrorPayload(error));
        assertTrue(error.contains("projectName"));
    }

    @Test
    public void completeComparisonPagesOnlyTheNewRows()
    {
        JsonObject payload = json(DiagnosticBaselineTool.comparisonPayload(BASELINE,
            List.of(problem("First"), problem("Second")), 1, null, new AddressResolution(), 1, 1, false, ""));
        assertTrue(payload.get("comparisonComplete").getAsBoolean());
        assertEquals(1, payload.get("resolvedSinceBaseline").getAsInt());
        assertEquals(2, payload.get("problemsTotal").getAsInt());
        assertEquals(2, payload.get("nextOffset").getAsInt());
        assertFalse(payload.get("hasMore").getAsBoolean());
        assertEquals("Second", payload.getAsJsonArray("problems").get(0).getAsJsonObject().get("message").getAsString());
    }

    @Test
    public void incompleteComparisonDoesNotReportAnExactResolvedCount()
    {
        JsonObject payload = json(DiagnosticBaselineTool.comparisonPayload(BASELINE, List.of(problem("Observed")),
            17, "Scan limit reached.", new AddressResolution(), 10, 0, false, ""));
        assertFalse(payload.get("comparisonComplete").getAsBoolean());
        assertFalse(payload.has("resolvedSinceBaseline"));
        assertEquals(1, payload.get("problemsTotal").getAsInt());
        assertTrue(payload.get("report").getAsString().contains("Scan limit reached."));
    }

    @Test
    public void aFailedCheckLookupCannotProduceAnApparentlyComparableMarker()
    {
        Marker marker = mock(Marker.class);
        IProject project = mock(IProject.class);
        IDtProject dtProject = mock(IDtProject.class);
        ICheckRepository repository = mock(ICheckRepository.class);
        when(marker.getProject()).thenReturn(project);
        when(marker.getCheckId()).thenReturn("SU23");
        when(repository.getUidForShortUid("SU23", project)).thenThrow(new IllegalStateException("unavailable"));
        when(repository.getUidForShortUid("SU23", dtProject)).thenThrow(new IllegalStateException("unavailable"));
        int[] unresolved = {0};
        MarkerRows.Match match = MarkerRows.matchOf(marker, null, null, Set.of(), Set.of(), repository,
            unresolved, false);
        assertNull(MarkerRows.buildComparableRow(marker, match, dtProject, repository, null, unresolved, ErrorInfo::new));
        assertNotNull(MarkerRows.buildRow(marker, match, repository, null, unresolved, true, ErrorInfo::new));
    }

    @Test
    public void aMissingTargetWithAnEmptyPresentationMakesTheScanIncomplete()
    {
        Marker marker = mock(Marker.class);
        when(marker.getObjectPresentation()).thenReturn("");
        int[] unreadable = {0};
        assertNull(MarkerRows.comparableMatchOf(marker, null, Set.of("commonmodule.example"),
            Set.of(), Set.of(), unreadable, true));
        assertEquals(1, unreadable[0]);
    }

    @Test
    public void comparisonSkipsAnObjectInTheSavedScopeThatTheRequestExcludes()
    {
        Set<String> saved = Set.of("commonmodule.a", "commonmodule.b");
        Set<String> requested = Set.of("commonmodule.a");
        int[] unreadable = {0};
        Marker marker = mock(Marker.class);
        when(marker.getObjectPresentation()).thenReturn("CommonModule.B.Module", "CommonModule.A.Module");

        assertNull(MarkerRows.comparableMatchOf(marker, null, saved, requested, Set.of(), unreadable, true));
        assertNotNull(MarkerRows.comparableMatchOf(marker, null, saved, requested, Set.of(), unreadable, true));
        assertEquals(0, unreadable[0]);
    }

    @Test
    public void severityNoneSkipsOtherLevelsBeforeReadingTheirLocations()
    {
        Marker marker = mock(Marker.class);
        when(marker.getSeverity()).thenReturn(MarkerSeverity.ERRORS);
        int[] unreadable = {0};

        assertNull(MarkerRows.comparableMatchOf(marker, MarkerSeverity.NONE, Set.of(), Set.of(),
            Set.of(), unreadable, false));
        verify(marker, never()).getObjectPresentation();
        assertEquals(0, unreadable[0]);
    }

    private static ErrorInfo problem(String message)
    {
        ErrorInfo row = new ErrorInfo();
        row.message = message;
        row.objectPresentation = "CommonModule.Example.Module";
        return row;
    }

    private static JsonObject json(String value)
    {
        return JsonParser.parseString(value).getAsJsonObject();
    }
}
