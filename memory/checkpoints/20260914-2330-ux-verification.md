# Checkpoint — D-075 UI and onboarding verification — 2026-09-14 23:06

## The story so far
Approved plan implemented by root and three agents. Home merges class/deadline briefing; timetable actions use overflow; transit/meal wording simplified; settings grouped and diagnostics folded. Onboarding is welcome/draft direction/shared permission checklist with DataStore draft and atomic finalization. Courses Today explains actionable emptiness while preserving completed list. Public stop labels follow current AGENTS. D-073/D-074 and unrelated meal work retained.

## Decided
D-075 implemented. First-run fixtures stay on emulator-5554; phone replacement install only after tests. All source changes remain local, no commit/deployment requested. Shared permission changes invoke app geofence resync/runtime refresh.

## Waiting on the user
None.

## Next first action
Read artifacts/ux-onboarding-20260914/ui-green-core.log (34-test run, session 71478) then rebuild final debug/test/optimized+lint after root final layout and DataSourceScreenTest text updates; run remaining UI tests before phone update.

## Tried
Two baseline tests failed as expected: completed-only Today has no empty message; first-run next page was notification instead of home direction. First green build succeeded in 3m25s, core tests underway. Root finish-layout.py executed once after green build; do not rerun scripts (they transform source once). Emulator medium_phone launched hidden this turn. Physical device uses isolated ADB port 5038; endpoint is session context only, never tracked evidence. Screenshot CLI capture requires immediate view_image. Final install must use optimized -r and pull-back SHA verification.
