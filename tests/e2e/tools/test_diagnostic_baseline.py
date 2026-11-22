"""Live diagnostic baseline round trips using controlled BSL errors."""

from collections import Counter
from time import monotonic, sleep

from harness import (
    PROJECT, _fail, assert_contains, assert_error, assert_error_quality, assert_no_diff,
    assert_ok, call, e2e_test, read_disk, settle_or_fail, split_markdown_row,
)


ERROR_MODULE = "Error"
EMPTY_MODULE = "OK"
SCOPED_MODULES = [EMPTY_MODULE, "Calc"]
OUTSIDE_MODULE = "DrySignal"
UNKNOWN_FQN = "CommonModule.NoSuchDiagnosticBaselineModule"
BROKEN_SOURCE = "E2eBaselineSyntaxProbe\n"
PROBE_CHECK = "bsl-legacy-check-simple-statement"


def _fqn(module):
    return "CommonModule." + module


def _module_path(module):
    return f"CommonModules/{module}/Module.bsl"


def _payload(result, context):
    assert_ok(result, context)
    if not isinstance(result.structured, dict):
        _fail(f"{context} must return structuredContent: {result.text!r}")
    return result.structured


def _save_baseline(modules=()):
    scope = [_fqn(module) for module in modules]
    args = {"projectName": PROJECT, "operation": "save"}
    if scope:
        args["objectFqns"] = scope
    saved = _payload(call("diagnostic_baseline", args), "save diagnostic baseline")
    if (saved.get("projectName") != PROJECT or saved.get("scope") != scope
            or not saved.get("savedAt") or type(saved.get("diagnosticsTotal")) is not int):
        _fail(f"invalid diagnostic baseline for {PROJECT}, scope {scope!r}: {saved!r}")
    return saved


def _clear_baseline(expected=None):
    result = call("diagnostic_baseline", {"projectName": PROJECT, "operation": "clear"})
    cleared = _payload(result, "clear diagnostic baseline")
    actual = cleared.get("cleared")
    if cleared.get("projectName") != PROJECT or type(actual) is not bool or (
            expected is not None and actual is not expected):
        _fail(f"clear must report cleared={expected} for {PROJECT}: {cleared!r}")


def _write_source(module, source):
    path = _module_path(module)
    result = call("write_module_source", {
        "projectName": PROJECT, "modulePath": path, "mode": "replace", "overwrite": True,
        "skipSyntaxCheck": True, "source": source,
    })
    assert_ok(result, f"write diagnostic probe to {path}")
    saved = read_disk("src/" + path).removeprefix("\ufeff")
    if saved.splitlines() != source.splitlines():
        _fail(f"unexpected source on disk in {path}: expected {source!r}, got {saved!r}")


def _revalidate(modules):
    settle_or_fail("revalidate diagnostic baseline modules")
    result = call("revalidate_objects", {"projectName": PROJECT, "objects": [_fqn(m) for m in modules]})
    assert_ok(result, "revalidate diagnostic baseline modules")
    assert_contains(result.text, f"objectsFound: {len(modules)}", "all probe modules must be revalidated")


def _wait_for_errors(modules, expected, line=None):
    args = {"projectName": PROJECT, "objectFqns": [_fqn(m) for m in modules], "checkId": PROBE_CHECK,
            "modulePaths": [_module_path(m) for m in modules]}
    counts = Counter(_module_path(module) for module in expected)
    deadline = monotonic() + 90
    while True:
        current = _payload(call("get_project_errors", args), "read current BSL errors")
        rows = current.get("problems")
        if not isinstance(rows, list):
            _fail(f"ordinary exact-address query must provide problem rows: {current!r}")
        if (Counter(row.get("modulePath") for row in rows) == counts
                and (line is None or all(row.get("line") == line for row in rows))):
            return rows
        if monotonic() >= deadline:
            _fail(f"validation did not publish {counts!r} at line {line!r}: {rows!r}")
        sleep(2)


def _compare(saved, *, total, resolved, found=None, **filters):
    args = {"projectName": PROJECT, "sinceBaseline": True, **filters}
    delta = _payload(call("get_project_errors", args), "compare BSL diagnostics to baseline")
    found = total if found is None else found
    next_offset = filters.get("offset", 0) + found
    expected = {
        "baselineSavedAt": saved["savedAt"], "baselineScope": saved["scope"],
        "comparisonComplete": True, "resolvedSinceBaseline": resolved,
        "problemsFound": found, "problemsTotal": total,
        "nextOffset": next_offset, "hasMore": next_offset < total,
    }
    for key, value in expected.items():
        if key not in delta or type(delta[key]) is not type(value) or delta[key] != value:
            _fail(f"comparison expected {key}={value!r}: {delta!r}")
    report = delta.get("report")
    if not isinstance(report, str):
        _fail(f"comparison must carry the Markdown report: {delta!r}")
    table = [split_markdown_row(line) for line in report.splitlines() if line.startswith("|")]
    rows = table[2:]
    if len(rows) != found or (found and table[0][:4] != ["Description", "Location", "Module path", "Line"]):
        _fail(f"comparison must render {found} problem rows: {report}")
    problems = delta.get("problems")
    if not isinstance(problems, list) or [row.get("modulePath") for row in problems] != [row[2] for row in rows]:
        _fail(f"structured comparison rows must match the Markdown page: {delta!r}")
    return rows


