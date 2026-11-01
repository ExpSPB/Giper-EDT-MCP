# Giper upstream porting guide and accumulated lessons

Updated: 2026-11-01 (Europe/Moscow).

The fresh 1.0.13 E7 full schedule completed 1,703 results: 1,652 PASS, 51 SKIP,
zero failure/error, with all sixteen original models, complete diagnostics and
application/default states restored. All 88 pinned files matched on each stand;
ignored settings additions limit the physical-keyset claim. SDK logs remain
nonzero. The observer actually exited 1 after one preserved unstable read, while
independent generation/counter coverage passed. The detailed bounded result and
coverage gaps are in the [1.0.13 port record](e2e/port-1.0.13.md).


Current 1.0.13 orchestration corrections: generated files can still be validation
inputs. `ToolDocsRatchetTest` reads `docs/tools/*.md` and `EDT-MCP.md` directly;
the DCS and form-finding ratchets also read generated pages. Checking only the
packaged GuideCoverageTest missed this dependency in pass7. Preserve that failed
build's manifest as incomplete for validation, regenerate documentation from an
isolated bootstrap when necessary, then include the pages, indexes and generator
in the next canonical input snapshot. A bootstrap is not release acceptance.

Capture application registrations and explicit defaults before test mutations,
alongside model/diagnostic baselines. The E3 final readbacks established that all
16 projects had no registrations or explicit defaults, but their first reads
were after earlier application calls. An empty mismatch list against unknown
baselines is not before/after equality. Keep unknown baselines explicit instead
of inferring their initial state from a clean final observation.

Bind a baseline collector's permitted preparation history to the complete,
successful canonical prime sequence. In E4, a helper rejected `revalidate_objects`
and `resync_to_disk` even though the selected refresh policy legitimately called
them. The correction verifies the exact sealed order, arguments and results,
followed only by the four expected application reads. Broadening a tool-name
allowlist alone would not establish that no unrelated mutation preceded capture.

Include the exact previous failure in the next early focused selection. E6's
strict form focus passed, but the later GetProjectErrors form-address case
failed during cleanup. E7 kept the original nine strict form identities and
added that exact case. The focus passed nine cases with one opportunistic skip;
the same regression and normal cleanup also passed in the separate full run.
Selection JSON order is not execution order: the wrapper uses registry order
and puts log ratchets after cleanup. Keep the three pixel captures in one
probe body and require fresh model/application and SDK evidence before the
independent full schedule.

This is the durable entry point for upstream ports, EDT compatibility work and
test orchestration. It consolidates the retained 1.0.8 profile specification,
1.0.10 port notes, 1.0.11 release notes, the completed 1.0.12 investigation and
the completed bounded 1.0.13 validation. The operating rules and conclusions
are recorded here so that previous chats are not required.

This guide does not certify a new build. Historical passes belong to the exact
source, artifact, SDK and runtime that produced them. Read [CLAUDE.md](../CLAUDE.md)
for code conventions and the applicable project skills for execution details.

## 1. Read order and evidence status

1. Read this guide, then the current version's newest checkpoint at the top of
   `.claude/work/port-<version-with-hyphens>/state.md`, if present.
2. Read the relevant detailed incident in
   [1.0.12 lessons](e2e/port-lessons-1.0.12.md). Its older sections retain
   intermediate failures and pending states; they are not the final verdict.
3. Use the [1.0.12 port record](e2e/port-1.0.12.md) for final behavior and
   validation, and the [compatibility audit](e2e/compatibility-audit-1.0.12.md)
   for the evidence behind SDK classifications. The audit was a read-only phase;
   its then-open findings were subsequently addressed where the port record says so.
4. Recheck current Git/source, installed JAR, SDK, process, workspace, port and
   profile identity before reusing any previous result or operating a stand.

Use these distinctions in every report:

| Status | What it establishes |
|---|---|
| Confirmed cause | A reproduction or direct source/runtime evidence explains the specific failure. |
| Implemented correction | The source contains the correction; name the tests and artifact that exercised it separately. |
| Precaution or mitigation | A defensive rule is adopted; it does not prove or cure the original cause. |
| Hypothesis | A plausible explanation that still needs evidence. |
| Accepted historical result | A bounded run passed its stated gates; no automatic acceptance of later changes follows. |

An old failed run remains failed after a fix. Recovery, a later ready state,
an observer's exit code or a successful unrelated test cannot convert it into
a passing run. Do not replace original baselines after a failure.

## 2. Compatibility and fork behavior that must survive

The supported baseline for this work is **EDT 2025.2.5**, observed product
**2025.2.5.2**, Java 17, Tycho 4 and Eclipse 2023-12 / 4.30. The original
1.0.13 request mentioned 2025.2.2; the user explicitly corrected it to 2025.2.5
on 2026-10-29. The same correction had occurred during 1.0.12.

The exact .5 core is `com._1c.g5.v8.dt.core` version
`26.0.1.v202604021910`. The mutable public `2025.2` channel was observed to
resolve the May `.6` core `26.0.1.v202605050943` during 1.0.12. A URL, cache key
or comment saying "2025.2" does not prove exact-.5 compatibility.

| Fork contract | Preserve during the port |
|---|---|
| Identity | `fm.giper.*`, Giper plugin/feature names, fork update and publication URLs, upstream copyright and AGPL attribution. |
| Documentation | Fork overview stays in `README.md`; upstream-derived product guide stays in `EDT-MCP.md`. |
| Named profiles | Per-profile allowlists, fallback reporting, path-bound sessions and notification isolation. New defaults must not widen existing stored profiles. |
| Profile exchange | Import replaces only the selected draft's content. Its ID/revision and other profiles remain unchanged. Apply/OK publishes; Cancel discards. Export includes the selected draft. |
| Exchange validation | Single-profile envelope, required typed fields, 5 MiB limit, UTF-8/BOM handling, unknown tool preservation, atomic writes, refusal of full-workspace documents and disabling `default`. |
| Preference migrations | Retain fork migrations, including v5/v6 history; introduce new migration numbers rather than replaying upstream numbers over existing state. |
| Transport and bridge | Preserve profile authorization, session binding, capability limits, structured-content policy, aliases and interruption/error semantics. Missing live policy must fail closed. |
| Proxy | Preserve profile-compatible routing and status-only refusal when backend status/surfaces cannot be validated. |
| Updates | Bump the plugin micro version for an installable update. A qualifier-only rebuild does not satisfy the project's p2 update convention. Check BOM and parent versions as well. |

