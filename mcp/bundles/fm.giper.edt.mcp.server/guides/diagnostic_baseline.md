Save the current EDT validation markers before editing a project. After EDT has validated the
edits, call `get_project_errors` with `sinceBaseline: true` and the same `projectName`.
The comparison lists new diagnostics and counts the saved diagnostics that have disappeared.

## Parameters and results

- `operation: "save"` replaces the project's snapshot after a complete scan. Pass `objectFqns`
  to scope the snapshot to exact model addresses. Address resolution follows `get_project_errors`,
  including owner-level diagnostics for members. Omit the array or pass `[]` for all project
  diagnostics. The response contains `savedAt`, `scope` and `diagnosticsTotal`.
- `operation: "clear"` removes the project snapshot. Omit `objectFqns`. The response contains
  `cleared: true` if a snapshot was removed and `cleared: false` if the project already had none.
- Both operations require the exact `projectName` and return it together with `operation`.

Each project has one snapshot in the EDT workspace's plugin state. It survives an EDT restart.
Saving reads existing validation markers. Run or wait for validation before taking the snapshot.
The tool is available in read-only presets because it only changes plugin state.

The scan can collect up to 5,000 matching diagnostics. An unreadable marker location, a scan
that exceeds the cap or an invalid scope prevents save. A failed save preserves the prior
snapshot. To recover, wait for project validation to settle or narrow the scope.

## Examples

- Save the whole project: `{operation: "save", projectName: "MyConfig"}`.
- Save one module's object: `{operation: "save", projectName: "MyConfig", objectFqns: ["CommonModule.Sales"]}`.
- Clear the snapshot: `{operation: "clear", projectName: "MyConfig"}`.
