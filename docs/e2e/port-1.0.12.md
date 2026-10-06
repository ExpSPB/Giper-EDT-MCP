# Giper 1.0.12 upstream port

## Scope

The source is `DitriXNew/EDT-MCP` master at `c99edaf3`, covering the 24 commits
after the previous port baseline `0be57656`. The fork starts at `fc732418`
(1.0.11). The target is EDT **2025.2.5**, Java 17, Tycho 4.0.5 and Eclipse 2023-12.

A separate full-tip audit also restores behavior omitted in the predecessor port,
including code unchanged within those 24 commits. Deliberate fork profile and
EDT 2025 adaptations remain in place.

The port adds `debug_pause`, `infobase_sessions`, `import_project_from_file`, and
`export_configuration_to_file`. It includes the upstream fixes for bounded
launch/debug waits, standalone server ownership, branch infobase bindings,
explicit XML import state, vendor-support write protection, role-right orphans,
command interfaces, DCS charts, form retyping and spreadsheet layout.

## Validation results

The current plugin `1.0.12.202610060609` passed its exact EDT 2025.2.5.2 build:
9,138 Java test cases, zero failures/errors and 19 skips. The completed canonical
E2E run has 1,358 passes and 42 individually reviewed applicability skips,
covering 1,400 results/1,397 identities. The standalone proxy fix passed all250
tests; fresh post-reboot profiles passed241/241 and all five official protocol
URLs passed the unchanged baseline. All sixteen original model inventories,
details and complete diagnostic Counters matched, and four fresh V11 bodies
confirmed all sixteen public computed points and current helper identity.
Independent final dynamic review accepted this bounded evidence (`e9da4f53`).
The SDK log is nonzero: all103 retained severity4 records (91 prior plus12
startup records) were individually classified, with no unknown/new plugin fault
or targeted NPE. The retained SDK validator failure's initiating object/writer
remain unidentified. The42 skips do not certify manual/live-infobase/native
binary/PII or other unavailable paths. Historical Updating investigation is
excluded by the user; restarting EDT and repeating the update is their accepted
workaround. The exact 530-file source capture and independent review passed
(`a478347b`), with no blocking static findings. The local commit result is
recorded in the latest work checkpoint; no push or merge is included.

## Fork and platform adaptations

