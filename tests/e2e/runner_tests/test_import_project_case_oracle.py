"""Case-collision probes must remain non-mutating on either filesystem kind."""
from contextlib import ExitStack
import importlib.util
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest import mock
import xml.etree.ElementTree as ET


E2E_DIR = Path(__file__).resolve().parents[1]
HARNESS_SPEC = importlib.util.spec_from_file_location("case_oracle_harness", E2E_DIR / "harness.py")
HARNESS = importlib.util.module_from_spec(HARNESS_SPEC)
HARNESS_SPEC.loader.exec_module(HARNESS)
TOOL_SPEC = importlib.util.spec_from_file_location("case_oracle_tool", E2E_DIR / "tools/test_import_project_folder_case.py")
TOOL = importlib.util.module_from_spec(TOOL_SPEC)
with mock.patch.dict("sys.modules", {"harness": HARNESS}):
    TOOL_SPEC.loader.exec_module(TOOL)


class ImportProjectCaseOracleTest(unittest.TestCase):
    def test_case_insensitive_probe_preserves_exact_names_real_path_and_resource_assertions(self):
        self._run_case(case_sensitive=False)

    def test_case_sensitive_probe_uses_present_malformed_xml_and_valid_base_before_cli(self):
        self._run_case(case_sensitive=True)

    def test_failure_keeps_the_malformed_input_for_pre_cleanup_evidence(self):
        self._run_case(case_sensitive=False, wrong_response=True)

    def test_absent_configuration_is_external_and_is_not_a_refusal_barrier(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(AssertionError, "EXTERNAL.*CLI"):
                self._dump_refusal(Path(directory), "TestConfiguration")

    @staticmethod
    def _dump_refusal(source, base):
        # Independent negative-control boundary from BinaryToXmlConverter.dumpKind:
        # no Configuration.xml means EXTERNAL, whose valid base can reach the importer.
        configuration = source / "Configuration.xml"
        if not configuration.exists():
            raise AssertionError("EXTERNAL input can reach CLI; absence is not a safe refusal")
        try:
            ET.parse(configuration)
        except ET.ParseError:
            if base:
                return "Configuration.xml could not be read, so the dump kind cannot be verified"
        raise AssertionError("input can reach CLI; expected present malformed XML with a base")

    def _run_case(self, case_sensitive, wrong_response=False):
        with tempfile.TemporaryDirectory() as directory, ExitStack() as stack:
            parent = Path(directory)
            source = parent / "owned-dump"
            source.mkdir()
            fixture = str(parent / "fixture" / "TestConfiguration")
            workspace = str(parent / "workspace")
            project = "TestConfiguration"
            requested = "testconfiguration"
            calls = []

            def call(name, params):
                calls.append((name, params))
                self.assertEqual(name, "import_configuration_from_xml")
                self.assertEqual(params, {"importPath": str(source), "projectName": requested,
                                          "baseProjectName": project})
                # Independently prove the source is malformed and safe even when the Windows
                # branch returns earlier at name validation and never needs to inspect XML.
                dump_error = self._dump_refusal(source, params["baseProjectName"])
                error = dump_error if case_sensitive else (
                    f"Project `{requested}` already exists as `{project}`: the names differ only by "
                    f"letter case and identify one resource at {fixture}. Choose a new project name.")
                if wrong_response:
                    error = "wrong refusal"
                return SimpleNamespace(is_error=True, text=error, error_text=lambda: error)

            stack.enter_context(mock.patch.object(TOOL, "PROJECT", project))
            stack.enter_context(mock.patch.object(TOOL, "PROJECT_DIR", fixture))
            stack.enter_context(mock.patch.object(TOOL, "_workspace_dir", return_value=workspace))
            stack.enter_context(mock.patch.object(TOOL.tempfile, "mkdtemp", return_value=str(source)))
            stack.enter_context(mock.patch.object(TOOL.os.path, "isdir", return_value=not case_sensitive))
            stack.enter_context(mock.patch.object(TOOL, "call", side_effect=call))
            no_diff = stack.enter_context(mock.patch.object(TOOL, "assert_no_diff"))
            run = TOOL.test_a_name_differing_only_by_case_from_an_existing_project_is_refused_on_a_case_insensitive_fs
            if wrong_response:
                with self.assertRaises(HARNESS.E2EAssertion):
                    run()
                self.assertEqual((source / "Configuration.xml").read_text(encoding="utf-8"), "<Configuration")
                no_diff.assert_not_called()
            else:
                run()
                no_diff.assert_called_once()
                self.assertFalse(source.exists(), "a successful refusal cleans only its owned source")
            self.assertEqual(len(calls), 1, "the case has exactly one RPC and no fallback import")


if __name__ == "__main__":
    unittest.main()
