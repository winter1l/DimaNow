# Checkpoint — R2 photo storage live, v2.1 ready — 2026-10-22

## The story so far
Observed: D-100 is deployed. Worker 6764a809 stores dormitory photos in the private R2 bucket `dima-now-dorm-photos` (30-day lifecycle rule `expire-30d` plus daily prune, 500 MB cap enforced by the Worker), wakes the publish workflow with workflow_dispatch choices, and serves photos to the workflow only with `PHOTO_READ_TOKEN` (Actions secrets `DORM_PHOTO_ENDPOINT`, `DORM_PHOTO_READ_TOKEN`). The `dorm-submissions` branch is deleted. v2.0 was withdrawn (tag unusable); v2.1 (versionCode 9) is built and signed (`dist/DIMA-Now-v2.1-optimized.apk`, sha256 ee2d6477…) and installed on the phone next to the old v1.x app; the timetable import file is in the phone's Download folder. No v2.1 GitHub release yet.

## Decided
- D-098 keep the release key; D-099 GitHub-only launch; D-100 private R2 photos (30 days, 500 MB cap).

## Waiting on the user
Publish v2.1 when asked. On the phone: finish onboarding in the new app, import `dima-now-timetable-2026-2.json` (설정 → 데이터 관리), then remove the old v1.x app. Owner: offline backup of `.local/release-signing/`; privacy contact email; vacation suspensions and 2027 terms in `data-source/calendar.json`.

## Next first action
Confirm the next Worker meal-watch dispatch succeeds with the new Actions-only App permission; then publish v2.1 if the user asks.

## Open follow-ups
OPEN-QUESTIONS 2026-10-22; first real photo upload and report on the R2 path not yet observed.
