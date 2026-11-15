/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server.tools.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import org.eclipse.jface.preference.IPreferenceStore;

import fm.giper.edt.mcp.server.Activator;
import fm.giper.edt.mcp.server.HostKind;
import fm.giper.edt.mcp.server.McpServer;
import fm.giper.edt.mcp.server.preferences.PreferenceConstants;
import fm.giper.edt.mcp.server.profiles.DefaultToolProfileFactory;
import fm.giper.edt.mcp.server.profiles.ProfileResolution;
import fm.giper.edt.mcp.server.profiles.ProfileResolver;
import fm.giper.edt.mcp.server.profiles.ProfileToolPolicy;
import fm.giper.edt.mcp.server.profiles.ToolProfile;
import fm.giper.edt.mcp.server.profiles.ToolProfileSnapshot;
import fm.giper.edt.mcp.server.protocol.JsonSchemaBuilder;
import fm.giper.edt.mcp.server.protocol.JsonUtils;
import fm.giper.edt.mcp.server.protocol.McpConstants;
import fm.giper.edt.mcp.server.protocol.ToolResult;
import fm.giper.edt.mcp.server.tools.IMcpTool;
import fm.giper.edt.mcp.server.tools.McpToolRegistry;
import fm.giper.edt.mcp.server.transport.HttpTransport;
import fm.giper.edt.mcp.server.transport.StdioPluginSupport;
import fm.giper.edt.mcp.server.transport.TransportStatus;
import fm.giper.edt.mcp.server.utils.CheckDescriptionLoader;
import fm.giper.edt.mcp.server.utils.NativeRenderModeProbe;
import fm.giper.edt.mcp.server.utils.NativeRenderModeProbe.NativeRenderMode;

/**
 * Self-diagnosis tool: returns the running MCP server's introspection snapshot
 * so a client can answer "why is the screenshot blank / why is JSON plain /
 * which tools are exposed" without guessing.
 * <p>
 * Reports: listening port, MCP protocol version, plugin and EDT version, the
 * enabled/total tool counts, the {@code plainTextMode} and {@code checksFolder}
 * preference flags, the startup modes and requested properties of the two form-render modes
 * ({@code -DnativeFormBufferedLayoutRender} / {@code -DnativeFormLayoutRender}),
 * whether authentication is enabled, and the request's effective tool profile.
 * <p>
 * SECURITY: the auth token value is never emitted — only the {@code authEnabled}
 * boolean derived from whether {@link PreferenceConstants#PREF_AUTH_TOKEN} is
 * non-empty. The {@code checksFolder} path is likewise reduced to a boolean
 * ({@code checksFolderConfigured}), never the path itself.
 * <p>
 * Read-only: the {@code get_} name prefix lets the central
 * {@code ToolAnnotationClassifier} mark this tool read-only and idempotent.
 * {@code execute()} is null-safe for a headless context (a missing
 * {@link Activator} or {@link McpServer} degrades to {@code unknown}/{@code false}
 * rather than throwing).
 */
public class GetServerStatusTool implements IMcpTool
{
    private final Supplier<McpServer> httpServer;
    private final BooleanSupplier ipcRunning;

    public GetServerStatusTool()
    {
        this(() -> Activator.getDefault() == null ? null : Activator.getDefault().getMcpServer(), StdioPluginSupport::isIpcRunning);
    }

    GetServerStatusTool(Supplier<McpServer> httpServer, BooleanSupplier ipcRunning)
    { this.httpServer = httpServer; this.ipcRunning = ipcRunning; }
    public static final String NAME = "get_server_status"; //$NON-NLS-1$

    /** Form-render JVM flag: enables the offscreen buffered layout render. */
    private static final String FLAG_BUFFERED_LAYOUT_RENDER = "nativeFormBufferedLayoutRender"; //$NON-NLS-1$

    /** Form-render JVM flag: selects the native (C++) layout render path. */
    private static final String FLAG_NATIVE_LAYOUT_RENDER = "nativeFormLayoutRender"; //$NON-NLS-1$

