# get_server_status

Diagnose the EDT MCP server, its feature flags, and the active tool profile. includeProfileTools=true requires includeProfiles=true. Parameters and examples: get_tool_guide('get_server_status').

## Parameters
| Parameter | Required | Type | Description |
| --- | --- | --- | --- |
| includeProfiles | — | boolean | When true, add availableProfiles (enabled profiles only, sorted by id, no allowlist). Default false. |
| includeProfileTools | — | boolean | When true, add sorted allowedTools on each availableProfiles entry. Requires includeProfiles=true. Default false. |

## Guide
A self-diagnosis snapshot of the running MCP server. Reach for it when something behaves oddly and you want the facts instead of guessing - especially a blank form screenshot, a JSON tool that came back as plain text, or to confirm which tool profile this connection actually uses.

## When to use
- `get_form_screenshot` / `get_form_layout_snapshot` returned blank - check the form-render JVM flags here.
- A JSON-response tool gave you plain text - check `plainTextMode`.
- You want to confirm the port, protocol version, plugin/EDT version, or how many tools are enabled vs. registered (e.g. when progressive disclosure is hiding tools).
- Confirm `activeProfile.id` (not `requestedProfileId`) after connecting to `/mcp` or `/mcp/profiles/<id>`.
- Discover other enabled profile URLs with `includeProfiles=true` (add `includeProfileTools=true` only when you need each profile's effective tool names).
- Quick "is the server actually up and reachable" check.

## Parameter details
- `includeProfiles` (boolean, optional, default false): add `availableProfiles` — enabled profiles only, sorted by `id`. Compact rows have no allowlist.
- `includeProfileTools` (boolean, optional, default false): add a sorted `allowedTools` array on each `availableProfiles` entry. Requires `includeProfiles=true`; otherwise the call returns an error.

## What you get
JSON with: `port`, `running`, `protocolVersion`, `pluginVersion`, `edtVersion`, `enabledTools` / `totalTools`, `plainTextMode`, `checksFolderConfigured`, `authEnabled`, and `formRenderFlags`. The latter contains `nativeFormLayoutRender` followed by `nativeFormBufferedLayoutRender`. Each flag always has `atStartup` (`on`, `off`, or `unknown`), sampled when the MCP plugin activates. The bundle is lazily activated, but EDT reads these modes once from system properties at class initialization. Each flag can also have `requested`, the raw system-property string currently set (omitted when unset). Changing a property later does not reconfigure EDT's renderer or its offscreen handler; changing the launch flag requires an EDT restart. On Windows, `get_form_screenshot` requires `-DnativeFormBufferedLayoutRender=true` after `-vmargs` in `1cedt.ini`; it is disabled by default because this flag makes EDT's own form designer show a grey rectangle instead of the form for the whole session (EDT behaviour). Linux and macOS already buffer native rendering. `get_form_layout_snapshot` does not require the buffered-render flag.

Also `activeProfile` (`requestedProfileId`, effective `id`, `displayName`, `description`, `revision`, canonical `endpoint`, `allowedToolCount`, `fallbackApplied`, plus `fallbackReason` / `warning` only on fallback).

When `includeProfiles=true`, also `availableProfiles` (`id`, `displayName`, `description`, `endpoint`, `revision`, `allowedToolCount`; optional `allowedTools`). `allowedToolCount` / `allowedTools` are the effective published surface, including the mandatory `get_server_status`. `enabledTools` and profile counts/lists reflect the saved profile allowlist intersected with the process launch preset (`EDT_MCP_TOOL_PRESET`). A preset can restrict a profile, never grant tools absent from it; `all-tools` adds no restriction.

## Notes & gotchas
- Secrets are never exposed: you get only the `authEnabled` boolean (never the token) and `checksFolderConfigured` (never the folder path).
- `checksFolderConfigured: false` does **not** mean `get_check_description` is unavailable: the descriptions ship with the plugin. The flag reports only whether an operator configured a folder that overrides them.
- Judge how EDT was launched by `atStartup`: it is the launch state. `requested` is the raw system property as currently set; nothing in this plugin writes it.
- For blank or gray screenshots, `nativeFormBufferedLayoutRender.atStartup: on` does not prove EDT could create its offscreen image. On Windows, buffered mode also switches the human designer to that image; an unusable buffer leaves the designer gray. Check the EDT log for `AccessDeniedException` on `dtgraphics-form.bin` (possibly followed by `NoClassDefFoundError: org/eclipse/core/runtime/Plugin`). EDT uses `<java.io.tmpdir>dtgraphics-form.bin` without inserting a separator: change `-Djava.io.tmpdir=C:\edt_tmp` to `-Djava.io.tmpdir=C:\edt_tmp\` and restart EDT. If nothing helps, remove the buffered-render flag and restart EDT.
- On Windows, `nativeFormBufferedLayoutRender.atStartup: off` means EDT was launched without the flag: add `-DnativeFormBufferedLayoutRender=true` after `-vmargs` in `1cedt.ini` and restart EDT. Linux and macOS need no buffered-render flag. `unknown` means the startup mode probe failed.
- `enabledTools` < `totalTools` can reflect profile restrictions, a launch preset, or progressive disclosure. `list_toolsets` / `enable_toolset` reveal only tools the current profile and launch preset already permit; they do not grant access beyond them.
- This tool does not switch the current connection or enlarge its rights. To use another profile, open a new MCP connection to that profile's `endpoint`.
- Disabled profiles are omitted from `availableProfiles`. Unknown or disabled requested ids fall back to `default`; `fallbackApplied` is then true.
- Endpoints are `http://127.0.0.1:<port>/mcp` or `http://127.0.0.1:<port>/mcp/profiles/<id>` — never derived from the HTTP Host header.

## Launch presets and saved profiles

`EDT_MCP_TOOL_PRESET` is resolved once for the process, ignoring case and surrounding whitespace. Supported preset IDs restrict every endpoint through the same intersection. Missing, blank, `custom`, or unknown values leave the saved profile surface in effect; `custom` logs one informational message, and an unknown value logs one warning. The profile editor still edits the saved selection and its preset provenance. Choosing Custom records that choice; upgrading or launching with a preset does not rewrite stored allowlists.

---
*Generated from the live MCP server (`get_tool_guide`) by `docs/generate_tool_docs.py`. Do not edit this file. Edit the tool's description/schema in its Java source and its guide body in `mcp/bundles/fm.giper.edt.mcp.server/guides/<tool>.md`.*
