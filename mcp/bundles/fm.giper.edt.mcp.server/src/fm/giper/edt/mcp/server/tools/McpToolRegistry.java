/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server.tools;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import fm.giper.edt.mcp.server.Activator;

/**
 * Registry for MCP tools.
 * Manages registration and lookup of tools by name.
 */
public class McpToolRegistry // NOSONAR intentional singleton (Eclipse service / getInstance); a single instance is by design
{
    private static final McpToolRegistry INSTANCE = new McpToolRegistry();

    /**
     * The live catalogue, REPLACED as a whole rather than emptied and refilled.
     * <p>
     * Readers - {@code tools/list}, {@code tools/call} and the in-process bridge - run
     * concurrently with a server restart, which re-registers every tool. Refilling in place
     * would let them observe an empty or half-populated catalogue and report an existing tool
     * as unknown, so a bulk update publishes a finished map instead: every reader sees either
     * the whole previous catalogue or the whole new one.
     */
    private volatile Map<String, IMcpTool> tools = new ConcurrentHashMap<>();
    private final AtomicLong catalogRevision = new AtomicLong(0);

    private McpToolRegistry()
    {
        // Private constructor for singleton
    }
    
    /**
     * Returns the singleton instance.
     * 
     * @return registry instance
     */
    public static McpToolRegistry getInstance()
    {
        return INSTANCE;
    }
    
    /**
     * Registers a tool.
     * 
     * @param tool the tool to register
     */
    public void register(IMcpTool tool)
    {
        if (tool == null || tool.getName() == null)
        {
            return;
        }
        tools.put(tool.getName(), tool);
        catalogRevision.incrementAndGet();
        Activator.logInfo("Registered MCP tool: " + tool.getName()); //$NON-NLS-1$
    }

    /**
     * Replaces the whole catalogue in one step.
     * <p>
     * This is what a (re)start must use instead of {@link #clear()} followed by
     * {@link #register(IMcpTool)} per tool: a concurrent {@code tools/list} would otherwise
     * see the gap between the two and answer with a partial catalogue, and a concurrent
     * {@code tools/call} would report a tool that exists as unknown.
     *
     * @param replacement the tools to publish; {@code null} entries and unnamed tools are
     *            ignored, a {@code null} collection empties the registry
     */
    public void replaceAll(Collection<IMcpTool> replacement)
    {
        Map<String, IMcpTool> published = new ConcurrentHashMap<>();
        if (replacement != null)
        {
            for (IMcpTool tool : replacement)
            {
                if (tool == null || tool.getName() == null)
                {
                    continue;
                }
                published.put(tool.getName(), tool);
                Activator.logInfo("Registered MCP tool: " + tool.getName()); //$NON-NLS-1$
            }
        }
        // The single point where readers switch over, and they switch over completely.
        tools = published;
        catalogRevision.incrementAndGet();
    }

    /**
     * Returns a tool by name.
     * 
     * @param name the tool name
     * @return tool or null if not found
     */
    public IMcpTool getTool(String name)
    {
        return tools.get(name);
    }
    
    /**
     * Unregisters a tool by name.
     *
     * @param name the tool name to remove
     */
    public void unregister(String name)
    {
        if (name != null)
        {
            tools.remove(name);
        }
    }

    /**
     * Returns all registered tools (regardless of enablement state).
     *
     * @return collection of all tools
     */
    public Collection<IMcpTool> getAllTools()
    {
        return Collections.unmodifiableCollection(tools.values());
    }

    public long getCatalogRevision()
    {
        return catalogRevision.get();
    }

    public long getCatalogRevision()
    {
        return catalogRevision.get();
    }
    
    /**
     * Returns the number of registered tools.
     * 
     * @return tool count
     */
    public int getToolCount()
    {
        return tools.size();
    }
    
    /**
     * Clears all registered tools.
     * <p>
     * To REPLACE the catalogue use {@link #replaceAll(Collection)}: clearing and re-registering
     * exposes the empty state in between.
     */
    public void clear()
    {
        tools = new ConcurrentHashMap<>();
        catalogRevision.incrementAndGet();
    }
}
