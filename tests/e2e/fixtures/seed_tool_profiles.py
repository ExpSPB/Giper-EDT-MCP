#!/usr/bin/env python3
"""Write the e2e tool-profile document into an EDT instance preference store.

Usage:
  python3 tests/e2e/fixtures/seed_tool_profiles.py /path/to/edt-workspace
"""

from __future__ import annotations

import argparse
import pathlib


PLUGIN_PREFS = "fm.giper.edt.mcp.server.prefs"
PREF_KEY = "mcpToolProfiles"
FIXTURE = pathlib.Path(__file__).with_name("e2e_tool_profiles.json")


def escape_pref_value(value: str) -> str:
    return (
        value.replace("\\", "\\\\")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("=", "\\=")
        .replace(":", "\\:")
    )


def seed(workspace: pathlib.Path) -> pathlib.Path:
    settings = workspace / ".metadata" / ".plugins" / "org.eclipse.core.runtime" / ".settings"
    settings.mkdir(parents=True, exist_ok=True)
    target = settings / PLUGIN_PREFS
    document = FIXTURE.read_text(encoding="utf-8").strip()
    existing = {}
    if target.exists():
        for line in target.read_text(encoding="utf-8").splitlines():
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, value = line.split("=", 1)
            existing[key] = value
    existing["eclipse.preferences.version"] = "1"
    existing[PREF_KEY] = escape_pref_value(document)
    lines = [f"{key}={existing[key]}" for key in sorted(existing)]
    target.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return target


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("workspace", type=pathlib.Path)
    args = parser.parse_args()
    written = seed(args.workspace.resolve())
    print("wrote", written)


if __name__ == "__main__":
    main()
