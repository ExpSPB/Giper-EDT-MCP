"""Exercise discriminating compatibility probes without EDT or network calls."""
from contextlib import ExitStack
import importlib.util
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest import mock


E2E_DIR = Path(__file__).resolve().parents[1]
HARNESS_SPEC = importlib.util.spec_from_file_location("compat_probe_harness", E2E_DIR / "harness.py")
HARNESS = importlib.util.module_from_spec(HARNESS_SPEC)
HARNESS_SPEC.loader.exec_module(HARNESS)
TOOL_SPEC = importlib.util.spec_from_file_location("compat_probe_tool", E2E_DIR / "tools/test_create_project.py")
TOOL = importlib.util.module_from_spec(TOOL_SPEC)
with mock.patch.dict("sys.modules", {"harness": HARNESS}):
    TOOL_SPEC.loader.exec_module(TOOL)


class CompatibilityProbeTest(unittest.TestCase):
    def test_27_and_85_fixtures_select_a_distinct_supported_lower_mode(self):
        self.assertEqual(TOOL._distinct_lower_compatibility_mode("8.3.27"), "8.3.24")
        self.assertEqual(TOOL._distinct_lower_compatibility_mode("8.5.1"), "8.3.27")
        self.assertEqual(TOOL._distinct_lower_compatibility_mode("8.5.1", "8.3.27"), "8.3.24")

    def test_unknown_or_non_discriminating_fixture_is_an_error_not_a_skip(self):
        for ceilings in [(), ("unknown",), ("8.3.24",), ("8.5.1", "8.3.24")]:
            with self.subTest(ceilings=ceilings), self.assertRaises(AssertionError):
                TOOL._distinct_lower_compatibility_mode(*ceilings)

    def test_manifest_requires_exactly_one_measured_runtime(self):
        self.assertEqual(TOOL._runtime_version_from_manifest(
            "Manifest-Version: 1.0\r\nRuntime-Version: 8.3.27\r\n", "fixture"), "8.3.27")
        for text in ["", "Runtime-Version: unknown\n",
                     "Runtime-Version: 8.3.27\nRuntime-Version: 8.3.27\n"]:
            with self.subTest(text=text), self.assertRaises(AssertionError):
                TOOL._runtime_version_from_manifest(text, "fixture")

    def test_persisted_runtime_mismatch_is_rejected(self):
        with mock.patch.object(TOOL, "_project_dir", return_value="unused"), \
             mock.patch.object(TOOL, "_poll_file", return_value="Runtime-Version: 8.5.1\n"), \
             self.assertRaisesRegex(AssertionError, "persisted runtime.*8.3.27"):
            TOOL._assert_persisted_runtime("copy", "8.3.27")

    def test_explicit_probe_keeps_disk_checked_state_and_both_message_modes(self):
        for base, requested in [("8.3.27", "8.3.24"), ("8.5.1", "8.3.27")]:
            with self.subTest(base=base):
                self._run_explicit_probe(base, requested)

    def test_explicit_probe_still_rejects_wrong_native_state_and_message(self):
        for defect in ("state", "message"):
            with self.subTest(defect=defect), self.assertRaises(HARNESS.E2EAssertion):
                self._run_explicit_probe("8.3.27", "8.3.24", defect)

    def _run_explicit_probe(self, base, requested, defect=None):
        mode = TOOL._mode_name(requested)
        state = "Extended" if defect == "state" else "Checked"
        root = (f"<compatibilityMode>{base}</compatibilityMode>"
                '<extension xsi:type="mdclassExtension:ConfigurationExtension">'
                f"<compatibilityMode>{state}</compatibilityMode></extension>")
        message = f"{mode} (requested; the base configuration's is {TOOL._mode_name(base)})"
        result = SimpleNamespace(is_error=False, structured={"compatibilityMode": mode,
                                 "message": "same mode" if defect == "message" else message})
        with ExitStack() as stack:
            for name, value in [("_base_root_facts", (base, "Language.English", "uuid")),
                                ("_ensure_absent", None), ("_project_dir", "unused"),
                                ("_poll_file", root), ("assert_no_diff", None)]:
                stack.enter_context(mock.patch.object(TOOL, name, return_value=value))
            create = stack.enter_context(mock.patch.object(TOOL, "_create_extension", return_value=(result, None)))
            TOOL.test_explicit_extension_compatibility_mode_keeps_the_base_mode_checked()
            create.assert_called_once_with(TOOL.NEW_EXT_COMPAT, compatibilityMode=mode)

    def test_import_probe_preserves_measured_runtime_and_checks_both_manifests(self):
        for fixture in ("8.3.27", "8.5.1"):
            with self.subTest(fixture=fixture):
                self._run_import_probe(fixture)

    def test_import_probe_rejects_runtime_coercion_instead_of_skipping(self):
        with self.assertRaisesRegex(AssertionError, "must retain runtime"):
            self._run_import_probe("8.3.27", returned_runtime="8.3.24")

    def test_import_probe_rejects_root_modes_derived_from_runtime(self):
        with self.assertRaisesRegex(AssertionError, "must not come from the runtime version"):
            self._run_import_probe("8.3.27", root_from_runtime=True)

    def _run_import_probe(self, fixture, returned_runtime=None, root_from_runtime=False):
        mode = TOOL._distinct_lower_compatibility_mode(fixture, fixture)
        result = SimpleNamespace(is_error=False, structured={"version": returned_runtime or fixture,
                                 "compatibilityMode": TOOL._mode_name(mode)})
        with tempfile.TemporaryDirectory() as directory, ExitStack() as stack:
            dump = Path(directory) / "Configuration.xml"
            seen = []

            def call(name, params):
                seen.append(name)
                if name == "export_configuration_to_xml":
                    dump.write_text(f"<CompatibilityMode>{TOOL._mode_name(fixture)}</CompatibilityMode>",
                                    encoding="utf-8")
                elif name == "import_configuration_from_xml":
                    self.assertEqual(params["platformVersion"], fixture)
                    self.assertEqual(dump.read_text(encoding="utf-8"),
                                     f"<CompatibilityMode>{TOOL._mode_name(mode)}</CompatibilityMode>")
                else:
                    self.fail("unexpected RPC " + name)
                return SimpleNamespace(is_error=False)

            root_mode = fixture if root_from_runtime else mode
            root = (f"<configurationExtensionCompatibilityMode>{root_mode}</configurationExtensionCompatibilityMode>"
                    f"<compatibilityMode>{root_mode}</compatibilityMode>")
            for name, value in [("_base_root_facts", (fixture, "Language.English", "uuid")),
                                ("read_disk", f"Runtime-Version: {fixture}\n"), ("_ensure_absent", None),
                                ("settle_or_fail", None), ("_project_dir", "unused"), ("_poll_file", root),
                                ("_create_extension", (result, None)), ("assert_no_diff", None),
                                ("_remove_successful_dump", None)]:
                stack.enter_context(mock.patch.object(TOOL, name, return_value=value))
            stack.enter_context(mock.patch.object(TOOL.tempfile, "mkdtemp", return_value=directory))
            stack.enter_context(mock.patch.object(TOOL, "call", side_effect=call))
            persisted = stack.enter_context(mock.patch.object(TOOL, "_assert_persisted_runtime"))
            TOOL.test_extension_compatibility_defaults_to_the_base_mode_not_the_runtime()
            self.assertEqual(seen, ["export_configuration_to_xml", "import_configuration_from_xml"])
            self.assertEqual(persisted.call_args_list, [mock.call(TOOL.NEW_BASE_8327, fixture),
                             mock.call(TOOL.NEW_BASE_8327 + "." + TOOL.NEW_EXT_8327, fixture)])


if __name__ == "__main__":
    unittest.main()
