# Project memory: causes and precautions from the 1.0.12 port

Recorded on 2026-10-05 (Europe/Moscow). The user corrected the target from
EDT 2025.2.2 to **EDT 2025.2.5**; the tested runtime reports 2025.2.5.2.
Preserve Java 17, Tycho 4 and Eclipse 2023-12 compatibility, Giper identity,
named profiles, path-bound sessions and fail-closed authorization.

This is a durable record of evidence and lessons, not a release approval.

On 2026-10-06, after the reboot pause, the user explicitly removed investigation
of the historical extension status stuck at Updating from this port scope. Their
accepted operational workaround is restarting EDT and repeating the update.
Treat it as non-blocking; do not claim a demonstrated underlying cause or
permanent cure, and do not spend further port time diagnosing it. New concrete
test failures still require their own evidence and triage.

"Fixed" below means implemented and checked at the stated scope; it does not
mean that the final full E2E and conformance gates passed.

The current compiled candidate is `1.0.12.202610060609`: Root18081 canonical
build exited 0 (`772362`) and its lean verifier exited 0 (`d02edb`), with 9,138
Java tests, zero failures/errors, 19 skips, 377 reports and 860 unchanged
compiled inputs. Its JAR SHA256 is
`d2554b4369aac6cf6cd44fb5fcdc7f60727282f3211ed3345d65e606d8a3fb24`.
The exact installed SDK proof retains 503 SDK bundles and ten supplements.
Only the reviewed vendor test and packaged delete guide changed from 0440;
production support guards and Java remained unchanged. Initial profiles241,
docs102, prime sixteen original models/complete diagnostics and V9r3 public
points passed; golden passed2/2. The first vendor focus failed with zero passes
and thirteen abort skips (Root71964/1/`3d5e53`), before any subsequent body.
The fresh full run, final original-model readback and post-reboot proxy
conformance and final independent dynamic review subsequently passed as
recorded below. The exact 530-file capture and independent source review passed
(`a478347b`), with no blocking static findings; the local commit result is
recorded in the latest work checkpoint.

The first focus exposed corruption in three module globals, `RU_CATALOG`,
`RU_ATTRIBUTE` and `RU_CLOSED`, during vendor candidate preparation. The old
correct v1, direct Git bytes and retained originals have the proper Russian
values; v2 contained mojibake. Explicit UTF8-byte decoding as CP1251 reproduces
those three values, but the historical process locale was not captured. Function
body equality missed this defect because names were resolved through globals.
Compare the whole module AST, including globals, imports and decorators, and
raw changed lines against the approved original. Use explicit UTF8 reads/writes
or byte copies; ASCII Unicode escapes protect fixed bilingual tokens.

After failure preservation (`c390a0`, proof `34bcb`), same-JVM public observations
passed (`9a45db`) and the four original models/complete diagnostics matched
(`2b04b7`). Root adopted only the three-global correction (`fda054`, reviewed
`262ebebd`); independent AST/raw checks confirm only lines40–42 changed and all
other nodes, decorators and the reviewed editable-member body stayed intact.
The compiled860 inputs and 0609 JAR are unchanged; no reset, restart or JAR
rebuild was performed for this test-only correction. The failed focus remains
failed and does not certify the corrected source; a new live run is required.

The next focused run (Root64977/1/`541e45`) passed eleven cases, then aborted
with one RESET-FAILED and two abort skips. The newly authored editable control
successfully compared all fixture bytes before/after delete preview, but its
helper additionally required equality to Git HEAD after its own successful
synonym change and member creation. Confirmed member deletion had not run.
Compare a preview or refusal against the immediately preceding whole-tree
snapshot when a scenario intentionally carries prior writes; HEAD equality is
a separate clean-baseline assertion. The WORK-only reviewed candidate preserves
unconditional complete byte equality, defaults `require_clean=True` for all
existing locked controls, and uses False only for the two new post-write checks.
It retains the strict preview, actual delete/disk/model and locked-referrer
assertions. After failure preservation (exit0 `591def`) and same-JVM public
observation (exit0 `fb19e3`), scoped recovery83348 exited0 (`d6149d`), restoring
the four original models/complete diagnostics with SDK0 before adoption and
resetting only SupportedConfiguration. Root then adopted/synced the reviewed
test-only candidate (exit0 `b62830`, current source `a1c3db17`, patch `79e27e`)
to four one-file private commits. V11 compilation and public attachment passed
(`224179`, `ae7e64`), while its receipt explicitly leaves
`currentSuiteSourceChecked=false`: SDK readiness and current canonical Python
identity are separate checks. The runner must enforce the latter. The fresh
focus then passed all fourteen cases without skips (Root21828/0/`6dd72e`),
enforcing the current `a1c3db17` source. Its complete four original inventories,
details and diagnostics matched, fixtures were clean, all four V11 computed
points passed, and both observed SDK windows had no severity4 entries. Actual
Root receipt and separate auxiliary gate passed (`ccf036`, `31ef83`); strict
proof `e694bb1c` retains that scoped evidence. Recovery or observations alone
were not used to manufacture those passes. Full29642 then completed with
exit0 `df1ae6` in `e2e-final-exact5-20261006-vendor-baseline-run1`: 1,358 passes,
42 skips, exact1,400 results/1,397 canonical identities and four child exits0.
Result integrity passed (`cefe30`); the same-run original16 inventory/detail and
complete diagnostic line-multiset readback passed (`ba7abd`), with all projects
ready and fixtures clean. Final V11 public points and the separate all16 check
passed. Independent pre-conformance review accepted the42 actual skip reasons
and classified SDK coverage. At that checkpoint, conformance and release
remained pending. Both prior
failed focuses and the failed0440 full remain immutable historical evidence.

The full SDK log is nonzero and must not be described as SDK0. In addition to
the JFace/negative-DCS/native8.5.1/retained own-negative/asynchronous FormEditor
entries, one BSL validator failed during CLEAN restart. Retained exactSDK source
and timeline evidence confirm a repeated transaction-handle validity transition;
the precise object, initiating writer and causal attribution remain unknown.
The final original16 now match completely, which does not clear the logged
failure, prove private queued-work completion or identify/cure the historical
hang. Per-entry pre-conformance review now accepts the91 retained severity4
entries and coverage at this concrete scope, with a separate final restored
validator packet (`c100f615`). Its decision is
`pre-conformance-sdk-review-decision-v2.json`, SHA
`b4bf6111354428697683e9bcb13dd9c7292d0c4afc0fd9d82968260c7c41e4f0`.
Accepted classification is not SDK0 or authority to ignore a future entry.
SDK phase windows0 do not turn an aborted scenario into acceptance or prove
fixture restoration; compiled860, guide and artifact remain unchanged.