    /** Output key: whether the MCP server is currently running. */
    private static final String KEY_RUNNING = "running"; //$NON-NLS-1$
    private static final String PARAM_INCLUDE_PROFILES = "includeProfiles"; //$NON-NLS-1$
    private static final String PARAM_INCLUDE_PROFILE_TOOLS = "includeProfileTools"; //$NON-NLS-1$

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public String getDescription()
    {
        return "Diagnose the EDT MCP server, its feature configuration, and the active tool profile. Parameters and examples: " //$NON-NLS-1$
            + "get_tool_guide('get_server_status')."; //$NON-NLS-1$
    }

    @Override
    public ResponseType getResponseType()
    {
        return ResponseType.JSON;
    }

    @Override
    public String getInputSchema()
    {
        return JsonSchemaBuilder.object()
            .booleanProperty(PARAM_INCLUDE_PROFILES, "List enabled profiles with their HTTP endpoints. Default: false.") //$NON-NLS-1$
            .booleanProperty(PARAM_INCLUDE_PROFILE_TOOLS, "Include the effective tool names per listed profile. Requires includeProfiles. Default: false.") //$NON-NLS-1$
            .build();
    }

    @Override
    public String getOutputSchema()
    {
        return JsonSchemaBuilder.object()
            .booleanProperty("success", "Whether the operation succeeded", true) //$NON-NLS-1$ //$NON-NLS-2$
            .integerProperty("port", "HTTP configured or last bound TCP port; active only when transports.http is true") //$NON-NLS-1$ //$NON-NLS-2$
            .booleanProperty(KEY_RUNNING, "Whether at least one MCP transport is listening") //$NON-NLS-1$
            .objectProperty("transports", "Active MCP listeners; IPC exposes no secret or socket address",
                JsonSchemaBuilder.object()
                    .booleanProperty("http", "Whether the HTTP listener is running", true)
                    .booleanProperty("ipc", "Whether the local IPC listener is running", true), false)
            .stringProperty("hostKind", "EDT host kind: WINDOWED or HEADLESS")
            .stringProperty("protocolVersion", "MCP protocol version implemented by the server") //$NON-NLS-1$ //$NON-NLS-2$
            .stringProperty("pluginVersion", "Version of the EDT-MCP plugin") //$NON-NLS-1$ //$NON-NLS-2$
            .stringProperty("edtVersion", "Detected 1C:EDT version") //$NON-NLS-1$ //$NON-NLS-2$
            .integerProperty("enabledTools", "Number of tools currently enabled") //$NON-NLS-1$ //$NON-NLS-2$
            .integerProperty("totalTools", "Total number of registered tools") //$NON-NLS-1$ //$NON-NLS-2$
            .booleanProperty("plainTextMode", "Whether JSON responses are forced to plain text") //$NON-NLS-1$ //$NON-NLS-2$
            .booleanProperty("checksFolderConfigured", "Whether a checks folder path is configured") //$NON-NLS-1$ //$NON-NLS-2$
            .booleanProperty("authEnabled", "Whether bearer-token authentication is enabled") //$NON-NLS-1$ //$NON-NLS-2$
            .objectProperty("formRenderFlags", //$NON-NLS-1$
                "atStartup is the mode when this plugin activated; requested is the current " //$NON-NLS-1$
                    + "system property. EDT binds these modes at class initialization; changing them " //$NON-NLS-1$
                    + "requires an EDT restart") //$NON-NLS-1$
            .objectProperty("activeProfile", "Profile serving this request, with its revision, endpoint, tool count and fallback status") //$NON-NLS-1$ //$NON-NLS-2$
            .objectArrayProperty("availableProfiles", "Enabled profiles and their explicit HTTP endpoints when includeProfiles is true") //$NON-NLS-1$ //$NON-NLS-2$
            .build();
    }

    @Override
    public String execute(Map<String, String> params)
    {
        return execute(params, null);
    }

