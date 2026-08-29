/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.profiles;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Structural rules for a profile and for a whole snapshot.
 * Unknown tool names are allowed; {@code get_server_status} is not forced into the stored allowlist.
 */
public final class ToolProfileValidator
{
    private ToolProfileValidator()
    {
        // Utility class
    }

    public static ValidationResult validate(ToolProfile profile)
    {
        List<String> errors = new ArrayList<>();
        if (profile == null)
        {
            return ValidationResult.failed("Profile is required"); //$NON-NLS-1$
        }
        String id = profile.getId();
        if (id == null || id.isEmpty())
        {
            errors.add("Profile id is required"); //$NON-NLS-1$
        }
        else if (!ToolProfile.ID_PATTERN.matcher(id).matches())
        {
            errors.add("Profile id '" + id + "' must match [a-z0-9][a-z0-9_-]{0,63}"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        if (profile.getDisplayName() == null || profile.getDisplayName().isBlank())
        {
            errors.add("Profile '" + id + "' needs a non-empty displayName"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        if (profile.getRevision() < 1L)
        {
            errors.add("Profile '" + id + "' revision must be >= 1"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        if (profile.isDefault() && !profile.isEnabled())
        {
            errors.add("Profile 'default' cannot be disabled"); //$NON-NLS-1$
        }
        return errors.isEmpty() ? ValidationResult.ok() : ValidationResult.failed(errors);
    }

    public static ValidationResult validate(ToolProfileSnapshot snapshot)
    {
        if (snapshot == null)
        {
            return ValidationResult.failed("Snapshot is required"); //$NON-NLS-1$
        }
        if (snapshot.getSchemaVersion() < 1)
        {
            return ValidationResult.failed("schemaVersion must be >= 1"); //$NON-NLS-1$
        }
        if (snapshot.getDocumentRevision() < 0L)
        {
            return ValidationResult.failed("documentRevision must be >= 0"); //$NON-NLS-1$
        }
        if (snapshot.getDefault() == null)
        {
            return ValidationResult.failed("Snapshot must contain the reserved 'default' profile"); //$NON-NLS-1$
        }
        List<String> errors = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ToolProfile profile : snapshot.asList())
        {
            if (!seen.add(profile.getId()))
            {
                errors.add("Duplicate profile id '" + profile.getId() + "'"); //$NON-NLS-1$ //$NON-NLS-2$
            }
            ValidationResult one = validate(profile);
            if (!one.isValid())
            {
                errors.addAll(one.getErrors());
            }
        }
        return errors.isEmpty() ? ValidationResult.ok() : ValidationResult.failed(errors);
    }

    public static final class ValidationResult
    {
        private final List<String> errors;

        private ValidationResult(List<String> errors)
        {
            this.errors = List.copyOf(errors);
        }

        public static ValidationResult ok()
        {
            return new ValidationResult(List.of());
        }

        public static ValidationResult failed(String error)
        {
            return new ValidationResult(List.of(error));
        }

        public static ValidationResult failed(List<String> errors)
        {
            return new ValidationResult(errors);
        }

        public boolean isValid()
        {
            return errors.isEmpty();
        }

        public List<String> getErrors()
        {
            return errors;
        }

        public String firstError()
        {
            return errors.isEmpty() ? null : errors.get(0);
        }
    }
}