Official conformance23703 exited1 (`31b8d3`): three direct URLs passed the
unchanged baseline, but the first proxy-default DNS case timed out. The retained
`proxy-conformance-thread1.txt` shows32/32 fixed HTTP workers blocked serving
long-lived GET SSE streams. POST/health/admission could not obtain a worker.
This confirms proxy HTTP executor starvation, not a cause of the historical
EDT extension-updating state. The bounded separate SSE executor is now
implemented and independently reviewed
(`b8cda181`, test correction `66aa2306`). Root77609 clean verify exited0
(`d262c6`), with all 250 proxy tests passing and no failures/errors/skips.
This establishes compiled/unit scope, not live protocol acceptance.
The supplementary artifact collector initially failed because Maven shade
replaces the ordinary final JAR rather than producing a separate `-shaded.jar`.
Root's corrected collector exited0 (`baf059`) and verifies the actual fat JAR,
250 tests/23 reports, 47 unchanged raw inputs and packaged Java17 class equality.
The proof is `proxy-stream-isolation-build-proof.json` (`08cdcb29`); standalone
JAR SHA256 is `2191bf6464234d60266af81a8c6474a3658634b2ca1e79954b322945d0d4c5bc`.
This does not alter the plugin archive. Fresh post-reboot identity and proxy
deployment subsequently passed; profiles passed241/241 (`98db89`). Official
five-URL conformance Root19672 exited0 (`ed3e27`), with unchanged baselines and
no new failures. Post-conformance exact original16 inventory/detail/complete
diagnostic Counter readback passed (`7efa2d`). The eight new V11 processes
exited0, but every output JSON is incomplete: BuildUtils was not already loaded
and `allFourComputedObserved=false`. Attachment delivery must be distinguished
from actual observation acceptance. Check required flags, errors and identities
inside every JSON rather than inferring readiness from process exit alone.
Retain incomplete records and run unchanged observation logic again only after
bounded helper definition through the verified bundle loader. Post SDK capture
completed with no read errors (`e66db8`, proof `90caf150`); twelve new platform
startup records were individually classified (`34bafb50`). After bounded
class-definition-only warmup, the unchanged observer ran under unique label
`reboot-post-conformance-confirmed`. Root validation (`3154d2`) confirmed all
four bodies, helper identity and `allFourComputedObserved=true`; the incomplete
records remain retained. Post-warmup raw SDK capture completed without read
errors (`d9f8b3`, proof `04d66392`). Final SDK review through that new observation
then passed in `independent-final-dynamic-review-v1/decision.json`
(`e9da4f53`). All103 severity4 entries (91 prior plus12 startup entries) are
classified at this retained scope, with zero unknown/new own fault or targeted
NPE. The failed SDK validator check remains recorded, with actor/cure unknown;
accepted classification is not zero SDK errors or a future exception waiver.
Strict final source/resource/helper observation validation also passed
(`a3888f`). The exact 530-file independent source review passed (`a478347b`),
with no blocking static findings. The local commit result is recorded in the
latest work checkpoint. Runtime/protocol passes alone do not classify SDK logs;
the independent per-entry decision above accepts only its captured scope.
Source and release acceptance remain separate from those runtime passes.

The local Windows Java17 test environment also exposed a selector initialization
failure: default temporary-directory handling produced `UnixDomainSockets`
`Invalid argument: connect`, followed by 60 selector errors and 17 dependent
class-initialization errors (Root13317/1/`080a26`, preserved before retry).
Setting `jdk.net.unixdomain.tmpdir` to a short dedicated directory removed all
those environment errors in the next execution (Root9432/1/`52349d`); that run
still failed on a separate stream-shutdown EOF assertion. The narrow reviewed
EOF/closed-transport assertion correction then passed all250 proxy tests under
Root77609/0/`d262c6`. Retain the short temporary-directory JVM option in local
Windows test invocations where this reproduced selector failure occurs; do not
commit workstation-specific paths. This is an observed environment workaround,
not proof that every selector failure has the same cause. A selector/temporary
path failure does not demonstrate an absent EDT API or a proxy production bug.

After proxy-only changes, require its actual deployed identity, profiles and
protocol gates, post-conformance SDK/original16 evidence and final source review.
The plugin860/JAR0609 are unchanged, so this scope requires neither a plugin
rebuild nor a repeat plugin full run. The failed conformance remains retained.

The preceding compiled candidate was `1.0.12.202610060440`: canonical build
Root54507 exited 0 (`138a5b`) and verifier-v4 exited 0 (`0efe5e`), with 9,138
Java tests, zero failures/errors, 19 skips, 377 reports and 860 unchanged captured
inputs. Its JAR SHA256 is
`769b24fd01d36c86ed70bd29425880351ac533d283f1660b7d3419d3d60566e1`.
Deployment15444, probe43844 and prime25811 exited 0 (`99169b`, `e62780`,
`fa9600`). Fresh original models and complete diagnostics were captured for
all sixteen project instances. Four V8 observations were dispatched after
completed prime and exited 0 (`3ffc52`, `42ad8f`, `3733eb`, `607a89`), accepting
sixteen public project points. Profiles passed 241/241 (`a751b9`), golden34513
passed 2/2 (`611a4f`), and docs passed (`95507b`) with 102 published files,
13 skills and 99 canonical guides from two fresh unchanged-generator runs.
History41335 passed 21 cases with its one canonical manual-PII skip (`414984`),
restoring the four original models and diagnostics with SDK0. Focus65139 produced
18 canonical passes and restored its four original models/public V8 points,
but its strict wrapper exited 1 (`a4b98c`) on three SDK UI records. Its separate
per-entry PLATFORM decision (`2a765431`) and Root's offline auxiliary gate
exit0 (`c061cc`) accept only that concrete window; strict exit1 is retained and
is not SDK0. The fresh full run Root92430 exited 1 (`a718f6`), with partial
1,225 PASS, 40 SKIP and one FAIL. Shard4's canonical
`modify_metadata::test_vendor_editable_objects_still_take_writes` expected
refusal when deleting a member of an editable catalog in a locked configuration;
the tool returned success. Exact target SDK review confirmed that the test and
packaged guide's blanket member-delete assertion was wrong: SDK target
`canDelete` and owning object's `canEdit` are distinct from whether the owning
top-level object itself may be deleted. The reviewed two-path correction was
adopted (`54b9f4`), changing the test and guide; production support guards and
Java source860 remain unchanged. The new success checks verify structural model
and disk deletion while retaining top-level and locked-referrer refusal checks.
The compiled 0440 artifact and all 377 reports/860 inputs/packaged resources were
preserved (`6397f3`, proof `511db654`) before adoption. The new packaged-resource
0609 build passed; the old 0440 full did not run the corrected body or guide.
Frozen failed results remain immutable. Final SDK/original-model comparisons, conformance and release
acceptance are absent; the compiled 0440 source860 proof remains unchanged.
These are scoped gates, not release acceptance or a historical hang cure.

Live failure preservation must distinguish sandbox denial from an operating-system
sharing lock. The first whole archive attempt failed before its first ZIP on a
sandbox read denial; an elevated repository-only inventory later read all 1,375
files. The next elevated whole archive reached the owned workspace's
`.metadata/.lock` and failed on that live Eclipse lock. Neither observation
establishes fixture corruption or permits excluding other unreadable files.
An initial four-lock preparation then failed on `telemetry.log.0.lck`.
Root's read-only inventory (`288024e4`) identified exactly 280 unreadable,
zero-byte lock files, 70 per owned workspace, all beneath `.metadata` and named
`.lock`, `telemetry.log.0.lck` or `write.lock` (search/validation indexes).
The new pre-stop preparation excludes only that hash-bound path list, checks
that each remains a regular zero-byte file, records its stat/no-read status and
explicitly declines full byte completeness. This is not a generic filename or
extension ignore rule. All other files and every repository/private-fixture
byte remain required; any other read failure or source drift refuses completion.
Before-stop preservation completed (`82347`, exit0 `c59d6f`) with that limited
scope, followed by exact-owned stop (direct exit0 `f6632c`). Post-stop preservation
completed (`91026`, exit0 `949bf0`, proof `e24f37c4`) for all eight whole trees,
with no exclusions. This establishes retained bytes at the stated points, not
private queue drain or restored models. Partial failed archives remain evidence
and must never be presented as completed ones.

The historical 0203 full run is rejected after a rename pre-body guard detected earlier
Moxel inactive-namespace callbacks. The rename body did not run. Exact installed
SDK bytecode and SWT source establish a concrete screenshot cleanup defect:
`TemplateScreenshotHelper` creates a standalone `MoxelControl`, then disposes
only its parent shell. SWT parent teardown calls the child's internal release
path, bypassing the public SDK `dispose()` override. That override removes
editing-language, project-language and style listeners before checking whether
the SWT widget is already disposed. Always explicitly dispose an owned
non-null Moxel control before disposing its parent; an `isDisposed()` guard
would skip required listener cleanup. Put parent disposal in its own `finally`
and keep SDK cleanup failures visible. The matching callback reads the retained
spreadsheet document after its namespace stops. Eight callbacks after screenshot
success match that path, but the exact leaked instances are not heap-identified.
This is an existing API lifecycle defect, not evidence of missing functionality
in EDT 2025.2.5. It does not establish the historical extension-updating cause.
The three-path fix is independently approved at source scope and adopted, with
six unit cases and a live render/CLEAN/render regression. The 0440 exact build
passed; the fresh focused render/CLEAN/render case passed within the 18 canonical
cases. The strict SDK0 gate remains false; the bounded per-entry PLATFORM review
accepts only its concrete UI window, while full verification remains pending. The failed run, compiled
artifact and all four repositories/workspaces were preserved before adoption.
The optional user areas were absent, separately verified rather than represented
as nonexistent archives. Evidence: the retained
`independent-sdk-interim4-source` packet and
`independent-moxel-candidate-v1-freeze-20261006.json` in the port work directory.

