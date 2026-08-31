/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server.bridge;

import java.util.Comparator;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import fm.giper.edt.mcp.server.Activator;
import fm.giper.edt.mcp.server.history.McpCallHistory;
import fm.giper.edt.mcp.server.protocol.GsonProvider;
import fm.giper.edt.mcp.server.protocol.JsonUtils;
import fm.giper.edt.mcp.server.protocol.McpConstants;
import fm.giper.edt.mcp.server.protocol.McpProtocolHandler;
import fm.giper.edt.mcp.server.protocol.McpRequestContext;
import fm.giper.edt.mcp.server.profiles.DefaultToolProfileFactory;
import fm.giper.edt.mcp.server.profiles.ProfileResolver;
import fm.giper.edt.mcp.server.profiles.ToolProfileRepository;
import fm.giper.edt.mcp.server.profiles.ProfileToolPolicy;
import fm.giper.edt.mcp.server.profiles.ToolProfile;
import fm.giper.edt.mcp.server.profiles.ToolProfileSnapshot;
import fm.giper.edt.mcp.server.tools.IMcpTool;
import fm.giper.edt.mcp.server.tools.McpToolRegistry;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

/**
 * Public OSGi implementation of {@link IEdtMcpBridge}.
 * <p>
 * Tool calls are deliberately sent through
 * {@link McpProtocolHandler#processRequest(String)} rather than invoking
 * {@link IMcpTool#execute(java.util.Map)} directly. This keeps the in-process
 * bridge on the same dispatch, enablement, result-shaping, redaction and logging
 * path as HTTP {@code tools/call}, without copying transport, SSE or interruption
 * responsibilities into this service.
 * <p>
 * The class also implements {@link BiFunction} and {@link Supplier} so the same
 * instance can be published under those JDK types, giving callers that cannot
 * see {@link IEdtMcpBridge} a typed handle instead of a reflective one.
 */
public class EdtMcpBridge implements IEdtMcpBridge, BiFunction<String, String, String>, Supplier<String>
{
    private static final long BRIDGE_REQUEST_ID = 1L;

    private final McpToolRegistry registry;
    private final McpProtocolHandler protocolHandler;
    private final Supplier<ToolProfileSnapshot> snapshotSupplier;

    /** Creates a bridge backed by the live singleton tool registry. */
    public EdtMcpBridge()
    {
        this(McpToolRegistry.getInstance(), new McpProtocolHandler(), EdtMcpBridge::liveOrAllowAll);
    }

    /** Package-private seam for focused headless tests (allow-all default profile). */
    EdtMcpBridge(McpToolRegistry registry, McpProtocolHandler protocolHandler)
    {
        this(registry, protocolHandler, () -> allowAllRegistered(registry));
    }

    /** Package-private seam with an explicit profile document. */
    EdtMcpBridge(McpToolRegistry registry, McpProtocolHandler protocolHandler,
        Supplier<ToolProfileSnapshot> snapshotSupplier)
    {
        this.registry = registry;
        this.protocolHandler = protocolHandler;
        this.snapshotSupplier = snapshotSupplier == null
            ? DefaultToolProfileFactory::createSafeSnapshot
            : snapshotSupplier;
    }

    @Override
    public String listTools()
    {
        return listTools(ToolProfile.DEFAULT_ID);
    }

    @Override
    public String listTools(String profileId)
    {
        BridgeActivity.callStarted();
        try
        {
            return renderToolList(contextFor(profileId));
        }
        finally
        {
            BridgeActivity.callFinished();
        }
    }

    private String renderToolList(McpRequestContext context)
    {
        ProfileToolPolicy policy = policyFor(context);
        JsonArray result = new JsonArray();
        policy.publishedTools(false).stream()
            .sorted(Comparator.comparing(IMcpTool::getName))
            .forEach(tool -> {
                JsonObject item = new JsonObject();
                item.addProperty("name", tool.getName()); //$NON-NLS-1$
                item.addProperty("description", tool.getDescription()); //$NON-NLS-1$
                result.add(item);
            });
        return GsonProvider.toJson(result);
    }

    @Override
    public String callTool(String toolName, String argsJson)
    {
        return callTool(toolName, argsJson, ToolProfile.DEFAULT_ID);
    }

    @Override
    public String callTool(String toolName, String argsJson, String profileId)
    {
        // Bracketed, not just counted: a caller waiting on an assistant turn needs to see that
        // work is under way, and one tool that runs for minutes ticks the counter only once.
        BridgeActivity.callStarted();
        try
        {
            return dispatchToolCall(toolName, argsJson, contextFor(profileId));
        }
        finally
        {
            BridgeActivity.callFinished();
        }
    }

