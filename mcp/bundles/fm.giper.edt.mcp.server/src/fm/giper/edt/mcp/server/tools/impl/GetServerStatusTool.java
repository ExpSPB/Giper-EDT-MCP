/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server.tools.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jface.preference.IPreferenceStore;

import fm.giper.edt.mcp.server.Activator;
import fm.giper.edt.mcp.server.McpServer;
import fm.giper.edt.mcp.server.preferences.PreferenceConstants;
import fm.giper.edt.mcp.server.profiles.DefaultToolProfileFactory;
import fm.giper.edt.mcp.server.profiles.FallbackReason;
import fm.giper.edt.mcp.server.profiles.ProfileResolution;
import fm.giper.edt.mcp.server.profiles.ProfileResolver;
import fm.giper.edt.mcp.server.profiles.ProfileToolPolicy;
import fm.giper.edt.mcp.server.profiles.ToolProfile;
import fm.giper.edt.mcp.server.profiles.ToolProfileRepository;
import fm.giper.edt.mcp.server.profiles.ToolProfileSnapshot;
import fm.giper.edt.mcp.server.protocol.JsonSchemaBuilder;
import fm.giper.edt.mcp.server.protocol.JsonUtils;
import fm.giper.edt.mcp.server.protocol.McpConstants;
import fm.giper.edt.mcp.server.protocol.McpRequestContext;
import fm.giper.edt.mcp.server.protocol.ToolResult;
import fm.giper.edt.mcp.server.tools.IMcpTool;
import fm.giper.edt.mcp.server.tools.McpToolRegistry;
import fm.giper.edt.mcp.server.transport.McpEndpoint;

/**
 * Self-diagnosis tool: returns the running MCP server's introspection snapshot
 * so a client can answer "why is the screenshot blank / why is JSON plain /
 * which tools are exposed" without guessing.
 * <p>
 * Reports: listening port, MCP protocol version, plugin and EDT version, the
 * enabled/total tool counts, the {@code plainTextMode} and {@code checksFolder}
 * preference flags, the two form-render JVM flags
 * ({@code -DnativeFormBufferedLayoutRender} / {@code -DnativeFormLayoutRender}),
 * whether authentication is enabled, and the active tool-profile resolution.
 * <p>
 * SECURITY: the auth token value is never emitted — only the {@code authEnabled}
 * boolean derived from whether {@link PreferenceConstants#PREF_AUTH_TOKEN} is
 * non-empty. The {@code checksFolder} path is likewise reduced to a boolean
 * ({@code checksFolderConfigured}), never the path itself. Profile endpoints
 * are built from the listen port and {@link McpEndpoint} prefixes, never from
 * an untrusted Host header.
 * <p>
 * Read-only: does not switch the request context or enlarge the published
 * surface. {@code execute()} is null-safe for a headless context (a missing
 * {@link Activator} or {@link McpServer} degrades to {@code unknown}/{@code false}
 * rather than throwing).
 */
public class GetServerStatusTool implements IMcpTool
{
    public static final String NAME = "get_server_status"; //$NON-NLS-1$

    /** Form-render JVM flag: enables the offscreen buffered layout render. */
    private static final String FLAG_BUFFERED_LAYOUT_RENDER = "nativeFormBufferedLayoutRender"; //$NON-NLS-1$

    /** Form-render JVM flag: selects the native (C++) layout render path. */
    private static final String FLAG_NATIVE_LAYOUT_RENDER = "nativeFormLayoutRender"; //$NON-NLS-1$

    /** Output key: whether the MCP server is currently running. */
    private static final String KEY_RUNNING = "running"; //$NON-NLS-1$

    private static final String PARAM_INCLUDE_PROFILES = "includeProfiles"; //$NON-NLS-1$
    private static final String PARAM_INCLUDE_PROFILE_TOOLS = "includeProfileTools"; //$NON-NLS-1$

    /** Loopback origin for canonical profile URLs (never taken from the request Host). */
    private static final String LOOPBACK_ORIGIN_PREFIX = "http://127.0.0.1:"; //$NON-NLS-1$

    private ToolProfileSnapshot snapshotOverride;
    private Collection<IMcpTool> catalogOverride;