Two additional 0440 SDK records have bounded source/context classifications;
neither is a completed full-run or release verdict. The seeded temporary
external-objects case passed in 60.20 seconds and checked source-project/root
metadata read routes before deleting its project. It did not request or assert
an EPF/ERF binary dump. Exact .5 `Version.LATEST` selects 8.5.1; the SDK's
automatic dump job reports no matching registered native installation and
disables automatic generation. Installed EDT metadata support does not supply
a registered 1C:Enterprise executable. Classify that concrete prerequisite as
PLATFORM_RUNTIME, separately from EDT API availability. Do not claim native
binary-output coverage from this passing source-only case, or infer that its
retained 60-second lifecycle wait timeout was caused by the dump failure.
Evidence: current RUN's `independent-0440-full-sdk-cut4/native-runtime-entry-review/review.json`
(`57ac84d8`).

The separate form-editor teardown record follows a target SDK workbench path:
the project `BEFORE_STOP` listener calls `close(false)`, Eclipse queues the close
through `Display.asyncExec`, and SDK `FormEditorPage.dispose()` reads the form
while the BM object is already disposed, before superclass disposal. The plugin's
form screenshot/layout routes use ordinary `IDE.openEditor` editors and later
active-editor reads. These are different ownership semantics from the temporary
Moxel control above: do not apply an always-dispose workaround to user/workbench
editors. Exact source establishes the queued-close path, but not the affected
editor's live identity, which opening call created it, or completion of the
remaining private cleanup after the exception. This is no blanket BM-disposed
waiver, private-queue drain proof or historical hang cure. Final original-model
and complete-marker comparisons remain required. Evidence: current RUN's
`independent-0440-full-sdk-cut7/editor-teardown-entry-review.json` (`ed0073a4`).

An auxiliary full-run observer has a confirmed policy defect: its cumulative
critical-entry filter treats every own-plugin ERROR as a new critical failure.
The historical 0203 full run contains the already pinned `Export failed` negative case
for `NoSuchProject_ZZZ_e2e`. Exact target CLI source checks project existence
before exporting; the canonical negative case passed. This record is existing
OWN validation noise, not PLATFORM and not SDK0. The auxiliary filter would
freeze a later rename on that unrelated earlier record. Active helpers and raw
results must stay immutable. A future exception must bind the unchanged
canonical baseline, the exact intentional failed call and typed target throw
site; a generic `Export failed` message or exception class is insufficient.
No additional baseline entry, source-log demotion or release acceptance follows
from this finding. The corrected auxiliary policy is implemented and independently
source-reviewed in the frozen `b612fa56` classifier for that exact existing
negative call. The current 0440 full producer uses those pinned bytes. Its checks
preserve all other critical failures; the failed Root92430 run does not establish
fresh full-run acceptance.

## Latest completed build and current gate status

Fresh 2357 priming stopped with actual exit1 before the focused suite. On shard3,
the owned extension catalog and its String `ExtValue` were present in metadata,
but a query immediately after successful CLEAN reported missing `Ссылка` and
`ExtValue` fields. The failed partial baseline and whole private shard3 repository
were archived before further observation. The identical later query succeeded
in the same JVM with zero issues, without reset or metadata mutation. The first
primer remains rejected; this is not a release or historical-hang cure.

Exact target bytecode confirms that `isAllComputed()` delegates to a predicate
which checks only whether pipeline status is `EMPTY`. The successful CLEAN's
snapshot was `EMPTY` at stage `SYNC`, with `MD` absent from ready segments. A later
read-only snapshot in the same JVM was `EMPTY` at `AFTER_BUILD`, with `MD` ready.
Thus an empty current queue does not prove that later-stage computations have
activated or that query fields are ready. Stage advancement and later query
recovery are confirmed; attribution of every field miss or historical hang is
not. Do not certify post-CLEAN readiness from that predicate alone. A reviewed
public-SDK completion guard is now implemented for CLEAN: it requires the
post-build stage, ready MD/FORM segments and live readiness predicates within
one nominal budget. SDK probe exceptions propagate through a checked shared
predicate; the existing tolerant boolean gates retain their behavior. Eighteen
unit regressions were added. The exact-.5 canonical build and verifier passed;
initial runtime priming passed on four stands;
this implementation does not certify the historical updating-hang cause or cure.

The preceding compiled exact-.5 candidate was `1.0.12.202610060203`:
9,132 Java tests, zero failures/errors, 19 skips, 376 reports and 859 unchanged
captured inputs. Its scoped synchronous resource-error guard is implemented and
compiled, with 21 new observer/apply regressions. Initial new-binary readiness
checks passed on all sixteen project instances. This guard does not establish a Windows lock cause or a historical hang
cure. Whole previous runtimes were preserved before exact owned-process stops.

The preceding compiled exact-.5 candidate was `1.0.12.202610060106`.
Root confirmed canonical build and verifier exit0: 9,111 Java tests, zero
failures/errors, 19 skips in 375 reports and 857 unchanged captured inputs.
The build retains exact 503 installed SDK bundles and ten test supplements.
Its checked CLEAN completion guard and pre-perform all-project barrier are
implemented and compiled. Fresh loaded-bundle identity and the original sixteen
model/diagnostic baselines passed on four stands. The immediate post-CLEAN owned
catalog query passed on each stand; subsequent public points observed AFTER_BUILD
and ready MD/FORM segments in all sixteen project instances. Profiles passed
241/241, golden passed 2/2 with clean fixtures, and two fresh documentation
generations matched all 102 published files. The focused runner passed 17 cases
with restored fixtures, but its strict wrapper exited 1 on three SDK errors.
History replay, full E2E and conformance acceptance remain pending. These checks do not prove
the old batch failure is prevented
or cure historical updating hangs. The new epoch uses a separately allocated
workspace and a new binary, with all sixteen original models and complete
diagnostic baselines required before mutation; old baselines remain historical.

The focused catalog rename recorded a core `ResourceException` while deleting
the borrowed extension's `Catalog/Forms/ItemForm/Form.form`. The deepest Windows
error is a sharing violation: the file was occupied by another process. That
Windows wording does not establish a distinct PID; another handle in the same
JVM can also prevent deletion. The error precedes the tool's success return.
Exact target bytecode shows that
`ProjectSynchronizationManager.processFqnUpdate` catches `CoreException`, calls
`V8CorePlugin.logError` and returns normally. The deletion is the first operation
in its workspace callback, before deleted-file notifications, resource metadata
removal, Git removal and derived-data cleanup; that failure skips those later
operations in this callback. A normal SDK return and ready DD predicates therefore
do not establish successful disk export. The canonical case checked the base catalog's new and old FQNs,
but did not capture the extension's intermediate disk tree before cleanup.
Later restored models, complete diagnostics and ready DD points cannot establish
that the failed export was harmless. The locking actor remains unidentified;
do not attribute this to antivirus, a leaked plugin stream or newer EDT APIs
without evidence. A later OS owner point query found no owner, which cannot
exclude a transient lock. Preserve the raw failure and whole repository before
any subsequent mutation, and freeze future diagnostic cases on resource/export
errors as well as batch, nested-transaction and validator errors. Two separate
queued JFace decoration errors in that window were individually reviewed as UI
lifecycle errors; that classification does not cover the core deletion failure.

