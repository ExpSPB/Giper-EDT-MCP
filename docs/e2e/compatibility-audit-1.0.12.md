# Independent compatibility audit: Giper 1.0.12 and EDT 2025.2.5

Date: 2026-10-05 (Europe/Moscow).

## Scope and evidence

The audit compares the complete tracked working tree, including staged and
unstaged changes, with fork HEAD `fc732418757b61f61c3d84726d89aa00cce557b0`.
Upstream is `c99edaf3`; the incremental baseline is `0be57656` (24 commits).
Predecessor behavior restored during the full-tip audit is also in scope.

The captured inventory contains 500 changed tracked files: 132 production Java
files, 112 Java test files and supporting tests, fixtures, documentation and
infrastructure. This is an inventory and a review by feature/dependency group,
not a claim that every changed line has a separately reproduced live verdict.

Three new reviewers, who did not implement the port, independently examined
metadata/forms, runtime/infobases and build/API dependencies. Root reviewed
transport/profile/UI changes, official release notes and the combined findings.
Previous successful tests are supporting evidence, not the basis for declaring
an API present. Presence/signature checks use the installed target SDK jars,
their sources, exported packages, bytecode and active `bundles.info` wiring.

No development fix, build, test, EDT launch, MCP request, fixture reset, commit
or publication was part of this audit. This report records that completed
read-only phase. The user subsequently resumed autonomous completion; current
fixes and validation are tracked in `port-lessons-1.0.12.md` and the latest work
checkpoint, without changing the audit's captured evidence.

Audit evidence is under `.claude/work/port-1-0-12/`:

- `independent-audit-snapshot-20261005.json`: source hashes and all 24 commits.
- `independent-audit-metadata-20261005.md`: metadata/forms reviewer.
- `independent-audit-runtime-20261005.md`: runtime/infobases reviewer.
- `independent-audit-build-api-20261005.md`: build/API reviewer.
- `independent-audit-verification-20261005.json`: all 500 captured file hashes
  unchanged after audit, all 24 commit rows present and all three reports saved.

## Classification

| Code | Meaning |
|---|---|
| FIX | Fixes an existing plugin operation using APIs already available in the target. |
| EXTEND | Adds MCP access or richer authoring using an existing target SDK capability. |
| ADAPTED | Requires a target-specific signature, model/default or infrastructure adaptation. |
| NEWER_ONLY | A concrete dependency on functionality absent from the target; exclude or provide a genuine equivalent. |
| PLATFORM_RUNTIME | Requires a 1C executable/version, infobase, application or separate optional plugin; this is not itself an EDT-version dependency. |
| UNCERTAIN | API presence does not establish runtime behavior, or the causal diagnosis is incomplete. |

These classifications can overlap. A new MCP command does not mean a new EDT
feature; an available API does not prove that all target SDK defects are absent.

## All 24 upstream commits

The tables below classify the resulting fork behavior, not every original
upstream implementation unchanged. The detailed reviewer reports name target
SDK methods, signatures, bundle versions and current source locations.

