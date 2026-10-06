"""
e2e tests for modify_metadata attaching / detaching an owner in a CommonAttribute's content list.

A common attribute (`CommonAttribute.<Name>`) is shared across many objects; which objects carry it
- its OWNERS - live in the common attribute's `content` list, each with a per-owner `use` flag. This
is edited through a sibling `content` payload (mirroring the Role `rights[]` precedent), NOT through
`properties`:

    content=[{op?:'add'|'remove' (default add), metadata:'Catalog.X', use?:'Use'|'DontUse'|'Auto'}]

Adding is idempotent (an already-listed owner has its `use` UPDATED, counted under `updated` rather
than `added`); removing detaches by the owner FQN. The change goes through a BM write transaction and
force-exports the CommonAttribute `.mdo`. The success payload carries a `content` counts object
{added, updated, removed}. Read the current content with get_metadata_details on the CommonAttribute
FQN (a "Content" table with Metadata / Use columns naming the owner FQN and its current use).

reset: kind="write-metadata" -> verified disk and model cleanup after each test.

Fixture (shipped TestConfiguration): CommonAttribute.CommonAttribute has NO <content> yet and uses
dataSeparation=DontUse; Catalog.Catalog is a valid owner for that simple common attribute;
CommonModule.OK is a NON-owner kind. The fixture has no Constant or separator common attribute,
so separator/Constant scenarios create both inside their tests.
"""

from harness import (
    call,
    assert_ok,
    assert_error,
    assert_error_quality,
    assert_no_diff,
    poll_diff_contains,
    poll_disk_lacks,
    read_disk,
    split_markdown_row,
    wait_for_project_ready,
    e2e_test,
    PROJECT,
)

# The shipped common attribute (no content in the fixture) and the on-disk file that owns its content.
CA = "CommonAttribute.CommonAttribute"
CA_MDO = "src/CommonAttributes/CommonAttribute/CommonAttribute.mdo"
OWNER = "Catalog.Catalog"                       # a valid owner kind present in the fixture
# The structural on-disk element that pins WHICH owner is attached (never the bare name, which would
# false-match a collection reference elsewhere). This is the load-bearing add/remove proof.
OWNER_MDO_TOKEN = "<metadata>Catalog.Catalog</metadata>"


def _details_text(fqn=CA):
    r = call("get_metadata_details", {"projectName": PROJECT, "objectFqns": [fqn]})
    assert_ok(r, "get_metadata_details for " + fqn)
    return r.text


def _create_ok(fqn, ctx):
    """Create setup metadata and require readiness before the dependent mutation."""
    r = call("create_metadata", {"projectName": PROJECT, "fqn": fqn})
    assert_ok(r, ctx)
    assert wait_for_project_ready(), "the project must settle after creating " + fqn
    return r


def _common_attribute_mdo(name):
    return "src/CommonAttributes/%s/%s.mdo" % (name, name)


def _content_rows(text, fqn=CA):
    """Read the target's public Content table; omitted Content means an empty collection."""
    lines = text.splitlines()
    title = "## CommonAttribute: " + fqn.split(".", 1)[1]
    headings = [i for i, line in enumerate(lines) if line.strip() == title]
    assert len(headings) == 1, "expected exactly one target object heading:\n%s" % text[:700]
    start = headings[0] + 1
    end = next((i for i in range(start, len(lines)) if lines[i].startswith("## ")), len(lines))
    sections = [i for i in range(start, end) if lines[i].strip() == "### Content"]
    assert len(sections) <= 1, "duplicate Content sections:\n%s" % text[:700]
    if not sections:
        return []
    start = sections[0] + 1
    end = next((i for i in range(start, end) if lines[i].startswith("#")), end)
    table = [split_markdown_row(line) for line in lines[start:end] if line.lstrip().startswith("|")]
    assert len(table) >= 3 and table[0] == ["Metadata", "Use"], \
        "Content must expose owner rows under Metadata / Use:\n%s" % text[:700]
    assert len(table[1]) == 2 and all(
        "-" in cell and set(cell) <= set("-:") for cell in table[1]), \
        "Content must have a Markdown table separator:\n%s" % text[:700]
    rows = table[2:]
    assert all(len(row) == 2 and row[0] and row[1] in ("Use", "DontUse", "Auto")
               for row in rows), "malformed Content row:\n%s" % text[:700]
    return [tuple(row) for row in rows]


