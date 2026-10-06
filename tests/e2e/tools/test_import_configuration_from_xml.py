"""
e2e tests for import_configuration_from_xml (kind: action).

THE TOOL (ImportConfigurationFromXmlTool, getResponseType() == MARKDOWN):
Wraps EDT's "Import -> Configuration from XML Files" CLI API
(com._1c.g5.v8.dt.cli.api.workspace.IImportConfigurationFilesApi, via reflection).
It reads a directory of XML source files and creates a NEW EDT project in the
workspace from them. The reverse of export_configuration_to_xml.

Required params (JsonSchemaBuilder): importPath, projectName.
Optional params: runtimeVersion (project platform version), baseProjectName (base
configuration project). projectNature/xmlVersion remain accepted only when empty;
explicit legacy overrides are refused before workspace/API access because EDT infers
the nature and XML format from the source files.

THE HAPPY PATH IS A REAL IMPORT (issue #647):
This file used to say a real import could not be tested here, because it needs a
valid XML dump and creates workspace state the git-fixture protocol cannot undo.
Neither half holds: export_configuration_to_xml MANUFACTURES the dump from
TestConfiguration into an OS temp directory, and delete_project removes the
imported project again — the same create-then-delete shape test_create_project.py
and test_delete_project.py already use. The fixture is only READ (the import
creates a SEPARATE project), and every test still asserts assert_no_diff().

The happy path exists because the REPORTED STATE is the thing under test. The CLI
import API leaves the new project behind a blocked start latch, so EDT never
starts it and list_projects reports it 'not_available' forever; the tool now
refreshes it, asks EDT to start it and waits, then states what it observed in
the front matter (state: ready / state: importing + projectReady). The latch is
NOT released on that path (releasing it would arm a second, competing start);
it is released only if the refresh or the start request itself fails, so EDT's
own watchdog can still start the project. A pre-fix server answers success with
NO state at all and the project never leaves 'not_available' — so this test
fails on it.

execute() validation order (each returns ToolResult.error -> isError, and each
happens BEFORE the CLI API is invoked, so every test below is non-destructive):
  1. requireArguments(importPath, projectName) -> "<name> is required"
       (checked in order: importPath first, then projectName)
     nonempty projectNature/xmlVersion -> actionable unsupported-override refusal
       before any path/workspace/API lookup; neither is passed to EDT
     nonblank malformed/zero runtimeVersion -> actionable invalid-version refusal
       after legacy refusals, before workspace/path/API lookup; blank means XML inference
  2. !Files.exists(importPath)        -> "importPath does not exist: <abs>"
  3. !Files.isDirectory(importPath)   -> "importPath is not a directory: <abs>"
  4. workspace project already exists -> "Project already exists in workspace:
       <name>. Import requires a new project name."
  5. an import of the SAME projectName is already running -> "An import into
       project `<name>` is already in progress (started N seconds ago)..."
       (claimed BEFORE guard 4, which cannot see a project the other call has
       not created yet)
  6. api == null                      -> "IImportConfigurationFilesApi is not
       available. Required EDT plugin com._1c.g5.v8.dt.cli.api is not installed."
  (only AFTER all of the above does it call importProject(...).)

Every test asserts assert_no_diff() on TestConfiguration: a rejected/validating
call must never touch the committed fixture on disk.
"""

import os
import shutil
import tempfile
import uuid
import zipfile
from pathlib import Path

from harness import (
    call,
    assert_ok,
    assert_error,
    assert_error_quality,
    assert_contains,
    assert_not_contains,
    assert_no_diff,
    assert_no_diff_rel,
    settle_or_fail,
    split_markdown_row,
    e2e_test,
    PROJECT,
    TESTS_PROJECT,
    TESTS_PROJECT_REL,
    REPO_ROOT,
)

