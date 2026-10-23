# OPEN QUESTIONS — registered, not remembered

## Status summary — 2026-10-11

Index only; the dated history below is unchanged and remains the evidence.

| Item | Status | Resolved by / evidence |
|---|---|---|
| Q-001 Samsung Live/Now Bar/lock/AOD class rendering | Open | Production class-detail rendering still unobserved |
| Q-002 Fixed-center 2-minute dwell/exit on campus | Open | Needs physical-site observation |
| Q-003 Shuttle countdown accuracy and widget resize in One UI Home | Open | Needs real-trip/launcher observation |
| Q-004 Compact pill and lock-screen order fit | Open | Unlocked visual fit and countdown option pending |
| Q-005 One UI 9 vs One UI 8 status-pill width | Open | No controlled One UI 8 baseline |
| Q-006 Folded One UI 9 Now Bar stale minute text | Resolved 2026-09-08 | D-066; `nowbar-refresh-fix-20260908.md` |
| Q-007 LMS WebView test timeout in full order | Resolved 2026-09-14 | Test helper fix; full 165-test order OK |
| Q-008 Stale dormitory assertion in `GuidanceCardTest` | Resolved 2026-09-14 | Test updated to D-067 split semantics |
| Q-009 Debug APK left on the phone | Resolved | Observed: optimized builds replace-installed on the phone since 2026-09-14 21:08 KST (`lms-recovery-20260914.md`); latest is the D-094 build 78718B5F6F70EF39 on 2026-10-01 10:39 KST (SESSION-LOG) |
| Q-010 Samsung eligibility without the developer test option | Open (unknown) | `nowbar-metadata-experiment-20260914.md` |
| Kakao room label / notification access (2026-09-14) | Obsolete | D-072 removed Kakao intake |
| D-080 history rewrite scope (2026-09-16) | Resolved locally; host-side residual open | Local/published refs rewritten 2026-09-16; GitHub server-managed refs and cached commits need GitHub's assessment (`git-privacy-cleanup-20260916.md`) |
| Q-011 Stadium boarding label | Resolved 2026-09-30 | D-090 |
| Q-012 Stranded dormitory submissions | Resolved 2026-09-30 | D-089 |
| Q-013 D-070 approval idempotency/weekend window | Resolved 2026-09-30 | D-089 (approval path removed; one weekend→next-week rule) |
| Q-014 Repo Worker differs from deployed Worker | Resolved 2026-10-11 | Repo Worker deployed (active 32e9b5a5), migration 0002 complete (SESSION-LOG 2026-10-11) |
| Q-015 Shuttle reporter identity by IP HMAC | Resolved 2026-09-30 | D-091 (shuttle reports removed) |
| Q-016 Eight failing instrumentation tests | Resolved 2026-09-30 | D-093; tests updated and passing |
| Q-017 Committed UI lacks D-081..D-083 | Resolved 2026-09-30 | D-093 (Gemini reconstruction is the baseline) |
| Docs debt (2026-09-30) | Resolved 2026-10-11 | PRIVACY/SECURITY/README updated for D-073, D-058, D-089 and the test device; PRODUCT-TRUTH visibility correction appended; sensitive values moved under D-092 |
| D-094 follow-ups (2026-10-01) | Resolved 2026-10-01 | Profiles regenerated, unused LMS code removed, rejection wording mapped, D-095 |
| Unused `shuttle_reports` D1 rows | Resolved 2026-10-11 | Observed 0 rows; table kept as history per D-091 |
| First real dorm upload and scheduled meal-watch run on the new Worker | Open | Not yet observed |
| `preview_urls: false` in `wrangler.jsonc` | Open | Needs a decision |

