# Checkpoint — D-104 merged and on the phone — 2026-10-22

## The story so far
Observed: D-102, D-103 and D-104 (M3 Expressive alignment with the large-screen layout) are on local main ad3bfa5, not pushed. The phone runs the release-signed v2.1 build of that commit (`dist/d104/`, sha256 f059d2fb…, first install time kept). On the Galaxy Tab S9, 31 of 32 UI/LMS/widget classes pass (Q-022 widget failures unchanged); 294 JVM tests and lint pass. The tablet is back on the debug and test builds.

## Decided
- D-098..D-103 as before; D-104 (item 16 = plain time labels; large screens follow rotation, superseding D-054 there).

## Waiting on the user
Push main when asked; publish v2.1 when asked (the release asset would be the D-104 build in `dist/d104/`, not the older D-103 file in `dist/`). Whether to add a two-pane Courses layout (Q-025). Earlier owner items unchanged (Q-022 widget sizes, release-key backup, privacy contact email, 2027 terms, old v1.x app).

## Next first action
Ask the user how the phone looks after the D-104 install (Q-024: phone-sized behaviour not yet observed); ask before pushing main.

## Open follow-ups
OPEN-QUESTIONS Q-021..Q-025; the blank-classroom separator task offered as a separate session; move Material3 to 1.5.0 stable when it ships.
