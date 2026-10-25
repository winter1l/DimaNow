# Feature check and plan — 2026-10-25

Context: after the D-123/D-124 commits the user asked what to add or refine. On 2026-10-25 they answered each candidate (D-125 records the confirmed directions) and asked "확인하고 계획을 세워줘". This file holds the checks behind the plan; Q-034 tracks what is still the user's choice.

## External checks (2026-10-25, read-only, Cloudflare WARP off on the PC)

- **Library** (observed): `lib.dima.ac.kr` loads with WARP off (the user: "1.1.1.1 warp 껏는데 지금은 보인다"). It runs Livetek "Sponge" (page titles end `| 라이브텍 중앙도서관`, assets under `/Sponge/`): server-rendered pages with a session login. Without sign-in: opening hours (리더스스테이션 09:00–20:00, 러닝라운지 09:00–20:00, 자유열람실 24시간), 공지사항·이벤트·공개자료실·분실물, new arrivals. Signed in (the user's Chrome already had a library session; nothing was typed): a header `연체중 N건, 대출중 N건` linking `/MyLibrary`; `/MyLibrary` has the tabs 대출정보 (sortable by 반납예정일, with a 연기신청 column), 예약정보 and 무인예약대출; also `/MyRequest` (희망도서신청) and `/MyFolder`; the page posts `/MyLibrary/GetMyLendChart`. No seat-reservation link was seen. Unknown: whether the library login is the portal SSO or a separate library account.
- **전자출결** (observed): the class.dima.ac.kr link `https://attend.dima.ac.kr/` has no public DNS record (NXDOMAIN from the PC's resolver, 8.8.8.8 and 1.1.1.1). The earlier error page was not caused by WARP. Assumed: campus-network-only or retired; which one is unknown.
- **Grades** (not checked): the intranet was not opened from the PC (no credential entry, and a PC sign-in could disturb the phone's sessions). Intranet pages the app already reads: `dormi/P_C_List.aspx` (상벌점), `dormi/outingApply.aspx` (외출·외박 신청), `su/timeTable_Stnt.aspx` (timetable). The grade page's URL is unknown.
- **4402 expected times** (observed on the user's phone): the user opened Kakao Map's `4402 예상시간표` for `동아방송예술대학교시외버스정류장(34710)`; the screen was captured once with the AI 안내 start/end notices, no taps. The page says `과거 운행 기반 예상시간표로 실제 상황과 다를 수 있습니다`, has day tabs 월–토 and 휴일, and lists 예상 시간표 with 가장 빠른 and 가장 늦은. On the 휴일 tab, compared with the app's official Sunday/holiday table at 34710 (`Bus4402Schedule.official`, Anseong timetable effective 2025-04-28), the departures pair up as 07:40→07:39, 08:20→08:19, 08:50→09:04 (08:51–09:12), 09:20→09:40 (09:30–09:47), 10:00→10:24 (10:18–10:32), 10:40→11:05 (10:58–11:17), 11:20→11:32, 12:00→12:03, 12:40→12:43, 13:20→13:23, 14:00→14:06, 14:40→14:54, 15:20→15:30. Kakao shows nothing before 07:30 (the official table starts at 05:00) and a last pass at 00:38. The cause is unknown (the pairing itself is an assumption; Kakao may count another direction or trip pattern). The weekday tabs were not viewed.
- **Real-time arrivals API** (confirmed from the data.go.kr pages): 경기도_버스도착정보 조회 (dataset 15080346, 경기도 교통정보과) gives real-time arrivals by station and route, REST with JSON or XML, automatic approval for development and operation, development traffic 1,000 calls (per day assumed), more by registering a use case, free, `이용허락범위 제한 없음`. Field names are not on that page (unverified). The national TAGO arrival API (15098530, `apis.data.go.kr/1613000/ArvlInfoInqireService`) also exists; whether it covers Anseong is unknown. The app already holds the GBIS ids (route 231000139, stations 231001421 and 231000470). A key needs a data.go.kr account, which only the user can create.
- **Kakao Map link to a bus stop**: no bus-stop URL scheme was found in Kakao's developer docs (unverified either way).

## Code facts used for the plan (2026-10-25)

- Notices come from the published static data (`StaticNoticeSource`); `CampusSyncWorker` refreshes them at launch and every 12 hours (`work/RefreshScheduler.kt`).
- Notices are published once a day by `publish-data.yml` (cron `43 0 * * *`, 09:43 KST), so a keyword alert can only be as fresh as that run unless the schedule changes.
- The academic calendar is the admin-maintained `data-source/calendar.json`, published when that file changes; its source is the public page `https://www.dima.ac.kr/?p=78` (`CALENDAR_SOURCE_URL`). Observed 2026-10-25 (WebFetch): the page is plain HTML headed `2026년도 학사일정`, months 1–12 as text (`01.06 10시 ~ 01.06 24시` 계절학기 등록, …), no 2027 yet, no year or month parameters; some entries carry older year labels. A scheduled pipeline read of this page could notice a new year's heading (Q-026).
- Home's 수업 card: chips count every future candidate deadline; titles only for the 3 nearest due by today + 2; `다가오는 마감이 없어요` only when nothing qualifies (details in Q-034). Undated items never reach Home.
- Q-032 recheck: every item is still in the code (details in the 2026-10-25 Q-032 update).

## Proposed plan (2026-10-25, awaiting the user's choice of order)

Phase 1, finishing v2.2 (no outside setup):
1. One font size per Home shuttle row (D-125(7)).
2. Home 수업 card: when nothing is due within two days, show the nearest deadline (date and title); far content such as videos is summarised apart (D-125(2)).
3. One daily bundled deadline notification from the last loaded LMS list, default morning, can be turned off (D-125(1)).
4. 점호 outing reminder only after a KakaoTalk 점호 notice was read, opening the outing page (D-125(3)), with a guard against chat questions (needs the user's choice: ignore 1:1 chats and question-like texts, or pin one room).
5. Q-032 fixes: (a), (c), (e), (b) fail-safe, (h); (f) needs a Worker deploy.
6. A what's-new sheet.

Phase 2, a personal Home:
7. Home editor: card order, hiding, favourites row (D-125(5)).
8. App shortcuts and a Quick Settings tile.
9. Notice keyword alerts, optional, suggested once (D-125(4)); limited by the daily 09:43 notice run.
10. ICS export through the share sheet only (D-125(6)).

Phase 3, new data:
11. Library loans and due dates, due-date reminders inside the bundled notification, renewal by opening the library page; opening hours.
12. 4402 real-time arrivals through the Worker (the user's data.go.kr key), or a Kakao Map link first.
13. 2027 academic calendar: a scheduled pipeline read of the public 학사일정 page (Q-026).
14. Grades: open the intranet grade page during 성적 열람 periods, once the page is found.
15. Large screens (Q-022/Q-025) and launch performance (Q-032 (g)).

Held: 전자출결 until the host is seen to work on the campus network.

## 2026-10-26 — The user's corrections and more checks

The user answered the 2026-10-25 findings: library login "포털 계정과 동일", the library app's 모바일 열람증 is what they show at the gate ("바로가기 버튼정도는 앱에 있으면 좋을 듯?"); 4402 "다음 정류장(33243)으로 보는게 더 정확할 것 같아. 실제로는 시간표대로 잘 운영하고 있거든" and, on real-time arrivals, "시간 잘 지키는데 굳이?"; 전자출결 "모바일 앱(헤이영)에서만 동작하는듯" (hearsay-level: the user's reading).

- **Library login** (observed, built-in browser, signed out): the site's 로그인 link is `https://portal.dima.ac.kr?r=https://lib.dima.ac.kr/sso/main.aspx`, the same portal-redirect pattern as the intranet's `/sso/main.aspx` entry (`SchoolService.kt`); there is also `/Account/LogonDacl` (외부이용자 로그인).
- **Library app** (observed on the phone; the user opened it; one capture with the AI 안내 notices): package `kr.ac.dima.lib`, version 20261014, tabs 홈 / 우수권장도서 / My Library / 모바일 열람증. Its manifest (read with aapt2 from the APK pulled into the scratchpad) exports only the launcher `StartBg`; the card screen `NativeIDcard` has no intent filter and is not exported, and the app declares no URL schemes. So DIMA Now can open the library app but not its card screen directly (Android 11+ also needs a `<queries>` entry for the package).
- **헤이영** (observed): `com.shinhan.heyoung`, launcher `.A_Intro`, VIEW scheme `heyoung` with hosts app, call, event, mmaauth, o2o (undocumented).
- **4402 at 33243** (observed, Kakao Map 예상시간표 opened by the user at `동아방송예술대학교입구(33243)`; one tap on 휴일): every row shown is the official origin time + 2 min, earliest + 1, latest up to + 4. 월: 05:02, 05:32, 06:02, 06:22, 06:52, 07:12, 07:32, 08:12, 08:52, 09:32, 10:12, 10:52, 11:32, 12:12, 12:42 for the official 05:00–12:40. 휴일: 05:02, 05:42, 06:22, 07:02, 07:42, 08:22, 08:52, 09:22, 10:02, 10:42, 11:22, 12:02, 12:42, 13:22 for the official 05:00–13:20. The official table holds; the app's `+1` estimate at 33243 equals Kakao's earliest. The 34710 rows of 2026-10-25 do not describe departures (assumed: Kakao counts the terminus differently).

### Code facts for phase 1 (2026-10-26, two read-only Explore subagents, source at abb2bdd)

- Notifications: only two channels exist (`dima_live_guidance_v2` in `LiveSurfaceController.kt`, `academic_schedule` in `AcademicReminderWorker.kt`); no shared posting helper. Taps use a `TARGET_PAGE` extra to MainActivity that `DimaNowApp` maps for DASHBOARD, SHUTTLE, MEAL, TIMETABLE, COURSES, SETTINGS only; there is no DORM or outing target, and school pages open only through `SchoolServiceBridge` inside the composed shell (`rememberOutingLauncher`, portal SSO, in-app WebView, one request at a time). Inferred: the academic reminder's PendingIntent has no Intent flags and MainActivity no `launchMode`, so a tap while the app runs may not reach `onNewIntent` (widgets use NEW_TASK|CLEAR_TOP).
- LMS: no background refresh; a worker can read `lmsSource.snapshot.first()` (items with kind, dueAt, completionState; `lastSuccessAt`). `upcomingHomeTasks` is internal; `HOME_TASK_KINDS` is private.
- 점호: `RollCallStore` keeps parsed facts only (14 days, `noBackupFilesDir`), loads asynchronously; `RollCallRules.status` gives `Announced` for a notice; `OutingRules.warning` uses `applyBy` or 21:00. The Home 점호 card shows only for `home_base` YEIN (엔터관).
- Settings: new notification rows go in the 알림 종류 card (`NotificationGuidanceSettings.kt`), following `academicReminders` (boolean key, flow, setter, SettingsRoute wiring).
- Home: one fixed card order in `DashboardScreen` with stable item keys; capsules size their countdown each on their own (`TextAutoSize.StepBased` in `DepartureLargeContent`); `measuredCellWidth` (TimetableChips.kt) is the measuring pattern to reuse. No ModalBottomSheet yet; no stored last-seen version (version-gate pattern: `campusZoneDefaultsVersion`). D-102(2) turned the launch-time update dialog into a Home card ("no interrupting on entry"), so the what's-new sheet opens from a Home card.
- External apps: nothing launches another app and the manifest has no `<queries>`; the 전체 tab's 바로가기 group holds only DIMA 포털 (`AllScreen.kt`).

## Proposed plan (2026-10-26, supersedes the 2026-10-25 plan; awaiting the user's go-ahead)

Phase 1 (v2.2):
1. One countdown font size per Home shuttle row: measure the tag, each capsule's clock column and share (TextMeasurer, `BoxWithConstraints`), step down from titleLargeEmphasized to 12sp until every countdown fits, pass the size to `DepartureCapsule` in place of auto-size.
2. Home 수업 card shows the 3 nearest open deadlines whatever their distance; far ones in a quiet `D-n · M/d 마감` line; dated 자료 only when LMS tracks it as incomplete. Tests pinning the two-day window change.
3. Daily bundled deadline notification (default 08:30 KST): today's open items from the last loaded LMS list, title like `오늘 마감 과제 2개, 퀴즈 1개`, lines with course, title and time, a `M/d HH:mm 기준` note; tap opens 수업; own channel; Settings row.
4. 점호 outing reminder: only after a held KakaoTalk 점호 notice for the day, only for 엔터관 (YEIN), default one hour before `applyBy` (21:00 when none), cancelled by a cancelling notice; tap opens the outing page through a new `TARGET_PAGE` and the school bridge; own channel; Settings row. Guard choice pending (recommended: group chats only and no question-like texts; the Kakao fields are to be checked without reading message values).
5. Q-032 fixes (a), (b) span expansion plus a fail-safe, (c) through a shared daily scheduler (one-time work re-enqueued for the next HH:mm; the old periodic work cancelled by a policy-version bump), (e), (h); notification taps get NEW_TASK|CLEAR_TOP; (f) only with a Worker deploy approval.
6. 모바일 열람증 shortcut in 전체 → 바로가기: `openApp` helper (launch `kr.ac.dima.lib`, Play Store fallback) plus a `<queries>` entry; optional 헤이영 row.
7. What's-new: after an update a dismissible Home card opens a sheet listing the version's changes; fresh installs skip it.

Phase 2: Home editor (order, hide, favourites row), app shortcuts and a Quick Settings tile, notice keyword alerts (optional, suggested once), ICS export (share only).
Phase 3: library loans and due dates through the portal account, 2027 calendar self-import, a 성적 열람 shortcut once the page is found, large screens and launch performance.
Dropped: 4402 real-time arrivals, attendance data.

## 2026-10-26 — Map added to the plan (D-127(3), plan only)
Candidate for phase 3: a campus map showing the shuttle stops (본관, 엔터관, 운동장), the 4402 stops (34710 대학 셔틀 정류장, 33243 원룸촌 앞), 예인관 and 원룸촌, with the current location. To decide when it is picked up: a drawn campus map in the app (no key, works offline) versus a map SDK (Kakao/Naver need an app key) versus a "지도에서 보기" link into the map app; whether it lives in 전체 or the shuttle tab. Earlier constraint: D-005 "no in-app map picker" concerns choosing zone centres, not a read-only map.
