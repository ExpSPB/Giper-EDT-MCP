"""Documentation generation must not publish MCP profile refusals as tool guides."""

import argparse
import contextlib
import importlib.util
import io
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch


ROOT = Path(__file__).resolve().parents[3]
SPEC = importlib.util.spec_from_file_location("generate_tool_docs", ROOT / "docs/generate_tool_docs.py")
GENERATOR = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(GENERATOR)


def guide(name, description="Published description"):
    return "# %s\n\n%s\n\n## Parameters\nNo parameters.\n\n## Guide\nComplete guide.\n" % (name, description)


def fake_rpc(hidden, denied):
    """Only the generator's read-only MCP methods exist on this fake server."""
    def rpc(_url, method, params=None):
        if method == "tools/list":
            return {"tools": [{"name": "alpha", "description": "Fresh alpha description"}]}
        if method != "tools/call":
            raise AssertionError("unexpected MCP method: " + method)
        if params["name"] == "list_toolsets":
            return {"structuredContent": {
                "progressiveDisclosure": False,
                "toolsets": [{"id": "all", "title": "All tools", "tools": ["alpha", *hidden]}]}}
        if params["name"] != "get_tool_guide":
            raise AssertionError("unexpected MCP tool: " + params["name"])
        name = params["arguments"]["toolName"]
        if name in denied:
            error = ("Tool '%s' is not allowed by profile 'default'. "
                     "Ask the user to add it to that profile: EDT Preferences "
                     "\u2192 MCP Server \u2192 Tools.") % name
            return {"isError": True, "content": [{"type": "text", "text": error}],
                    "structuredContent": {"success": False, "error": error}}
        return {"content": [{"type": "resource", "resource": {"text": guide(name, "Fresh description")}}]}
    return rpc


@contextlib.contextmanager
def configured_generator(root, hidden, denied):
    docs = root / "docs"
    tools = docs / "tools"
    tools.mkdir(parents=True)
    with contextlib.ExitStack() as stack:
        stack.enter_context(patch.object(GENERATOR, "HERE", str(docs)))
        stack.enter_context(patch.object(GENERATOR, "OUT_DIR", str(tools)))
        stack.enter_context(patch.object(GENERATOR, "parse_args", return_value=argparse.Namespace(
            host="127.0.0.1", port="9879")))
        stack.enter_context(patch.object(GENERATOR, "rpc", side_effect=fake_rpc(hidden, denied)))
        stack.enter_context(contextlib.redirect_stdout(io.StringIO()))
        stderr = stack.enter_context(contextlib.redirect_stderr(io.StringIO()))
        yield tools, stderr


