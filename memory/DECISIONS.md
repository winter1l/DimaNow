# DECISIONS — append-only ledger

## D-001 · Personal on-device Android product boundary — 2026-08-26 (user-approved implementation brief)

DIMA Now is a personal-use Kotlin Android app for one Galaxy device on One UI 8 / Android 16. It uses Compose, Room, and DataStore with compile/target SDK 36 and min SDK 31. Data stays on device; there is no backend. Version one excludes dorm-meal automation, login automation, multi-user distribution, server operation, and store release.

## D-002 · Domain vocabulary and seeded timetable — 2026-08-26 (user-approved implementation brief)

The zones are YEIN, MAIN, ONE_ROOM, and OUTSIDE. User-visible origins are `엔터관`, `본관`, and `원룸촌`; the official stadium-side wording is internal-only and must never appear on user surfaces or in display tests. The editable default term is 2026-08-24 through 2026-12-18 with the six approved courses and editable no-class dates.

## D-003 · Guidance, data sources, and live surfaces — 2026-08-26 (user-approved implementation brief)

`GuidanceEngine` produces the complete shared snapshot. Official DIMA shuttle and cafeteria pages plus public official Instagram embed assets are the only network sources. API 36 requests promoted ongoing notification treatment; ordinary silent ongoing notification is the fallback. A special-use foreground updater may run only while two live shuttle countdowns are shown.

## D-004 · Vertical TDD seams and acceptance boundary — 2026-08-26 (user-approved implementation brief)

Use red-green vertical slices at `GuidanceEngine`, `CampusDataRepository`, `ShuttleSource`, `MealSource`, `LocationResolver`, and `LiveSurfaceController`, with fakes only at those seams and literal expected values. Automated and emulator checks do not prove Samsung Now Bar behavior; live-device acceptance remains pending until observed on the user's phone.

## D-005 · Verified fixed campus coordinates supersede map setup — 2026-08-26 (user correction)

Do not use an in-app map picker. Seed verified fixed centers for 예인관, 본관, and 원룸촌 and show their coordinates/evidence in the location screen. Preserve any existing saved center or radius during upgrades; applying a verified center changes only the selected zone. The default radius remains 120 m and editable from 75 to 300 m.

## D-006 · Configurable Live Update presentation and revised MAIN geofence — 2026-08-27 (user-approved change; supersedes D-005 radius and MAIN center)

Offer two durable Live Update presentation choices: the API 36 status chip may show either the remaining countdown or the classroom, and the lock-screen notification may put either the course name or the classroom first. Course start/end editing uses Android's time-picker UI instead of free-form time text. The verified MAIN center is the OpenStreetMap centroid for 지성관 way 471488710 (`37.0590572`, `127.3580137`). New/default campus radii are 250 m, remain editable from 75 to 300 m, and the upgrade must preserve deliberately customized centers/radii while replacing the previous generated defaults.

## D-007 · Unfiltered schedule viewing and lock-aware classroom chip — 2026-08-27 (user-approved change)

The data screen shows every cached shuttle service day and origin/destination group without requiring a day or origin selection, deduplicating route variants only at the user-visible day/origin/destination/time level. It also shows the current Monday-through-Sunday meal week in full, marking dates without a validated menu as unavailable. One UI reuses `shortCriticalText` in the collapsed lock-screen Now Bar: when the classroom chip option is selected, use the classroom while unlocked and switch the same critical field to `시작까지/종료까지 N분` while locked. When the classroom is also the lock-screen first line, omit repeated course/classroom text from the lower line.

## D-008 · Complementary locked Now Bar detail — 2026-08-27 (user-approved clarification; supersedes D-007 lower-line detail)

When the compact pill is configured for classroom, the locked One UI lower line must contain the semantic remaining time plus the class item that is absent from the lock-screen first line. Course-first therefore shows `시작까지/종료까지 N분 · 강의실`; classroom-first shows `시작까지/종료까지 N분 · 수업명`. Never repeat the same course or classroom already shown on the first line. The unlocked compact pill remains the classroom.

