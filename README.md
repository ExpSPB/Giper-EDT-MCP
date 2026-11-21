[![GitHub all releases](https://img.shields.io/github/downloads/ExpSPB/Giper-EDT-MCP/total)](https://github.com/ExpSPB/Giper-EDT-MCP/releases)
[![Build & Unit Tests](https://github.com/ExpSPB/Giper-EDT-MCP/actions/workflows/build.yml/badge.svg)](https://github.com/ExpSPB/Giper-EDT-MCP/actions/workflows/build.yml)
[![Proxy](https://github.com/ExpSPB/Giper-EDT-MCP/actions/workflows/proxy.yml/badge.svg)](https://github.com/ExpSPB/Giper-EDT-MCP/actions/workflows/proxy.yml)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=ExpSPB_Giper-EDT-MCP&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=ExpSPB_Giper-EDT-MCP)
[![Bugs](https://sonarcloud.io/api/project_badges/measure?project=ExpSPB_Giper-EDT-MCP&metric=bugs)](https://sonarcloud.io/summary/new_code?id=ExpSPB_Giper-EDT-MCP)
[![Code Smells](https://sonarcloud.io/api/project_badges/measure?project=ExpSPB_Giper-EDT-MCP&metric=code_smells)](https://sonarcloud.io/summary/new_code?id=ExpSPB_Giper-EDT-MCP)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=ExpSPB_Giper-EDT-MCP&metric=coverage)](https://sonarcloud.io/summary/new_code?id=ExpSPB_Giper-EDT-MCP)
[![E2E 2026.2](https://github.com/ExpSPB/Giper-EDT-MCP/actions/workflows/e2e-2026.2.yml/badge.svg)](https://github.com/ExpSPB/Giper-EDT-MCP/actions/workflows/e2e-2026.2.yml)
[![Conformance 2026.2](https://github.com/ExpSPB/Giper-EDT-MCP/actions/workflows/conformance-2026.2.yml/badge.svg)](https://github.com/ExpSPB/Giper-EDT-MCP/actions/workflows/conformance-2026.2.yml)

# Giper-EDT-MCP

MCP (Model Context Protocol) server plugin for 1C:EDT. AI assistants (Claude, GitHub Copilot, Cursor, and others) talk to the EDT workspace through this plugin.

**Giper-EDT-MCP** is a fork of [EDT-MCP](https://github.com/DitriXNew/EDT-MCP) by DitriX, maintained by ExpSPB. Version 1.0.16 was verified on **1C:EDT 2026.2.2.21**. Releases for EDT 2025.2.5 are documented on the [archived branch](https://github.com/ExpSPB/Giper-EDT-MCP/blob/giper-2025.2/README.md).

<details open>
<summary><strong>1.0.17</strong> (unreleased)</summary>

- Git can initialize and clone repositories, and metadata writes wait for EDT to reload the model after a branch switch.
- Metadata tools support cloning across projects, renaming more member types, and editing DCS layouts. BSL edits can replace whole words and copy or remove methods.
- Profiling and technological log tools have more ways to capture and analyze results. `get_server_status` shows active calls and background jobs.
- The server validates tool arguments against their schemas. The confirmation dialog wait is configurable and defaults to 45 seconds.

</details>

<details>
<summary><strong>1.0.16</strong></summary>

The proxy now returns the reason why a profile denied `list_projects` in an MCP response with `isError=true`. Verified on EDT 2026.2.2.21 with the published ZIP and proxy JAR. The `ui-test` profile returned the denial reason, and `default` returned `TestConfiguration` in `ready`. Both profiles retained their settings when updated from 1.0.15 through p2. [Release v1.0.16](https://github.com/ExpSPB/Giper-EDT-MCP/releases/tag/v1.0.16)

</details>

<details>
<summary><strong>1.0.15</strong></summary>

Ported the Giper plugin identity, tool profiles, HTTP session policy, profile import and export, and proxy routing to EDT 2026.2. Tagged release builds check Giper-EDT-MCP GitHub Releases for updates. Local builds disable that check. [Release v1.0.15](https://github.com/ExpSPB/Giper-EDT-MCP/releases/tag/v1.0.15)

</details>

## What this fork adds

### Giper plugin identity and updates

Giper-EDT-MCP has its own Eclipse bundle and p2 feature IDs. Each release ships one p2 ZIP and one proxy JAR. [Releases](https://github.com/ExpSPB/Giper-EDT-MCP/releases) · [p2 update site](https://expspb.github.io/Giper-EDT-MCP/)

### Named tool profiles

Separate profiles let multiple agents share one EDT workbench with different tool allowlists.

| URL | Tool surface |
|-----|--------------|
| `http://127.0.0.1:8765/mcp` | Legacy endpoint for existing client configurations |
| `http://127.0.0.1:8765/mcp/profiles/default` | Saved default profile |
| `http://127.0.0.1:8765/mcp/profiles/<id>` | Named profile |

Create, edit, enable or disable profiles in **Window → Preferences → MCP Server → Tools**. **Export profile…** and **Import profile…** exchange the selected profile as JSON. Import changes that profile's draft and keeps its ID. **Apply** or **OK** saves the draft.

The active allowlist controls both `tools/list` and `tools/call`. An unknown or disabled profile ID falls back to `default`. Use `get_server_status` to check the effective profile and whether fallback was applied. The proxy uses the same endpoint paths and routes calls to backends with a compatible profile. [Profile settings and live updates](EDT-MCP.md#named-tool-profiles) · [Proxy profile transport](proxy/README.md#profile-endpoints-sessions-and-events)

## Installation

1. In EDT, open **Help → Install New Software...**.
2. Add the [Giper-EDT-MCP update site](https://expspb.github.io/Giper-EDT-MCP/).
3. Select **EDT MCP Server (for Giper.fm)** and restart EDT.

When switching from upstream EDT-MCP, uninstall its feature first. The update site currently serves `1.0.16`. [Installation options](EDT-MCP.md#installation)

### Upgrade from 1.0.15 to 1.0.16 on Windows

This procedure was verified on EDT 2026.2.2.21 with Giper-EDT-MCP 1.0.15 already installed.

1. Close the EDT instance you want to upgrade. Download
   [`MCP-EDT.v1.0.16.zip`](https://github.com/ExpSPB/Giper-EDT-MCP/releases/download/v1.0.16/MCP-EDT.v1.0.16.zip)
   and extract it to a directory. Use the extracted p2 repository root, which contains `plugins/`
   and `features/`.
2. In Command Prompt, set `EDT_DIR` to the installation that has 1.0.15 and `P2_REPO` to the
   file URI of the extracted repository. Run both IU changes in one p2 Director invocation.
   Installing 1.0.16 alone can conflict with the feature JAR required by 1.0.15.

   ```cmd
   set "EDT_DIR=C:\path\to\1c-edt-2026.2.2+21-x86_64"
   set "P2_REPO=file:///D:/downloads/edt-mcp-1.0.16"
   set "MCP_IU=fm.giper.edt.mcp.server.feature.feature.group"

   cd /d "%EDT_DIR%"
   start "" /wait "%EDT_DIR%\1cedt.exe" -nosplash -consoleLog ^
       -application org.eclipse.equinox.p2.director ^
       -destination "%EDT_DIR%" -profile DefaultProfile ^
       -repository "%P2_REPO%" ^
       -uninstallIU "%MCP_IU%/1.0.15" -installIU "%MCP_IU%/1.0.16" ^
       -profileProperties org.eclipse.update.install.features=true ^
       -p2.os win32 -p2.ws win32 -p2.arch x86_64
   ```

3. After p2 reports success, reopen the **same workspace**. For a scripted start,
   use `1cedt.exe -data "<existing-workspace>"` and let EDT select its own configuration
   directory. The tool profiles are stored in the workspace. Verify them in
   **Window → Preferences → MCP Server → Tools**, and check `pluginVersion` in `get_server_status`.

On the test stand, both profiles retained their settings. Through the published 1.0.16 proxy JAR,
`ui-test` returned the reason for denying `list_projects`, and `default` returned
`TestConfiguration` with state `ready`.

**Windows `-configuration` pitfall.** In the test that failed, the explicit `-configuration`
argument caused Equinox to see the installation's configuration directory as two file URLs
with different drive-letter case (`file:/D:/...` and `file:/d:/...`). In this cascaded configuration
mode, p2 recorded a surrogate profile with 38 installable units. The full profile had 1,554.
The surrogate lacked `com._1c.g5.v8.dt.platform_v8.3.12`, so Director could not resolve the EDT
License Feature from the plugin repository alone. Starting EDT without `-configuration` kept the
full p2 profile on the test stand. If the installed copy already reports the missing platform
dependency, start with a complete EDT installation, install 1.0.15 there, and then run the upgrade
above with the existing workspace.

**p2 exit code 13 in the Codex sandbox.** Java's `Files.isWritable` can return `false` for
the target EDT directory even when the process can create a temporary file there. p2 Director
then reports the directory as read-only, and the EDT launcher shows
`Java was started but returned exit code=13`. On a separate copy of the same EDT build,
installing 1.0.15 from its release ZIP succeeded from a regular `cmd.exe` outside the sandbox
with exit code `0` and without Windows administrator elevation.

## Tools

The generated [full tool reference](docs/tools/README.md) lists every tool, parameter and guide, grouped by toolset.
See [tool management](docs/tool-management.md) for profiles, presets and progressive disclosure.

| Toolset | Purpose | Reference |
|---|---|---|
| `core` | Project/module navigation, source reads and tool discovery | [Core](docs/tools/README.md#core) |
| `metadata` | Discover, create, edit and refactor metadata and DCS. Check extension properties with `get_metadata_details(controlledMismatches: true)`. | [Metadata](docs/tools/README.md#metadata) |
| `code` | Read/write methods, navigate symbols and inspect calls | [Code](docs/tools/README.md#code) |
| `debug` | Breakpoints, stepping, variables and expressions | [Debug](docs/tools/README.md#debug) |
| `testing` | Run and debug YAXUnit tests | [Testing](docs/tools/README.md#testing) |
| `profiling` | Capture, compare and exchange performance profiles; capture and analyze technological logs | [Profiling](docs/tools/README.md#profiling) |
| `forms` | Inspect form layouts, screenshots and templates | [Forms](docs/tools/README.md#forms) |
| `tags` | Read and filter metadata tags | [Tags](docs/tools/README.md#tags) |
| `translation` | Generate and synchronize translation strings | [Translation](docs/tools/README.md#translation) |
| `project` | Diagnostics, applications, infobases and database updates | [Project](docs/tools/README.md#project) |
| `comparison` | Three-way configuration comparison and merge rules | [Comparison](docs/tools/README.md#comparison) |
| `git` | Git commands and branch/infobase bindings | [Git](docs/tools/README.md#git) |

## Full product documentation

The [EDT-MCP user guide](EDT-MCP.md) covers installation, client configuration, tools and building from source.

- [Connecting AI assistants](EDT-MCP.md#connecting-ai-assistants)
- [Tool Management](EDT-MCP.md#tool-management)
- [Tool usage overviews](docs/tool-overviews.md)
- [Building from source](docs/building.md)
- [Proxy configuration](proxy/README.md)

## License

- Copyright (C) 2025–2026 DitriX (https://github.com/DitriXNew)
- Copyright (C) 2026 Diversus23 (https://github.com/Diversus23)
- Modified by ExpSPB in 2026 (https://github.com/ExpSPB)

GNU Affero General Public License v3.0 or later. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
