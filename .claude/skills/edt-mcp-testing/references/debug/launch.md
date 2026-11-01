# launch — how to test

**Purpose.** Start an EDT session: by `launchConfigurationName` (any config, incl. Attach) or by `projectName+applicationId` (runtime-client). `mode` picks the Eclipse launch mode - `debug` (default) or `run`; Attach configs are debug-only. The legacy name `debug_launch` still resolves on a call but is not listed in `tools/list`.

**Precondition.** The scenario from [SETUP](SETUP.md) is in place (code in `OnStart` + breakpoint), and the infobase is available exclusively for the update.

**Representative call (historical scenario; revalidate on the current binary):**
```
launch(launchConfigurationName="TestConfiguration Thin Client", updateBeforeLaunch=true)
```

**Expected result (no update, fast path):**
```json
{"success":true,"mode":"debug","project":"TestConfiguration","attach":false,
 "configurationType":"com._1c.g5.v8.dt.launching.core.RuntimeClient",
 "launchConfiguration":"TestConfiguration Thin Client",
 "message":"Debug session started successfully"}
```
With `updateBeforeLaunch=true` the infobase is updated first (designer agent), then the client starts, and `OnStart` catches the breakpoint.

**Gotchas.**
- **`updateBeforeLaunch=true` hangs if another client holds the infobase** → log `Connecting to designer agent for infobase ...` + timeout. Close only the client processes verified to belong to the owned test stand; do not stop operator sessions. An elevated client remains under the operator's control. Without the update the new code never reaches the infobase.
- **An MCP-call timeout ≠ failure**: the server side may still be finishing the update/start. Re-check `debug_status` and `Get-Process 1cv8c`.
- **Legacy path `projectName+applicationId`** looks up the config by the project+app pair; if no runtime-client config is bound to that appId → `success:false` + `availableConfigurations` ("No launch configuration found"). Calling by `launchConfigurationName` is more reliable.
- **If the application is already running** (incl. RUN mode without a debug target) — the tool returns `alreadyRunning:true` with `mode`, rather than bringing up a second client. To recreate the session — first call `terminate_launch`.
- **Flaky channel:** a bare `Error` has been observed — for the real cause look in `D:\WS\EDT\.metadata\.log` (the full `structuredContent` is there).

For port verification, use the isolated stand and process ownership in the current checkpoint. Follow [the porting guide](../../../../../docs/porting-guide.md), including evidence preservation before recovery; the example paths above describe the historical test bench.
