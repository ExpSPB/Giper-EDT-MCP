/**
 * MCP Server for EDT
 * Adapted from AI-EDT, Copyright (C) 2026 Desko77 (https://github.com/Desko77)
 * Licensed under AGPL-3.0-or-later
 */
package fm.giper.edt.mcp.server.tools.reference;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/** Bounded breadth-first walk over directed, model-resolved reference occurrences. */
public final class DependencyGraph
{
    public record Options(String direction, int depth, int maxNodes, int maxEdges)
    {
    }

    public record Node(String fqn, int depth)
    {
    }

    public record Edge(String from, String to, String via, int count)
    {
    }

    record Connection(String from, String to, String via)
    {
    }

    /** BM identifies a reference by its source object, feature and target object. */
    record Link(Connection connection, long sourceId, long targetId)
    {
    }

    public record Result(String root, List<Node> nodes, List<Edge> edges, List<List<String>> cycles,
        List<String> partialReasons)
    {
    }

    private static final Comparator<Connection> EDGE_ORDER = Comparator.comparing(Connection::from)
        .thenComparing(Connection::to).thenComparing(Connection::via);

    private DependencyGraph()
    {
    }

    static Result walk(String root, Options options, Function<String, Collection<Link>> neighbors,
        BooleanSupplier stop, Set<String> reasons)
    {
        Map<String, Integer> depths = new LinkedHashMap<>();
        Map<Connection, Set<Link>> edges = new LinkedHashMap<>();
        var queue = new ArrayDeque<String>();
        queue.add(root);
        depths.put(root, 0);

        walk: while (!queue.isEmpty() && !stop.getAsBoolean())
        {
            String current = queue.remove();
            int step = depths.get(current) + 1;
            if (step > options.depth())
            {
                break;
            }
            var links = new ArrayList<>(neighbors.apply(current));
            links.sort(Comparator.comparing(Link::connection, EDGE_ORDER));
            for (Link link : links)
            {
                if (stop.getAsBoolean())
                {
                    break walk;
                }
                Connection edge = link.connection();
                String next = edge.from().equals(current) ? edge.to() : edge.from();
                boolean newNode = !depths.containsKey(next);
                boolean newEdge = !edges.containsKey(edge);
                if (newNode && depths.size() == options.maxNodes())
                {
                    reasons.add("nodeLimit"); //$NON-NLS-1$
                }
                if (newEdge && edges.size() == options.maxEdges())
                {
                    reasons.add("edgeLimit"); //$NON-NLS-1$
                }
                if (reasons.contains("nodeLimit") || reasons.contains("edgeLimit")) //$NON-NLS-1$ //$NON-NLS-2$
                {
                    break walk;
                }
                if (newNode)
                {
                    depths.put(next, step);
                    queue.add(next);
                }
                edges.computeIfAbsent(edge, key -> new HashSet<>()).add(link);
            }
        }

        List<Node> nodes = depths.entrySet().stream().map(e -> new Node(e.getKey(), e.getValue())).toList();
        List<Edge> rows = edges.entrySet().stream().sorted(Map.Entry.comparingByKey(EDGE_ORDER))
            .map(e -> new Edge(e.getKey().from(), e.getKey().to(), e.getKey().via(), e.getValue().size())).toList();
        return new Result(root, nodes, rows, cycles(depths.keySet(), edges.keySet()), List.copyOf(reasons));
    }

    /** Iterative two-pass SCC search. A deep component does not consume the Java call stack. */
    static List<List<String>> cycles(Collection<String> nodes, Collection<Connection> edges)
    {
        Map<String, Set<String>> forward = new HashMap<>();
        Map<String, Set<String>> reverse = new HashMap<>();
        for (Connection edge : edges)
        {
            forward.computeIfAbsent(edge.from(), key -> new LinkedHashSet<>()).add(edge.to());
            reverse.computeIfAbsent(edge.to(), key -> new LinkedHashSet<>()).add(edge.from());
        }

        List<String> finished = finishOrder(nodes, forward);
        Set<String> seen = new HashSet<>();
        List<List<String>> components = new ArrayList<>();
        for (int i = finished.size() - 1; i >= 0; i--)
        {
            String start = finished.get(i);
            if (!seen.add(start))
            {
                continue;
            }
            var pending = new ArrayDeque<String>();
            List<String> component = new ArrayList<>();
            pending.push(start);
            while (!pending.isEmpty())
            {
                String node = pending.pop();
                component.add(node);
                for (String next : reverse.getOrDefault(node, Set.of()))
                {
                    if (seen.add(next))
                    {
                        pending.push(next);
                    }
                }
            }
            if (component.size() > 1 || forward.getOrDefault(start, Set.of()).contains(start))
            {
                component.sort(String::compareTo);
                components.add(List.copyOf(component));
            }
        }
        components.sort(Comparator.comparing(component -> component.get(0)));
        return List.copyOf(components);
    }

    private record Frame(String node, Iterator<String> neighbors)
    {
    }

    private static List<String> finishOrder(Collection<String> roots, Map<String, Set<String>> forward)
    {
        List<String> finished = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        var stack = new ArrayDeque<Frame>();
        for (String root : roots)
        {
            if (!seen.add(root))
            {
                continue;
            }
            stack.push(new Frame(root, forward.getOrDefault(root, Set.of()).iterator()));
            while (!stack.isEmpty())
            {
                Frame frame = stack.peek();
                if (!frame.neighbors().hasNext())
                {
                    finished.add(stack.pop().node());
                    continue;
                }
                String next = frame.neighbors().next();
                if (seen.add(next))
                {
                    stack.push(new Frame(next, forward.getOrDefault(next, Set.of()).iterator()));
                }
            }
        }
        return finished;
    }
}
