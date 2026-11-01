"""Offline checks for the canonical SDK-specific page-switch oracle; no EDT calls."""
import importlib.util
from pathlib import Path
import unittest
from unittest import mock


E2E_DIR = Path(__file__).resolve().parents[1]
HARNESS_SPEC = importlib.util.spec_from_file_location("page_switch_harness", E2E_DIR / "harness.py")
HARNESS = importlib.util.module_from_spec(HARNESS_SPEC)
HARNESS_SPEC.loader.exec_module(HARNESS)
TOOL_SPEC = importlib.util.spec_from_file_location("page_switch_tool",
                                                E2E_DIR / "tools/test_get_form_screenshot.py")
TOOL = importlib.util.module_from_spec(TOOL_SPEC)
with mock.patch.dict("sys.modules", {"harness": HARNESS}):
    TOOL_SPEC.loader.exec_module(TOOL)


def response(error=None, blob=None, structured=None):
    result = {"isError": error is not None, "structuredContent": structured}
    if error is not None:
        result["content"] = [{"type": "text", "text": error}]
    elif blob is not None:
        result["content"] = [{"type": "resource", "resource": {"mimeType": "image/png", "blob": blob}}]
    return HARNESS.Result({"result": result})


def refusal(name):
    return ("showElement '%s' is inside a Pages group, but this EDT does not support safe page "
            "switching with selection-frame clearing and selection restoration. No page switch was "
            "attempted. Omit showElement to capture the current page, or use get_metadata_details "
            "for the element tree." % name)


class FormPageSwitchContractTest(unittest.TestCase):
    def run_body(self, responses, buffered="on"):
        with mock.patch.object(TOOL, "native_form_layout_render_mode", return_value="on"), \
             mock.patch.object(TOOL, "buffered_form_render_at_startup", return_value=buffered) as flag, \
             mock.patch.object(TOOL, "call", side_effect=responses) as calls, \
             mock.patch.object(TOOL, "assert_no_diff") as clean:
            TOOL.test_show_element_switches_the_page_and_puts_the_editor_back()
            return calls.call_args_list, flag.call_count, clean.call_count

    def test_exact_5_checks_both_refusals_before_the_buffer_gate_and_outside_capture(self):
        calls, flag_calls, clean_calls = self.run_body([
            response(structured={"edtVersion": "2025.2.5.2"}),
            response(error=refusal("PageExtra")), response(error=refusal("extrapagelabel")),
            response(error="Form image data is not available. Enable buffered capture."),
        ], buffered="off")
        self.assertEqual(flag_calls, 0)
        self.assertEqual(clean_calls, 1)
        self.assertEqual([c.args[1].get("showElement") for c in calls],
                         [None, "PageExtra", "extrapagelabel", "OK"])
        self.assertTrue(calls[1].args[1]["refresh"])
        self.assertTrue(calls[2].args[1]["refresh"])

    def test_exact_5_rejects_a_generic_render_failure_for_a_page(self):
        with self.assertRaises(HARNESS.E2EAssertion):
            self.run_body([response(structured={"edtVersion": "2025.2.5.2"}),
                           response(error="Form image data is not available.")])

    def test_verified_6_uses_the_same_refusal_and_does_not_widen_to_other_releases(self):
        calls, flag_calls, clean_calls = self.run_body([
            response(structured={"edtVersion": "2025.2.6"}),
            response(error=refusal("PageExtra")), response(error=refusal("extrapagelabel")),
            response(blob=TOOL._PNG_B64_PREFIX + "outside"),
        ], buffered="off")
        self.assertEqual(len(calls), 4)
        self.assertEqual(flag_calls, 0)
        self.assertEqual(clean_calls, 1)
        for version in ("2025.2.6.1", "2025.2.5.2"):
            self.assertTrue(TOOL._has_verified_legacy_page_selection(version))
        for version in ("2025.2.7", "2025.2.60", "2026.1.0", "2026.2.0", "2025.2.6.unknown"):
            self.assertFalse(TOOL._has_verified_legacy_page_selection(version))

    def test_exact_5_rejects_capability_refusal_outside_pages(self):
        with self.assertRaises(AssertionError):
            self.run_body([response(structured={"edtVersion": "2025.2.5.2"}),
                           response(error=refusal("PageExtra")), response(error=refusal("extrapagelabel")),
                           response(error=refusal("OK"))])

    def test_newer_sdk_keeps_full_page_change_and_restoration_assertions(self):
        prefix = TOOL._PNG_B64_PREFIX
        pngs = [prefix + suffix for suffix in ("base", "base", "base", "extra", "extra", "base")]
        calls, flag_calls, clean_calls = self.run_body([
            response(structured={"edtVersion": "2026.2.0"}),
            *[response(blob=png) for png in pngs],
        ])
        self.assertEqual(len(calls), 7)
        self.assertEqual(flag_calls, 1)
        self.assertEqual(clean_calls, 1)
        pngs[3] = pngs[1]  # A no-op page switch must still fail on a supported SDK.
        with self.assertRaisesRegex(AssertionError, "nothing was switched"):
            self.run_body([response(structured={"edtVersion": "2026.2.0"}),
                           *[response(blob=png) for png in pngs]])


if __name__ == "__main__":
    unittest.main()