## D-009 · Classroom chip is invariant across lock state — 2026-08-27 (user-approved correction; supersedes D-007/D-008 chip mutation)

When the compact status-pill option is classroom, Android's chip-only `shortCriticalText` must always be the classroom and must never be replaced with `시작까지/종료까지` or course detail during screen off/on, AOD, or keyguard transitions. Semantic remaining time and the complementary class item stay in the notification card body. On One UI, the collapsed lock-screen Now Bar may reuse the classroom chip text and omit the countdown; that OEM limitation is preferable to violating the explicitly selected status-pill value.

## D-009 · Navigation re-architecture, weekday meals, and simplified location settings — 2026-08-27 (user-approved change; supersedes D-007 meal week and tab organization)

The primary bottom navigation uses five dedicated pages: 홈 (Dashboard), 시간표 (Timetable), 셔틀 (Shuttle), 식단 (Meal), and 설정 (Settings). The weekly meal screen shows only Monday through Friday and excludes Saturday/Sunday. Shuttle timetable provides day-of-week selection chips, route grouping, first/last bus annotations, and upcoming departure highlights. Location setup and data/cache status are consolidated inside the Settings tab, removing the manual radius slider in favor of standard verified default radii.

## D-010 · Widget UX refinement, compact 2x1 shuttle, and multi-line vertical expansion — 2026-08-27 (user-approved change)

Home screen widgets remove manual refresh buttons to maximize clean information density. Meal widget uses '학생식당' label and multi-line item list (up to 8 lines) so all side dishes fit naturally without ellipses on 2x2 cells. Shuttle widget supports 2x1 minimum size, annotates remaining minutes with exact departure clock times (e.g. `328분(08:30)`), and expands vertically on >=2-row heights to display departures from other campus zones in addition to the current location. Widget touches deep-link directly to their respective Shuttle and Meal tabs.

## D-011 · Campus All-In-One summary widget (4x2) — 2026-08-27 (user-approved implementation)

Provide a large 4x2 home screen summary widget (`CampusSummaryWidgetProvider`) displaying the current campus zone, today's date, the upcoming/ongoing course schedule with start/end countdowns, real-time shuttle departures from the user's current origin, and today's student cafeteria lunch menu in an integrated 3-card layout. Follows standard Seam architecture via `CampusSummaryWidgetPlanner`.

## D-012 · M3 Expressive widget dark mode and truncated shuttle departure elimination — 2026-08-27 (user-approved change)

Support native Android system dark mode (Night Mode) across all app widgets using dedicated `values/colors.xml` and `values-night/colors.xml` M3 Expressive color tokens. For compact 2-column shuttle widgets, format the first and second upcoming departures across two distinct vertical lines with a header origin badge (`DIMA 셔틀 · 엔터관`), completely eliminating right-edge horizontal truncation.

## D-013 · Dashboard M3 Expressive redesign with direct card navigation — 2026-08-27 (user-approved change)

Redesign the main Dashboard screen with a primary Hero briefing card (date, origin location badge, ongoing/next class countdown, and class details), a 2-chip real-time shuttle card (fastest and next departures from current zone), and an integrated student cafeteria menu card. Each card directly navigates to its corresponding tab (Timetable, Shuttle, Meal) upon touch without redundant quick-action rows.

## D-014 · Official custom app icon and adaptive launcher assets — 2026-08-27 (user-approved change)

Generate and integrate a modern high-resolution custom vector monogram launcher icon for 'DIMA Now' (combining the stylized 'D' letter, campus clock/timer dial, compass navigation cue, and shuttle transit wave on deep midnight blue background with vibrant electric blue and gold accents). Provide full adaptive icon foreground and multi-dpi mipmap densities (mdpi through xxxhdpi, 512x512 playstore).

## D-015 · Shared minute board, selectable home base, polygon location, and pause mode — 2026-08-27 (user-approved implementation plan)

