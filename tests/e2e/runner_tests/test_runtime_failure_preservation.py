"""Runtime E2E cleanup must preserve failed scratch evidence before orchestration resets it."""

import importlib.util
from pathlib import Path
import sys
import tempfile
from types import ModuleType
import unittest
from unittest import mock


E2E = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location("runtime_preservation_harness", E2E / "harness.py")
HARNESS = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(HARNESS)


class RuntimeFailurePreservationTest(unittest.TestCase):
    def load(self, filename):
        source = E2E / "tools" / filename
        module = ModuleType("runtime_preservation_under_test")
        module.__file__ = str(source)
        with mock.patch.dict(sys.modules, {"harness": HARNESS}), mock.patch.object(HARNESS, "REGISTRY", []):
            exec(compile(source.read_bytes(), str(source), "exec"), module.__dict__)
        module.call = mock.Mock()
        return module

    def test_failed_project_and_dump_are_retained_and_cannot_be_reset_in_same_run(self):
        for filename in ("test_create_project.py", "test_import_configuration_from_xml.py"):
            with self.subTest(filename=filename), tempfile.TemporaryDirectory() as directory:
                module = self.load(filename)
                dump = Path(directory) / "xml-source"
                dump.mkdir()
                evidence = dump / "Configuration.xml"
                evidence.write_text("failed model evidence", encoding="utf-8")
                with self.assertRaisesRegex(AssertionError, "original failure"):
                    try:
                        raise AssertionError("original failure")
                    finally:
                        module._ensure_absent("OwnedScratch")
                        module._remove_successful_dump(dump)
                module.call.assert_not_called()
                self.assertEqual("failed model evidence", evidence.read_text(encoding="utf-8"))
                with self.assertRaisesRegex(AssertionError, "retained failed project"):
                    module._ensure_absent("OwnedScratch")
                module.call.assert_not_called()

    def test_successful_scratch_work_is_cleaned(self):
        for filename in ("test_create_project.py", "test_import_configuration_from_xml.py"):
            with self.subTest(filename=filename), tempfile.TemporaryDirectory() as directory:
                module = self.load(filename)
                dump = Path(directory) / "xml-source"
                dump.mkdir()
                module._ensure_absent("OwnedScratch")
                module._remove_successful_dump(dump)
                module.call.assert_called_once_with("delete_project", {
                    "projectName": "OwnedScratch", "deleteContent": True, "confirm": True,
                })
                self.assertFalse(dump.exists())
