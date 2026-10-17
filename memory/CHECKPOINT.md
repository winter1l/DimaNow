# Checkpoint — launch readiness reviewed — 2026-10-17

## The story so far
Observed: v1.6 (versionCode 7) is the latest GitHub release (0 downloads), signed with the release key via v3 rotation; D-098 keeps that key. The user asked what an actual public launch needs. The read-only review in `launch-readiness-20261017.md` found blockers for both channels (personal seed timetable, term-end handling, holiday/vacation calendars, photo metadata on public uploads, forward compatibility of the first public build, unverified key backup, missing in-app disclosures). No code changed.

## Decided
- D-098 keep the D-096 release key.

## Waiting on the user
Distribution channel (GitHub APK, Google Play or both) and applicationId change; dorm-menu report path (D-097); D-073 at multi-user scale; whether to contact the school. Owner action: offline backup of `.local/release-signing/` with a restore check. Phone v1.6 install still pending.

## Next first action
After the user's decisions, start the proposed order in `launch-readiness-20261017.md` (empty first run and term setup first) and record a decision superseding D-001's multi-user/store exclusions.

## Open follow-ups
OPEN-QUESTIONS: launch decisions (2026-10-17), Q-001..Q-005/Q-010 physical observations, late-night class wrap, Dependabot vs verification metadata, real dorm upload end to end on the new Worker.