All active app and widget shuttle surfaces consume a KST `GuidanceEngine.ShuttleBoard`; remaining minutes use ceiling rounding and route variants collapse only within service-day/origin/destination/time. General MAIN status preserves both 엔터관 and 원룸촌 directions, while class-dependent return guidance and the compact shuttle widget use the selected YEIN or ONE_ROOM home base. In-class end countdowns are removed; after 30 minutes class context continues with return shuttles, then class payload is removed at scheduled end. ONE_ROOM is a versioned offline OSM polygon with a circular wake geofence and lower priority than YEIN/MAIN. A durable inclusive guidance-pause range augments, but never deletes, legacy individual no-class dates.

## D-016 · First/last emphasis, bottom source actions, and four pause choices — 2026-08-27 (user-approved change)

Every user-visible shuttle timetable slot identifies a first or last departure; last departures also use a distinct warning color. Shuttle and meal refresh/source action cards belong after their schedule/menu and source-status content. Pause mode offers only today, a selected range, the semester end, or until the user explicitly turns pause mode off; the former single-date picker is removed.

## D-017 · Background Live updater denial is a safe degradation — 2026-08-27 (engineering safety finding)

Android 16 may reject a `specialUse` foreground updater start while DIMA Now is backgrounded, including package-replacement recovery. The already-posted notification remains the live-surface fallback, and `LiveSurfaceController` must catch that platform denial instead of terminating the process. This does not weaken the rule that the updater runs only while two shuttle countdowns require it.

## D-018 · User-approved fixed polygons, test mode, compact source UI, and policy refresh — 2026-08-27 (user-approved change; supersedes D-005/D-006 editable location setup)

The APK has no user coordinate, radius, or map editing controls. The three user-approved OSM-based polygons in `dima-now-campus-zones-v2.geojson` are bundled as `CAMPUS_ZONES_V2_USER_2026_08_27`; a versioned install replaces only the three zone rows and preserves all unrelated Room/DataStore state. Android circular geofences wake the app, while final classification uses the offline polygon with YEIN/MAIN priority over ONE_ROOM. Durable test mode ignores GPS/geofence resolution until disabled and permits manual switching among YEIN, MAIN, ONE_ROOM, and OUTSIDE.

Dashboard source freshness is removed. Shuttle and meal pages keep only a top-right serialized refresh action; cache/source details move to Settings. Meal service status is KST-aware, course deletion requires confirmation, and the one-time Now Bar guide contains only the two required Samsung setup instructions. Automatic shuttle refresh is weekly by KST last-success, while meal refresh begins after the cached validated week ends and retries with backoff until a newer validated week is obtained.

## D-019 · Official stadium-stop presentation and origin-grouped shuttle screen — 2026-08-27 (user-approved correction; narrowly supersedes D-002)

Keep the location zone `MAIN` and its ordinary label `본관`, but when the official cached departure selected for display has source stop ID `stadium-stop`, show its boarding origin as `운동장`. This exception applies consistently to Shuttle screen next/full schedule, Live Update/Now Bar, the shuttle widget, and the all-in-one widget. The Shuttle screen groups destination cards under one origin header, marks the first stadium departure as `운동장 전환`, and keeps route variants internally. Home shows a compact `운행 종료` state when a valid cached service day has no remaining departures.

## D-020 · Invariant MAIN origin header and Dashboard shuttle capsule parity — 2026-08-27 (user-approved change)

The Shuttle screen origin group header for `CampusZoneId.MAIN` is always displayed as `본관`, keeping stadium boarding stop annotations within individual slot chips and countdown badges. The Home (Dashboard) screen renders real-time shuttle departures in identical capsule-shaped chip surfaces with last bus (`막차`) error-container emphasis and, when service has finished for the day, retains destination-specific capsule summary rows showing `운행 종료` and the final departure clock time.

## D-021 · Home shuttle prominent left destination badge and first/last bus schedule on termination — 2026-08-27 (user-approved change)

On the Home (Dashboard) screen shuttle card, the destination tag (e.g. `엔터관행`, `본관행`, `원룸촌행`) is always prominently positioned to the left of the capsule rows regardless of destination count. When a route's service has ended for the day, the right summary capsule explicitly presents both the first bus and last bus clock times (`첫차 HH:mm · 막차 HH:mm`) alongside the `운행 종료` label.

