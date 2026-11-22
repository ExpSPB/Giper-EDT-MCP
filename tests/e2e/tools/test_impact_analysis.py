"""Exercise exact and capped usage reports on a real indexed EDT project."""

from harness import assert_error, assert_no_diff_rel, assert_ok, call, e2e_test
from tools.test_dependency_graph import FIXTURE, PROJECT, ROOT, _fixture_bytes, _wait_ready


def _report(fqn=ROOT, **options):
    result = call("impact_analysis", {"projectName": PROJECT, "objectFqn": fqn, **options})
    assert_ok(result, f"impact of {fqn} with {options}")
    return result.text


@e2e_test(tool="impact_analysis", kind="read")
def test_impact_analysis_distinguishes_metadata_and_indexed_bsl():
    _wait_ready()
    files_before = _fixture_bytes()
    metadata = _report(skipBsl=True)
    assert "**Scope:** metadata\n" in metadata, metadata
    assert "**Total references:** 2" in metadata, metadata
    assert "Catalog.GraphTail" in metadata and "Catalog.GraphInbound" in metadata, metadata
    assert "GraphUsage" not in metadata, metadata
    assert metadata == _report("Справочник.GraphRoot", skipBsl=True), metadata

    indexed = _report(action="rename")
    assert "**Scope:** metadata and indexed BSL" in indexed, indexed
    assert "**Severity:** MEDIUM" in indexed and "rename_metadata_object" in indexed, indexed
    assert "CommonModules/GraphUsage/Module.bsl" in indexed, indexed
    assert "| 2 |" in indexed, indexed
    assert "**Incomplete search:**" not in indexed, indexed
    assert _fixture_bytes() == files_before, "impact query changed fixture bytes"
    assert_no_diff_rel(FIXTURE, "impact scans preserve fixture files")


@e2e_test(tool="impact_analysis", kind="read")
def test_impact_analysis_reports_authored_configuration_settings():
    _wait_ready()
    report = _report("Language.English", skipBsl=True)
    assert "**Total references:** 1" in report, report
    assert "Configuration.MetadataAnalysisFixture" in report and "defaultLanguage" in report, report
    assert "**Incomplete search:**" not in report, report
    assert_no_diff_rel(FIXTURE, "configuration usage scan preserves fixture files")


@e2e_test(tool="impact_analysis", kind="read")
def test_impact_analysis_distinguishes_exact_zero_from_a_lower_bound():
    files_before = _fixture_bytes()
    empty = _report("Catalog.GraphIsolated", skipBsl=True)
    assert "**Total references:** 0" in empty and "**Severity:** LOW" in empty, empty
    partial = _report(skipBsl=True, limit=1, action="delete")
    assert "**References found:** at least 1" in partial, partial
    assert "reference limit was reached" in partial, partial
    assert "**Severity:**" not in partial and "**Recommendation:**" not in partial, partial
    assert _fixture_bytes() == files_before, "bounded impact query changed fixture bytes"
    assert_no_diff_rel(FIXTURE, "bounded impact scans preserve fixture files")


@e2e_test(tool="impact_analysis", kind="read")
def test_impact_analysis_rejects_unknown_roots_and_bad_options():
    for patch, expected in [({"objectFqn": "Catalog.MissingImpactRoot"}, "MissingImpactRoot"),
                            ({"projectName": "MissingImpactProject"}, "MissingImpactProject"),
                            ({"action": "archive"}, "action"), ({"skipBsl": "maybe"}, "skipBsl"),
                            ({"limit": 501}, "limit")]:
        result = call("impact_analysis", {"projectName": PROJECT, "objectFqn": ROOT, **patch})
        assert_error(result, f"refuse impact {patch}")
        assert expected in result.error_text(), result.error_text()
