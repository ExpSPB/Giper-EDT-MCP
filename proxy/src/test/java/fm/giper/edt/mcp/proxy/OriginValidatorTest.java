/**
 * MCP Server for EDT - Proxy Tests
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.proxy;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Tests for {@link OriginValidator}. Locks the same allow-list as the plugin's
 * {@code McpOriginValidator}: loopback / file / {@code "null"} / vscode-webview are
 * admitted; remote and look-alike hosts are rejected.
 */
public class OriginValidatorTest
{
    @Test
    public void testLocalhostHttpAllowed()
    {
        assertTrue(OriginValidator.isValidOrigin("http://localhost"));
        assertTrue(OriginValidator.isValidOrigin("http://localhost:8764"));
    }

    @Test
    public void testLocalhostHttpsAllowed()
    {
        assertTrue(OriginValidator.isValidOrigin("https://localhost"));
        assertTrue(OriginValidator.isValidOrigin("https://localhost:8764"));
    }

    @Test
    public void testLoopbackIpAllowed()
    {
        assertTrue(OriginValidator.isValidOrigin("http://127.0.0.1"));
        assertTrue(OriginValidator.isValidOrigin("http://127.0.0.1:8764"));
        assertTrue(OriginValidator.isValidOrigin("https://127.0.0.1:8764"));
    }

    @Test
    public void testFileOriginAllowed()
    {
        assertTrue(OriginValidator.isValidOrigin("file:///C:/page.html"));
    }

    @Test
    public void testNullLiteralAllowed()
    {
        assertTrue(OriginValidator.isValidOrigin("null"));
    }

    @Test
    public void testVscodeWebviewAllowed()
    {
        assertTrue(OriginValidator.isValidOrigin("vscode-webview://abc123"));
    }

    @Test
    public void testRemoteHttpRejected()
    {
        assertFalse(OriginValidator.isValidOrigin("http://example.com"));
        assertFalse(OriginValidator.isValidOrigin("https://evil.example.com"));
        assertFalse(OriginValidator.isValidOrigin("http://evil.example.com"));
    }

    @Test
    public void testUnrelatedSchemeRejected()
    {
        assertFalse(OriginValidator.isValidOrigin("ftp://localhost"));
        assertFalse(OriginValidator.isValidOrigin("chrome-extension://abc"));
    }

    @Test
    public void testEmptyStringRejected()
    {
        assertFalse(OriginValidator.isValidOrigin(""));
    }

    @Test
    public void testLookAlikeHostRejected()
    {
        assertFalse(OriginValidator.isValidOrigin("http://localhost.attacker.com"));
        assertFalse(OriginValidator.isValidOrigin("http://127.0.0.1.attacker.com"));
        assertFalse(OriginValidator.isValidOrigin("https://localhost.evil.example"));
        assertFalse(OriginValidator.isValidOrigin("http://localhostx"));
        assertFalse(OriginValidator.isValidOrigin("http://127.0.0.1.attacker.com:8764"));
    }
}
