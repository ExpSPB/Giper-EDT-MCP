/**
 * MCP Server for EDT - Tests
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server.utils.compare;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com._1c.g5.v8.dt.compare.model.ComparisonNode;

/**
 * Tests for {@link SupportStateReader} on EDT 2025.2, where support-settings comparison node types
 * are absent and the reader is deliberately degraded.
 */
public class SupportStateReaderTest
{
    /** A null node still yields a well-formed empty state, not {@code null}. */
    @Test
    public void testANullNodeReturnsDegradedEmptyState()
    {
        SupportStateReader.SupportState state = SupportStateReader.read(null);
        assertNotNull("degraded reader never returns null", state); //$NON-NLS-1$
        assertTrue("null node is an empty support state", state.isEmpty()); //$NON-NLS-1$
    }

    /** Any non-null node yields the same degraded empty state on 2025.2. */
    @Test
    public void testAnyNodeReturnsDegradedEmptyState()
    {
        SupportStateReader.SupportState state =
            SupportStateReader.read(org.mockito.Mockito.mock(ComparisonNode.class));
        assertNotNull(state);
        assertTrue(state.isEmpty());
    }
}
