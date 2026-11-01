/**
 * MCP Server for EDT
 * Copyright (C) 2026 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */
package fm.giper.edt.mcp.server.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.preferences.InstanceScope;

import com.e1c.g5.dt.applications.IApplication;
import com.e1c.g5.dt.applications.IApplicationManager;
import fm.giper.edt.mcp.server.Activator;
import fm.giper.edt.mcp.server.protocol.ToolResult;

/**
 * Preserves explicit defaults across EDT launcher reloads, which publish DELETED for all infobase
 * applications, even on duplicate refusal. Raw IDs are observations only: restoration always uses
 * a newly resolved application and the native setter, keeping its cache and listeners consistent.
 */
public final class LauncherDefaultPreservation
{
    interface Environment
    {
        IProject[] projects();
        String rawId(IProject project);
        IApplicationManager manager();
    }

    private static final Environment EDT = new Environment()
    {
        @Override
        public IProject[] projects()
        {
            return ResourcesPlugin.getWorkspace().getRoot().getProjects();
        }

        @Override
        public String rawId(IProject project)
        {
            return InstanceScope.INSTANCE.getNode("com.e1c.g5.dt.applications") //$NON-NLS-1$
                .node("applicationDefaults").node("applications").get(project.getName(), null); //$NON-NLS-1$ //$NON-NLS-2$
        }

        @Override
        public IApplicationManager manager()
        {
            return Activator.getDefault().getApplicationManager();
        }
    };

    private final Environment environment;
    private final Map<IProject, String> candidates = new LinkedHashMap<>();
    private IApplicationManager manager;
    private boolean lastLoadVerified;
    private String operation = "Launcher reload may have changed application settings."; //$NON-NLS-1$

    LauncherDefaultPreservation(Environment environment)
    {
        this.environment = environment == null ? EDT : environment;
    }

    /** Called under the existing coordinator, before entering native reload. */
    void beforeReload()
    {
        Map<IProject, String> observed = new LinkedHashMap<>();
        for (IProject project : Objects.requireNonNull(environment.projects()))
        {
            String id = environment.rawId(Objects.requireNonNull(project));
            if (id != null)
            {
                if (id.isBlank())
                {
                    throw new IllegalStateException("An explicit application default is invalid; launcher reload was not started."); //$NON-NLS-1$
                }
                observed.put(project, id);
            }
        }
        if (!observed.isEmpty() || !candidates.isEmpty())
        {
            manager = Objects.requireNonNull(environment.manager(),
                "Application defaults cannot be preserved because IApplicationManager is unavailable."); //$NON-NLS-1$
        }
        // A visible new choice wins; a later native null must not erase the earlier snapshot.
        candidates.putAll(observed);
        lastLoadVerified = false;
    }

    void loadVerified()
    {
        lastLoadVerified = true;
    }

    void operation(String message)
    {
        operation = message;
    }

