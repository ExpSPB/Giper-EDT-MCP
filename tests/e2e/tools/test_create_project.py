"""
e2e tests for create_project (kind: action).

THE TOOL: creates a NEW project in the EDT workspace. The projectKind parameter
selects: configuration | extension | externalObjects.

HAPPY PATH:
- extension round-trip: creates a fresh extension from the fixture base config,
  verifies it appears in list_projects, then deletes it via delete_project (cleanup).
- configuration round-trip: creates a fresh standalone configuration, verifies it
  appears in list_projects, then deletes it.
- externalObjects round-trips: a seeded project exposes its root through the metadata
  read tools, while omitting externalObject still creates an empty import target.

NEGATIVE: missing projectKind, invalid projectKind, missing baseProjectName for
extension, baseProjectName rejected for configuration, invalid externalObject shapes,
duplicate name guard.
"""

from pathlib import Path
import time
import uuid
import xml.etree.ElementTree as ET

from harness import (
    call,
    assert_ok,
    assert_error,
    assert_error_quality,
    assert_contains,
    assert_no_diff,
    settle_or_fail,
    wait_for_project_ready,
    read_disk,
    split_markdown_row,
    e2e_test,
    PROJECT,
)

# Unique project names unlikely to collide with anything in the workspace.
NEW_EXT = "CreateProjTest_Ext_e2e"
NEW_CONFIG = "CreateProjTest_Config_e2e"
NEW_EXT_OBJ = "CreateProjTest_ExtObj_e2e"
NEW_EXT_OBJ_SEEDED = "CreateProjTest_ExtObjSeeded_e2e"
SEEDED_ROOT = "ExternalDataProcessor.CreateProjSeededRoot_e2e"


def _ensure_absent(name):
    """Best-effort pre/post cleanup: remove a leftover project from a prior crashed run."""
    call("delete_project", {"projectName": name, "deleteContent": True, "confirm": True})


def _created_project_configuration(project_name, projects_markdown):
    """Read the newly created project's persisted root from its reported location."""
    rows = [split_markdown_row(line) for line in projects_markdown.splitlines()]
    header = next(row for row in rows if "Name" in row and "Path" in row)
    name_column, path_column = header.index("Name"), header.index("Path")
    row = next(row for row in rows
               if len(row) == len(header) and row[name_column] == project_name)
    path = Path(row[path_column]) / "src" / "Configuration" / "Configuration.mdo"
    deadline = time.monotonic() + 15
    while True:
        try:
            return ET.fromstring(path.read_bytes())
        except (FileNotFoundError, ET.ParseError):
            assert time.monotonic() < deadline, "created Configuration must be exported: %s" % path
            time.sleep(0.2)


# ── NEGATIVE ──────────────────────────────────────────────────────────────────

@e2e_test(tool="create_project", kind="action")
def test_missing_project_kind_errors():
    """No arguments at all — the first required arg 'projectKind' is reported."""
    r = call("create_project", {})
    e = assert_error(r, "no args")
    assert_error_quality(e, names=["projectKind"], suggests=[],
                         ctx="missing 'projectKind' is named in the error")
    assert_no_diff("a rejected call must not touch the fixture")


@e2e_test(tool="create_project", kind="action")
def test_missing_name_errors():
    """'projectKind' present but 'name' missing."""
    r = call("create_project", {"projectKind": "extension"})
    e = assert_error(r, "missing name")
    assert_error_quality(e, names=["name"], suggests=[],
                         ctx="missing 'name' is named in the error")
    assert_no_diff("a rejected call must not touch the fixture")


@e2e_test(tool="create_project", kind="action")
def test_invalid_project_kind_errors():
    """An unknown projectKind value must be rejected with a clear error."""
    r = call("create_project", {"projectKind": "bogusKind", "name": "MyProject"})
    e = assert_error(r, "invalid projectKind")
    assert_error_quality(e, names=["bogusKind"], suggests=[],
                         ctx="invalid projectKind value is named in the error")
    assert_no_diff("a rejected call must not touch the fixture")


