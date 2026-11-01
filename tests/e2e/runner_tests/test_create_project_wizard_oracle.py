"""Native exact-.5 extension state oracle; no EDT calls or fixture mutations."""
import importlib.util
from pathlib import Path
import unittest
from unittest import mock


E2E_DIR = Path(__file__).resolve().parents[1]
HARNESS_SPEC = importlib.util.spec_from_file_location("wizard_oracle_harness", E2E_DIR / "harness.py")
HARNESS = importlib.util.module_from_spec(HARNESS_SPEC)
HARNESS_SPEC.loader.exec_module(HARNESS)
TOOL_SPEC = importlib.util.spec_from_file_location("wizard_oracle_tool", E2E_DIR / "tools/test_create_project.py")
TOOL = importlib.util.module_from_spec(TOOL_SPEC)
with mock.patch.dict("sys.modules", {"harness": HARNESS}):
    TOOL_SPEC.loader.exec_module(TOOL)


def root(properties):
    return '<extension xsi:type="mdclassExtension:ConfigurationExtension">' + properties + '</extension>'


class NativeWizardOracleTest(unittest.TestCase):
    def setUp(self):
        self.no_calls = mock.patch.object(TOOL, "call", side_effect=AssertionError("no MCP call allowed"))
        self.no_calls.start()
        self.addCleanup(self.no_calls.stop)

    def test_exact_5_requires_all_native_states_independent_of_old_fixture(self):
        actual = TOOL._expected_wizard_extension_states(root('<defaultRoles>Extended</defaultRoles>'),
                                                       '8.3.27', '2025.2.5.2')
        self.assertEqual(actual, {
            'defaultRunMode': 'Checked', 'usePurposes': 'Checked',
            'commandInterface': 'Extended', 'mainSectionCommandInterface': 'Extended',
            'defaultLanguage': 'Checked', 'interfaceCompatibilityMode': 'Checked',
            'compatibilityMode': 'Checked', 'defaultStyle': 'Extended', 'defaultRoles': 'Extended',
        })

    def test_style_and_roles_use_the_native_distinct_compatibility_thresholds(self):
        for version, style, roles in [('8.3.11', False, False), ('8.3.12', False, False),
                                      ('8.3.13', True, False), ('8.3.14', True, True),
                                      ('8.3.27', True, True)]:
            with self.subTest(version=version):
                states = TOOL._expected_wizard_extension_states(root(''), version, '2025.2.5.2')
                self.assertEqual(states.get('defaultStyle'), 'Extended' if style else None)
                self.assertEqual(states.get('defaultRoles'), 'Extended' if roles else None)

    def test_other_runtime_keeps_the_original_fixture_oracle(self):
        fixture = root('<defaultRunMode>Checked</defaultRunMode><defaultRoles>Extended</defaultRoles>')
        for runtime in ('2025.2.2', '2025.2.5.3', '2026.1.0'):
            with self.subTest(runtime=runtime):
                self.assertEqual(TOOL._expected_wizard_extension_states(fixture, '8.3.27', runtime),
                                 {'defaultRunMode': 'Checked', 'defaultRoles': 'Extended'})

    def test_unknown_compatibility_is_refused_instead_of_guessing(self):
        with self.assertRaisesRegex(AssertionError, 'explicit base compatibility'):
            TOOL._expected_wizard_extension_states(root(''), 'unknown', '2025.2.5.2')

    def test_actual_result_keeps_extra_and_wrong_states_visible_to_equality(self):
        for properties, expected in [('<defaultStyle>Checked</defaultStyle>', {'defaultStyle': 'Checked'}),
                                      ('<unexpected>Extended</unexpected>', {'unexpected': 'Extended'})]:
            self.assertEqual(TOOL._extension_states(root(properties), 'actual'), expected)

    def test_duplicate_actual_property_is_refused(self):
        with self.assertRaisesRegex(AssertionError, 'duplicate extension property'):
            TOOL._extension_states(root('<defaultStyle>Extended</defaultStyle>'
                                        '<defaultStyle>Checked</defaultStyle>'), 'actual')


if __name__ == '__main__':
    unittest.main()
