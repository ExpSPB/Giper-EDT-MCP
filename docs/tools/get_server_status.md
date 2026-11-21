# get_server_status

Diagnose the EDT MCP server, including its configuration, active tool profile, running calls, and background jobs. Parameters and examples: get_tool_guide('get_server_status').

## Parameters
| Parameter | Required | Type | Description |
| --- | --- | --- | --- |
| includeProfiles | — | boolean | List enabled profiles with their HTTP endpoints. Default: false. |
| includeProfileTools | — | boolean | Include the effective tool names per listed profile. Requires includeProfiles. Default: false. |

## Guide
Snapshot of the running server, transport listeners, preferences, form-render flags, and the tool calls and background jobs running right now.

## When to use
- A call seems stuck: see what is running and for how long (`activeCalls`, `runningJobs`).
- `get_form_screenshot` / `get_form_layout_snapshot` returned blank - check the form-render modes here.
- Unexpected text delivery: `plainTextMode` is the saved default; `/mcp/plaintext` or stdio `-resultMode` overrides it per client.
- You want to confirm the port, protocol version, plugin/EDT version, or how many tools are enabled vs. registered (e.g. when progressive disclosure is hiding tools).
- You want to verify which profile applies to the current HTTP session or discover other enabled profile endpoints.
- Quick "is the server actually up and reachable" check.

## Parameter details
- `includeProfiles` adds `availableProfiles` with IDs, display names, descriptions, explicit HTTP endpoints, revisions and effective tool counts. It defaults to false.
- `includeProfileTools` adds the sorted effective `allowedTools` to each entry. It defaults to false and requires `includeProfiles: true`.

## What you get

`transports.http` and `transports.ipc` report whether the HTTP and local IPC listeners are up. `running` is true when either listener is up. `port` retains the configured or last bound HTTP port; it indicates an active listener only when `transports.http` is true. An EDT started by stdio normally reports HTTP false and IPC true. No socket path or handshake secret is returned.
JSON with: `port`, `running`, `protocolVersion`, `pluginVersion`, `edtVersion`, `enabledTools` / `totalTools`, `plainTextMode`, `checksFolderConfigured`, `authEnabled`, and `formRenderFlags`. The latter contains `nativeFormLayoutRender` followed by `nativeFormBufferedLayoutRender`. Each flag always has `atStartup` (`on`, `off`, or `unknown`), sampled when the MCP plugin activates. The bundle is lazily activated, but EDT reads these modes once from system properties at class initialization. Each flag can also have `requested`, the raw system-property string currently set (omitted when unset). Changing a property later does not reconfigure EDT's renderer or its offscreen handler; changing the launch flag requires an EDT restart. On Windows, `get_form_screenshot` requires `-DnativeFormBufferedLayoutRender=true` after `-vmargs` in `1cedt.ini`; it is disabled by default because this flag makes EDT's own form designer show a grey rectangle instead of the form for the whole session (EDT behaviour). Linux and macOS already buffer native rendering. `get_form_layout_snapshot` does not require the buffered-render flag.

`activeProfile` describes the effective profile with `id`, `requestedProfileId`, `displayName`, `description`, `revision`, `endpoint` and `allowedToolCount`. On fallback, `fallbackApplied` is true and the response also includes `fallbackReason` and `warning`. The endpoint is a loopback URL built from the configured or bound HTTP port. Discovery lists enabled profiles at `/mcp/profiles/<id>`, including `/mcp/profiles/default`.

On an explicit profile endpoint, `enabledTools` counts the tools permitted by the request's profile, launch preset and host. On `/mcp` and stdio it counts enabled registrations, while `activeProfile.allowedToolCount` reports the number advertised by the compatibility catalog after host filtering and progressive disclosure.

`activeCalls` lists the tool calls whose EDT work is running now, oldest first, on every transport (HTTP, stdio, the Workmate bridge): `tool`, `projectName` (only when the call passed one; no other argument is reported), `startedAtMs` (epoch milliseconds) and `elapsedMs`. Your own `get_server_status` call is not listed. An entry stays until the EDT work ends, not until the response is sent: `responseSent: true` marks a call whose client already has an answer (a user interrupt) while its operation keeps running. A listed call is RUNNING, not necessarily healthy - a wedged call looks the same as a long one; judge by `elapsedMs` against the tool's usual time. `activeCallsUntracked` appears only when more calls were running than the registry holds (256). `runningJobs` lists the background jobs whose status is running, oldest first: `jobId`, `tool`, `startedAtMs`, `elapsedMs` - poll one with `get_job_status`. A tool that answered with a `jobId` is in `runningJobs`, not in `activeCalls`. Both arrays are always present; an empty one means nothing of that kind is running.

`hostKind` is `WINDOWED` or `HEADLESS`, selected by the host application. A headless host has no workbench; its tool catalog excludes mandatory UI tools. Removing `-headless` from the client configuration changes the next cold start, not an already running host.

## Notes & gotchas
- Secrets are never exposed: you get only the `authEnabled` boolean (never the token) and `checksFolderConfigured` (never the folder path).
- `checksFolderConfigured: false` does **not** mean `get_check_description` is unavailable: the descriptions ship with the plugin. The flag reports only whether an operator configured a folder that overrides them.
- Judge how EDT was launched by `atStartup`: it is the launch state. `requested` is the raw system property as currently set; nothing in this plugin writes it.
- For blank or gray screenshots, `nativeFormBufferedLayoutRender.atStartup: on` does not prove EDT could create its offscreen image. On Windows, buffered mode also switches the human designer to that image; an unusable buffer leaves the designer gray. Check the EDT log for `AccessDeniedException` on `dtgraphics-form.bin` (possibly followed by `NoClassDefFoundError: org/eclipse/core/runtime/Plugin`). EDT uses `<java.io.tmpdir>dtgraphics-form.bin` without inserting a separator: change `-Djava.io.tmpdir=C:\edt_tmp` to `-Djava.io.tmpdir=C:\edt_tmp\` and restart EDT. If nothing helps, remove the buffered-render flag and restart EDT.
- On Windows, `nativeFormBufferedLayoutRender.atStartup: off` means EDT was launched without the flag: add `-DnativeFormBufferedLayoutRender=true` after `-vmargs` in `1cedt.ini` and restart EDT. Linux and macOS need no buffered-render flag. `unknown` means the startup mode probe failed.
- `enabledTools` < `totalTools` is normal when progressive disclosure is on - use `list_toolsets` / `enable_toolset` to reveal more.

---
*Generated from the live MCP server (`get_tool_guide`) by `docs/generate_tool_docs.py`. Do not edit this file. Edit the tool's description/schema in its Java source and its guide body in `mcp/bundles/fm.giper.edt.mcp.server/guides/<tool>.md`.*