| Commit | Change | Classification and disposition |
|---|---|---|
| `681718a9` | Standalone server/launch/update outcomes and infobase sessions | FIX + EXTEND + PLATFORM_RUNTIME. Existing standalone/Ibcmd/session APIs; retain guards and real runtime prerequisites. |
| `76df6775` | Escape control bytes in Git output | FIX. Plugin output handling; no new EDT dependency. |
| `e80f4b0d` | Resolve standard command groups | EXTEND. Existing target command-group model; retain bilingual/address checks. |
| `8c57ac25` | Keep ownership until standalone server stop completes | FIX. Existing service lifecycle with safer plugin state handling. |
| `7932d7a1` | Channel coherence guard and local 2026.2 verification | FIX/ADAPTED infrastructure plus NEWER_ONLY upstream target infrastructure excluded from the fork. Exact .5 build identity needs correction, described below. |
| `f19d860c` | Notify tool-list changes and pin fixture declarations | FIX. MCP/profile notification and test hygiene, not a new EDT UI feature. |
| `d0f5e09e` | Explicit project start/state after XML import | FIX. Existing lifecycle APIs; retained optional-argument contract defect described below must be corrected. |
| `f0f33276` | Spreadsheet rotation, column auto-width and weight | EXTEND. Target Moxel interfaces already have the required setters. |
| `c2aed241` | Log expected refusals without server-error severity | FIX. Plugin error/log contracts. |
| `5fd4ca6a` | Judge form retyping by final type and remove orphan handlers | FIX. Existing form model and handler semantics. |
| `503a8161` | Bounded launch/debug waits and lifecycle/application resolution | FIX. Existing Eclipse Jobs/debug/application APIs; inspect behavior as well as signatures. |
| `8a06e9e2` | Module load, read-only hint, session parameter, group handlers, DCS flake | FIX. Existing BSL/form/runtime/DCS capabilities; no demonstrated newer-only API. |
| `25fdff76` | `debug_pause` | EXTEND. Existing suspend/debug target capability exposed through MCP. |
| `58abe6d0` | Import `.cf/.cfe/.epf/.erf` into a new EDT project | EXTEND + PLATFORM_RUNTIME. Existing conversion/CLI/project APIs; requires suitable platform execution and file-kind prerequisites. |
| `9e0bd9f7` | Section command-interface visibility, placement and order | EXTEND. Target CMI model/tasks already expose the relevant operations. |
| `c69de5a7` | Export infobase configuration/extension to `.cf/.cfe` | EXTEND + PLATFORM_RUNTIME. Existing dump/thick-client APIs; infobase/default application/installed platform prerequisites are independent. |
| `862e2f4e` | Typed DCS charts with validated references | EXTEND. Existing DCS chart/group/selection model; runtime correctness still requires its own live gate. |
| `1a3cf3c6` | Report and explicitly sweep proven orphan role rights | EXTEND. Existing target rights/address model; preserve preview and guarded deletion. |
| `7c0fd187` | Refuse update for an infobase not bound to the current branch | FIX. Plugin safety plus existing application context. |
| `d62c416f` | Vendor guards, markers, defaults, external-object scope and typed refusals | FIX + EXTEND + ADAPTED. Existing target model/services; distribution types, factories and fixture representation need target-specific handling. |
| `67b4864f` | Do not let a stale sync flag permanently block writes | FIX. Existing readiness/derived-data state handling; final cleanup remains unproven. |
| `febf069b` | Read a subordinate role right at its own address | FIX. Target rights model; output address correction. |
| `048cdd1d` | Track whether launch update auto-confirmation was armed | FIX. Existing UI/job lifecycle; release only resources actually acquired. |
| `c99edaf3` | Use `refs/heads/<branch>` in branch infobase context | FIX. Existing EDT/EGit context contract; no new branch feature required. |

## Predecessor restorations and fork-only adaptations

| Group | Classification | Evidence and disposition |
|---|---|---|
| Parse-once dispatch, timing/history, interruption outcome, bounded client capabilities and structured-content contracts | FIX | JDK HTTP/JSON and plugin request/session logic. Keep per-profile/path-bound state and explicit opt-out behavior. |
| Bridge `BiFunction`/`Supplier` service aliases | EXTEND | Standard JDK interfaces and existing OSGi registration. Aliases delegate to the fork's profile-aware bridge. |
| Consent audit redaction and normalized server auth/restart checks | FIX | Plugin authorization/audit behavior. Retain the live listener snapshot and refuse invalid remote restart before stopping it. |
| Shipped check descriptions, optional override and project-aware check lookup | FIX/EXTEND | Existing OSGi resource/check APIs; not dependent on newly introduced check UI. Individual check availability still depends on installed check providers. |
| Live endpoint display/copy and saved-token/unsaved-state warnings | FIX/EXTEND | Existing SWT/JFace/OSGi APIs; preserve Giper branding and named profiles. |
| Tool migrations and named-profile isolation | FIX | Plugin policy. New tools must not silently widen previously stored restricted profiles. |
| Workmate adapter/error guidance | PLATFORM_RUNTIME/UNCERTAIN | Separate optional plugin version and reflective contract; do not equate its release number with EDT's. |
| Typed configuration adoption and owner-aware dimension factories | ADAPTED/FIX | Target adoption/factory APIs exist; mandatory defaults and new identities matter. |
| Vendor fixture, form command bars, encoding and corrected public readback assertions | ADAPTED/FIX | Fixture/test compatibility. These do not add an EDT feature or justify weaker assertions. |
| Export-quiescence ordering, snapshot parity and documentation-generation guards | FIX | Harness/orchestration correctness. They do not certify the unresolved dependent-form reset. |