| ID | Question | Opened | Status |
|---|---|---|---|
| Q-001 | Does an eligible active class notification appear in Samsung Live notifications, Now Bar, lock screen, and AOD? | 2026-08-26 | Promotion, unlocked countdown pill, and DIMA Now lock-screen/AOD bar physically verified; synthetic expanded bar showed app name only, so production class-detail rendering remains pending |
| Q-002 | Do all three fixed centers produce correct 2-minute dwell/exit transitions on campus? | 2026-08-26 | Pending physical-site observation |
| Q-003 | Do the shuttle countdowns stay within one minute and do both widgets resize/read correctly in One UI Home? | 2026-08-26 | Pending real-trip/launcher observation |
| Q-004 | During active production guidance, does One UI render both compact pill choices and both course/classroom lock-screen orders without unexpected truncation? | 2026-08-27 | Classroom field persisted across five screen cycles and AOD/keyguard; normal unlocked visual fit and countdown option remain pending |
| Q-005 | How much wider is the One UI 9 status pill than One UI 8 under identical device/font/density settings? | 2026-09-08 | One UI 9 physical probe showed eight Korean syllables fully, ninth fading; controlled One UI 8 baseline remains unknown. See PRODUCT-TRUTH.md dated probe evidence. |
| Q-006 | Why does the folded One UI 9 shuttle Now Bar retain its first minute text after Android's active notification payload changes? | 2026-09-08 | Resolved by D-066 on the connected One UI 9 phone: separate a silent DEFAULT live card from the LOW service keeper. Actual folded UI and payload matched 33, 32, 31 minutes and 29 after screen-off/wake. Exact proprietary cache/filter implementation remains unknown. See nowbar-refresh-fix-20260908.md. |
| Q-007 | Why does `LmsCredentialSubmissionWebViewTest.unexpectedVisibleAdditionalAuthenticationFieldStopsSubmission` time out only during the full instrumentation order? | 2026-09-13 | Resolved 2026-09-14: the helper no longer tears down the WebView inside the JavaScript callback before signaling completion. It now completes a `CompletableFuture`, guards evaluation once, applies a 30-second test timeout, and performs main-thread teardown in `finally`. The target passed 12 consecutive repetitions, the three-test class passed, and the settled API 36 emulator completed the full 165-test order with `OK`. |
| Q-008 | Should the obsolete combined dormitory-meal assertion in `GuidanceCardTest` be updated to the D-067 split heading/menu semantics? | 2026-09-13 | Resolved 2026-09-14: yes. The test now asserts the `중식` heading and `기숙사 제육볶음` menu separately, rejects the obsolete combined string, and retains the absence check for `학생식당`. It passed focused, within the 11-test class, and in the full 165-test API 36 emulator order. |
| Q-009 | Which optimized artifact should replace the Debug APK accidentally installed on the physical phone during emulator verification? | 2026-09-13 | Open: settings and test-package cleanup are complete, but the exact previous optimized APK hash is unavailable. Do not install the current optimized build without explicit user direction because that would deploy the security-remediated working tree to the phone. |

## Readings in force — assumed, not decided

| ID | User's words | Our reading (`assumed`) | Breaks if wrong | Ends when | Relied on in |
|---|---|---|---|---|---|

- Resolved (2026-09-08, D-065): the user authorized repairing the investigated report card. Reporting now follows published boarding departures; the 18:45 inferred MAIN arrival remains in topology but cannot be reported. Whether passengers can board that physical arrival is still unknown and is not asserted by the app. See shuttle-report-audit-20260908.md for the historical investigation.

- Resolved (2026-09-08, D-065): after the user authorized repair, the title was changed to departure origin plus following vehicle and shortCriticalText retained the nearest vehicle. On the user's One UI 9 phone, the folded Now Bar visibly rendered `본관 출발 · 다음 차 29분` above `엔터관행 4분`; the unlocked chip also retained `엔터관행 4분`. See PRODUCT-TRUTH.md for dated evidence and nowbar-duplicate-audit-20260908.md for the original investigation.

## Now Bar eligibility follow-up - 2026-09-14

- Q-010 (unknown): which additional Samsung eligibility conditions allow DIMA Now's existing package identity to show without the developer test option? Metadata-only replacement was insufficient on the current API 37 / One UI 9 device. Android promotion was present, but Samsung Showing-list membership and the physical lock-screen card required the option in the tested controls. Cold reboot and any official OEM admission path remain unverified. See `nowbar-metadata-experiment-20260914.md`.

## 2026-09-14 — Kakao setup and phone observation

Unknown: exact classroom room label in actual Kakao notifications. User has been asked for room name and phone unlock. Provisional D-071 reading: default-off local detection with explicit review of structured proposals. Real Kakao end-to-end observation requires user-granted special notification access and a qualifying new notification; synthetic notification tests are not real Kakao acceptance.