| Area | Adaptation |
|------|------------|
| Identity | Keep `fm.giper.*`, Giper plugin/feature names, fork update URLs and upstream attribution. |
| Profiles | Preserve named allowlists, profile exchange, path-bound sessions and fail-closed dispatch. New tools enter presets, without widening existing stored profiles. |
| Notifications | Applying preferences invalidates only affected profile sessions. Clients reinitialize to see the new surface. |
| Transport and capabilities | Refuse an unauthenticated remote restart before stopping the healthy listener. Track the live bind snapshot for authorization and UI warnings. Retain the fork's protocol-version CORS header and path-bound sessions. Bound retained client capabilities at 4,096 characters; distill larger declarations to the explicit structured-content opt-out without retaining the input tree. |
| Parsed requests and history | Parse the HTTP request once and pass the same parsed request, timing and profile/session context through the executor and protocol handler. Clear per-request metadata after history recording. Interrupted tool results carry `isError=true`. Preserve concurrent call tracking and per-session capabilities. |
| In-process bridge | Restore the `BiFunction`/`Supplier` service aliases on the same bridge instance. Their call/list entry points retain the fork's live default-profile policy; named-profile overloads and Workmate's profile-bound dispatch remain. |
| Git audit | Redact caller-authored commit/message and server-option payloads from unattended consent audit while preserving identifiers, paths and argument boundaries. Keep the fork's branch-context and infobase bindings. |
| Check descriptions and status | Load shipped descriptions with an optional external override; a configured folder is no longer a precondition. Retain project-aware UID resolution and actionable unknown-check refusals. Derive auth status from the authorizer's normalized token and override-folder status from the shared loader, without exposing credentials or paths. |
| Connection preferences | Display and copy the live listener's endpoint and saved normalized token. Copy valid escaped MCP JSON only on an explicit button press; identify unsaved edits, remote-listener lockout and unpresentable tokens. Refresh after the preference page restarts the listener. Keep the default `/mcp` endpoint and named-profile tabs. |
| Preference migration | Keep the fork's v5/v6 migrations; apply later upstream defaults through new migration numbers. |
| Vendor support | EDT 2025 exposes distribution settings through `AbstractDistributionSupport`; inspect the concrete `DistributionSupport` safely. Use the 2025 export version floor for the distribution-model package. |
| Vendor-support fixture | Keep `Configuration.distr` and an equivalent version-6 `ParentConfigurations.bin`: EDT 2025.2.5's `.distr` editing provider requires an associated vendor library, which this isolated fixture lacks; its legacy provider reads the `.bin`. Generate it from the `.distr` loaded through EDT's EMF `DistributionSupportPackage`, including effective defaults, using the platform's `TextListOutStream`; round-trip it through `DistributionSupportFileDeserializer` and compare all 13 item UUIDs, parent UUIDs, support modes and parent metadata/rules. That historical fixture conversion left assertions and production code unchanged; the later editable-member delete assertion and guide correction are described separately below. |
| Resync isolation | Skip configuration dangling-reference cleanup for external-objects scopes before accessing a BM id. The upstream caller mixed a linked base Configuration with the external-project BM model; own external roots continue to export and repair normally. |
| Extension creation | Adopt the base Configuration through EDT's `IModelObjectAdopter`, retaining typed extension state and default-language mappings. Refuse aliased or incorrectly typed adoption results before modifying identity; assign a fresh UUID and preserve caller overrides. |
| Register dimensions | Use EDT's owner-aware metadata factory for all four register dimension types. Preserve its String(10) default and caller overrides; refuse missing type descriptions before attaching the child. |
| Form fixtures | Restore the upstream default FormCommandBar in the borrowed form, its BaseForm and CommonForm.Form. EDT 2025.2.5's adoption controller and HippoGenerator assume this default bar exists. Pin the three additional declarations. Audit all five fixture forms against both upstream baselines. |
| Extension-owned query fixture | Restore upstream `Catalog.tests_ExtOnly` with String(50) `ExtValue`, its Configuration collection reference and six explicit false Role rights. Pin the exact nine added declarations. Preserve the target's 8.3.27 compatibility and existing form/support adaptations. All 91 upstream fixture files were compared; the compiled 855-input scope is unchanged. |
| Target platform | Keep the 2025.2 p2 target and Java 17. Port channel-coherence checks and include the served EDT build in CI cache keys. |
| SDK enum unit compatibility | In `ComparisonEngineTest.anUnexpectedStatusIsNotFoldedIntoAComparisonPhase`, replace the unavailable `BEFORE_MERGE_PROCESS_STARTED` literal with EDT 2025.2.5's `MERGE_PROCESS_STARTED`. Preserve both assertions: the phase is `UNEXPECTED` and the raw reported status is unchanged. Production comparison logic is unaffected. |
| Release build | Resolve the Tycho versions plugin from the project's pinned `tycho.version`. |
| Test harness | Retain profile clients and isolation checks while porting upstream mutation evidence, model refresh and fixture ratchets. Normalize line endings for model/disk comparisons on Windows. Defer the first fixture revert until readiness and export quiet. Restore extension dependencies through CLEAN; retain per-project cold-reset evidence after committed delete/rename or unknown mutation outcomes until that project's original model is verified. Ordinary known writes retain incremental refresh. Preserve mandatory and evidenced optional scopes, freeze guards and checked optional disk cleanup. |
| CLEAN completion | Register the EDT lifecycle barrier before scheduling CLEAN. After restart, require the post-build stage, ready MD/FORM segments and live readiness predicates within one nominal wait budget. An empty SYNC queue does not certify completion. Propagate SDK probe failures through a checked shared predicate; preserve existing tolerant gates and committed mutation evidence. Positively identified plain Eclipse projects retain resource-only clean. Public exact-.5 status types require the exported `com._1c.g5.v8.derived.pipeline` OSGi import. |
| Rename resource errors | Observe the public core log only during each synchronous SDK refactoring on its actual thread. Refuse a swallowed resource synchronization error, preserve partial/unknown mutation evidence and stop later refactorings. Keep the SDK error visible. This does not identify a locking actor or certify asynchronous export coherence. |
| Inherited encoding damage | Restore the upstream UTF-8 characters in code, guides and bilingual test inputs that were misdecoded as CP866 in 1.0.11. Independently verify every restored token against upstream; preserve ASCII and fork changes. |
| Test contracts | Restore the upstream exact-address refusal and form-render status assertions. Pin the fork configuration's actual 8.3.27 compatibility value. With `EDT_MCP_INSTALLED_PLATFORM=1`, skip the import and infobase-creation scenarios whose precondition is an absent runtime. The latter strictly checks the real missing-runtime refusal in headless CI; runtime-dependent happy paths still require the separate live-infobase gate. |
| CommonAttribute readback | Restore all 14 current-tip scenarios, including constant separation and removal refusals. Assert the public Content table's exact owner/use rows, uniqueness, updates and empty readback after removal; retain counts and disk checks. Do not assert internal EMF class names in rendered Markdown. |
| Role fixtures | Creation already persists an empty rights model in both upstream baselines. Replace stale no-model test preconditions with exact empty Rights XML/default-state checks, a specific authored cell appearing after the first write, and byte-identical rights/tree after a rejected mixed payload. Keep null-model unit coverage. |
| Fixtures | Preserve fork fixture differences; pin their actual declarations. Add disposable binary-import and vendor-support fixtures. |
| CI runtime support | Install both platform-support 8.3.27 and 8.5.1; the vendor-support fixture uses 8.5.1. Both IU identifiers participate in the EDT cache key. |
| Documentation | Restore the previously missing comparison/error-breakpoint entries; validate 99 canonical built-in tools and the proxy's `router_status` entry. |
| Documentation generator | Reject failed MCP toolset and guide responses before writing files. Preserve an existing hidden-tool page only for an exact profile refusal, with a warning; missing pages and internal failures abort generation. |

Upstream changes to the 2026.2 target and its oldest-platform verification script
are deliberately excluded. Existing EDT 2025 infobase API fallbacks remain.

## Current candidate and validation evidence

