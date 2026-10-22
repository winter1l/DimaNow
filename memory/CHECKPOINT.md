# Checkpoint — D-108 on the worktree branch and the phone — 2026-10-23

## The story so far
Observed: main 2baac30 (not pushed) has D-102..D-105. The worktree branch `claude/ux-guide-pass` adds D-106, D-107 and now D-108 (intranet SSO route fix; 외출·외박 and DIMA C pages inside the app; 장비 대여 redesign; saved 상벌점 with refresh; shuttle side from the room's building). The phone runs the release-signed worktree build (`C:\project\dahak\DimaNow\dist\d107-intranet\`). JVM 316 pass; tablet 31/32 UI classes pass (Q-022). Signed-in reads verified on the phone (Q-027 closed).

## Decided
- D-106; D-107 with its amendment; D-108 (DIMA C inside the app confirmed by the user).

## Waiting on the user
D-108 is committed on the branch (66e095f); merge into main and push / publish v2.1 only when asked. 2027 학사일정 when the school posts it (Q-026).

## Next first action
Nothing pending from D-108; ask what to do next (merge into main, push or new work); reply in Korean.

## Open follow-ups
OPEN-QUESTIONS Q-021..Q-023, Q-025, Q-026, Q-029; Material3 1.5.0 stable; graphify code graph of `app/src` in `graphify-out/` (refresh with `graphify update`).