@e2e_test(tool="create_project", kind="action")
def test_missing_base_project_name_for_extension_errors():
    """Extension kind without baseProjectName must fail with a clear error."""
    r = call("create_project", {"projectKind": "extension", "name": NEW_EXT})
    e = assert_error(r, "missing baseProjectName for extension")
    assert_error_quality(e, names=["baseProjectName"], suggests=["list_projects"],
                         ctx="missing baseProjectName is named in the error")
    assert_no_diff("a rejected call must not touch the fixture")


@e2e_test(tool="create_project", kind="action")
def test_base_project_name_rejected_for_configuration():
    """baseProjectName supplied with kind=configuration must be rejected."""
    r = call("create_project", {
        "projectKind": "configuration",
        "name": NEW_CONFIG,
        "baseProjectName": PROJECT,
    })
    e = assert_error(r, "baseProjectName rejected for configuration")
    assert_error_quality(e, names=["baseProjectName"], suggests=[],
                         ctx="baseProjectName is named in the error")
    assert_no_diff("a rejected call must not touch the fixture")


@e2e_test(tool="create_project", kind="action")
def test_nonexistent_base_project_errors():
    """A base project name that does not exist in the workspace."""
    bad = "NoSuchBase_cep_zzz"
    r = call("create_project",
             {"projectKind": "extension", "name": NEW_EXT, "baseProjectName": bad})
    e = assert_error(r, "nonexistent base project")
    assert_error_quality(e, names=[bad], suggests=["list_projects"],
                         ctx="nonexistent base project named + list_projects hint")
    assert_no_diff("a rejected call must not touch the fixture")


@e2e_test(tool="create_project", kind="action")
def test_external_object_bare_name_errors_with_expected_shape():
    """A root must include its bilingual type token; a bare Name is never guessed."""
    bad = "BareExternalRoot"
    r = call("create_project", {
        "projectKind": "externalObjects",
        "name": NEW_EXT_OBJ,
        "externalObject": bad,
    })
    e = assert_error(r, "bare externalObject Name")
    assert_error_quality(e, names=[bad],
                         suggests=["ExternalDataProcessor.<Name>", "ExternalReport.<Name>"],
                         ctx="bare root error names the value and both accepted shapes")
    assert_no_diff("a rejected call must not touch the fixture")


@e2e_test(tool="create_project", kind="action")
def test_external_object_unknown_type_errors_with_valid_kinds():
    """An unknown root type is rejected with the two kinds EDT can attach."""
    bad = "UnknownExternalRoot.MyObject"
    r = call("create_project", {
        "projectKind": "externalObjects",
        "name": NEW_EXT_OBJ,
        "externalObject": bad,
    })
    e = assert_error(r, "unknown externalObject type")
    assert_error_quality(e, names=[bad],
                         suggests=["ExternalDataProcessor", "ExternalReport"],
                         ctx="unknown root type names the value and both valid kinds")
    assert_no_diff("a rejected call must not touch the fixture")


# ── HAPPY PATH + DUPLICATE GUARD (extension) ──────────────────────────────────

