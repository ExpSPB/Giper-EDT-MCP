/**
 * MCP Server for EDT - Tests
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server.bridge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;

import fm.giper.edt.mcp.server.protocol.McpProtocolHandler;
import fm.giper.edt.mcp.server.Activator;
import fm.giper.edt.mcp.server.profiles.DefaultToolProfileFactory;
import fm.giper.edt.mcp.server.profiles.ToolProfile;
import fm.giper.edt.mcp.server.profiles.ToolProfileRepository;
import fm.giper.edt.mcp.server.profiles.ToolProfileSnapshot;
import fm.giper.edt.mcp.server.protocol.McpRequestContext;
import fm.giper.edt.mcp.server.tools.IMcpTool;
import fm.giper.edt.mcp.server.tools.McpToolRegistry;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Proves both consumer contracts of the bridge: the string-name/reflection lookup,
 * and the JDK-typed OSGi alias a consumer uses when it cannot see this package.
 */
public class EdtMcpBridgeTest
{
    private static final String INTERFACE_NAME =
        "fm.giper.edt.mcp.server.bridge.IEdtMcpBridge"; //$NON-NLS-1$
    private static final String IMPLEMENTATION_NAME =
        "fm.giper.edt.mcp.server.bridge.EdtMcpBridge"; //$NON-NLS-1$
    private static final String PROBE_TOOL_NAME = "bridge_echo_probe"; //$NON-NLS-1$
    private static final String SECRET_TOOL_NAME = "bridge_secret_write"; //$NON-NLS-1$

    private final AtomicReference<String> receivedValue = new AtomicReference<>();

    @Before
    public void setUp()
    {
        McpToolRegistry.getInstance().clear();
        McpToolRegistry.getInstance().register(new EchoProbeTool(receivedValue));
        McpToolRegistry.getInstance().register(new EchoProbeTool(SECRET_TOOL_NAME, receivedValue));
    }

    @After
    public void tearDown()
    {
        McpToolRegistry.getInstance().clear();
    }

