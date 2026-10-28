# Checkpoint — D-137/D-138 motion on the phone — 2026-10-28

## The story so far
Observed: branch `claude/ux-guide-pass` (worktree `C:\project\dahak\DimaNow-ux`) is 26 commits ahead of main (e93f947), last commit the D-138 phone install record. The app is still 2.1 / versionCode 9. D-135 and D-136 are on the phone (release-signed build, 2026-10-28). D-137 (motion pass) is committed (8554f08 code, 0fc556f memory) and installed on the phone at 23:11 (release-signed, `dist\d137`); 520 JVM tests and 159 tablet tests pass, lint 0 errors. Nothing is pushed. D-138 (quiet bottom-tab fade, lateral pages for a screen's own tabs) is committed (ac96e6d, b5f9466) and installed on the phone at 23:43 (`dist\d138`): 521 JVM tests, lint 0 errors, tablet 159/159.

## Decided
D-123..D-136 since the previous checkpoint (see DECISIONS.md); D-137 (M3 Expressive motion filled in across the app); D-138 (bottom tabs fade, a screen's own tabs slide as lateral pages).

## Waiting on the user
How D-137/D-138 look on the phone. The motion proposals in Q-045. 2027 학사일정 when the school posts it (Q-026).

## Next first action
Check by eye on the phone what the tablet could not show: the predictive back preview, dialog mark morphs, list item fades, sheets and onboarding (`motion-pass-20261028.md`, "Device checks still to make by eye"). Reply in Korean.

## Open follow-ups
OPEN-QUESTIONS Q-021..Q-023, Q-025, Q-026, Q-029..Q-032, Q-038, Q-039, Q-042, Q-044(b), Q-045; graphify graph in `graphify-out/` (refresh with `graphify update .`).