The 1.0.8 specification established draft-only profile exchange; 1.0.9 exposed
a headless exchange controller for testing. The retained 1.0.10 review log
recorded a passing unit build but still had live/version/commit work pending at
that point. Do not use that intermediate log as a final release certificate.
The 1.0.11 notes record a further micro bump after 1.0.10 had been published.

## 3. Audit the complete upstream product

The 1.0.12 incremental comparison covered 24 commits, from `0be57656` through
`c99edaf3`, but a full-tip audit found omitted behavior in 17 production files
outside that delta. A later query failure exposed omitted extension fixture
content as well. A list of successfully ported commits is not completeness proof.

Before implementation:

- Record fork HEAD, upstream tip, the previous imported baseline and the target
  version. A merge base may already contain a newer EDT target: it did in 1.0.10.
- Audit both the commit delta and the complete upstream tip. Include production,
  Java/Python tests, fixture declarations, rights, forms, guides, preferences,
  build/CI scripts, transport, bridge and UI behavior.
- Keep a disposition table: ported, adapted, already equivalent, deliberately
  excluded, or unresolved. Give each exclusion a concrete reason and map tests
  to equivalent fork coverage.
- Separate target-platform migrations from useful fixes in the same commit.
  Do not wholesale merge upstream's newer Java/Tycho/Eclipse target or replace
  the fork's profile-aware transport with the upstream single-profile stack.
- Preserve byte-level evidence for bilingual changes. Comparing function bodies
  alone misses changed globals, imports and decorators.

For SDK-dependent changes, classify the actual dependency:

| Class | Required evidence/action |
|---|---|
| Existing API used incorrectly | Correct ownership, transaction, lifecycle or factory usage. |
| Changed signature/default/contract | Adapt to the exact target implementation and test meaningful arguments. |
| Absent API | Verify the missing symbol in the exact SDK; use a genuine supported equivalent or report the unsupported scope. |
| Native/runtime prerequisite | Check 1C executable, platform registration, infobase, launch config or optional plugin separately. |
| Uncertain behavior/SDK defect | Reproduce and retain bounded evidence; do not label it newer-only without proof. |

Inspect SDK jars, exported packages, source/bytecode, factories, enum constants
and method return types. Matching Java signatures do not prove matching argument
semantics or OSGi runtime wiring. The unavailable comparison enum in a restored
test was absent from both checked .5 and .6 SDKs; it did not prove a new-version
production dependency.

## 4. Prepare infrastructure without disturbing the operator

Use disposable EDT installations and separate configuration, user, workspace,
temporary and private fixture-repository directories. The operator's installed
EDT and endpoint are not a test stand. Recheck process ownership before stopping
anything; old PIDs are never authority. Launch background helpers hidden.

Do not run [local-edt-e2e.ps1](../scripts/local-edt-e2e.ps1) unchanged for isolated
work: the inspected script stops all `1cedt` processes, defaults to port 8765 and
imports fixtures from the main checkout. Likewise, a generic `taskkill /IM`
recipe must not be used on a machine with an operator's EDT session.

At intake check executable versions, actual Git/network access, dependency
repositories, disk/memory, sleep settings, port ownership and child-process
temporary-file access. Exercise the same execution context that will run E2E.

Observed on 2026-10-29: GitHub/API, Maven Central, Eclipse/EDT p2 and npm were
reachable; Java 17.0.13, Maven 3.9.14, Python 3.14.3, Node 24.18.1 and Git Bash
5.2.37 worked. Child Python could read its parent's UTF-8 temporary file; a
short loopback bind/connect/read probe passed. Sleep/hibernate timeouts were
disabled. These are historical preflight observations, not future guarantees.

Sandboxed Git DNS, CIM process reads and MSYS named-object creation failed;
the approved outside-sandbox executions passed. Earlier sandboxed Python
temporary directories produced export `WinError 5`, while the same scenarios
outside the sandbox exported 33 files. Diagnose execution context before
changing plugin behavior. Follow the current permission mechanism; a prior
successful escalation does not authorize every future action.

The pass9 XML replacement investigation also required matched execution tokens.
An initial sandbox probe reproduced an access denial, but it did not match the
normal-user canonical build. Separate normal-user arms measured both parent and
child as non-AppContainer and non-elevated: the task's D-drive Java temporary
directory failed on operation 8, while a new owned directory under the user's
C-drive Windows TEMP completed 5,000 operations. Code, JDK and inherited
TEMP/TMP were identical; only `java.io.tmpdir` and evidence locations differed.
This supports a task-local Java-temp mitigation, not a production fix or an
identified antivirus/filter cause. Keep the short socket-temp option independent;
do not change global TEMP, ACLs or security settings. Pass10 validated this
task-local environment in the unchanged canonical build. It does not identify
the pass9 failure's cause or establish a live-EDT mitigation; pass9 remains failed.

Optional live-infobase/native/YAXUnit/PII scenarios require their own prerequisites
and authorization. Installed EDT metadata support does not supply a registered
native 1C executable. A source-only external-object test does not prove EPF/ERF
binary generation. Record applicability skips explicitly.

## 5. Build against the exact SDK and identify every artifact

Use [compile.sh](../source/compile.sh) with `--edt-install` pointing to the owned
exact-.5 installation and explicit discovered JDK/Maven paths. The script uses
[edt-sdk-target.py](../scripts/edt-sdk-target.py) to snapshot the installed p2
profile and verify the canonical unit runtime afterwards.

`--target-definition` selects a target but does not by itself trigger the
installed-SDK post-build verifier. Run the equivalent verification explicitly
if using that route. Compilation against the floating channel cannot replace it.

- Capture source/resource/build inputs before and after the build. Retain JAR
  and archive hashes, OSGi qualifier, actual exits and Surefire results.
- Compare resolved SDK bundle IDs, canonical versions and bytes with the exact
  installed inventory; supplements must be explicit test-only dependencies.
- Key artifact hashes by classifier/path: plugin and feature jars may share a
  basename. Normalize valid OSGi versions (`1.77` equals `1.77.0`) before identity
  comparison. Both mistakes previously resembled cache corruption.
- Check OSGi package imports for API return types too. The CLEAN change compiled
  but failed at runtime until `derived.pipeline` was explicitly imported.
- Use APIs present in the target test dependencies. Mockito 2.23 lacked
  `verifyNoInteractions`; verified compatible assertions retained the no-call check.
- Redeploy only after a fresh build and check the deployed bytes. A redeploy
  without a build can install the previous JAR and validate stale behavior.
- Identify the proxy independently. Maven shade replaced the ordinary final
  JAR in 1.0.12; assuming a separate `-shaded.jar` broke artifact collection.

