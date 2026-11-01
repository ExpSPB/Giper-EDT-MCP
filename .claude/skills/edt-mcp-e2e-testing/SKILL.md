---
name: edt-mcp-e2e-testing
description: How to write/run the AUTOMATED black-box e2e suite (tests/e2e/) that covers every EDT-MCP tool (62 today) against a live server with git-fixture isolation, happy + negative + error-quality coverage, and an anti-cheat bar. Use when adding/editing a test in tests/e2e/, adding a new tool (the coverage ratchet requires a test), or running the suite. (For the older MANUAL per-tool reference checklists see edt-mcp-testing; for Java unit/build see edt-mcp-build-test.)
---

# EDT-MCP — automated black-box e2e suite (tests/e2e/)

> **The full, authoritative, always-current guide is `tests/e2e/SKILL.md` in the repo. READ IT before writing or running a test** — it has the harness API, the worked examples, and the anti-cheat anti-pattern list. This skill is the entrypoint + the rules in brief; the repo file wins on any disagreement (it lives with the harness).

## What this is
A Python (stdlib-only) **black-box** suite: a real MCP client hits the **live** server on `:8765` and asserts the **real** effect. One file per tool: `tests/e2e/tools/test_<tool>.py`. Shared base: `harness.py`. Runner: `run_all.py` (serial, `--junit-xml`, per-test timeout). Every registered tool covered (the coverage ratchet `test_coverage_ratchet.py` fails the suite if a `tools/list` tool has no test). Different layer from Java/JUnit (which is headless and can't touch a real project).

## Run it
```
python tests/e2e/run_all.py --project TestConfiguration --junit-xml tests/e2e/e2e-results.xml
```
Needs a LIVE server on `:8765` + `TestConfiguration` loaded (the dev EDT copy — see the project memory / edt-redeploy loop). Execution is **serial** (one fixture, one git tree); authoring can be parallel (one agent per tool file). `--filter <substr>` runs a subset.

## Launch-preset wire checks

Launch EDT separately with `EDT_MCP_TOOL_PRESET=analysis-only` and `code-review`.
Pass the same value to the test process and disable progressive disclosure on the
EDT process (`EDT_MCP_PROGRESSIVE_DISCLOSURE=false`). Resolution is lazy and occurs
once per process, including bridge-first/manual startup. Predefined IDs are
`all-tools`, `analysis-only`, `code-review`, `development`, case-insensitive and
trimmed. Null/blank and `custom` use saved selection; explicit `custom` logs INFO
once. Unknown values are ignored with one WARNING listing predefined IDs.

In Giper the launch preset only restricts each saved profile: effective tools are
the intersection of its allowlist and the preset, plus mandatory
`get_server_status`. `all-tools` grants no additional rights. Explicit profile
endpoints exclude `enable_toolset`. Launch resolution does not persist a preset
or rewrite saved allowlists. Existing named-profile allowlists are preserved on
upgrade. The Tools tab edits saved data and shows the launch restriction as a
read-only note. Edits can change the intersection but cannot lift a launch
restriction. Its preset choice is per-profile provenance: manual checkbox edits
retain it; Custom changes provenance without changing the allowlist. A no-op
Apply keeps existing profile bytes/revisions unchanged.

Seed a disposable profile before launch, then set
`EDT_MCP_PRESET_PROFILE_ALLOWLIST` to that exact saved comma-separated allowlist.
This is independent stand-setup evidence; do not derive it from the filtered
tools/list response. With clean fixtures, run from `tests/e2e`, for example after
seeding `preset-check` with the following names:

```bash
export EDT_MCP_PRESET_PROFILE_ID=preset-check
export EDT_MCP_PRESET_PROFILE_ALLOWLIST=get_server_status,get_tool_guide,list_projects,read_module_source,write_module_source,clean_project,list_git_branches
EDT_MCP_TOOL_PRESET=analysis-only python -m tools.test_preset_launch
# Restart the owned EDT stand with code-review, then:
EDT_MCP_TOOL_PRESET=code-review python -m tools.test_preset_launch
```

`EDT_MCP_PRESET_PROFILE_ID` selects `/mcp/profiles/<id>` in the standalone entry
point before its handshake; omit it to test `/mcp`. Use `MCP_HOST`, `MCP_PORT`,
and `MCP_PROJECT` for the owned stand. The module checks the saved-profile upper
bound, expected permitted read tools, write-hint keep-list and excluded writers.
It never invokes a writer and verifies fixture cleanliness before/after. Missing
preset or saved-allowlist evidence reports a clear skip; other presets skip this
read-only policy check. These tests do not prove arbitrary named profiles have
all upstream keep-list tools enabled.

Run this module directly: the normal runner uses `clean_project` during model
setup/cleanup, and read-only presets disable that tool. Do not invoke the normal
full schedule against a preset-restricted stand. Each different launch preset
requires a new EDT process. Unit tests cover stored/runtime separation, named
profiles, bridge/resources/status consistency and provenance persistence.

## The rules in brief (details in tests/e2e/SKILL.md)
1. **One file per tool**, `tests/e2e/tools/test_<tool>.py`, importing ONLY from `harness`. Adding a new MCP tool? add its test file — the **coverage ratchet** fails the suite otherwise.
2. **git-fixture isolation:** `TestConfiguration` is a committed fixture. The orchestrator resets it before every test (`git reset` + `git checkout HEAD --` + `git clean -fd`). Read tools must leave it clean (`assert_no_diff()`).
3. **Verify the effect the RIGHT way per `kind=`:**
   - `read` → `assert_no_diff()` + content.
   - `write` (file-write, write_module_source) → `assert_diff_contains()` (on disk).
   - `write-metadata` (create/add/delete/rename) → **MODEL READ-BACK + on-disk structure.** Call a read tool and assert the change in the model, AND assert it landed on disk via `poll_diff_contains` of the **structural element** (`<name>X</name>`, `<catalogs>Type.X</catalogs>` — not the bare name). Metadata writes now persist to disk via forceExport (~1-2s lag → poll). The orchestrator runs `reset_fixture`+`reset_model` after these.
   - `action` → usually `assert_no_diff()` + status; never run a destructive happy mutation that corrupts the fixture.
   - **env-dependent** (form-render JVM flag, LanguageTool, debug no-session) → env-robust branch: real result OR a clear actionable **sentinel**.
4. **Negative coverage is MANDATORY** (not just happy): invalid path/object, every XOR/conditional/enum, missing-required, boundaries. Assert **error QUALITY** (`assert_error_quality`): names the bad value + actionable next step. A vague/non-actionable tool error → `# AUDIT:` note + a fix-card; **do NOT weaken the test** to a bad error.
5. **Anti-cheat (mutation thinking):** "would this test FAIL if the tool were broken?" If not, it's a CHEAT. No trivial asserts, no `assert_no_diff()`-only, no swallowed exceptions, no tolerant matching. A separate anti-cheat reviewer rules REAL/CHEAT.

## Don't
- Don't add pip dependencies (stdlib only; the client is kept spec-conformant by hand on purpose).
- Don't assert a metadata-write effect with the BARE name in the diff (it false-matches the parent's collection reference) — assert the structural element via `poll_diff_contains`, paired with a model read-back. (Metadata writes DO persist now, with a ~1-2s lag.)
- Don't parallelize the RUN (serial only). Don't call `reset_fixture`/`reset_model` in a test (the orchestrator owns them).

## Protocol conformance (a separate gate)
This suite tests tool **business logic**. The MCP **protocol** itself (initialize handshake, capabilities/version negotiation, `Mcp-Session-Id`, `Accept`/`Content-Type`, `isError`, `ping`, SSE) is a different layer, checked by the official `modelcontextprotocol/conformance` suite — see `tests/conformance/` (`tests/conformance/README.md`). Run: `npx @modelcontextprotocol/conformance@latest server --url http://127.0.0.1:8765/mcp --spec-version 2025-11-25 --expected-failures tests/conformance/baseline.yml` → green when only the pinned (intentional) gaps fail. Don't mix the two gates.

Related: `edt-mcp-tool-conventions` (param/error/output canon), `edt-mcp-bilingual` (ru/en), `edt-mcp-build-test` (Java unit/build), `edt-mcp-testing` (manual per-tool checklists), `edt-mcp-ready-to-deploy` (final pre-merge checklist).
