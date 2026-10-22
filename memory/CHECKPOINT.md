# Checkpoint — D-104 M3 Expressive alignment done on the worktree branch — 2026-10-22

## The story so far
Observed: D-102 and D-103 are on local main 7beaf93 (not pushed) and on the phone. D-104 (every item of `m3e-review-20261022.md`, per the user's answers) is implemented and committed on the worktree branch `claude/ux-guide-pass` in `DimaNow-ux`, including the large-screen layout (navigation rail from 600dp, centred 840dp column with aligned app bar, rotation on large screens). 294 JVM tests, androidTest compile and lint pass; on the Galaxy Tab S9, 31 of 32 UI/LMS/widget classes pass (the widget failures of Q-022 are unchanged).

## Decided
- D-098..D-103 as before; D-104 (item 16 = plain time labels; large screens follow rotation, superseding D-054 there).

## Waiting on the user
Whether to merge D-104 into main and install it on the phone (not requested yet). Whether to add a two-pane Courses layout (Q-025). Earlier owner items unchanged (push main, publish v2.1, Q-022 widget sizes, release-key backup, privacy contact email, 2027 terms, old v1.x app).

## Next first action
Ask the user about merging and the phone install; on merge, check phone-sized portrait behaviour (Q-024) before or right after installing.

## Open follow-ups
OPEN-QUESTIONS Q-021..Q-025; the blank-classroom separator task offered as a separate session.