    @Test
    public void testListToolsThroughStringNamedInterfaceAndReflection() throws Exception
    {
        Object service = newReflectiveService();
        Method listTools = service.getClass().getMethod("listTools"); //$NON-NLS-1$
        String json = (String) listTools.invoke(service);

        JsonArray tools = JsonParser.parseString(json).getAsJsonArray();
        assertTrue(containsName(tools, PROBE_TOOL_NAME));
        JsonObject tool = findNamed(tools, PROBE_TOOL_NAME);
        assertEquals("Bridge reflection probe", tool.get("description").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals(2, tool.size());
    }

    @Test
    public void testDefaultProfileListsEnabledToolsAndExplicitProfileIsNarrower()
    {
        EdtMcpBridge bridge = new EdtMcpBridge(McpToolRegistry.getInstance(), new McpProtocolHandler(),
            () -> reviewSnapshot(Set.of(PROBE_TOOL_NAME)));

        JsonArray defaultList = JsonParser.parseString(bridge.listTools()).getAsJsonArray();
        assertTrue(containsName(defaultList, PROBE_TOOL_NAME));
        assertTrue(containsName(defaultList, SECRET_TOOL_NAME));

        JsonArray reviewList = JsonParser.parseString(bridge.listTools("review")).getAsJsonArray(); //$NON-NLS-1$
        assertTrue(containsName(reviewList, PROBE_TOOL_NAME));
        assertFalse(containsName(reviewList, SECRET_TOOL_NAME));
    }

    @Test
    public void testStaleDefaultListCannotCallAToolForbiddenOnExplicitProfile()
    {
        EdtMcpBridge bridge = new EdtMcpBridge(McpToolRegistry.getInstance(), new McpProtocolHandler(),
            () -> reviewSnapshot(Set.of(PROBE_TOOL_NAME)));

        String listed = bridge.listTools();
        assertTrue(listed.contains(SECRET_TOOL_NAME));

        String denied = bridge.callTool(SECRET_TOOL_NAME, "{\"value\":\"no\"}", "review"); //$NON-NLS-1$ //$NON-NLS-2$
        JsonObject response = JsonParser.parseString(denied).getAsJsonObject();
        assertTrue(response.has("error")); //$NON-NLS-1$
        String message = response.getAsJsonObject("error").get("message").getAsString(); //$NON-NLS-1$ //$NON-NLS-2$
        assertTrue(message.contains(SECRET_TOOL_NAME));
        assertTrue(message.contains("review")); //$NON-NLS-1$
        assertNull(receivedValue.get());
    }

    @Test
    public void testDirectCallOnDefaultStillReachesAListedTool()
    {
        EdtMcpBridge bridge = new EdtMcpBridge(McpToolRegistry.getInstance(), new McpProtocolHandler(),
            () -> reviewSnapshot(Set.of(PROBE_TOOL_NAME)));
        String json = bridge.callTool(PROBE_TOOL_NAME, "{\"value\":\"ok\"}"); //$NON-NLS-1$
        assertEquals("ok", receivedValue.get()); //$NON-NLS-1$
        assertTrue(json.contains("echo:ok")); //$NON-NLS-1$
    }

    @Test
    public void testCallToolThroughReflectionUsesNormalMcpDispatch() throws Exception
    {
        Object service = newReflectiveService();
        Method callTool = service.getClass().getMethod("callTool", //$NON-NLS-1$
            String.class, String.class);
        String bridgeJson = (String) callTool.invoke(service, PROBE_TOOL_NAME,
            "{\"value\":\"hello\"}"); //$NON-NLS-1$

        assertEquals("hello", receivedValue.get()); //$NON-NLS-1$
        JsonObject response = JsonParser.parseString(bridgeJson).getAsJsonObject();
        assertEquals("2.0", response.get("jsonrpc").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals(1L, response.get("id").getAsLong()); //$NON-NLS-1$
        JsonObject result = response.getAsJsonObject("result"); //$NON-NLS-1$
        assertEquals("echo:hello", result.getAsJsonArray("content").get(0) //$NON-NLS-1$ //$NON-NLS-2$
            .getAsJsonObject().get("text").getAsString()); //$NON-NLS-1$

        // Byte-for-byte equality with the regular handler proves this is not a
        // second raw IMcpTool.execute dispatcher hidden in the bridge.
        String directRequest = "{\"jsonrpc\":\"2.0\",\"id\":1," //$NON-NLS-1$
            + "\"method\":\"tools/call\",\"params\":{" //$NON-NLS-1$
            + "\"name\":\"bridge_echo_probe\",\"arguments\":{\"value\":\"hello\"}}}"; //$NON-NLS-1$
        String directJson = new McpProtocolHandler().processRequest(directRequest);
        assertEquals(directJson, bridgeJson);
    }

    @Test
    public void testInvalidArgsJsonReturnsActionableJsonRpcError() throws Exception
    {
        Object service = newReflectiveService();
        Method callTool = service.getClass().getMethod("callTool", //$NON-NLS-1$
            String.class, String.class);
        String json = (String) callTool.invoke(service, PROBE_TOOL_NAME, "[]"); //$NON-NLS-1$
        JsonObject response = JsonParser.parseString(json).getAsJsonObject();
        assertEquals(-32602, response.getAsJsonObject("error").get("code").getAsInt()); //$NON-NLS-1$ //$NON-NLS-2$
        String message = response.getAsJsonObject("error").get("message").getAsString(); //$NON-NLS-1$ //$NON-NLS-2$
        assertTrue(message.contains("argsJson")); //$NON-NLS-1$
        assertTrue(message.contains("Pass an object")); //$NON-NLS-1$
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testBridgeJdkFunctionTypesDelegateToDefaultProfile() throws Exception
    {
        Object service = newReflectiveService();
        assertTrue(service instanceof BiFunction);
        assertTrue(service instanceof Supplier);
        assertTrue(((Supplier<String>) service).get().contains(PROBE_TOOL_NAME));
        String json = ((BiFunction<String, String, String>) service).apply(PROBE_TOOL_NAME,
            "{\"value\":\"typed\"}"); //$NON-NLS-1$
        assertEquals("typed", receivedValue.get()); //$NON-NLS-1$
        assertTrue(json.contains("echo:typed")); //$NON-NLS-1$
    }

    @Test
    public void testJdkAliasesCannotBypassTheDefaultAllowlist()
    {
        EdtMcpBridge bridge = new EdtMcpBridge(McpToolRegistry.getInstance(), new McpProtocolHandler(),
            () -> ToolProfileSnapshot.of(1L,
                List.of(DefaultToolProfileFactory.createDefault(Set.of(PROBE_TOOL_NAME)))));
        Supplier<String> tools = bridge;
        assertTrue(tools.get().contains(PROBE_TOOL_NAME));
        assertFalse(tools.get().contains(SECRET_TOOL_NAME));
        BiFunction<String, String, String> call = bridge;
        JsonObject denied = JsonParser.parseString(
            call.apply(SECRET_TOOL_NAME, "{\"value\":\"blocked\"}")).getAsJsonObject(); //$NON-NLS-1$
        assertTrue(denied.has("error")); //$NON-NLS-1$
        assertNull(receivedValue.get());
    }

    @Test
    public void testPublicBridgeWithoutActivatorUsesStatusOnlyPolicy() throws Exception
    {
        withActivator(null, () -> {
            EdtMcpBridge bridge = new EdtMcpBridge();
            assertUnavailablePolicyCannotDispatch(bridge, isolatedHandler(bridge));
        });
    }

    @Test
    public void testPublicBridgeWithoutProfileRepositoryUsesStatusOnlyPolicy() throws Exception
    {
        withActivator(mock(Activator.class), () -> {
            EdtMcpBridge bridge = new EdtMcpBridge();
            assertUnavailablePolicyCannotDispatch(bridge, isolatedHandler(bridge));
        });
    }

    @Test
    public void testRetainedPublicBridgeLosingRepositoryCannotWidenItsRestrictedPolicy() throws Exception
    {
        ToolProfileRepository repository = mock(ToolProfileRepository.class);
        when(repository.getSnapshot()).thenReturn(ToolProfileSnapshot.of(1L,
            List.of(DefaultToolProfileFactory.createDefault(Set.of(PROBE_TOOL_NAME)))));
        AtomicReference<ToolProfileRepository> current = new AtomicReference<>(repository);
        Activator activator = mock(Activator.class);
        when(activator.getToolProfileRepository()).thenAnswer(invocation -> current.get());
        withActivator(activator, () -> {
            EdtMcpBridge bridge = new EdtMcpBridge();
            McpProtocolHandler handler = isolatedHandler(bridge);
            String allowed = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"content\":[]}}"; //$NON-NLS-1$
            when(handler.processRequest(anyString(), any(McpRequestContext.class))).thenReturn(allowed);
            assertTrue(bridge.listTools().contains(PROBE_TOOL_NAME));
            assertFalse(bridge.listTools().contains(SECRET_TOOL_NAME));
            assertTrue(JsonParser.parseString(bridge.callTool(SECRET_TOOL_NAME, "{}")) //$NON-NLS-1$
                .getAsJsonObject().has("error")); //$NON-NLS-1$
            verifyZeroInteractions(handler);
            assertEquals(allowed, bridge.callTool(PROBE_TOOL_NAME, "{}")); //$NON-NLS-1$
            verify(handler).processRequest(anyString(), any(McpRequestContext.class));

            // The same object must read the current policy; losing it cannot resurrect any tools.
            reset(handler);
            current.set(null);
            assertUnavailablePolicyCannotDispatch(bridge, handler);
        });
    }

    private void assertUnavailablePolicyCannotDispatch(EdtMcpBridge bridge, McpProtocolHandler handler)
        throws Exception
    {
        McpToolRegistry.getInstance().register(new EchoProbeTool("get_server_status", receivedValue)); //$NON-NLS-1$
        Supplier<String> typedList = bridge;
        JsonArray listed = JsonParser.parseString(typedList.get()).getAsJsonArray();
        assertEquals(1, listed.size());
        assertTrue(containsName(listed, "get_server_status")); //$NON-NLS-1$
        Object untyped = bridge;
        String reflectedList = (String)untyped.getClass().getMethod("listTools").invoke(untyped); //$NON-NLS-1$
        assertEquals(listed, JsonParser.parseString(reflectedList).getAsJsonArray());

        BiFunction<String, String, String> typedCall = bridge;
        String direct = bridge.callTool(SECRET_TOOL_NAME, "{\"value\":\"forbidden\"}"); //$NON-NLS-1$
        String typed = typedCall.apply(PROBE_TOOL_NAME, "{\"value\":\"forbidden\"}"); //$NON-NLS-1$
        String reflected = (String)untyped.getClass().getMethod("callTool", String.class, String.class) //$NON-NLS-1$
            .invoke(untyped, SECRET_TOOL_NAME, "{\"value\":\"forbidden\"}"); //$NON-NLS-1$
        for (String denied : List.of(direct, typed, reflected))
        {
            JsonObject response = JsonParser.parseString(denied).getAsJsonObject();
            assertEquals(-32601, response.getAsJsonObject("error").get("code").getAsInt()); //$NON-NLS-1$ //$NON-NLS-2$
        }
        assertNull("a forbidden tool must never execute", receivedValue.get()); //$NON-NLS-1$
        verifyZeroInteractions(handler);
    }

    /** Replace only this bridge's handler to prove refusal precedes the protocol dispatcher. */
    private static McpProtocolHandler isolatedHandler(EdtMcpBridge bridge) throws Exception
    {
        Field field = EdtMcpBridge.class.getDeclaredField("protocolHandler"); //$NON-NLS-1$
        field.setAccessible(true);
        McpProtocolHandler handler = mock(McpProtocolHandler.class);
        field.set(bridge, handler);
        return handler;
    }

    /** Mirrors the existing Activator-boundary tests and always restores the real singleton. */
    private static void withActivator(Activator activator, CheckedAssertion assertion) throws Exception
    {
        Field field = Activator.class.getDeclaredField("plugin"); //$NON-NLS-1$
        field.setAccessible(true);
        Object saved = field.get(null);
        try
        {
            field.set(null, activator);
            assertion.run();
        }
        finally
        {
            field.set(null, saved);
        }
    }

    @FunctionalInterface
    private interface CheckedAssertion
    {
        void run() throws Exception;
    }

    @Test
    public void testOsgiRegistrationIsFilterableUnderTheInterfaceName() throws Exception
    {
        Bundle bundle = FrameworkUtil.getBundle(EdtMcpBridgeTest.class);
        assumeNotNull(bundle);
        BundleContext context = bundle.getBundleContext();
        assumeNotNull(context);

        String filter = '(' + IEdtMcpBridge.SERVICE_PROPERTY + '='
            + IEdtMcpBridge.SERVICE_PROPERTY_VALUE + ')';
        Collection<ServiceReference<IEdtMcpBridge>> references =
            context.getServiceReferences(IEdtMcpBridge.class, filter);
        if (references.isEmpty())
        {
            ServiceReference<?>[] byName =
                context.getServiceReferences(IEdtMcpBridge.class.getName(), filter);
            assertNotNull("the bridge must be findable by IEdtMcpBridge + service property", byName); //$NON-NLS-1$
            assertEquals("the bridge must be findable by IEdtMcpBridge + service property", //$NON-NLS-1$
                1, byName.length);
            Object mcp = context.getService(byName[0]);
            assertTrue(mcp instanceof BiFunction);
            assertTrue(mcp instanceof Supplier);
            assertEquals(1, context.getServiceReferences(BiFunction.class, filter).size());
            assertEquals(1, context.getServiceReferences(Supplier.class, filter).size());
            Method callTool = mcp.getClass().getMethod("callTool", //$NON-NLS-1$
                String.class, String.class, String.class);
            String json = (String) callTool.invoke(mcp, PROBE_TOOL_NAME,
                "{\"value\":\"osgi\"}", "default"); //$NON-NLS-1$ //$NON-NLS-2$
            assertNotNull(json);
            return;
        }
        assertEquals(1, references.size());
        IEdtMcpBridge mcp = context.getService(references.iterator().next());
        String json = mcp.callTool(PROBE_TOOL_NAME, "{\"value\":\"osgi\"}", "default"); //$NON-NLS-1$ //$NON-NLS-2$
        assertNotNull(json);
        JsonObject envelope = JsonParser.parseString(json).getAsJsonObject();
        assertEquals("2.0", envelope.get("jsonrpc").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertTrue(envelope.has("result") || envelope.has("error")); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals(1, context.getServiceReferences(BiFunction.class, filter).size());
        assertEquals(1, context.getServiceReferences(Supplier.class, filter).size());
    }

    private static boolean containsName(JsonArray tools, String name)
    {
        return findNamed(tools, name) != null;
    }

    private static JsonObject findNamed(JsonArray tools, String name)
    {
        for (int i = 0; i < tools.size(); i++)
        {
            JsonObject item = tools.get(i).getAsJsonObject();
            if (name.equals(item.get("name").getAsString())) //$NON-NLS-1$
            {
                return item;
            }
        }
        return null;
    }

    private static ToolProfileSnapshot reviewSnapshot(Set<String> reviewTools)
    {
        return ToolProfileSnapshot.of(1L, List.of(
            DefaultToolProfileFactory.createDefault(Set.of(PROBE_TOOL_NAME, SECRET_TOOL_NAME)),
            ToolProfile.builder()
                .id("review") //$NON-NLS-1$
                .displayName("Review") //$NON-NLS-1$
                .allowedTools(reviewTools)
                .build()));
    }

    private static Object newReflectiveService() throws Exception
    {
        // This deliberately mirrors the Workmate/JShell consumer: neither type is
        // imported; both are resolved from stable string names.
        Class<?> contract = Class.forName(INTERFACE_NAME);
        java.lang.reflect.Constructor<?> ctor = Class.forName(IMPLEMENTATION_NAME)
            .getDeclaredConstructor(McpToolRegistry.class, McpProtocolHandler.class);
        ctor.setAccessible(true);
        Object service = ctor.newInstance(McpToolRegistry.getInstance(), new McpProtocolHandler());
        assertNotNull(service);
        assertTrue(contract.isInstance(service));
        assertTrue(java.lang.reflect.Modifier.isPublic(service.getClass().getModifiers()));
        return service;
    }

    private static final class EchoProbeTool implements IMcpTool
    {
        private final AtomicReference<String> received;

        private final String name;

        private EchoProbeTool(AtomicReference<String> received)
        {
            this(PROBE_TOOL_NAME, received);
        }

        private EchoProbeTool(String name, AtomicReference<String> received)
        {
            this.name = name;
            this.received = received;
        }

        @Override
        public String getName()
        {
            return name;
        }

        @Override
        public String getDescription()
        {
            return "Bridge reflection probe"; //$NON-NLS-1$
        }

        @Override
        public String getInputSchema()
        {
            return "{\"type\":\"object\",\"properties\":{" //$NON-NLS-1$
                + "\"value\":{\"type\":\"string\"}},\"required\":[\"value\"]}"; //$NON-NLS-1$
        }

        @Override
        public String execute(Map<String, String> params)
        {
            String value = params.get("value"); //$NON-NLS-1$
            received.set(value);
            return "echo:" + value; //$NON-NLS-1$
        }

        @Override
        public ResponseType getResponseType()
        {
            return ResponseType.TEXT;
        }
    }
}