    /**
     * Test hook: publish a snapshot without going through {@link Activator}.
     */
    void setSnapshotForTests(ToolProfileSnapshot snapshot)
    {
        this.snapshotOverride = snapshot;
    }

    /**
     * Test hook: catalog used by {@link ProfileToolPolicy} (must include this
     * status tool so {@code allowedToolCount} matches the published surface).
     */
    void setCatalogForTests(Collection<IMcpTool> catalog)
    {
        this.catalogOverride = catalog;
    }

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public String getDescription()
    {
        return "Diagnose the EDT MCP server, its feature flags, and the active tool profile. " //$NON-NLS-1$
            + "includeProfileTools=true requires includeProfiles=true. Parameters and examples: " //$NON-NLS-1$
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
            .booleanProperty(PARAM_INCLUDE_PROFILES,
                "When true, add availableProfiles (enabled profiles only, sorted by id, no allowlist). Default false.") //$NON-NLS-1$
            .booleanProperty(PARAM_INCLUDE_PROFILE_TOOLS,
                "When true, add sorted allowedTools on each availableProfiles entry. Requires includeProfiles=true. Default false.") //$NON-NLS-1$
            .build();
    }

    @Override
    public String getOutputSchema()
    {
        return JsonSchemaBuilder.object()
            .booleanProperty("success", "Whether the operation succeeded", true) //$NON-NLS-1$ //$NON-NLS-2$
            .integerProperty("port", "TCP port the MCP server listens on") //$NON-NLS-1$ //$NON-NLS-2$
            .booleanProperty(KEY_RUNNING, "Whether the MCP server is currently running") //$NON-NLS-1$
            .stringProperty("protocolVersion", "MCP protocol version implemented by the server") //$NON-NLS-1$ //$NON-NLS-2$
            .stringProperty("pluginVersion", "Version of the EDT-MCP plugin") //$NON-NLS-1$ //$NON-NLS-2$
            .stringProperty("edtVersion", "Detected 1C:EDT version") //$NON-NLS-1$ //$NON-NLS-2$
            .integerProperty("enabledTools", "Number of tools currently enabled") //$NON-NLS-1$ //$NON-NLS-2$
            .integerProperty("totalTools", "Total number of registered tools") //$NON-NLS-1$ //$NON-NLS-2$
            .booleanProperty("plainTextMode", "Whether JSON responses are forced to plain text") //$NON-NLS-1$ //$NON-NLS-2$
            .booleanProperty("checksFolderConfigured", "Whether a checks folder path is configured") //$NON-NLS-1$ //$NON-NLS-2$
            .booleanProperty("authEnabled", "Whether bearer-token authentication is enabled") //$NON-NLS-1$ //$NON-NLS-2$
            .objectProperty("formRenderFlags", "Form-render JVM flag states keyed by flag name") //$NON-NLS-1$ //$NON-NLS-2$
            .objectProperty("activeProfile", //$NON-NLS-1$
                "Requested vs effective profile, revision, canonical endpoint, allowedToolCount, fallback flags") //$NON-NLS-1$
            .objectArrayProperty("availableProfiles", //$NON-NLS-1$
                "Enabled profiles when includeProfiles=true; optional allowedTools when includeProfileTools=true") //$NON-NLS-1$
            .build();
    }

    @Override
    public String execute(Map<String, String> params)
    {
        return execute(params, McpRequestContext.legacyDefault());
    }

