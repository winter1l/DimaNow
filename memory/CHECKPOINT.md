# Checkpoint — v2.0 public release — 2026-10-18

## The story so far
Observed: v2.0 (`io.github.winter1l.dimanow`, versionCode 8) is the latest GitHub release, immutable, signed with the release key alone (asset sha256 b3973a90…, 5,940,492 bytes). It implements every D-099 recommendation except Google Play. CI (validate, instrumentation 2 shards, publish) passed on 154b705; Pages serves the calendar and app_release datasets, the download page and privacy.html; Worker 815eb7a7 serves the dormitory report endpoint.

## Decided
- D-098 keep the release key; D-099 public GitHub launch without Google Play.

## Waiting on the user
Phone migration: install v2.0 alongside v1.x (new package), import `.local/migration/my-timetable-2026-2.json`, check, then remove v1.x. Owner actions: offline backup of `.local/release-signing/`; decide a privacy contact email; fill vacation shuttle suspensions and 2027 terms in `data-source/calendar.json` when DIMA publishes them.

## Next first action
If the user asks for the phone install, connect the phone and install `dist/DIMA-Now-v2.0-optimized.apk`, then import the timetable file.

## Open follow-ups
OPEN-QUESTIONS 2026-10-18 entries; Q-001..Q-005/Q-010 physical observations; real dorm upload and report end to end on Worker 815eb7a7.
