---
name: edt-mcp-project-code-research
description: Investigate 1C BSL behavior, dependencies, impact, and implementation differences through EDT-MCP. Read-only by default; not for plugin development.
---

# EDT-MCP code research

## Purpose and trigger

Use this skill to produce a bounded, evidence-backed explanation of 1C project
code, dependencies, impact, or implementation differences.

## Operating rule

Read and apply [the common operating rules](../COMMON.md) before this workflow.

## Task boundary

Keep the task read-only and tied to exact project, metadata FQN, module, method,
or symbol targets. Route a known bounded correction to
`edt-mcp-project-local-fix`; do not turn research into implementation without
authorization.

## Primary workflow

1. Resolve candidates with `get_metadata_objects`, `list_modules`, and
   `search_in_code`. Search is literal and not dialect-aware: for identifiers,
   use AST-backed definition/reference/hierarchy tools and do not infer absence
   from one Russian or English spelling.
2. Narrow with `get_module_structure`, then prefer `read_method_source` over
   `read_module_source` unless module-level context is required.
3. Follow only the relationships needed using `go_to_definition`,
   `find_references`, `get_method_call_hierarchy`, `get_outgoing_structures`,
   or `get_symbol_info`.
4. `get_method_call_hierarchy` scans only one project's `<project>/src`.
   Inspect each relevant base/extension project separately and name any project
   left unsearched. Treat `get_outgoing_structures` as a heuristic lower bound,
   even when its response is not marked partial.
5. State which projects and how many hops a relationship search covered.
   Inspect bounded source and relevant projects before drawing conclusions
   beyond that scope.
6. Start with counts, file lists, filters, or method/range reads; request full
   payloads or more pages only when needed. Preserve truncation and cursor
   evidence in the result.
7. Use `get_platform_documentation` when the conclusion depends on platform
   behavior rather than project code.

## Metadata dependencies and impact

Use a programmatic FQN for a top-level object in the selected project, for
example `Catalog.Products` or `Справочник.Products`.

- **Trace dependencies before moving or restructuring an object.** Call
  `dependency_graph` with `direction: "out"` for its dependencies, or
  `direction: "in"` for the objects that depend on it. Use `both` when the
  task calls for exploring connections in both directions. Start at depth 1
  for direct links and increase it when the task calls for transitive links.
  Each edge points from the referring object to its target. Report cycles
  within the returned graph and include `partialReasons` when `partial` is true.
- **Review the impact of a proposed change.** Call `impact_analysis` with
  `action: "modify"`, `"rename"`, or `"delete"`. Report direct uses in
  metadata and indexed BSL, including module and line where available. Use
  `skipBsl: true` for a metadata-only check. Keep the reported lower bound and
  reason when the search is incomplete. EDT 2026.2 may omit indexed BSL
  references in projects with spaces in their names. Follow the report with an
  incoming graph when you also need to trace indirect dependents.

Both tools cover the selected project. State which relevant extensions or other
projects were checked, and name those that need separate investigation.

## Authority rule

Do not write code, metadata, runtime data, or Git state. Request a new task
boundary before any implementation or runtime experiment.

## Stop rule

Stop when the exact target cannot be resolved, required project evidence is
unavailable, or the conclusion needs unauthorized runtime proof.

## Completion signal

Return exact targets, the evidenced call/data flow, relevant dependencies and
exceptions, impact classification, partial-result caveats, unknowns, and the
smallest useful next validation step.
