/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server.profiles;

import java.util.Set;

/**
 * Headless view of the selected draft so exchange orchestration stays in
 * {@code profiles} and does not depend on the preference-page model class.
 */
public interface ProfileDraftSink
{
    ToolProfile getSelected();

    ProfileDraftApplyResult applyImported(ToolProfile imported);

    Set<String> unknownAllowedTools();
}