## Findings requiring a decision

### 1. Build baseline differs from the exact supported runtime

The active owned EDT installation uses
`com._1c.g5.v8.dt.core_26.0.1.v202604021910.jar` and product application `1.34.5`.
It reports runtime `2025.2.5.2`. Its Eclipse platform is 4.30.0 / 2023-12.

`.github/scripts/edt-pin.sh` pins channel `2025.2` to
`26.0.1.v202605050943` while its comment incorrectly labels that qualifier as
2025.2.5. The live official channel supplies product application `1.34.6` and
that May core qualifier. `mcp/targets/default/default.target` selects `0.0.0`
IUs from the mutable channel. A channel pin/cache key does not make it the
installed April .5 SDK.

All 119 manifest import clauses, ten required-bundle ranges and 26 explicit
package-version floors were checked against installed .5 exporters and satisfy
the declared requirements. This proves dependency availability, not full live
OSGi wiring or every method contract. It does not remove the build-baseline gap.

Retained latest-build Surefire evidence confirms this is not just a future
resolution risk: `mcp/tests/fm.giper.edt.mcp.server.tests/target/surefire-reports/TEST-fm.giper.edt.mcp.server.ActiveToolCallRegistryTest.xml`
records the May core qualifier in `osgi.bundles`. The generated Surefire
properties date is 2026-10-05 16:22:15 MSK, within the latest canonical build.
The passing Java tests therefore ran on the .6 SDK while live checks used .5.

Disposition: establish an exact, reproducible .5 compilation/API gate using a
local archived target or equivalent locked dependencies; distinguish any .6
build from the .5 support verdict. Correct the misleading label. Audit only:
no target or CI edit has been made here.

### 2. Upstream's 2026 platform migration is outside this port

