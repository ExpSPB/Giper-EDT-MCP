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
JSON with: `port`, `running`, `protocolVersion`, `pluginVersion`, `edtVersion`, `enabledTools` / `totalTools`, `plainTextMode`, `checksFolderConfigured`, `authEnabled`, `formRenderFlags` (the two render flags `nativeFormBufferedLayoutRender` and `nativeFormLayoutRender` with their boolean states), and `activeProfile` (`requestedProfileId`, effective `id`, `displayName`, `description`, `revision`, canonical `endpoint`, `allowedToolCount`, `fallbackApplied`, plus `fallbackReason` / `warning` only on fallback).

When `includeProfiles=true`, also `availableProfiles` (`id`, `displayName`, `description`, `endpoint`, `revision`, `allowedToolCount`; optional `allowedTools`). `allowedToolCount` / `allowedTools` are the effective published surface, including the mandatory `get_server_status`.

## Notes & gotchas
- Secrets are never exposed: you get only the `authEnabled` boolean (never the token) and `checksFolderConfigured` (never the folder path).
- If a form screenshot is blank, look for `nativeFormBufferedLayoutRender=true` - without it the layout renderer has no offscreen buffer and screenshots stay empty (fixed by adding the flag to the EDT `-vmargs`, not by changing code).
- `enabledTools` < `totalTools` is normal when progressive disclosure is on - use `list_toolsets` / `enable_toolset` to reveal more.
- This tool does not switch the current connection or enlarge its rights. To use another profile, open a new MCP connection to that profile's `endpoint`.
- Disabled profiles are omitted from `availableProfiles`. Unknown or disabled requested ids fall back to `default`; `fallbackApplied` is then true.
- Endpoints are `http://127.0.0.1:<port>/mcp` or `http://127.0.0.1:<port>/mcp/profiles/<id>` — never derived from the HTTP Host header.
