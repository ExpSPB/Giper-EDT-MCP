/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Modified by ExpSPB in 2026 (https://github.com/ExpSPB)
 * Licensed under AGPL-3.0-or-later
 */

package fm.giper.edt.mcp.server.tools.rename;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import com._1c.g5.v8.derived.IDerivedDataManager;
import com._1c.g5.v8.dt.core.platform.IBmModelManager;
import com._1c.g5.v8.dt.core.platform.IDerivedDataManagerProvider;
import com._1c.g5.v8.dt.core.platform.IDtProject;
import com._1c.g5.v8.dt.core.platform.IDtProjectManager;
import fm.giper.edt.mcp.server.Activator;
import fm.giper.edt.mcp.server.utils.BuildUtils;

/**
 * Checked post-preparation/inter-perform drain. This is a completion observation,
 * not an atomic exclusion guard. Never release/pump the UI thread or block DATA.
 * The same accumulated budget covers every project, pass and await invocation.
 * SDK/provider/event calls can exceed their nominal timeout; elapsed rechecks
 * refuse afterwards, while the adapter's existing BoundedJob bounds caller wait.
 */
final class RenameCompletionBarrier implements MetadataRenameService.RenameBarrier
{
    static final long WAIT_BUDGET_MS = 60_000L;

    @FunctionalInterface
    interface EventDrain
    {
        boolean drain();
    }

    private final Supplier<Collection<IDtProject>> projects;
    private final Function<IDtProject, IDerivedDataManager> managers;
    private final EventDrain events;
    private final LongSupplier clock;
    private long remainingNanos;

    RenameCompletionBarrier(Supplier<Collection<IDtProject>> projects,
        Function<IDtProject, IDerivedDataManager> managers, EventDrain events,
        LongSupplier clock, long budgetMs)
    {
        if (budgetMs <= 0 || budgetMs > WAIT_BUDGET_MS)
        {
            throw new IllegalArgumentException("Invalid rename wait budget"); //$NON-NLS-1$
        }
        this.projects = Objects.requireNonNull(projects);
        this.managers = Objects.requireNonNull(managers);
        this.events = Objects.requireNonNull(events);
        this.clock = Objects.requireNonNull(clock);
        remainingNanos = budgetMs * 1_000_000L;
    }

    static RenameCompletionBarrier live()
    {
        return new RenameCompletionBarrier(() -> {
            Activator plugin = Activator.getDefault();
            IDtProjectManager manager = plugin == null ? null : plugin.getDtProjectManager();
            return manager == null ? null : manager.getDtProjects();
        }, project -> {
            Activator plugin = Activator.getDefault();
            IDerivedDataManagerProvider provider = plugin == null ? null : plugin.getDerivedDataManagerProvider();
            return provider == null ? null : provider.get(project);
        }, () -> {
            Activator plugin = Activator.getDefault();
            IBmModelManager manager = plugin == null ? null : plugin.getBmModelManager();
            if (manager == null)
            {
                return false;
            }
            // Public SDK has no caller-timed overload. Exact EDT .5 uses an internal 60s
            // event deadline, which can still exceed our remaining budget.
            manager.waitAllEnqueuedEventsSent();
            return true;
        }, System::nanoTime, WAIT_BUDGET_MS);
    }

    @Override
    public String await() throws InterruptedException
    {
        long started = clock.getAsLong();
        long deadline = started + remainingNanos;
        try
        {
            while (true)
            {
                checkInterrupted();
                if (deadline - clock.getAsLong() <= 0)
                {
                    return timedOut();
                }
                boolean eventDrainComplete = events.drain();
                checkInterrupted();
                if (!eventDrainComplete)
                {
                    return unavailable();
                }
                if (deadline - clock.getAsLong() <= 0)
                {
                    return timedOut();
                }
                Map<String, Entry> observed = snapshot();
                if (observed == null)
                {
                    return unavailable();
                }
                for (Entry entry : observed.values())
                {
                    long remaining = deadline - clock.getAsLong();
                    if (remaining <= 0)
                    {
                        return timedOut();
                    }
                    // Positive, clipped nominal SDK timeout; zero is never passed.
                    long timeoutMs = Math.max(1L, remaining / 1_000_000L);
                    BuildUtils.DerivedDataCompletion completion =
                        BuildUtils.waitForDerivedDataCompletion(entry.manager, timeoutMs);
                    checkInterrupted();
                    if (deadline - clock.getAsLong() <= 0)
                    {
                        return timedOut();
                    }
                    if (completion != BuildUtils.DerivedDataCompletion.COMPLETE)
                    {
                        return "Project '" + entry.project.getName() //$NON-NLS-1$
                            + "' has not completed its pending work. Wait for the project to finish and retry."; //$NON-NLS-1$
                    }
                }
                // A drain after DD can enqueue more DD. Re-observe before declaring success.
                eventDrainComplete = events.drain();
                checkInterrupted();
                if (!eventDrainComplete)
                {
                    return unavailable();
                }
                if (deadline - clock.getAsLong() <= 0)
                {
                    return timedOut();
                }
                Map<String, Entry> refreshed = snapshot();
                if (refreshed == null)
                {
                    return unavailable();
                }
                boolean complete = observed.keySet().equals(refreshed.keySet());
                for (Map.Entry<String, Entry> row : refreshed.entrySet())
                {
                    Entry prior = observed.get(row.getKey());
                    Entry current = row.getValue();
                    complete &= prior != null && prior.project == current.project
                        && prior.manager == current.manager && current.manager.isAllComputed();
                }
                if (deadline - clock.getAsLong() <= 0)
                {
                    return timedOut();
                }
                checkInterrupted();
                if (complete)
                {
                    return null;
                }
                // Conditional next pass, not a fixed settling sleep or infinite event drain.
            }
        }
        finally
        {
            remainingNanos = Math.max(0L, remainingNanos - (clock.getAsLong() - started));
        }
    }

    private Map<String, Entry> snapshot()
    {
        Collection<IDtProject> current = projects.get();
        // A prepared EDT rename cannot have an empty registered project set.
        if (current == null || current.isEmpty())
        {
            return null;
        }
        Map<String, Entry> result = new LinkedHashMap<>();
        for (IDtProject project : current)
        {
            if (project == null || project.getId() == null || project.getId().isBlank())
            {
                return null;
            }
            IDerivedDataManager manager = managers.apply(project);
            if (manager == null || result.put(project.getId(), new Entry(project, manager)) != null)
            {
                return null;
            }
        }
        return result;
    }

    private static void checkInterrupted() throws InterruptedException
    {
        if (Thread.currentThread().isInterrupted())
        {
            throw new InterruptedException("Rename completion wait interrupted"); //$NON-NLS-1$
        }
    }

    private static String timedOut()
    {
        return "Pending project work did not finish within the rename wait budget. " //$NON-NLS-1$
            + "Wait for the workspace to finish its current work and retry."; //$NON-NLS-1$
    }

    private static String unavailable()
    {
        return "Could not verify that all open EDT projects finished their pending work. " //$NON-NLS-1$
            + "Use list_projects to check project readiness, then retry."; //$NON-NLS-1$
    }

    private static final class Entry
    {
        final IDtProject project;
        final IDerivedDataManager manager;

        Entry(IDtProject project, IDerivedDataManager manager)
        {
            this.project = project;
            this.manager = manager;
        }
    }
}
