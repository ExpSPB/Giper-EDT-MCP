"""Offline regression tests for the live tool-documentation generator."""

import argparse
import contextlib
import importlib.util
import io
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch


SPEC = importlib.util.spec_from_file_location(
    "generate_tool_docs", Path(__file__).resolve().parents[1] / "generate_tool_docs.py"
)
GENERATOR = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(GENERATOR)


class GenerateToolDocsTest(unittest.TestCase):
    def test_explicit_profile_selects_its_own_endpoint_without_changing_default(self):
        args = argparse.Namespace(host="127.0.0.1", port="9876", profile=None)
        self.assertEqual("http://127.0.0.1:9876/mcp", GENERATOR.endpoint_url(args))
        args.profile = "e2e-docs"
        self.assertEqual("http://127.0.0.1:9876/mcp/profiles/e2e-docs", GENERATOR.endpoint_url(args))
        args.profile = "invalid/extra"
        self.assertTrue(GENERATOR.endpoint_url(args).endswith("/invalid%2Fextra"))

    def generate(self, guides, resource_error=None, resource_contents=None, profile=None):
        """Run the real generator with a fake RPC transport, without opening a socket."""
        requests = []

        def rpc(_url, method, params=None):
            requests.append((method, params))
            if method == "tools/list":
                return {"tools": [{"name": "large_tool", "description": "Large guide."}]}
            if method == "resources/read":
                if resource_error:
                    raise resource_error
                if resource_contents is not None:
                    return {"contents": resource_contents}
                uri = params["uri"]
                return {"contents": [{"uri": uri, "text": guides[uri.removeprefix("guide://")]}]}
            if method == "tools/call":
                if params["name"] == "list_toolsets":
                    return {"structuredContent": {
                        "progressiveDisclosure": False,
                        "toolsets": [{"id": "test", "title": "Test tools", "tools": sorted(guides)}],
                    }}
                if params["name"] == "get_tool_guide":
                    markdown = guides[params["arguments"]["toolName"]]
                    # Reproduce the tools/call cap which resources/read bypasses.
                    if len(markdown) > 100_000:
                        markdown = markdown[:99_000] + "\n[OUTPUT TRUNCATED]"
                    return {"content": [{"type": "resource", "resource": {"text": markdown}}]}
            raise AssertionError("Unexpected RPC: %s %r" % (method, params))

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            docs = root / "docs"
            docs.mkdir()
            (root / "EDT-MCP.md").write_text(
                "Before\n<!-- TOOLS-INDEX:START -->\nold\n<!-- TOOLS-INDEX:END -->\nAfter\n",
                encoding="utf-8",
            )
            output = docs / "tools"
            errors = io.StringIO()
            with patch.object(GENERATOR, "HERE", str(docs)), \
                    patch.object(GENERATOR, "OUT_DIR", str(output)), \
                    patch.object(GENERATOR, "parse_args", return_value=argparse.Namespace(
                        host="offline.invalid", port="0", profile=profile)), \
                    patch.object(GENERATOR, "rpc", side_effect=rpc), \
                    contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(errors):
                GENERATOR.main()
            pages = {p.name: p.read_bytes().decode("utf-8") for p in output.glob("*.md")}
            readme = (root / "EDT-MCP.md").read_text(encoding="utf-8")
        return pages, readme, requests, errors.getvalue()

    def test_oversized_guides_use_uncapped_resources(self):
        guide = "# large_tool\n\nLarge guide.\n\n" + "Full guide body.\n" * 8_000 + "END OF GUIDE"
        pages, _readme, requests, errors = self.generate({"large_tool": guide})

        self.assertTrue("END OF GUIDE" in pages["large_tool.md"], "The oversized guide lost its final line")
        self.assertNotIn("[OUTPUT TRUNCATED]", pages["large_tool.md"])
        self.assertIn(("resources/read", {"uri": "guide://large_tool"}), requests)
        self.assertFalse(any(p.get("name") == "get_tool_guide"
                             for m, p in requests if m == "tools/call"))
        self.assertNotIn("falling back to tools/call", errors)

    def test_hidden_tools_also_use_uncapped_resources_and_appear_in_both_indexes(self):
        guides = {
            "large_tool": "# large_tool\n\nLarge guide.",
            "hidden_tool": "# hidden_tool\n\nHidden description.\n\n" + "Hidden body.\n" * 10_000 + "HIDDEN END",
        }
        pages, readme, requests, _errors = self.generate(guides)

        self.assertTrue("HIDDEN END" in pages["hidden_tool.md"], "The hidden tool's oversized guide was truncated")
        self.assertIn(("resources/read", {"uri": "guide://hidden_tool"}), requests)
        self.assertIn("](hidden_tool.md)", pages["README.md"])
        self.assertIn("](docs/tools/hidden_tool.md)", readme)
        self.assertIn("**2 tools.**", pages["README.md"])
        self.assertIn("**2 tools**", readme)

    def test_named_profile_omission_is_not_described_as_a_default_policy(self):
        pages, readme, _requests, _errors = self.generate({
            "large_tool": "# large_tool\n\nVisible guide.",
            "hidden_tool": "# hidden_tool\n\nHidden guide."}, profile="e2e-docs")
        self.assertNotIn("not enabled by default", pages["README.md"])
        self.assertNotIn("not enabled by default", readme)

    def test_legacy_server_falls_back_to_embedded_resource_with_a_notice(self):
        guide = "# large_tool\n\nLegacy guide."
        pages, _readme, requests, errors = self.generate(
            {"large_tool": guide}, resource_error=GENERATOR.RpcError(
                "resources/read", {"code": -32601, "message": "Method not found"}))

        self.assertTrue(pages["large_tool.md"].startswith(guide))
        self.assertIn(("tools/call", {"name": "get_tool_guide",
                                     "arguments": {"toolName": "large_tool"}}), requests)
        self.assertIn("falling back to tools/call", errors)
        self.assertIn("[OUTPUT TRUNCATED]", errors)

    def test_empty_resource_response_is_not_hidden_by_tool_fallback(self):
        with self.assertRaisesRegex(RuntimeError, "resources/read returned no text"):
            self.generate({"large_tool": "# large_tool\n\nGuide."}, resource_contents=[])

    def test_unclassified_protocol_and_transport_failures_do_not_fall_back(self):
        errors = [GENERATOR.RpcError("resources/read", {"code": -32603, "message": "Internal error"}),
                  GENERATOR.RpcError("resources/read", {"code": -32602, "message": "Access denied"}),
                  OSError("Connection failed")]
        for error in errors:
            with self.subTest(error=error), patch.object(GENERATOR, "rpc", side_effect=error), \
                    patch.object(GENERATOR, "call_text") as fallback:
                with self.assertRaises(type(error)):
                    GENERATOR.read_guide("unused", "large_tool")
                fallback.assert_not_called()

    def test_known_hidden_resource_still_requires_the_tool_profile_check(self):
        uri = "guide://large_tool"
        error = GENERATOR.RpcError("resources/read", {"code": -32602, "message":
            "Unknown guide resource: %s. Call resources/list (or tools/list) for valid names." % uri})
        denial = GENERATOR.ToolCallError("get_tool_guide", "profile denial")
        with patch.object(GENERATOR, "rpc", side_effect=error), \
                patch.object(GENERATOR, "call_text", side_effect=denial) as fallback, \
                contextlib.redirect_stderr(io.StringIO()):
            with self.assertRaises(GENERATOR.ToolCallError):
                GENERATOR.read_guide("unused", "large_tool")
            fallback.assert_called_once_with("unused", "get_tool_guide", {"toolName": "large_tool"})

    def test_page_assembly_normalizes_crlf_lone_cr_and_trailing_whitespace(self):
        page = GENERATOR.assemble_page("# stub\r\n\r\nFirst.\rSecond.\r\n \t\r\n\r\n")

        self.assertEqual("# stub\n\nFirst.\nSecond." + GENERATOR.FOOTER, page)
        self.assertNotIn("\r", page)
        self.assertTrue(page.endswith(".*\n"))


    def test_guide_can_document_the_truncation_marker_without_being_truncated(self):
        text = "# large_tool\n\nLong results use the `[OUTPUT TRUNCATED]` notice.\n"
        response = {"contents": [{"uri": "guide://large_tool", "text": text}]}
        with patch.object(GENERATOR, "rpc", return_value=response):
            self.assertEqual(text, GENERATOR.read_guide("unused", "large_tool"))

    def test_resource_truncation_notice_is_rejected_without_fallback(self):
        for notice in ("[OUTPUT TRUNCATED]",
                       "[OUTPUT TRUNCATED] This result exceeded the maximum output size of 100 characters"):
            response = {"contents": [{"uri": "guide://large_tool",
                         "text": "# large_tool\n\nPartial guide\n\n---\n" + notice}]}
            with self.subTest(notice=notice), patch.object(GENERATOR, "rpc", return_value=response), \
                    patch.object(GENERATOR, "call_text") as fallback:
                with self.assertRaisesRegex(RuntimeError, "truncated guide"):
                    GENERATOR.read_guide("unused", "large_tool")
                fallback.assert_not_called()


if __name__ == "__main__":
    unittest.main()