    /** Uses the same profile snapshot and authorization policy as the request dispatcher. */
    public String execute(Map<String, String> params, ProfileToolPolicy policy)
    {
        try
        {
            boolean includeProfiles = JsonUtils.extractBooleanArgument(params, PARAM_INCLUDE_PROFILES, false);
            boolean includeProfileTools = JsonUtils.extractBooleanArgument(params, PARAM_INCLUDE_PROFILE_TOOLS, false);
            if (includeProfileTools && !includeProfiles)
            {
                return ToolResult.error("includeProfileTools=true requires includeProfiles=true.").toJson(); //$NON-NLS-1$
            }
            Activator activator = Activator.getDefault();

            McpToolRegistry registry = McpToolRegistry.getInstance();
            int totalTools = registry.getToolCount();
            int enabledTools = policy == null ? registry.getEnabledTools().size() : policy.effectiveToolNames().size();

            ToolResult result = ToolResult.success();

            // Port: read from the live server when available; null-safe for headless.
            McpServer server = httpServer.get();
            TransportStatus transports = TransportStatus.read(server, ipcRunning);
            boolean http = transports.http();
            boolean ipc = transports.ipc();
            int port = server == null ? PreferenceConstants.DEFAULT_PORT : server.getPort();
            result.put("port", port); //$NON-NLS-1$
            result.put(KEY_RUNNING, transports.running());
            result.put("transports", Map.of("http", http, "ipc", ipc));

            result.put("hostKind", HostKind.current().name());
            result.put("protocolVersion", McpConstants.PROTOCOL_VERSION); //$NON-NLS-1$
            result.put("pluginVersion", McpConstants.PLUGIN_VERSION); //$NON-NLS-1$
            result.put("edtVersion", GetEdtVersionTool.getEdtVersion()); //$NON-NLS-1$

            result.put("enabledTools", enabledTools); //$NON-NLS-1$
            result.put("totalTools", totalTools); //$NON-NLS-1$

            // Preference-backed flags. Degrade to defaults/false when the
            // preference store is unavailable (headless / no Activator).
            boolean plainTextMode = PreferenceConstants.DEFAULT_PLAIN_TEXT_MODE;
            boolean checksFolderConfigured = false;
            boolean authEnabled = false;
            int advertisedPort = http ? port : PreferenceConstants.DEFAULT_PORT;
            if (activator != null)
            {
                IPreferenceStore store = activator.getPreferenceStore();
                if (store != null)
                {
                    plainTextMode = store.getBoolean(PreferenceConstants.PREF_PLAIN_TEXT_MODE);
                    int configuredPort = store.getInt(PreferenceConstants.PREF_PORT);
                    if (!http && configuredPort > 0)
                    {
                        advertisedPort = configuredPort;
                    }

                    // Only whether a checks folder is configured, never the path. Since #31 the
                    // descriptions SHIP with the plugin, so this reports an OVERRIDE being in
                    // play - not whether get_check_description works. Read through the loader so
                    // "configured" means here exactly what it means where it is acted on.
                    checksFolderConfigured = CheckDescriptionLoader.hasOverrideFolder();

                    // Only whether auth is on, never the token value - and "on" means what the
                    // authorizer means by it, so a preference of blanks reports auth OFF here
                    // instead of promising a check that HttpTransport does not perform.
                    String authToken = store.getString(PreferenceConstants.PREF_AUTH_TOKEN);
                    authEnabled = !HttpTransport.normalizeToken(authToken).isEmpty();
                }
            }
            result.put("plainTextMode", plainTextMode); //$NON-NLS-1$
            result.put("checksFolderConfigured", checksFolderConfigured); //$NON-NLS-1$
            result.put("authEnabled", authEnabled); //$NON-NLS-1$

            // EDT-startup render modes and raw requested System properties:
            // the diagnostic for a blank get_form_screenshot / get_form_layout_snapshot.
            Map<String, Object> formRenderFlags = new LinkedHashMap<>();
            formRenderFlags.put(FLAG_NATIVE_LAYOUT_RENDER,
                createRenderFlagState(NativeRenderModeProbe.getStartupNativeRenderMode(),
                    System.getProperty(FLAG_NATIVE_LAYOUT_RENDER)));
            formRenderFlags.put(FLAG_BUFFERED_LAYOUT_RENDER,
                createRenderFlagState(NativeRenderModeProbe.getStartupBufferedRenderMode(),
                    System.getProperty(FLAG_BUFFERED_LAYOUT_RENDER)));
            result.put("formRenderFlags", formRenderFlags); //$NON-NLS-1$

            ProfileResolution active = policy == null ? ProfileResolver.resolveDefault(workspaceSnapshot(activator))
                : policy.getResolution();
            int activeToolCount = policy == null ? registry.getVisibleTools().size() : policy.effectiveToolNames().size();
            result.put("activeProfile", activeProfile(active, advertisedPort, activeToolCount)); //$NON-NLS-1$
            if (includeProfiles)
            {
                result.put("availableProfiles", availableProfiles(active.getSnapshot(), registry.getAllTools(),
                    advertisedPort, includeProfileTools)); //$NON-NLS-1$
            }

            return result.toJson();
        }
        catch (Exception e)
        {
            Activator.logError("Error in get_server_status", e); //$NON-NLS-1$
            return ToolResult.error(e.getMessage()).toJson();
        }
    }

