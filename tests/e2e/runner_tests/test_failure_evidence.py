"""Ordinary failures preserve their original fixture evidence before any reset."""

import importlib.util
import json
import os
from pathlib import Path
import subprocess
import tempfile
from types import SimpleNamespace
import unittest
from unittest import mock


SPEC = importlib.util.spec_from_file_location(
    "failure_evidence_runner", Path(__file__).resolve().parents[1] / "run_all.py")
RUNNER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(RUNNER)


class CallTimeout(Exception):
    pass


class Skip(Exception):
    pass


class ResetFailed(Exception):
    pass


class FailureEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.repo = self.root / "repo"
        self.repo.mkdir()
        self.fixture = self.repo / "fixture"
        self.fixture.mkdir()
        self.file = self.fixture / "Form.form"
        self.file.write_bytes(b"\0baseline")
        self.git("init", "-q")
        self.git("add", ".")
        self.git("-c", "user.name=Runner test", "-c", "user.email=runner@example.invalid",
                 "commit", "-qm", "baseline")
        self.evidence = self.root / "evidence"
        self.workspace = self.root / "workspace"
        metadata = self.workspace / ".metadata"
        metadata.mkdir(parents=True)
        (metadata / ".log").write_bytes(b"current EDT\n")
        (metadata / ".bak_1.log").write_bytes(b"rotated EDT\n")
        self.rpc = self.root / "mcp-call-history.jsonl"
        self.rpc.write_bytes(b'{"request":"original"}\n')
        self.harness = SimpleNamespace(
            REPO_ROOT=str(self.repo), ALL_FIXTURE_RELS=["fixture"],
            MCP_URL="http://127.0.0.1:9877/mcp", GIT_STATUS_TIMEOUT=15,
            E2ECallTimeout=CallTimeout, E2ESkip=Skip, E2EModelResetFailed=ResetFailed,
            calls_aborted=mock.Mock(return_value=False),
            fixtures_frozen=mock.Mock(return_value=False),
            abort_further_calls=mock.Mock(return_value=True),
            _workspace_dir=mock.Mock(return_value=str(self.workspace)),
        )
        self.env = mock.patch.dict(os.environ, {
            "E2E_FAILURE_EVIDENCE_DIR": str(self.evidence),
            "E2E_RPC_LOG_FILES": json.dumps([str(self.rpc)]),
        })
        self.env.start()
        self.addCleanup(self.env.stop)

    def git(self, *args):
        return subprocess.check_output(["git", "-C", str(self.repo), *args],
                                       stderr=subprocess.STDOUT)

    def failure(self, error=None):
        def fail():
            raise error or AssertionError("the model did not match")
        return {"tool": "writer", "name": "test_failure", "kind": "write-metadata", "func": fail}

    def test_staged_unstaged_binary_and_untracked_bytes_are_saved_before_cleanup(self):
        self.file.write_bytes(b"\0staged")
        self.git("add", "fixture/Form.form")
        self.file.write_bytes(b"\0unstaged")
        new = self.fixture / "new.mdo"
        new.write_bytes(b"untracked original")
        want_staged = self.git("diff", "--cached", "--binary")
        want_unstaged = self.git("diff", "--binary")

        def cleanup(*_):
            packet, = self.evidence.iterdir()
            self.assertEqual(b"\0unstaged", (packet / "fixture-files/fixture/Form.form").read_bytes())
            self.assertEqual(b"untracked original", (packet / "fixture-files/fixture/new.mdo").read_bytes())
            self.assertEqual(want_staged, (packet / "staged.patch").read_bytes())
            self.assertEqual(want_unstaged, (packet / "unstaged.patch").read_bytes())
            self.assertEqual(b"rotated EDT\n", (packet / "edt-logs/.bak_1.log").read_bytes())
            self.assertEqual(self.rpc.read_bytes(), (packet / "rpc-logs/0-mcp-call-history.jsonl").read_bytes())
            self.assertTrue(json.loads((packet / "manifest.json").read_text(encoding="utf-8"))["complete"])
            self.git("reset", "--hard", "HEAD")
            new.unlink()

        with mock.patch.object(RUNNER, "_reset_after_write", side_effect=cleanup) as reset:
            with self.assertRaisesRegex(AssertionError, "model did not match"):
                RUNNER._run_test_unit(self.harness, self.failure())
        reset.assert_called_once()
        self.harness._workspace_dir.assert_called_once_with("")
        self.assertEqual(b"\0baseline", self.file.read_bytes())

    def test_capture_failure_freezes_and_prevents_cleanup(self):
        self.file.write_bytes(b"\0only surviving evidence")
        with mock.patch.object(RUNNER, "_write_failure_evidence", side_effect=OSError("disk full")), \
                mock.patch.object(RUNNER, "_reset_after_write") as reset:
            with self.assertRaisesRegex(ResetFailed, "disk full") as caught:
                RUNNER._run_test_unit(self.harness, self.failure())
        reset.assert_not_called()
        self.harness.abort_further_calls.assert_called_once()
        self.assertIn(str(self.evidence), str(caught.exception))
        self.assertIn("model did not match", str(caught.exception))
        self.assertEqual(b"\0only surviving evidence", self.file.read_bytes())

    def test_destination_inside_checkout_is_refused_without_cleanup(self):
        with mock.patch.dict(os.environ, {"E2E_FAILURE_EVIDENCE_DIR": str(self.fixture / "evidence")}), \
                mock.patch.object(RUNNER, "_reset_after_write") as reset:
            with self.assertRaisesRegex(ResetFailed, "outside the fixture checkout"):
                RUNNER._run_test_unit(self.harness, self.failure())
        reset.assert_not_called()
        self.assertFalse((self.fixture / "evidence").exists())

    def test_pass_does_not_invoke_collector(self):
        with mock.patch.object(RUNNER, "_write_failure_evidence") as capture, \
                mock.patch.object(RUNNER, "_reset_after_write") as reset:
            RUNNER._run_test_unit(self.harness, {"func": lambda: None})
        capture.assert_not_called()
        reset.assert_called_once()

    def test_unknown_outcome_keeps_existing_freeze_without_diagnostic_calls(self):
        self.harness.calls_aborted.return_value = True
        with mock.patch.object(RUNNER, "_write_failure_evidence") as capture, \
                mock.patch.object(RUNNER, "_reset_after_write"):
            with self.assertRaises(AssertionError):
                RUNNER._run_test_unit(self.harness, self.failure())
        capture.assert_not_called()
        self.harness._workspace_dir.assert_not_called()

    def test_timeout_does_not_collect_or_reset(self):
        with mock.patch.object(RUNNER, "_write_failure_evidence") as capture, \
                mock.patch.object(RUNNER, "_reset_after_write") as reset:
            with self.assertRaises(CallTimeout):
                RUNNER._run_test_unit(self.harness, self.failure(CallTimeout("pending")))
        capture.assert_not_called()
        reset.assert_not_called()

    def test_without_opt_in_existing_cleanup_contract_is_retained(self):
        with mock.patch.dict(os.environ, {"E2E_FAILURE_EVIDENCE_DIR": ""}), \
                mock.patch.object(RUNNER, "_write_failure_evidence") as capture, \
                mock.patch.object(RUNNER, "_reset_after_write") as reset:
            with self.assertRaises(AssertionError):
                RUNNER._run_test_unit(self.harness, self.failure())
        capture.assert_not_called()
        reset.assert_called_once()

    def test_git_capture_failure_marks_packet_incomplete_and_keeps_bytes(self):
        self.file.write_bytes(b"\0evidence")
        with mock.patch.object(RUNNER.subprocess, "run", side_effect=OSError("git unavailable")), \
                mock.patch.object(RUNNER, "_reset_after_write") as reset:
            with self.assertRaisesRegex(ResetFailed, "git unavailable"):
                RUNNER._run_test_unit(self.harness, self.failure(RuntimeError("original error")))
        reset.assert_not_called()
        packet, = self.evidence.iterdir()
        record = json.loads((packet / "manifest.json").read_text(encoding="utf-8"))
        self.assertFalse(record["complete"])
        self.assertEqual("original error", record["error"]["message"])
        self.assertEqual(b"\0evidence", self.file.read_bytes())

    def test_untracked_bytes_changing_after_copy_freeze_even_when_git_status_is_unchanged(self):
        new = self.fixture / "new.mdo"
        new.write_bytes(b"before asynchronous export")
        original_status = self.git("status", "--porcelain=v1", "-z")

        def export_after_fixture_copy(_):
            new.write_bytes(b"after asynchronous export")
            return str(self.workspace)

        self.harness._workspace_dir.side_effect = export_after_fixture_copy
        with mock.patch.object(RUNNER, "_reset_after_write") as reset:
            with self.assertRaisesRegex(ResetFailed, "fixture bytes changed during capture"):
                RUNNER._run_test_unit(self.harness, self.failure())
        reset.assert_not_called()
        self.harness.abort_further_calls.assert_called_once()
        self.assertEqual(original_status, self.git("status", "--porcelain=v1", "-z"))
        packet, = self.evidence.iterdir()
        self.assertEqual(b"before asynchronous export", (packet / "fixture-files/fixture/new.mdo").read_bytes())
        self.assertFalse(json.loads((packet / "manifest.json").read_text(encoding="utf-8"))["complete"])


if __name__ == "__main__":
    unittest.main()
