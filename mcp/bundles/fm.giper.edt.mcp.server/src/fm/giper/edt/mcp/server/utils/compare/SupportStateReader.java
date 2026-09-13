/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server.utils.compare;

import com._1c.g5.v8.dt.compare.model.ComparisonNode;

/**
 * Reads the three-way SUPPORT state of a compared metadata object out of the comparison tree.
 *
 * <p><b>EDT 2025.2 degradation.</b> The support-settings comparison node types
 * ({@code SupportSettingsComparisonNode}, {@code UserSupportModeComparisonNode},
 * {@code ParentSupportModeComparisonNode}) are absent from the 2025.2 platform bundles, so this
 * reader cannot inspect vendor-support state there. {@link #read(ComparisonNode)} therefore
 * always returns an empty {@link SupportState} rather than {@code null}: callers still get a
 * well-formed object, but every field is blank and {@link SupportState#isEmpty()} is {@code true}.
 * On platforms where those node types exist again, restore the child-node walk from upstream.</p>
 *
 * <p>Every accessor here touches comparison-tree nodes, so the caller MUST already be inside the
 * comparison's own read boundary ({@code ComparisonEngine.read(...)}); this class opens none of its
 * own.</p>
 */
public final class SupportStateReader
{
    private SupportStateReader()
    {
        // Utility class
    }

    /**
     * The three-way support state of one compared object. A side whose object does not exist (or
     * whose value the platform left unset) renders as an empty string - never as a guess.
     */
    public static final class SupportState
    {
        /** Name of the parent (vendor) configuration, or {@code ""} when the platform left it unset. */
        public final String parentConfigurationName;
        /** {@code UserSupportMode} on the main side, or {@code ""}. */
        public final String mainUserMode;
        /** {@code UserSupportMode} on the other side, or {@code ""}. */
        public final String otherUserMode;
        /** {@code UserSupportMode} on the common-ancestor side, or {@code ""}. */
        public final String ancestorUserMode;
        /** {@code ParentSupportMode} on the main side, or {@code ""}. */
        public final String mainParentMode;
        /** {@code ParentSupportMode} on the other side, or {@code ""}. */
        public final String otherParentMode;
        /** {@code ParentSupportMode} on the common-ancestor side, or {@code ""}. */
        public final String ancestorParentMode;

        SupportState(String parentConfigurationName, String mainUserMode, String otherUserMode,
            String ancestorUserMode, String mainParentMode, String otherParentMode,
            String ancestorParentMode)
        {
            this.parentConfigurationName = parentConfigurationName;
            this.mainUserMode = mainUserMode;
            this.otherUserMode = otherUserMode;
            this.ancestorUserMode = ancestorUserMode;
            this.mainParentMode = mainParentMode;
            this.otherParentMode = otherParentMode;
            this.ancestorParentMode = ancestorParentMode;
        }

        /** @return {@code true} when at least one user-support-mode value was read. */
        public boolean hasUserMode()
        {
            return !mainUserMode.isEmpty() || !otherUserMode.isEmpty() || !ancestorUserMode.isEmpty();
        }

        /** @return {@code true} when at least one parent-support-mode value was read. */
        public boolean hasParentMode()
        {
            return !mainParentMode.isEmpty() || !otherParentMode.isEmpty()
                || !ancestorParentMode.isEmpty();
        }

        /** @return {@code true} when the node carried a support-settings child but no readable value. */
        public boolean isEmpty()
        {
            return !hasUserMode() && !hasParentMode() && parentConfigurationName.isEmpty();
        }
    }

    private static final SupportState DEGRADED_EMPTY = new SupportState(
        "", "", "", "", "", "", ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$

    /**
     * Reads the support state carried by {@code node}. On EDT 2025.2 this always returns a
     * degraded empty state because the platform comparison node types are unavailable.
     *
     * @param node the comparison node to inspect; may be {@code null}
     * @return an empty {@link SupportState} on 2025.2; never {@code null}
     */
    public static SupportState read(ComparisonNode node)
    {
        if (node == null)
        {
            return DEGRADED_EMPTY;
        }
        return DEGRADED_EMPTY;
    }
}