Upstream's target selects EDT 2026.2, Eclipse 2025-12 and its newer supporting
bundles. Official [EDT 2026.2 release notes](https://edt.1c.ru/docs/new/versiya-2026-2/)
describe the Eclipse 2025-12 / Java 25 migration and additional merge behavior.
Those platform migrations are not required to expose the audited existing APIs
through MCP. The fork retains Java 17, Tycho 4 and Eclipse 2023-12 and excludes
the upstream 2026 target/oldest-platform script change.

Disposition: retain that exclusion. Keep version-neutral repository consistency
and cache fixes, but prove them against the intended target identity.

### 3. A missing enum constant was a test compatibility issue

The upstream comparison unit test referenced `BEFORE_MERGE_PROCESS_STARTED`,
which is absent from the target enum. The current test uses the available
`MERGE_PROCESS_STARTED` and retains the unexpected-phase/raw-status assertions.
This is an ADAPTED test, not evidence that the whole comparison feature requires
a new EDT version. Production logic does not directly reference the missing
literal.

The cached .6 comparison enum also lacks the same literal. Its use in an
upstream test therefore does not by itself prove introduction in a newer EDT;
the audit has not established which newer SDK, if any, provides it.

The upstream comment in `ApplicationSupport` also describes interruption
behavior observed in the newer Eclipse Jobs bundle. The reviewer checked the
same interrupt/`canBlock` behavior in the target's Jobs 3.15.100 source; this
comment is not evidence of a missing target API.

### 4. Predefined characteristic initialization is a plugin defect

`PredefinedWriter` conditionally applies the type only when `valueTypeSet` is
true and constructs the characteristic item through the raw metadata factory.
The target validator dereferences its null type. The valid omitted-type request
and preserved SDK stack confirm an initialization defect; the characteristic
model/type API itself is available in the target.

The same conditional initialization/raw factory branches already exist at fork
HEAD, upstream baseline and upstream tip. This is an inherited defect exposed
by validation, not a branch introduced by the 24-commit port.

Disposition: correct initialization using the target SDK contract, preserving
explicit caller types and identities. Do not drop predefined-item functionality
or introduce an arbitrary String default. The audit applies no fix.

### 5. Extension-form mismatch may depend on a later SDK bug fix

The target has automatic form-adoption synchronization. Its existence rules out
the explanation that form auto-adoption is wholly absent in .5. The current
cleanup/readiness path does not prove that the extension form body and dependent
bindings return to the initial state.

Official later fixes are relevant leads:

- [1C issue #2030](https://github.com/1C-Company/1c-edt-issues/issues/2030) describes
  phantom adopted-form differences after importing changes; it names 2025.2.1
  and is listed as fixed in the 2026.2 release notes.
- [1C issue #1966](https://github.com/1C-Company/1c-edt-issues/issues/1966) describes
  loss of a form field's data path after deleting an attribute used by its choice
  parameter bindings; it names 2025.1 and is also listed as fixed in 2026.2.

These scenarios are not identical to the failed Git-refresh reset. Classify as
UNCERTAIN / potential dependency on a newer SDK bug fix, not a proven NEWER_ONLY
feature. Establish one reproducible mutation and compare clean imports before
deciding on a bounded .5 workaround or an explicit unsupported operation.
Do not infer causality from matching symptoms or weaker postconditions.

### 6. XML importer uses an existing API with incorrect optional arguments

`ImportConfigurationFromXmlTool.java:1267-1270` reflects
`importProject(Path, String, String, String)` and passes project name,
`projectNature` and `xmlVersion`. The tool documents the last two as nature and
XML format version.

The exact target implementation in
`com._1c.g5.v8.dt.cli.api.impl_0.2.600.v202604021910.jar` uses the third argument
as runtime version and the fourth as base-project name. Root independently
checked its bytecode: `Version.parseVersion` consumes the third argument;
`createManifest` writes the fourth to `Base-Project`; nature is inferred from
the imported content. External-object import also resolves that base name
through `getParentProject`.

The implementation's LocalVariableTable independently names slot 3 `version`
and slot 4 `baseProjectName` in both public overloads. The source-line metadata
maps version parsing to line 108, overload forwarding to line 162 and the
`Base-Project` manifest write to line 242.

Consequently, a nonempty `xmlVersion` can become an invalid base-project name,
and `projectNature` is not applied as advertised. Supplying both as null conceals
the mismatch. The branch is already present in the upstream predecessor, and
the current E2E coverage does not exercise nonempty values for these options.

Classification: inherited FIX/contract misuse, not NEWER_ONLY. Correct the
public contract and adapter deliberately, with meaningful optional-argument
coverage, when development resumes. This audit does not execute a reproduction
or apply the fix. The separate binary importer uses runtime/base arguments in
the correct roles.

### 7. Optional integrations and platform execution remain separate gates

The owned Workmate plugin is version `1.0.8.v202609241142`, independent of EDT's
product version. The adapter probes structural API shapes; its public guidance
naming 1.0.5/1.0.7 is not a strict runtime allowlist. The reviewer verified the
current signatures and the existing-project alternative to a missing ProjectId
class. Live model/cloud/tool-round behavior is outside this audit's evidence.

The new binary import/export and session-management operations also depend on
installed platform executables, runtime selection, credentials and application
or infobase context. SupportedConfiguration's platform-support 8.5.1 component
does not itself install a platform executable. API availability alone does not
certify a successful CFE export or destructive session operation.

## Decision boundary

Retain the ordinary fixes and extensions whose required target APIs were
verified. Do not wholesale-remove the four new tools simply because upstream
now targets 2026.2. Prioritize exact build-baseline identity, omitted-type
initialization, XML optional-argument contract and dependent extension-form
state. Treat actual platform
executables, application context, permissions, infobase access and optional
plugins as separate prerequisites.

This source/dependency audit does not certify a release, resolve the runtime
blockers or replace complete E2E, SDK-log classification and protocol conformance.