def _assert_content_rows(text, expected, ctx, fqn=CA):
    rows = _content_rows(text, fqn)
    assert rows == expected, "%s: expected %r, got %r:\n%s" % (ctx, expected, rows, text[:700])


# ──────────────────────────────────────────────────────────────────────────────
# Happy — attach an owner: the <content> block lands on disk AND the model shows it
# ──────────────────────────────────────────────────────────────────────────────

@e2e_test(tool="modify_metadata", kind="write-metadata")
def test_add_owner_persists_and_get_details_renders_content_columns():
    # Precondition (anti-cheat): the owner is NOT already attached, so a no-op would FAIL the diff.
    before = _details_text()
    _assert_content_rows(before, [], "the fixture must start with an empty content list")

    r = call("modify_metadata", {
        "projectName": PROJECT, "fqn": CA,
        "content": [{"op": "add", "metadata": OWNER, "use": "Use"}],
    })
    assert_ok(r, "attach Catalog.Catalog as an owner of the common attribute")
    assert r.structured.get("action") == "modified", "must report modified: %r" % (r.structured,)
    counts = r.structured.get("content") or {}
    assert counts.get("added") == 1, "exactly one owner must be added: %r" % (r.structured,)
    assert counts.get("updated") == 0 and counts.get("removed") == 0, \
        "a fresh add must not update / remove: %r" % (r.structured,)

    # (1) ON-DISK structure: the owner FQN lands inside a <content> block in the CommonAttribute .mdo.
    poll_diff_contains(OWNER_MDO_TOKEN,
                       ctx="the attached owner must land in a <content> block on disk")
    # (2) MODEL read-back: the common attribute now carries a content item (empty -> one row).
    after = _details_text()
    _assert_content_rows(after, [(OWNER, "Use")],
                         "the model must show exactly one attached owner with Use")


# ──────────────────────────────────────────────────────────────────────────────
# Happy — a separator common attribute accepts a Constant owner (#507)
# ──────────────────────────────────────────────────────────────────────────────

@e2e_test(tool="modify_metadata", kind="write-metadata")
def test_separator_accepts_constant_owner():
    separator_name = "E2EDataSeparator507"
    constant_name = "E2EDataSeparatorConstant507"
    separator_fqn = "CommonAttribute." + separator_name
    constant_fqn = "Constant." + constant_name
    owner_token = "<metadata>%s</metadata>" % constant_fqn

    _create_ok(constant_fqn, "create the Constant owner for the separator")
    _create_ok(separator_fqn, "create the common attribute that will become a separator")

    make_separator = call("modify_metadata", {
        "projectName": PROJECT,
        "fqn": separator_fqn,
        "properties": [{"name": "dataSeparation", "value": "Separate"}],
    })
    assert_ok(make_separator, "set the target common attribute dataSeparation to Separate")
    assert make_separator.structured.get("persisted") is True, \
        "the separator property must persist before attaching an owner: %r" % (make_separator.structured,)
    assert wait_for_project_ready(), "the separator must settle before attaching its owner"

    before = _details_text(separator_fqn)
    _assert_content_rows(before, [], "the new separator must start with no owners", fqn=separator_fqn)

    attach = call("modify_metadata", {
        "projectName": PROJECT,
        "fqn": separator_fqn,
        "content": [{"metadata": constant_fqn, "use": "Use"}],
    })
    assert_ok(attach, "attach a Constant owner to a separator common attribute")
    counts = attach.structured.get("content") or {}
    # A Constant belongs only to EDT's data-separable owner set. Acceptance proves that
    # the writer used the live dataSeparation to select the separator predicate.
    assert counts.get("added") == 1 and counts.get("updated") == 0 and counts.get("removed") == 0, \
        "the Constant must be attached as one fresh owner: %r" % (attach.structured,)
    assert attach.structured.get("persisted") is True, \
        "the Constant owner must persist to disk: %r" % (attach.structured,)

    on_disk = read_disk(_common_attribute_mdo(separator_name))
    # Separate is the serialization default, so its element may be omitted. DontUse
    # would prove the target incorrectly remained a simple common attribute.
    assert "<dataSeparation>DontUse</dataSeparation>" not in on_disk, \
        "the target must not have fallen back to a simple common attribute:\n%s" % on_disk[:600]
    assert owner_token in on_disk, "the Constant owner must land in the separator's content on disk"

    _assert_content_rows(_details_text(separator_fqn), [(constant_fqn, "Use")],
                         "the separator must read back exactly one Constant owner", fqn=separator_fqn)