A later controlled single-case rename passed with SDK0 and coherent serialized
base/extension catalog and form links. Complete before/post/after raw copies,
including Git, were retained before recovery. Restart Manager observed no owners
in 35 samples, with a 6.4 ms gap before the response endpoint. This successful
case cannot explain or clear the original sharing violation. Public synchronous
core-log observation is now compiled to reject a swallowed resource error during
the actual SDK perform; compilation does not certify live listener wiring.
This guard cannot identify the locking actor or prove
asynchronous export coherence.

The compiled guard was exercised with an intentional test-owned read handle that
denied deletion of the borrowed form. The exact SDK resource exception occurred
inside the held-handle window. The tool returned a failed partial result with one
confirmed base refactoring and committed-mutation evidence, rather than reporting
the failing extension refactoring as completed. The handle was released and the
entire dirty tree was preserved before recovery. The strict diagnostic wrapper
still exited one because its expected-path predicate used forward slashes while
the raw Windows stack used backslashes; a separate retained-copy review confirmed
the intended error path without editing that failed report or rerunning mutation.
This expected-fault test is separate from SDK0/full-suite acceptance and does not
explain the earlier natural locking actor.

Freezing cleanup does not prove a timed-out SDK request has stopped. After a
transport or tool deadline, avoid source-file and model snapshots until actual
quiescence is established; a new read handle can itself prevent Windows deletion.
Retain raw logs and the freeze receipt meanwhile. An exact completed synchronous
service refusal provides stronger evidence than an HTTP response alone.

Raw Git index bytes can change when a caller refreshes cached file statistics.
Do not silently ignore an index difference: preserve both versions and compare
all indexed paths, object IDs, modes, stages, flags, extensions and checksums.
The controlled fault had one stat-only difference; all source and other Git bytes
matched the retained post-guard snapshot. Its preservation proof binds that
bounded comparison explicitly.

Recovery preconditions must distinguish a pristine input reference from the
current intentionally modified diagnostic tree. Check the latter against its
preserved dirty snapshot before reverting it. An epoch's fixture input list can
also include runner declarations outside the four project fixture directories;
validate every declared path against a complete preserved pristine repository,
rather than assuming a four-directory copy covers the input contract. Run the
pure input loader before dispatching recovery. Two preparation errors stopped
before mutation; the corrected canonical recovery restored all four original
models, complete diagnostic Counters and a clean repository. Its strict journal
check retained one new JFace disposal error for independent classification.

An earlier compiled candidate was `1.0.12.202610052155`; the immediate
predecessor of 0106 was `1.0.12.202610052357`.
Rename-final canonical build passed 9,063 Java tests, zero failures/errors and 19 skips in
374 reports. All 503 resolved installed SDK bundles match exact April versions
and binary hashes, with ten explicit test supplements; 855 captured source/build
inputs match before and after. Both installed Guava versions are retained as
verified exact-inventory evidence. This is not a blanket duplicate-version
exception or a release approval. The changed rename suite passed 36 tests.
Independent current-source rehashing matches all 855 build captures and finds
exactly the two reviewed rename Java files changed from 2049. The reviewed query
Python file is separately hash-bound; Java compilation does not cover its body.
This runtime passed golden 2/2, profiles 241/241,
two complete docs generations matching 102 published files and four-copy parity
for 1,396 canonical identities. Focused, full and conformance evidence remains pending.

The first fresh 2155 focus (Root 65159) failed after five passes because a
catalog-attribute post-body direct observation found extension DD
`BLOCKED`/`SYNC` and `isAllComputed=false`. No SDK severity-4 record was present.
The final log gate skipped on abort and authored changes remained dirty, so this
run does not certify cleanup or restoration. A read-only observation in the same
JVM later found all four predicates true with `EMPTY`/`NORMAL`, without repair.
The unchanged marker-read retry observed readiness about 2.08 seconds after the
false point. The exact transition time is unknown; the later 68-second direct
observation interval is not completion latency. A false point establishes
incomplete work at that instant; it cannot establish a deadlock. Preserve the
failed output and freeze mutation before any owner-approved recovery. The
separate bounded auxiliary retry retains false samples, rejects unobservable or
error states and freezes before cleanup on expiry. It is not a plugin fix or a
cure for historical hangs. Owner-approved canonical recovery after readiness
and export quiet separately restored original models and complete diagnostics.

The bounded retry (Root 87371) failed with actual exit1, 15 PASS and one ERROR;
its canonical log gate passed and the final unknown-field query case did not
run. Extension-owned table resolution exposed a confirmed port omission:
upstream `Catalog.tests_ExtOnly`, its Configuration collection reference and
related six false Role rights were absent. All 91 upstream fixture files were
audited before restoration. The catalog and rights now match upstream bytes,
the collection reference and exact nine declaration pins are restored, and
existing 8.3.27/form/support adaptations remain. Root's hygiene retry passed
eight tests with actual exit0. These four paths are outside all 855 compiled
inputs; bundle 2155 and canonical bodies remain unchanged. Preserve this failed
run's full repositories, indexes, history and SDK evidence before any recovery.

Four specific JFace severity-4 disposed-widget records in that failed retry
were classified from exact lifecycle/source evidence as queued decoration updates
during canonical CLEAN stop/reload. The individual decorating provider and
disposal cause are unidentified; stock-only frames alone do not exclude prior
plugin participation. This is neither SDK0 nor model/release acceptance. The
frozen window contains no new target DD batch, nested transaction or EValidator
record; historical DD batch failures remain separate and unresolved.

Fixture-final priming (Root 99173) and probe exited zero: all four owned query
readbacks are valid, extensions now contain eight objects, and all sixteen new
complete original diagnostic snapshots were captured. Never reuse the old
seven-object original baseline for this epoch. Corrected focus/history/full and
conformance remain pending. The epoch creator originally hashed LF JSON before
a Windows CRLF write; a separate raw-byte receipt preserves that erroneous claim
and binds the actual manifest, proving only the line-ending writer difference.
Use raw file digests as authority; do not normalize model diagnostics or silently
overwrite earlier proof files.

The corrected fixture-final focus (Root 39080) completed its canonical 17 cases
with no skips and exact same-JVM original models, complete diagnostic Counters,
direct DD predicates and clean fixtures. Its strict SDK0 wrapper still exited1
for ten individually reviewed queued-decoration disposed-item records; a separate
hash-bound SDK decision accepts only those instances, preserving strict
`accepted=false`. This is focused evidence, not full or conformance acceptance.

The first history attempt (Root 63288) remained failed: its missing proxy-port
environment caused an extra canonical skip and the strict verifier refused before
final readback. Complete evidence was archived before the unique retry. Corrected
`fixture-history-retry1` (Root 33570) then failed with 19 PASS, one RESET-FAILED
and two SKIP after catalog confirmation. At02:15:26.205 on2026-10-06, two exact
DD batch exceptions reproduced the earlier stopped2049 PartBasedComputer and
command-interface failure paths. Both occur inside the rename request before
its success return and before cleanup. Four pre-body public DD observations and
base/dependent settle calls had reported computed; all four same manager
identities also reported computed after the errors. These completion predicates
do not prove successful DD contexts. The failure gate froze before reset and
post-body marker collection. No original-model/marker cleanup certification
exists for this retry. The old batch failure is now reproduced on unchanged2155;
it is not a consequence of the earlier form nested-transaction defect or proof
that the restored extension catalog caused it. Actual worker/model and batch
ownership remain unidentified. Exact evidence is bound by
`fixture-history-retry1-independent-failure-review.json` and its English report.