## D-022 · Material 3 Expressive frontend modernization and concise phrasing — 2026-08-27 (user-approved change)

Refactored all 5 screens (Dashboard, Timetable, Shuttle, Meal, Settings) to fully comply with Material 3 Expressive guidelines. Standardized on 8dp grid spacing (4dp, 8dp, 16dp, 24dp) and tonal container hierarchy (`surfaceContainerLow`, `surfaceContainer`, `surfaceContainerHigh`, `primaryContainer`). Replaced redundant helper copy and text buttons with compact iconography, highlighted `[오늘]` badges in Meal cards, and structured course summaries with vertical time capsules and M3 icon action buttons.

## D-023 · Top header bar separation, fixed shuttle chip height, and concise copy fine-tuning — 2026-08-27 (user-approved change)

1. **Dashboard Header Separation**: Separated current date (`M월 d일 E요일`) and campus location badge (`위치: OOO`) into a dedicated standalone top bar row outside the hero briefing card.
2. **Uniform Shuttle Chip Height**: Fixed all departure chips in the full schedule flow to a single line (`maxLines = 1`, `height = 30.dp`), eliminating tall 2-line rendering on first/last/stadium bus chips.
3. **Clean Professor Display**: Removed the redundant `'담당 '` prefix across Dashboard, Timetable, and widgets to display professor names cleanly.
4. **NowBar Dialog Lockscreen Guidance**: Refined lockscreen instruction to explicitly guide `"'잠긴 상태에서 알림 내용 표시' 옵션을 항상 표시로 변경해주세요."`
5. **Concise Meal Status**: Shortened meal service completion text across the app and widgets from `"오늘 운영이 끝났어요"` to `"운영 종료"`.

## D-024 · DIMA brand personal colors (#EC268F, #383738) new modern app logo — 2026-08-27 (user-approved change)

Adopted a modern, minimalist app logo integrating Dong-ah Institute of Media and Arts (DIMA)'s official brand colors: `#EC268F` (Magenta Pink) and `#383738` (Charcoal Slate Gray). The emblem combines the stylized capital letter 'D' with a dynamic real-time forward arrow motif, deployed across adaptive icon foregrounds, light background (`#F8F9FB`), standard mipmap drawables, and Play Store assets.

## D-025 · NowBar brand accent color, 15m in-class cutoff, unified top bar height, and M3 Button Groups — 2026-08-27 (user-approved change)

1. **NowBar & Notification Accent Color**: Configured `NotificationCompat.Builder.setColor(0xFFEC268F)` to use DIMA's official primary brand color (`#EC268F`) for system lockscreen and notification chips.
2. **15-Minute In-Class Guidance Cutoff**: Updated `GuidanceEngine` to automatically terminate and dismiss ongoing class live guidance 15 minutes after class start (`startsAt.plusMinutes(15)`), transitioning to `GuidancePhase.NONE`.
3. **Home Top Bar Title Replacement**: Removed the redundant "DIMA Now" large title and placed the current date and campus location badge directly in the standard 48dp top bar area.
4. **Unified Top Bar Height**: Standardized `ScreenColumn` across all 5 tabs (Home, Timetable, Shuttle, Meal, Settings) to an exact 48dp header area with uniform paddings.
5. **Light Mode Shuttle Card Color Fix**: Fixed an alpha-blending layout color bug on current-location cards by enforcing clean M3 `surfaceContainerLow` containers with distinct primary indicator badges.
6. **M3 Expressive Connected Button Group for Weekdays**: Replaced standard segmented buttons with an M3 Expressive Connected Button Group (`height = 44.dp`, 3dp micro-gap, 18dp outer/4dp inner corner radii, `primary` accent on selection) for high-emphasis Monday~Sunday selection.

## D-026 · M3 Expressive Motion Physics, AnimatedContent Transitions, and Touch Bounce — 2026-08-27 (user-approved change)

