/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server.handlers;

import org.eclipse.ui.navigator.CommonViewer;

/**
 * Handler for the "Expand All" command.
 * Expands all nodes in the Navigator tree.
 */
public class ExpandAllHandler extends AbstractNavigatorViewerHandler
{
    @Override
    protected void onViewer(CommonViewer viewer)
    {
        viewer.expandAll();
    }
}
