# Checkpoint — v1.6 released — 2026-10-12

## The story so far
Observed: v1.6 (versionCode 7) is the latest GitHub release, signed with the new release key via v3 rotation (D-096). CI now runs JVM, pipeline, Worker and full instrumentation suites green. Worker c3c5b371 (security hardening) is live; D1 migration 0002 complete.

## Decided
- D-095 card styling, D-096 release key rotation, D-097 content limits only for auto-published dorm menus.

## Waiting on the user
Whether to install v1.6 on the phone now (one-time ADB/manual install; data kept). Owner actions: back up `.local/release-signing/` offline; decide on the old workflow file on the `dorm-submissions` branch.

## Next first action
Read the 17:07Z Worker tail log (scratchpad worker-tail.jsonl) to confirm the meal watch dispatches on the new Worker.

## Open follow-ups
OPEN-QUESTIONS: Q-001..Q-005/Q-010 physical observations, late-night class wrap edge case, Dependabot vs verification metadata, real dorm upload end to end on the new Worker.