# ──────────────────────────────────────────────────────────────────────────────
# Happy — remove remains a cleanup operation after the target kind makes the row illegal
# ──────────────────────────────────────────────────────────────────────────────

@e2e_test(tool="modify_metadata", kind="write-metadata")
def test_remove_owner_illegal_for_current_target_kind_succeeds():
    attribute_name = "E2EIllegalOwnerCleanup507"
    constant_name = "E2EIllegalOwnerCleanupConstant507"
    attribute_fqn = "CommonAttribute." + attribute_name
    constant_fqn = "Constant." + constant_name
    owner_token = "<metadata>%s</metadata>" % constant_fqn
    attribute_mdo = _common_attribute_mdo(attribute_name)

    _create_ok(constant_fqn, "create the separator-only Constant owner")
    _create_ok(attribute_fqn, "create the common attribute for the cleanup case")

    make_separator = call("modify_metadata", {
        "projectName": PROJECT,
        "fqn": attribute_fqn,
        "properties": [{"name": "dataSeparation", "value": "Separate"}],
    })
    assert_ok(make_separator, "make the target a separator before attaching the Constant")
    assert wait_for_project_ready(), "the separator must settle before attaching its owner"

    attach = call("modify_metadata", {
        "projectName": PROJECT,
        "fqn": attribute_fqn,
        "content": [{"op": "add", "metadata": constant_fqn, "use": "Use"}],
    })
    assert_ok(attach, "attach the Constant while its class is legal for the target")
    assert (attach.structured.get("content") or {}).get("added") == 1, \
        "the setup must attach exactly one Constant owner: %r" % (attach.structured,)

    make_simple = call("modify_metadata", {
        "projectName": PROJECT,
        "fqn": attribute_fqn,
        "properties": [{"name": "dataSeparation", "value": "DontUse"}],
    })
    assert_ok(make_simple, "change the populated target to the simple common-attribute kind")
    assert wait_for_project_ready(), "the changed target must settle before the cleanup call"

    before_remove = read_disk(attribute_mdo)
    assert "<dataSeparation>DontUse</dataSeparation>" in before_remove, \
        "the target must now be simple before exercising cleanup:\n%s" % before_remove[:700]
    assert owner_token in before_remove, "the now-illegal Constant row must exist before removal"
    _assert_content_rows(_details_text(attribute_fqn), [(constant_fqn, "Use")],
                         "the now-illegal owner must remain in the model before cleanup", fqn=attribute_fqn)

    # Removal resolves a real owner and attached row, but skips the owner-class rule for
    # the target's current kind so an obsolete row remains removable.
    remove = call("modify_metadata", {
        "projectName": PROJECT,
        "fqn": attribute_fqn,
        "content": [{"op": "remove", "metadata": constant_fqn}],
    })
    assert_ok(remove, "remove the Constant that is illegal for the target's current simple kind")
    counts = remove.structured.get("content") or {}
    assert counts.get("removed") == 1 and counts.get("added") == 0 and counts.get("updated") == 0, \
        "the cleanup call must remove exactly the obsolete row: %r" % (remove.structured,)
    poll_disk_lacks(attribute_mdo, owner_token,
                    ctx="the now-illegal Constant owner row must be removable from disk")
    _assert_content_rows(_details_text(attribute_fqn), [],
                         "the obsolete owner must also be removed from the model", fqn=attribute_fqn)