    void recover(List<CredentialWriteControl> parents, long timeoutMs)
    {
        if (candidates.isEmpty())
        {
            return;
        }
        // One deadline covers dispatch, lock acquisition and every project, not one per default.
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Math.max(1, timeoutMs));
        var complete = new AtomicBoolean();
        var admission = new CredentialWriteControl();
        BoundedJob.Result result;
        try (admission)
        {
            result = BoundedJob.runWithCancellation("Preserve explicit EDT application defaults", //$NON-NLS-1$
                timeoutMs, monitor -> {
                    try (var coordination = InfobaseMutationCoordinator.acquireRecovery())
                    {
                        for (var candidate : candidates.entrySet())
                        {
                            requireAdmission(monitor, deadline, admission, parents);
                            restore(candidate.getKey(), candidate.getValue(), monitor, deadline, admission, parents);
                        }
                        complete.set(true);
                    }
                }, admission::close);
            admission.close();
            if (admission.writeInProgress())
            {
                throw new Failure("PENDING", operation, result.getFailure()); //$NON-NLS-1$
            }
            if (!result.isSuccess() || !complete.get())
            {
                throw new Failure("UNVERIFIED", operation, result.getFailure()); //$NON-NLS-1$
            }
        }
    }

    private void restore(IProject project, String id, IProgressMonitor monitor, long deadline,
            CredentialWriteControl admission, List<CredentialWriteControl> parents) throws Exception
    {
        if (environment.rawId(project) != null)
        {
            return;
        }
        var found = manager.getApplication(project, id);
        requireAdmission(monitor, deadline, admission, parents);
        if (environment.rawId(project) != null)
        {
            return;
        }
        if (found.isEmpty())
        {
            if (lastLoadVerified)
            {
                return; // The selected ID really disappeared; never recreate an application.
            }
            throw new IllegalStateException("Application absence could not be verified after launcher reload failure."); //$NON-NLS-1$
        }
        IApplication live = found.get();
        requireSameApplication(project, id, live);
        var permits = new ArrayList<CredentialWriteControl.Permit>();
        try
        {
            for (CredentialWriteControl control : parents)
            {
                takePermit(control, permits);
            }
            takePermit(admission, permits);
            requireAdmission(monitor, deadline, admission, parents);
            if (environment.rawId(project) != null)
            {
                return;
            }
            // GUI writes have no public CAS. An explicit GUI null while SDK already cleared the
            // preference is indistinguishable; observed non-null choices are never overwritten.
            manager.setDefaultApplication(project, live);
        }
        finally
        {
            for (int i = permits.size() - 1; i >= 0; i--)
            {
                permits.get(i).close();
            }
        }
        requireAdmission(monitor, deadline, admission, parents);
        String actual = environment.rawId(project);
        if (actual != null && !id.equals(actual))
        {
            return; // A newer visible choice also wins after our setter returns.
        }
        if (!id.equals(actual))
        {
            throw new IllegalStateException("The restored explicit application default could not be verified."); //$NON-NLS-1$
        }
        var current = manager.getApplication(project, id);
        requireAdmission(monitor, deadline, admission, parents);
        if (current.isEmpty())
        {
            throw new IllegalStateException("The restored application no longer exists."); //$NON-NLS-1$
        }
        requireSameApplication(project, id, current.get());
        if (environment.rawId(project) == null)
        {
            throw new IllegalStateException("The restored explicit application default was cleared during verification."); //$NON-NLS-1$
        }
    }

    private static void requireSameApplication(IProject project, String id, IApplication app)
    {
        if (!id.equals(app.getId()) || !project.equals(app.getProject()))
        {
            throw new IllegalStateException("EDT resolved a different application while preserving defaults."); //$NON-NLS-1$
        }
    }

    private static void requireAdmission(IProgressMonitor monitor, long deadline,
            CredentialWriteControl admission, List<CredentialWriteControl> parents)
    {
        if (monitor.isCanceled() || System.nanoTime() >= deadline || !admission.getAsBoolean()
            || parents.stream().anyMatch(control -> !control.getAsBoolean()))
        {
            throw new IllegalStateException("Application-default recovery was cancelled before another write."); //$NON-NLS-1$
        }
    }

    private static void takePermit(CredentialWriteControl control,
            List<CredentialWriteControl.Permit> permits)
    {
        var permit = control.tryBeginWrite();
        if (permit == null)
        {
            throw new IllegalStateException("Application-default recovery write admission is closed."); //$NON-NLS-1$
        }
        permits.add(permit);
    }

    /** A launcher operation may already have committed when its defaults cannot be verified. */
    public static final class Failure extends IllegalStateException
    {
        private static final long serialVersionUID = 1L;
        private final String outcome;

        Failure(String outcome, String operation, Throwable cause)
        {
            super(operation + " Explicit application-default recovery is " + outcome //$NON-NLS-1$
                + "; the launcher operation may have completed. " //$NON-NLS-1$
                + ("PENDING".equals(outcome) ? "An admitted native default write may still complete. " : "") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                + "Inspect the launcher and explicit defaults before retrying.", cause); //$NON-NLS-1$
            this.outcome = outcome;
        }

        public String outcome()
        {
            return outcome;
        }
    }

    /** Preserve recovery evidence even when try-with-resources suppressed it behind another error. */
    public static Failure failure(Throwable error)
    {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        var pending = new ArrayList<Throwable>();
        if (error != null)
        {
            pending.add(error);
        }
        Failure result = null;
        for (int i = 0; i < pending.size(); i++)
        {
            Throwable current = pending.get(i);
            if (!seen.add(current))
            {
                continue;
            }
            if (current instanceof Failure found)
            {
                result = found;
                if ("PENDING".equals(found.outcome())) //$NON-NLS-1$
                {
                    return found;
                }
            }
            if (current.getCause() != null)
            {
                pending.add(current.getCause());
            }
            Collections.addAll(pending, current.getSuppressed());
        }
        return result;
    }

    public static ToolResult decorate(ToolResult result, Throwable error)
    {
        Failure failure = failure(error);
        return failure == null ? result : result.put("defaultRestoration", failure.outcome()) //$NON-NLS-1$
            .put("operationMayHaveCompleted", true).put("defaultRestorationMessage", failure.getMessage()); //$NON-NLS-1$ //$NON-NLS-2$
    }
}