Adopted Material 3 Expressive motion system across the app:
1. **Directional Tab Transitions**: Implemented `AnimatedContent` for bottom navigation tabs with spring physics (`Spring.DampingRatioNoBouncy`, `Spring.StiffnessMediumLow`) providing forward/backward horizontal slide and fade transitions.
2. **Spring Color Morphing**: Applied `animateColorAsState(spring())` to the weekday Connected Button Group for organic tonal morphing.
3. **Smooth Schedule Crossfade**: Applied `AnimatedContent` with spring vertical slide and crossfade when switching weekday schedules in the Shuttle screen.
4. **Touch Scale Bounce Feedback**: Implemented `Modifier.expressiveBounceClick()` with bouncy spring physics (`0.97f` scale down on press, bouncy spring release) for interactive summary cards and timetable items.
5. **Real-time Live Pulse Animation**: Added `Modifier.pulseBreath()` for subtle alpha breathing effect on active badges (`수업 중`, `곧 출발`).

## D-027 · DIMA brand fixed color scheme and full-app Expressive animation pass — 2026-08-27 (user-requested M3 Expressive full revamp)

1. **Brand color scheme replaces dynamic color**: The in-app theme uses a fixed M3 tonal scheme seeded from DIMA's `#EC268F` (light primary `#B31169`, dark primary `#FFB1C8`, pink-neutral surface containers) in place of Android dynamic color, extending D-024's brand adoption from launcher/notification assets to every Compose surface. Widget `values/colors.xml` and `values-night/colors.xml` follow the same seed.
2. **Expressive theme API constraint**: `MaterialExpressiveTheme`, `MotionScheme`, and `LoadingIndicator` are internal/absent in the pinned material3 1.4.0 artifact (BOM 2026.03.01), so `DIMANowTheme` stays on `MaterialTheme` and spring physics live in `ui/motion/ExpressiveMotion.kt`.
3. **Animation system additions**: `Modifier.staggeredEntrance(index)` (60 ms-per-card spring rise-and-fade applied across Dashboard, Timetable header, Meal week, and Settings cards), `AnimatedCountText` (spring vertical slide swap for every per-minute countdown text on Dashboard and Shuttle capsules), weekday connected-button-group corner-radius shape morphing to a full pill on selection, bottom navigation selected-icon bouncy scale (1.15f), and round-cap refresh progress indicators. All user-visible strings and content descriptions are unchanged.










## D-028 - Shuttle day-switch performance, full timetable stagger, M3 Expressive toggle buttons, and dimmed past meal days - 2026-08-27 (user-requested change)

1. **Shuttle day-switch de-jank**: FlowTimeChips creates its LazyListState with the current-time initial index instead of a post-composition scrollToItem, and the per-route GuidanceEngine annotatedServiceDepartures/shuttleBoard calls plus day filtering and origin grouping are remember-memoized, so switching to today's weekday no longer forces mid-transition relayout of every chip row. Inside the day AnimatedContent, activeDay (not selectedDay) drives content.
2. **Timetable full stagger**: The entrance animation covers the term card, add button, weekday headers, every course card, and the pause card via a running index; staggeredEntrance caps its delay at index 8 (480 ms) so long lists do not trail.
3. **M3 Expressive toggle buttons**: Settings-tab selections (Live Update chip/lock-screen order, home base, test zone) use a shared ExpressiveToggleButton per the M3 Expressive button spec - 40dp S size, spring corner morph between full pill (unselected) and 12dp square (selected), surfaceContainer/onSurfaceVariant to primary/onPrimary color morph, disabled at 12%/38% onSurface. FilterChip remains only in the course editor weekday picker.
4. **Dimmed past meal days**: Weekly meal cards for dates before today render at 0.5 alpha on surfaceContainerLowest, keeping the today primaryContainer emphasis.

## D-029 - Home top-bar simplification, next-class preview, current-origin emphasis, today marker, and Settings reordering - 2026-08-27 (user-approved change; supersedes D-023/D-025 home top-bar date and D-018 settings layout details)