# ──────────────────────────────────────────────────────────────────────────────
# Happy — op defaults to 'add' when omitted (the doc's default), owner still attached
# ──────────────────────────────────────────────────────────────────────────────

@e2e_test(tool="modify_metadata", kind="write-metadata")
def test_add_owner_op_defaults_to_add():
    r = call("modify_metadata", {
        "projectName": PROJECT, "fqn": CA,
        "content": [{"metadata": OWNER}],           # no op -> defaults to add; no use -> defaults to Use
    })
    assert_ok(r, "attach an owner with op/use omitted (defaults apply)")
    counts = r.structured.get("content") or {}
    assert counts.get("added") == 1, "the omitted op must default to add: %r" % (r.structured,)
    poll_diff_contains(OWNER_MDO_TOKEN,
                       ctx="the default-add owner must land in a <content> block on disk")
    # the default use is Use -> the item carries a <use>Use</use> in the same content block.
    poll_diff_contains("<use>Use</use>",
                       ctx="the default per-owner use must serialize as Use")
    _assert_content_rows(_details_text(), [(OWNER, "Use")],
                         "the default add must read back exactly one owner with Use")


# ──────────────────────────────────────────────────────────────────────────────
# Happy — idempotent re-add: no duplicate; a second add of the same owner UPDATES its use
# ──────────────────────────────────────────────────────────────────────────────

@e2e_test(tool="modify_metadata", kind="write-metadata")
def test_readd_is_idempotent_and_updates_use():
    first = call("modify_metadata", {
        "projectName": PROJECT, "fqn": CA,
        "content": [{"op": "add", "metadata": OWNER, "use": "Use"}],
    })
    assert_ok(first, "first attach")
    assert (first.structured.get("content") or {}).get("added") == 1, \
        "first attach must add: %r" % (first.structured,)
    _assert_content_rows(_details_text(), [(OWNER, "Use")],
                         "the first attach must read back one owner with Use before the update")

    # Re-add the SAME owner with a DIFFERENT use -> no duplicate, counted as an update.
    second = call("modify_metadata", {
        "projectName": PROJECT, "fqn": CA,
        "content": [{"op": "add", "metadata": OWNER, "use": "DontUse"}],
    })
    assert_ok(second, "re-attach the same owner (idempotent)")
    counts = second.structured.get("content") or {}
    assert counts.get("added") == 0 and counts.get("updated") == 1, \
        "a re-add of a listed owner must UPDATE (not add) its use: %r" % (second.structured,)

    # The use flip landed on disk (Use -> DontUse) for the single, un-duplicated owner.
    poll_diff_contains("<use>DontUse</use>",
                       ctx="the re-add must update the owner's use to DontUse on disk")
    # anti-cheat: exactly ONE content item exists (no duplicate row in the model read-back).
    text = _details_text()
    _assert_content_rows(text, [(OWNER, "DontUse")],
                         "the idempotent re-add must update Use without duplicating the owner")


# ──────────────────────────────────────────────────────────────────────────────
# Happy — remove: seed the owner, then detach it; the <content> owner token is gone
# ──────────────────────────────────────────────────────────────────────────────

@e2e_test(tool="modify_metadata", kind="write-metadata")
def test_remove_owner_detaches_it():
    seed = call("modify_metadata", {
        "projectName": PROJECT, "fqn": CA,
        "content": [{"op": "add", "metadata": OWNER, "use": "Use"}],
    })
    assert_ok(seed, "seed the owner to later remove")
    poll_diff_contains(OWNER_MDO_TOKEN, ctx="the seeded owner must be on disk before removal")
    _assert_content_rows(_details_text(), [(OWNER, "Use")],
                         "the seeded owner must be present in the model before removal")

    r = call("modify_metadata", {
        "projectName": PROJECT, "fqn": CA,
        "content": [{"op": "remove", "metadata": OWNER}],
    })
    assert_ok(r, "detach the owner from the common attribute")
    counts = r.structured.get("content") or {}
    assert counts.get("removed") == 1, "exactly one owner must be removed: %r" % (r.structured,)
    assert counts.get("added") == 0 and counts.get("updated") == 0, \
        "a pure remove must not add / update: %r" % (r.structured,)

    # The owner FQN is gone from the content list on disk (the whole <content> block for it is removed).
    poll_disk_lacks(CA_MDO, OWNER_MDO_TOKEN,
                    ctx="the detached owner's <content> block must be gone from the .mdo")
    # The model read-back no longer lists a content item (back to an empty content list).
    after = _details_text()
    _assert_content_rows(after, [], "the model must show the seeded owner removed")