@e2e_test(tool="create_project", kind="action")
def test_create_extension_then_delete():
    """Create a fresh extension from the fixture base, verify it, then clean up."""
    effective_name = PROJECT + "." + NEW_EXT
    _ensure_absent(effective_name)
    base_xml = read_disk("src/Configuration/Configuration.mdo")
    base = ET.fromstring(base_xml)
    default_language_fqn = base.findtext("defaultLanguage")
    assert default_language_fqn, "the base fixture must declare a default Language"
    default_language_name = default_language_fqn.split(".", 1)[1]
    base_language = next(language for language in base.findall("languages")
                         if language.findtext("name") == default_language_name)
    try:
        r = call("create_project",
                 {"projectKind": "extension", "name": NEW_EXT, "baseProjectName": PROJECT})
        assert_ok(r, "create_project extension happy path")
        assert r.structured is not None, "response must carry structuredContent"
        assert r.structured.get("action") == "created", \
            "action must be 'created', got %r" % (r.structured,)
        assert r.structured.get("project") is not None, \
            "project must be present in the response"
        assert r.structured.get("projectKind") == "extension", \
            "projectKind must be 'extension', got %r" % r.structured.get("projectKind")
        ext_project_name = r.structured["project"]
        assert ext_project_name == PROJECT + "." + NEW_EXT, \
            "project must default to '<base>.<name>', got %r" % ext_project_name

        # Verify it appears in list_projects
        wait_for_project_ready()
        lp = call("list_projects", {})
        assert_contains(lp.text, ext_project_name,
                        "new extension must appear in list_projects")

        # ADOPTED alone is insufficient: EDT derived updates require the typed extension
        # property states, and the wizard also adopts the base's default Language.
        stored = _created_project_configuration(ext_project_name, lp.text)
        stored_uuid = stored.attrib.get("uuid")
        uuid.UUID(stored_uuid)
        assert stored_uuid != base.attrib["uuid"], "the new root must have a fresh UUID"
        assert stored.findtext("objectBelonging") == "Adopted", "root must be adopted"
        extension = stored.find("extension")
        assert extension is not None, "ADOPTED root must persist its extension metadata"
        xsi_type = "{http://www.w3.org/2001/XMLSchema-instance}type"
        assert extension.attrib.get(xsi_type, "").split(":")[-1] == "ConfigurationExtension", \
            "persisted extension must be the ConfigurationExtension subtype"
        assert extension.findtext("defaultLanguage") == "Checked", \
            "adopter must preserve the default-Language property state"
        assert extension.findtext("compatibilityMode") == "Checked", \
            "adopter must preserve the compatibility-mode property state"
        assert stored.findtext("keepMappingToExtendedConfigurationObjectsByIDs") == "true", \
            "the new root must retain mapping to base-object IDs"
        assert stored.findtext("defaultLanguage") == default_language_fqn
        language = next(language for language in stored.findall("languages")
                        if language.findtext("name") == default_language_name)
        assert language.findtext("objectBelonging") == "Adopted", \
            "the default Language must be adopted, not a new unrelated Language"
        assert language.attrib.get("extendedConfigurationObject") == base_language.attrib["uuid"], \
            "adopted default Language must map to its source UUID"
        details = call("get_metadata_details", {
            "projectName": ext_project_name, "objectFqns": [default_language_fqn],
        })
        assert_ok(details, "read the default Language from the created extension model")
        assert_contains(details.text, "Language: " + default_language_name,
                        "model readback must resolve the adopted default Language")
        assert_contains(details.text, "**Origin:** core (adopted)",
                        "model readback must retain the default Language's base origin")
        assert read_disk("src/Configuration/Configuration.mdo") == base_xml, \
            "extension creation must not modify the base Configuration"

        # Duplicate guard: calling again with the same computed name must fail
        r2 = call("create_project",
                  {"projectKind": "extension", "name": NEW_EXT, "baseProjectName": PROJECT})
        e2 = assert_error(r2, "duplicate extension name")
        assert_error_quality(e2, names=[ext_project_name], suggests=[],
                             ctx="duplicate project name is named in the error")

    finally:
        # The extension's effective project name is always '<base>.<name>'; clean up
        # only that. Deleting the bare NEW_EXT would risk an unrelated same-named project.
        _ensure_absent(PROJECT + "." + NEW_EXT)

    assert_no_diff("the fixture must be untouched after the round-trip")


# ── HAPPY PATH (configuration) ────────────────────────────────────────────────