# A real, existing DIRECTORY on disk (valid importPath shape) that is NOT a
# config XML dump. Using the repo root keeps the test self-contained and
# deterministic; it lets us reach the existing-project guard with a path that
# Files.exists()+isDirectory() both accept.
_EXISTING_DIR = REPO_ROOT
# A real, existing FILE (exists() true, isDirectory() false) to hit the
# "not a directory" branch. CLAUDE.md is committed at the repo root.
_EXISTING_FILE = os.path.join(REPO_ROOT, "CLAUDE.md")
# A path that does not exist at all.
_MISSING_PATH = os.path.join(REPO_ROOT, "no_such_import_dir_e2e_zzz")
# The throwaway project the round-trip imports into (never the committed fixture).
_ROUND_TRIP_PROJECT = "ZZImportRoundTrip"


def _reported_project_path(project_name, projects_text):
    rows = [split_markdown_row(line) for line in projects_text.splitlines()]
    header = next(row for row in rows if "Name" in row and "Path" in row)
    row = next(row for row in rows
               if len(row) == len(header) and row[header.index("Name")] == project_name)
    path = Path(row[header.index("Path")]).resolve()
    assert path.name == project_name, "scratch project must have its own directory: %s" % path
    return path


def _read_project_manifest(path):
    text = (path / "DT-INF" / "PROJECT.PMF").read_text(encoding="utf-8-sig")
    return {key.strip(): value.strip() for line in text.splitlines()
            for key, separator, value in [line.partition(":")] if separator}


def _delete_scratch_project_strict(project_name):
    """Delete only the UUID-named project observed in this test, then prove absence."""
    assert project_name.startswith("ZZImportXmlOptions"), "unexpected cleanup target"
    assert len(project_name.removeprefix("ZZImportXmlOptions")) == 32
    projects = call("list_projects", {})
    assert_ok(projects, "observe scratch project before cleanup")
    stored_path = None
    if _project_state(projects.text, project_name) is not None:
        stored_path = _reported_project_path(project_name, projects.text)
        deleted = call("delete_project", {
            "projectName": project_name, "deleteContent": True, "confirm": True,
        })
        assert_ok(deleted, "delete the observed XML-import scratch project")
    after = call("list_projects", {})
    assert_ok(after, "verify scratch project cleanup")
    assert _project_state(after.text, project_name) is None, \
        "scratch project survived cleanup: %s" % project_name
    if stored_path is not None:
        assert not stored_path.exists(), "scratch project contents survived cleanup: %s" % stored_path


def _preserve_failed_xml_import(project_name, dump_dir, stored_path, temp_root):
    """Discover any created UUID project and preserve both trees before allowing deletion."""
    assert project_name.startswith("ZZImportXmlOptions")
    assert len(project_name.removeprefix("ZZImportXmlOptions")) == 32
    if stored_path is None:
        projects = call("list_projects", {})
        assert_ok(projects, "discover failed XML import project before preserving artifacts")
        if _project_state(projects.text, project_name) is not None:
            stored_path = _reported_project_path(project_name, projects.text)
    assert dump_dir.is_dir(), "failed XML source tree must remain available for preservation"
    sources = [("xml-source", dump_dir)]
    if stored_path is not None:
        assert stored_path.name == project_name and stored_path.is_dir(), \
            "observed failed project tree must remain available: %s" % stored_path
        sources.append(("imported-project", stored_path))
    evidence_dir = Path(tempfile.mkdtemp(prefix="edt_failed_xml_import_")).resolve()
    assert evidence_dir.parent == temp_root
    archive_path = evidence_dir / (project_name + ".zip")
    with zipfile.ZipFile(archive_path, "w", zipfile.ZIP_DEFLATED) as archive:
        for label, directory in sources:
            for path in sorted(directory.rglob("*")):
                if path.is_file():
                    assert path.resolve().is_relative_to(directory.resolve())
                    archive.write(path, label + "/" + path.relative_to(directory).as_posix())
    print("XML import failure artifacts preserved: %s" % archive_path, flush=True)


def _ensure_absent(project_name):
    """Best-effort pre/post clean so a leftover project from a crashed run cannot make the
    round-trip trip over the tool's own "project already exists" guard. Ignores the result."""
    call("delete_project", {"projectName": project_name, "deleteContent": True, "confirm": True})