On 2026-10-06 the user resumed work after restarting the computer. Old process
IDs and JVM observations are historical; fresh owned runtime identity and
post-reboot checks are required. The user explicitly excluded further
investigation of the historical extension status stuck at Updating: restart EDT
and repeat the update is their accepted operational workaround. This issue is
not a release blocker, and its underlying cause or permanent cure is not claimed.

The current compiled candidate is `1.0.12.202610060609`, built against exact
EDT 2025.2.5.2 and the April SDK: 9,138 Java tests, zero failures/errors,
19 skips, 377 reports and 860 stable captured inputs. Its archive is
`dist-exact5-vendor-support/MCP-EDT.v1.0.12.zip`, SHA-256
`1203bc5a372fc573c71f1c5411c64e2879a1b14bd8647aef5046ea86fbafe3ec`;
the bundle SHA-256 is
`d2554b4369aac6cf6cd44fb5fcdc7f60727282f3211ed3345d65e606d8a3fb24`.
The build proof is `vendor-support-build-proof.json`. Root's canonical build
18081 exited 0 (`772362`) and the separate verifier exited 0 (`d02edb`),
checking all 503 installed SDK bundles and ten test supplements.

The preceding 0440 full run failed with 1,225 passes, 40 skips and one failure:
the vendor-support test expected an editable member deletion to be refused.
Exact SDK review confirmed that a member's `canDelete` and its owner's `canEdit`
are distinct from permission to delete that owner. The corrected canonical test
checks structural model and disk deletion and retains top-level and locked
referrer refusals. The packaged delete guide now describes that contract.
Production Java and support guards are unchanged; all 860 compiled inputs match
0440. The failed full, compiled artifact/reports and whole stopped stands were
preserved before reuse. Its passes do not certify the corrected test.

The fresh 0609 four-stand manifest and runtime identity match this bundle.
Prime captured all sixteen original models and complete diagnostic tables;
the extension-owned catalog query is valid with zero issues on every shard.
V9 revision3 observations confirmed the loaded bundle and compiled input map,
with successful public reads and computed state for all sixteen project points.
Profiles passed 241/241. Two fresh unchanged-generator runs matched all 102
published document bytes; only the generated delete guide changed from 0440.
Golden passed 2/2 (Root60069, exit0 `d83ad7`). The focused run failed at its
first canonical vendor-create body after receiving a corrupted Russian FQN
(Root71964, exit1 `3d5e53`): zero passes and thirteen abort skips. Its later
SDK capture does not establish model restoration or acceptance. A narrow test-only
correction restored three module globals using ASCII Unicode escapes after failure
preservation. Whole-module AST and raw-line comparison retained every other node,
decorator and the reviewed editable-member body. The same-JVM public observations
and original four-model/complete-marker readback passed before adoption; no JAR
rebuild, reset or restart was needed for this test-only correction. The failed focus
remains failed. The next focus passed eleven cases, then failed the newly authored
editable preview check: its full fixture bytes matched, but the helper demanded
clean Git HEAD after the scenario's own successful writes. Confirmed member
deletion had not run. After preserving that failure, scoped recovery restored
the four original models and complete markers with SDK0 before source adoption.
The reviewed correction keeps full byte equality mandatory, retains strict
clean-baseline defaults for existing locked controls, and disables only the two
post-write HEAD checks. Root adopted the test-only patch and synced four private
commits; current test SHA256 starts `a1c3db17`. Compiled inputs, JAR, ZIP and guide
are unchanged. The fresh focus passed all fourteen cases with no skips
(Root21828, exit0 `6dd72e`), enforcing current `a1c3db17` source. Its final
four original inventories, details and complete diagnostics matched; fixtures
were clean, V11 observed all four computed points, and both observed SDK windows
contained no severity4 entries. Those completed gates apply to this focus only.
The fresh full run completed under Root29642 (exit0 `df1ae6`) in
`e2e-final-exact5-20261006-vendor-baseline-run1`: 1,358 passes and 42 skips,
all 1,400 results for the exact 1,397 canonical identities, with all four child
exits0. The result-integrity verifier passed (`cefe30`). Same-run readback
passed (`ba7abd`) for all sixteen original inventories, details and complete
diagnostic line multisets, with every project ready and fixtures clean. V11
public observations and the separate all-sixteen check passed on the current
bundle. Independent pre-conformance review accepted all 42 skip reasons.

The full SDK log is nonzero. Per-entry review covers JFace, intentional negative
DCS cases, native8.5.1 installation prerequisites, retained own negative cases,
asynchronous SDK FormEditor disposal and one BSL validator failure during CLEAN.
The latter has a confirmed repeated SDK transaction-handle transition; its
precise object and writer remain unidentified. Complete original16 readback
does not establish private queue drain or a historical hang cure. The per-entry
pre-conformance decision accepted all 91 retained severity4 entries and capture
coverage at that scope, including the bounded validator-restoration review.
This is a classified nonzero log, not SDK0 or a general exception waiver.

