/**
 * MCP Server for EDT
 * Adapted from AI-EDT tests, Copyright (C) 2026 Desko77 (https://github.com/Desko77)
 * Licensed under AGPL-3.0-or-later
 */
package fm.giper.edt.mcp.server.tools.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import fm.giper.edt.mcp.server.protocol.ToolResult;
import fm.giper.edt.mcp.server.tools.IMcpTool.ResponseType;
import fm.giper.edt.mcp.server.tools.reference.MetadataReferenceService.ImpactReference;
import fm.giper.edt.mcp.server.tools.reference.MetadataReferenceService.ImpactResult;

public class ImpactAnalysisToolTest
{
    @Test
    public void gradesOnlyAnExactCountAtTheDonorBoundaries()
    {
        Map.of(0, "LOW", 1, "MEDIUM", 10, "MEDIUM", 11, "HIGH").forEach((count, tier) -> {
            String answer = ImpactAnalysisTool.render("Fixture", "modify", false, result(count, List.of()));
            assertTrue(answer.contains("**Severity:** " + tier));
            assertTrue(answer.contains("**Total references:** " + count));
        });
        for (String reason : List.of("the reference limit was reached", "the time budget expired",
            "the search was cancelled", "the BSL index or project search scope was incomplete"))
        {
            String answer = ImpactAnalysisTool.render("Fixture", "delete", false, result(0, List.of(reason)));
            assertTrue(answer.contains("**References found:** at least 0"));
            assertTrue(answer.contains(reason));
            assertFalse(answer.contains("**Total references:**"));
            assertFalse(answer.contains("**Severity:**"));
            assertFalse(answer.contains("**Recommendation:**"));
        }
    }

    @Test
    public void forwardsTheScopeAndReportsTheChangeWithoutParsingMarkdown()
    {
        var tool = new ImpactAnalysisTool((project, fqn, skipBsl, limit) -> {
            assertEquals("Fixture", project);
            assertEquals("Catalog.Root", fqn);
            assertTrue(skipBsl);
            assertEquals(3, limit);
            return result(1, List.of());
        });
        String answer = tool.execute(Map.of("projectName", "Fixture", "objectFqn", "Catalog.Root",
            "skipBsl", "true", "action", "rename", "limit", "3"));
        assertEquals(ResponseType.MARKDOWN, tool.getResponseType());
        assertTrue(answer.contains("**Scope:** metadata\n"));
        assertTrue(answer.contains("rename_metadata_object"));
        assertTrue(answer.contains("Catalog.Source"));
        assertTrue(ToolResult.isErrorPayload(tool.execute(Map.of("projectName", "Fixture",
            "objectFqn", "Catalog.Root", "skipBsl", "maybe"))));
    }

    private static ImpactResult result(int size, List<String> reasons)
    {
        return new ImpactResult("Catalog.Root", Collections.nCopies(size,
            new ImpactReference("Catalogs", "Catalog.Source", "types", -1)), reasons);
    }
}
