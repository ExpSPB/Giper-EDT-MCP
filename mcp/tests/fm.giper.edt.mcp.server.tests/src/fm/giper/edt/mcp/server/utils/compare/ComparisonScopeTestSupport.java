/**
 * MCP Server for EDT - Tests
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server.utils.compare;

import java.lang.reflect.Method;
import java.util.List;

import com._1c.g5.v8.dt.compare.core.ComparisonScope;
import com._1c.g5.v8.dt.compare.model.ComparisonSide;

/**
 * Test helper for {@link ComparisonScope#extendScope} across EDT versions.
 * <p>
 * On the 2025.2 floor ({@code compare.core} 22.1.0) the platform API takes only the symlink and
 * side; a reason string exists only in newer EDT builds and in test prose. The three-argument form
 * keeps call sites readable while compiling against the older signature.
 */
public final class ComparisonScopeTestSupport
{
    private ComparisonScopeTestSupport()
    {
        // Utility class
    }

    public static void extendScope(ComparisonScope scope, String symlink, ComparisonSide side)
    {
        scope.extendScope(symlink, side);
    }

    public static void extendScope(ComparisonScope scope, String symlink, String ignoredReason,
        ComparisonSide side)
    {
        scope.extendScope(symlink, side);
    }

    /**
     * @return {@code true} when {@link ComparisonScope#getExtendedScope} is map-shaped and carries
     *     per-name reasons; on the 2025.2 floor it is a {@code List<String>} of names only
     */
    public static boolean extendedScopeCarriesReasons()
    {
        try
        {
            Method method = ComparisonScope.class.getMethod("getExtendedScope", ComparisonSide.class); //$NON-NLS-1$
            return !List.class.isAssignableFrom(method.getReturnType());
        }
        catch (ReflectiveOperationException e)
        {
            return false;
        }
    }
}