    private String dispatchToolCall(String toolName, String argsJson, McpRequestContext context)
    {
        JsonElement arguments;
        try
        {
            arguments = argsJson == null || argsJson.trim().isEmpty()
                ? new JsonObject() : JsonParser.parseString(argsJson);
        }
        catch (JsonSyntaxException e)
        {
            return JsonUtils.buildJsonRpcError(McpConstants.ERROR_INVALID_PARAMS,
                "Invalid argsJson: expected a JSON object with tool arguments. " //$NON-NLS-1$
                    + "Fix the JSON syntax and retry callTool.", BRIDGE_REQUEST_ID); //$NON-NLS-1$
        }

        if (!arguments.isJsonObject())
        {
            return JsonUtils.buildJsonRpcError(McpConstants.ERROR_INVALID_PARAMS,
                "Invalid argsJson: expected a JSON object with tool arguments, but got " //$NON-NLS-1$
                    + jsonKind(arguments) + ". Pass an object such as {} and retry callTool.", //$NON-NLS-1$ //$NON-NLS-2$
                BRIDGE_REQUEST_ID);
        }

        JsonObject params = new JsonObject();
        params.addProperty("name", toolName); //$NON-NLS-1$
        params.add("arguments", arguments); //$NON-NLS-1$

        JsonObject request = new JsonObject();
        request.addProperty("jsonrpc", McpConstants.JSONRPC_VERSION); //$NON-NLS-1$
        request.addProperty("id", BRIDGE_REQUEST_ID); //$NON-NLS-1$
        request.addProperty("method", McpConstants.METHOD_TOOLS_CALL); //$NON-NLS-1$
        request.add("params", params); //$NON-NLS-1$

        ProfileToolPolicy policy = policyFor(context);
        if (!policy.isCallable(toolName))
        {
            return JsonUtils.buildJsonRpcError(McpConstants.ERROR_METHOD_NOT_FOUND,
                policy.deniedMessage(toolName), BRIDGE_REQUEST_ID);
        }

        McpCallHistory.bindRequestMeta(context);
        try
        {
            return protocolHandler.processRequest(GsonProvider.toJson(request), context);
        }
        finally
        {
            McpCallHistory.clearRequestMeta();
        }
    }

    private McpRequestContext contextFor(String profileId)
    {
        ToolProfileSnapshot snapshot = snapshotSupplier.get();
        if (profileId == null || profileId.isBlank() || ToolProfile.DEFAULT_ID.equals(profileId))
        {
            return McpRequestContext.builder()
                .resolution(ProfileResolver.resolveDefault(snapshot))
                .requestedPath("/mcp") //$NON-NLS-1$
                .legacyCompatibilityWrapper(true)
                .build();
        }
        return McpRequestContext.builder()
            .resolution(ProfileResolver.resolve(profileId, false, snapshot))
            .requestedPath("/mcp/profiles/" + profileId) //$NON-NLS-1$
            .legacyCompatibilityWrapper(false)
            .build();
    }

    private ProfileToolPolicy policyFor(McpRequestContext context)
    {
        return new ProfileToolPolicy(context.getResolution(), registry.getAllTools());
    }

    private static ToolProfileSnapshot liveOrAllowAll()
    {
        Activator activator = Activator.getDefault();
        if (activator != null)
        {
            ToolProfileRepository repository = activator.getToolProfileRepository();
            if (repository != null)
            {
                return repository.getSnapshot();
            }
        }
        return allowAllRegistered(McpToolRegistry.getInstance());
    }

    private static ToolProfileSnapshot allowAllRegistered(McpToolRegistry registry)
    {
        return ToolProfileSnapshot.of(1L, List.of(
            DefaultToolProfileFactory.createDefault(registry.getAllTools().stream()
                .map(IMcpTool::getName)
                .collect(Collectors.toSet()))));
    }

    /**
     * {@link Supplier} face of {@link #listTools()} for callers that hold this
     * service under its JDK-type alias.
     */
    @Override
    public String get()
    {
        return listTools();
    }

    /**
     * {@link BiFunction} face of {@link #callTool(String, String)} for callers
     * that hold this service under its JDK-type alias.
     */
    @Override
    public String apply(String toolName, String argsJson)
    {
        return callTool(toolName, argsJson);
    }

    private static String jsonKind(JsonElement element)
    {
        if (element == null || element.isJsonNull())
        {
            return "null"; //$NON-NLS-1$
        }
        if (element.isJsonArray())
        {
            return "an array"; //$NON-NLS-1$
        }
        return "a primitive value"; //$NON-NLS-1$
    }
}