A blind public `IProjectOperationApi.performExclusiveOperation` around SDK
`IRefactoring.perform()` was investigated and rejected as unsafe. The lower
operation holds normal DATA exclusion until its synchronous callback returns,
but SDK finalization ends its batch and waits for MD/MD_PRE inside that callback,
discarding a false 300000ms result. Exact `waitCompletion` bytecode contains no
operation-owner release or exemption; priority changes do not restore DATA
permission. The metadata factory exposes no settings overload and its returned
`IRefactoring` exposes no public settings injection or alternate perform
overload. An invented caller batch handle, private production reflection or
outer boolean pipeline block is not a supported fix. The separate base and
adopted-extension perform handoff is a concrete scope hypothesis; the actual
per-refactoring models/managers and shared batch owner still need observation.
No coordinator or historical stuck-updating cure was implemented by this audit.

The unchanged 2155 shard-1 diagnostic replay (Root 37703) completed its canonical
22-case schedule with 21 PASS, one manual-PII SKIP, a passing log gate and clean
fixtures. Its bounded read-only sampler observed two real SDK batch controllers:
the first included base and extension managers; the second included only the
extension while the base pipeline was unblocked. During the second controller,
four active base `EXP_O` export tasks were sampled with a stable volatile-owner
reread. This confirms the base-to-extension handoff and nonparticipant export
overlap, not attribution of the earlier failures: no offending `DD2` or `CMI`
writer was captured and the sampled request's SDK severity-4 count was zero.
Legal export overlap must not be described as a batch violation. The requested
2ms cadence had a maximum observed gap of about 20.12ms; fields were read without
an atomic snapshot and thread sampling perturbed timing. No gaps were reported,
but unobserved short work remains possible. Exact evidence is bound by
`review-history-shard1-batch-owner-20261006.json` (SHA256 `1fd002a2e15c3919345df9baab8e171a20de4384fdbe6ece8e75a3b563b2ab96`).
The old retry-1 batch failures remain actionable; a checked all-project drain
before each perform was a reviewed mitigation proposal at that observation.
Root later confirmed actual exit0 and exact original models and complete
diagnostics for the replay, then preserved its full private repository and
evidence in `history-shard1-diagnostic-preserved/proof.json`. The reviewed V3
barrier has now been adopted in nine canonical paths and its 857-input build
passed as recorded above. Live acceptance is pending; adoption and compilation
do not prove that it prevents the old failures or cures historical updating hangs.

Fresh 2049 owned stands passed eight priming CLEAN requests and froze all sixteen
fixture instances' original diagnostics before mutation. Golden passed 2/2,
profiles passed 241/241 and source parity retained 1,396 identities. The new full
`e2e-final-exact5-20261005-guards-run1`, Root session 22115, failed with actual
exit1 at 00:39:38 on 2026-10-06: 1,183 PASS, 38 SKIP and one ERROR. All four owned
stands were stopped, complete archives verified and whole old repositories moved;
only shard 2 was dirty. Evidence is `guards-run1-failure-preserved`. No successful
full or post-run original-model/marker certification exists for that run.

The failing unknown-field query test assumed English SDK diagnostic wording.
The SDK correctly returned `valid=false`, ERROR `Поле 'НетТакогоПоля' не найдено`
at 1/9/8. Keep SDK-owned localized messages intact. The assertion correction
checks Russian and English queries, each requiring the same issue's ERROR,
authored field, English/Russian not-found phrase and exact position; verdict,
error-count and no-diff checks remain. Its source review passed; live acceptance
is pending. A separate nested-BM-transaction finding during form rename now has
a narrow implementation and independent source/API review: preserve only old
name/id across the initial read, acquire a fresh global SDK handle, recheck its
name inside another short read, then invoke SDK refactoring construction outside
both reads. Eleven boundary regressions passed in the 2155 canonical build.
Focused live and full acceptance remain pending; this specific correction is not a general DD or
historical stuck-updating cure.
Final results and SDK/model/conformance acceptance remain pending.

The historical 1949 same-runtime focus passed 25/25 with exact original inventories/details
and complete diagnostic line multisets, ready clean fixtures and zero SDK severity-4
entries. It does not certify subsequent source changes. For 2049, two independent seeded-tree generations
used the unchanged docs generator and match all 102 published files: 99 canonical
guides, retained `debug_launch` alias, folder README and `EDT-MCP.md`. The two
default-profile-hidden guides were preserved explicitly, not fetched anew.
Agent validation passed 13 skills and 99 canonical guides.

The first two in-place docs generations remain OSError-22 failures. An isolated
parent-held mmap reproduces the truncating-open mechanism; ordinary writes and
parent hash reads passed. The actual external mapper is unknown. Do not report
the isolated successful generations as successful in-place writes or claim the
mapper's identity. New generation evidence is `guards-final-docs-generation.json`;
historical evidence is `dd-final-docs-generation.json`, generation logs
and `docs-fs-probe-1791230839601751700/proof.json` in the port work directory.

The historical 1949 full run was intentionally stopped after independent source review confirmed
two inherited guard gaps: clean-all treated an unavailable DT manager as an
observed empty project set, and the public bridge widened its profile to all
registered tools when live profile context was unavailable. Missing observations
must fail closed. The narrow clean-all guard and two regressions were applied
only after all four owned stands stopped and complete run/SDK/thread/fixture/index
evidence was archived in `dd-run1-audit-stop-preserved`. Shards 3 and 4 remained
dirty: two private repositories, not three. No reset or final original-model
certification was performed. Both guards are implemented with five new regressions
and the 2049 canonical build passed; fresh live/final evidence must use that
artifact. 1949's healthy results remain valid only for their compiled source scope.

The first guards attempt failed test compilation because exact Mockito
`2.23.0.v20200310-1642` lacks `verifyNoInteractions`. Actual cached-jar javap
verified `verifyZeroInteractions` delegates to `verifyNoMoreInteractions`; fresh
or reset mocks retain strict no-dispatch checks. Preserve the failed compile;
only the corrected retry1 executed the passing 9,052 tests.

Read filename manifests with explicit UTF-8. An auxiliary archive comparison's
Windows default decoding falsely reported Unicode-filename drift; UTF-8
rechecking verified all four archives before preserving whole repositories.
This was an auxiliary decoding error, not source mutation or SDK corruption.

Final full results, complete SDK-window classification, original-model/marker
readback, current-artifact conformance and local commit remain pending. Earlier
failed runs, interrupted fixtures and historical scopes below remain evidence.

## DD completion and transport deadline evidence

Final-1715 full run2 stopped during extension cleanup after a successful context
restart. A captured executor was polling `waitAllComputations`; its nine derived
data workers were idle. No SDK severity-4 entry accompanied that wait. The client
expired at 180 seconds before the SDK completion argument of 300 seconds elapsed.
Exact target bytecode can first wait accumulated contexts and then begin another
completion deadline: the argument is not an end-to-end ceiling. Idle workers,
STARTED and clean Git do not prove that all required DD segments are computed.
This capture does not prove a permanent deadlock or the historical updating cause.

The same canonical BSL marker scenario alone passed 2/2 on a fresh workspace with
unchanged cleanup. Final read-only inventories, detail probes and complete
diagnostics matched all four original fixtures; readiness and disk were clean,
with no new SDK errors. The exact nearest twenty-case history replay subsequently
passed 21/21 including the log gate, with original model inventories/detail probes,
clean fixtures and readiness, and no new SDK errors. It did not reproduce the wait.
Its complete marker comparison against a different earlier JVM failed on six
property-pair presentation orderings; the SDK formats an unordered HashMap.
No complete pre-mutation diagnostics were captured in that replay's own workspace.
Retain that failed comparison and the missing same-runtime baseline explicitly;
do not normalize it away or use a post-run snapshot as an original baseline.

Source inspection independently confirms a completion-contract gap: the old void
DD helper merely logs a false wait result or exception, and CLEAN can subsequently
report success. A checked CLEAN completion result and isolated diagnostic snapshots
have been implemented and independently reviewed against exact target SDK methods.
Current canonical build and same-runtime focused acceptance now pass as recorded
above. This certifies the checked completion contract at those scopes, not a
demonstrated cure for the DD trigger. Keep
lifecycle completion, DD completion, original model equality and client timeout
as separate proofs. Never extend a deadline or substitute model-only readiness
to certify an unresolved restore. Evidence: full-failure-20261005-run2-preserved,
threads-shard4-20261005-220740.txt, review-sdk-run2-stopped-dd-wait.md and
dd-focused-a-final-readonly-proof.json under the port work directory.

