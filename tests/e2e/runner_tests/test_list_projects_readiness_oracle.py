"""Execute the canonical offline readiness oracle without an EDT endpoint."""
import importlib.util
from pathlib import Path
import unittest
from unittest import mock


E2E_DIR = Path(__file__).resolve().parents[1]
HARNESS_SPEC = importlib.util.spec_from_file_location("readiness_oracle_harness", E2E_DIR / "harness.py")
HARNESS = importlib.util.module_from_spec(HARNESS_SPEC)
HARNESS_SPEC.loader.exec_module(HARNESS)
TOOL_SPEC = importlib.util.spec_from_file_location("readiness_oracle_tool", E2E_DIR / "tools/test_list_projects.py")
TOOL = importlib.util.module_from_spec(TOOL_SPEC)
with mock.patch.dict("sys.modules", {"harness": HARNESS}):
    TOOL_SPEC.loader.exec_module(TOOL)


class ListProjectsReadinessOracleTest(unittest.TestCase):
    def test_canonical_body_preserves_all_rows_and_fail_closed_unparsed_diagnostics(self):
        with mock.patch.object(HARNESS, "call", side_effect=AssertionError("Offline oracle made an RPC")), \
                mock.patch.object(TOOL, "call", side_effect=AssertionError("Offline oracle made an RPC")):
            TOOL.test_readiness_gate_examines_every_row_not_just_the_last()


if __name__ == "__main__":
    unittest.main()