On the affected Windows Java 17 environment, a short dedicated
`jdk.net.unixdomain.tmpdir` removed reproduced selector initialization failures.
Keep this as a local environment setting, not a committed workstation path or
a universal diagnosis of socket failures.

## 6. Confirmed model and lifecycle lessons

The corrections below were implemented in the 1.0.12 port. Preserve them when
merging; their historical tests do not certify a changed implementation.

| Failure or wrong assumption | Correction and rule to retain |
|---|---|
| Raw extension construction left required state uninitialized. | Adopt the base Configuration through the target's typed adopter, retain language mappings, reject aliased/wrongly typed results, and assign fresh identities. |
| Raw register dimensions lacked required type defaults. | Use owner-aware factories for all four register kinds; preserve factory defaults and explicit caller overrides. |
| Omitted predefined characteristic type caused validator NPE. | Copy the owner's effective type, preserving qualifiers/references with a separate description; refuse malformed owners. Do not invent an arbitrary String default. |
| External-object resync treated a linked base Configuration as owned by its BM model. | Establish model ownership before BM access; retain repair/export of the external project's own roots. |
| Form fixtures lacked default command bars. | Restore the base/borrowed/common-form bars and pin declarations; inspect fixtures before changing the rendering implementation. |
| Extension-only query fixture was omitted. | Restore `Catalog.tests_ExtOnly`, its collection reference and six false role rights; audit the complete fixture set. |
| XML importer matched a four-String signature but passed the wrong meanings. | Use runtime version/base-project arguments; refuse unsupported nonempty legacy overrides and malformed explicit versions. Test nonempty arguments. |
| Form rename constructed SDK refactoring inside a BM read. | Retain identity/name across short reads, reacquire/recheck the handle, then construct the SDK refactoring outside those reads. |
| Deleting an editable member was incorrectly expected to fail because its configuration was locked. | Distinguish target `canDelete` from owner `canEdit` and top-level deletion rules. Keep locked-referrer and top-level refusals. |
| Disposing only a parent SWT shell skipped Moxel listener cleanup. | Explicitly dispose the owned non-null Moxel control before the shell, even if the widget is already disposed; dispose the shell in its own finally and retain cleanup errors. |
| Core resource deletion errors were logged and swallowed by SDK rename. | Observe the scoped synchronous core error and return truthful partial/unknown mutation evidence; do not report all refactorings as completed. The natural locking actor remained unidentified. |
| Missing project/policy services were treated as empty/permissive state. | Refuse unobservable clean-all state and keep the bridge status-only when live profile policy is unavailable. |

Keep live metadata owner/form resolution and edit-context capture in one
explicit BM read, then verify the same model before a separate form-content
read. E6 source review found that `MetadataScope` retains a live Configuration,
not a detached snapshot. The exact SDK can open short automatic reads for
individual getters; that does not make a multi-object traversal coherent or
exempt it from `CLAUDE.md`. `bmGetId()` itself is a plain field read, not evidence
that every outside getter is an illegal SDK operation. The adopted correction
passed canonical pass15 and fresh E7 focused/full validation; it is not an
established cause of the E6 derived-data block.

Distinguish model interfaces from their runtime BM implementation in tests.
Exact .5's `CatalogForm` interface does not declare `bmGetId()`, although a
runtime form can also implement `IBmObject`. Mock that additional interface
explicitly and access BM identity through it. The added test's pass12 compile
failure was a mock-typing defect, not proof of a missing production SDK API;
production compilation alone did not validate that test or accept the build.

Interruption tests must establish the caller and the actual wait boundary.
Pass13 preserved a single old Java failure after the fake application worker
reached its own 30-second ceiling; the source was identical to accepted
pass11. Exact .5 `JobManager.join` can consume an interrupt when
`LockManager.canBlock()` is false. The saved failure did not record that
policy or identify its consuming actor. Worker-entry notification alone
does not prove the caller has entered join. Use a dedicated non-UI caller,
observe its target SDK join/semaphore wait, check every latch result, and
assert return before releasing the worker plus the restored interrupt flag.
Keep unconditional INTERRUPTED-versus-TIMED_OUT checks and bounded cleanup;
do not accept COMPLETED, add a skip, alter a global lock listener, or label
an unmeasured historical path as the confirmed cause. The dedicated caller
in pass14 returned before worker release but failed its restored-flag check.
Restore the flag again in a finally after cancellation: exact .5
`JobManager.cancel` calls `JobListeners.waitAndSendEvents`, whose timed
`tryLock` can consume the flag without restoring it. Keep the flag visible
during callbacks and preserve cleanup exceptions. Two added regressions
consume the flag in a cancellation callback and check normal/throwing
cleanup. A running job can invoke that callback twice; record a positive
observation monotonically, rather than overwriting it with a later cleared
flag. The correction passed 39 focused tests, including both new cases, with
503 exact-.5 SDK bundles and all 1,791 captured inputs unchanged. Full canonical
pass15 also passed 12,328 cases (12,305 PASS, 23 SKIP). Fresh E7 live results
and their nonzero SDK/coverage limits are recorded in the current port record.
The pass14 failure did not reach its later outcome assertion or record the
precise SDK instruction that consumed the flag.

Use shared project/resolution/transaction helpers from `CLAUDE.md`. Resolve
synonyms by language code, never language display name, and do not hardcode a
Russian fallback. Test Russian and English names and SDK-localized diagnostics.
An SDK error's wording need not be English; assert its authored field, severity,
location and meaning without weakening the expected result.

## 7. Fixture restoration is a tested operation

The 1.0.12 investigation demonstrated two distinct cleanup failures:

1. Restoring a base form did not restore the adopted extension form, even after
   repeated refresh/resync and rewriting identical bytes. Restoring the base
   first and then CLEANing the extension restored the compared forms.
2. Incrementally recreating a deleted CommonModule introduced a stale-owner
   validator error. Base CLEAN followed by dependent CLEAN restored the original
   complete diagnostic multisets and model readback in repeated regressions.

**Set `MCP_MODEL_RESET_MODE=refresh` explicitly in the acceptance runner.** In
this harness, the name selects the guarded reset policy; it does not mean that
every reset is incremental. The accepted 1.0.12 launchers used this mode. The
`clean` mode performs an early Git restore before the readiness/export-quiet
barrier and is not an equivalent, more conservative substitute. The first
1.0.13 launchers mistakenly selected `clean`; recorded rename/reset windows
then contained missing-file indexing and removed-owner validator errors. A fresh
two-stand comparison ran the same two canonical rename tests: `clean` reproduced
both categories after Git restore and before CLEAN, while `refresh` produced no
new SDK ERROR. Both restored all four original models and complete diagnostics.
This supports the guarded policy at that scope; it is not a universal cure for
SDK validators or proof of the exact retained stale-owner task. Record and verify
the selected mode in each invocation, and reject an unintended mode before any
acceptance test runs.