Official conformance failed (Root23703, exit1 `31b8d3`): three direct URLs passed
the unchanged protocol baseline, then the first proxy-default DNS case timed out.
The retained proxy thread dump shows all 32 fixed HTTP workers occupied by
long-lived GET SSE streams, starving POST, health and admission requests. This
proxy executor starvation is confirmed independently of the unresolved historical
EDT extension-updating issue. The separate bounded SSE executor is implemented:
ordinary HTTP admission remains responsive while up to 64 streams use dedicated
workers; excess streams receive 503 before SSE headers. Independent production
and narrow test reviews passed (`b8cda181`, `66aa2306`). Root77609 clean verify
exited0 (`d262c6`): all 250 proxy tests passed without failures/errors/skips.
Standalone artifact verification passed (`baf059`, proof `08cdcb29`), including
47 stable raw inputs and packaged Java17 class equality. The fat JAR is the
ordinary Maven final artifact; shade does not retain the separate `-shaded.jar`
name expected by the failed collector. Its SHA256 is
`2191bf6464234d60266af81a8c6474a3658634b2ca1e79954b322945d0d4c5bc`.
Fresh deployment and runtime identity passed after reboot. Profiles passed
241/241 (`98db89`). Official conformance Root19672 exited0 (`ed3e27`) on all
five direct/profile/proxy URLs, using the unchanged baseline with no new failures.
Post-conformance readback passed (`7efa2d`) for all sixteen exact original
inventories/details/complete diagnostic Counters, with projects ready and
fixtures clean. The eight fresh V11 attachment processes exited0, but independent
review found every JSON incomplete: BuildUtils was not already loaded and
`allFourComputedObserved=false`. Those receipts prove delivery only; fresh
computed points/helper identity were not accepted from those records. After
bounded class-definition-only warmup, unchanged V11 completed under unique
label `reboot-post-conformance-confirmed`; separate Root validation (`3154d2`)
confirmed all four observation bodies, helper identity and
`allFourComputedObserved=true`. Fresh SDK capture completed without read errors
(`d9f8b3`, proof `04d66392`). Twelve new platform startup records were already
individually classified (`34bafb50`). Final independent dynamic review accepted
the new capture (`e9da4f53`): all103 records classified, no unknown/new own fault
or targeted NPE. This is not SDK0. The plugin's
860 inputs and 0609 JAR are unchanged. The required proxy identity, profiles, conformance and original16 model
readback and fresh V11 computed observations now passed. Final dynamic review
and the exact 530-file independent source review (`a478347b`) passed at their
recorded scopes, without a new plugin build or plugin full rerun. The local
commit result is recorded in the latest work checkpoint.

The screenshot cleanup fix explicitly disposes an owned Moxel control before
its parent shell. Exact SDK bytecode confirms that parent teardown bypasses
listener removal in the public child override; an already-disposed child still
needs that override. Six unit cases cover ordering and cleanup failures.
This fixes an existing lifecycle defect; it does not identify the historical
extension-updating cause. Its six cases passed again in the 0609 canonical build;
this unit evidence does not certify future callbacks or full runtime acceptance.

The following candidates and observations are retained historical evidence.
Their live results do not certify the current 0609 bundle.

An additional reviewed CLEAN completion fix is now implemented, with eighteen
new unit regressions. Its canonical exact-.5 build passed; runtime acceptance
remains pending. The preceding 2357 primer was rejected after
an early EMPTY/SYNC success and missing extension query fields. Its complete
failure evidence and stopped workspaces were preserved before the new attempt.

The preceding accepted compiled candidate was `1.0.12.202610060203`, built against
exact EDT 2025.2.5.2. Its canonical build and separate verifier passed: 9,132
tests, zero failures/errors, 19 skips, 376 reports and 859 unchanged source/build
inputs. All 503 installed SDK bundles and ten explicit test supplements retain
the checked April versions and bytes.

The archive is `dist-exact5-resource-observer/MCP-EDT.v1.0.12.zip`, SHA-256
`043186954a78eadc9207c128e7576180e955d44dd43ad7aade2eb68eed914a82`;
the bundle SHA-256 is
`285e5fdc43e50059b6037ee74343b40700dfa37a5d0535b0ab780dc49292fd97`.
The build proof is `resource-observer-build-proof.json`.

Twenty-one new unit cases cover the synchronous SDK resource-error observer and
its apply/report boundaries, including cancellation and a listener failure that
Eclipse SafeRunner would otherwise absorb. New-binary live checks are in progress.
The preceding 0106 focused runner passed 17 cases, but its strict wrapper rejected
a real core failure deleting the borrowed extension's form file before reporting
rename success. Exact SDK bytecode confirms that the deletion exception was logged
and swallowed. The locking actor remains unknown; a later controlled successful
case cannot clear that failure. The new guard prevents that observed synchronous
error path from being counted as a confirmed refactoring.

The new rename barrier uses public target-SDK services to drain model events,
check all registered project computations and recheck project/manager identity
before each SDK refactoring. It retains one accumulated 60-second barrier budget
and preserves partial or unknown mutation reporting. These checks are observations,
not atomic exclusion; they do not prove that the historical batch exception or
extension updating hang is cured. On the preceding 0106 candidate, fresh loaded identity and all sixteen original
model/diagnostic baselines passed on four stands, including the immediate
post-CLEAN owned catalog query. Profiles passed 241/241, golden passed 2/2 with
clean fixtures, and two independent documentation generations matched all 102
published files. Those preceding results do not certify 0203. The current 0203
deployment separately passed all sixteen original model/diagnostic baselines,
including the immediate owned catalog query, profiles 241/241, golden 2/2 and
two byte-identical generations of 102 published files. Its focused replay passed
17/17 canonical cases, but the strict wrapper exited one for three retained
JFace disposed-item callbacks. Independent per-entry SDK/source/lifecycle review
classifies those three UI callbacks separately; the strict failure remains
unchanged. Its history replay passed 21 cases with one environment-gated manual
PII skip, clean fixtures and no fresh SDK errors. A controlled denied-delete
experiment confirmed truthful partial rename reporting and was restored through
the canonical reset with all original baselines equal. The intentional SDK
failure remains preserved. Full E2E and conformance remain pending.