The first strict-completion canonical build ran 9,047 tests and failed one runtime
unit with `NoClassDefFoundError: ...derived/pipeline/PipelineStatus`. Compilation
had access to the SDK classes, but importing the parent `derived` package does
not transitively wire its getter return types in OSGi. The exact-.5 bundle exports
`com._1c.g5.v8.derived.pipeline` version 2.1.0; the host now imports it explicitly.
The regression remains unchanged and the complete retry passed 9,047 tests with
zero failures/errors. The first failed report set remains preserved.
Inspect getter return types and the host bundle's package wiring as well as Java
signatures. Preserve each failed report set before retrying.

## New exact-.5 SDK marker teardown evidence

The fresh 1949 full run records one `MarkerException: Project storage not found
for P/TestConfiguration.tests` inside the canonical clean-all request. The old
batch's markers were created before extension teardown; duplicate checking ran
at 23:14:03.994 inside BEFORE_STOP 03.953–STOPPED 04.018. New storage initialized
at 04.584, STARTED followed at 05.101 and strict CLEAN returned at 06.346.
Exact target bytecode corroborates storage removal during CLEAN, delayed
duplicate-check work and catch/log/notify continuation. This is a specific
SDK teardown overlap with observed storage/lifecycle recovery, not a generic
disposed-widget exemption. The stopped full run has no accepted final original
diagnostic comparison: persistent loss is neither proved nor excluded by this
interim observation.

`IMarkerManagerV2.commit(Collection<IProject>)` exists, but commits the present
storages without draining the internal committer queue. That committer uses its
own scheduled executor rather than an Eclipse Job. Generic Job joins and public
commit/DD APIs cannot certify that private queue is drained. Base SDK CLEAN also
cascades to its dependents, so merely serializing client calls is not a proven
cure for this overlap. Do not invent a public flush or use blind delays to claim
queue quiescence. No SDK baseline exception has been added. This observation does
not establish the historical stuck-updating cause. Evidence: the new 1949 full-run interim
review and exact decision JSON under the port work directory.

## Confirmed port corrections

| Cause | Evidence / correction | Prevent recurrence |
|---|---|---|
| Auditing only the 24 commits after the claimed predecessor baseline missed behavior already absent from the fork. | A full-tip audit found omissions in 17 production files. Transport, capabilities, bridge aliases, consent redaction, descriptions, restart and connection UI behavior were restored while preserving fork policy. | Audit both the commit delta and the complete upstream tip before a full run. Record deliberate fork differences and map upstream tests to equivalent fork coverage. |
| Raw metadata construction left SDK-required state uninitialized. | Extension adoption and register-dimension initialization caused SDK exceptions. Typed adoption/factory paths, fresh identities and appropriate defaults were added. | Inspect the exact target SDK factories and object invariants. Verify persisted objects and SDK logs, including requests that omit optional properties. Never invent an arbitrary default that changes caller intent. |
| Three inherited form fixtures omitted their default command bar. | Restoring the upstream command bars removed the corresponding adoption/render failures. | Compare fixtures with upstream and target SDK requirements before changing production rendering code. Pin fixture declarations and check fixture integrity. |
| Vendor distribution metadata required an unavailable vendor library in the target SDK. | SDK-generated format-6 `ParentConfigurations.bin` supplied the equivalent support state; the original `.distr` and support assertions were retained. | Verify vendor fixture round trips and semantic rules on the target EDT. Do not bypass support protection to make a test green. |
| External-object cleanup treated linked base-model identifiers as belonging to the external project's model. | The resync path encountered a BM access/class-cast error. Cleanup was restricted to identifiers owned by the appropriate metadata scope. | Establish model ownership before entering BM transactions. Keep the external project's own exports covered by regression tests. |
| Inherited assertions and damaged encoding did not match actual public contracts. | Role tests assumed no rights model; common-attribute tests asserted internal class tokens. Assertions were corrected to check authored rights, rollback and public readback. UTF-8 corruption was repaired. | Check the original baseline and public contract before changing implementation. Preserve substantive assertions; do not weaken them or replace them with internal implementation details. |
| Python 3.14 temporary directories created inside the Windows sandbox were unreadable by Python/EDT. | Export failures included `WinError 5`; the same export scenarios passed outside the sandbox and produced 33 files. | Diagnose permissions using the same process context as the real test. Use the approved execution path for live E2E instead of changing export behavior or silently working around sandbox restrictions. |
| Git restored source files while EDT was still exporting model changes. | Moving the first restore after model readiness/export quiescence resolved the demonstrated cleanup race. Focused regressions and log checks passed. | Wait for export completion before restoring. Check disk state, model readback and extension dependencies; a successful reset call alone is insufficient. This fix does not resolve the separate extension-form issue below. |
| Test copies had stale inputs, generated preferences or missing Java sources used by the coverage scanner. | Preflight was expanded to check canonical inputs, Java/Python parity, dependency files and fixture hygiene across all four copies. | Check source hashes, installed bundle identity, workspace, profile and fixture baseline before any full run. Keep mutable fixtures isolated from the working repository and the user's workspace. |
| Documentation generation could publish a tool error as a guide or conceal a hidden-tool failure. | Generation now validates rendered results and fails closed; only the documented exact profile-denial case may preserve an existing valid guide. | Generate from the intended live binary/profile and validate all tool guides before publishing. Do not treat arbitrary errors as documentation. |
| A restored test referenced an enum constant absent from EDT 2025.2.5. | The test was adapted to the available merge event while retaining unexpected-event/raw-result assertions. The canonical build passed. | Verify API symbols against the exact target SDK; do not assume upstream's newer API exists locally. |

## Confirmed cleanup cause: incremental recreation of deleted BSL owners

Final bundle `1.0.12.202610051715` reproduced two SDK
`CompositeEValidator` / `IllegalStateException: Object is removed` errors after
canonical `CommonModule.Calc` deletion. Exact target public deletion uses
`IMdRefactoringService` / `IRefactoring.perform`, owns its BM batch and removes
associated BSL indexes; no post-delete plugin access to the removed object was
established. Do not classify this as a missing newer EDT API or blanket platform
noise merely because the stack has SDK frames.

A fresh shard2 marker observation proved31 original base rows without EValidator,
25 surviving rows after deletion without EValidator,32 rows with one EValidator
after ordinary refresh reset, and the exact original31 rows after standard base
CLEAN then extension CLEAN. It repeated twice, preserving extension27 rows and
original complete model/source state. A fresh shard3 timestamped observer placed
the error at21:23:13.407,2.77s after Git restore returned21:23:10.640543; the
post-ready/export-quiet marker read before restore had no EValidator. This pins
the failure to incremental restoration/import, rather than the delete response.
The immediate SDK mechanism is BSL Module.owner referring to a removed CommonModule;
the specific retained resource/task and SDK invalidation race remain unproven.

The harness now selects CLEAN_BUILD after the existing ready/quiet/scoped-revert
barrier for per-project evidenced delete/rename and uncertain fixture writes.
Ordinary confirmed writes retain incremental refresh. Rename is a conservative
object-lifetime policy, not a claim that the exact
Calc failure was reproduced on rename. Mandatory extension CLEAN remains ordered
after base restoration. Persistent cold evidence participates in addressed reset
scope and shortcut prevention, survives test boundaries and is retired only for
the entire verified selected set; later read/action/skip tests also restore it
without being accused of the earlier mutation. Optional setup/evidence guards,
original inventory/detail equality and failure preservation remain strict.

