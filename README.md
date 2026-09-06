[![GitHub all releases](https://img.shields.io/github/downloads/ExpSPB/Giper-EDT-MCP/total)](https://github.com/ExpSPB/Giper-EDT-MCP/releases)

[![Build & Unit Tests](https://github.com/ExpSPB/Giper-EDT-MCP/actions/workflows/build.yml/badge.svg)](https://github.com/ExpSPB/Giper-EDT-MCP/actions/workflows/build.yml)
[![Proxy](https://github.com/ExpSPB/Giper-EDT-MCP/actions/workflows/proxy.yml/badge.svg)](https://github.com/ExpSPB/Giper-EDT-MCP/actions/workflows/proxy.yml)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=ExpSPB_Giper-EDT-MCP&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=ExpSPB_Giper-EDT-MCP)
[![Bugs](https://sonarcloud.io/api/project_badges/measure?project=ExpSPB_Giper-EDT-MCP&metric=bugs)](https://sonarcloud.io/summary/new_code?id=ExpSPB_Giper-EDT-MCP)
[![Code Smells](https://sonarcloud.io/api/project_badges/measure?project=ExpSPB_Giper-EDT-MCP&metric=code_smells)](https://sonarcloud.io/summary/new_code?id=ExpSPB_Giper-EDT-MCP)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=ExpSPB_Giper-EDT-MCP&metric=coverage)](https://sonarcloud.io/summary/new_code?id=ExpSPB_Giper-EDT-MCP)

[![E2E 2025.2](https://github.com/ExpSPB/Giper-EDT-MCP/actions/workflows/e2e-2025.2.yml/badge.svg)](https://github.com/ExpSPB/Giper-EDT-MCP/actions/workflows/e2e-2025.2.yml)

[![Conformance 2025.2](https://github.com/ExpSPB/Giper-EDT-MCP/actions/workflows/conformance-2025.2.yml/badge.svg)](https://github.com/ExpSPB/Giper-EDT-MCP/actions/workflows/conformance-2025.2.yml)

# Giper-EDT-MCP

MCP (Model Context Protocol) server plugin for 1C:EDT. AI assistants (Claude, GitHub Copilot, Cursor, and others) talk to the EDT workspace through this plugin.

**Giper-EDT-MCP** is a fork of [EDT-MCP](https://github.com/DitriXNew/EDT-MCP) by DitriX, maintained by ExpSPB. AGPL-3.0; full upstream attribution is in [LICENSE](LICENSE) and [NOTICE](NOTICE).

<details>
<summary><strong>1.0.8</strong></summary>

Plugin OSGi/Tycho version (`1.0.8-SNAPSHOT`). p2 Check for Updates compares `major.minor.micro` only.

**Single-profile file exchange** on **Window → Preferences → MCP Server → Tools**: **Export profile…** saves the selected profile; **Import profile…** overlays a file onto the selected profile. Import updates the **draft** only — **Apply** or **OK** publishes; **Cancel** discards. The profile **id in the form stays unchanged** (your `/mcp/profiles/{id}` URL is unchanged).

</details>

<details>
<summary><strong>1.0.7</strong></summary>

Plugin OSGi/Tycho version (`1.0.7-SNAPSHOT`). `source/compile.sh` reads this label when `--version` is omitted. p2 Check for Updates compares `major.minor.micro` only — bump the micro before a rebuild that must install as an update.

</details>

## Full product documentation (from `master`)

The original EDT-MCP user guide — install, tools, tags, groups, Workmate, proxy, build, per-tool index — is **[EDT-MCP.md](EDT-MCP.md)** (`README.md` from `master`, renamed with `git mv`).

Direct jumps:

- [Installation](EDT-MCP.md#installation)
- [Connecting AI assistants](EDT-MCP.md#connecting-ai-assistants)
- [Tool Management](EDT-MCP.md#tool-management)
- [Available tools](EDT-MCP.md#available-tools)
- [Building from source](EDT-MCP.md#building-from-source)
- [Security](EDT-MCP.md#security--trust-model)

> [!TIP]
> **Contributing?** Read [CLAUDE.md](CLAUDE.md) first. How-to lives under `.claude/skills/`.
> For 1C business projects see the [agent skills pack](agent/README.md) and the [rules pack](rules/README.md).

> [!IMPORTANT]
> Supports 1C:EDT **2025.2.5** (Ruby). Compiled against the 2025.2 p2 (Java 17 / Eclipse 2023-12). E2E and conformance run on the same line.

## What this fork adds: tool-set profiles

Upstream EDT-MCP has **one** enabled-tool list for the whole workbench. This fork publishes **several named allowlists (profiles)** from one EDT process, each on its own URL, so several agents can share one workspace without sharing one tool surface.

| URL | Surface |
|-----|---------|
| `http://127.0.0.1:8765/mcp` | **default** profile (legacy path — keep it in existing client configs) |
| `http://127.0.0.1:8765/mcp/profiles/<id>` | Named enabled profile (`review`, `writer`, …) |
| `http://127.0.0.1:8765/mcp/` (trailing slash) or any other `/mcp/...` path | HTTP **400** — not an alias, no fallback |

Create, duplicate, rename, and enable/disable profiles on **Window → Preferences → MCP Server → Tools**. Copy the shown endpoint into the agent's MCP config. Two agents against one EDT: point one at `/mcp` and the other at `/mcp/profiles/review`.

**File exchange (one profile).** On the same Tools tab, **Export profile…** / **Import profile…** move the **currently selected** profile to or from a JSON file — not the whole workspace profile document. Import replaces the selected profile's draft allowlist; other profiles are untouched; the profile id and endpoint URL stay the same until you change them manually. Details: [Profile file exchange](EDT-MCP.md#profile-file-exchange-108).

**Apply does not restart the MCP server.** It closes only the sessions and SSE streams of the **affected URL**. Editing `review` does not drop `/mcp`. Changing **default** also drops fallback clients and sessionless `/mcp`; the next request must `initialize` again. Live `notifications/tools/list_changed` without reconnect is not the Apply contract.

**Fallback.** An unknown or disabled profile ID still accepts the connection, but the effective surface is **default**. `initialize` and `get_server_status` (`includeProfiles: true`) report it (`activeProfile.fallbackApplied`, `fallbackReason`). Trust `activeProfile.id`, not the path segment. Each fallback at initialize is written to the EDT log. A malformed path is HTTP 400, not fallback.

**Discovery.** `get_server_status` with `includeProfiles: true` lists enabled profiles and canonical endpoints. Add `includeProfileTools: true` only when you need each allowlist (refused unless `includeProfiles` is also true). This does not switch the caller's profile or enlarge rights. `get_server_status` is always callable.

**Allowlists are fail-closed.** A new built-in tool is **not** added to existing profiles until you allow it (or re-apply a preset). A tool missing from the active profile is omitted from `tools/list` and refused on `tools/call` (and `guide://` resources). The profile ID is a routing key, **not a secret** — anyone who can hit `/mcp` still gets the default allowlist.

**Presets** (All / Analysis Only / Code Review / Development) seed a **new** profile; they do not create an extra URL by themselves. Restore Defaults resets only the selected profile's allowlist — not global destructive-consent and not other profiles.

**Progressive disclosure** (`list_toolsets` / `enable_toolset`) remains a compatibility mode of the legacy `/mcp` endpoint only. Explicit `/mcp/profiles/<id>` endpoints have a fixed surface; `enable_toolset` does not change them.

**Proxy.** [`edt-mcp-proxy`](proxy/README.md) forwards the same `/mcp` and `/mcp/profiles/<id>` paths. Routing is **fail-closed**: malformed backend `get_server_status` / `tools/list`, mixed profile fingerprints, or an empty compatible group expose only `router_status` and `router_refresh` and never route or fan out backend tool calls.

**In-process bridge.** `IEdtMcpBridge` overloads take `profileId`; no-arg methods use `default`. Workmate/JShell cannot bypass the HTTP allowlist.

Example — two agents, one EDT (do not treat the profile id as a credential):

```json
{
  "mcpServers": {
    "edt-default": { "url": "http://127.0.0.1:8765/mcp" },
    "edt-review": { "url": "http://127.0.0.1:8765/mcp/profiles/review" }
  }
}
```

Original (single-list) tool groups, presets, and parameter defaults: [Tool Management](EDT-MCP.md#tool-management).

## Install (this fork)

1. In EDT: **Help → Install New Software...**
2. Add update site: `https://expspb.github.io/Giper-EDT-MCP/`
3. Select **EDT MCP Server Feature**
4. Restart EDT

Command-line p2 install, the form-screenshot JVM flag, preferences, and screenshots: [EDT-MCP.md — Installation](EDT-MCP.md#installation).

Releases also ship `edt-mcp-proxy-<version>.jar`. Local build: [Building from source](EDT-MCP.md#building-from-source).

## License

- Copyright (C) 2025–2026 DitriX (https://github.com/DitriXNew)
- Copyright (C) 2026 Diversus23 (https://github.com/Diversus23)
- Modified by ExpSPB in 2026 (https://github.com/ExpSPB)

GNU Affero General Public License v3.0 (`AGPL-3.0-or-later`). Full text: [LICENSE](LICENSE); notices: [NOTICE](NOTICE).
