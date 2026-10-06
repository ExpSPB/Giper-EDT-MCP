/**
 * MCP Server for EDT - Tests
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */
package fm.giper.edt.mcp.server.transport;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import fm.giper.edt.mcp.server.McpServer;
import fm.giper.edt.mcp.server.protocol.McpProtocolHandler;
import fm.giper.edt.mcp.server.protocol.jsonrpc.JsonRpcRequest;
import com.sun.net.httpserver.HttpServer;

/** Counts parsing through both direct and interruptible HTTP dispatch. */
public class ParsedRequestTransportTest
{
    @Test
    public void eachHttpRequestIsParsedOnceAcrossTheWholeDispatchChain() throws IOException
    {
        AtomicInteger parses = new AtomicInteger();
        McpProtocolHandler protocol = new McpProtocolHandler() {
            @Override
            public JsonRpcRequest parse(String body)
            {
                parses.incrementAndGet();
                return super.parse(body);
            }
        };
        McpServer mcp = new McpServer();
        HttpServer http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0); //$NON-NLS-1$
        http.createContext("/mcp", new McpHttpHandler(mcp, protocol, //$NON-NLS-1$
            new InterruptibleToolExecutor(mcp, protocol), false));
        http.start();
        try
        {
            for (String body : new String[] {
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}", //$NON-NLS-1$
                "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\","
                    + "\"params\":{\"name\":\"unknown_parse_count_probe\",\"arguments\":{}}}" //$NON-NLS-1$
            })
            {
                int before = parses.get();
                HttpURLConnection connection = (HttpURLConnection) new URL(
                    "http://127.0.0.1:" + http.getAddress().getPort() + "/mcp").openConnection(); //$NON-NLS-1$ //$NON-NLS-2$
                try
                {
                    connection.setConnectTimeout(5000);
                    connection.setReadTimeout(5000);
                    connection.setRequestMethod("POST"); //$NON-NLS-1$
                    connection.setRequestProperty("Content-Type", "application/json"); //$NON-NLS-1$ //$NON-NLS-2$
                    connection.setDoOutput(true);
                    try (var output = connection.getOutputStream())
                    {
                        output.write(body.getBytes(StandardCharsets.UTF_8));
                    }
                    assertEquals(200, connection.getResponseCode());
                    try (var input = connection.getInputStream())
                    {
                        input.readAllBytes();
                    }
                    assertEquals(1, parses.get() - before);
                }
                finally
                {
                    connection.disconnect();
                }
            }
        }
        finally
        {
            http.stop(0);
        }
    }
}
