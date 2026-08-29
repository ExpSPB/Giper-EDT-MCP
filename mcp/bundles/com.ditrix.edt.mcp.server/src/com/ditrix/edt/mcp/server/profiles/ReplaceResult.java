/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.profiles;

/**
 * Outcome of {@link ToolProfileRepository#replaceAll(long, ToolProfileSnapshot)}.
 */
public final class ReplaceResult
{
    public enum Status
    {
        ACCEPTED,
        CONFLICT,
        NO_OP,
        REJECTED
    }

    private final Status status;
    private final ToolProfileSnapshot snapshot;
    private final ToolProfileChangeSet changeSet;
    private final String message;

    private ReplaceResult(Status status, ToolProfileSnapshot snapshot, ToolProfileChangeSet changeSet,
        String message)
    {
        this.status = status;
        this.snapshot = snapshot;
        this.changeSet = changeSet == null ? ToolProfileChangeSet.empty() : changeSet;
        this.message = message;
    }

    public static ReplaceResult accepted(ToolProfileSnapshot snapshot, ToolProfileChangeSet changeSet)
    {
        return new ReplaceResult(Status.ACCEPTED, snapshot, changeSet, null);
    }

    public static ReplaceResult conflict(ToolProfileSnapshot current)
    {
        return new ReplaceResult(Status.CONFLICT, current, ToolProfileChangeSet.empty(),
            "expectedRevision does not match the published documentRevision"); //$NON-NLS-1$
    }

    public static ReplaceResult noOp(ToolProfileSnapshot current)
    {
        return new ReplaceResult(Status.NO_OP, current, ToolProfileChangeSet.empty(), null);
    }

    public static ReplaceResult rejected(ToolProfileSnapshot current, String message)
    {
        return new ReplaceResult(Status.REJECTED, current, ToolProfileChangeSet.empty(), message);
    }

    public Status getStatus()
    {
        return status;
    }

    public boolean isAccepted()
    {
        return status == Status.ACCEPTED;
    }

    public ToolProfileSnapshot getSnapshot()
    {
        return snapshot;
    }

    public ToolProfileChangeSet getChangeSet()
    {
        return changeSet;
    }

    public String getMessage()
    {
        return message;
    }
}
