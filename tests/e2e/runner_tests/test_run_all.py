"""Pure runner-contract tests; no EDT server or fixture checkout required."""

import ast
import datetime
import importlib.util
import inspect
import json
import os
import subprocess
import tempfile
import unittest
from contextlib import ExitStack, redirect_stdout
from io import StringIO
from pathlib import Path
from unittest import mock


E2E_DIR = os.path.dirname(os.path.dirname(__file__))
RUN_ALL_PATH = os.path.join(E2E_DIR, "run_all.py")
SPEC = importlib.util.spec_from_file_location("edt_mcp_e2e_run_all", RUN_ALL_PATH)
RUN_ALL = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(RUN_ALL)

HARNESS_SPEC = importlib.util.spec_from_file_location(
    "edt_mcp_e2e_harness", os.path.join(E2E_DIR, "harness.py"))
HARNESS = importlib.util.module_from_spec(HARNESS_SPEC)
HARNESS_SPEC.loader.exec_module(HARNESS)
RATCHET_SPEC = importlib.util.spec_from_file_location(
    "edt_mcp_e2e_log_ratchet", os.path.join(E2E_DIR, "tools", "test_edt_log_ratchet.py"))
RATCHET = importlib.util.module_from_spec(RATCHET_SPEC)
with mock.patch.dict("sys.modules", {"harness": HARNESS}):
    RATCHET_SPEC.loader.exec_module(RATCHET)