Retain the harness's scoped policy: wait for readiness/export quiet before
reverting; ordinary confirmed writes can use incremental refresh, while committed
delete/rename and uncertain writes carry cold-reset evidence for the affected
projects. Restore dependent extensions after their bases. Keep that evidence
across test boundaries until every selected original model verifies.

E6 exposed an ordering hazard: a global settle after base refresh/resync can
abort before the later explicit extension reset is reached. The adopted narrow
mitigation applies only to a refresh-eligible base: require successful refresh
and strict resync, recheck freeze/abort guards, perform exactly one ordinary public
base CLEAN, then retain the existing global settle, explicit extension reset,
final all-model comparisons and cold-reset evidence. Optional/cold paths and
failure assertions stay unchanged. This workflow change has offline runner-test
evidence and passed the runner self-tests. Fresh E7 focus and full runs passed
the previous GetProjectErrors cleanup regression with base CLEAN before the
extension's CLEAN. One full-run base revalidation first refused active SYNC and
then succeeded on bounded retry; that refusal remains evidence. All sixteen
original models and complete diagnostics restored. This validates the mitigation
in those workloads, not a permanent cure or an identified E6 blocking actor.
Do not substitute blind
all-project CLEAN: exact .5 can autosave stale model state while stopping, which
is why refresh/import/resync comes first.

Before any mutation, persist the same-runtime original inventory, relevant
details and complete diagnostics for all required fixture projects. Compare
semantic diagnostic rows with duplicate counts, not presentation order. A
baseline from another JVM or after failed cleanup can hide corruption.

Preserve staged and unstaged changes. Disk cleanliness and a successful reset
response alone do not establish in-memory restoration. Preview/refusal checks
must compare with the immediately preceding whole-tree snapshot when the test
intentionally contains prior writes; equality to Git HEAD is a separate clean
baseline assertion. This distinction fixed a test helper without dropping its
unconditional byte-equality check.

Treat saved EDT `.form` sources and platform/Designer form XML as different
serialization paths. In the exact .5 SDK, `PageGroupExtInfo.showTitle` in the
source model is an EBoolean with default false; `showTitle851` is a separate enum.
The source Ecore converter rejects `auto`, rather than converting it to false.
Normal source serialization omits the unset false default. The invalid token's
parser recovery was not observed, so explicit `auto` and omission must not be
described as equivalent valid input states. The platform XML enum reader/writer
does not establish the behavior of the saved `.form` resource.

Keep raw bytes, Git-normalized source and rendered pixels as separate checks.
The E4 form probe restored identical pixels but EDT removed two invalid boolean
`showTitle=auto` fields and changed LF to CRLF. Removing the invalid properties
corrected that content mismatch. A path-specific LF Git attribute added after E4
could not control EDT saves: E5 still failed on modified Git status despite an
empty normalized diff. Its independent raw captures differ only by 203 CR bytes.
The harness compares status, normalized diff and untracked hashes; it does not
hash tracked raw bytes. Preserve those raw captures separately.

An isolated Windows reproduction established that the existing `* text=auto`
policy, a native CRLF source baseline and native Git cleanup preserve identical
raw bytes and clean status after the reversible title edit. The special LF rule
has been removed and this Windows working fixture uses CRLF. E6 strict form
focus subsequently passed with exact raw CRLF restoration and equal PNG/RGBA
results. Its full run failed later during fixture restoration and remains failed.
Fresh E7 later passed its full schedule with exact pinned raw restoration; ignored
`.settings` additions are reported separately. No global EOL setting changed;
Linux EDT save behavior was not measured.

A representation rebuild dispatches nested SWT work. Revalidate that the same
editor is still open BEFORE resolving its page again, then require the same
viewer/representation and a live control. The first guard checked viewer identity
but resolved it through the captured editor after the event pump. An E5 diagnostic
JFR recording found two new NullPointerExceptions at exact SDK FormEditor.findPage
line534; dispose sets its pages field to null at413. They came from the post-pump
resolver, immediately followed by reflective wrappers and the warning logger on
the same UI thread. Which closure actor disposed the editor remains unproven;
this does not retrospectively identify the earlier message-only E4/E5 warnings.
The editor-membership correction and deterministic regressions are implemented;
canonical pass11 passed all 14 refresh regressions and E6 strict form focus passed
8 cases with one opportunistic skip. All complete raw E6 generations contained
zero owned refresh-null, failed-refresh or `findPage:534` regression markers.
Separate native `FormEditorPage.dispose` failures remain bounded SDK teardown
observations; they do not exercise that owned refresh path. The failed full E6
run is not accepted by the focus result.

Bound diagnostic recording and decoding. JavaExceptionThrow records exception
construction, not every rethrow or cause-object identity. Preserve raw JFR and
check settings, time coverage, loss and relevant stack truncation. Printing all
recorded objects as JSON expanded a 1.84MB recording to 5.9GB in this attempt;
stream selected scalar fields with RecordingFile or use bounded text output.
Preserve the decoder incident as evidence, separately from runtime failures.

Run tests serially within one mutable fixture repository. Parallel shards need
separate installations/workspaces/private repositories and exact input parity,
including Java sources used by ratchets and declarations outside fixture folders.
Four stands were a throughput choice, not an EDT compatibility requirement.

A raw directory copy can also include old ignored logs and generated reports.
Compare the complete copy with its source separately from comparing tracked
files with Git HEAD. For any difference between those keysets, retain the exact
paths and their `git check-ignore -v` rules; do not broadly exempt source or
fixture files. A clean Git status alone does not establish complete copy parity.

## 8. Readiness, failure preservation and SDK logs

Seal all four original models with complete diagnostics and application/default
states in every fresh stand before test mutations. Bind application capture to
the complete actual ordered successful prime history, then exactly four reads;
E7 had 35 prime calls rather than E6's 34. Compare final sixteen models and apps
to their own same-JVM originals. Model equality does not imply app/default equality.

Keep exact schedule multiplicities: E7 had 1,697 unique identities but 1,703 results
because two log ratchets ran in all four shards. Match every skip to its actual
reason and frozen source. Optional infobase/service/preset matrices, missing
fixtures and a known positive-test gap are coverage limits, not passes. An aborted
old shard can hide cases that would ordinarily skip; compare identities and reasons,
not raw ordinary-skip totals alone.

