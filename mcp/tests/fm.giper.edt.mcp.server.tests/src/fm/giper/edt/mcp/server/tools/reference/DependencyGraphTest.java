/**
 * MCP Server for EDT
 * Adapted from AI-EDT tests, Copyright (C) 2026 Desko77 (https://github.com/Desko77)
 * Licensed under AGPL-3.0-or-later
 */
package fm.giper.edt.mcp.server.tools.reference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.stream.IntStream;

import org.junit.Test;

import fm.giper.edt.mcp.server.tools.reference.DependencyGraph.Connection;
import fm.giper.edt.mcp.server.tools.reference.DependencyGraph.Edge;
import fm.giper.edt.mcp.server.tools.reference.DependencyGraph.Link;
import fm.giper.edt.mcp.server.tools.reference.DependencyGraph.Node;
import fm.giper.edt.mcp.server.tools.reference.DependencyGraph.Options;
import fm.giper.edt.mcp.server.tools.reference.DependencyGraph.Result;

public class DependencyGraphTest
{
    private static final Connection AB = new Connection("A", "B", "types");
    private static final Connection BC = new Connection("B", "C", "types");
    private static final Connection CA = new Connection("C", "A", "types");
    private static final Link AB1 = new Link(AB, 1, 2);
    private static final Link AB2 = new Link(AB, 3, 2);
    private static final Link BC1 = new Link(BC, 4, 5);
    private static final Link CA1 = new Link(CA, 6, 7);

    @Test
    public void respectsDirectionDepthAndCountsTheSameOccurrenceOnce()
    {
        assertEquals(List.of(new Node("A", 0), new Node("B", 1)),
            walk(new Options("out", 1, 10, 10), () -> false).nodes());
        assertEquals(List.of(new Node("A", 0), new Node("C", 1)),
            walk(new Options("in", 1, 10, 10), () -> false).nodes());
        assertTrue(walk(new Options("out", 2, 10, 10), () -> false).cycles().isEmpty());
        Result both = walk(new Options("both", 2, 10, 10), () -> false);
        assertEquals(List.of(List.of("A", "B", "C")), both.cycles());
        assertEquals(new Edge("A", "B", "types", 2), both.edges().get(0));
        assertEquals(both, walk(new Options("both", 2, 10, 10), () -> false));
    }

    @Test
    public void reachingAnExactCapCompletesAndExceedingItKeepsEdgesClosed()
    {
        assertTrue(walk(new Options("out", 3, 3, 3), () -> false).partialReasons().isEmpty());
        Result cappedNodes = walk(new Options("out", 3, 2, 3), () -> false);
        assertEquals(List.of("nodeLimit"), cappedNodes.partialReasons());
        assertEquals(List.of(new Edge("A", "B", "types", 2)), cappedNodes.edges());
        Result cappedEdges = walk(new Options("out", 3, 3, 1), () -> false);
        assertEquals(List.of("edgeLimit"), cappedEdges.partialReasons());
        assertEquals(List.of(new Node("A", 0), new Node("B", 1)), cappedEdges.nodes());
    }

    @Test
    public void recordsSelfLoopsAndLongCyclesWithoutRecursion()
    {
        var nodes = IntStream.range(0, 12_000).mapToObj(Integer::toString).toList();
        var links = new ArrayList<Connection>();
        for (int i = 0; i < nodes.size(); i++)
        {
            links.add(new Connection(nodes.get(i), nodes.get((i + 1) % nodes.size()), "types"));
        }
        assertEquals(12_000, DependencyGraph.cycles(nodes, links).get(0).size());
        assertEquals(List.of(List.of("A")), DependencyGraph.cycles(List.of("A", "B"),
            List.of(new Connection("A", "A", "owner"))));
    }

    @Test
    public void stopsBetweenExpansionsAndKeepsTheAlreadyVisitedPrefix()
    {
        AtomicBoolean cancelled = new AtomicBoolean();
        var reasons = new LinkedHashSet<String>();
        Result result = DependencyGraph.walk("A", new Options("out", 3, 3, 3), current -> {
            if (current.equals("B"))
            {
                cancelled.set(true);
            }
            return current.equals("A") ? List.of(AB1) : List.of(BC1);
        }, () -> cancelled.get() && reasons.add("cancelled"), reasons);
        assertEquals(List.of("cancelled"), result.partialReasons());
        assertEquals(List.of(new Edge("A", "B", "types", 1)), result.edges());
    }

    @Test
    public void monotonicBudgetAndCallerCancellationAreDistinguishable()
    {
        var clock = new AtomicLong();
        var bound = ReferenceSearchBound.of(3, 5L, clock::get);
        assertFalse(bound.shouldStop(0));
        clock.set(5_000_000L);
        assertTrue(bound.shouldStop(0));
        assertEquals(ReferenceSearchBound.StopReason.TIME_BUDGET, bound.stopReason());

        var cancelled = ReferenceSearchBound.of(3, 5L, clock::get);
        cancelled.cancelWhen(() -> true);
        assertTrue(cancelled.monitor(() -> 0).isCanceled());
        assertEquals(ReferenceSearchBound.StopReason.CANCELLED, cancelled.stopReason());
    }

    private static Result walk(Options options, BooleanSupplier stop)
    {
        List<Link> links = List.of(CA1, AB2, BC1, AB1);
        return DependencyGraph.walk("A", options, current -> links.stream().filter(link -> {
            Connection edge = link.connection();
            return !options.direction().equals("in") && edge.from().equals(current)
                || !options.direction().equals("out") && edge.to().equals(current);
        }).toList(), stop, new LinkedHashSet<>());
    }
}