class RunAllRatchetTest(unittest.TestCase):
    def _probe_token_from(self, captured_calls):
        """The probe's token, read out of the call it actually made - after checking that call
        against the published contract.

        These runner tests never see the live server, so nothing here would notice the probe
        naming an argument the tool does not read. That failure is silent in the worst way: the
        tool would answer normally, log no error line, and the ratchet would SKIP on every run
        rather than fail. `tools_list.golden.json` is the half of the contract the fake cannot
        supply, so the tool and its argument are looked up there. Both are taken from the captured
        call, so this tracks whatever the probe sends instead of restating it.
        """
        probe_tool, probe_arguments = captured_calls[-1]
        with open(os.path.join(E2E_DIR, "tools_list.golden.json"),
                  encoding="utf-8") as handle:
            contracts = {tool["name"]: tool for tool in json.load(handle)}
        self.assertIn(probe_tool, contracts)
        probe_argument_key, = probe_arguments
        self.assertIn(probe_argument_key,
                      contracts[probe_tool]["inputSchema"]["properties"])
        return probe_arguments[probe_argument_key]

    def test_an_explicit_workspace_that_is_not_one_fails_rather_than_advising(self):
        """The override naming a directory with no .metadata is the same operator error as the
        override naming the wrong EDT, only cruder, so it gets the same verdict. Skipping here
        would print advice about the wrong problem - it would tell an operator who HAS set the
        variable to set it."""
        with tempfile.TemporaryDirectory() as tmp:
            with mock.patch.object(RATCHET, "call", side_effect=AssertionError("no call")),                     mock.patch.dict(os.environ, {"EDT_MCP_EDT_WORKSPACE": tmp}):
                with self.assertRaises(HARNESS.E2EAssertion) as failed:
                    RATCHET.test_run_adds_no_unbaselined_error_entries_to_the_edt_log()
                self.assertIn(tmp, str(failed.exception))
                self.assertIn("no .metadata directory there", str(failed.exception))

    def test_an_explicit_workspace_must_contain_the_server_log_probe(self):
        def entry_at(plugin, severity, message, epoch=None):
            if epoch is None:
                epoch = HARNESS.RUN_STARTED_AT
            stamp = datetime.datetime.fromtimestamp(epoch).strftime("%Y-%m-%d %H:%M:%S")
            return "!ENTRY %s %s 0 %s.000\n!MESSAGE %s\n" % (
                plugin, severity, stamp, message)

        with tempfile.TemporaryDirectory() as tmp:
            metadata = os.path.join(tmp, ".metadata")
            os.makedirs(metadata)
            log_path = os.path.join(metadata, ".log")
            captured_calls = []
            write_probe = {"enabled": False}

            def fake_call(tool, arguments):
                captured_calls.append((tool, arguments))
                # Model the server's logging boundary: only the missing-project request writes
                # the line the probe depends on. If the probe stops provoking that outcome, the
                # ratchet's missing-evidence check must be the assertion that catches it.
                token = arguments.get("projectName")
                if write_probe["enabled"] and token is not None:
                    with open(log_path, "a", encoding="utf-8") as handle:
                        handle.write(entry_at(
                            RATCHET.OUR_PLUGIN, "2",
                            "Failed tools/call: get_project_errors - Project not found: %s"
                            % token,
                            datetime.datetime.now().timestamp()))

            with mock.patch.object(RATCHET, "call", side_effect=fake_call), \
                    mock.patch.dict(os.environ, {"EDT_MCP_EDT_WORKSPACE": tmp}):
                with self.assertRaises(HARNESS.E2EAssertion) as failed:
                    RATCHET.test_run_adds_no_unbaselined_error_entries_to_the_edt_log()
                self.assertIn("EDT workspace was named explicitly at %s" % tmp,
                              str(failed.exception))
                self.assertIn("probe sent through get_project_errors", str(failed.exception))
                self.assertFalse(os.path.exists(log_path))

                with open(log_path, "w", encoding="utf-8") as handle:
                    handle.write(entry_at(
                        "org.eclipse.core.runtime", "1", "unrelated platform status"))
                with self.assertRaises(HARNESS.E2EAssertion) as failed:
                    RATCHET.test_run_adds_no_unbaselined_error_entries_to_the_edt_log()
                self.assertIn("EDT workspace was named explicitly at %s" % tmp,
                              str(failed.exception))
                self.assertIn("probe sent through get_project_errors", str(failed.exception))

                with open(log_path, "w", encoding="utf-8") as handle:
                    handle.write(entry_at(
                        RATCHET.OUR_PLUGIN, "2", "another server instance was here"))
                with self.assertRaises(HARNESS.E2EAssertion) as failed:
                    RATCHET.test_run_adds_no_unbaselined_error_entries_to_the_edt_log()
                self.assertIn("EDT workspace was named explicitly at %s" % tmp,
                              str(failed.exception))
                self.assertIn("probe sent through get_project_errors", str(failed.exception))

                with open(log_path, "w", encoding="utf-8"):
                    pass
                write_probe["enabled"] = True
                self.assertIsNone(
                    RATCHET.test_run_adds_no_unbaselined_error_entries_to_the_edt_log())
                probe_token = self._probe_token_from(captured_calls)
                self.assertTrue(probe_token.startswith("edtmcplogprobe"))
                with open(log_path, encoding="utf-8") as handle:
                    self.assertIn("Project not found: %s" % probe_token, handle.read())

                failure_message = "Runner probe gate found an unbaselined plugin error"
                with open(log_path, "w", encoding="utf-8") as handle:
                    handle.write(entry_at(RATCHET.OUR_PLUGIN, "4", failure_message))
                with self.assertRaises(HARNESS.E2EAssertion) as failed:
                    RATCHET.test_run_adds_no_unbaselined_error_entries_to_the_edt_log()
                self.assertIn(failure_message, str(failed.exception))
                probe_token = self._probe_token_from(captured_calls)
                with open(log_path, encoding="utf-8") as handle:
                    self.assertIn("Project not found: %s" % probe_token, handle.read())

    def test_an_inferred_workspace_without_the_server_log_probe_still_skips(self):
        with tempfile.TemporaryDirectory() as tmp:
            os.makedirs(os.path.join(tmp, ".metadata"))
            project_path = os.path.join(tmp, "Base")
            os.makedirs(project_path)
            projects_table = (
                "| Name | State | Path | Open | EDT Project | Natures |\n"
                "|---|---|---|---|---|---|\n"
                "| Base | ready | %s | Yes | Yes | fixture |\n" % project_path)
            captured_calls = []

            def fake_call(tool, arguments):
                captured_calls.append((tool, arguments))
                if tool == "list_projects":
                    return mock.Mock(text=projects_table)
                if tool == "get_project_errors":
                    return mock.Mock()
                raise AssertionError("unexpected tool call: %s" % tool)

            with mock.patch.object(HARNESS, "call", side_effect=fake_call), \
                    mock.patch.object(RATCHET, "call", side_effect=fake_call), \
                    mock.patch.dict(os.environ, {}, clear=False):
                os.environ.pop("EDT_MCP_EDT_WORKSPACE", None)
                with self.assertRaises(HARNESS.E2ESkip) as skipped:
                    RATCHET.test_run_adds_no_unbaselined_error_entries_to_the_edt_log()

            self.assertIn("EDT workspace was located at %s" % tmp, str(skipped.exception))
            self.assertIn("does not carry this server's own log output", str(skipped.exception))
            self.assertEqual(["list_projects", "get_project_errors"],
                             [tool for tool, _arguments in captured_calls])

    def test_error_from_first_read_survives_probe_overwriting_its_generation(self):
        error_message = "Error from generation overwritten while emitting the probe"
        probe_token = "edtmcplogprobefixedtoken"
        events = []

        def collect(_workspace, token):
            events.append(("collect", token))
            if token is None:
                return {error_message: 1}, False
            return {}, True

        def emit_probe():
            events.append(("emit", probe_token))
            return probe_token

        with mock.patch.object(RATCHET, "_workspace_dir", return_value="workspace"), \
                mock.patch.object(RATCHET, "_collect_our_errors", side_effect=collect), \
                mock.patch.object(RATCHET, "_emit_log_probe", side_effect=emit_probe), \
                mock.patch.object(RATCHET, "_load_baseline", return_value=set()), \
                mock.patch.dict(os.environ, {}, clear=False):
            os.environ.pop("EDT_MCP_EDT_WORKSPACE", None)
            with self.assertRaises(HARNESS.E2EAssertion) as failed:
                RATCHET.test_run_adds_no_unbaselined_error_entries_to_the_edt_log()

        self.assertIn(error_message, str(failed.exception))
        self.assertEqual([
            ("collect", None),
            ("emit", probe_token),
            ("collect", probe_token),
        ], events)

    @staticmethod
    def _mutation_harness():
        harness = mock.Mock()
        harness.PROJECT = "Base"
        harness.EXT_OBJECTS_PROJECT = "ExternalObjects"
        harness.OPTIONAL_FIXTURE_PROJECTS = ("ExternalObjects",)
        harness.ALL_FIXTURE_PROJECTS = ["Base", "Extension", "ExternalObjects"]
        harness.optional_model_synced.return_value = True
        harness.confirmed_mutation_tools.return_value = frozenset({"modify_metadata"})
        harness.mutation_kind_violation_tools.return_value = ("modify_metadata",)
        harness.mutations_unresolved.return_value = False
        harness.mutated_fixture_projects.return_value = frozenset()
        harness.dependent_mutation_fixture_projects.return_value = frozenset()
        harness.evidenced_mutation_fixture_projects.return_value = frozenset()
        harness.mutation_could_have_cascaded.return_value = False
        harness.reset_all_fixtures.return_value = True
        harness.fixtures_frozen.return_value = False
        harness.calls_aborted.return_value = False
        harness._refresh_reset_enabled.return_value = False
        return harness

    def test_kind_violation_resets_every_fixture_through_model_reset_and_prints_advisory(self):
        harness = self._mutation_harness()
        output = StringIO()

        with redirect_stdout(output):
            RUN_ALL._reset_after_write(harness, {"name": "writer", "kind": "action"})

        harness.reset_all_fixtures.assert_called_once_with()
        harness.reset_model.assert_called_once_with(harness.ALL_FIXTURE_PROJECTS)
        harness.call.assert_not_called()
        self.assertIn("[kind-advisory]", output.getvalue())

    def test_kind_violation_skips_unsynced_external_objects_named_only_by_refused_call(self):
        harness = self._mutation_harness()
        harness.optional_model_synced.return_value = False
        # Another call produced the confirmed mutation that triggered this branch. The refused
        # call only named ExternalObjects, so it is present in the attempted-target union but not
        # in the per-call outcome-evidenced set.
        harness.mutated_fixture_projects.return_value = frozenset({"ExternalObjects"})

        RUN_ALL._reset_after_write(harness, {"name": "writer", "kind": "action"})

        harness.reset_all_fixtures.assert_called_once_with()
        harness.reset_model.assert_called_once_with(["Base", "Extension"])

    def test_kind_violation_resets_unsynced_external_objects_named_by_evidenced_call(self):
        harness = self._mutation_harness()
        harness.optional_model_synced.return_value = False
        harness.mutated_fixture_projects.return_value = frozenset({"ExternalObjects"})
        harness.evidenced_mutation_fixture_projects.return_value = frozenset(
            {"ExternalObjects"})

        RUN_ALL._reset_after_write(harness, {"name": "writer", "kind": "action"})

        harness.reset_all_fixtures.assert_called_once_with()
        harness.reset_model.assert_called_once_with(harness.ALL_FIXTURE_PROJECTS)
        harness.evidenced_mutation_fixture_projects.assert_called_once_with()

    def test_kind_violation_skips_unsynced_external_objects_when_call_did_not_target_it(self):
        harness = self._mutation_harness()
        harness.optional_model_synced.return_value = False
        harness.mutated_fixture_projects.return_value = frozenset({"Base"})

        RUN_ALL._reset_after_write(harness, {"name": "writer", "kind": "action"})

        # This case is deliberately indistinguishable from the pre-fix behaviour: an unsynced
        # fixture the call never named stays skipped either way. It guards the OPPOSITE direction
        # from its sibling above - that widening the set to "what the call targeted" did not
        # quietly become "everything" - so it is a boundary test, not a discriminating one. The
        # assertion is therefore on the decision, not on which accessor was consulted to reach it.
        harness.reset_all_fixtures.assert_called_once_with()
        harness.reset_model.assert_called_once_with(["Base", "Extension"])

    def test_each_optional_fixture_is_judged_on_its_own_sync(self):
        harness = self._mutation_harness()
        harness.OPTIONAL_FIXTURE_PROJECTS = ("ExternalObjects", "Supported")
        harness.ALL_FIXTURE_PROJECTS = ["Base", "Extension", "ExternalObjects", "Supported"]
        harness.optional_model_synced.side_effect = lambda project: project == "Supported"

        RUN_ALL._reset_after_write(harness, {"name": "writer", "kind": "action"})

        harness.reset_model.assert_called_once_with(["Base", "Extension", "Supported"])

    def test_kind_violation_model_reset_failure_propagates(self):
        class E2EModelResetFailed(Exception):
            pass

        harness = self._mutation_harness()
        harness.E2EModelResetFailed = E2EModelResetFailed
        harness.reset_model.side_effect = E2EModelResetFailed("could not restore fixture")

        with self.assertRaisesRegex(E2EModelResetFailed, "could not restore fixture"):
            RUN_ALL._reset_after_write(harness, {"name": "writer", "kind": "action"})

    def test_declared_write_resets_base_and_named_fixture_projects(self):
        harness = self._mutation_harness()
        harness.mutation_kind_violation_tools.return_value = ()
        harness.model_is_pristine.return_value = False
        harness.reset_fixture.return_value = True
        harness.mutated_fixture_projects.return_value = frozenset({"Extension"})

        RUN_ALL._reset_after_write(harness, {"name": "writer", "kind": "write-metadata"})

        harness.reset_model.assert_called_once_with(["Base", "Extension"])

    def test_declared_cascade_write_resets_every_available_fixture_project(self):
        harness = self._mutation_harness()
        harness.mutation_kind_violation_tools.return_value = ()
        harness.model_is_pristine.return_value = False
        harness.reset_fixture.return_value = True
        harness.optional_model_synced.return_value = False
        harness.mutated_fixture_projects.return_value = frozenset({"Base"})
        harness.mutation_could_have_cascaded.return_value = True

        RUN_ALL._reset_after_write(
            harness, {"name": "base delete", "kind": "write-metadata"})

        harness.reset_model.assert_called_once_with(["Base", "Extension"])

    def _skipping_unit_harness(self, confirmed, unresolved):
        harness = self._mutation_harness()
        harness.E2ESkip = HARNESS.E2ESkip
        harness.E2ECallTimeout = HARNESS.E2ECallTimeout
        harness.confirmed_mutation_tools.return_value = frozenset(confirmed)
        harness.mutations_unresolved.return_value = unresolved
        return harness

    def test_skip_after_a_confirmed_write_still_resets_the_model(self):
        harness = self._skipping_unit_harness({"create_metadata"}, False)
        test = {"name": "seeds then skips", "kind": "write-metadata",
                "func": mock.Mock(side_effect=HARNESS.E2ESkip("unsupported"))}

        with mock.patch.object(RUN_ALL, "_reset_after_write") as reset:
            with self.assertRaises(HARNESS.E2ESkip):
                RUN_ALL._run_test_unit(harness, test)

        reset.assert_called_once_with(harness, test)

    def test_skip_after_an_unresolved_write_still_resets_the_model(self):
        harness = self._skipping_unit_harness(set(), True)
        test = {"name": "wire death then skip", "kind": "write-metadata",
                "func": mock.Mock(side_effect=HARNESS.E2ESkip("unsupported"))}

        with mock.patch.object(RUN_ALL, "_reset_after_write") as reset:
            with self.assertRaises(HARNESS.E2ESkip):
                RUN_ALL._run_test_unit(harness, test)

        reset.assert_called_once_with(harness, test)

    def test_skip_before_any_write_does_not_reset(self):
        harness = self._skipping_unit_harness(set(), False)
        test = {"name": "skips first", "kind": "write-metadata",
                "func": mock.Mock(side_effect=HARNESS.E2ESkip("unsupported"))}

        with mock.patch.object(RUN_ALL, "_reset_after_write") as reset:
            with self.assertRaises(HARNESS.E2ESkip):
                RUN_ALL._run_test_unit(harness, test)

        reset.assert_not_called()

    def test_every_shard_holds_its_own_log_ratchet_out_of_the_main_loop(self):
        first = {"tool": "alpha", "name": "first"}
        deferred = {"tool": "omega", "name": "last", "last": True}
        ratchet = {"tool": "_edt_log_ratchet", "name": "audit", "last": True}

        scheduled, held = RUN_ALL.schedule_tests([first, deferred],
                                                  [first, ratchet, deferred],
                                                  per_shard=True)

        self.assertEqual([first, deferred], scheduled)
        self.assertEqual([ratchet], held)
        self.assertNotIn(ratchet, scheduled)

    def test_post_cleanup_ratchet_failure_is_a_junit_testcase_failure(self):
        ratchet = {"tool": "_edt_log_ratchet", "name": "audit"}
        results = [(ratchet, "fail", "cleanup logged a new plugin ERROR", 0.25)]
        handle, path = tempfile.mkstemp(suffix=".xml")
        os.close(handle)
        self.addCleanup(lambda: os.path.exists(path) and os.remove(path))

        RUN_ALL.write_junit(results, path, final_clean=True)

        with open(path, encoding="utf-8") as stream:
            report = stream.read()
        self.assertIn('tests="1" failures="1"', report)
        self.assertIn('_edt_log_ratchet::audit', report)
        self.assertIn('cleanup logged a new plugin ERROR', report)

    def test_main_executes_log_ratchet_after_the_last_final_cleanup_call(self):
        tree = ast.parse(inspect.getsource(RUN_ALL.main))
        cleanup_lines = []
        ratchet_loop_line = None
        nfail_line = None
        for node in ast.walk(tree):
            if isinstance(node, ast.Call) and isinstance(node.func, ast.Attribute) \
                    and node.func.attr == "final_cleanup":
                cleanup_lines.append(node.lineno)
            if isinstance(node, ast.For) and isinstance(node.iter, ast.Name) \
                    and node.iter.id == "log_ratchets":
                ratchet_loop_line = node.lineno
            if isinstance(node, ast.Assign) and any(isinstance(target, ast.Name)
                    and target.id == "nfail" for target in node.targets):
                nfail_line = node.lineno

        self.assertTrue(cleanup_lines)
        self.assertIsNotNone(ratchet_loop_line)
        self.assertIsNotNone(nfail_line)
        self.assertLess(max(cleanup_lines), ratchet_loop_line)
        self.assertLess(ratchet_loop_line, nfail_line,
                        "ratchet result must be counted before the exit decision")


