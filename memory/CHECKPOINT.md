# Checkpoint — D-102/D-103 merged and on the phone — 2026-10-22

## The story so far
Observed: D-102 (UX pass from HIG, M3 and Toss) and D-103 (fade-through tabs, bar refresh, M3 connected button group on Material3 1.5.0-beta02 with Compose 1.12.1, compileSdk 37, AGP 9.1.1, Gradle 9.3.1) are on local main 67f9ea0, not pushed. The phone runs the release-signed v2.1 build of that commit (sha256 d9deb62d…). Instrumentation now runs on the Galaxy Tab S9 over USB; the emulator is shut down (watchdog restarts, /data 92%).

## Decided
- D-098..D-101 as before; D-102 UX pass; D-103 M3 recommendations and toolchain.

## Waiting on the user
Push main when asked; publish v2.1 when asked. Decide whether to fix the tablet-only portrait and widget failures (Q-022). Earlier owner items unchanged (release-key offline backup, privacy contact email, vacation suspensions and 2027 terms, removing the old v1.x app from the phone).

## Next first action
Ask before pushing main; then move Material3 to 1.5.0 stable when it ships and rerun the UI instrumentation on the tablet.

## Open follow-ups
OPEN-QUESTIONS Q-021..Q-023; the blank-classroom separator task offered as a separate session.