A console line beginning `| ` is not automatically trace. E7 emitted eleven
Markdown table rows inside one `get_tool_guide` WARN. Subtract only occurrences
byte-matched to an independent trace, retain the source-bound message rows, and
require full entry/severity, parent/nested Counters and session prologues to agree.
The adopted helper's refusal stayed failed; the correction was a separately
reviewed local audit, not a running-producer edit or arbitrary line filter.
Label hash representations: one warning had 515 raw bytes (`43fb11115e...`) but
509 normalized event bytes (`fa773c217e...`). Neither hash alone classifies it.
Do not infer byte framing from terminal rendering; the native provisioning packet
had raw CRLF, not the initially hypothesized CRCRLF. Its out-of-band progress line
belongs to that producer channel, not a reason to weaken the GUI log parser.

Archive bytes read through an open descriptor even if the post-read path changes
or raises OSError. Keep unstable/incomplete flags and the real process exit. E7's
one provisional sample was fully contained in later stable same-inode archives,
including its last complete entry and all E/W multiplicity. The watcher still
exited 1; independent coverage passed. A later stable sample does not automatically
clear an earlier failure or classify a future read. Preserve precise stat tuples
when diagnosing which comparison changed; the E7 tuples did not identify that step.

Once a receipt hash has been announced, preserve those bytes and add a new
supplemental receipt. A launch side effect observed through fresh JVM/socket
identity is separate from a still-pending wrapper exit. Retain the original tool
session until its actual exit arrives; an inherited Tee handle remains a hypothesis
unless measured. E7's original wrappers finally returned zero after owned shutdown,
while forced Java stderr reported exit=-1. These are different outcomes.


`ready`, a completed lifecycle callback, an empty queue, Git cleanliness and a
successful HTTP response prove different things. In the exact SDK,
`isAllComputed()` could be true at `SYNC` before MD became ready. CLEAN now
requires the post-build stage, ready MD/FORM segments and checked predicates
within its budget. Public observations remain non-atomic and do not prove
private queues have drained or that every computation succeeded.

In the exact E6 SDK, `isIdle()` accepts pipeline `BLOCKED` or `EMPTY`, while
`isAllComputed()` requires `EMPTY`. Idle therefore does not mean computed or an
empty queue. E6's sequential public point returned idle=true, allComputed=false
and a DTO reporting `BLOCKED`/`SYNC`/modelSyncActive=false. Even the DTO reads stage
before status, whose getter can refresh cached pipeline state; do not infer a
single atomic instant, private flags or a blocking owner from that tuple. One
later ordinary base CLEAN restored the measured models and diagnostics, but did
not establish the original cause or a permanent cure.

A transport deadline does not stop an SDK request. The SDK's completion argument
was not an end-to-end deadline either. After a timeout, freeze new mutations and
resets; retain logs first. Wait for demonstrated quiescence before opening model
or source snapshots that could themselves introduce Windows sharing locks.

At the first failure:

1. Record exact source/binary/runtime identity, invocation, real process exits
   and the first failing/aborted status. `RESET-FAILED`, `CALL-TIMEOUT`, partial
   runs and abort-induced skips are failed gates even if most cases passed.
2. Preserve raw current/rotated SDK logs, runner reports, calls, fixture trees,
   index state and relevant observations before reset/restart. State unreadable
   files and incomplete preservation explicitly.
3. Diagnose one bounded reproduction and review the correction. Do not edit a
   running/frozen producer, rewrite its report, extend timeouts blindly or
   weaken assertions to obtain green output.
4. Recover through the reviewed scoped reset only after preservation and safe
   quiescence; verify original models, complete diagnostics and fixture bytes.
5. Run the focused regression on the corrected candidate before another full run.

For native file-write failures, retain the exception subtype and both staged and
target bytes before the writer/test teardown deletes them. A tool's message-only
error cannot establish the native code or actor. A WORK-only observer added solely
after the original IOException captured the pass9 failure without changing its
success path or retrying the write. The original target stayed intact. Sequential
post-failure DOS attributes and ACLs are not an effective-access calculation or
proof of the state at the failed syscall; absence of a readonly flag does not
identify the reason for a native access denial.

Distinguish sandbox denial from OS sharing locks. Historical pre-stop archives
excluded only a verified hash-bound list of zero-byte lock files and explicitly
declined complete byte coverage; post-stop archives included all bytes. This is
not permission to ignore every `.lock` file. Raw Git index changes can be stat-only,
but preserve both copies and compare entries, modes, stages, flags and extensions
before making that bounded claim.

Review every SDK ERROR/WARN container and nested status across all current and
rotated raw generations. Preserve Counter multiplicity, including duplicate
occurrences; a deduplicated alert stream is not a complete log count. Classify
each entry using actual source, call and lifecycle evidence. Absence of
`fm.giper` frames does not exclude invalid state created by the plugin. A negative
test allowance must bind its exact call and throw site, not broadly whitelist
an exception class or message. Keep queued UI errors without a completed-call
overlap explicitly unattributed.

Check observer process health independently of log activity: an unchanged journal
is not a heartbeat. Preserve an unexpected disappearance and unknown actual exit;
never rebaseline away new full-run events. E6's authorized successor started
34.720869 seconds after the first full body. Exact raw-prefix coverage preserved
the gap's bytes, but did not prove uninterrupted sampling or the predecessor's
exit. Close an owned observer only after its final raw coverage check and fresh
full process identity, using its unique STOP marker; consume the actual session
exit as well as checking the finished artifact.

A normal application-readback helper can correctly refuse an original failed
runner receipt before any RPC. Preserve that refusal. A separately reviewed
diagnostic collector may compare the original application/default baseline after
recovery, but must label itself diagnostic and keep suite acceptance false.
Do not fabricate a green runner receipt or bypass the normal guard.

## 9. Orchestration and evidence-format traps

