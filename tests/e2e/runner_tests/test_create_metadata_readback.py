"""Pure dimension read-back regression; no MCP calls or fixture mutations."""
import importlib.util
from pathlib import Path
import unittest
from unittest import mock


E2E_DIR = Path(__file__).resolve().parents[1]
HARNESS_SPEC = importlib.util.spec_from_file_location("dimension_readback_harness", E2E_DIR / "harness.py")
HARNESS = importlib.util.module_from_spec(HARNESS_SPEC)
HARNESS_SPEC.loader.exec_module(HARNESS)
TOOL_SPEC = importlib.util.spec_from_file_location(
    "dimension_readback_tool", E2E_DIR / "tools/test_create_metadata.py")
TOOL = importlib.util.module_from_spec(TOOL_SPEC)
with mock.patch.dict("sys.modules", {"harness": HARNESS}):
    TOOL_SPEC.loader.exec_module(TOOL)


DIMENSION = "E2EUnifiedDim"
ACTUAL_TWO_TABLES = """## dimensions
| Name | Synonym | Type | Indexing | Fill Checking | Full Text Search | Password Mode | Multi Line | Quick Choice | Create On Input |
|------|---------|------|----------|---------------|------------------|---------------|------------|--------------|-----------------|
| E2EUnifiedDim | | String | - | DontCheck | - | No | No | Auto | Auto |

## Dimension Indexing
| Name | Indexing |
|------|----------|
| E2EUnifiedDim | DontIndex |
"""


class DimensionModelReadbackTest(unittest.TestCase):
    def setUp(self):
        self.no_calls = mock.patch.object(TOOL, "call", side_effect=AssertionError("no MCP call allowed"))
        self.no_calls.start()
        self.addCleanup(self.no_calls.stop)

    def test_actual_type_and_indexing_tables_read_exact_type_column(self):
        TOOL._assert_dimension_model_default(ACTUAL_TWO_TABLES, DIMENSION)

    def test_wrong_type_is_rejected(self):
        with self.assertRaises(AssertionError):
            TOOL._assert_dimension_model_default(ACTUAL_TWO_TABLES.replace("| String |", "| Number |"), DIMENSION)

    def test_missing_dimension_in_type_table_is_rejected(self):
        with self.assertRaises(AssertionError):
            TOOL._assert_dimension_model_default(
                ACTUAL_TWO_TABLES.replace("| E2EUnifiedDim | |", "| Other | |"), DIMENSION)

    def test_duplicate_dimension_in_type_table_is_rejected(self):
        data = "| E2EUnifiedDim | | String | - | DontCheck | - | No | No | Auto | Auto |\n"
        with self.assertRaises(AssertionError):
            TOOL._assert_dimension_model_default(ACTUAL_TWO_TABLES.replace(data, data + data), DIMENSION)

    def test_name_and_type_are_selected_by_header_instead_of_position(self):
        TOOL._assert_dimension_model_default(
            "| Type | Name |\n|------|------|\n| String | E2EUnifiedDim |\n", DIMENSION)

    def test_a_different_table_cannot_supply_the_missing_type(self):
        markdown = "| Name | Type |\n|------|------|\n| Other | Number |\n\n"
        markdown += "| Name | Indexing |\n|------|----------|\n| E2EUnifiedDim | String |\n"
        with self.assertRaises(AssertionError):
            TOOL._assert_dimension_model_default(markdown, DIMENSION)


if __name__ == "__main__":
    unittest.main()
