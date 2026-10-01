# Checkpoint — D-089..D-092 implemented locally — 2026-09-30

## The story so far
Observed: main was merged with origin and pushed (cd6e019); CI passed. Post-push review led to user decisions D-089 (automatic dorm publication), D-090 (restore 운동장 label), D-091 (remove shuttle reports) and D-092 (sensitive memory values in ignored `.local/memory-private/`). All four are implemented and verified locally but not committed.

## Decided
- Dorm submissions auto-publish; the `dorm-submissions` branch keeps its older workflow file, which calls the same `publish-dorm-meal` command.
- Worker deployment and APK install are separate operator steps.

## Waiting on the user
Commit/push approval for the D-089..D-092 working tree.

## Next first action
After approval: commit (code, docs, memory), push, confirm `검증` and `캠퍼스 데이터 게시` runs.

## Open follow-ups
- Worker deploy: D1 migration 0002, remove report endpoints, delete `SHUTTLE_REPORT_HMAC_KEY`, decide on unused `shuttle_reports` rows.
- `prepareShuttleTopology` has no production caller after D-091.
- Docs debt not yet fixed: SECURITY/README HTTPS-only LMS claim vs D-073, removed LMS 읽음 filter, One UI 8 device note, PRODUCT-TRUTH "private repo", D-009 duplicate ID, 00-INDEX missing files.
- Sensitive values already in published Git history remain there.