def _project_state(list_projects_markdown, project_name):
    """The `state` cell list_projects prints for one project, or None when it has no row.

    Parsed the same way harness._all_edt_projects_ready parses the table: a leading "|", the
    name in the first cell and the state in the second.
    """
    for line in list_projects_markdown.splitlines():
        line = line.strip()
        if not line.startswith("|") or set(line) <= set("|- "):
            continue
        cells = [c.strip() for c in line.strip("|").split("|")]
        if len(cells) >= 2 and cells[0] == project_name:
            return cells[1].strip().lower()
    return None


# ──────────────────────────────────────────────────────────────────────────────
# POSITIVE: the real import round-trip, then the existing-project guard
# ──────────────────────────────────────────────────────────────────────────────
@e2e_test(tool="import_configuration_from_xml", kind="write-metadata")
def test_import_round_trip_leaves_a_project_edt_has_started():
    """Export the fixture to XML, import it as a NEW project, and require that EDT has
    actually STARTED the result.

    The assertion that matters is the reported state. A pre-fix server returns success with
    no `state`/`projectReady` in the front matter at all, and the imported project stays
    'not_available' in list_projects indefinitely (the CLI import API parks a blocked start
    latch on it and nothing releases it), so both halves below fail on it.

    Cleanup is unconditional: the throwaway project and the temp dump are removed in finally,
    so the test is repeatable and leaves no workspace residue.
    """
    _ensure_absent(_ROUND_TRIP_PROJECT)
    dump_dir = tempfile.mkdtemp(prefix="edt_import_e2e_")
    try:
        ex = call("export_configuration_to_xml",
                  {"projectName": PROJECT, "outputPath": dump_dir})
        assert_ok(ex, "export the fixture configuration to XML")
        assert os.listdir(dump_dir), "export must produce the XML dump the import reads"

        im = call("import_configuration_from_xml",
                  {"importPath": dump_dir, "projectName": _ROUND_TRIP_PROJECT})
        assert_ok(im, "import the dumped XML into a new project")
        assert_contains(im.text, "project: " + _ROUND_TRIP_PROJECT,
                        "front matter must echo the created project")
        # The honest-state contract: the answer says which of the two states it observed.
        assert_contains(im.text, "projectReady:",
                        "the answer must state whether EDT started the project")
        assert ("state: ready" in im.text) or ("state: importing" in im.text), \
            "front matter must carry state: ready or state: importing, got:\n" + im.text
        if "state: ready" in im.text:
            assert_contains(im.text, "projectReady: true",
                            "a ready project must be reported as ready")
        else:
            assert_contains(im.text, "projectReady: false",
                            "a project still starting must not be reported as ready")
        # Settle on BOTH branches, not just the 'importing' one: the tool's `ready` means the
        # project CONTEXT is started (isStarted + a DtProject exists), while list_projects reports
        # `building` until EDT has finished the derived data. Going straight to the assertion below
        # on the ready branch would be a race, not a check. settle_or_fail, not the bare wait: a
        # wait that ran out would otherwise be dropped on the floor and the assertion below would
        # then fail on a `building` row with no diagnostics about WHY EDT never settled.
        settle_or_fail("the list_projects ground-truth check of the imported project")

        # GROUND TRUTH, independent of the import's own answer: list_projects must report the
        # imported project as an EDT project in state 'ready' - i.e. EDT really started it.
        lp = call("list_projects", {})
        assert_contains(lp.text, _ROUND_TRIP_PROJECT,
                        "the imported project must appear in list_projects")
        state = _project_state(lp.text, _ROUND_TRIP_PROJECT)
        assert state == "ready", \
            "the imported project must reach state 'ready', got %r:\n%s" % (state, lp.text)
    finally:
        _ensure_absent(_ROUND_TRIP_PROJECT)
        shutil.rmtree(dump_dir, ignore_errors=True)

    assert_no_diff("the round-trip only READS the fixture; the import creates a separate project")


