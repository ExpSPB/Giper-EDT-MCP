/**
 * MCP Server for EDT
 * Adapted from AI-EDT, Copyright (C) 2026 Desko77 (https://github.com/Desko77)
 * Licensed under AGPL-3.0-or-later
 */
package fm.giper.edt.mcp.server.tools.reference;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;

import com._1c.g5.v8.bm.core.IBmCrossReference;
import com._1c.g5.v8.bm.core.IBmEngine;
import com._1c.g5.v8.bm.core.IBmObject;
import com._1c.g5.v8.bm.core.IBmTransaction;
import com._1c.g5.v8.dt.bsl.model.Module;
import com._1c.g5.v8.dt.mcore.TypeItem;
import com._1c.g5.v8.dt.metadata.mdclass.Configuration;
import com._1c.g5.v8.dt.metadata.mdclass.MdObject;
import com._1c.g5.v8.dt.metadata.mdclass.util.MdClassUtil;
import com._1c.g5.v8.dt.metadata.mdtype.MdType;
import com._1c.g5.v8.dt.metadata.mdtype.MdTypeSet;

import fm.giper.edt.mcp.server.tools.reference.DependencyGraph.Connection;
import fm.giper.edt.mcp.server.tools.reference.DependencyGraph.Link;
import fm.giper.edt.mcp.server.tools.reference.DependencyGraph.Options;
import fm.giper.edt.mcp.server.tools.reference.DependencyGraph.Result;
import fm.giper.edt.mcp.server.utils.BmObjectOwnership;
import fm.giper.edt.mcp.server.utils.MetadataTypeUtils;

/** Supplies project-local EMF and BM references to the bounded graph walk. */
public final class MetadataDependencyGraph
{
    private static final String MODEL_INCOMPLETE = "modelIncomplete"; //$NON-NLS-1$

    private final Configuration configuration;
    private final IBmTransaction tx;
    private final IBmEngine engine;
    private final ReferenceSearchBound budget;
    private final boolean incoming;
    private final boolean outgoing;
    private final Map<String, IBmObject> objects = new HashMap<>();
    private final Map<Long, IBmObject> typeOwners = new HashMap<>();
    private final Set<String> reasons = new LinkedHashSet<>();
    private boolean typeOwnersIndexed;

    private MetadataDependencyGraph(Configuration configuration, IBmTransaction tx, IBmEngine engine,
        ReferenceSearchBound budget, Options options)
    {
        this.configuration = configuration;
        this.tx = tx;
        this.engine = engine;
        this.budget = budget;
        incoming = !"out".equals(options.direction()); //$NON-NLS-1$
        outgoing = !"in".equals(options.direction()); //$NON-NLS-1$
    }

    public static Result read(String projectName, String objectFqn, Options options)
    {
        ReferenceSearchBound budget = ReferenceSearchBound.of(Integer.MAX_VALUE,
            ReferenceSearchBound.TIME_BUDGET_MS, System::nanoTime);
        return MetadataAnalysis.read("Read metadata dependency graph", projectName, objectFqn, budget, //$NON-NLS-1$
            (project, config, model, tx, monitor, root) -> {
                var graph = new MetadataDependencyGraph(config, tx, model.getEngine(), budget, options);
                IBmObject object = (IBmObject)root;
                String address = graph.address(object);
                return DependencyGraph.walk(address, options, graph::neighbors, graph::stopped, graph.reasons);
            });
    }

    private boolean stopped()
    {
        if (!budget.shouldStop(0))
        {
            return false;
        }
        reasons.add(budget.stopReason() == ReferenceSearchBound.StopReason.CANCELLED
            ? "cancelled" : "timeLimit"); //$NON-NLS-1$ //$NON-NLS-2$
        return true;
    }

    private String address(IBmObject object)
    {
        String fqn = MetadataTypeUtils.toCanonicalEnglishFqn(object.bmGetFqn());
        objects.putIfAbsent(fqn, object);
        return fqn;
    }

    private Collection<Link> neighbors(String fqn)
    {
        IBmObject root = objects.get(fqn);
        Set<Link> links = new LinkedHashSet<>();
        visit(root, links);
        var children = root.eAllContents();
        while (!stopped() && children.hasNext())
        {
            EObject child = children.next();
            if (child instanceof Module || child instanceof IBmObject bm && bm.bmIsTop()
                && metadataOwner(bm) != root)
            {
                children.prune();
            }
            else if (child instanceof IBmObject bm)
            {
                visit(bm, links);
            }
        }
        if (incoming)
        {
            for (IBmObject type : producedTypes((MdObject)root))
            {
                if (stopped())
                {
                    break;
                }
                collectIncoming(type, links);
            }
        }
        return links;
    }

