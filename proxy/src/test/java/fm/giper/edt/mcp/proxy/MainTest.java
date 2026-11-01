/**
 * MCP Server for EDT - Proxy Tests
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.proxy;

import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * The connection ceiling {@link Main} installs before any HTTP server exists (issue #567 review).
 *
 * <p>It is the only admission decision in the proxy that does not need a worker thread: the
 * JDK's accept loop closes a connection outright once the limit is reached, so a burst arriving
 * while every worker is blocked on a backend cannot retain sockets without bound. The handler's
 * own 50-in-flight shed sits far below it and does the ordinary work.
 */
public class MainTest
{
    private static final String PROPERTY = "jdk.httpserver.maxConnections";
    private static final String DRAIN_PROPERTY = "sun.net.httpserver.drainAmount";

    private String originalValue;
    private String originalDrainValue;

    @Before
    public void rememberProperty()
    {
        originalValue = System.getProperty(PROPERTY);
        originalDrainValue = System.getProperty(DRAIN_PROPERTY);
    }

    @After
    public void restoreProperty()
    {
        if (originalValue == null)
        {
            System.clearProperty(PROPERTY);
        }
        else
        {
            System.setProperty(PROPERTY, originalValue);
        }
        if (originalDrainValue == null)
        {
            System.clearProperty(DRAIN_PROPERTY);
        }
        else
        {
            System.setProperty(DRAIN_PROPERTY, originalDrainValue);
        }
    }

    @Test
    public void aCeilingIsInstalledWhenTheOperatorSetNone()
    {
        System.clearProperty(PROPERTY);

        Main.capOpenConnections();

        assertEquals("the proxy must not run with the JDK default of 'no limit'",
            "1024", System.getProperty(PROPERTY));
    }

    @Test
    public void anOperatorsOwnValueWins()
    {
        // Including turning the limit OFF: -1 is the JDK's "no limit", and an operator who asked
        // for it must get it rather than have this quietly overrule them.
        for (String chosen : new String[] {"64", "-1", "100000"})
        {
            System.setProperty(PROPERTY, chosen);

            Main.capOpenConnections();

            assertEquals("an explicit -D must win", chosen, System.getProperty(PROPERTY));
        }
    }

    @Test
    public void implicitRequestDrainIsDisabledUnlessTheOperatorConfiguredIt()
    {
        System.clearProperty(DRAIN_PROPERTY);
        Main.disableImplicitRequestDrain();
        assertEquals("0", System.getProperty(DRAIN_PROPERTY));
        System.setProperty(DRAIN_PROPERTY, "4096");
        Main.disableImplicitRequestDrain();
        assertEquals("4096", System.getProperty(DRAIN_PROPERTY));
    }
}
