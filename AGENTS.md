# Project memory

Read `CLAUDE.md` before working in this repository; it defines the project code,
language, safety and validation conventions.

For upstream ports, EDT compatibility changes or E2E orchestration, also read
`docs/e2e/port-lessons-1.0.12.md`. It records confirmed causes, unresolved
hypotheses and precautions from the 1.0.12 port. Do not report a hypothesis as
a confirmed cause or a recorded precaution as an implemented fix.

Before resuming the current port, read the latest checkpoint at the top of
`.claude/work/port-1-0-12/state.md` if present. Older entries are historical.
Verify the current source, binary and runtime identity before using old results.

Keep confirmed lessons current when new evidence changes their status. Preserve
failure evidence before resetting fixtures or restarting a failed test stand.