@e2e_test(tool="diagnostic_baseline", kind="write-metadata")
def test_diagnostic_baseline_tracks_replaced_and_shifted_diagnostics():
    """Moving the same syntax error to another module fixes one problem and introduces one."""
    modules = [ERROR_MODULE, EMPTY_MODULE]
    filters = {"checkId": PROBE_CHECK, "modulePaths": [_module_path(m) for m in modules]}
    _write_source(ERROR_MODULE, BROKEN_SOURCE)
    _revalidate(modules)
    original, = _wait_for_errors(modules, [ERROR_MODULE])
    if type(original.get("line")) is not int:
        _fail(f"the fixture syntax error must have a saved line: {original!r}")
    try:
        saved = _save_baseline()
        if saved["diagnosticsTotal"] < 1:
            _fail(f"baseline must include the fixture syntax error: {saved!r}")
        _compare(saved, total=0, resolved=0, **filters)

        _write_source(ERROR_MODULE, "// Line shift for baseline comparison\n\n" + BROKEN_SOURCE)
        _revalidate(modules)
        _wait_for_errors(modules, [ERROR_MODULE], line=original["line"] + 2)
        _compare(saved, total=0, resolved=0, **filters)

        _write_source(ERROR_MODULE, "// Fixed fixture syntax error\n")
        _write_source(EMPTY_MODULE, BROKEN_SOURCE)
        _revalidate(modules)
        replacement, = _wait_for_errors(modules, [EMPTY_MODULE], line=original["line"])
        for key in ("message", "checkCode"):
            if original.get(key) != replacement.get(key):
                _fail(f"expected the same syntax error in a different module: {original!r}, {replacement!r}")
        row, = _compare(saved, total=1, resolved=1, **filters)
        if row[0] != replacement["message"] or row[2:4] != [_module_path(EMPTY_MODULE), str(original["line"])]:
            _fail(f"comparison must render the new error in {EMPTY_MODULE}: {row!r}")

        _write_source(EMPTY_MODULE, "// Fixed new syntax error\n")
        _revalidate(modules)
        _wait_for_errors(modules, [])
        _compare(saved, total=0, resolved=1, **filters)
    finally:
        _clear_baseline()


@e2e_test(tool="diagnostic_baseline", kind="write-metadata")
def test_diagnostic_baseline_applies_object_scope_before_paging():
    """Three current errors produce exactly two new rows in the saved scope."""
    modules = [*SCOPED_MODULES, OUTSIDE_MODULE]
    filters = {"checkId": PROBE_CHECK, "modulePaths": [_module_path(m) for m in modules]}
    _revalidate(modules)
    _wait_for_errors(modules, [])
    try:
        saved = _save_baseline(SCOPED_MODULES)
        for module in modules:
            source = read_disk("src/" + _module_path(module)).removeprefix("\ufeff")
            _write_source(module, source + "\n" + BROKEN_SOURCE)
        _revalidate(modules)
        _wait_for_errors(modules, modules)

        full = _compare(saved, total=2, resolved=0, **filters)
        if Counter(row[2] for row in full) != Counter(_module_path(m) for m in SCOPED_MODULES):
            _fail(f"comparison must contain exactly the modules in the saved scope: {full!r}")
        first = _compare(saved, total=2, resolved=0, found=1, limit=1, **filters)
        second = _compare(saved, total=2, resolved=0, found=1, limit=1, offset=1, **filters)
        if first + second != full:
            _fail(f"pages must partition the new rows in order: {first!r}, {second!r} vs {full!r}")
        _compare(saved, total=0, resolved=0, checkId=PROBE_CHECK, modulePaths=[_module_path(OUTSIDE_MODULE)])
    finally:
        _clear_baseline()


@e2e_test(tool="diagnostic_baseline", kind="action")
def test_diagnostic_baseline_rejects_missing_snapshot_after_idempotent_clear():
    """A rejected save leaves the valid snapshot readable until it is cleared."""
    try:
        saved = _save_baseline([ERROR_MODULE])
        invalid_operation = call("diagnostic_baseline", {"projectName": PROJECT, "operation": "publish"})
        assert_error_quality(assert_error(invalid_operation, "invalid baseline operation"),
                             names=["operation", "publish"], suggests=["save", "clear"])
        missing_project = call("get_project_errors", {"sinceBaseline": True})
        assert_error_quality(assert_error(missing_project, "comparison without a project"),
                             names=["sinceBaseline", "projectName"], suggests=["exact EDT projectName"])
        scoped_clear = call("diagnostic_baseline", {
            "projectName": PROJECT, "operation": "clear", "objectFqns": [_fqn(ERROR_MODULE)],
        })
        assert_error_quality(assert_error(scoped_clear, "clear with a scope"),
                             names=["objectFqns"], suggests=["omit"])
        invalid_save = call("diagnostic_baseline", {
            "projectName": PROJECT, "operation": "save", "objectFqns": [_fqn(ERROR_MODULE), UNKNOWN_FQN],
        })
        assert_error_quality(assert_error(invalid_save, "save with an unknown object"),
                             names=[UNKNOWN_FQN], suggests=["get_metadata_objects"])
        _compare(saved, total=0, resolved=0)
        _clear_baseline(True)
        _clear_baseline(False)

        missing = call("get_project_errors", {"projectName": PROJECT, "sinceBaseline": True})
        assert_error_quality(assert_error(missing, "compare after clearing the baseline"),
                             names=[PROJECT], suggests=["diagnostic_baseline", "save"])
    finally:
        _clear_baseline()
    assert_no_diff("managing the baseline must leave the project source tree clean")