1. **Home top bar**: The date text is removed; the 48dp top bar keeps only the campus location badge, left-aligned and slightly enlarged (16dp icon, labelMedium).
2. **Empty hero preview**: When no classes remain today (or guidance is paused), the hero card adds a one-line preview of the next class day's first course - the next 7 days are scanned excluding noClassDates/guidancePause and outside-term dates; the label is `내일 첫 수업` for tomorrow, otherwise `X요일 첫 수업`, with `HH:mm`, course name, and room.
3. **Shuttle current-origin emphasis**: The current-location origin header uses titleLarge in primary with a pulsing `현재 위치` badge, and that group's route cards use surfaceContainer plus a solid 1dp primary border (no alpha blending per D-025).
4. **Today dot marker**: The weekday connected button group shows a 4dp dot under today's letter (onPrimary when selected, primary otherwise) so the today position stays visible from any selected day.
5. **Settings reorder**: Card order is home base, Live Update display (with the `나우바 설정 안내` button integrated at the card bottom via an optional LiveDisplaySettings callback), location/test mode (toggles only), system permission status (denied fine/background location rows get an inline `요청` FilledTonalButton wired to the permission launchers; locationMessage moves here), and data/sources last per D-016.

## D-030 - Official notice card, school service shortcuts, and LMS scraping deferral - 2026-08-28 (user-approved change; narrowly extends D-003 network sources)

The official notice board page (`https://www.dima.ac.kr/?p=111`) joins the allowed network sources. A `NoticeSource` (jsoup + DimaNoticeParser, Room `notices` table v3 with MIGRATION_2_3, `source_status` key `notice`) caches up to 10 newest notices, refreshed daily by KST via a NoticeRefreshWorker launch check plus 24h periodic work. The Home tab bottom shows a "학교 공지" card with the latest 3 titles (tap opens the notice URL; header opens the board) and a shortcut row with "DIMA Portal" (`https://portal.dima.ac.kr/`) and "LMS" (`https://lms.dima.ac.kr/lms/myLecture/doListView.dunet`) buttons that open the external browser. LMS/portal scraping is deferred: unauthenticated access redirects to login, the LMS enforces single concurrent login (its own notice warns of forced logouts), and D-001 excludes login automation. A gated `NoticeSourceLiveProbeTest` (runNoticeNetworkProbe=true) verifies the live pipeline on demand.

## D-031 - Widget course computation split from live guidance, compact labels, and shuttle capsule redesign - 2026-08-28 (user-approved change)

1. **All-in-one course slot**: `CampusSummaryWidgetPlanner` takes today's courses directly (provider filters weekday/no-class/pause/term) instead of the Now Bar `GuidanceSnapshot`, so an ongoing class shows `▶ 수업명` + `강의실 · HH:mm까지` until its end and a later class previews all day (`시작까지 N분 · 강의실` inside 60 minutes, otherwise `강의실 · HH:mm 시작`). The D-025 15-minute live-guidance cutoff is unchanged for Now Bar surfaces.
2. **Short empty state**: the summary widget empty course slot reads `오늘 수업 없음` / `등록된 일정 없음` so the half-width one-line TextView never ellipsizes.
3. **Meal widget header**: BEFORE_OPEN drops the `운영 전 · ` prefix in the 2x2 meal widget only (`11:00부터`); the shared `mealServiceStatus` labels and all in-app surfaces are unchanged.
4. **Shuttle widget capsules**: the shuttle widget renders app-home-style rows - destination tag pill plus departure capsules - via `RemoteViews.addView` of `widget_shuttle_row.xml`, with solid pre-blended `widget_capsule`/`widget_capsule_last` colors in values and values-night (no alpha blending per D-025) and last-bus emphasis by warning color (D-016). Capsule text restores D-010's compact `N분(HH:mm)` (plus `·운동장`/`·운동장 전환` per D-019); ended service shows `운행 종료·막차 HH:mm` per destination. Cells narrower than 190dp show only the soonest capsule so 3-digit night countdowns never truncate.

## D-032 - Compact shuttle widget summary line, GENERAL board for all zones, and destination suffix in the shuttle tab - 2026-08-28 (user-approved change; supersedes the D-015 home-base direction for the shuttle widget)