Resolved 2026-09-14: D-072 removes Kakao intake, so the prior room-name/access question is withdrawn. Phone unlock and actual course UI observation completed. D-073 records user-approved SSO bridge exception, including Android domain-level allowance with application port/path checks.

## 2026-09-16 — D-080 historical Git privacy
Pending explicit history scope: two accidental screenshots remain in existing local commits after index removal; previously published documentation contains device metadata in GitHub main/history. Complete local ignore/redaction/checker changes first. Rewriting published commit IDs and force-updating remote refs requires a concrete reviewed plan and user authorization; do not conflate local index cleanup with erased historical exposure.

Resolved 2026-09-16: user explicitly approved history rewriting and force updates.
Local refs and published branches/tags are rewritten and verified. Remaining
host-side issue: server-managed refs and cached commit access (private P-05) require
GitHub's assessment. An ignored support request draft is ready but not sent;
see git-privacy-cleanup-20260916.md for exact evidence and limits.

## 2026-09-30 — Post-push review (D-070 live on main)

- Q-011 (observed, code): stadium-stop boarding label. D-019 requires `운동장` for `stadium-stop` departures on Shuttle, Live Update, and widgets; since 14691bd `GuidanceEngine.boardingOriginName` returns `본관` for both branches and no superseding decision or test records the change. Needs user decision: restore D-019 or record a superseding decision.
- Q-012 (confirmed): dormitory push runs still execute the older workflow file on the `dorm-submissions` branch (blob 5404720, step `기숙사 식단 검증 및 게시`) with pipeline code from `main`, so candidates become PENDING_REVIEW but the new review-summary step never runs and no operator notification exists. Submissions can wait indefinitely; the app shows only a generic "계속 확인 중" message.
- Q-013 (confirmed, code): D-070 approval has no idempotency/rollback guard (re-dispatch republishes and overwrites the audit record; older candidate can replace newer) and weekend approval window rejects the ending week that staging accepts.
- Q-014 (observed from records): repo Worker differs from deployed f721083c. Next APK release is blocked until the repo Worker, D1 migration 0002 and new secrets are deployed, because the app now calls `/v1/shuttle-reporter-token`, which the deployed Worker does not serve.
- Q-015 (observed, code): shuttle reporter identity is now a Worker HMAC of the client IP instead of D-055's install-scoped token; users behind shared campus NAT collapse to one reporter. Needs decision: accept as D-070 consequence or restore install scope.
- Docs debt (observed): README/PRIVACY/SECURITY still describe dormitory auto-publication (contradicts live D-070), HTTPS-only LMS hosts (contradicts D-073 SSO exception), a removed LMS 읽음 filter, and One UI 8 as the test device; PRODUCT-TRUTH still says the repository is private. Public memory contains a real course/classroom and a Figma file URL; history-residual PR pointers remain listed.