class GenerateToolDocsTest(unittest.TestCase):
    def test_is_error_payload_is_not_returned_as_guide_text(self):
        with patch.object(GENERATOR, "rpc", side_effect=fake_rpc(["git"], {"git"})):
            with self.assertRaisesRegex(GENERATOR.ToolCallError, "not allowed by profile"):
                GENERATOR.call_text("unused", "get_tool_guide", {"toolName": "git"})

    def test_two_hidden_existing_pages_and_published_summaries_are_preserved(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with configured_generator(root, ["ask_workmate", "git"], {"ask_workmate", "git"}) as (tools, stderr):
                before = {}
                rows = {}
                for name in ["ask_workmate", "git"]:
                    before[name] = guide(name).encode("utf-8")
                    (tools / (name + ".md")).write_bytes(before[name])
                    rows[name] = "| [`%s`](%s.md) | Published %s summary with \\| pipe. |" % (name, name, name)
                (tools / "README.md").write_text("\n".join(rows.values()) + "\n", encoding="utf-8")
                (root / "EDT-MCP.md").write_text(
                    "Before index\n<!-- TOOLS-INDEX:START -->\nOld index\n"
                    "<!-- TOOLS-INDEX:END -->\nAfter index\n", encoding="utf-8")

                GENERATOR.main()

                for name in before:
                    self.assertEqual(before[name], (tools / (name + ".md")).read_bytes())
                    self.assertIn(rows[name], (tools / "README.md").read_text(encoding="utf-8"))
                    self.assertIn(rows[name].replace("](" + name + ".md)", "](docs/tools/" + name + ".md)"),
                                  (root / "EDT-MCP.md").read_text(encoding="utf-8"))
                    self.assertIn("preserving " + str(tools / (name + ".md")), stderr.getvalue())
                self.assertIn("profile that allows this tool", stderr.getvalue())
                self.assertTrue((tools / "alpha.md").read_text(encoding="utf-8").startswith("# alpha\n"))
                self.assertIn("**3 tools.**", (tools / "README.md").read_text(encoding="utf-8"))
                index = (root / "EDT-MCP.md").read_text(encoding="utf-8")
                self.assertTrue(index.startswith("Before index\n"))
                self.assertTrue(index.endswith("After index\n"))

    def test_new_hidden_tool_without_existing_guide_fails_before_any_write(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with configured_generator(root, ["new_hidden"], {"new_hidden"}) as (tools, _stderr):
                sentinel = guide("alpha", "Existing alpha page").encode("utf-8")
                (tools / "alpha.md").write_bytes(sentinel)
                (tools / "README.md").write_text("Existing index\n", encoding="utf-8")
                with self.assertRaisesRegex(RuntimeError, "Cannot generate new_hidden.*no valid existing guide"):
                    GENERATOR.main()
                self.assertEqual(sentinel, (tools / "alpha.md").read_bytes())
                self.assertEqual("Existing index\n", (tools / "README.md").read_text(encoding="utf-8"))
                self.assertFalse((tools / "new_hidden.md").exists())

    def test_existing_refusal_text_cannot_satisfy_hidden_guide_fallback(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with configured_generator(root, ["git"], {"git"}) as (tools, _stderr):
                refusal = b"Tool 'git' is not allowed by profile 'default'.\n"
                (tools / "git.md").write_bytes(refusal)
                with self.assertRaisesRegex(RuntimeError, "Cannot generate git.*no valid existing guide"):
                    GENERATOR.main()
                self.assertEqual(refusal, (tools / "git.md").read_bytes())
                self.assertFalse((tools / "alpha.md").exists())

    def test_visible_tool_error_does_not_use_an_existing_page_as_fallback(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with configured_generator(root, [], {"alpha"}) as (tools, _stderr):
                previous = guide("alpha").encode("utf-8")
                (tools / "alpha.md").write_bytes(previous)
                with self.assertRaisesRegex(RuntimeError, "Cannot generate alpha"):
                    GENERATOR.main()
                self.assertEqual(previous, (tools / "alpha.md").read_bytes())

    def test_toolset_failure_aborts_before_writing_a_truncated_index(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with configured_generator(root, ["git"], set()) as (tools, _stderr):
                previous = guide("alpha").encode("utf-8")
                index = b"| [`git`](git.md) | Existing Git description. |\n"
                (tools / "alpha.md").write_bytes(previous)
                (tools / "README.md").write_bytes(index)
                delegate = fake_rpc(["git"], set())

                def failed_toolsets(url, method, params=None):
                    if method == "tools/call" and params["name"] == "list_toolsets":
                        return {"isError": True, "structuredContent": {
                            "success": False, "error": "Cannot inspect toolsets"}}
                    return delegate(url, method, params)

                with patch.object(GENERATOR, "rpc", side_effect=failed_toolsets):
                    with self.assertRaisesRegex(GENERATOR.ToolCallError, "Cannot inspect toolsets"):
                        GENERATOR.main()
                self.assertEqual(previous, (tools / "alpha.md").read_bytes())
                self.assertEqual(index, (tools / "README.md").read_bytes())
                self.assertFalse((tools / "git.md").exists())

    def test_hidden_internal_failure_or_another_tools_denial_cannot_use_fallback(self):
        failures = ["Failed to render guide for git: internal error",
                    "Tool 'git' is not allowed by profile 'default'.",
                    "Tool 'ask_workmate' is not allowed by profile 'default'. "
                    "Ask the user to add it to that profile: EDT Preferences "
                    "\u2192 MCP Server \u2192 Tools."]
        for message in failures:
            with self.subTest(message=message), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                with configured_generator(root, ["git"], set()) as (tools, _stderr):
                    previous = guide("git").encode("utf-8")
                    index = b"Existing index\n"
                    (tools / "git.md").write_bytes(previous)
                    (tools / "README.md").write_bytes(index)
                    delegate = fake_rpc(["git"], set())

                    def failed_guide(url, method, params=None):
                        if (method == "tools/call" and params["name"] == "get_tool_guide"
                                and params["arguments"]["toolName"] == "git"):
                            return {"isError": True, "content": [{"type": "text", "text": message}]}
                        return delegate(url, method, params)

                    with patch.object(GENERATOR, "rpc", side_effect=failed_guide):
                        with self.assertRaisesRegex(RuntimeError, "Cannot generate git"):
                            GENERATOR.main()
                    self.assertEqual(previous, (tools / "git.md").read_bytes())
                    self.assertEqual(index, (tools / "README.md").read_bytes())
                    self.assertFalse((tools / "alpha.md").exists())


if __name__ == "__main__":
    unittest.main()
