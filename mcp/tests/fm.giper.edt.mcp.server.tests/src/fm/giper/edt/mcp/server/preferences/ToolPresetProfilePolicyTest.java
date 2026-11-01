/** Copyright (c) 2026 DitriX; modified by ExpSPB in 2026. */
package fm.giper.edt.mcp.server.preferences;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.junit.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import fm.giper.edt.mcp.server.Activator;
import fm.giper.edt.mcp.server.bridge.EdtMcpBridge;
import fm.giper.edt.mcp.server.profiles.DefaultToolProfileFactory;
import fm.giper.edt.mcp.server.profiles.PreferenceToolProfileRepository;
import fm.giper.edt.mcp.server.profiles.ProfileResolver;
import fm.giper.edt.mcp.server.profiles.ProfileToolPolicy;
import fm.giper.edt.mcp.server.profiles.ToolProfile;
import fm.giper.edt.mcp.server.profiles.ToolProfileSnapshot;
import fm.giper.edt.mcp.server.protocol.McpProtocolHandler;
import fm.giper.edt.mcp.server.protocol.McpRequestContext;
import fm.giper.edt.mcp.server.tools.impl.GetServerStatusTool;
import fm.giper.edt.mcp.server.tools.impl.GetToolGuideTool;

/** The same launch restriction must hold at every profile-aware access boundary. */
public class ToolPresetProfilePolicyTest extends ToolPresetSessionTestSupport
{
    @Test
    public void allToolsDoesNotGrantToolsToNamedOrFallbackProfiles() throws Exception
    {
        ToolProfileSnapshot snapshot = profileSnapshot();
        launch("all-tools");
        ProfileToolPolicy narrow = policy("narrow", false, snapshot);
        assertEquals(Set.of("get_server_status", "get_tool_guide"), narrow.effectiveToolNames());
        assertFalse(narrow.isCallable("write_module_source"));
        assertFalse(narrow.isCallable("debug_launch"));
        assertEquals(Set.of("get_server_status", "list_projects"),
            policy("missing", false, snapshot).effectiveToolNames());
        assertEquals(Set.of("get_server_status"),
            policy("missing", false, DefaultToolProfileFactory.createSafeSnapshot()).effectiveToolNames());
    }

    @Test
    public void readOnlyRestrictionRetainsSavedDataAndEndpointRules() throws Exception
    {
        ToolProfileSnapshot snapshot = profileSnapshot();
        launch("code-review");
        ProfileToolPolicy policy = policy("writer", false, snapshot);
        assertEquals(Set.of("get_server_status", "get_tool_guide", "list_projects", "read_module_source"),
            policy.effectiveToolNames());
        assertFalse(policy.isCallable("debug_launch"));
        assertFalse(policy.isCallable("enable_toolset"));
        assertTrue(policy.deniedMessage("write_module_source").contains("launch preset 'code-review'"));
        assertTrue(policy("narrow", false, snapshot).deniedMessage("write_module_source").contains("not allowed by profile"));
        assertTrue(snapshot.get("writer").getAllowedTools().contains("write_module_source"));
        assertTrue(snapshot.get("writer").getAllowedTools().contains("unknown_future_tool"));
        assertEquals("development", snapshot.get("writer").getPresetId());
    }

    @Test
    public void malformedPresetCannotWidenNamedProfile() throws Exception
    {
        for (String value : new String[] {null, "", " ", "custom", "{analysis-only}", "unknown"})
        {
            launch(value);
            assertEquals(Set.of("get_server_status", "get_tool_guide"),
                policy("narrow", false, profileSnapshot()).effectiveToolNames());
        }
    }