    @Override
    public String execute(Map<String, String> params, McpRequestContext context)
    {
        try
        {
            boolean includeProfiles = JsonUtils.extractBooleanArgument(params, PARAM_INCLUDE_PROFILES, false);
            boolean includeProfileTools = JsonUtils.extractBooleanArgument(params, PARAM_INCLUDE_PROFILE_TOOLS,
                false);
            if (includeProfileTools && !includeProfiles)
            {
                return ToolResult.error(
                    "includeProfileTools=true requires includeProfiles=true. Call again with both set, "
                    + "or omit includeProfileTools for compact discovery.") //$NON-NLS-1$
                    .toJson();
            }

            Activator activator = Activator.getDefault();

            McpToolRegistry registry = McpToolRegistry.getInstance();
            int totalTools = registry.getToolCount();

            ToolResult result = ToolResult.success();

            // Port: read from the live server when available; null-safe for headless.
            McpServer server = activator != null ? activator.getMcpServer() : null;
            int port;
            if (server != null)
            {
                port = server.getPort();
                result.put("port", port); //$NON-NLS-1$
                result.put(KEY_RUNNING, server.isRunning());
            }
            else
            {
                port = PreferenceConstants.DEFAULT_PORT;
                result.put("port", port); //$NON-NLS-1$
                result.put(KEY_RUNNING, false);
            }
            // A constructed-but-not-started server reports port 0; URLs still use the
            // configured default so discovery stays copy-pasteable.
            int advertisedPort = port > 0 ? port : PreferenceConstants.DEFAULT_PORT;

            result.put("protocolVersion", McpConstants.PROTOCOL_VERSION); //$NON-NLS-1$
            result.put("pluginVersion", McpConstants.PLUGIN_VERSION); //$NON-NLS-1$
            result.put("edtVersion", GetEdtVersionTool.getEdtVersion()); //$NON-NLS-1$

            result.put("totalTools", totalTools); //$NON-NLS-1$

            // Preference-backed flags. Degrade to defaults/false when the
            // preference store is unavailable (headless / no Activator).
            boolean plainTextMode = PreferenceConstants.DEFAULT_PLAIN_TEXT_MODE;
            boolean checksFolderConfigured = false;
            boolean authEnabled = false;
            if (activator != null)
            {
                IPreferenceStore store = activator.getPreferenceStore();
                if (store != null)
                {
                    plainTextMode = store.getBoolean(PreferenceConstants.PREF_PLAIN_TEXT_MODE);

                    // Only whether a checks folder is configured, never the path.
                    String checksFolder = store.getString(PreferenceConstants.PREF_CHECKS_FOLDER);
                    checksFolderConfigured = checksFolder != null && !checksFolder.trim().isEmpty();

                    // Only whether auth is on, never the token value.
                    String authToken = store.getString(PreferenceConstants.PREF_AUTH_TOKEN);
                    authEnabled = authToken != null && !authToken.isEmpty();
                }
            }
            result.put("plainTextMode", plainTextMode); //$NON-NLS-1$
            result.put("checksFolderConfigured", checksFolderConfigured); //$NON-NLS-1$
            result.put("authEnabled", authEnabled); //$NON-NLS-1$

            // Form-render JVM flags (System properties), the diagnostic for a
            // blank get_form_screenshot / get_form_layout_snapshot.
            Map<String, Object> formRenderFlags = new LinkedHashMap<>();
            formRenderFlags.put(FLAG_BUFFERED_LAYOUT_RENDER,
                Boolean.parseBoolean(System.getProperty(FLAG_BUFFERED_LAYOUT_RENDER)));
            formRenderFlags.put(FLAG_NATIVE_LAYOUT_RENDER,
                Boolean.parseBoolean(System.getProperty(FLAG_NATIVE_LAYOUT_RENDER)));
            result.put("formRenderFlags", formRenderFlags); //$NON-NLS-1$

            McpRequestContext requestContext = context != null ? context : McpRequestContext.legacyDefault();
            Collection<IMcpTool> catalog = resolveCatalog(registry);
            ToolProfileSnapshot snapshot = resolveSnapshot(activator, requestContext);
            ProfileResolution resolution = requestContext.getResolution();
            ProfileToolPolicy activePolicy = new ProfileToolPolicy(resolution, catalog);
            result.put("enabledTools", activePolicy.effectiveToolNames().size()); //$NON-NLS-1$

            result.put("activeProfile", buildActiveProfile(resolution, activePolicy, advertisedPort)); //$NON-NLS-1$

            if (includeProfiles)
            {
                result.put("availableProfiles", //$NON-NLS-1$
                    buildAvailableProfiles(snapshot, catalog, advertisedPort, includeProfileTools));
            }

            return result.toJson();
        }
        catch (Exception e)
        {
            Activator.logError("Error in get_server_status", e); //$NON-NLS-1$
            return ToolResult.error(e.getMessage()).toJson();
        }
    }