class RunAllFailurePreservationTest(unittest.TestCase):
    def test_main_preserves_reset_failure_and_final_cleanup_failures(self):
        for failure in ("reset-failed", "final-reset", "final-timeout"):
            with self.subTest(failure=failure), ExitStack() as stack:
                fake = mock.Mock()
                fake.E2ECallTimeout = HARNESS.E2ECallTimeout
                fake.E2EModelResetFailed = HARNESS.E2EModelResetFailed
                fake.PROJECT = HARNESS.PROJECT
                fake.MCP_URL = "http://127.0.0.1:9876/mcp"
                fake.REGISTRY = [{"tool": "writer", "name": "mutation", "kind": "write-metadata"}]
                fake.wait_for_project_ready.return_value = True
                fake.mutations_unresolved.return_value = False
                fake.calls_aborted.return_value = False
                fake.snapshot_model_baseline.return_value = (True, True)
                fake.all_fixtures_status.return_value = "staged and unstaged failure evidence"
                fake.freeze_fixtures.return_value = True
                if failure != "reset-failed":
                    exception = (HARNESS.E2ECallTimeout if failure == "final-timeout"
                                 else HARNESS.E2EModelResetFailed)
                    fake.final_cleanup.side_effect = [None, exception("failed cleanup")]
                args = mock.Mock(host="127.0.0.1", port=9876, project=HARNESS.PROJECT,
                                 filter=None, shard="1/1", test_timeout=10, junit=None)
                stack.enter_context(mock.patch.dict("sys.modules", {"harness": fake}))
                stack.enter_context(mock.patch.object(RUN_ALL, "parse_args", return_value=args))
                stack.enter_context(mock.patch.object(RUN_ALL.os, "listdir", return_value=[]))
                stack.enter_context(mock.patch.object(RUN_ALL, "schedule_tests",
                                                     return_value=(fake.REGISTRY, [])))
                stack.enter_context(mock.patch.object(RUN_ALL, "_run_with_timeout", return_value=(
                    "reset-failed" if failure == "reset-failed" else "pass", "evidence", False)))
                with redirect_stdout(StringIO()), self.assertRaises(SystemExit) as result:
                    RUN_ALL.main()
                self.assertEqual(1, result.exception.code)
                fake.freeze_fixtures.assert_called_once_with()
                fake.reset_all_fixtures.assert_not_called()
                self.assertEqual(1 if failure == "reset-failed" else 2,
                                 fake.final_cleanup.call_count)


