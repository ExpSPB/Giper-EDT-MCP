/**
 * Minimal stand-in for {@code com.jcraft.jsch.JSchException} on the unit-test classpath.
 * The real class lives in the platform; this stub lets {@link fm.giper.edt.mcp.server.utils.PlatformFailuresTest}
 * exercise the JSch provenance path without bundling JSch in the test fragment.
 */
package com.jcraft.jsch;

@SuppressWarnings("serial")
public class JSchException extends Exception
{
    public JSchException(String message)
    {
        super(message);
    }
}