The current full runner exposed an auxiliary policy defect: its cumulative
rename guard classifies the existing intentional missing-project XML-export
refusal as a new critical own-plugin error. The unchanged canonical ERROR
baseline already accepts that exact negative case. A narrowly typed and
request-context-bound file-only classifier is under independent review; raw
severity-four evidence and any rejected runner outcome are preserved. This
correction does not exempt resource, transaction, validator or new plugin errors.

That full run subsequently stopped before a rename body on earlier inactive
namespace errors from a Moxel language callback. Exact target SWT/SDK inspection
confirmed a separate plugin cleanup defect: disposing the screenshot's parent
shell bypasses the public Moxel control disposal that unregisters model listeners.
A three-path candidate explicitly disposes the control before its shell, including
an already-disposed widget, while retaining cleanup failures. Independent review
approves its source contract; six unit cases and a render/CLEAN/render live case
are prepared. It has not yet been adopted or compiled. The exact leaked instances
and the historical extension updating hang remain unidentified. The 0203 full run
remains failed, and its raw state is preserved before another candidate is tested.

## Historical verification after independent audit

The user resumed autonomous completion after the paused audit. The exact target
is EDT 2025.2.5.2 with April core `26.0.1.v202604021910`, Java 17 and Eclipse
2023-12. The mutable public 2025.2 channel resolves 2025.2.6, so the earlier
canonical builds below do not certify exact-.5 compilation.

The installed-profile snapshot now pins active artifacts and verifies all resolved
installed SDK bundle versions and binary hashes, with explicit test-only
supplements. The preceding rename-final canonical build passed 9,063 tests
with zero failures/errors and 19 skips. All 374 reports use the exact April SDK:
503 installed bundles match versions and binary hashes, plus ten explicitly
pinned test supplements. The two installed Guava versions are verified against
the exact inventory and retained runtime identity, without a blanket duplicate
exception. All 855 captured Java/resource/build inputs match before and after
the build. Current source discovery contains 1,396 E2E scenarios.

The preceding accepted compiled candidate is bundle `1.0.12.202610052155`, SHA-256
`1721a6a79f382bc0327d4923031490b8bad583b492c57e0e11db413521e7d70b`, in
`dist-exact5-rename-final/MCP-EDT.v1.0.12.zip`, archive SHA-256
`63a107da05ea4aca1c5da9928823f35bbaa5d8ed858a5cb819e31eea4f96a5cd`.
The accepted build proof is `rename-final-build-proof.json`; the changed rename
suite passed 36 tests. Independent rehashing matches all 855 before/after/current
inputs and finds only the two reviewed rename Java files changed from 2049.
The query Python assertion is separately hash-bound, outside that Java input set.
On this runtime, golden passed 2/2, profiles passed 241/241,
two independent docs generations match all 102 published files and all four
private source copies retain 1,396 canonical identities. Focused, full and conformance acceptance are pending;
disk JAR hashes and initial snapshots alone are not final loaded-runtime/model proof.

The first fresh 2155 focus, Root session 65159, stopped after five passing cases
when its catalog-attribute checkpoint observed extension DD `BLOCKED`/`SYNC`
with `isAllComputed=false`. The SDK log window contained zero severity-4 entries.
Its final log gate skipped after the abort and the fixture remained dirty; this
is a failed focus, without canonical cleanup or accepted original-model proof.
A same-JVM marker-read retry observed readiness about 2.08 seconds after the
false point; a later direct observation found all four projects computed with
`EMPTY`/`NORMAL`, without repair. The exact transition time is unknown. The later
68-second observation interval is not completion latency. This establishes
transient work, not a deadlock or the cause of the historical hang. The failed
artifacts remain preserved. Canonical recovery separately restored the original
models and complete diagnostic line multisets after readiness/export quiet.

The separate bounded retry, Root 87371, also failed with actual exit1: 15 PASS
and one ERROR, including a passing canonical log gate. The final unknown-field
query case did not run. Extension-owned query validation failed because the port
omitted upstream `Catalog.tests_ExtOnly`; this is a fixture omission, not an
absent EDT feature. The complete failed repositories, indexes, history and SDK
evidence were verified and archived before restoration. Four specific JFace
disposed-widget records were independently classified as queued UI decoration
updates during canonical CLEAN stop/reload. Their exact decorating provider and
disposal cause remain unidentified; classification does not make the run SDK0
or certify original models. No new target DD batch, nested transaction or
EValidator record appeared in that frozen failed window.