| Observed trap | Required handling |
|---|---|
| UTF-8 bytes decoded as CP866/CP1251 damaged bilingual globals and guides. | Use explicit UTF-8 or byte copies; verify whole-module AST plus raw changes, including globals/imports/decorators. |
| PowerShell 5 native redirection emitted UTF-16LE. | Retain raw logs and decode their actual BOM; do not assume every log is UTF-8. |
| Git/source capture omitted or misread stderr. | Preserve both stdout and stderr with the actual exit. Classify channel diagnostics separately; CRLF warnings are not source diffs, and raw CRLF worktree bytes need not equal normalized LF HEAD bytes. |
| PowerShell 7 converted ISO JSON values to DateTime automatically. | Validate the persisted scalar contract; use `ConvertFrom-Json -DateKind String` when an epoch guard expects strings, rather than passing an auto-converted value to a string-only parser. |
| System OSGi API classes had no bundle from `FrameworkUtil.getBundle`. | Keep a narrowly explicit identity route for the exact framework API classes: pinned owned framework JAR CodeSource and resource bytes. Null/wrong CodeSource fails closed; every other SDK class retains its mandatory bundle identity check. This is not a generic fallback. |
| Console and workspace event hashes differed at trace and rotation boundaries. | E6 console mixed CP1251 Eclipse statuses with UTF-8 trace bytes. Separate only byte-exact matches to the independent trace, preserving multiplicity; split at `!SESSION` prologues byte-bound to raw workspace generations. Then compare complete nested E/W Counters without temporal filtering. |
| A pre-write LF hash differed from the saved CRLF manifest. | Hash final raw bytes, not an intended in-memory representation. |
| JSON array wrapped in `@(...)` became nested in PowerShell 5. | Validate parsed shape and types; assign `Get-Content ... -Raw` through `ConvertFrom-Json` directly when appropriate. |
| Regex replacement mishandled Windows backslashes. | Use literal path operations when literal replacement is intended. Syntax validation is not runtime validation. |
| Console and JSON summaries used different keys. | Inspect the persisted schema before verifying results; require actual child exits and expected test identities/counts. |
| An in-memory proof used tuples while its saved JSON contained arrays. | Compare the complete persisted JSON representation, preserving fields and scalar types; test the actual save/load round trip. Do not remove mismatching fields. |
| Catching `BaseException` recorded normal `SystemExit(0)` as failure. | Do not intercept normal exit as an exception; preserve failed recorder outputs and correct under a new run identity. |
| Observer processes exited zero with incomplete JSON. | Require observation-body flags, identities, errors and all expected computed points; delivery is not acceptance. |
| A tool wrapper printed only stdout and lost the running command's session ID. | Retain the complete execution result, including session ID and exit status. Poll the yielded operation instead of repeating it. If the exit is lost, record it as unknown and independently audit any prepared state before adopting it. |
| Guide generation saved an error as documentation or hit a mapped-file write failure. | Validate every guide; use reviewed isolated generation when needed and retain failed in-place evidence. Only the exact documented profile-denial case may preserve a valid existing guide. |
| Long-lived SSE streams occupied all 32 proxy HTTP workers. | Retain the separately bounded SSE executor. Verify admission/POST/health/protocol behavior and the deployed proxy independently. |

Delegate implementation by disjoint files or isolated checkouts. The orchestrator
owns integration and evidence; reviewers must check actual code/SDK facts and
report unverified scope. Run a real compile inside review iterations when API
uncertainty is blocking progress. Preserve source and producer hashes so review
and execution refer to the same candidate.

## 10. Required completion gates

Follow [ready-to-deploy](../.claude/skills/edt-mcp-ready-to-deploy/SKILL.md)
in order, with [build/test](../.claude/skills/edt-mcp-build-test/SKILL.md) and
the [canonical E2E guide](../tests/e2e/SKILL.md). Stop at the first red gate.

1. Review hygiene, fork invariants, upstream disposition and meaningful tests.
2. Canonical exact-SDK build/unit gate and independent input/runtime identity check.
3. Documentation/schema/guide consistency and the required version bump.
4. Owned live redeploy with verified plugin/proxy bytes and fresh fixture baselines.
5. Profile authorization, golden surface and targeted regressions.
6. Complete canonical E2E schedule, justified skips, final fixture/model/diagnostic
   equality and per-entry SDK-log review.
7. Official conformance for all applicable plugin/proxy/default/explicit-profile
   surfaces against the committed baseline; do not expand it to hide regressions.
8. Independent final review and clean intended diff; local commit by default.

Revalidate only the affected layers when source identities prove others unchanged.
The final proxy-only 1.0.12 fix did not require rebuilding the unchanged plugin;
it did require proxy build/deployment, profiles/protocol and subsequent model/log
evidence. Do not apply this shortcut when packaged resources or plugin inputs change.

## 11. Historical acceptance and unresolved limits

The completed 1.0.12 local commit was
`291155ea90c53e9b31aa4f83d18960c36da21379`. Its accepted plugin was
`1.0.12.202610060609`, JAR SHA256
`d2554b4369aac6cf6cd44fb5fcdc7f60727282f3211ed3345d65e606d8a3fb24`.
Historical evidence records 9,138 Java cases (zero failures/errors, 19 skips),
250 passing proxy tests, 1,358 E2E passes plus 42 reviewed skips, 241 profile
checks, two golden checks and five conformance surfaces. All sixteen original
model/detail/complete-diagnostic comparisons matched. These counts are evidence
for that schedule, not hardcoded expectations for a later suite.

Its SDK log was **not empty**: 103 retained severity-4 entries were individually
classified at that scope. A failed SDK validator check remained recorded with
unidentified initiating object/writer. Acceptance was not a blanket waiver for
future errors or proof of a cure.

The historical extension status stuck at Updating has **no demonstrated root
cause or permanent cure** in this work. On 2026-10-06 the user excluded further
investigation; restarting EDT and retrying the update was the accepted workaround.
New concrete failures still require preservation and triage. Neither CLEAN
corrections, rename barriers, legal export overlap, Moxel cleanup, SSE starvation
nor similar upstream issue symptoms establish that historical cause.

After completion, four owned test stands and the proxy were stopped and their
temporary trees removed after verified preservation. Old PIDs and paths are
historical. A later archive compaction retained non-cache bytes and manifests
while removing documented duplicate SDK caches; old whole-archive hashes do not
mean those original ZIPs still exist. Inspect the latest cleanup checkpoint.

The 2026-10-29 1.0.13 check verified infrastructure, retained release hashes and
an exact installed SDK snapshot (1,260 active bundles / 1,721 artifacts). It did
**not** build 1.0.13, boot a new stand or run E2E/conformance. The old stands still
needed recreation at that checkpoint.

## 12. Keep this memory usable

For each new confirmed lesson record the symptom, bounded evidence, cause or
remaining uncertainty, correction, prevention rule and exact validation scope.
Update this guide when a conclusion changes; retain failed evidence and explain
supersession in the detailed incident record. Do not silently turn precautions
into fixes or old hypotheses into facts.