@e2e_test(tool="import_configuration_from_xml", kind="write-metadata")
def test_explicit_runtime_version_and_base_project_are_persisted_on_extension_import():
    """Export existing extension XML and prove the two SDK argument meanings on disk."""
    project_name = "ZZImportXmlOptions" + uuid.uuid4().hex
    source_manifest = _read_project_manifest(Path(REPO_ROOT) / TESTS_PROJECT_REL)
    runtime_version = source_manifest.get("Runtime-Version")
    assert runtime_version, "the extension fixture must declare its platform version"
    prefix = "edt_import_xml_options_"
    dump_dir = Path(tempfile.mkdtemp(prefix=prefix)).resolve()
    temp_root = Path(tempfile.gettempdir()).resolve()
    stored_path = None
    cleanup_allowed = True
    try:
        before = call("list_projects", {})
        assert_ok(before, "check the unique scratch project name")
        assert _project_state(before.text, project_name) is None, "scratch project name must be unused"
        exported = call("export_configuration_to_xml", {
            "projectName": TESTS_PROJECT, "outputPath": str(dump_dir),
        })
        assert_ok(exported, "export extension XML without changing the fixture")
        assert any(dump_dir.iterdir()), "extension export must produce importable XML"

        imported = call("import_configuration_from_xml", {
            "importPath": str(dump_dir), "projectName": project_name,
            "runtimeVersion": runtime_version, "baseProjectName": PROJECT,
            "projectNature": "", "xmlVersion": "",
        })
        assert_ok(imported, "import extension with explicit SDK version and base project")
        assert_contains(imported.text, "projectReady:", "the start lifecycle must still report its state")
        settle_or_fail("explicit-version extension import and base-project linkage")
        projects = call("list_projects", {})
        assert_ok(projects, "read back the imported extension's location and state")
        assert _project_state(projects.text, project_name) == "ready", \
            "the extension must become ready:\n" + projects.text
        stored_path = _reported_project_path(project_name, projects.text)
        manifest = _read_project_manifest(stored_path)
        assert manifest.get("Runtime-Version") == runtime_version, \
            "argument three must persist the requested runtime version: %r" % manifest
        assert manifest.get("Base-Project") == PROJECT, \
            "argument four must persist the requested base project: %r" % manifest
        project_xml = (stored_path / ".project").read_text(encoding="utf-8-sig")
        assert_contains(project_xml, "com._1c.g5.v8.dt.core.V8ExtensionNature",
                        "nature must be inferred as extension from XML")
        properties = call("get_configuration_properties", {"projectName": project_name})
        assert_ok(properties, "read the linked extension model independently of its manifest")
        assert_contains(properties.text, "projectKind: Extension", "EDT must resolve an extension model")
    except BaseException as original:
        cleanup_allowed = False
        try:
            _preserve_failed_xml_import(project_name, dump_dir, stored_path, temp_root)
        except BaseException as preservation:
            raise AssertionError(
                "XML import failed (%s: %s); artifact preservation also failed (%s: %s). "
                "Refusing deletion; retain project %s and XML source %s for diagnosis."
                % (type(original).__name__, original, type(preservation).__name__, preservation,
                   project_name, dump_dir)) from original
        cleanup_allowed = True
        raise
    finally:
        if cleanup_allowed:
            try:
                _delete_scratch_project_strict(project_name)
            finally:
                assert dump_dir.parent == temp_root and dump_dir.name.startswith(prefix), \
                    "refuse recursive cleanup outside this test's temporary directory"
                shutil.rmtree(dump_dir)
                assert not dump_dir.exists(), "scratch XML dump must be removed"

    assert_no_diff("XML options import must leave the base fixture unchanged")
    assert_no_diff_rel(TESTS_PROJECT_REL, "XML options import must leave the source extension unchanged")


@e2e_test(tool="import_configuration_from_xml", kind="action")
def test_nonempty_legacy_overrides_are_refused_before_project_creation():
    for key, value in (("projectNature", "com._1c.g5.v8.dt.core.V8ConfigurationNature"),
                       ("xmlVersion", "8.3.20")):
        name = "ZZImportXmlLegacy" + uuid.uuid4().hex
        result = call("import_configuration_from_xml", {
            "importPath": _MISSING_PATH, "projectName": name, key: value,
        })
        error = assert_error(result, "explicit unsupported legacy XML import override")
        assert_error_quality(error, names=[key, value], suggests=["Omit", "runtimeVersion"],
                             ctx="legacy refusal must name the option and the correct contract")
        assert_contains(error, "not supported", "must refuse before source/workspace/API validation")
        assert_not_contains(error, "does not exist", "source validation must not mask the override refusal")
        assert_not_contains(error, "mutationCommitted", "early refusal cannot claim a mutation")
        projects = call("list_projects", {})
        assert_ok(projects, "verify refused override created no project")
        assert _project_state(projects.text, name) is None, "legacy override must not create a project"
    assert_no_diff("legacy XML override refusals must not alter the fixture")


