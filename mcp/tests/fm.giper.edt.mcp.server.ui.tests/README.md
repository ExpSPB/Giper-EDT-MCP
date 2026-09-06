# EDT MCP Server UI Tests (SWTBot)

Optional SWTBot tests for the MCP Server preference page. This module is **not** listed in `mcp/tests/pom.xml` and is **not** part of the default `mvn -f mcp/pom.xml verify` / CI reactor.

## Prerequisites

Resolve the target platform once so SWTBot IUs from `mcp/targets/default/default.target` are available (same flow as the main plugin build).

## Run locally

From the repository root, after a target resolve:

```bash
mvn -f mcp/tests/fm.giper.edt.mcp.server.ui.tests/pom.xml verify
```

These tests launch an Eclipse workbench with the UI harness. They open Preferences and inspect the Tools tab only — native file dialogs are not automated.