    @Test
    public void wireBridgeGuidesAndStatusShareEffectivePolicy() throws Exception
    {
        ToolProfileSnapshot snapshot = profileSnapshot();
        PreferenceToolProfileRepository repository = new PreferenceToolProfileRepository(store);
        repository.replaceAll(0L, snapshot);
        when(Activator.getDefault().getToolProfileRepository()).thenReturn(repository);
        launch("analysis-only");
        McpRequestContext context = context("writer", false, repository.getSnapshot());
        Set<String> expected = Set.of("get_server_status", "get_tool_guide", "list_projects");
        McpProtocolHandler handler = new McpProtocolHandler();
        JsonObject listed = response(handler, "tools/list", "{}", context);
        assertEquals(expected, names(listed.getAsJsonObject("result").getAsJsonArray("tools")));

        Set<String> resourceNames = new TreeSet<>();
        for (JsonElement resource : response(handler, "resources/list", "{}", context)
            .getAsJsonObject("result").getAsJsonArray("resources"))
        {
            resourceNames.add(resource.getAsJsonObject().get("uri").getAsString().substring("guide://".length()));
        }
        assertEquals(expected, resourceNames);
        assertTrue(response(handler, "resources/read", "{\"uri\":\"guide://debug_launch\"}", context).has("error"));
        assertTrue(response(handler, "tools/call", "{\"name\":\"write_module_source\",\"arguments\":{}}", context)
            .toString().contains("launch preset"));
        assertFalse(JsonParser.parseString(new GetToolGuideTool().execute(
            Map.of("toolName", "write_module_source", "mode", "summary"), context))
            .getAsJsonObject().get("success").getAsBoolean());

        EdtMcpBridge bridge = new EdtMcpBridge();
        assertEquals(expected, names(JsonParser.parseString(bridge.listTools("writer")).getAsJsonArray()));
        assertTrue(bridge.callTool("write_module_source", "{}", "writer").contains("launch preset"));

        JsonObject status = JsonParser.parseString(new GetServerStatusTool().execute(
            Map.of("includeProfiles", "true", "includeProfileTools", "true"), context)).getAsJsonObject();
        assertEquals(expected.size(), status.get("enabledTools").getAsInt());
        assertEquals(expected.size(), status.getAsJsonObject("activeProfile").get("allowedToolCount").getAsInt());
        for (JsonElement item : status.getAsJsonArray("availableProfiles"))
        {
            JsonObject profile = item.getAsJsonObject();
            Set<String> allowed = new TreeSet<>();
            profile.getAsJsonArray("allowedTools").forEach(name -> allowed.add(name.getAsString()));
            assertEquals(policy(profile.get("id").getAsString(), profile.get("id").getAsString().equals("default"),
                repository.getSnapshot()).effectiveToolNames(), allowed);
            assertEquals(allowed.size(), profile.get("allowedToolCount").getAsInt());
        }
    }

    private ProfileToolPolicy policy(String id, boolean legacy, ToolProfileSnapshot snapshot)
    {
        return new ProfileToolPolicy(ProfileResolver.resolve(id, legacy, snapshot), registry.getAllTools());
    }

    private static McpRequestContext context(String id, boolean legacy, ToolProfileSnapshot snapshot)
    {
        return McpRequestContext.builder().resolution(ProfileResolver.resolve(id, legacy, snapshot))
            .requestedPath(legacy ? "/mcp" : "/mcp/profiles/" + id).build();
    }

    private static ToolProfileSnapshot profileSnapshot()
    {
        return ToolProfileSnapshot.of(0L, List.of(
            DefaultToolProfileFactory.createDefault(Set.of("list_projects")),
            ToolProfile.builder().id("narrow").displayName("Narrow")
                .allowedTools(Set.of("get_tool_guide")).build(),
            ToolProfile.builder().id("writer").displayName("Writer")
                .presetId("development")
                .allowedTools(Set.of("write_module_source", "list_projects", "read_module_source",
                    "launch", "get_tool_guide", "enable_toolset", "unknown_future_tool")).build()));
    }

    private static JsonObject response(McpProtocolHandler handler, String method, String params,
        McpRequestContext context)
    {
        return JsonParser.parseString(handler.processRequest("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\""
            + method + "\",\"params\":" + params + "}", context)).getAsJsonObject();
    }

    private static Set<String> names(Iterable<JsonElement> tools)
    {
        Set<String> result = new TreeSet<>();
        tools.forEach(tool -> result.add(tool.getAsJsonObject().get("name").getAsString()));
        return result;
    }
}
