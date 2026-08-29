/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.protocol;

import com.ditrix.edt.mcp.server.profiles.DefaultToolProfileFactory;
import com.ditrix.edt.mcp.server.profiles.ProfileResolution;
import com.ditrix.edt.mcp.server.profiles.ProfileResolver;
import com.ditrix.edt.mcp.server.profiles.ToolProfile;

/**
 * Immutable per-request MCP context. Profile choice comes from the URL, never from
 * session state or a {@code ThreadLocal}.
 */
public final class McpRequestContext
{
    private final ProfileResolution resolution;
    private final String requestedPath;
    private final String protocolVersion;
    private final ClientCapabilities clientCapabilities;
    private final String sessionId;
    private final boolean legacyCompatibilityWrapper;

    private McpRequestContext(ProfileResolution resolution, String requestedPath, String protocolVersion,
        ClientCapabilities clientCapabilities, String sessionId, boolean legacyCompatibilityWrapper)
    {
        this.resolution = resolution;
        this.requestedPath = requestedPath;
        this.protocolVersion = protocolVersion;
        this.clientCapabilities = clientCapabilities == null ? ClientCapabilities.ABSENT : clientCapabilities;
        this.sessionId = sessionId;
        this.legacyCompatibilityWrapper = legacyCompatibilityWrapper;
    }

    /**
     * Synthetic {@code default} context for the old {@code processRequest(String)} wrapper
     * and in-process callers that do not yet pass an HTTP endpoint.
     */
    public static McpRequestContext legacyDefault()
    {
        return builder()
            .resolution(ProfileResolver.resolveDefault(DefaultToolProfileFactory.createSafeSnapshot()))
            .requestedPath("/mcp") //$NON-NLS-1$
            .legacyCompatibilityWrapper(true)
            .build();
    }

    public static Builder builder()
    {
        return new Builder();
    }

    public ProfileResolution getResolution()
    {
        return resolution;
    }

    public String getRequestedPath()
    {
        return requestedPath;
    }

    public String getProtocolVersion()
    {
        return protocolVersion;
    }

    public ClientCapabilities getClientCapabilities()
    {
        return clientCapabilities;
    }

    public String getSessionId()
    {
        return sessionId;
    }

    public boolean isLegacyCompatibilityWrapper()
    {
        return legacyCompatibilityWrapper;
    }

    public String effectiveProfileId()
    {
        return resolution == null ? ToolProfile.DEFAULT_ID : resolution.getEffectiveProfileId();
    }

    public McpRequestContext withClientCapabilities(ClientCapabilities capabilities)
    {
        return new McpRequestContext(resolution, requestedPath, protocolVersion, capabilities, sessionId,
            legacyCompatibilityWrapper);
    }

    public McpRequestContext withSessionId(String newSessionId)
    {
        return new McpRequestContext(resolution, requestedPath, protocolVersion, clientCapabilities,
            newSessionId, legacyCompatibilityWrapper);
    }

    public static final class Builder
    {
        private ProfileResolution resolution;
        private String requestedPath = "/mcp"; //$NON-NLS-1$
        private String protocolVersion = McpConstants.PROTOCOL_VERSION;
        private ClientCapabilities clientCapabilities = ClientCapabilities.ABSENT;
        private String sessionId;
        private boolean legacyCompatibilityWrapper;

        public Builder resolution(ProfileResolution resolution)
        {
            this.resolution = resolution;
            return this;
        }

        public Builder requestedPath(String requestedPath)
        {
            this.requestedPath = requestedPath;
            return this;
        }

        public Builder protocolVersion(String protocolVersion)
        {
            this.protocolVersion = protocolVersion;
            return this;
        }

        public Builder clientCapabilities(ClientCapabilities clientCapabilities)
        {
            this.clientCapabilities = clientCapabilities;
            return this;
        }

        public Builder sessionId(String sessionId)
        {
            this.sessionId = sessionId;
            return this;
        }

        public Builder legacyCompatibilityWrapper(boolean legacyCompatibilityWrapper)
        {
            this.legacyCompatibilityWrapper = legacyCompatibilityWrapper;
            return this;
        }

        public McpRequestContext build()
        {
            ProfileResolution resolved = resolution != null
                ? resolution
                : ProfileResolver.resolveDefault(DefaultToolProfileFactory.createSafeSnapshot());
            return new McpRequestContext(resolved, requestedPath, protocolVersion, clientCapabilities,
                sessionId, legacyCompatibilityWrapper);
        }
    }
}