class RunAllRefreshCleanupTest(unittest.TestCase):
    """Exercise the runner through the real scoped reset callbacks, with no Git or MCP IO."""

    def setUp(self):
        self.stack = ExitStack()
        self.addCleanup(self.stack.close)
        self.events = []
        self.stack.enter_context(mock.patch.dict(
            os.environ, {"MCP_MODEL_RESET_MODE": "refresh"}))
        self.patch(RUN_ALL, "_ABANDONED", False)
        for name, value in {
            "_FIXTURES_FROZEN": False, "_TIMED_OUT": False, "_ABORT_REASON": None,
            "_MUTATIONS_UNRESOLVED": 0, "_MUTATION_CONFIRMED": False,
            "_CASCADE_CONFIRMED_CALLED": False, "_UNRESOLVED_CASCADE_CALLS": 0,
            "_CALLED_TOOLS": set(), "_CONFIRMED_MUTATION_TOOLS": set(),
            "_MUTATED_PROJECTS": set(), "_EVIDENCED_MUTATION_PROJECTS": set(),
            "_UNRESOLVED_MUTATION_PROJECTS": {}, "_OPTIONAL_MODELS_SYNCED": set(),
            "_COLD_RESET_PROJECTS": set(),
        }.items():
            self.patch(HARNESS, name, value)
        self.patch(HARNESS, "model_is_pristine", mock.Mock(return_value=False))
        self.ready = self.patch(HARNESS, "wait_for_project_ready", mock.Mock(
            side_effect=lambda **kwargs: self.events.append("ready") or True))
        self.quiet = self.patch(HARNESS, "wait_for_fixture_quiet", mock.Mock(
            side_effect=lambda *, rel: self.events.append(("quiet", rel)) or True))
        self.revert = self.patch(HARNESS, "_reset_rel", mock.Mock(
            side_effect=lambda rel: self.events.append(("revert", rel)) or []))
        self.model_operations = []
        def restore(project):
            self.events.append(("model", project))
            self.model_operations.append((project, HARNESS._model_reset_label(project)))
            return True
        self.restore = self.patch(HARNESS, "_reset_fixture_project", mock.Mock(side_effect=restore))
        self.patch(HARNESS, "_baseline_mismatch", mock.Mock(return_value=None))
        self.patch(HARNESS, "_non_base_mismatch", mock.Mock(return_value=None))
        self.patch(HARNESS, "_failed_settle_evidence", mock.Mock())
        self.synced = self.patch(HARNESS, "_mark_model_synced", mock.Mock(
            wraps=HARNESS._mark_model_synced))

    def patch(self, target, name, value):
        return self.stack.enter_context(mock.patch.object(target, name, value))

    def confirm_write(self, project, tool="modify_metadata"):
        args = {"projectName": project}
        HARNESS._record_attempt(tool, args)
        HARNESS._record_outcome(tool, args, False, {"success": True})

    def reset(self, kind="write-metadata"):
        with redirect_stdout(StringIO()):
            RUN_ALL._reset_after_write(HARNESS, {"name": "writer", "kind": kind})

    @staticmethod
    def refresh_events(project):
        rel = HARNESS.FIXTURE_REL_BY_PROJECT[project]
        return ["ready", ("quiet", rel), ("revert", rel), ("model", project), "ready"]

    def test_refresh_first_revert_waits_for_ready_and_quiet_for_each_written_model(self):
        self.confirm_write(HARNESS.TESTS_PROJECT)

        self.reset()

        self.assertEqual(self.refresh_events(HARNESS.PROJECT)
                         + self.refresh_events(HARNESS.TESTS_PROJECT), self.events)
        self.assertFalse(HARNESS.mutations_unresolved())

    def test_clean_mode_keeps_early_base_revert_and_uses_no_quiet_wait(self):
        self.confirm_write(HARNESS.PROJECT)
        with mock.patch.dict(os.environ, {"MCP_MODEL_RESET_MODE": "clean"}):
            self.reset()

        self.assertEqual([
            ("revert", HARNESS.PROJECT_REL), "ready", ("revert", HARNESS.PROJECT_REL),
            ("model", HARNESS.PROJECT), "ready",
            "ready", ("revert", HARNESS.TESTS_PROJECT_REL), ("model", HARNESS.TESTS_PROJECT), "ready",
        ], self.events)
        self.quiet.assert_not_called()

    def test_ordinary_successful_base_write_resets_extension_after_base_without_optional_inference(self):
        self.confirm_write(HARNESS.PROJECT)
        self.reset()
        self.assertEqual(self.refresh_events(HARNESS.PROJECT)
                         + self.refresh_events(HARNESS.TESTS_PROJECT), self.events)

    def test_unknown_base_write_resets_extension_and_refusal_does_not_infer_it(self):
        args = {"projectName": HARNESS.PROJECT}
        HARNESS._record_attempt("modify_metadata", args)
        HARNESS._record_outcome("modify_metadata", args, True, {"mutationOutcomeUnknown": True})
        self.reset()
        self.assertEqual(self.refresh_events(HARNESS.PROJECT)
                         + self.refresh_events(HARNESS.TESTS_PROJECT), self.events)
        self.assertEqual([(HARNESS.PROJECT, "clean_project"),
                          (HARNESS.TESTS_PROJECT, "clean_project")], self.model_operations)
        HARNESS.begin_test_calls()
        self.events.clear()
        self.model_operations.clear()
        HARNESS._record_attempt("modify_metadata", args)
        HARNESS._record_outcome("modify_metadata", args, True, {"success": False})
        self.reset()
        self.assertEqual(self.refresh_events(HARNESS.PROJECT), self.events)
        self.assertEqual([(HARNESS.PROJECT, "revalidate_objects + resync_to_disk")],
                         self.model_operations)

    def test_read_action_and_skip_restore_persistent_cold_evidence_without_false_advisory(self):
        for kind, skip in (("read", False), ("action", False), ("read", True)):
            with self.subTest(kind=kind, skip=skip):
                args = {"projectName": HARNESS.PROJECT}
                HARNESS._record_attempt("modify_metadata", args)
                HARNESS._record_outcome("modify_metadata", args, True,
                                       {"mutationOutcomeUnknown": True})
                HARNESS.begin_test_calls()
                # This unrelated successful reset must neither retire the base's evidence nor
                # permit the next read/action unit to skip its required cleanup.
                HARNESS.reset_model([HARNESS.EXT_OBJECTS_PROJECT])
                self.events.clear()
                self.model_operations.clear()
                self.assertFalse(HARNESS.mutations_unresolved())
                self.assertEqual(frozenset(), HARNESS.confirmed_mutation_tools())
                body = mock.Mock(side_effect=HARNESS.E2ESkip("read skipped") if skip else None)
                output = StringIO()
                test = {"name": "later reader", "kind": kind, "func": body}
                with redirect_stdout(output):
                    if skip:
                        with self.assertRaises(HARNESS.E2ESkip):
                            RUN_ALL._run_test_unit(HARNESS, test)
                    else:
                        RUN_ALL._run_test_unit(HARNESS, test)
                body.assert_called_once_with()
                self.assertEqual(self.refresh_events(HARNESS.PROJECT)
                                 + self.refresh_events(HARNESS.TESTS_PROJECT), self.events)
                self.assertEqual([(HARNESS.PROJECT, "clean_project"),
                                  (HARNESS.TESTS_PROJECT, "clean_project")], self.model_operations)
                self.assertNotIn("kind-advisory", output.getvalue())
                self.assertEqual(set(), HARNESS._COLD_RESET_PROJECTS)

    def test_read_action_and_skip_reset_persistent_optional_target_in_both_modes(self):
        for mode in ("refresh", "clean"):
            for tool in ("modify_metadata", "delete_metadata"):
                for kind, skip in (("read", False), ("action", False), ("read", True)):
                    with self.subTest(mode=mode, tool=tool, kind=kind, skip=skip), \
                            mock.patch.dict(os.environ, {"MCP_MODEL_RESET_MODE": mode}):
                        args = {"projectName": HARNESS.EXT_OBJECTS_PROJECT, "confirm": True}
                        HARNESS._record_attempt(tool, args)
                        HARNESS._record_outcome(tool, args, tool == "modify_metadata",
                            {"mutationOutcomeUnknown": True} if tool == "modify_metadata"
                            else {"action": "executed"})
                        HARNESS.begin_test_calls()
                        HARNESS.reset_model([HARNESS.PROJECT])
                        self.events.clear()
                        self.model_operations.clear()
                        self.restore.reset_mock()
                        self.assertIn(HARNESS.EXT_OBJECTS_PROJECT,
                                      HARNESS.evidenced_mutation_fixture_projects())
                        body = mock.Mock(side_effect=HARNESS.E2ESkip("read skipped") if skip else None)
                        output = StringIO()
                        with redirect_stdout(output):
                            test = {"name": "later reader", "kind": kind, "func": body}
                            if skip:
                                with self.assertRaises(HARNESS.E2ESkip):
                                    RUN_ALL._run_test_unit(HARNESS, test)
                            else:
                                RUN_ALL._run_test_unit(HARNESS, test)
                        body.assert_called_once_with()
                        self.assertEqual([mock.call(HARNESS.PROJECT),
                                          mock.call(HARNESS.EXT_OBJECTS_PROJECT)],
                                         self.restore.call_args_list)
                        self.assertEqual([(HARNESS.PROJECT,
                            "revalidate_objects + resync_to_disk" if mode == "refresh" else "clean_project"),
                            (HARNESS.EXT_OBJECTS_PROJECT, "clean_project")], self.model_operations)
                        self.assertNotIn("kind-advisory", output.getvalue())
                        self.assertEqual(set(), HARNESS._COLD_RESET_PROJECTS)

    def test_aborted_or_frozen_runner_does_not_start_dependency_cleanup(self):
        self.confirm_write(HARNESS.PROJECT)
        for flag in ("_ABANDONED",):
            with mock.patch.object(RUN_ALL, flag, True):
                self.reset()
        with mock.patch.object(HARNESS, "_FIXTURES_FROZEN", True):
            self.reset()
        with mock.patch.object(HARNESS, "_TIMED_OUT", True):
            self.reset()
        self.assertEqual([], self.events)

    def test_failed_gate_preserves_staged_and_unstaged_fixture_evidence(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)
            def git(*args):
                return subprocess.check_output(["git", "-C", directory, *args], stderr=subprocess.STDOUT)
            git("init", "-q")
            fixture = path / "Form.form"
            fixture.write_bytes(b"\0baseline")
            git("add", "Form.form")
            git("-c", "user.name=Runner Test", "-c", "user.email=runner@example.invalid",
                "commit", "-qm", "baseline")
            fixture.write_bytes(b"\0staged mutation")
            git("add", "Form.form")
            fixture.write_bytes(b"\0unstaged mutation")
            staged = git("diff", "--cached", "--binary")
            unstaged = git("diff", "--binary")
            reset = self.patch(HARNESS, "reset_all_fixtures", mock.Mock())
            with redirect_stdout(StringIO()):
                RUN_ALL._preserve_failed_fixtures(HARNESS, "RESET-FAILED")
            self.assertTrue(HARNESS.fixtures_frozen())
            self.assertEqual(b"\0unstaged mutation", fixture.read_bytes())
            self.assertEqual(staged, git("diff", "--cached", "--binary"))
            self.assertEqual(unstaged, git("diff", "--binary"))
            reset.assert_not_called()

    def test_failed_freeze_reports_incomplete_preservation_without_claiming_untouched(self):
        output = StringIO()
        with mock.patch.object(HARNESS, "freeze_fixtures", return_value=False), redirect_stdout(output):
            RUN_ALL._preserve_failed_fixtures(HARNESS, "RESET-FAILED")
        self.assertIn("preservation is incomplete", output.getvalue())
        self.assertIn("active fixture reset", output.getvalue())
        self.assertNotIn("left the fixtures untouched", output.getvalue())

    def test_refresh_kind_violation_restores_mandatory_and_evidenced_optional_models(self):
        self.confirm_write(HARNESS.EXT_OBJECTS_PROJECT)

        self.reset(kind="action")

        self.assertEqual(
            self.refresh_events(HARNESS.PROJECT)
            + self.refresh_events(HARNESS.TESTS_PROJECT)
            + self.refresh_events(HARNESS.EXT_OBJECTS_PROJECT)
            + [("revert", HARNESS.FIXTURE_REL_BY_PROJECT[HARNESS.SUPPORTED_PROJECT])],
            self.events)

    def test_refresh_kind_violation_preserves_disk_cleanup_without_resetting_refused_optional(self):
        self.confirm_write(HARNESS.PROJECT)
        args = {"projectName": HARNESS.EXT_OBJECTS_PROJECT}
        HARNESS._record_attempt("modify_metadata", args)
        HARNESS._record_outcome("modify_metadata", args, True, {"success": False})

        self.reset(kind="action")

        self.assertEqual(
            self.refresh_events(HARNESS.PROJECT)
            + self.refresh_events(HARNESS.TESTS_PROJECT)
            + [("revert", HARNESS.FIXTURE_REL_BY_PROJECT[HARNESS.EXT_OBJECTS_PROJECT]),
               ("revert", HARNESS.FIXTURE_REL_BY_PROJECT[HARNESS.SUPPORTED_PROJECT])],
            self.events)
        self.assertNotIn(mock.call(HARNESS.EXT_OBJECTS_PROJECT), self.restore.call_args_list)

    def test_refresh_cascade_keeps_synced_and_evidenced_optional_scopes(self):
        self.confirm_write(HARNESS.EXT_OBJECTS_PROJECT)
        HARNESS._OPTIONAL_MODELS_SYNCED.add(HARNESS.SUPPORTED_PROJECT)
        self.patch(HARNESS, "mutation_could_have_cascaded", mock.Mock(return_value=True))

        self.reset()

        self.assertEqual(sum((self.refresh_events(project)
                              for project in HARNESS.ALL_FIXTURE_PROJECTS), []), self.events)

    def test_optional_disk_cleanup_failed_git_and_dirty_path_aborts_without_model_reset(self):
        self.confirm_write(HARNESS.PROJECT)
        optional_rel = HARNESS.FIXTURE_REL_BY_PROJECT[HARNESS.EXT_OBJECTS_PROJECT]

        def failed_optional_revert(rel):
            self.events.append(("revert", rel))
            return ["checkout failed"] if rel == optional_rel else []

        self.revert.side_effect = failed_optional_revert
        status = self.patch(HARNESS, "status_porcelain_rel", mock.Mock(
            return_value=" M %s/src/ExternalReport.mdo" % optional_rel))

        with self.assertRaisesRegex(HARNESS.E2EModelResetFailed, "checkout failed.*still dirty"):
            self.reset(kind="action")

        status.assert_called_once_with(optional_rel)
        self.assertEqual([mock.call(HARNESS.PROJECT), mock.call(HARNESS.TESTS_PROJECT)],
                         self.restore.call_args_list)
        self.assertEqual(
            self.refresh_events(HARNESS.PROJECT)
            + self.refresh_events(HARNESS.TESTS_PROJECT)
            + [("revert", optional_rel),
               ("revert", HARNESS.FIXTURE_REL_BY_PROJECT[HARNESS.SUPPORTED_PROJECT])],
            self.events)

    def test_optional_disk_cleanup_failed_git_but_clean_path_is_allowed_without_model_reset(self):
        self.confirm_write(HARNESS.PROJECT)
        optional_rel = HARNESS.FIXTURE_REL_BY_PROJECT[HARNESS.EXT_OBJECTS_PROJECT]

        def failed_optional_revert(rel):
            self.events.append(("revert", rel))
            return ["clean failed after checkout restored the path"] if rel == optional_rel else []

        self.revert.side_effect = failed_optional_revert
        status = self.patch(HARNESS, "status_porcelain_rel", mock.Mock(return_value=""))

        self.reset(kind="action")

        status.assert_called_once_with(optional_rel)
        self.assertEqual([mock.call(HARNESS.PROJECT), mock.call(HARNESS.TESTS_PROJECT)],
                         self.restore.call_args_list)
        self.assertEqual(
            self.refresh_events(HARNESS.PROJECT)
            + self.refresh_events(HARNESS.TESTS_PROJECT)
            + [("revert", optional_rel),
               ("revert", HARNESS.FIXTURE_REL_BY_PROJECT[HARNESS.SUPPORTED_PROJECT])],
            self.events)

    def test_clean_kind_violation_keeps_early_all_fixture_revert(self):
        self.confirm_write(HARNESS.PROJECT)
        with mock.patch.dict(os.environ, {"MCP_MODEL_RESET_MODE": "clean"}):
            self.reset(kind="action")

        self.assertEqual([("revert", rel) for rel in HARNESS.ALL_FIXTURE_RELS],
                         self.events[:len(HARNESS.ALL_FIXTURE_RELS)])
        self.assertEqual([mock.call(HARNESS.PROJECT), mock.call(HARNESS.TESTS_PROJECT)],
                         self.restore.call_args_list)
        self.quiet.assert_not_called()

    def test_refresh_not_ready_never_reverts_or_restores_or_retires_unknown_mutation(self):
        HARNESS._record_attempt("modify_metadata", {"projectName": HARNESS.PROJECT})
        self.patch(HARNESS, "MODEL_SETTLE_ATTEMPTS", 1)

        def not_ready(**kwargs):
            kwargs["failure_details"].append("project is building")
            return False

        self.ready.side_effect = not_ready
        with self.assertRaises(HARNESS.E2EModelResetFailed):
            self.reset()

        self.revert.assert_not_called()
        self.restore.assert_not_called()
        self.synced.assert_not_called()
        self.assertTrue(HARNESS.mutations_unresolved())

    def test_refresh_not_quiet_never_reverts_any_fixture_after_kind_violation(self):
        self.confirm_write(HARNESS.PROJECT)
        self.patch(HARNESS, "MODEL_SETTLE_ATTEMPTS", 1)
        self.quiet.side_effect = lambda **kwargs: False

        with self.assertRaises(HARNESS.E2EModelResetFailed):
            self.reset(kind="action")

        self.revert.assert_not_called()
        self.restore.assert_not_called()
        self.synced.assert_not_called()

    def test_refresh_model_failure_propagates_before_optional_disk_cleanup(self):
        self.confirm_write(HARNESS.PROJECT)
        self.patch(HARNESS, "MODEL_CLEAN_ATTEMPTS", 1)
        self.restore.side_effect = lambda project: False

        with self.assertRaises(HARNESS.E2EModelResetFailed):
            self.reset(kind="action")

        self.assertEqual([mock.call(HARNESS.PROJECT_REL)], self.revert.call_args_list)
        self.synced.assert_not_called()

    def test_frozen_fixtures_allow_no_cleanup_requests_or_reverts(self):
        self.confirm_write(HARNESS.PROJECT)
        HARNESS.freeze_fixtures()

        self.reset(kind="action")

        self.assertEqual([], self.events)
        self.synced.assert_not_called()

    def test_freeze_during_ready_refuses_revert_and_does_not_retire_unknown_write(self):
        HARNESS._record_attempt("modify_metadata", {"projectName": HARNESS.PROJECT})

        def freeze_during_ready(**kwargs):
            self.events.append("ready")
            HARNESS.freeze_fixtures()
            return True

        self.ready.side_effect = freeze_during_ready
        with self.assertRaisesRegex(HARNESS.E2EModelResetFailed, "fixtures are frozen"):
            self.reset()

        self.assertEqual(["ready", ("quiet", HARNESS.PROJECT_REL)], self.events)
        self.revert.assert_not_called()
        self.restore.assert_not_called()
        self.synced.assert_not_called()
        self.assertTrue(HARNESS.mutations_unresolved())

    def test_freeze_during_quiet_refuses_revert_and_does_not_retire_unknown_write(self):
        HARNESS._record_attempt("modify_metadata", {"projectName": HARNESS.PROJECT})

        def freeze_during_quiet(*, rel):
            self.events.append(("quiet", rel))
            HARNESS.freeze_fixtures()
            return True

        self.quiet.side_effect = freeze_during_quiet
        with self.assertRaisesRegex(HARNESS.E2EModelResetFailed, "fixtures are frozen"):
            self.reset()

        self.assertEqual(["ready", ("quiet", HARNESS.PROJECT_REL)], self.events)
        self.revert.assert_not_called()
        self.restore.assert_not_called()
        self.synced.assert_not_called()
        self.assertTrue(HARNESS.mutations_unresolved())

    def test_unknown_transport_write_freezes_disk_and_prevents_all_cleanup_calls(self):
        write = lambda: HARNESS.call("modify_metadata", {"projectName": HARNESS.PROJECT})
        with mock.patch.object(HARNESS, "_post", side_effect=ConnectionResetError("lost response")):
            with self.assertRaisesRegex(ConnectionResetError, "lost response"):
                RUN_ALL._run_test_unit(HARNESS, {
                    "name": "unknown write", "kind": "write-metadata", "func": write})

        self.assertTrue(HARNESS.calls_aborted())
        self.assertTrue(HARNESS.fixtures_frozen())
        self.assertTrue(HARNESS.mutations_unresolved())
        self.assertEqual([], self.events)
        self.synced.assert_not_called()


if __name__ == "__main__":
    unittest.main()
