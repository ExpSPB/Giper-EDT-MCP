"""Exercise the metadata graph on the dedicated MetadataAnalysisFixture project."""

from hashlib import sha256
from pathlib import Path

from harness import REPO_ROOT, assert_error, assert_no_diff_rel, assert_ok, call, e2e_test, wait_for_named_projects_ready

PROJECT = "MetadataAnalysisFixture"
FIXTURE = "tests/fixtures/metadata-analysis"
ROOT = "Catalog.GraphRoot"
NEXT = "Catalog.GraphNext"
TAIL = "Catalog.GraphTail"
INBOUND = "Catalog.GraphInbound"


def _wait_ready():
    states = wait_for_named_projects_ready([PROJECT])
    assert states.get(PROJECT) == "ready", states


def _fixture_bytes():
    root = Path(REPO_ROOT) / FIXTURE
    return {str(file.relative_to(root)): sha256(file.read_bytes()).digest()
            for file in root.rglob("*") if file.is_file()}


def _graph(fqn=ROOT, **options):
    result = call("dependency_graph", {"projectName": PROJECT, "objectFqn": fqn, **options})
    assert_ok(result, f"graph of {fqn} with {options}")
    assert isinstance(result.structured, dict), result.text
    return result.structured


@e2e_test(tool="dependency_graph", kind="read")
def test_dependency_graph_direction_depth_cycles_and_duplicate_references():
    _wait_ready()
    files_before = _fixture_bytes()
    before = _graph(direction="out", depth=1)
    assert before["nodes"] == [{"fqn": ROOT, "depth": 0}, {"fqn": NEXT, "depth": 1}], before
    assert not before["partial"] and not before["cycles"], before
    outgoing = before["edges"]
    assert len(outgoing) == 1 and outgoing[0]["count"] == 2, before
    assert (outgoing[0]["from"], outgoing[0]["to"]) == (ROOT, NEXT), before

    incoming = _graph(direction="in", depth=1)
    assert {(edge["from"], edge["to"]) for edge in incoming["edges"]} == {(TAIL, ROOT), (INBOUND, ROOT)}, incoming
    assert {node["fqn"]: node["depth"] for node in incoming["nodes"]} == {ROOT: 0, TAIL: 1, INBOUND: 1}, incoming
    assert not incoming["partial"], incoming

    two_steps = _graph(direction="out", depth=2)
    assert {node["fqn"]: node["depth"] for node in two_steps["nodes"]}[TAIL] == 2, two_steps
    cycle = _graph(direction="both", depth=3)
    assert cycle["cycles"] == [sorted([ROOT, NEXT, TAIL])], cycle
    assert cycle == _graph("Справочник.GraphRoot", direction="both", depth=3), cycle
    assert [edge for edge in cycle["edges"] if (edge["from"], edge["to"]) == (ROOT, NEXT)] == outgoing, cycle
    assert {node["fqn"] for node in cycle["nodes"]} == {ROOT, NEXT, TAIL, INBOUND}, cycle
    assert _fixture_bytes() == files_before, "graph query changed fixture bytes"
    assert_no_diff_rel(FIXTURE, "dependency graph reads preserve fixture files")


@e2e_test(tool="dependency_graph", kind="read")
def test_dependency_graph_limits_and_isolated_object():
    files_before = _fixture_bytes()
    isolated = _graph("Catalog.GraphIsolated")
    assert isolated["nodes"] == [{"fqn": "Catalog.GraphIsolated", "depth": 0}], isolated
    assert isolated["edges"] == [] and not isolated["partial"], isolated
    exact = _graph(direction="out", depth=1, maxNodes=2, maxEdges=1)
    assert not exact["partial"], exact
    for params, reason in [({"maxNodes": 2}, "nodeLimit"), ({"maxEdges": 1}, "edgeLimit")]:
        capped = _graph(direction="out", depth=3, **params)
        assert capped["partial"] and reason in capped["partialReasons"], capped
        vertices = {node["fqn"] for node in capped["nodes"]}
        assert capped["edges"], capped
        assert all({edge["from"], edge["to"]} <= vertices for edge in capped["edges"]), capped
    assert_ok(call("get_metadata_details", {"projectName": PROJECT, "objectFqns": [ROOT]}), "model after limits")
    assert _fixture_bytes() == files_before, "bounded graph query changed fixture bytes"
    assert_no_diff_rel(FIXTURE, "limited graph reads preserve fixture files")


@e2e_test(tool="dependency_graph", kind="read")
def test_dependency_graph_rejects_invalid_roots_and_arguments():
    base = {"projectName": PROJECT, "objectFqn": ROOT}
    for patch, expected in [({"objectFqn": "Catalog.UnknownGraphObject"}, "UnknownGraphObject"),
                            ({"objectFqn": "Catalog.Graph root display name"}, "programmatic"),
                            ({"objectFqn": f"{ROOT}.Attribute.Next"}, "top-level"),
                            ({"projectName": "MissingGraphProject"}, "MissingGraphProject"),
                            ({"direction": "sideways"}, "direction"), ({"depth": 2.5}, "depth")]:
        result = call("dependency_graph", base | patch)
        assert_error(result, f"refuse graph {patch}")
        assert expected in result.error_text(), result.error_text()
