/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server.bridge;

/**
 * Stable in-process entry point for other Eclipse bundles that need to discover
 * and call EDT-MCP tools without importing EDT-MCP implementation packages.
 * <p>
 * Consumers look this service up by this interface's string name (or the
 * {@link #SERVICE_PROPERTY} filter) and invoke its methods reflectively.
 * Implementations and the methods below form a public OSGi-facing contract.
 * <p>
 * {@code listTools()} / {@code callTool(name, args)} use the {@code default}
 * profile. Pass {@code profileId} to stay inside a narrower allowlist. Policy is
 * re-applied on every call; a name from an earlier list is not enough.
 * <p>
 * This package is deliberately NOT in {@code Export-Package}, and adding it there
 * BREAKS THE BUILD. The test bundle is a FRAGMENT of this host; while the host
 * exports nothing, the fragment compiles against every host package, but as soon
 * as one package is exported Tycho derives the fragment's access rules from that
 * export list and every other host package becomes unresolvable. Nothing needs the
 * export: an OSGi service is found by the string class name whether or not the
 * requesting bundle is wired to the package.
 */
public interface IEdtMcpBridge
{
    /** Service property that marks every published alias of this bridge. */
    String SERVICE_PROPERTY = "edt.mcp.bridge"; //$NON-NLS-1$

    /** Value of {@link #SERVICE_PROPERTY} for this contract revision. */
    String SERVICE_PROPERTY_VALUE = "v1"; //$NON-NLS-1$

    /**
     * Lists tools published for the {@code default} profile.
     *
     * @return JSON array {@code [{"name":...,"description":...}]}
     */
    String listTools();

    /**
     * Lists tools published for {@code profileId}. Policy is applied on this
     * call; a previously listed name is not trusted later.
     *
     * @param profileId requested profile id; blank means {@code default}
     * @return JSON array {@code [{"name":...,"description":...}]}
     */
    String listTools(String profileId);

    /**
     * Calls a registered tool through the normal MCP {@code tools/call}
     * dispatcher under the {@code default} profile.
     *
     * @param toolName exact registered tool name
     * @param argsJson JSON object containing the tool arguments; blank means an
     *            empty object
     * @return the same JSON-RPC response shape produced for MCP
     *         {@code tools/call}
     */
    String callTool(String toolName, String argsJson);

    /**
     * Calls a tool under {@code profileId}. Policy is re-applied; a name from an
     * earlier {@link #listTools()} of another profile is not enough.
     *
     * @param toolName exact registered tool name
     * @param argsJson JSON object containing the tool arguments
     * @param profileId requested profile id; blank means {@code default}
     * @return the same JSON-RPC response shape produced for MCP {@code tools/call}
     */
    String callTool(String toolName, String argsJson, String profileId);
}