@e2e_test(tool="import_configuration_from_xml", kind="action")
def test_invalid_runtime_versions_are_refused_before_project_creation():
    for version in ("not-a-version", "8.3.bad", "8.3.27.1", "-1.3.27", "0", "0.0.0"):
        name = "ZZImportXmlInvalidVersion" + uuid.uuid4().hex
        result = call("import_configuration_from_xml", {
            "importPath": _MISSING_PATH, "projectName": name, "runtimeVersion": version,
        })
        error = assert_error(result, "invalid explicit XML import runtime version")
        assert_error_quality(error, names=["runtimeVersion", version],
                             suggests=["8.3.27", "omit runtimeVersion"],
                             ctx="refusal must preserve explicit-version intent")
        assert_contains(error, "Invalid runtimeVersion", "must reject before import work")
        assert_not_contains(error, "does not exist", "source validation must not mask version refusal")
        assert_not_contains(error, "mutationCommitted", "early refusal cannot claim a mutation")
        assert_not_contains(error, "mutationOutcomeUnknown", "early refusal cannot have entered the API")
        projects = call("list_projects", {})
        assert_ok(projects, "verify invalid version created no project")
        assert _project_state(projects.text, name) is None, "invalid version must not create a project"
    assert_no_diff("invalid XML runtime version refusals must not alter the base fixture")
    assert_no_diff_rel(TESTS_PROJECT_REL, "invalid runtime versions must not alter the extension fixture")


@e2e_test(tool="import_configuration_from_xml", kind="action")
def test_existing_project_name_is_refused_before_any_import():
    """Valid-shaped args (a real directory + a real project name), but the
    project name is TestConfiguration which ALREADY exists in the workspace.

    This is the deepest validation reachable without triggering a real import:
    the tool resolves the workspace, sees the project exists, and returns the
    "Project already exists" error -> the import API is NEVER invoked. It proves
    the tool refuses to clobber an existing project (a no-op/broken tool that
    skipped this guard would proceed to import and corrupt state).

    The on-disk fixture must be untouched (assert_no_diff): this rejected call
    creates nothing.
    """
    r = call("import_configuration_from_xml", {
        "importPath": _EXISTING_DIR,
        "projectName": PROJECT,
    })
    e = assert_error(r, "import into an already-existing project name")
    # Message names the conflicting project value AND tells the caller what to do
    # ("requires a new project name") -> genuinely actionable.
    assert_error_quality(
        e,
        names=[PROJECT],
        suggests=["new project name"],
        ctx="existing-project guard names the project and the fix",
    )
    # Belt-and-braces on the exact contract wording (the guard, not a generic fail).
    assert_contains(e, "already exists",
                    "must be the existing-project guard, not a generic error")
    assert_no_diff("a rejected import must not touch the committed fixture")


# ──────────────────────────────────────────────────────────────────────────────
# NEGATIVE MATRIX
# ──────────────────────────────────────────────────────────────────────────────
@e2e_test(tool="import_configuration_from_xml", kind="action")
def test_missing_both_required_params_errors_on_importpath_first():
    """No args at all. requireArguments checks importPath FIRST, so the error
    must name importPath (a broken guard that checked the wrong param, or none,
    would name projectName / fail generically)."""
    r = call("import_configuration_from_xml", {})
    e = assert_error(r, "no required params supplied")
    # AUDIT: "importPath is required" names the param but offers no next step
    # (no pointer to export_configuration_to_xml as the source of a dump dir) ->
    # suggests=[] is intentional. Fix-card: make the required-arg guard actionable.
    assert_error_quality(e, names=["importPath"], suggests=[],
                         ctx="missing importPath named first (check order)")
    assert_no_diff("a rejected call must not touch the fixture")