@e2e_test(tool="create_project", kind="action")
def test_create_configuration_then_delete():
    """Create a fresh standalone configuration, verify it, then clean up."""
    _ensure_absent(NEW_CONFIG)
    try:
        r = call("create_project", {"projectKind": "configuration", "name": NEW_CONFIG})
        assert_ok(r, "create_project configuration happy path")
        assert r.structured is not None, "response must carry structuredContent"
        assert r.structured.get("action") == "created", \
            "action must be 'created', got %r" % (r.structured,)
        assert r.structured.get("projectKind") == "configuration", \
            "projectKind must be 'configuration', got %r" % r.structured.get("projectKind")
        proj_name = r.structured.get("project")
        assert proj_name == NEW_CONFIG, \
            "project must default to 'name', got %r" % proj_name

        # Verify it appears in list_projects
        wait_for_project_ready()
        lp = call("list_projects", {})
        assert_contains(lp.text, NEW_CONFIG,
                        "new configuration must appear in list_projects")

    finally:
        _ensure_absent(NEW_CONFIG)

    assert_no_diff("the fixture must be untouched after the round-trip")


# ── HAPPY PATH (externalObjects) ──────────────────────────────────────────────

@e2e_test(tool="create_project", kind="action")
def test_create_external_objects_with_seeded_root_then_delete():
    """Seed the project root and resolve it through both metadata read routes."""
    _ensure_absent(NEW_EXT_OBJ_SEEDED)
    try:
        r = call("create_project", {
            "projectKind": "externalObjects",
            "name": NEW_EXT_OBJ_SEEDED,
            "externalObject": SEEDED_ROOT,
        })
        assert_ok(r, "create_project externalObjects with seeded root")
        assert r.structured is not None, "response must carry structuredContent"
        assert r.structured.get("project") == NEW_EXT_OBJ_SEEDED, \
            "seeded project must use the requested project name, got %r" % r.structured
        settle_or_fail("reading the root seeded by create_project")

        roots = call("get_metadata_objects", {"projectName": NEW_EXT_OBJ_SEEDED})
        assert_ok(roots, "list the newly seeded external-object root")
        assert_contains(roots.text, "CreateProjSeededRoot_e2e",
                        "get_metadata_objects must see the seeded root Name")
        assert_contains(roots.text, "ExternalDataProcessor",
                        "get_metadata_objects must see the seeded root type")

        details = call("get_metadata_details", {
            "projectName": NEW_EXT_OBJ_SEEDED,
            "objectFqns": [SEEDED_ROOT],
        })
        assert_ok(details, "read the newly seeded external-object root")
        assert_contains(details.text, "ExternalDataProcessor: CreateProjSeededRoot_e2e",
                        "get_metadata_details must resolve the seeded root FQN")
    finally:
        _ensure_absent(NEW_EXT_OBJ_SEEDED)

    assert_no_diff("the fixture must be untouched after the seeded-project round-trip")

@e2e_test(tool="create_project", kind="action")
def test_create_external_objects_then_delete():
    """Omitting externalObject must preserve the empty-project import workflow."""
    _ensure_absent(NEW_EXT_OBJ)
    try:
        r = call("create_project", {"projectKind": "externalObjects", "name": NEW_EXT_OBJ})
        assert_ok(r, "create_project externalObjects happy path")
        assert r.structured is not None, "response must carry structuredContent"
        assert r.structured.get("action") == "created", \
            "action must be 'created', got %r" % (r.structured,)
        assert r.structured.get("projectKind") == "externalObjects", \
            "projectKind must be 'externalObjects', got %r" % r.structured.get("projectKind")
        proj_name = r.structured.get("project")
        assert proj_name == NEW_EXT_OBJ, \
            "project must default to 'name', got %r" % proj_name

        # Verify it appears in list_projects and that the model remains empty.
        settle_or_fail("checking an unseeded external-objects project")
        lp = call("list_projects", {})
        assert_contains(lp.text, NEW_EXT_OBJ,
                        "new externalObjects project must appear in list_projects")

        roots = call("get_metadata_objects", {"projectName": NEW_EXT_OBJ})
        assert_ok(roots, "list roots of an unseeded external-objects project")
        assert_contains(roots.text, "**Total:** 0 objects",
                        "omitting externalObject must leave the project empty")
        assert_contains(roots.text, "No metadata objects found.",
                        "the empty project must report that it has no roots")

    finally:
        _ensure_absent(NEW_EXT_OBJ)

    assert_no_diff("the fixture must be untouched after the round-trip")
