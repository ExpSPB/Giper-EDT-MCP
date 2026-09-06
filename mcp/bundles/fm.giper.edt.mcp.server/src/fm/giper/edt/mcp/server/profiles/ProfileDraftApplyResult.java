/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server.profiles;

/**
 * Outcome of overlaying an imported profile onto the selected draft.
 */
public final class ProfileDraftApplyResult
{
    private final boolean ok;
    private final String message;

    private ProfileDraftApplyResult(boolean ok, String message)
    {
        this.ok = ok;
        this.message = message;
    }

    public static ProfileDraftApplyResult ok()
    {
        return new ProfileDraftApplyResult(true, null);
    }

    public static ProfileDraftApplyResult failed(String message)
    {
        return new ProfileDraftApplyResult(false, message);
    }

    public boolean isOk()
    {
        return ok;
    }

    public String getMessage()
    {
        return message;
    }
}