Evidence: `.claude/work/port-1-0-12/review-delete-validation-focused20261005.md`,
the cold-shard2/timed-shard3 marker/timing/log artifacts and
`delete-reset-policy-runner-unit-20261005-reviewed.log` (248 tests, zero failures or
errors, one Windows skip). New-policy default-reset focused acceptance passed
5/5: two canonical Calc deletions, common-module rename, catalog-attribute rename
and plugin log gate. The observer uses no manual recovery clean, restores the
first original complete marker baseline31/27 each time, preserves canonical
model/source equality and records zero new SDK severity4. Frozen log/source
hashes were independently checked; see reset-policy-focused-report.json and
marker phases. The running full suite is a separate gate. No SDK baseline was
changed and the two old validator records retain their exact classified logs.
This diagnosis does not prove the historical
extension-updating hang's cause or its resolution.

## Confirmed open defect: predefined characteristic type

A valid creation request for
`ChartOfCharacteristicTypes.E2EPredefTypes.Predefined.Weight` supplied a code
without an explicit value type. `PredefinedWriter` creates a raw item and only
sets its type when `props.valueTypeSet` is true. Otherwise the type remains null.
The EDT 2025.2.5 characteristic validator dereferences it in
`ValidationUtil.isTypeDescriptionSubsetOtherTypeDescription`, producing an NPE.

Evidence: `tests/e2e/tools/test_predefined_items.py`,
`.claude/work/port-1-0-12/readback-failure-sdklogs/chart-predefined-characteristic-npe.log`.

Status at pause: diagnosed, **no production fix applied**. Inspect the target
SDK's standard initialization and use its correct typed default. Preserve
explicit value types and fresh identities. Cover omitted types and constrained
owner types; do not silently insert an arbitrary String type. Verify the saved
model and absence of this SDK exception after the fix.

After resumption, source changes copy the owner's effective non-null type on
omission and use a fresh empty description on an explicit clear. They retain
qualifiers and TypeItem references; malformed null owners are refused before
mutation. The exact-.5 compilation exposed an invented fixture setter:
`ChartOfCharacteristicTypes` declares `setType`, while `getTypeDescription` is
an operation without a setter. Unit fixtures and live XML/property assertions
were corrected to `type`. Final exact-.5 build and focused live acceptance
subsequently passed: 26/26 checks on bundle `1.0.12.202610051715`, clean fixtures
and original model readback, with none of the four targeted initialization NPEs.
Three specific JFace disposed item records were independently classified as UI
lifecycle races; they remain recorded rather than added to an error baseline.
Full-suite acceptance is separate.

## Compatibility hypothesis raised by the user

The user suspects that some upstream behavior depends on functionality absent
from EDT 2025.2.5. Keep this as an explicit audit question, not a general diagnosis
of every SDK failure. A confirmed example is the restored test's absent
`BEFORE_MERGE_PROCESS_STARTED` enum constant; the available
`MERGE_PROCESS_STARTED` event was used without dropping the substantive checks.

The subsequent independent audit also checked the cached .6 enum and found the
same literal absent there. This is a confirmed test/API mismatch, not proof
that the event was introduced in a newer EDT version.

Before implementing an upstream feature, inventory the SDK classes, methods,
services, enum constants, factory defaults and runtime behavior it relies on.
Check each against the exact target SDK/source. Classify discrepancies as an
absent API, a changed contract/default or a supported API used incorrectly.
Use a documented equivalent when available; report an unsupported feature
explicitly rather than masking missing functionality with a catch-all fallback.
Compilation alone does not validate factory defaults or live model behavior.

The predefined-item null type is a confirmed initialization defect, not evidence
of an absent feature. The extension auto-adoption controller exists in the target
SDK; its interaction with cleanup still requires an isolated reproduction.

The isolated reproduction and one successful ordered cleanup are recorded
below; the broader regression gate remains pending.

## Open investigation: extension-form reset mismatch

Confirmed observations:

- Three shards stopped with `RESET-FAILED` after create, rename and delete
  scenarios. These were not readiness timeouts.
- Revalidation/resync returned success and EDT reported ready, but extension
  readback did not match the runner's initial baseline.
- A later read-only comparison of the four stands showed an extra Description
  title in two forms and missing Code/Description/Attribute data paths in one.
- The initial full-run baseline was not persisted as a separate evidence file;
  cross-stand differences alone do not establish the exact actor or chronology.

An isolated fresh-workspace reproduction on 2026-10-05 confirmed that changing
only the base form's Description title propagates to the extension model.
Restoring the base removes its title, but the extension keeps the modified title
while both projects report ready and their source/index are clean. Three explicit
extension refresh/resync cycles do not restore the original readback. Rewriting
the identical committed extension form bytes also does not restore it. This
proves dependent model drift and an insufficient reset; it does not yet prove
the mechanism behind the user's historical stuck updating status.

The next isolated step restored both form readbacks exactly: refresh the base
from restored source first, then call the existing `clean_project` on the
extension. The extension clean finished in about 4.5 seconds, with all projects
ready and source/index clean. Evidence is `diagnostic-extension-clean.json` and
`diagnostic-extension-clean-evidence/`. This certifies that single sequence,
not the final suite. Its ordering must be covered by runner tests and repeated
with rename/data-path cases before treating it as the general cleanup fix.

The ordered reset is now implemented and independently reviewed. Final runner
verification passed 237 tests with one Windows symlink skip. An owned live run
repeated the exact title mutation and canonical attribute rename twice, then
checked the final log: 5/5 passed, both complete forms matched the original
baseline after every reset/final cleanup, all projects ready, source/index clean,
and no new SDK severity-4 records. It used the older 1321 plugin. New production
binary and full-suite acceptance are separate gates still pending. Evidence:
`extension-reset-focused-20261005-202039/` and the final diagnosis report.

Source inspection found that ordinary
create/modify cleanup differs from rename/delete cascade cleanup. The target
SDK has an auto-adoption synchronization controller. Revalidation performs
refresh/build, not unload/reimport; resync's metadata traversal does not prove
that the `Form.form` body was restored.

Evidence: `.claude/work/port-1-0-12/readback-failure-current-details.json` and
`.claude/work/port-1-0-12/readback-failure-sdklogs/`; the isolated initial baseline
and mutation phases are in `diagnostic-extension-form-observations.json`, with
same-byte refresh evidence in `diagnostic-forced-form-refresh.json` and frozen
SDK logs in `diagnostic-extension-form-sdklogs/` in that work directory.

Before a fix: preserve the failed fixture trees, staged/unstaged binary diffs,
SDK logs, calls and model readback. Compare with an independently clean import.
Reproduce one base-form mutation followed by ordinary cleanup and extension
readback, then the canonical rename/delete cleanup. Persist the baseline before
mutation. Do not weaken readback checks, redefine the baseline after a failure,
or assume that increasing timeouts will fix a model-state mismatch.

## Orchestration and SDK-log lessons

Windows PowerShell 5 evidence needs its actual runtime format checked. Its
native-output redirection writes UTF-16LE logs with a BOM; a UTF-8-only verifier
fails before it can classify an otherwise successful test. Decode the observed
BOM explicitly and retain raw bytes. The profile auditor's console summary uses
`failedChecks`, while its saved report uses `failed`; validate the saved schema
before writing an acceptance receipt. In this port both bookkeeping failures
occurred before receipt creation and left the successful live results unchanged.
Retain failed recorder sources and exits, and correct them under a new name.

Assign `Get-Content ... | ConvertFrom-Json` directly when expecting a JSON array:
wrapping the pipeline in `@(...)` produced a nested array in Windows PowerShell
5 and invalid shard/port values. Use literal path `Replace` for backslashes;
a one-backslash regular-expression pattern failed before process launch.
PowerShell AST parsing proves syntax, not those runtime behaviors. Preserve
partially seeded preferences and launch progress before a uniquely named retry.

An additional isolated reproduction identified incremental restoration of a
deleted CommonModule as a separate cleanup defect. A genuinely fresh baseline
had 31 base markers and 27 extension markers, with no EValidator failure. After
supported deletion, every surviving marker remained valid. Git restoration
followed by incremental refresh produced a new `Error executing EValidator`
marker on the recreated Calc module while both projects reported ready.
Standard base CLEAN_BUILD, followed by extension CLEAN_BUILD, restored the exact
original complete marker multisets and model readback; this repeated twice.

