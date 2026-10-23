# Checkpoint — D-110 전체 tab on the phone — 2026-10-23

## The story so far
Observed: main 2baac30 (not pushed) has D-102..D-105. The worktree branch `claude/ux-guide-pass` adds D-106, D-107 and now D-108 (intranet SSO route fix; 외출·외박 and DIMA C pages inside the app; 장비 대여 redesign; saved 상벌점 with refresh; shuttle side from the room's building). The phone runs the release-signed worktree build (`C:\project\dahak\DimaNow\dist\d107-intranet\`). JVM 316 pass; tablet 31/32 UI classes pass (Q-022). Signed-in reads verified on the phone (Q-027 closed).

## Decided
- D-106; D-107 with its amendment; D-108 (DIMA C inside the app confirmed by the user); D-109 (class chip `약칭 N분`); D-110 (전체 tab); D-111 (add-course menu, 기숙사 page, equipment days kept); D-112 (기숙사 guide, Kakao 점호 reading); D-113 (기숙사 first screen and detail pages); D-114 (card contrast, icon badges).

## Waiting on the user
D-108 is committed on the branch (66e095f); merge into main and push / publish v2.1 only when asked. 2027 학사일정 when the school posts it (Q-026).

## Next first action
`dist/d114-contrast/` is on the phone; D-114 is committed on `claude/ux-guide-pass` (not pushed). Once the user allows notification access, confirm a real 점호 announcement reaches the 기숙사 오늘 card; look at the class chip before a class. If wireless ADB drops after an adb server restart, `adb connect` the last-known endpoint from `.local/device-connections.json` (main checkout); reply in Korean.

## Open follow-ups
OPEN-QUESTIONS Q-021..Q-023, Q-025, Q-026, Q-029; Material3 1.5.0 stable; graphify code graph of `app/src` in `graphify-out/` (refresh with `graphify update`).