    private void visit(IBmObject object, Set<Link> links)
    {
        if (incoming)
        {
            collectIncoming(object, links);
        }
        if (!outgoing)
        {
            return;
        }
        for (EReference feature : object.eClass().getEAllReferences())
        {
            if (stopped())
            {
                return;
            }
            if (!isGraphReference(feature))
            {
                continue;
            }
            Object value = object.eGet(feature);
            if (value instanceof Iterable<?> values)
            {
                for (Object target : values)
                {
                    if (stopped())
                    {
                        return;
                    }
                    addLink(object, target, feature, links);
                }
            }
            else
            {
                addLink(object, value, feature, links);
            }
        }
    }

    private void collectIncoming(IBmObject target, Set<Link> links)
    {
        if (stopped())
        {
            return;
        }
        for (IBmCrossReference reference : engine.getBackReferences(target))
        {
            if (stopped())
            {
                return;
            }
            if (reference.getFeature() instanceof EReference feature && isGraphReference(feature))
            {
                IBmObject source = reference.getObject();
                if (source == null)
                {
                    if (reference.getNamespace() == tx.getNamespace())
                    {
                        reasons.add(MODEL_INCOMPLETE);
                    }
                    continue;
                }
                addLink(source, target, feature, links);
            }
        }
    }

    private void addLink(IBmObject source, Object value, EReference feature, Set<Link> links)
    {
        if (!(value instanceof IBmObject target) || source instanceof TypeItem || !MetadataAnalysis.isAuthoredSource(source))
        {
            return;
        }
        IBmObject from = metadataOwner(source);
        if (from == null)
        {
            return;
        }
        if (target.eIsProxy())
        {
            reasons.add(MODEL_INCOMPLETE);
            return;
        }
        IBmObject to = metadataOwner(target);
        if (to != null)
        {
            links.add(new Link(new Connection(address(from), address(to), feature.getName()),
                source.bmGetId(), target.bmGetId()));
        }
    }

    private IBmObject metadataOwner(IBmObject object)
    {
        if (object.eIsProxy() || object instanceof Module || object.bmGetNamespace() != tx.getNamespace())
        {
            return null;
        }
        if (object instanceof TypeItem)
        {
            long typeId = object.bmGetId();
            IBmObject known = typeOwners.get(typeId);
            if (known == null && !typeOwnersIndexed)
            {
                indexTypeOwners();
                known = typeOwners.get(typeId);
            }
            return known;
        }
        IBmObject top = object.bmIsTop() ? object : object.bmGetTopObject();
        if (top instanceof Module || top instanceof Configuration || !BmObjectOwnership.isPrimary(tx, top))
        {
            return null;
        }
        String fqn = top.bmGetFqn();
        if (fqn == null)
        {
            return null;
        }
        int nameStart = fqn.indexOf('.') + 1;
        if (nameStart == 0)
        {
            return null;
        }
        int memberStart = fqn.indexOf('.', nameStart);
        if (memberStart < 0)
        {
            return top instanceof MdObject ? top : null;
        }
        // Some owned forms and other members are independently stored BM objects.
        // They still belong to the enclosing configuration object in this graph.
        MdObject owner = MetadataTypeUtils.findObject(configuration,
            fqn.substring(0, nameStart - 1), fqn.substring(nameStart, memberStart));
        return owner instanceof IBmObject bm && BmObjectOwnership.isPrimary(tx, bm) ? bm : null;
    }

    private static boolean isGraphReference(EReference feature)
    {
        return !feature.isContainment() && !feature.isContainer() && !feature.isDerived() && !feature.isTransient();
    }

    private void indexTypeOwners()
    {
        if (typeOwnersIndexed)
        {
            return;
        }
        typeOwnersIndexed = true;
        for (String kind : MetadataTypeUtils.getAllEnglishSingularNames())
        {
            if (stopped())
            {
                return;
            }
            List<? extends MdObject> objectsOfKind = MetadataTypeUtils.getObjects(configuration, kind);
            if (objectsOfKind == null)
            {
                continue;
            }
            for (MdObject metadata : objectsOfKind)
            {
                if (stopped())
                {
                    return;
                }
                if (metadata instanceof IBmObject owner && BmObjectOwnership.isPrimary(tx, owner))
                {
                    for (IBmObject type : producedTypes(metadata))
                    {
                        typeOwners.put(type.bmGetId(), owner);
                    }
                }
            }
        }
    }

    private List<IBmObject> producedTypes(MdObject metadata)
    {
        var types = MdClassUtil.getProducedTypes(metadata);
        List<IBmObject> result = new ArrayList<>();
        if (types != null)
        {
            for (EObject entry : types.eContents())
            {
                TypeItem type = entry instanceof MdType mdType ? mdType.getType()
                    : entry instanceof MdTypeSet typeSet ? typeSet.getTypeSet() : null;
                if (type instanceof IBmObject bm && !bm.eIsProxy())
                {
                    typeOwners.put(bm.bmGetId(), (IBmObject)metadata);
                    result.add(bm);
                }
            }
        }
        return result;
    }
}