The independent 91-file upstream fixture audit found only the missing catalog,
collection reference and related Role rights. Catalog and rights now match
upstream bytes; the four changed paths include exact declaration pins. Root's
restored-fixture hygiene retry exited zero with eight tests passing. None of
those paths belongs to the accepted 855 compiled inputs, and no Java/test body
changed. Bundle 2155 therefore remains the same compiled candidate. Root's new
fixture-final prime (99173) and probe exited zero: all four extension-owned
queries are valid, each extension inventory contains eight objects and all
sixteen complete original diagnostics were captured. Old seven-object snapshots
remain historical. Corrected focus/history/full, final SDK/model readback and
conformance remain pending; the new original snapshot is a new fixture epoch,
not normalization or replacement of failed-run evidence.

For 2049, priming passed eight CLEAN requests and captured all sixteen fixture
instances' original complete diagnostics before mutation. Golden passed 2/2,
profiles passed 241/241, docs passed two independent complete generations and
private source parity retained all 1,396 identities. The new full run
`e2e-final-exact5-20261005-guards-run1` failed: Root session 22115 actually exited
one at 00:39:38 on 2026-10-06, with 1,183 PASS, 38 SKIP and one ERROR. The query
test required English `not found`, while the SDK returned the correct Russian
field ERROR with `valid=false` and position 1/9/8. All four owned stands were
stopped and complete evidence verified in `guards-run1-failure-preserved` before
moving whole old repositories; only shard 2 was dirty. No reset or successful
full/post-marker certification is claimed. The narrow assertion now checks both
query dialects and requires the same ERROR issue's exact field, localized
not-found phrase and position, plus `valid=false`, error count and no disk diff.
Independent source review passed; focused live acceptance remains pending.
The SDK review also found an actionable nested BM transaction during form rename.
The narrow correction captures immutable old name/id in one read, reacquires a
global SDK handle, rechecks the name in a second short read and calls the SDK
factory only after both reads close. Independent source/API review passed; eleven
new boundary regressions passed in the 2155 canonical build. Focused live and full
acceptance remain pending; 2049 does not certify that correction.
Completed full results, final SDK/readback, conformance and local commit remain pending.

The historical 1949 same-runtime focused gate passed 25/25, including its log gate: original inventories,
details and complete diagnostic line multisets match, fixtures are clean and
ready, and the focused window has zero SDK severity-4 entries. Evidence is
`dd-focused-new-final-readonly-proof.json`; no cross-runtime normalization or
post-run replacement baseline was used. This focused proof does not certify the
subsequent two guard changes.

Two complete generations in independent seeded trees used the unchanged docs
generator and produced bytes identical to all 102 published files: 99 canonical
guide pages, the retained `debug_launch` alias, folder README and `EDT-MCP.md`.
The default profile exposes 97 tools; its two denied hidden guides are explicitly
preserved under the existing validated-guide contract. Agent validation passed
13 skills and 99 canonical guides. The first two in-place generation attempts
remain failed OSError-22 evidence. A controlled mmap probe reproduces that
truncating-open mechanism, but the actual external mapper is unproven. Evidence:
`guards-final-docs-generation.json` records the new successful generations;
`dd-final-docs-generation.json` and preserved generation/probe logs retain the
earlier 1949 scope and in-place failures.

Source fixes correct the XML importer runtime/base arguments and reject malformed
explicit versions before SDK import. Predefined characteristic creation copies
its owner's effective type on omission and rejects a missing owner type before
mutation. Dependent extension CLEAN restores the original base and extension
models. Fresh timed experiments also identify incremental Git recreation of a
deleted CommonModule as the cause of the background removed-object validator
errors. The default cleanup policy now uses per-project cold reset for evidenced
delete/rename or unknown mutation outcomes. Canonical deletion/rename regression
passed 5/5, restored complete original diagnostics and models, and added no SDK
severity-4 entries. The full suite checks against immutable initial model readback.
The user's historical stuck updating status remains a separate diagnostic lead.

The earlier archive is `dist-exact5-final/MCP-EDT.v1.0.12.zip`, bundle
`1.0.12.202610051715`; its SHA and test identity are recorded in
`final-exact5-build-proof.json`. Its historical live gate observed all sixteen
projects ready. Golden passed 2/2 and profiles passed
241/241. Visible guides were generated twice with identical output; the two
unchanged hidden guides retain their verified source content. Agent validator
passed 13 skills and 99 canonical guides.

The earlier 1,075-PASS run with three RESET-FAILED remains preserved. The first
full run of the final exact-.5 binary stopped at 795 PASS, 29 SKIP and one ERROR:
an offline readiness assertion expected the former substring fallback. It was
corrected to preserve every-row checks and refuse malformed/unknown output;
independent review and all 248 runner tests passed (one Windows symlink skip).
All four stands were stopped and archived before restoring the one interrupted
fixture. The repeated full run stopped at 806 PASS, 219 SKIP and one CALL-TIMEOUT
during extension derived-data cleanup after STARTED. Those skips include aborts;
this is a failed gate. All four stands and complete fixture/index/log evidence
were preserved. The captured wait was shorter than the SDK completion deadline,
so it does not prove a permanent deadlock. The SDK can perform two consecutive
waits for one timeout argument. Isolated replay passed 2/2, and the nearest
twenty-case history passed 21/21; neither reproduced the long wait.