# ──────────────────────────────────────────────────────────────────────────────
# Happy — a Russian bilingual FQN type token resolves the owner (only the token is bilingual)
# ──────────────────────────────────────────────────────────────────────────────

@e2e_test(tool="modify_metadata", kind="write-metadata")
def test_add_owner_by_russian_type_token():
    # 'Справочник.Catalog' - the Russian type token 'Справочник' (= Catalog), same programmatic Name.
    r = call("modify_metadata", {
        "projectName": PROJECT, "fqn": CA,
        # use='DontUse' (a NON-default use that serializes): 'Auto' is the CommonAttributeUse EMF
        # default, so setUse(Auto) writes no <use> element (like an ExchangePlan's default 'Deny').
        "content": [{"op": "add", "metadata": "Справочник.Catalog", "use": "DontUse"}],
    })
    assert_ok(r, "attach an owner addressed by the Russian type token")
    counts = r.structured.get("content") or {}
    assert counts.get("added") == 1, \
        "the bilingual type token must resolve the same owner and add it: %r" % (r.structured,)
    # It resolves to the SAME owner -> the English-canonical FQN + the DontUse use land on disk.
    poll_diff_contains(OWNER_MDO_TOKEN,
                       ctx="the Russian-token owner must serialize to the canonical Catalog.Catalog")
    poll_diff_contains("<use>DontUse</use>",
                       ctx="the non-default DontUse use must serialize for the bilingual-token owner")
    _assert_content_rows(_details_text(), [(OWNER, "DontUse")],
                         "the bilingual owner must read back its canonical FQN and DontUse")


# ──────────────────────────────────────────────────────────────────────────────
# Negative — an unresolvable owner FQN is a clean, actionable error (nothing written)
# ──────────────────────────────────────────────────────────────────────────────

@e2e_test(tool="modify_metadata", kind="write-metadata")
def test_add_unresolvable_owner_is_error():
    bad = "Catalog.NoSuchOwner_e2e"
    r = call("modify_metadata", {
        "projectName": PROJECT, "fqn": CA,
        "content": [{"op": "add", "metadata": bad, "use": "Use"}],
    })
    e = assert_error(r, "attach an owner that does not exist")
    assert_error_quality(e, names=[bad],
                         ctx="an unresolvable owner FQN names the bad value and is actionable")
    assert_no_diff("a rejected content add must change nothing")


# ──────────────────────────────────────────────────────────────────────────────
# Negative — a valid object that is NOT a common-attribute owner kind is rejected
# ──────────────────────────────────────────────────────────────────────────────

@e2e_test(tool="modify_metadata", kind="write-metadata")
def test_add_non_owner_kind_is_error():
    # A CommonModule exists but cannot OWN a common attribute (not a common-attribute owner kind).
    not_owner = "CommonModule.OK"
    r = call("modify_metadata", {
        "projectName": PROJECT, "fqn": CA,
        "content": [{"op": "add", "metadata": not_owner, "use": "Use"}],
    })
    e = assert_error(r, "attach an object that is not a valid owner kind")
    assert_error_quality(e, names=[not_owner], suggests=["owner"],
                         ctx="a non-owner kind is rejected with the object named + an owner hint")
    assert_no_diff("a rejected non-owner add must change nothing")


