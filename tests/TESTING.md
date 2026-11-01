# Testing EDT MCP Server

## Architecture

The testing infrastructure consists of a build step plus two test layers:

### 0. Build

**Requirements:** JDK 17 is mandatory. The project targets `JavaSE-17`, and the Tycho 4.0.5 / Eclipse Compiler (ECJ 3.36) build fails on newer JDKs (e.g. JDK 25 fails with `Cannot invoke "java.nio.file.FileSystem.getPath(...)" because "this.fs" is null`). Make sure `JAVA_HOME` points to a JDK 17 before building.

The plugin must be built with the official build script `source/compile.sh`. It runs the Tycho build (`mvn clean verify`, which also executes the unit tests) and packages the p2 update site into a versioned zip:
```bash
bash source/compile.sh --java-home /usr/lib/jvm/java-17-openjdk-amd64   # adjust JDK 17 path
```
Outputs:
- update-site zip: `source/dist/MCP-EDT.v<version>.zip`
- p2 repository: `mcp/repositories/fm.giper.edt.mcp.server.repository/target/repository`

To run only the unit tests (without packaging the archive) you can invoke Maven directly:
```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64   # adjust to your JDK 17 path
cd mcp
mvn clean verify
```

### 1. Unit Tests (Tycho Surefire)

Located in `mcp/tests/fm.giper.edt.mcp.server.tests/`

These are JUnit 4 tests that run inside the Eclipse/Tycho build without requiring a running EDT instance. They cover:

- **Protocol layer**: `JsonSchemaBuilder`, `JsonUtils`, `GsonProvider`, `McpConstants`
- **JSON-RPC DTOs**: `JsonRpcRequest`, `JsonRpcResponse`, `JsonRpcError`
- **Tool results**: `ToolResult`, `ToolCallResult`, `ToolsListResult`

Unit tests run automatically during the build (see **0. Build**). Results are in:
```
mcp/tests/fm.giper.edt.mcp.server.tests/target/surefire-reports/
```

### 2. E2E Tests (Python HTTP client)

Located in `tests/e2e/` — a modular black-box suite: `harness.py` (HTTP/SSE client +
git-fixture isolation + assertions), `run_all.py` (serial runner, discovery,
`--junit-xml`, `--filter`), and one `tools/test_<tool>.py` per tool. A coverage ratchet
(`tools/test_coverage_ratchet.py`) fails the suite if `tools/list` advertises a tool with
no test.

These tests send real HTTP requests to a running MCP server and validate every tool. They require:
- A running EDT instance with the MCP plugin installed
- The `TestConfiguration` project loaded in EDT

**Prerequisite — install the plugin in EDT manually.** Build it first (see
**0. Build**). There is currently no automated/headless installation, so the
plugin must be installed by hand before running E2E:

1. In EDT: **Help → Install New Software… → Add… → Archive…**, select
   `source/dist/MCP-EDT.v<version>.zip` (or **Local…** → the `repository` folder),
   install `fm.giper.edt.mcp.server.feature`, restart EDT.
2. Import the `TestConfiguration` project into the EDT workspace and wait until
   derived data is built.
3. Make sure the MCP server is listening on port 8765 (the plugin starts it
   automatically inside the EDT workbench).

> Why manual: the system EDT install lives under a read-only path (e.g.
> `/opt/...`), so a headless `p2 director` install needs elevated rights, and the
> MCP server only starts inside the full EDT workbench (it is skipped in the
> headless `1cedtcli` runtime). Until a dedicated EDT runner/Docker image exists
> (see "Future: Full CI Pipeline"), install the plugin through the EDT UI.

**Running locally:**
```bash
# Make sure EDT is running with MCP server on port 8765
python tests/e2e/run_all.py

# Or with custom settings:
python tests/e2e/run_all.py --host localhost --port 8765 --project TestConfiguration

# Run a subset (substring match on tool/test name):
python tests/e2e/run_all.py --filter modify_metadata

# Generate JUnit XML report:
python tests/e2e/run_all.py --junit-xml results.xml
```

Each mutating test isolates itself with a **git fixture**: it snapshots the
`TestConfiguration/` tree, runs, then restores it (`git checkout` + `git clean`), so a
failed test never leaks artifacts into the next one. The runner reports `fixture clean:
True/False` at the end; a dirty fixture fails the run even if every functional test passed.

**E2E tests cover all tools** — one `tools/test_<tool>.py` per tool (happy path + negative
+ error-quality + anti-cheat), enforced by the coverage ratchet. The unified metadata CRUD
tools are covered by `test_create_metadata.py`, `test_modify_metadata.py`,
`test_delete_metadata.py`, and `test_get_metadata_details.py` (basic / full / `assignable`).

### Plugin transport session wire checks

`tools/test_transport_session.py` uses raw HTTP to retain the actual status codes.
Run it against the owned plugin port, for example:

```bash
python tests/e2e/run_all.py --port 9876 --filter _transport_session
```

The test bodies read protocol state and create/delete only their own sessions;
normal runner fixture setup and cleanup still apply. The reserved explicit path
`/mcp/profiles/default` needs no additional named-profile fixture. Use a freshly
started stand without hot Apply of the default profile: that intentional policy
change revokes legacy sessionless access. `/mcp` otherwise remains compatible
with sessionless calls, while an explicit path requires the session header (400).
Unknown, deleted or cross-path session ids receive 404; cross-path DELETE must
leave the original session usable. The plugin's repeated/unknown DELETE is 404,
while the proxy has a separate idempotent-DELETE contract. Do not point this
plugin-specific module at the proxy. Origin refusal is checked before session
admission and must grant no CORS access.

### Launch-preset wire checks

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

## Test Configuration

The `TestConfiguration/` directory contains a minimal 1C:Enterprise configuration for testing:

- **Catalog.Catalog** — with ItemForm
- **CommonModule.OK** — empty, valid module
- **CommonModule.Error** — module with intentional error
- **CommonForm.Form** — common form
- **CommonAttribute.CommonAttribute** — common attribute
- **Subsystem.Subsystem** — subsystem
- **SessionParameter.SessionParameter** — session parameter

## GitHub Actions

### build.yml (automatic)
Runs unit tests on every push/PR to master. Test results are published to PR checks.

### e2e-tests.yml (manual)
Triggered via `workflow_dispatch`. Requires a running MCP server (self-hosted runner or tunnel).

### Future: Full CI Pipeline
For fully automated E2E on GitHub Actions, the plan is:
1. Build the plugin via `source/compile.sh` (Tycho)
2. Install EDT headless (if a headless runner/Docker image becomes available)
3. Import TestConfiguration
4. Start MCP server
5. Run E2E tests
6. Publish results

## Project Structure

```
EDT-MCP/
├── mcp/
│   ├── bundles/
│   │   └── fm.giper.edt.mcp.server/        # Main plugin
│   ├── tests/
│   │   ├── pom.xml                             # Tests parent
│   │   └── fm.giper.edt.mcp.server.tests/   # Unit test fragment
│   │       ├── META-INF/MANIFEST.MF
│   │       ├── pom.xml
│   │       └── src/                            # JUnit tests
│   └── pom.xml                                 # Root (includes tests module)
├── tests/
│   └── e2e/
│       ├── harness.py                          # HTTP/SSE client + git-fixture + asserts
│       ├── run_all.py                          # Serial E2E runner (--filter, --junit-xml)
│       └── tools/test_<tool>.py                # One black-box test file per tool
├── TestConfiguration/                          # Test 1C configuration
│   └── src/
└── .github/workflows/
    ├── build.yml                               # CI with unit tests
    └── e2e-tests.yml                           # E2E test workflow
```
