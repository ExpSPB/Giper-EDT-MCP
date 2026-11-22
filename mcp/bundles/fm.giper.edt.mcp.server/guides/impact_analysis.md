Review direct usages of one top-level metadata object before modifying, renaming, or deleting it. The query reads the named project and includes indexed BSL usages by default. Set `skipBsl: true` to search metadata only.

For example, pass `projectName`, `objectFqn: "Catalog.Products"`, and `action: "rename"`. The type token may also be `Справочник`. Use the programmatic object name.

The report identifies the scope, the planned action, and known sources and features. BSL rows include a module and a line when available. A completed search reports the exact count and a usage-based severity of LOW for zero, MEDIUM for 1–10, or HIGH above 10. The estimate applies to the stated static-search scope.

EDT 2026.2 can omit indexed BSL references when the workspace project name contains spaces. For these projects, an indexed BSL report gives the references found and marks the BSL scope incomplete. A metadata-only report can still give an exact metadata count.

`limit` caps the combined metadata and BSL results. The search budget is 45 seconds, and EDT may finish a native read already in progress. When the work stops early or available data is incomplete, the report gives a lower bound and the reason. Severity and recommendations require a complete search. Use `dependency_graph` for transitive dependencies.