@e2e_test(tool="import_configuration_from_xml", kind="action")
def test_missing_projectname_errors_clearly():
    """importPath present, projectName omitted -> the SECOND required-arg guard
    fires and names projectName specifically (proves both required params are
    enforced, not just the first)."""
    r = call("import_configuration_from_xml", {
        "importPath": _EXISTING_DIR,
    })
    e = assert_error(r, "missing projectName with importPath present")
    # AUDIT: "projectName is required" names the param but is not actionable
    # (no list_projects pointer) -> suggests=[] intentional. Fix-card.
    assert_error_quality(e, names=["projectName"], suggests=[],
                         ctx="missing projectName named (second required guard)")
    assert_no_diff("a rejected call must not touch the fixture")


@e2e_test(tool="import_configuration_from_xml", kind="action")
def test_nonexistent_importpath_errors_and_names_path():
    """Both required params present, but importPath points at a path that does
    not exist -> Files.exists()==false branch. The error must echo the resolved
    (absolute) path so the caller can see exactly what was looked up."""
    r = call("import_configuration_from_xml", {
        "importPath": _MISSING_PATH,
        "projectName": "ImportTargetProject_e2e_zzz",
    })
    e = assert_error(r, "non-existent importPath")
    # The tool normalizes to an absolute path; the leaf name is stable across
    # that normalization, so assert on the distinctive leaf rather than the full
    # absolute string (which varies by checkout location).
    # AUDIT: "importPath does not exist: <abs>" names the bad path but does not
    # suggest a remedy (e.g. run export_configuration_to_xml first to produce a
    # dump) -> suggests=[]. Fix-card: make the path errors actionable.
    assert_error_quality(e, names=["no_such_import_dir_e2e_zzz"], suggests=[],
                         ctx="non-existent importPath echoes the path")
    assert_contains(e, "does not exist",
                    "must be the existence guard, not a generic error")
    assert_no_diff("a rejected call must not touch the fixture")


@e2e_test(tool="import_configuration_from_xml", kind="action")
def test_importpath_is_a_file_not_directory_errors_clearly():
    """importPath exists but is a FILE, not a directory -> the isDirectory()
    branch (distinct from the existence branch above). Confirms the tool
    distinguishes "missing" from "present-but-wrong-kind" (a real, committed
    file is used so the existence check passes and we reach this branch)."""
    r = call("import_configuration_from_xml", {
        "importPath": _EXISTING_FILE,
        "projectName": "ImportTargetProject_e2e_zzz",
    })
    e = assert_error(r, "importPath is a file, not a directory")
    # AUDIT: "importPath is not a directory: <abs>" names the path but is not
    # actionable (no "point importPath at the directory of XML files" hint) ->
    # suggests=[]. Fix-card.
    assert_error_quality(e, names=["CLAUDE.md"], suggests=[],
                         ctx="file-not-directory branch echoes the path")
    assert_contains(e, "not a directory",
                    "must be the directory-kind guard, distinct from existence")
    assert_no_diff("a rejected call must not touch the fixture")


@e2e_test(tool="import_configuration_from_xml", kind="action")
def test_existence_check_precedes_directory_check():
    """Ordering guard: a non-existent path must surface the EXISTENCE error,
    never the directory-kind error. Files.exists() is checked before
    Files.isDirectory(), so a missing path can only ever produce "does not
    exist". This pins the validation order (a refactor that reordered the two
    checks would regress here)."""
    r = call("import_configuration_from_xml", {
        "importPath": _MISSING_PATH,
        "projectName": "ImportTargetProject_e2e_zzz",
    })
    e = assert_error(r, "ordering: existence before directory-kind")
    assert_contains(e, "does not exist",
                    "missing path must hit the existence branch first")
    # And must NOT mislabel a missing path as "present but not a directory".
    assert_not_contains(e, "is not a directory",
                        "a missing path must not surface the directory-kind error")
    assert_no_diff("a rejected call must not touch the fixture")