Resolved 2026-09-30 (same day): Q-011 by D-090 (stadium label restored), Q-012 and Q-013 by D-089 (automatic publication; approval path removed; single weekend week rule), Q-015 by D-091 (shuttle reports removed). Q-014 narrowed: the app no longer calls `/v1/shuttle-reporter-token`, so the next APK is not blocked by the Worker; repo Worker still differs from deployed f721083c and its deployment (D1 migration 0002, removal of report endpoints, `SHUTTLE_REPORT_HMAC_KEY` deletion, unused `shuttle_reports` rows) remains an operator task.
- Q-016 (observed 2026-09-30; see Q-017 — regression, not stale tests): eight instrumentation tests fail on API 36 emulator both at cd6e019 and 479a617 — DataSourceScreenTest (nearestLastDeparture…, mainGroupAndDepartureLabels…, userSeesEveryShuttleDay…, pullingTheShuttleList…, nearestDeparturesKeepClockTime…, todaysMealExplains…, shuttleCacheDescribes…) and GuidanceCardTest.timetableCourseUsesKoreanWeekday…. Assertions expect pre-D-081 combined text such as `09:00 · 30분 후 · 막차`; the redesigned UI splits countdown and clock. Needs test update (or UI decision) — CI does not run instrumentation, so these are invisible there.
- Q-017 (confirmed 2026-09-30): committed `ui/DimaNowApp.kt` lacks most D-081/D-082/D-083 changes. It still uses `ExpressiveToggleButton` (8 uses), has no `shuttle_times_toggle` expansion, no `표시할 출발 시각` wording, and never calls `DimaNavigationShell` (dead in main). Two unreachable git blobs hold the 9/16 redesigned file: 84506044 (D-082) and dbfe60ea (D-082 + D-083 color restore, 3334 lines), saved to the session scratchpad. The committed file differs from dbfe60ea by about 1,400 lines, and from 14691bd (pre-redesign) by only the d6de094 partial edits. The Q-016 failures are therefore a UI regression, not stale tests. Cause (which session overwrote the file between 9/16 and 9/30) is unknown. Needs user decision: restore dbfe60ea as the base and re-apply D-090/D-091 and later edits, or accept the current UI and rewrite the tests/decisions.
- Q-017 correction (observed 2026-09-30, phone via USB): the installed phone APK is the 9/21 shuttle-fix build (SHA-256 8EC0C3AA…, lastUpdate 2026-09-21 16:17). Its dex, like the 9/21 meal-fix build, has no `shuttle_times_toggle` or `표시할 출발 시각` and still has `ExpressiveToggleButton`. memory/gemini-redesign-handoff-20260916.md and gemini-install-20260917.md show the user requested a free Antigravity/Gemini frontend reconstruction on 9/16, reviewed and installed it on the phone on 9/17 (E13BB1D2…), and D-084 transferred that "current source" to Figma. So the current DimaNowApp.kt is most likely the user-requested Gemini redesign, not an accidental overwrite. Gap: no DECISIONS entry supersedes the D-081/D-082 visual specifics, so the D-082 tests were left stale. Pending user confirmation to record the Gemini UI as baseline and update the eight tests.

Resolved 2026-09-30: Q-016/Q-017 by D-093 (user confirmed the Gemini reconstruction as UI baseline); the eight tests were updated and pass on the API 36 emulator.

## 2026-10-01 — D-094 follow-ups
- Baseline profiles in app/src/main/generated/baselineProfiles are stale (still list removed ExpressiveToggleButton/pulseBreath and pre-split class names); regenerate with the :benchmark generator.
- Unused non-UI code left: LmsCacheDao.getItem/markItemRead, LmsHtmlParser.parseBoardItems, LmsAssignmentDates, isCompletedLmsLogin; two warnings in live/LiveSurfaceController.kt.
- Nearby-stop 4402 card (primaryContainer) makes the secondaryContainer second capsule/chip blend in (D-083 colors) — needs a color decision.
- Server-sent dormitory rejection reasons are still shown verbatim; update-check errors no longer keep raw detail for diagnostics.
- Emulator system_server crashes (vold/keystore2 native crashes) during long instrumentation runs; run classes individually.

Resolved 2026-10-01: baseline profiles regenerated; unused LMS code removed; dormitory rejection wording mapped; 4402 nearby color decided (D-095). Still open: Worker production deploy (needs user wrangler login), shuttle_reports D1 rows left in place.
- 2026-10-11 (observed): production D1 is in a partial migration-0002 state — gateway_budgets (+index) exists, gateway_leases (+index) missing. Harmless for the currently deployed Worker; the new Worker must not be deployed until gateway_leases exists. Deploy also needs wrangler login and a ≥32-char RATE_LIMIT_SALT.

Resolved 2026-10-11: Worker deployed from repo (active 32e9b5a5), migration 0002 complete, RATE_LIMIT_SALT rotated, SHUTTLE_REPORT_HMAC_KEY deleted. Open: confirm the next real dorm upload and scheduled meal-watch run on the new Worker; decide whether to set `preview_urls: false` in wrangler.jsonc.

