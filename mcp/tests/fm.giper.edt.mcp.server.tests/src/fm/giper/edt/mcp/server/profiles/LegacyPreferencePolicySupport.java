/** Copyright (c) 2026 DitriX; modified by ExpSPB in 2026. */
package fm.giper.edt.mcp.server.profiles;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import fm.giper.edt.mcp.server.preferences.ToolSettingsService;
import fm.giper.edt.mcp.server.tools.IMcpTool;
import fm.giper.edt.mcp.server.tools.McpToolRegistry;

/** Adapts upstream single-selection tests to Giper's actual shared authorization policy. */
public final class LegacyPreferencePolicySupport
{
    private LegacyPreferencePolicySupport()
    {
    }

    public static ProfileToolPolicy policy()
    {
        Collection<IMcpTool> catalog = McpToolRegistry.getInstance().getAllTools();
        Set<String> allowed = catalog.stream().map(IMcpTool::getName).collect(Collectors.toSet());
        allowed.removeAll(ToolSettingsService.getInstance().getDisabledTools());
        ToolProfileSnapshot snapshot = ToolProfileSnapshot.of(1L,
            Set.of(DefaultToolProfileFactory.createDefault(allowed)));
        return new ProfileToolPolicy(ProfileResolver.resolve("default", true, snapshot), catalog); //$NON-NLS-1$
    }
}