1. **Widget board**: the shuttle widget always uses the GENERAL `ShuttleBoard`, so at MAIN it shows both 엔터관행 and 원룸촌행 (the D-015 home-base RETURN direction no longer applies to this widget; Live Update surfaces are unchanged).
2. **Compact mode**: cells narrower than 190dp or with portrait height (OPTION_APPWIDGET_MAX_HEIGHT) under 140dp keep the `DIMA 셔틀 · <출발지>` header and summarize every destination on one accent-span text line - `<행선지>행 N분` per destination (`곧 출발`, `·운동장` per D-019; all-ended collapses to `운행 종료`). Wide+tall cells keep the D-031 capsule rows with `N분(HH:mm)` and last-bus warning color.
3. **Shuttle tab labels**: destination card titles in the in-app Shuttle screen read `<행선지>행` (e.g. `엔터관행`); origin group headers stay bare per D-020.

## D-033 - Status bar translucent vertical gradient scrim overlay - 2026-08-28 (user-approved change)

To eliminate content overlap and unreadable status icons when scrolling while maintaining edge-to-edge aesthetics, `ScreenColumn` overlays a top vertical gradient scrim (`height = statusBarTop + 20.dp`) in the current `surface` color transitioning from 0.95 alpha (top) -> 0.70 alpha (middle) -> 0.0 alpha (bottom). Status bar icons and time remain legible across light/dark themes while content gracefully fades out beneath the status bar.

## D-034 - Balanced runtime and install-build optimization - 2026-08-28 (user-approved implementation)

Preserve the current five-tab product, DIMA pink presentation, motion, widgets, Live Update payload rules, raw official cache, Room schema, fixed polygons, and settings while reducing duplicate work. An application-scoped `GuidanceRuntimeCoordinator` combines the schedule, cached shuttle data/index, effective zone, home base, and display options; refresh triggers are conflated and serialized without cancelling an in-flight notification/alarm side effect. `GuidanceEngine.prepareShuttleSchedule()` indexes service-day/origin/destination slots once per changed cache and keeps the List overload as the approved compatibility seam. Only Home, Shuttle, and Meal subscribe to the system-change-aware KST minute ticker while visible. Shuttle and all-in-one widgets share one minute alarm, with legacy provider alarms cancelled.

The local `optimized` build uses the same application ID, version, debug signing certificate, and package data as debug so `adb install -r` preserves personal state. It is non-debuggable, R8/resource-shrunk, deliberately non-obfuscated, contains generated Baseline/Startup Profiles, and has a Macrobenchmark-only `benchmark` module whose journey excludes network refresh and OCR. Physical Samsung rendering and battery acceptance remain observation-only evidence.

## D-035 - Deterministic app-shell back navigation - 2026-08-28 (user-requested bug fix)

Bottom tabs do not form an action history. Android Back first dismisses the currently open dialog through its existing `onDismissRequest`; from Timetable, Shuttle, Meal, or Settings it then returns directly to Dashboard, regardless of how many tabs were visited. Back from Dashboard is not intercepted and finishes `MainActivity` through Android's normal dispatcher. External Settings/browser Activities retain their own system Back behavior. This prevents revisiting a long sequence of earlier tab actions while preserving predictable dialog dismissal and launcher return.

## D-036 - Private GitHub source repository and direct-install release - 2026-08-28 (user-approved publication)

Convert the existing non-Git project into a local Git repository and publish the curated source to a private GitHub repository owned by the authenticated user. Exclude local toolchains, SDK paths, build/cache directories, device-derived databases and UI dumps, and APKs from source history. Publish the verified optimized APK only as a GitHub Release asset with its SHA-256 and direct-install signing limitation documented. Preserve all excluded local files in place.

## D-037 - Korean GitHub README - 2026-08-28 (user-requested documentation)

The repository README is written primarily in Korean for the intended user and maintainer. It must keep build, signing, Samsung system-control, device-evidence, data-source, privacy, and current large-screen limitations explicit rather than presenting unverified capabilities as complete.
