/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.history;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import org.junit.Test;

/**
 * Tests for {@link McpCallRecord}: it is an immutable value object whose getters
 * round-trip the constructor arguments (including {@code null} for the optional
 * fields).
 */
public class McpCallRecordTest
{
    @Test
    public void testGettersRoundTripConstructorArguments()
    {
        // Distinct original sizes (100/200) to prove they round-trip independently of the
        // stored bodies — the recorder passes the PRE-truncation lengths here.
        McpCallRecord record =
            new McpCallRecord(1234L, "tools/call", "list_projects", "{\"req\":1}", "{\"res\":2}", 42L, 100, 200);

        assertEquals("timestampMs must round-trip", 1234L, record.getTimestampMs());
        assertEquals("method must round-trip", "tools/call", record.getMethod());
        assertEquals("toolName must round-trip", "list_projects", record.getToolName());
        assertEquals("requestJson must round-trip", "{\"req\":1}", record.getRequestJson());
        assertEquals("responseJson must round-trip", "{\"res\":2}", record.getResponseJson());
        assertEquals("durationMs must round-trip", 42L, record.getDurationMs());
        assertEquals("originalRequestChars must round-trip", 100, record.getOriginalRequestChars());
        assertEquals("originalResponseChars must round-trip", 200, record.getOriginalResponseChars());
    }

    @Test
    public void testNullableFieldsAreTolerated()
    {
        // method/toolName/requestJson/responseJson are all nullable; the record
        // stores them verbatim without throwing.
        McpCallRecord record = new McpCallRecord(0L, null, null, null, null, 0L, 0, 0);

        assertNull("method may be null", record.getMethod());
        assertNull("toolName may be null", record.getToolName());
        assertNull("requestJson may be null", record.getRequestJson());
        assertNull("responseJson may be null", record.getResponseJson());
        assertEquals("timestampMs is preserved", 0L, record.getTimestampMs());
        assertEquals("durationMs is preserved", 0L, record.getDurationMs());
        assertEquals("originalRequestChars is preserved", 0, record.getOriginalRequestChars());
        assertEquals("originalResponseChars is preserved", 0, record.getOriginalResponseChars());
    }

    @Test
    public void testClassAndAllFieldsAreFinal()
    {
        // Immutability contract: the class is final and carries no mutable state and
        // no setter can rebind a field.
        assertTrue("McpCallRecord must be final",
            Modifier.isFinal(McpCallRecord.class.getModifiers()));

        for (Field field : McpCallRecord.class.getDeclaredFields())
        {
            if (field.isSynthetic())
            {
                continue;
            }
            assertTrue("field must be final: " + field.getName(),
                Modifier.isFinal(field.getModifiers()));
        }
    }

    @Test
    public void testProfileAndSessionFieldsRoundTripAndLegacyOverloadIsNull()
    {
        McpCallRecord legacy = new McpCallRecord(1L, "tools/call", "t", "{}", "{}", 1L, 2, 2);
        assertNull(legacy.getRequestedProfileId());
        assertNull(legacy.getEffectiveProfileId());
        assertNull(legacy.getFallbackReason());
        assertNull(legacy.getSessionId());

        McpCallRecord full = new McpCallRecord(1L, "tools/call", "t", "{}", "{}", 1L, 2, 2,
            "review", "default", "UNKNOWN_PROFILE", "sess-1"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        assertEquals("review", full.getRequestedProfileId()); //$NON-NLS-1$
        assertEquals("default", full.getEffectiveProfileId()); //$NON-NLS-1$
        assertEquals("UNKNOWN_PROFILE", full.getFallbackReason()); //$NON-NLS-1$
        assertEquals("sess-1", full.getSessionId()); //$NON-NLS-1$
    }

    @Test
    public void testNoSetterMethodsExposed()
    {
        // A value object exposes only accessors; assert nothing named set* leaks in.
        for (java.lang.reflect.Method method : McpCallRecord.class.getDeclaredMethods())
        {
            assertTrue("no setter allowed on an immutable record: " + method.getName(),
                !method.getName().startsWith("set"));
        }
    }
}
