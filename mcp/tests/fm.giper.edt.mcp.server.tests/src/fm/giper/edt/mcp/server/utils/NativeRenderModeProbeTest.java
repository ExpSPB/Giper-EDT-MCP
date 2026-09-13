/**
 * MCP Server for EDT - Tests
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

import fm.giper.edt.mcp.server.utils.NativeRenderModeProbe.NativeRenderMode;

/**
 * Tests for {@link NativeRenderModeProbe}.
 */
public class NativeRenderModeProbeTest
{
    @Test
    public void testNativeRenderModeIsAlwaysOneOfTheThreeDocumentedStates()
    {
        NativeRenderMode mode = NativeRenderModeProbe.getNativeRenderMode();

        assertNotNull("the probe must never answer null", mode); //$NON-NLS-1$
        assertEquals("the mode must round-trip through the enum: " + mode, //$NON-NLS-1$
            mode, NativeRenderMode.valueOf(mode.name()));
    }

    @Test
    public void testBufferedRenderModeIsAlwaysOneOfTheThreeDocumentedStates()
    {
        NativeRenderMode mode = NativeRenderModeProbe.getBufferedRenderMode();

        assertNotNull("the probe must never answer null", mode); //$NON-NLS-1$
        assertEquals("the mode must round-trip through the enum: " + mode, //$NON-NLS-1$
            mode, NativeRenderMode.valueOf(mode.name()));
    }

    @Test
    public void testStartupRenderModesAreAlwaysOneOfTheThreeDocumentedStates()
    {
        NativeRenderModeProbe.captureStartupModes();

        assertDocumentedMode(NativeRenderModeProbe.getStartupNativeRenderMode());
        assertDocumentedMode(NativeRenderModeProbe.getStartupBufferedRenderMode());
    }

    @Test
    public void testCaptureStartupModesIsIdempotent()
    {
        NativeRenderModeProbe.captureStartupModes();
        NativeRenderMode nativeMode = NativeRenderModeProbe.getStartupNativeRenderMode();
        NativeRenderMode bufferedMode = NativeRenderModeProbe.getStartupBufferedRenderMode();

        NativeRenderModeProbe.captureStartupModes();

        assertEquals("repeated capture must not change the startup native mode", //$NON-NLS-1$
            nativeMode, NativeRenderModeProbe.getStartupNativeRenderMode());
        assertEquals("repeated capture must not change the startup buffered mode", //$NON-NLS-1$
            bufferedMode, NativeRenderModeProbe.getStartupBufferedRenderMode());
    }

    @Test
    public void testTheAnswerDoesNotFollowTheSystemProperty()
    {
        String property = "nativeFormBufferedLayoutRender"; //$NON-NLS-1$
        String original = System.getProperty(property);
        try
        {
            System.setProperty(property, "true"); //$NON-NLS-1$
            NativeRenderMode withTrue = NativeRenderModeProbe.getBufferedRenderMode();
            System.setProperty(property, "false"); //$NON-NLS-1$
            NativeRenderMode withFalse = NativeRenderModeProbe.getBufferedRenderMode();

            assertEquals("the effective mode must not change with the requested property", //$NON-NLS-1$
                withTrue, withFalse);
        }
        finally
        {
            if (original == null)
            {
                System.clearProperty(property);
            }
            else
            {
                System.setProperty(property, original);
            }
        }
    }

    private static void assertDocumentedMode(NativeRenderMode mode)
    {
        assertNotNull("the startup snapshot must never answer null", mode); //$NON-NLS-1$
        assertEquals("the startup mode must round-trip through the enum: " + mode, //$NON-NLS-1$
            mode, NativeRenderMode.valueOf(mode.name()));
    }
}
