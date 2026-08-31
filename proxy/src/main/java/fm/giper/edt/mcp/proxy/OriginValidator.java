/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.proxy;

/**
 * Origin allow-list for the proxy's Streamable HTTP transport.
 *
 * <p>Mirrors the plugin's {@code McpOriginValidator} 1:1 — the proxy module has no compile
 * dependency on the plugin, so the policy is duplicated here. A localhost MCP endpoint
 * must reject a non-loopback {@code Origin} (HTTP 403) so a DNS-rebinding browser request
 * from {@code evil.example.com} cannot invoke tools. A missing {@code Origin} is allowed:
 * CLI / MCP clients are not browsers and do not send one.
 *
 * <p>Allowed: {@code localhost} / {@code 127.0.0.1} over http or https (optional port),
 * {@code file://}, the literal {@code "null"} (local HTML files), and
 * {@code vscode-webview://}. Look-alike hosts such as {@code localhost.attacker.com}
 * are rejected (exact host match, not a naive {@code startsWith}).
 */
public final class OriginValidator
{
    private OriginValidator()
    {
        // utility
    }

    /**
     * Validates an {@code Origin} header value.
     *
     * @param origin the Origin header value (must be non-null)
     * @return {@code true} if the origin is on the allow-list
     */
    public static boolean isValidOrigin(String origin)
    {
        return isLoopbackHost(origin, "http://localhost") || //$NON-NLS-1$
               isLoopbackHost(origin, "http://127.0.0.1") || //$NON-NLS-1$
               isLoopbackHost(origin, "https://localhost") || //$NON-NLS-1$
               isLoopbackHost(origin, "https://127.0.0.1") || //$NON-NLS-1$
               origin.startsWith("file://") || //$NON-NLS-1$
               origin.equals("null") || //$NON-NLS-1$
               origin.startsWith("vscode-webview://"); //$NON-NLS-1$
    }

    /**
     * Exact host match: {@code prefix} or {@code prefix} immediately followed by {@code ':'}.
     * Rejects {@code http://localhost.attacker.com} that a naive {@code startsWith} would
     * accept. An {@code Origin} carries no path, so no {@code '/'} handling is required.
     *
     * @param origin the Origin header value (non-null)
     * @param prefix the {@code scheme://host} prefix to match exactly
     * @return {@code true} if the origin is exactly the prefix or the prefix plus a port
     */
    private static boolean isLoopbackHost(String origin, String prefix)
    {
        return origin.equals(prefix) || origin.startsWith(prefix + ":"); //$NON-NLS-1$
    }
}