# NOTE: a content payload against a genuinely non-dispatch FQN kind (one with no membership list) is
# covered by test_content_payload_on_non_dispatch_kind_is_error in test_modify_metadata_membership.py.
# In #174 v2 a Catalog / ExchangePlan / Document FQN IS a valid content-dispatch target (owners /
# content / register records), so a "Catalog is not a CommonAttribute -> error" check no longer holds.


# ──────────────────────────────────────────────────────────────────────────────
# Negative — remove an owner that is not attached is a clean error (nothing written)
# ──────────────────────────────────────────────────────────────────────────────

@e2e_test(tool="modify_metadata", kind="write-metadata")
def test_remove_unresolvable_owner_is_error():
    missing = "Catalog.NoSuchOwnerToRemove_e2e"
    # Removal skips only the class predicate; it must still resolve the owner object.
    r = call("modify_metadata", {
        "projectName": PROJECT, "fqn": CA,
        "content": [{"op": "remove", "metadata": missing}],
    })
    e = assert_error(r, "detach an owner object that does not exist")
    assert_error_quality(e, names=[missing],
                         ctx="removing a non-existent owner names the unresolved FQN")
    assert_no_diff("a rejected removal of a non-existent owner must change nothing")


@e2e_test(tool="modify_metadata", kind="write-metadata")
def test_remove_not_attached_owner_is_error():
    # The fixture common attribute has no content, so removing any owner is a clean, actionable error.
    r = call("modify_metadata", {
        "projectName": PROJECT, "fqn": CA,
        "content": [{"op": "remove", "metadata": OWNER}],
    })
    e = assert_error(r, "detach an owner that is not in the content list")
    assert_error_quality(e, names=[OWNER],
                         ctx="removing a non-listed owner names it and is actionable")
    assert_no_diff("a rejected remove must change nothing")


# ──────────────────────────────────────────────────────────────────────────────
# Negative — an empty content entry (no metadata FQN) is rejected with the shape
# ──────────────────────────────────────────────────────────────────────────────

@e2e_test(tool="modify_metadata", kind="write-metadata")
def test_content_entry_without_metadata_is_error():
    r = call("modify_metadata", {
        "projectName": PROJECT, "fqn": CA,
        "content": [{"op": "add", "use": "Use"}],   # no 'metadata' -> the required field is missing
    })
    e = assert_error(r, "content entry missing the required metadata FQN")
    assert_error_quality(e, names=["metadata"],
                         ctx="a content entry with no metadata FQN is rejected naming the field")
    assert_no_diff("a rejected malformed content entry must change nothing")


# ──────────────────────────────────────────────────────────────────────────────
# Negative — a bad 'use' value is rejected and lists the accepted literals
# ──────────────────────────────────────────────────────────────────────────────

@e2e_test(tool="modify_metadata", kind="write-metadata")
def test_bad_use_value_lists_allowed():
    r = call("modify_metadata", {
        "projectName": PROJECT, "fqn": CA,
        "content": [{"op": "add", "metadata": OWNER, "use": "Maybe_zz"}],
    })
    e = assert_error(r, "a bad per-owner use value")
    # the error names the bad value AND lists the three accepted literals.
    assert_error_quality(e, names=["Maybe_zz"], suggests=["Use", "DontUse", "Auto"],
                         ctx="an unrecognized use value lists Use / DontUse / Auto")
    assert_no_diff("a rejected bad-use add must change nothing")


# ──────────────────────────────────────────────────────────────────────────────
# Negative — a content payload cannot be combined with a generic 'properties' change
# ──────────────────────────────────────────────────────────────────────────────

@e2e_test(tool="modify_metadata", kind="write-metadata")
def test_content_mixed_with_properties_is_error():
    r = call("modify_metadata", {
        "projectName": PROJECT, "fqn": CA,
        "content": [{"op": "add", "metadata": OWNER, "use": "Use"}],
        "properties": [{"name": "comment", "value": "x"}],
    })
    e = assert_error(r, "content payload mixed with a properties change")
    assert_error_quality(e, suggests=["cannot be combined", "separately"],
                         ctx="a content change cannot be combined with a generic properties change")
    assert_no_diff("a rejected mixed content+properties call must change nothing")
