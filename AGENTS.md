# Project memory

Read `CLAUDE.md` before working in this repository; it defines the project code,
language, safety and validation conventions.

For upstream ports, EDT compatibility changes or E2E orchestration, read
`docs/porting-guide.md` first. It consolidates the reusable lessons, fork
invariants, exact-SDK workflow and failure-preservation rules across attempts,
without requiring previous chats. Also read `docs/e2e/port-lessons-1.0.12.md`
for the detailed confirmed causes, unresolved hypotheses and precautions from
the 1.0.12 port. Do not report a hypothesis as a confirmed cause or a recorded
precaution as an implemented fix.

Before resuming a port, read the latest checkpoint at the top of its
`.claude/work/port-<version-with-hyphens>/state.md` if present (for example,
`port-1-0-13` for 1.0.13). The `port-1-0-12` checkpoint describes the previous
port and its subsequent cleanup. Older entries are historical.
Verify the current source, binary and runtime identity before using old results.

Keep confirmed lessons current when new evidence changes their status. Preserve
failure evidence before resetting fixtures or restarting a failed test stand.