The first strict CLEAN completion canonical build failed one of 9,047 tests
because a diagnostic getter's return package lacked OSGi wiring. The exact SDK
publicly exports pipeline version 2.1.0; the mandatory package import fixes that
dependency without weakening the regression. The complete current retry and
focused gates above pass; the old 1715 binary does not certify these source
changes. Fresh private clones retain the original
committed fixtures; entire old repositories are preserved under
`repo-full-run2-preserved`. Old workspaces are never reopened, because their
absolute project paths would now point to the new clones.
The two background BSL validator errors retain their raw evidence and confirmed
incremental-restoration classification.

The full run `e2e-final-exact5-20261005-dd-run1` was intentionally stopped after
independent source review found two inherited fail-open guard gaps. A missing DT
project manager in clean-all was treated as an empty successful enumeration;
the public in-process bridge widened to all registered tools when its live
profile context was unavailable. Missing service/context is not an observed
empty workspace or an authorization grant. The narrow clean-all guard and two
regressions were applied after all four owned stands stopped and complete run,
SDK logs, threads, fixture trees and indexes were archived in
`dd-run1-audit-stop-preserved`. Shards 3 and 4 were dirty: two fixture repositories,
not three. No reset or final original-model certification was performed. Both
guards are implemented with five new regressions. The 2049 canonical build
certifies their compiled source; 1949's earlier focused results retain their
historical scope. Fresh live/final gates are bound to 2049.

The first guards build stopped at test compilation because resolved Mockito
`2.23.0.v20200310-1642` lacks `verifyNoInteractions`; no 9,052-test execution is
claimed for that attempt. Exact cached-jar javap proved `verifyZeroInteractions`
delegates to `verifyNoMoreInteractions`. Its fresh/reset mock positions preserve
strict refusal-before-dispatch assertions. The corrected retry1 passed.
An auxiliary archive check initially read UTF-8 JSON with the Windows default
encoding and falsely reported Unicode-filename drift. Explicit UTF-8 reading
verified all four preserved archives before moving the old repositories. No
fixture/source mutation or SDK project corruption was caused by that check.

One exact SDK marker duplicate-check batch in that stopped run overlapped
extension storage teardown inside canonical CLEAN; new storage, STARTED and
strict completion followed. Public marker commit and generic Job/DD waits do not
drain the private scheduled committer queue. Base CLEAN itself cascades to
dependents, so sequential client timing is not a demonstrated cure. This bounded
recovery does not prove or exclude persistent diagnostic loss; final complete
original-marker equality is mandatory. It does not explain the historical
stuck-updating status, and no SDK baseline exemption was added.

The stopped run's frozen auxiliary driver catches its normal `sys.exit(0)` as
BaseException and can write `{"error": "SystemExit: 0"}`. Root session 73818
actually exited one after the audit stop; it cannot qualify as a successful run.
The new producer catches `(KeyboardInterrupt, Exception)` and excludes SystemExit.
The generic final verifier rejects any driver-error artifact and requires actual
Root/child exit-zero evidence, no first-red/abort and exact 1,396-identity/1,399-result
integrity. No hardcoded old-session exemption remains. Raw original-model comparisons and
SDK capture-gap review remain required. Full E2E, final SDK classification,
current-artifact protocol conformance and local commit are pending. No final
release is certified.

The protocol baseline is unchanged from fork HEAD. The plugin ERROR baseline
was deliberately tightened from nine patterns to two by exact upstream commit
`c2aed241`; seven explicit refusal/missing-module patterns were removed, with
matching production logging and tests. This staged port change precedes live
testing. No baseline additions or blanket SDK exception exclusions were used.

## Historical verification record

These results apply to the earlier bundle. Installed EDT 2025.2.5 API jars were
used for inspection; retained canonical Surefire resolved the newer .6 SDK. The
results below must not be combined with later exact-.5 results as one green gate.

