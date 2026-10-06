"""Offline readback-contract regressions for common-attribute content assertions."""

import importlib.util
import os
import unittest
from unittest import mock


E2E_DIR = os.path.dirname(os.path.dirname(__file__))
HARNESS_SPEC = importlib.util.spec_from_file_location(
    "common_attribute_test_harness", os.path.join(E2E_DIR, "harness.py"))
HARNESS = importlib.util.module_from_spec(HARNESS_SPEC)
HARNESS_SPEC.loader.exec_module(HARNESS)
TOOL_SPEC = importlib.util.spec_from_file_location(
    "common_attribute_content_tests",
    os.path.join(E2E_DIR, "tools", "test_modify_metadata_common_attribute.py"))
TOOL = importlib.util.module_from_spec(TOOL_SPEC)
with mock.patch.dict("sys.modules", {"harness": HARNESS}):
    TOOL_SPEC.loader.exec_module(TOOL)


EMPTY = """# Metadata Details: TestConfiguration

## CommonAttribute: CommonAttribute

### Basic Properties

| Property | Value |
|---|---|
| Name | CommonAttribute |
"""
CONTENT = """
### Content

| Metadata | Use |
|---|---|
%s

**Origin:** core
"""


class CommonAttributeContentRowsTest(unittest.TestCase):
    def test_named_owner_and_updated_use_match_the_public_renderer(self):
        text = EMPTY + CONTENT % "| Catalog.Catalog | DontUse |"

        TOOL._assert_content_rows(text, [(TOOL.OWNER, "DontUse")], "updated owner")

    def test_created_separator_uses_its_own_object_heading_and_constant_owner(self):
        fqn = "CommonAttribute.E2ESeparator"
        text = EMPTY.replace("## CommonAttribute: CommonAttribute", "## CommonAttribute: E2ESeparator") \
            + CONTENT % "| Constant.E2EConstant | Use |"

        TOOL._assert_content_rows(text, [("Constant.E2EConstant", "Use")], "separator owner", fqn=fqn)
        with self.assertRaises(AssertionError):
            TOOL._assert_content_rows(text, [("Constant.E2EConstant", "Use")], "wrong target")

    def test_duplicate_owner_rows_are_rejected_including_different_use_values(self):
        for second_use in ("DontUse", "Use"):
            with self.subTest(second_use=second_use):
                text = EMPTY + CONTENT % (
                    "| Catalog.Catalog | DontUse |\n| Catalog.Catalog | %s |" % second_use)
                with self.assertRaisesRegex(AssertionError, "expected.*got"):
                    TOOL._assert_content_rows(text, [(TOOL.OWNER, "DontUse")], "idempotent re-add")

    def test_owner_with_stale_use_does_not_pass_an_update_assertion(self):
        text = EMPTY + CONTENT % "| Catalog.Catalog | Use |"

        with self.assertRaises(AssertionError):
            TOOL._assert_content_rows(text, [(TOOL.OWNER, "DontUse")], "updated owner")

    def test_a_valid_empty_object_readback_proves_no_owner_rows(self):
        TOOL._assert_content_rows(EMPTY, [], "removed owner")

    def test_empty_or_error_body_cannot_masquerade_as_successful_removal(self):
        for text in ("", "Object not found: CommonAttribute.CommonAttribute"):
            with self.subTest(text=text):
                with self.assertRaisesRegex(AssertionError, "target object heading"):
                    TOOL._assert_content_rows(text, [], "removed owner")

    def test_still_attached_owner_fails_removal_without_an_internal_class_name(self):
        text = EMPTY + CONTENT % "| Catalog.Catalog | Use |"

        with self.assertRaises(AssertionError):
            TOOL._assert_content_rows(text, [], "removed owner")

    def test_only_the_target_content_section_supplies_owner_rows(self):
        text = EMPTY + "\n## CommonAttribute: Other\n" + CONTENT % "| Catalog.Catalog | Use |"
        TOOL._assert_content_rows(text, [], "target is empty")
        with self.assertRaises(AssertionError):
            TOOL._assert_content_rows(text, [(TOOL.OWNER, "Use")], "target owner")

    def test_other_sections_and_internal_class_names_are_not_content_rows(self):
        text = EMPTY + """
### Synonym
| Metadata | Use |
|---|---|
| Catalog.Catalog | Use |

CommonAttributeContentItem
""" + CONTENT % "| Catalog.Catalog | DontUse |"
        TOOL._assert_content_rows(text, [(TOOL.OWNER, "DontUse")], "updated owner")

    def test_malformed_or_truncated_content_cannot_be_accepted_as_empty(self):
        malformed = (
            CONTENT % "",  # The renderer omits empty collections; a header alone is no proof.
            (CONTENT % "| Catalog.Catalog | Use |").replace("| Metadata | Use |", "| Use | Metadata |"),
            (CONTENT % "| Catalog.Catalog | Use |").replace("|---|---|", "|broken|---|"),
            CONTENT % "| Catalog.Catalog | Use | extra |",
            CONTENT % "| Catalog.Catalog | Maybe |",
            CONTENT % "| | Use |",
            "\n### Content\n| Use | Value |\n|---|---|\n| Use | CommonAttributeContentItem |\n",
        )
        for section in malformed:
            with self.subTest(section=section):
                with self.assertRaises(AssertionError):
                    TOOL._assert_content_rows(EMPTY + section, [], "removed owner")

    def test_duplicate_content_sections_are_not_silently_merged_or_dropped(self):
        section = CONTENT % "| Catalog.Catalog | Use |"

        with self.assertRaisesRegex(AssertionError, "duplicate Content"):
            TOOL._assert_content_rows(EMPTY + section + section, [(TOOL.OWNER, "Use")], "owner")


if __name__ == "__main__":
    unittest.main()
