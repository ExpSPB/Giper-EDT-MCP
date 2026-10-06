/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server.tools.rename;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import org.eclipse.core.resources.IResourceStatus;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.ILogListener;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.osgi.framework.Bundle;

/**
 * Captures synchronous core resource failures swallowed during one SDK perform.
 * No model access, waiting, retries, log scans or suppression. Does not certify
 * asynchronous export outcomes or successful disk coherence.
 */
final class RenameSdkFailureObserver implements MetadataRenameService.SdkFailureScope, ILogListener
{
    private static final String CORE_PLUGIN = "com._1c.g5.v8.dt.core"; //$NON-NLS-1$
    private final ILog log;
    private final Thread owner = Thread.currentThread();
    private boolean closed;
    private String failure;

    private RenameSdkFailureObserver(ILog log)
    {
        this.log = log;
    }

    static RenameSdkFailureObserver openLive()
    {
        Bundle bundle = Platform.getBundle(CORE_PLUGIN);
        if (bundle == null || bundle.getState() != Bundle.ACTIVE)
        {
            return null;
        }
        ILog log = Platform.getLog(bundle);
        return log == null || log.getBundle() != bundle ? null : open(log);
    }

    /** Public ILog seam; production always resolves and checks the live core bundle above. */
    static RenameSdkFailureObserver open(ILog log)
    {
        RenameSdkFailureObserver observer = new RenameSdkFailureObserver(log);
        try
        {
            log.addLogListener(observer);
            return observer;
        }
        catch (RuntimeException | Error registrationFailure)
        {
            try
            {
                observer.close();
            }
            catch (RuntimeException | Error removalFailure)
            {
                if (registrationFailure != removalFailure)
                {
                    registrationFailure.addSuppressed(removalFailure);
                }
            }
            throw registrationFailure;
        }
    }

    @Override
    public void logging(IStatus status, String plugin)
    {
        if (closed || Thread.currentThread() != owner || !CORE_PLUGIN.equals(plugin) || failure != null)
        {
            return;
        }
        try
        {
            if (status != null && status.matches(IStatus.ERROR) && containsResourceFailure(status))
            {
                failure = "EDT reported a resource synchronization failure during this refactoring. " //$NON-NLS-1$
                    + "Model or file changes may be partial. Inspect the target, project diagnostics " //$NON-NLS-1$
                    + "and project files before retrying."; //$NON-NLS-1$
            }
        }
        catch (RuntimeException | LinkageError | AssertionError probeFailure)
        {
            // SafeRunner otherwise absorbs listener failures; never permit false success.
            failure = "Could not observe EDT resource synchronization reliably. " //$NON-NLS-1$
                + "Model or file changes may be partial. Inspect the target and project files before retrying."; //$NON-NLS-1$
        }
    }

    private static boolean containsResourceFailure(IStatus root)
    {
        ArrayDeque<Object> pending = new ArrayDeque<>();
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        pending.add(root);
        for (int inspected = 0; !pending.isEmpty(); inspected++)
        {
            if (inspected >= 64)
            {
                throw new IllegalStateException("SDK failure evidence exceeded its observation bound"); //$NON-NLS-1$
            }
            Object current = pending.removeFirst();
            if (!visited.add(current))
            {
                continue;
            }
            if (current instanceof CoreException)
            {
                return true;
            }
            if (current instanceof IStatus status)
            {
                if (status instanceof IResourceStatus && status.matches(IStatus.ERROR))
                {
                    return true;
                }
                Throwable exception = status.getException();
                if (exception != null)
                {
                    pending.add(exception);
                }
                IStatus[] children = status.getChildren();
                if (children.length > 64)
                {
                    throw new IllegalStateException("SDK failure children exceeded their observation bound"); //$NON-NLS-1$
                }
                for (IStatus child : children)
                {
                    if (child != null)
                    {
                        pending.add(child);
                    }
                }
            }
            else if (current instanceof Throwable exception)
            {
                Throwable cause = exception.getCause();
                if (cause != null)
                {
                    pending.add(cause);
                }
            }
        }
        return false;
    }

    @Override
    public String failure()
    {
        return failure;
    }

    @Override
    public void close()
    {
        if (!closed)
        {
            closed = true;
            log.removeLogListener(this);
        }
    }
}