    private Collection<IMcpTool> resolveCatalog(McpToolRegistry registry)
    {
        if (catalogOverride != null)
        {
            return catalogOverride;
        }
        return registry.getAllTools();
    }

    private ToolProfileSnapshot resolveSnapshot(Activator activator, McpRequestContext context)
    {
        if (snapshotOverride != null)
        {
            return snapshotOverride;
        }
        ToolProfileRepository repository = activator != null ? activator.getToolProfileRepository() : null;
        if (repository != null && repository.getSnapshot() != null)
        {
            return repository.getSnapshot();
        }
        ToolProfile effective = context.getResolution() != null
            ? context.getResolution().getEffectiveProfile()
            : null;
        if (effective != null)
        {
            return ToolProfileSnapshot.of(context.getResolution().getDocumentRevision(), List.of(effective));
        }
        return DefaultToolProfileFactory.createSafeSnapshot();
    }

    private static Map<String, Object> buildActiveProfile(ProfileResolution resolution,
        ProfileToolPolicy policy, int port)
    {
        ToolProfile effective = resolution.getEffectiveProfile();
        Set<String> published = policy.effectiveToolNames();
        Map<String, Object> active = new LinkedHashMap<>();
        active.put("requestedProfileId", resolution.getRequestedProfileId()); //$NON-NLS-1$
        active.put("id", effective.getId()); //$NON-NLS-1$
        active.put("displayName", effective.getDisplayName()); //$NON-NLS-1$
        active.put("description", effective.getDescription()); //$NON-NLS-1$
        active.put("revision", effective.getRevision()); //$NON-NLS-1$
        active.put("endpoint", canonicalEndpoint(effective, port)); //$NON-NLS-1$
        active.put("allowedToolCount", published.size()); //$NON-NLS-1$
        active.put("fallbackApplied", resolution.isFallbackApplied()); //$NON-NLS-1$
        if (resolution.isFallbackApplied())
        {
            FallbackReason reason = resolution.getFallbackReason();
            active.put("fallbackReason", reason.name()); //$NON-NLS-1$
            active.put("warning", fallbackWarning(resolution.getRequestedProfileId(), reason)); //$NON-NLS-1$
        }
        return active;
    }

    private static List<Map<String, Object>> buildAvailableProfiles(ToolProfileSnapshot snapshot,
        Collection<IMcpTool> catalog, int port, boolean includeProfileTools)
    {
        List<Map<String, Object>> listed = new ArrayList<>();
        for (ToolProfile profile : snapshot.enabledProfiles())
        {
            boolean legacy = profile.isDefault();
            ProfileResolution listedResolution = ProfileResolver.resolve(profile.getId(), legacy, snapshot);
            ProfileToolPolicy listedPolicy = new ProfileToolPolicy(listedResolution, catalog);
            Set<String> published = listedPolicy.effectiveToolNames();
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", profile.getId()); //$NON-NLS-1$
            entry.put("displayName", profile.getDisplayName()); //$NON-NLS-1$
            entry.put("description", profile.getDescription()); //$NON-NLS-1$
            entry.put("endpoint", canonicalEndpoint(profile, port)); //$NON-NLS-1$
            entry.put("revision", profile.getRevision()); //$NON-NLS-1$
            entry.put("allowedToolCount", published.size()); //$NON-NLS-1$
            if (includeProfileTools)
            {
                List<String> names = new ArrayList<>(published);
                names.sort(String::compareTo);
                entry.put("allowedTools", names); //$NON-NLS-1$
            }
            listed.add(entry);
        }
        return listed;
    }

    private static String canonicalEndpoint(ToolProfile profile, int port)
    {
        String path = profile.isDefault()
            ? McpEndpoint.LEGACY_PATH
            : new McpEndpoint(McpEndpoint.PROFILES_PREFIX + profile.getId(), profile.getId(), false)
                .canonicalPath();
        return LOOPBACK_ORIGIN_PREFIX + port + path;
    }

    private static String fallbackWarning(String requestedProfileId, FallbackReason reason)
    {
        return "Requested profile '" + requestedProfileId + "' is unavailable (" + reason //$NON-NLS-1$ //$NON-NLS-2$
            + "). Connected to profile 'default' instead. Treat activeProfile.id as the source of truth."; //$NON-NLS-1$
    }
}