    private static ToolProfileSnapshot workspaceSnapshot(Activator activator)
    {
        var repository = activator == null ? null : activator.getToolProfileRepository();
        return repository == null ? DefaultToolProfileFactory.createSafeSnapshot() : repository.refreshSnapshot();
    }

    private static Map<String, Object> activeProfile(ProfileResolution resolution, int port, int count)
    {
        Map<String, Object> entry = describeProfile(resolution.getEffectiveProfile(), port, resolution.isLegacyEndpoint(), count);
        entry.put("requestedProfileId", resolution.getRequestedProfileId()); //$NON-NLS-1$
        entry.put("fallbackApplied", resolution.isFallbackApplied()); //$NON-NLS-1$
        if (resolution.isFallbackApplied())
        {
            var reason = resolution.getFallbackReason();
            entry.put("fallbackReason", reason.name()); //$NON-NLS-1$
            entry.put("warning", "Requested profile '" + resolution.getRequestedProfileId() //$NON-NLS-1$ //$NON-NLS-2$
                + "' is unavailable (" + reason + "). Connected to profile 'default' instead."); //$NON-NLS-1$ //$NON-NLS-2$
        }
        return entry;
    }

    private static List<Map<String, Object>> availableProfiles(ToolProfileSnapshot snapshot,
        Collection<IMcpTool> catalog, int port, boolean includeTools)
    {
        List<Map<String, Object>> entries = new ArrayList<>();
        for (ToolProfile profile : snapshot.enabledProfiles())
        {
            var resolution = ProfileResolver.resolve(profile.getId(), false, snapshot);
            var allowed = new ProfileToolPolicy(resolution, catalog).effectiveToolNames();
            Map<String, Object> entry = describeProfile(profile, port, false, allowed.size());
            if (includeTools)
            {
                entry.put("allowedTools", allowed.stream().sorted().toList()); //$NON-NLS-1$
            }
            entries.add(entry);
        }
        return entries;
    }

    private static Map<String, Object> describeProfile(ToolProfile profile, int port, boolean legacy, int count)
    {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", profile.getId()); //$NON-NLS-1$
        entry.put("displayName", profile.getDisplayName()); //$NON-NLS-1$
        entry.put("description", profile.getDescription()); //$NON-NLS-1$
        entry.put("revision", profile.getRevision()); //$NON-NLS-1$
        entry.put("endpoint", "http://127.0.0.1:" + port //$NON-NLS-1$ //$NON-NLS-2$
            + (legacy ? "/mcp" : "/mcp/profiles/" + profile.getId())); //$NON-NLS-1$ //$NON-NLS-2$
        entry.put("allowedToolCount", count); //$NON-NLS-1$
        return entry;
    }

    /**
     * Builds one render-flag state from the mode at MCP activation and the raw requested
     * system property. EDT reads the flags into static finals at class initialization and
     * binds its offscreen handler once in {@code HippoLayoutService.INSTANCE}; changing
     * a property later cannot reconfigure that handler. An EDT restart is required.
     */
    private static Map<String, Object> createRenderFlagState(NativeRenderMode startupMode,
        String requested)
    {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("atStartup", startupMode.name().toLowerCase(Locale.ROOT)); //$NON-NLS-1$
        if (requested != null)
        {
            state.put("requested", requested); //$NON-NLS-1$
        }
        return state;
    }
}