| Gate | Result |
|------|--------|
| Plugin canonical `source/compile.sh` | BUILD SUCCESS, exit 0, finished `2026-10-05T16:27:11+03:00`; 9,017 tests, 0 failures, 0 errors, 19 skips. The earlier focused resync unit run also passed all 58 tests. |
| Proxy verification | BUILD SUCCESS; 248 tests, 0 failures/errors/skips. |
| Python runner contracts | 201 tests: 200 passed; 1 skipped because Windows symlink creation is unavailable. Includes six dimension read-back cases, seven documentation-generation cases, 15 cleanup ordering/scope/failure regressions and 11 strict CommonAttribute Content-table parser cases. |
| Canonical E2E discovery | 1,392 scenarios after the full-tip predecessor audit; the previous 1,360-scenario registry is superseded. Discovery is separate from the pending full live verdict. |
| Agent documentation validator | Passed: 13 skills, 99 canonical guides and the proxy's `router_status` entry. Generated all 99 guides twice from the fresh readback bundle; both outputs were byte-identical, including fresh guides for the normally hidden `git` and `ask_workmate` tools. No preserved-page fallback was used. Temporary full-profile preferences were restored byte-for-byte. Evidence: `readback-docs-generation.json`. |
| Fresh EDT 2025.2.5 deployment | Four isolated installations run bundle `1.0.12.202610051321` in fresh workspaces; four fixture projects are ready in each workspace (16 ready project instances in total), as recorded in `fresh-stand-probe.json`. Handshakes report plugin 1.0.12 and EDT 2025.2.5.2. Their seeded default allows 97 tools; registry contains 99. The operator's separately installed earlier 1.0.12 retains its existing 87-tool allowlist. |
| Live golden | Passed against the fresh isolated `1.0.12.202610051321` bundle: 2/2 tests, fixture clean, default 97-tool schema matched. Synchronized the golden to Root and all four disposable checkouts. Evidence: `golden-readback-run.log` and `golden-readback-results.xml`. |
| Earlier focused live regressions | Resync/vendor-support: 15/15 passed; role rights: 12/12 passed. Fresh extension/dimension/form/nested-delete regressions: 6/6 passed. Export regressions outside the Windows sandbox: 4/4 passed, including 33 actual XML files and the unchanged CFE prerequisite/file-kind refusals. Successful CFE export was not exercised because no default application is configured. Corrected CommonForm rendering: 3/3 passed. Force-delete alone: 2/2 passed; form-delete then force-delete: 3/3 passed. After the refresh-order correction, the canonical nested-subsystem sequence passed 3/3 without a diagnostic hold and with no new SDK entries. Every focused run included its own log ratchet and clean fixture verification. Targeted extension, dimension and form NullPointerExceptions remained 0 before and after. These historical focused results do not replace the fresh full-run gate. |
| Full e2e | PENDING for the fresh bundle and 1,392-scenario registry. The latest preceding outside-sandbox run ended through failfast after 964 PASS, 37 SKIP and 1 ERROR (1,002 reported results; driver and all runner exit codes 1). First red: `modify_metadata::test_readd_is_idempotent_and_updates_use`; the assertion expected an internal `CommonAttributeContentItem` name although public readback correctly showed one `Catalog.Catalog` / `DontUse` row. The source contract is now corrected with all 14 tip cases and strict public-table assertions. Final fixture cleanliness was not certified because failfast interrupted final cleanup. The older 785-PASS/31-SKIP run was interrupted for the inherited CommonForm fixture; it is a separate historical run. The corrected fixtures pass 32 offline hygiene checks. Logs and fixture trees are preserved; no interrupted run is counted as a successful full gate. |
| Live profile audit | 241 read-only validations passed across direct and proxy endpoints: exact allowlists, disabled/unknown fallback, discovery and HTTP 400 for malformed paths. Explicit endpoints hide `enable_toolset` as documented. |
| Official MCP conformance | Passed on the operator's `/mcp` and `/mcp/profiles/default`: 8 passed, 24 expected failures on each; no new failures or baseline changes. Remaining isolated/profile/proxy surfaces are pending. |
| EDT log triage | Fixed external-project resync BM access, missing typed extension adoption, untyped register dimensions and three incomplete form fixtures. Corrected the runner's early refresh revert after a subsystem cleanup race; the focused canonical sequence then passed with no new SDK entries. The original derived item's identity remains unavailable. Preserve all 14 SDK severity-4 entries from the latest interrupted run, classified below. Targeted Dimension/Form/ObjectExtension NPEs, CMI/Hippo entries and new own-plugin errors were all 0 in that window. The fresh full run requires its own log verdict; SDK and plugin baselines remain unchanged. |

The interrupted run's SDK entries are retained separately from test outcomes:

| SDK classification | Count |
|--------------------|-------|
| Marker-storage lookup during CLEAN | 1 |
| Missing 8.5.1 runtime for automatic external-object dump | 1 |
| Deliberately malformed DCS input | 2 |
| JFace disposed-widget decoration | 4 |
| Background validation reading a removed CommonModule | 2 |
| FormEditor disposal reading its old BM model | 2 |
| Moxel listener modifying outside a transaction | 2 |
| Total | 14 |

The two Moxel entries at `14:31:21.960` and `14:31:26.143` retain complete stacks
through `MoxelControl.lambda$17`, the configuration event broker and BM async
listeners; neither stack contains an `fm.giper` frame. This is stack evidence,
not proof of every causal interaction or complete diagnostic delivery. The
classification does not turn failed SDK tasks into successful ones, whitelist
events or certify the interrupted full run. Evidence is preserved in
`runtime-release-monitor-final.json`, `runtime-release-sdk-interim.json` and
`sdk-release-final-classification.md` under `.claude/work/port-1-0-12/`.

The update-site archive is generated at
`mcp/repositories/fm.giper.edt.mcp.server.repository/target/fm.giper.edt.mcp.server.repository-1.0.12-SNAPSHOT.zip`.
The source version is `1.0.12-SNAPSHOT`; p2 artifacts use `1.0.12` plus the build
qualifier. No remote push, merge or release publication is part of this port.

The earlier canonical archive is
`.claude/work/port-1-0-12/dist-extension-sdk-final/MCP-EDT.v1.0.12.zip`, with SHA-256
`754b14751e4eeed0505cd074b102effd34d94d77742187e85b7091163a6e429a`.
The reactor's p2 repository ZIP has SHA-256
`d23fcad12687f5f78511494f8e64ca5bcbb9a4670f0a399af0142ab1a5cfbaf5`.
Its bundle is `1.0.12.202610051321`, with SHA-256
`31c60d6fbd7c7df7f6daaba069cb941fc73d7ee91de745ea49b15097f52d1e18`.