## 2026-10-11 — Status sweep
Resolved 2026-10-11: Q-009 — the phone has run optimized builds since 2026-09-14 21:08 KST (`lms-recovery-20260914.md`); the latest is the D-094 build on 2026-10-01 (SESSION-LOG). The 2026-09-14 Kakao note is obsolete under D-072. The 2026-09-30 docs debt is closed by the README/SECURITY update and the PRODUCT-TRUTH correction of 2026-10-11. See the status summary at the top.
- 2026-10-12 (owner action): the `dorm-submissions` branch still carries an older publish workflow that persists checkout credentials and handles only the latest file; main's scheduled sweep now covers missed submissions. Decide whether to delete/sync that branch's workflow. Dependabot Gradle updates require regenerating gradle/verification-metadata.xml.
- 2026-10-12 (observed, low): GuidanceEngine class guidance uses same-day LocalTime windows, so a class starting within ~15 minutes before midnight KST yields phase NONE (window wraps past midnight). No real class is scheduled that late; LiveClassMinuteUpdateTest now skips when its fixture would cross midnight. Fix only if late-night classes become possible.

Resolved 2026-10-12: dorm-submissions branch pruned to images only (1015a56); processing triggered from main via Worker repository_dispatch. Open: user decision on keeping the D-096 release key vs reverting to debug signing (must be decided before the phone installs v1.6).

Resolved 2026-10-12: new Worker scheduled meal watch confirmed by the 17:07Z repository_dispatch run.

Resolved 2026-10-17: signing question closed by D-098 (keep the release key).
- 2026-10-17 (open, user decision): the user asked for a launch-readiness review to "actually release" the app. D-001 still excludes multi-user distribution and store release, and README lists both as out of scope. Distribution channel (GitHub direct APK vs Google Play), audience and the applicationId (`com.example.dimanow`) are undecided; a launch decision would supersede those D-001 clauses.
- 2026-10-17 launch review (`launch-readiness-20261017.md`), user decisions pending: (1) channel GitHub APK / Google Play / both; (2) applicationId change before the first public build (`com.example` is rejected by Play per community reports — unconfirmed by a primary source); (3) a report/take-down path for auto-published dorm menus, which would supersede D-097; (4) re-confirm D-073's HTTP SSO exception for many users; (5) contacting the school about name, colours and LMS automation.
- 2026-10-17 unconfirmed external facts: Korea's date for Android developer verification of sideloaded apps (global 2027, no country list); whether uploaded photos/IP hashes make the developer a 개인정보처리자; the 60-day scheduled-workflow inactivity rule's effect on this repo; Samsung Auto Blocker default per model; Samsung's Now Bar admission mechanism.