Keep reusable conclusions and release-level outcomes in tracked `docs/` files.
Keep machine paths, manifests, raw reports and current task checkpoints under
`.claude/work/`; that directory is ignored and may be absent in another clone.
Its absence must not make the operational lessons above depend on chat history.
After each completed port, publish the durable conclusions here and link the
version's detailed report before cleaning disposable stands.


## Confirmed 1.0.13 infrastructure observations

- Quote each Maven `-D` argument in PowerShell. A local argument probe reproduced
  `-DnewVersion=1.0.13-SNAPSHOT` arriving as two arguments,
  `-DnewVersion=1` and `.0.13-SNAPSHOT`; the fully quoted argument arrived intact.
  The first version command left a partial version change before a Windows sharing
  violation. Check every reactor parent/project version and bundle/feature version
  after any failed version command; do not just replace remaining old strings.
- Java 17's Windows NIO pipe may use Unix-domain sockets. The same short owned
  `jdk.net.unixdomain.tmpdir` precaution applies to forked Maven tests and standalone
  proxy processes, not only EDT. An ordinary-temp proxy run produced 80 connection
  errors with `UnixDomainSockets.connect0`; the identical source passed all255
  tests with a short owned temp directory. Preserve the failed reports first.
- The exact installed-SDK snapshot is a compilation target, not a complete EDT
  product repository. A fresh p2 install from it could not resolve native
  `com._1c.g5.v8.dt.feature_root` units. A copied shared p2 profile separately
  encountered an optional Workmate dependency and then `Profile not locked`.
  Removing surrogate/cache profile properties did not fix that lock. A later
  installation succeeded with a separate director configuration, p2 data area
  and runtime profile, while provisioning the copied target DefaultProfile.
  This is a confirmed working setup; re-entry into the target profile through
  the extension-location repository is the stack-supported explanation, not a
  claim that every profile-lock failure has that cause. Keep these diagnostics
  separate from model/test failures. Optional Workmate was removed only from
  that disposable copy; its integration is not covered by this result.
- The proven bundle-overlay approach can bootstrap an owned exact installation:
  copy static installation files, use fresh runtime caches/workspace/user/temp,
  add exactly one hash-verified plugin row to the copy's `bundles.info`, and
  verify reported EDT/plugin identity. This proves loading of the overlay; it
  does not substitute for p2 installability verification of the final archive.
- Full-tip comparison still matters after a clean incremental merge. The 1.0.13
  audit found earlier omitted proxy behavior (default connection ceiling and
  refusal of JSON-RPC initialize errors inside HTTP200). Classify unchanged-from-
  previous-upstream fork differences too. Strip comments, whitespace and BOM for
  the inventory, but preserve literal strings and inspect actual code differences.

These are observed infrastructure/merge lessons, not a final 1.0.13 release verdict.

- A short Java temp directory inside the repository changes Git-discovery test
  assumptions: walking parents of a supposedly non-Git temporary directory
  correctly finds the project repository. Negative resolver tests must use a
  genuinely isolated search boundary; positive nested-repository cases must
  remain. Do not change production discovery to satisfy an invalid fixture.
- JDK17 HttpServer implicitly drains unread request bodies while closing an
  exchange. Sending 413 only after an unbounded drain blocks on an incomplete
  upload. `available()` is not a reliable socket-drain bound, and
  `readNBytes(limit)` can perform a zero-length read that waits for the next
  chunk header. The proxy now caps reads with a positive-length loop, flushes
  413 first, disables the implicit drain by default at startup, and uses a
  bounded, cancellable drain executor. Tests cover complete oversized bodies,
  missing body/chunk terminators, exhausted drain capacity and valid keepalive.
  An explicit JVM drain-property override retains the operator's setting and
  falls outside the default bounded-close guarantee. This is JDK17 evidence,
  not a blanket claim about other HTTP implementations.
- Documentation can legitimately describe the literal `[OUTPUT TRUNCATED]`
  marker. Reject an actual standalone truncation notice, not every inline
  mention. Preserve fail-closed handling of empty, malformed and unclassified
  resource failures, and generate every guide before writing the output pages.

- On the exact Eclipse jobs 3.15.100 runtime used by EDT 2025.2.5, scheduling
  event dispatch can consume an interrupt before the caller reaches `Job.join`.
  A worker-started latch does not prove the caller has finished `Job.schedule`.
  A separate exact-.5 diagnostic observed a deliberately pre-interrupted caller's
  flag clear immediately after `Job.schedule`. Separately, the original handoff
  failed with its caller waiting in `JobManager.join` and a cleared flag; that
  failing iteration did not measure where the flag was consumed. Test cancellation of the
  bounded wait only after observing that actual wait, retaining the same timeout
  and no-late-write assertions. This corrects the test's handoff; it does not fix
  the SDK's earlier scheduling-window cancellation limitation or promise that
  native platform work always responds immediately to interruption.

- Distinguish inherited p2 references from new runtime-owned ones. Sanitize every
  retained profile snapshot before cloning, preserving its original bytes. EDT's
  first boot may then create surrogate/shared/cache-extension properties whose
  targets are inside that stand. Post-boot checks should validate exact ownership
  and strictly contained file URIs (including reparse-point checks), rather than
  reject every property name. This does not justify allowing operator caches or
  weakening static clone checks.
- Runtime-owned p2 references do not prove that the latest local profile is a
  complete provisioning input. In pass8 the previously booted template had
  partial surrogate snapshots with 37 local IUs, compared with 1,926 in its
  complete pre-boot snapshot. A later director operation returned exit 0 but
  left a dangling old server row in bundles.info; the old JAR was absent. The
  complete native action sequence is unresolved. Check all active rows against
  actual bytes and the complete profile, not only the director exit code or
  newest JAR. Preserve profile history before a supported native recovery.
  Keeping a provisioned template cold and running tests in separate clones is
  the adopted precaution; it is not proof of this incident's root cause.
- Native p2 revert does not promise replacement of every unchanged IU's XML
  metadata. In the exact SDK, IU equality uses id/version and the operation
  generator excludes that intersection from reinstall work. The reviewed
  recovery restored all target keys, profile/IU properties and roots, while
  retaining the complete pre-operation metadata of all 1,923 unchanged IUs.
  Compare restored units with the historical target and unchanged units with
  their sealed immediate prior values; compare every field and all active bytes.
  Do not normalize arbitrary differences away. The original overly strict
  verification failure remains recorded separately from this contextual result.
