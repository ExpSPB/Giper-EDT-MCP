/**
 * MCP Server for EDT
 * Licensed under AGPL-3.0-or-later
 */
package fm.giper.edt.mcp.server.tools.reference;

import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.swt.widgets.Display;

import com._1c.g5.v8.bm.core.IBmObject;
import com._1c.g5.v8.bm.core.IBmTransaction;
import com._1c.g5.v8.bm.integration.IBmModel;
import com._1c.g5.v8.dt.mcore.FieldSource;
import com._1c.g5.v8.dt.metadata.mdclass.Configuration;
import com._1c.g5.v8.dt.metadata.mdclass.MdObject;

import fm.giper.edt.mcp.server.Activator;
import fm.giper.edt.mcp.server.utils.BmModelResolver;
import fm.giper.edt.mcp.server.utils.BmObjectOwnership;
import fm.giper.edt.mcp.server.utils.BmTransactions;
import fm.giper.edt.mcp.server.utils.MetadataTypeUtils;
import fm.giper.edt.mcp.server.utils.ProjectContext;
import fm.giper.edt.mcp.server.utils.ProjectStateChecker;
import fm.giper.edt.mcp.server.utils.ReadToolExecution;
import fm.giper.edt.mcp.server.utils.Refusals;

/** The shared project, model and argument boundary for graph and impact analysis. */
public final class MetadataAnalysis
{
    public static final String OBJECT_FQN = "objectFqn"; //$NON-NLS-1$
    public static final String FQN_DESCRIPTION = "Top-level object FQN, e.g. Catalog.Products. " //$NON-NLS-1$
        + "The type token may be English or Russian. Use the programmatic name, not the synonym."; //$NON-NLS-1$

    private MetadataAnalysis()
    {
    }

    @FunctionalInterface
    interface ModelRead<T>
    {
        T read(IProject project, Configuration config, IBmModel model, IBmTransaction tx,
            IProgressMonitor monitor, MdObject target);
    }

    static <T> T read(String task, String projectName, String objectFqn, ReferenceSearchBound bound,
        ModelRead<T> analysis)
    {
        String address = MetadataTypeUtils.normalizeFqn(objectFqn.trim());
        String[] parts = address.split("\\.", -1); //$NON-NLS-1$
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank())
        {
            throw Refusals.argument("Invalid objectFqn '" + objectFqn //$NON-NLS-1$
                + "'. Use a top-level object such as Catalog.Products."); //$NON-NLS-1$
        }
        ProjectContext context = ProjectContext.of(projectName);
        if (!context.exists() || !context.project().isOpen())
        {
            throw Refusals.argument(ProjectContext.notFoundMessage(projectName));
        }
        Thread caller = Thread.currentThread();
        bound.cancelWhen(caller::isInterrupted);
        var server = Activator.getDefault().getMcpServer();
        var call = server == null ? null : server.getExecutingToolCall();
        if (call != null)
        {
            bound.cancelWhen(call::isResponseDelivered);
        }
        if (Display.getCurrent() == null)
        {
            ProjectStateChecker.awaitSearchScopeReady(projectName, 10_000L);
        }
        if (bound.shouldStop(0))
        {
            throw Refusals.state("Analysis stopped before the model of '" + projectName + "' could be read."); //$NON-NLS-1$ //$NON-NLS-2$
        }

        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<RuntimeException> failure = new AtomicReference<>();
        ReadToolExecution.dispatch(() -> {
            try
            {
                IProject project = context.project();
                // Refresh the selected project's readiness after settling and dispatching. This
                // also guards callers that already ran on the UI thread and could not wait.
                String notReady = ProjectStateChecker.checkReadyOrError(project);
                if (notReady != null)
                {
                    throw Refusals.state(notReady);
                }
                IBmModel model = BmModelResolver.resolve(project).getModel();
                if (model == null)
                {
                    throw Refusals.state("BM model unavailable for project '" + projectName //$NON-NLS-1$
                        + "'. Wait for list_projects to report ready and retry."); //$NON-NLS-1$
                }
                result.set(BmTransactions.read(model, task, (tx, monitor) -> {
                    bound.cancelWhen(monitor::isCanceled);
                    if (bound.shouldStop(0))
                    {
                        throw Refusals.state("Analysis stopped before resolving '" + objectFqn + "'."); //$NON-NLS-1$ //$NON-NLS-2$
                    }
                    var resolved = context.resolveMetadataRootInTransaction();
                    Configuration config = resolved.configuration();
                    if (!resolved.ok() || config == null)
                    {
                        throw Refusals.state("Configuration unavailable for project '" + projectName //$NON-NLS-1$
                            + "'. Check list_projects and retry when the configuration is ready."); //$NON-NLS-1$
                    }
                    MdObject root = MetadataTypeUtils.findObject(config, parts[0], parts[1]);
                    if (!(root instanceof IBmObject bm) || !BmObjectOwnership.isPrimary(tx, bm))
                    {
                        throw Refusals.argument("Object not found in project '" + projectName + "': " //$NON-NLS-1$ //$NON-NLS-2$
                            + objectFqn + ". Use the programmatic FQN from get_metadata_objects."); //$NON-NLS-1$
                    }
                    return analysis.read(project, config, model, tx, monitor, root);
                }));
            }
            catch (RuntimeException e)
            {
                failure.set(e);
            }
        });
        if (failure.get() != null)
        {
            throw failure.get();
        }
        return result.get();
    }

    /** Generated field views mirror authored attributes and contain synthetic Ref/Parent types. */
    static boolean isAuthoredSource(IBmObject source)
    {
        for (EObject node = source; node.eContainer() != null; node = node.eContainer())
        {
            var containment = node.eContainmentFeature();
            if (containment != null && (containment.isDerived() || containment.isTransient()
                || "fields".equals(containment.getName()) && node.eContainer() instanceof FieldSource)) //$NON-NLS-1$
            {
                return false;
            }
            if (node instanceof IBmObject bm && bm.bmIsTop())
            {
                break;
            }
        }
        return true;
    }
}