A third fresh stand timestamped the existing restore callback: ready/export-quiet
marker capture ended at 21:23:10.572, Git restoration ran at 10.573–10.641, and
the SDK removed-object exception was logged at 13.407. Exact SDK inspection
traces the stale nonproxy `Module.owner` to BSL scope resolution during derived
validation. The public delete/refactoring lifecycle is inherited from 1.0.11 and
does not dereference the deleted object afterward. This evidence places the
reproduced failure in incremental test restoration, not the delete's response;
it does not prove the user's historical permanent updating hang has the same cause.

Evidence: `delete-validation-cold-shard2-*` and
`delete-validation-timed-shard3-*` under the port work directory. Preserve the
raw marker phases, restore timestamps and SDK records. Compare complete semantic
marker rows with duplicate counts: raw Markdown order can change without marker
loss. A previously failed cleanup can contaminate the next baseline with an
EValidator marker, so use a genuinely fresh baseline before claiming recovery.
The per-project runner correction is implemented and independently reviewed:
successful/committed delete or rename, and uncertain writes, use CLEAN_BUILD for
their evidenced projects. Ordinary known writes retain incremental refresh;
the dependent extension remains cold after its base. Evidence survives test
boundaries and unrelated resets until every selected original model verifies.
All 248 runner tests passed with one Windows symlink skip. Focused live default
cleanup passed both Calc-delete cases, module rename, attribute rename and the
log gate: 5/5, original complete marker multisets/models/source restored after
every operation, no new SDK severity-4 entries. Evidence is
`reset-policy-focused-*`. Full-suite acceptance remains a separate gate.

- The scratch full-run driver matched `FAIL`, `ERROR` and `TIMEOUT` but missed
  `RESET-FAILED`; the progress display also hid that status. This delayed
  detection. Its correction was not certified before pause. Treat every runner
  nonzero exit, reset failure, aborted run and unexpected terminal status as a
  failed gate; progress counts alone cannot establish success.
- The stopped 1949 run's frozen driver recognizes those red statuses, reset failures,
  CALL-TIMEOUT, fatal summaries and unexpected terminal statuses. Its broad
  BaseException handler also catches normal `sys.exit(0)` and records the exact
  `{"error": "SystemExit: 0"}` object. That was a proposed bounded bookkeeping
  classification, never acceptance of the stopped run. The new 2049 producer
  catches ordinary exceptions and KeyboardInterrupt without intercepting SystemExit;
  its generic final verifier rejects any driver-error artifact. Require actual
  Root/child exit zero, no first red/abort and the exact 1,396-identity/1,399-result
  schedule. Preserve active/frozen producer bytes. SDK snapshots
  taken once per minute retain raw evidence, not a guarantee of gap-free rotated
  log collection; final hash/coverage and complete-record review remain mandatory.
  Root session 73818 actually exited one after the intentional audit stop and
  cannot qualify for the normal-exit exception.
- Stop at the first failed gate. Finish source/fixture parity and focused
  regressions before repeating the expensive full suite. A lower-tier green
  result cannot certify a higher tier or a different binary.
- Inspect current and rotated SDK logs after live checks. The last frozen run
  contained 24 SDK severity-4 records requiring classification. Absence of
  `fm.giper` stack frames does not prove the plugin did not create invalid state;
  the predefined-characteristic defect is an example.
- Classify each SDK entry as a real plugin defect, plugin logging noise or a
  platform issue using evidence. Do not blanket-whitelist exceptions or claim
  causality from temporal proximity alone. Async Moxel/disposal exceptions and
  an EGit local-resource failure still require bounded conclusions.
- Operate only explicitly owned test EDT installations, workspaces and PIDs.
  The user's endpoint 8765 must remain untouched. Four copies are an execution
  choice for disjoint mutable fixtures, not a compatibility requirement.
- Preserve staged fixture changes as well as worktree changes before cleanup.
  At pause the first private fixture repository retained an interrupted staged
  rename. A worktree-only restore would leave it dirty.

## Exact-SDK verification lessons after resumption

The local target must retain the complete installed profile and immutable artifact
hashes. The resolved test runtime is checked against installed bundle IDs, OSGi
versions and exact bytes, with a small explicit list of test-only supplements.
The retained retry3 runtime passed this identity check for 503 installed bundles
and ten test supplements; its three unit failures remain a separate red result.

Two verifier bugs initially resembled cache corruption. Plugin and feature jars
can share a basename, so artifact hashes must be keyed by classifier-scoped paths.
OSGi manifest versions such as `1.77` normalize to `1.77.0`; compare canonical
versions before checking binary hashes. Independent installed/cache/snapshot
comparison proved identical bytes. Neither observation justified deleting caches,
changing the installed SDK or accepting genuine binary/version drift.

Explicit XML runtime versions require validation before SDK import. The target
SDK catches malformed version parsing and silently infers a version from XML.
Reject malformed and empty explicit versions early so caller intent is preserved;
omitted or blank values may still request inference. Source tests cover both.

## Last validated checkpoint and remaining gates

### Subsequent independent source audit

The user authorized a read-only independent audit after the pause. Three new
reviewers inspected exact target SDK jars/source/bytecode without resuming live
testing. See `docs/e2e/compatibility-audit-1.0.12.md` and the three detailed reports
under `.claude/work/port-1-0-12/independent-audit-*-20261005.md`.

Confirmed additional lessons:

- Retained latest-build Surefire evidence uses .6 core
  `26.0.1.v202605050943`; the .5 stand uses `26.0.1.v202604021910`. The mutable
  `2025.2` target and CI pin do not certify exact .5 compilation. Check resolved
  bundle identities, not a channel name or misleading pin comment. An exact
  archived/local .5 compile gate is required before claiming that evidence.
- The predefined null-type branch already exists in fork HEAD and both upstream
  snapshots. Distinguish inherited defects from regressions introduced by a port.
- XML import advertises nature/XML-version arguments but the target four-argument
  API uses runtime version/base-project name. Check String argument meanings in
  SDK implementation/debug metadata, not just matching reflection signatures.
  Both-null happy paths hide this inherited defect; meaningful nonempty-option
  coverage is required for its future fix.
- Official 2026.2 fixes #2030/#1966 are possible leads for extension-form state,
  not proof that those fixes cause or solve the current Git-refresh mismatch.
  Separate dependencies on SDK bug fixes from missing SDK functionality.
- No concrete newer-only production dependency was established in the audited
  groups. This is bounded static evidence; it does not certify live operation.

Historical paused checkpoint: the user later explicitly resumed autonomous
completion and authorized necessary task actions. The results below describe the
older artifact and do not certify the subsequent exact-SDK build or final suite.

- Latest compiled qualifier: `1.0.12.202610051321`.
- Canonical `source/compile.sh`: 9,017 Java tests, zero failures/errors, 19 skips.
- Python: 201 tests, 200 passes and one Windows symlink skip.
- Profile audit: 241/241 passes; latest golden: 2/2 passes.
- Focused common-attribute checks plus log ratchet: 15/15 passes; check-description
  checks plus log ratchet: 7/7 passes.
- Latest full run was incomplete: 1,075 PASS, three RESET-FAILED and 255 SKIP,
  including 217 abort-induced skips and 38 ordinary/gated skips. The remaining
  shard was interrupted; final fixture/model cleanliness was not certified.
- Earlier conformance on the user's 8765 endpoint passed eight checks with 24
  expected baseline failures per endpoint. It does not certify the latest
  compiled artifact. Latest-artifact conformance remains pending.
- No final root commit, push or release approval was made.

Historical resume priority at that pause: preserve evidence, isolate the extension-form mismatch and fix
the predefined-item initialization; then rebuild canonically and repeat the
relevant focused checks, complete full E2E/cleanup/log classification and run
latest-artifact conformance against the unchanged protocol baseline.