- After an unexpected restart, verify boot time, fresh process identities,
  source/input hashes, artifact hashes and saved completion receipts before
  resuming. An old PID or prepared command is not execution evidence. Completed
  matching build results can be retained; an interrupted or unstarted runtime
  gate must not be inferred from them. Preserve the failed stand and its logs.
- Select the verified PowerShell executable explicitly for nested helpers. In
  this environment `powershell` selected Windows PowerShell 5 with an inherited
  module path from PowerShell 7; `Get-FileHash` was unavailable. The same guarded
  script ran correctly under the verified `pwsh`. Retain the failed preflight;
  do not relax path or artifact checks to get past a shell/runtime mismatch.
- Exercise PowerShell guards with actual empty and occupied process/listener
  results. Under strict mode, an unwrapped function returning no objects has no
  `.Count`; use `@(Get-... ...).Count`. Literal slash conversion should use
  `String.Replace`, since a single backslash is an invalid regex pattern.
  Parsing alone missed both errors in the documentation bootstrap's preflight;
  both refusals happened before the new stand directory was created.
- Mockito restubbing can invoke an existing Answer during `when(...)` with
  matcher placeholder values. One default-recovery test failed before entering
  its operation because that Answer looked up a null project in ConcurrentHashMap.
  An isolated reproduction with the actual SDK/Mockito classpath established
  the cause; `doReturn` avoids invoking the old Answer. Retain the real recovery
  assertions and execute the corrected test, rather than treating a setup
  exception as a production failure. Disabling HotSpot's omitted-fast-throw
  stack optimization in the isolated diagnostic recovered the missing stack.
- Two canonical builds encountered a merge-rules sibling-file atomic replacement
  failure in different test methods, with unchanged production/test source.
  The returned two-path message loses the original IOException subtype and
  native error code. Closed-stream source review and 2,000 successful isolated
  diagnostic invocations did not establish its cause. Do not attribute it to
  antivirus, sharing locks or access denial without evidence, and do not weaken
  identity/content guards or add broad retries on that assumption. Preserve the
  original exception and file state before cleanup if it recurs; a later passing
  run does not explain the earlier failures.
- A private E2E snapshot must include the explicit canonical build-input
  manifest as well as current Git files. The first 1.0.13 capture omitted 168
  ignored generated check-guide copies that were inputs to the accepted build.
  Preparation refused the mismatch before creating a stand. A new immutable
  snapshot included those exact inputs; canonical source files remain the root
  `checks/` directory, so these generated copies are not added to the main Git
  history.
- Version-sensitive wizard oracles must follow the exact installed SDK. The
  first 1.0.13 focus correctly created an extension root with `defaultStyle`,
  but a stored fixture from another SDK omitted it. EDT 2025.2.5's native
  adopter includes that state above compatibility 8.3.12 and default-role
  states above 8.3.13. Verify the native adopter and wizard path before changing
  production behavior. Compare the complete actual state map against an
  explicit version-specific expectation; do not filter out unexpected states
  merely to pass. The corrected oracle has six portable regression cases.
- A failed UI callback and the component that caused its stale widget are
  distinct facts. Three 1.0.13 focus records matched the exact historical JFace
  navigator-decoration stack during CLEAN resource loading. Framework source
  located a queued label update reading a disposed mapped item; the item's
  disposing actor and map-retention mechanism were not established. Supported
  `IProject.build(CLEAN_BUILD)` use, matching old stacks and restored models
  justify only a bounded per-event disposition, not a universal SDK-only cause
  or permission to ignore later errors. Keep these records separate from the
  proven Moxel listener-cleanup defect and do not broaden the log baseline.
- Preserve the failed test epoch even for a test-only correction. Capture the
  scratch project and original-model readback before stopping its owned JVMs;
  retain the entire failed stand. Seal the corrected source in a new epoch and
  record new process identities and model baselines. A readback or a corrected
  oracle does not retroactively turn the failed execution into a pass.
- Do not infer heap exhaustion from the exact .5 CPU-overload warning's
  `Memory ... used` field. Its SDK implementation computes `maxMemory - freeMemory`,
  which includes uncommitted capacity up to Xmx; the warning itself is triggered
  by a delayed watchdog wait. During 1.0.13 validation it printed about 4 GiB;
  a later guarded `jcmd GC.heap_info` showed about 217 MiB actually used. The SDK
  itself requests GC after that warning, so the later snapshot does not measure
  heap use at the warning's timestamp. Inspect actual metrics and the cause
  before changing heap limits; this observation does not rule out another
  memory failure.
- Reflection needs exact-runtime tests as well as compilation. A synthetic event
  exposing a newer factory let the form-render tests pass while exact .5 lacked
  `NativeRenderEvent.buildUpdateEvent()`. The native .5 renderer uses its public
  no-argument constructor. Check the actual SDK class and caller; fall back only
  for an absent method, not when an existing factory throws. Selection-event
  constructors have a different contract: proving plain refresh does not prove
  empty/multiple selection restoration or safe ID width conversion.
- Launcher `reload` is not read-only in exact .5. It emits application deletion
  events globally and clears explicit defaults even when a duplicate registration
  is then refused. Check the selected default after refusal and across unrelated
  projects, not only registration IDs. An automatic single-application fallback
  is not a persisted explicit choice. The native settings store also caches the
  application object, so restoring only preference bytes or an old object is
  insufficient. The 1.0.13 correction is implemented and its Java tests pass in
  canonical pass8. Its bounded worker, cancellation, lock-order and truthful
  partial-result contracts have been reviewed. E6 subsequently passed the
  synthetic registration/default-preservation cases, including unrelated projects,
  and compared measured application/default baselines. Those bounded cases do
  not accept the failed full E6 run or a changed candidate.
- A missing `Configuration.xml` is not necessarily an invalid XML-import source:
  the current converter classifies that directory as external objects. A negative
  case-collision test must use an input that actually fails before the native
  importer on case-sensitive hosts too. A deliberately malformed, owned XML file
  provides that refusal; an unsupported legacy parameter only tests the earlier
  parameter guard. Verify refusal ordering before executing a proposed test fix.

- If documentation generation fails while opening an output file, retain the
  exact error and before/after source inventory before using another location.
  In pass8 the unchanged canonical generator succeeded in a fresh owned copy of
  all pages and indexes; generated bytes matched main and no documentation was
  copied back. The original Errno 22 cause remains unknown. Isolated output is a
  precaution. Publish a changed golden only after fresh runtime/schema proof,
  exact expected-diff review and source checks. An exclusive sibling file and
  atomic replacement avoid partial truncation; retain it on refusal rather than
  adding a blind retry.
