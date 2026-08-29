/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.profiles;

import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

import com.ditrix.edt.mcp.server.Activator;
import com.ditrix.edt.mcp.server.preferences.ToolSettingsService;
import com.ditrix.edt.mcp.server.tools.IMcpTool;
import com.ditrix.edt.mcp.server.tools.McpToolRegistry;

/**
 * One-shot creation of the profile document from the legacy disabled-set.
 * Must run only after the full tool catalogue is registered.
 */
public final class ToolProfileMigration
{
    private ToolProfileMigration()
    {
        // Utility class
    }

    /**
     * Migrates the live activator repository when the catalogue is already registered.
     */
    public static void migrateIfNeeded()
    {
        Activator activator = Activator.getDefault();
        if (activator == null || activator.getToolProfileRepository() == null)
        {
            return;
        }
        Set<String> registered = registeredNames();
        migrateIfNeeded(activator.getToolProfileRepository(), registered,
            ToolSettingsService.getInstance());
    }

    /**
     * Testable entry: creates {@code default} from {@code registered − disabled} when no
     * document exists yet. A valid document makes this a no-op.
     */
    public static ReplaceResult migrateIfNeeded(ToolProfileRepository repository,
        Collection<String> registeredToolNames, ToolSettingsService settings)
    {
        if (repository == null || settings == null)
        {
            return ReplaceResult.rejected(null, "Repository and settings are required"); //$NON-NLS-1$
        }
        if (repository.getDocumentState() == ProfileDocumentState.VALID)
        {
            return ReplaceResult.noOp(repository.getSnapshot());
        }
        if (repository.getDocumentState() != ProfileDocumentState.ABSENT)
        {
            return ReplaceResult.rejected(repository.getSnapshot(),
                "Refusing to migrate over a malformed or unsupported profile document"); //$NON-NLS-1$
        }
        if (registeredToolNames == null || registeredToolNames.isEmpty())
        {
            return ReplaceResult.rejected(repository.getSnapshot(),
                "Migration cannot run before the tool catalogue is registered"); //$NON-NLS-1$
        }
        Set<String> allowed = new TreeSet<>(registeredToolNames);
        allowed.removeAll(settings.getDisabledTools());
        ToolProfileSnapshot snapshot = ToolProfileSnapshot.of(0L,
            Set.of(DefaultToolProfileFactory.createDefault(allowed)));
        return repository.replaceAll(repository.getSnapshot().getDocumentRevision(), snapshot);
    }

    public static Set<String> allowlistFromDisabled(Collection<String> registeredToolNames,
        Collection<String> disabledTools)
    {
        Set<String> allowed = new TreeSet<>();
        if (registeredToolNames != null)
        {
            allowed.addAll(registeredToolNames);
        }
        if (disabledTools != null)
        {
            allowed.removeAll(disabledTools);
        }
        return Set.copyOf(allowed);
    }

    private static Set<String> registeredNames()
    {
        Set<String> names = new TreeSet<>();
        for (IMcpTool tool : McpToolRegistry.getInstance().getAllTools())
        {
            if (tool != null && tool.getName() != null)
            {
                names.add(tool.getName());
            }
        }
        return names;
    }
}
