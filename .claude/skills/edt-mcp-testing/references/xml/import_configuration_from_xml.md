# import_configuration_from_xml — live verification

Creates a new EDT project from XML files using
`IImportConfigurationFilesApi.importProject(Path, String, String, String)`.
On EDT 2025.2.5 the last two arguments are the platform version and base project
name. The nature and XML format are inferred from the source files.

## Preconditions

Use an isolated workspace, an unused disposable project name and an XML dump
exported from a known fixture. The EDT CLI API plugin must be available. Allow
time for the import and the subsequent project start; the import API itself
has no bounded cancellation. Run one mutation sequence at a time.

## Parameters

- `importPath`: required XML source directory; an outside-workspace path is
  allowed and flagged in the response.
- `projectName`: required new project name; an existing project is refused.
- `runtimeVersion`: optional project platform version, such as `8.3.27`. SDK
  short numeric forms and whitespace remain accepted, and the legacy spelling
  is forwarded unchanged. Without a base, the runtime registry (when available)
  also checks that the requested version is supported.
- `platformVersion`: compatible alias. If both aliases are nonblank, their
  parsed EDT versions must agree. Blank values mean omitted. An explicit
  version remains valid with a base; if both are omitted, inherit the base
  runtime when a base is given, otherwise let EDT infer it from XML.
- `baseProjectName`: required for extensions, refused for main configurations,
  optional for external objects. It must identify an open main configuration.
  An unreadable `Configuration.xml` cannot establish the kind when a base is
  requested; an absent `Configuration.xml` is an external-object dump.
- `projectNature`, `xmlVersion`: deprecated. Omit or leave empty. A nonempty
  override is refused before workspace/path/API access. It must never be
  forwarded as the runtime version or base project name.

## Procedure

1. Export the fixture using `export_configuration_to_xml` to a disposable
   directory. Check actual XML files were written.
2. Import under the unused name. For extension verification pass the fixture's
   `Runtime-Version` as `runtimeVersion` and its base configuration as
   `baseProjectName`.
3. Poll `list_projects` until the imported project reports `ready`. Verify its
   reported path, `.project` nature, `DT-INF/PROJECT.PMF` runtime/base values and
   independent model readback. A successful response alone is insufficient.
4. On failure preserve the XML source, observed imported project files and EDT
   logs before cleanup. Delete only the observed disposable project with
   `delete_project(confirm=true, deleteContent=true)`; verify project and path
   absence. Remove the owned temporary XML dump. Both source fixtures must be
   clean afterward.

Automated coverage lives in
`tests/e2e/tools/test_import_configuration_from_xml.py`. It covers the real
configuration round trip, explicit extension runtime/base linkage, compatible
version aliases and their conflicts, legacy override refusals, missing
parameters/paths and project-name collisions.

## Result and recovery

Success is Markdown with front matter. `projectReady: true` describes the
started project context; derived data may still be computing, so rely on
`list_projects` before using model tools. `projectReady: false` and
`state: importing` mean the files exist and EDT start work continues after the
call's 300-second start budget. Do not repeat the import under the same name.

The tool refreshes the imported files and explicitly starts the project. It
retains EDT's start latch during normal operation to prevent a competing
watchdog start; failure to schedule the post-import start releases the latch
for recovery. A committed import followed by a failed start is an error with
mutation evidence, rather than a rolled-back import.

Errors use the structured tool-error envelope. Assert the invalid parameter or
project value and an actionable remedy; inspect the workspace after an opaque
import failure because the SDK may have created a partial project. This tool
does not support overwriting an existing project and has no preview mode.