Resolved 2026-10-17: launch decisions — D-099 (GitHub only, new applicationId, report/take-down path, D-073 kept with disclosure). The school has not been contacted (owner's choice to make).
- 2026-10-18 (open, owner): privacy-officer contact is GitHub-only; adding an email is the owner's decision. Vacation shuttle suspensions and 2027 term dates are unknown and must be added to `data-source/calendar.json` when published (the app shows a "학기 중이 아니라 운행 시간이 다를 수 있어요" note outside terms meanwhile). Pushes to `data` by a contents:write token remain possible (P-06). Korea's date for Android developer verification of sideloaded apps is unknown; registration of `io.github.winter1l.dimanow` and the release key will be needed when it applies.
- 2026-10-18 (open, observation): first real dormitory report and photo upload on Worker 815eb7a7 not yet observed; v2.0 not yet installed on the phone.
- 2026-10-22: Worker-initiated workflow_dispatch observed (upload test, run 39750237218). Still open: a real menu photo end to end and a report dispatch after the permission fix. P-06's `data` push residual is closed by removing Contents write from the App (D-100).

## 2026-10-22 — D-102 UX pass, items for the user
- Q-018 (user decision): adopt the `claude/ux-guide-pass` branch into main? It is unpushed and unmerged; the phone still runs v2.1 without D-102.
- Q-019 (user decision, M3 conflict with standing decisions): M3 recommends fade-through for navigation-bar destination changes, while D-026/D-042 chose a directional slide + fade; M3 also asks for a single-tap refresh besides pull-to-refresh (D-058 removed the top refresh icon). Kept as decided; change only on request.
- Q-020 (later, library): M3 now prefers the connected button group over segmented buttons; that component needs Material3 1.5 (beta02 on 2026-10-21). Revisit when 1.5 is stable (D-094 keeps 1.4.0).
- Q-021 (observed, unresolved): Korean word-unit line wrapping via `LineBreak.WordBreak.Phrase` + `ko-KR` did not change wrapping on the API 36 emulator (en-US system locale); unknown whether a Korean-locale Android 17 phone behaves differently. Not shipped.
- 2026-10-22 (observed): a course saved without a classroom renders dangling separators in Now Bar/notification/widget text (`GuidanceEngine`, `LiveSurfaceController`, `CampusSummaryWidgetPlanner`); Home was fixed in D-102, the rest was offered as a separate task.

Resolved 2026-10-22: Q-019 and Q-020 by D-103 (fade-through tabs, bar refresh action, connected button group on Material3 1.5.0-beta02). New risk to watch: Material3 is a beta artifact until 1.5.0 is stable; move to the stable release when it ships and re-run the UI instrumentation.

Resolved 2026-10-22: Q-018 — the user adopted D-102/D-103; main fast-forwarded to 67f9ea0 (not pushed) and the phone runs it.
- Q-022 (observed, pre-existing): on the Galaxy Tab S9 (Android 16, large screen) `PortraitOrientationTest.mainActivityStaysPortraitWhenTheLargeScreenRotates` sees landscape after rotation, and `CampusSummaryWidgetLayoutTest` fails twice (compact line clipped 77 > 47; NPE in `verifySummary`). Both fail the same on the pre-D-102 build, so D-054's portrait opt-out and the widget size thresholds do not hold on this tablet. The release build also opened in landscape there. Update 2026-10-22 (observed, D-104 worktree build): the portrait half is superseded by D-104 — large screens now follow rotation and the rewritten `PortraitOrientationTest` passes on the tablet (3/3); the two widget failures remain unchanged (77 > 47, NPE).
- Q-023 (observed): the API 36 emulator restarts system_server by watchdog during long UI runs (WindowManager blocked ~73s) with /data at 92%; consider wiping or recreating the AVD before relying on it again.
- Q-024 (observed, D-104): on the Galaxy Tab S9 in landscape the whole Home list fits once the flexible app bar collapses, so `TabStateRestorationTest.homeListScrollPositionSurvivesATripToShuttleAndBack` is skipped there by an assumption (the shuttle-day round trip still runs). The Home scroll round trip is covered only on a phone-sized window; no phone or emulator run of D-104 yet (the D-104 build is installed on the phone since 2026-10-22 23:14, but no test or UI check ran there).
- Q-025 (open, D-104): the large-screen step has the rail, a centred 840dp column and rotation, but no two-pane (list-detail) layout. The natural candidate is Courses (LMS list and detail), which needs a signed-in session to verify. Ask the user whether to add it.

Resolved 2026-10-22: Q-024 — with the D-105 pinned bar and 16dp gaps the Home list overflows the tablet window again and `homeListScrollPositionSurvivesATripToShuttleAndBack` runs and passes there (no assumption skip).

- Q-026 (open, D-106): the intranet 학사일정 lists 2026 only; 2027 terms and exam weeks must be added to `data-source/calendar.json` when the school publishes them, or Home falls back to `방학` after 2026-12-18. Intranet feature ideas (timetable import, 학사 일정 알림, 예인관 외출외박/상벌점) await the user's choice.

- Q-027 (open, D-107): the signed-in paths are untested. Unknown whether the portal honours `?r=` for the intranet (`https://intranet.dima.ac.kr/default.aspx`) and DIMA C (`/sso/index.jsp`), which `sso.dima.ac.kr:8080/sso/*` hops they use, and whether the intranet table and 상벌점 headers match the scripts on a real account. A failure message names the last host/path (no query) to fix the route. Needs the user to try 장비 대여, 시간표 가져오기 and 상벌점 불러오기 on the phone.

Resolved 2026-10-23: Q-027 — on the phone (SM-S918N, saved portal account) 상벌점 불러오기 first failed with `intranet.dima.ac.kr/errors/WrongAccess.aspx`; with the intranet's `/sso/main.aspx` entry (D-108) 상벌점 and the timetable preview (5 courses) read correctly, and DIMA C catalog and availability read as before. The 상벌점 table headers and the timetable table match the scripts on a real account.

- Q-028 (open, D-108): opening DIMA C 기자재 대여신청 inside the app (signed in) is the agent's extension of the outing request; the user has not confirmed it yet. Revert to the external browser if they prefer.
- Q-029 (open, D-108): on 2026-10-23 01:47–01:52 KST DIMA C's HTTPS (`udimac.dima.ac.kr:443`) reset connections from the phone's LTE address while port 80, the portal, intranet, LMS and the PC's network worked; it recovered by itself. Assumed (not confirmed) a rate limit after repeated test reads; the catalog now pauses between group requests. Watch for it in normal use.

Resolved 2026-10-23: Q-028 — the user confirmed that DIMA C에서 신청하기 keeps opening signed in inside the app (D-108 confirmation).

- Q-029 update (2026-10-23): DIMA C's HTTPS reset the phone's LTE address again about 11:52–11:57 KST after repeated test reads (install, catalog, model days). Hidden reads now skip page decoration on the service hosts (D-111). Still assumed, not confirmed, to be a rate limit.

- Q-030 (open, D-112): a real KakaoTalk 점호 announcement reaching the 기숙사 오늘 card is not yet observed. 2026-10-23 evening the user turned notification access on; observed on the phone: the listener is enabled and bound (`dumpsys notification`), the 카톡 button left the 오늘 card, and the card shows the rule (Friday: 다음 점호 10월 27일 (화) 23:00). No KakaoTalk notification was in the shade, and the listener reads only notifications posted after it binds, so nothing was parsed yet. Unknown: whether the 예인관 room is muted (a muted room posts nothing) and whether KakaoTalk's MessagingStyle extras carry the text as the parser expects. Closes when a real or user-arranged test message (for example `금일 점호는 실시되지 않습니다.` sent by someone else) shows `카톡 공지 HH:mm` on the card.

- Q-030 update (2026-10-23): D-115 also reads a notice already in the shade when the listener connects, so a test message that arrived before an app update or before access was allowed now counts too. Still unobserved with a real KakaoTalk notification.

- Q-031 (open, 2026-10-23): read-only optimization audit by two subagents; top items spot-checked in source by the main session. Nothing changed yet.
  - Confirmed from code: (a) `AppPreferences` flows map one DataStore without `distinctUntilChanged`, so any write re-runs the `combine(zones, locationMode, policy)` in `DimaNowApplication` and `CampusGeofenceManager.sync` removes and re-adds every geofence with an initial ENTER/DWELL trigger; near 4402 the proximity state (`lastValidAt = now`) is written every 30 s, so this repeats every 30 s. (b) `LiveMinuteUpdateScheduler` refreshes twice a minute (in-process ticker via the coordinator plus the exact-alarm receiver calling `guidanceOrchestrator.refresh` directly), and `LiveSurfaceController.ensureChannel` runs its channel calls on every show. (c) The equipment page re-reads DIMA C on every entry after a failure, with no cooldown (Q-029). (d) Each model expand builds a new hidden WebView and reloads `ST_MPRT01_L01.do`. (e) Hidden reads block images, fonts and CSS but not `.js`. (f) `ST_MPRT02_R00` asks for `recordCountPerPage=10000`. (g) LMS detail always refetches (D-047). (h) The baseline-profile journey still opens `nav_TIMETABLE` (gone since D-110); profiles date from 2026-10-11. (i) Meal camera captures in `cacheDir/dorm-meals/` are never deleted.
  - Likely, not measured: the widget minute alarm (`RTC_WAKEUP`, exact) wakes the phone every minute while a shuttle widget shows rows with the screen off; extra JS and XHR load from (d)/(e).
  - Needs a decision: equipment disk cache (D-108(4)), LMS detail reuse (D-047), slower 4402 sampling (D-061/D-068), pausing minute updates while the screen is off (D-063/D-066/D-068/D-109).

- Q-031 update (2026-10-23): D-116 fixes (a), (b), (c), (e), (h) and (i), and the widget alarm (the widgets are home-screen only, the user said). (f) is not a problem (DIMA C returns only current and coming bookings) and (d) is left as is (one page request of four after (e)). Still open: (g) and the items that need a decision; `ServiceCalendarStore.loadCached` stays on the main thread at start (readers use it synchronously, so moving it could start guidance with the bundled calendar).
