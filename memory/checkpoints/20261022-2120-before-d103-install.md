# Checkpoint — D-102 UX pass waiting for review — 2026-10-22

## The story so far
Observed: the user asked for a UX pass from Apple HIG, Material 3 and the Toss consumer UX guide, keeping the M3 Expressive look, in a new worktree. D-102 is implemented in `C:\project\dahak\DimaNow-ux` on branch `claude/ux-guide-pass` (from main f31e1aa): one-step permission priming, Home date title + in-place location request + update card, timetable empty state and full-screen course form, pinned shuttle header, tab re-select to top, grouped Settings, LMS login polish. Evidence and sources: `ux-guide-pass-20261022.md`. Main, the phone (v2.1) and GitHub are untouched; the branch is local only.

## Decided
- D-098 keep the release key; D-099 GitHub-only launch; D-100 private R2 photos; D-101 compact dorm cards; D-102 UX pass (branch only until the user adopts it).

## Waiting on the user
Review `claude/ux-guide-pass` and say whether to merge it into main (Q-018). Decide on the M3-vs-standing-decision items (Q-019, Q-020). Earlier items unchanged: publish v2.1 when asked; phone migration; release-key offline backup; privacy contact email; vacation suspensions and 2027 terms.

## Next first action
If the user adopts D-102: merge the branch into main (avoid the graphify session's files), run unit + instrumentation tests on main, then ask before pushing or installing on the phone.

## Open follow-ups
OPEN-QUESTIONS 2026-10-22 (Q-018..Q-021, blank-room separators task offered as a separate session).
