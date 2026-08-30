# PRODUCT TRUTH — DIMA Now

Evidence class: local source, automated test output, emulator observation, and explicitly identified physical-device observation are kept distinct.

## Implemented

### Android project bootstrap — verified — 2026-08-26

Evidence: `app/build.gradle.kts` declares compile/target 36 and min 31; generated baseline `gradlew.bat test` completed successfully on 2026-08-26.

Checked: 2026-08-26.

### Shared guidance and editable local state — verified — 2026-08-26

Evidence: JVM tests cover the 60-minute boundary, start/during/end transitions, Tuesday adjacency, no-class dates, final-class return, and reachable two-leg transfers. API 36 instrumented tests cover Room seeding/editing and exact Compose guidance strings. The emulator rendered the seeded dashboard without a process crash.

### Location, permissions, and recovery — source, emulator, and V2 Galaxy install verified — 2026-08-27

Evidence: `LocationResolver` JVM tests cover the approved YEIN/MAIN/ONE_ROOM polygons, school-over-ONE_ROOM priority, nearest-center overlap, test-mode GPS override, last-zone retention, and explicit all-geofence exit. API 36 Room tests prove `CAMPUS_ZONES_V2_USER_2026_08_27` installs 5/6/9-vertex polygons while preserving courses and guidance pause. The approved GeoJSON SHA-256 is `90A0D9E51129D2481B2945CC78800B9E34D24D42AE7B33536A0A06512D8EA094`; wake-up circles enclose the farthest vertex with approximately 107–110 m margin. After `adb install -r`, a read-only Galaxy Room check found the exact V2 centers, 5/6/9 vertices, 550/570/680 m wake radii, and retained user data (5 current courses, 605 shuttle rows, 5 meal days). Test mode visibly ignored GPS and selected MAIN, then disabling it restored the GPS-derived YEIN zone. Physical-site dwell/exit/overlap transitions remain unobserved.

### Official shuttle source and widget — live-source verified — 2026-08-26

Evidence: the physical Galaxy performed one official-source refresh, visibly entered a serialized loading state, and retained 605 weekly-expanded rows/575 direction-aware service-day/origin/destination/time slots with a new KST success time. The older 505 count ignored destination and is not used by the current projection. Parser fixtures cover A/B/C table shapes. Guidance tests cover direction filtering, duplicate user slots, and reachable transfers. The classic RemoteViews widget renders only whole remaining minutes, schedules a cached minute-boundary recomputation, promotes passed departures, and has no countdown Chronometer.

### Live guidance surface — source, emulator, and Galaxy rendering verified — 2026-08-27

Evidence: the Galaxy reported manifest permission, promotion request, promotable characteristics, LOW (not MIN) channel, and `canPostPromotedNotifications=true`. The unavailable Samsung promoted-settings Activity safely fell back to `AppNotificationSettingsActivity` without an exception. A gated physical-device test notification was visibly rendered as a status-bar live countdown pill, an AOD card, and a lock-screen Now Bar item on the SM-S918N when Developer options > all-app live information was enabled. With only that setting disabled, the same ongoing notification remained posted but DIMA Now was absent from the lock-screen Now Bar and the promoted-notification app-op recorded a rejection; Developer options and USB debugging remained enabled throughout and the setting was restored after the comparison. DIMA Now remained absent from Samsung's normal Live notifications app list, so ordinary-list enrollment is Samsung-controlled. API 36 tests now verify selectable countdown/classroom compact content, selectable course/classroom first-line order, and exact `시작까지`/`종료까지` expanded wording. The physical permission screen exposed both option groups, but their final Samsung rendering during production guidance is still pending.

### Official cafeteria source, on-device OCR, validation, and widget — live-source and Galaxy verified — 2026-08-27

Evidence: the official DIMA homepage exposes only five recent Instagram posts, so the 2026-08-24 cafeteria post had rolled out of that discovery window by 2026-08-27. A red-green parser fixture now covers the official profile's login-free public feed metadata fallback while retaining the public post embed as the carousel/image source. On the SM-S918N, one serialized refresh visibly entered `새로고침 중`, found shortcode `DcaCs3yE6So`, ran Korean ML Kit OCR locally, validated 2026-08-24 through 2026-08-28, and stored five meal days. The UI showed the five menus, a KST last-success instant, and `캐시: 8/24~8/30`; these persisted across process restart. The displayed five dates, hours, and every menu line were visually compared with the official second carousel slide and matched. Instrumented cache-protection testing confirms an invalid candidate does not replace the last valid week.

### UX and cache improvements — test and Galaxy verified — 2026-08-26

Evidence: the physical app showed Korean weekday grouping, human-readable KST freshness, weekly meal range semantics, truthful shuttle cache labels, and deduplicated first/last annotations. Compose testing covers loading/debounce and exact display strings. Automatic guidance ignores a previously stored false value and no user toggle remains. Adaptive and monochrome app icons and compact meal widget resources passed Android resource compilation/lint; launcher add/resize remains manual.

### Timetable time picker and Live presentation preferences — test and Galaxy verified — 2026-08-27

Evidence: red-green JVM/API 36 tests verify persisted Live presentation choices and exact notification title/detail/compact payloads. Compose tests verify both settings groups and the absence of free-form `HH:mm` fields. On the physical Galaxy, `수업 추가` displayed `시작 10:00` and `종료 11:00`; tapping start opened Android's `시작 시간 선택` 24-hour picker with Cancel/Confirm controls. The dialog was dismissed without saving, and the process/activity remained healthy.

### Complete shuttle week and current meal week — test and Galaxy verified — 2026-08-27

Evidence: Compose tests feed literal `ShuttleSource` and `MealSource` data through the public data screen. They verify all service days/origins are present without filter controls, user-visible route-time duplicates collapse, first/last annotations remain, only the current Monday-through-Sunday meal week is shown, and missing dates are truthful. On the physical Galaxy, the retained official cache rendered 605 weekly-expanded rows/575 direction-aware user slots as weekday route groups; Monday included `엔터관 → 본관` and `본관 → 엔터관` with absolute times and first/last annotations. The same screen showed the validated 2026-08-24–2026-08-30 meal week and weekend no-menu cards. Captured UI contained no forbidden stadium-side label.

### Lock-invariant classroom Live Update — Galaxy transition verified — 2026-08-27

Evidence: the pre-fix physical loop showed `shortCriticalText=덕성관 510-1` before screen off and `shortCriticalText=시작까지 53분 · 덕성관 510-1` after screen ON while the keyguard was showing. Android documents this field as exclusively for the compact chip, so independent red-green `LiveSurfaceController` cases now require `덕성관 402` for both course-first and classroom-first orders even when `deviceLocked=true`. After `adb install -r`, five consecutive SM-S918N screen off/on cycles all retained `shortCriticalText=덕성관 510-1`. The visible AOD/lock-screen card showed `13:00 · 프리젠테이션영어` and `덕성관 510-1`; the notification body separately retained `시작까지 42분 · 덕성관 510-1`. One UI's collapsed Now Bar reuse of chip text means it can omit the countdown, but it no longer corrupts the configured classroom pill.

### Automated acceptance — verified — 2026-08-27

The stale `DataSourceScreenTest` mismatch has been replaced with current Shuttle/Meal public-screen tests. The latest full evidence is recorded in “Shared minute guidance follow-up” below.

## Pending live-device acceptance

- Remaining production presentation checks: normal unlocked classroom-pill fit/truncation and the countdown-pill option. Classroom persistence across AOD/keyguard screen cycles is verified; One UI's collapsed card intentionally receives only the classroom chip value.
- Physical geofence transitions at 예인관, 본관, and 원룸촌, including dwell and arrival cancellation.
- Two-countdown accuracy within one minute across real trips and final-class return behavior.
- One UI Home add/resize verification for both widgets.
- One-day battery observation. The official meal slide comparison is complete; only the One UI widget layout remains pending under the widget item above.

### Shared minute guidance follow-up — automated and Galaxy smoke verified — 2026-08-27

Evidence: final regression has 68 passing JVM tests. API 36 emulator instrumentation reports 41 cases with 39 executed passes and two explicitly gated Samsung visual-notification probes skipped; Android-test compilation, `lintDebug`, and `assembleDebug` pass. The public tests cover common ceil-minute calculation, route-variant deduplication without losing simultaneous destinations, home-base return selection, the class +30-minute transition, inclusive and until-disabled pause ranges, fixed polygon priority, test-mode GPS override, widget minute-boundary planning, current five-tab UI, source-screen cleanup, policy refresh, both Now Bar settings actions, and non-duplicated shuttle-only notification payloads.

The final APK SHA-256 is `358917BC66A9AEE77F3DC1A2C1D1A854093BC2828E24125236F9CD13AE171F38`. It was installed with `adb install -r` on the physical SM-S918N/API 36, retained the user's current courses and source caches, and cold-launched without an immediate FATAL, ANR, or Room migration failure. The Now Bar guide's two setting actions were then exercised on the Galaxy: both retained PID 14037 and opened the corresponding Samsung Settings Activity without a FATAL exception. A separately approved synthetic promotion probe passed: Samsung set `FLAG_PROMOTED_ONGOING`, the unlocked screen visibly showed the blue countdown pill, and the lock screen visibly showed a DIMA Now bar. The expanded synthetic item showed only the app name, so production class-detail and AOD layout remain pending. The synthetic notification and test-only package were removed afterward.

### First/last, bottom actions, pause choices, and background-start safety — test and Galaxy verified — 2026-08-27

Evidence: independent red-green Compose tests require `08:10 (첫차)` and `09:10 (막차)`, verify each data-management card is below both content and source status, and expose only the four approved pause choices. JVM and Room tests prove an until-disabled pause suppresses later guidance and round-trips without a schema migration. On the physical SM-S918N, the Thursday timetable visibly rendered `17:45 (막차)` in red, source status before `셔틀 데이터 관리`, the five weekday menus before `식단 데이터 관리`, and the exact four-choice pause dialog. The current UI reports 605 raw weekly-expanded rows and 575 direction-aware service-day/origin/destination/time slots.

During the first full physical run, the crash buffer exposed `ForegroundServiceStartNotAllowedException` when a package-replacement refresh tried to start `LiveMinuteUpdateService` from the background. A public `LiveSurfaceController` red-green case now requires safe degradation. After the fix, 55 JVM tests, `lintDebug`, both debug APK assemblies, and all 34 API 36 instrumented cases passed; the two Samsung visual probes remained assumption-gated in the full runner. A clean replace install and cold launch left PID 30897 alive, retained the Room database and DataStore files, and produced an empty crash buffer. APK SHA-256 is `FEAAD39C4B40467075609BF8983FBC48B5C86E590A21BFF3690F5089B6485A21`.

### Fixed polygons, test mode, source-screen cleanup, and automatic refresh policy — emulator and Galaxy verified — 2026-08-27

Evidence: the user-supplied final GeoJSON is preserved under `artifacts/zone-boundary-capture`. Three independent `LocationResolver` red-green slices verify the expanded approved polygon interiors, and API 36 Room tests verify atomic zone-row replacement without losing timetable or pause data. Compose tests verify the absence of dashboard/source footers, the top-right debounced refresh controls, KST source details in Settings, meal operating-state copy, course-delete confirmation, and the two-line Now Bar setup guide. JVM policy tests verify current-week shuttle suppression, previous-week refresh, meal-expiry refresh, and current-week meal suppression. Final verification is 68 passing JVM tests plus 39 API 36 emulator cases with 37 passes and the two Samsung-only cases assumption-skipped, with Android-test compilation, lint, and APK assembly green. The final APK SHA-256 is `BE83D5305C6FDFFB794036FE0D2EF9B8A8693E049E76898EA029187D2301164B`. The V2 build was replace-installed on the authorized SM-S918N, retained app data, passed UI/test-mode/deletion-guide smoke checks, and produced no immediate FATAL or ANR. A separately gated physical promotion test received `FLAG_PROMOTED_ONGOING` and visibly rendered the unlocked pill and lock-screen DIMA Now bar; production class detail and AOD layout remain pending.

### Balanced runtime optimization and optimized install - automated, emulator, and Galaxy smoke verified - 2026-08-28

Evidence: 88 JVM tests pass; `compileDebugAndroidTestKotlin`, `lintDebug`, `assembleDebug`, and `assembleOptimized` pass; API 36 instrumentation reports 47 cases with 44 passes and three intentional Samsung-physical/live-network skips. Unit and integration tests cover system-time ticker resynchronization, coalesced serialized runtime refresh, stale guidance-alarm cancellation, indexed-versus-List shuttle equivalence, one shared widget minute plan, visible-route ticker selection, and KST meal semantics. Compose compiler metrics for the optimized variant report strong skipping enabled, 159 skippable composables, and only the expected minute/animation state helpers as non-skippable.

The generated Baseline/Startup Profile journey covers cold launch, all five tabs, and shuttle scrolling without network/OCR. On the same API 36 emulator, five-iteration Macrobenchmark medians changed from 13,877.2 ms compilation-none cold start to 7,617.3 ms with Baseline Profile (45.1% lower); frame CPU P50 changed 266.5 to 193.4 ms and frame overrun P50 472.0 to 302.1 ms. These are emulator comparisons, not physical latency claims. The final optimized APK is 48,659,897 bytes versus debug 114,656,465 bytes (57.6% smaller), contains `assets/dexopt/baseline.prof`/`.profm`, and has SHA-256 `E2515F6BAE40BC49C51034BB45CD6ED03BA6DE10613A4FE09E82015A652BBA25`.

An exact-serial `adb install -r` on SM-S918N/API 36 succeeded using the same signing certificate. Cold launch reported 324 ms and PID 6826 remained alive. Read-only UI hierarchy checks opened all five tabs and proved retained course, 605-row shuttle projection, validated current meal, notices, and effective YEIN location. `dumpsys alarm` showed one active `UPDATE_ALL_WIDGETS_MINUTE` alarm plus the separate next guidance boundary; no legacy provider minute alarm was active. No immediate DIMA Now FATAL/ANR was found. This smoke did not re-certify OEM-controlled Now Bar/AOD rendering, physical geofence transitions, or one-day battery behavior.

### Deterministic Back navigation - emulator and Galaxy verified - 2026-08-28

Evidence: API 36 tests navigate Shuttle -> Meal -> Settings and require one Back to select Dashboard, then one Back to destroy `MainActivity`; another case requires Back to dismiss the course editor while Timetable remains selected, followed by Back to Dashboard. The full API 36 suite has 46 passes and three intentional Samsung/network skips. After `adb install -r` on SM-S918N, ADB/UI-hierarchy observation proved Settings -> Back selected Home and a second Back resumed One UI Home; Timetable -> course editor -> Back dismissed only the dialog, then Back selected Home. No DIMA Now FATAL/ANR appeared. The current optimized APK SHA-256 is `693CD96CE71BCDEE341254CC530691B9FECA11533D7915B96FF97A4D20F8C816`.

### Private GitHub source and v1.0 direct-install release - published and verified - 2026-08-28

Evidence: the authenticated GitHub account `winter1l` owns `winter1l/DimaNow`; the GitHub connector returned `visibility=private`, full admin/push permission, default branch `main`, and the published `README.md` content. Local caches, toolchains, SDK paths, device-derived databases/UI dumps, and APK build folders are ignored and were not included in source history. A fresh `testDebugUnitTest lintDebug assembleOptimized` run passed with 88/88 JVM tests. GitHub Release `v1.0` is published (not draft or prerelease) with `DIMA-Now-v1.0-optimized-20260828.apk`, 48,676,281 bytes, and GitHub's asset digest `sha256:78d39e715dcd42d6c322d5938dc07d78b399423fa94536eb00c7a8b91474a9ca`. `apksigner` verified APK Signature Scheme v2; the signer remains the Android debug certificate, so this is a personal direct-install artifact rather than Play Store production signing.

### Public static data and Gemini meal OCR - hosted Actions and Pages verified - 2026-08-30

Evidence: commit `71a9478` replaced the GitHub-hosted Tesseract meal step with one schema-constrained `gemini-3.5-flash-lite` image request while retaining the published `MealPayload` v1 contract and last-good cache protection. The local `.env` is ignored, `.env.example` contains only the variable name, and the repository Actions secret list confirms `GEMINI_API_KEY` exists. Local verification passed 79 app JVM tests, 17 pipeline tests, Android-test compilation, `lintDebug`, `assembleDebug`, and `assembleOptimized`. GitHub validation run `33316317176` and the manually dispatched meal publish run `33316410514` completed successfully; its log contained no Google API-key-shaped value. The deployed Pages manifest reports meal revision 6 and `READY`, published at `2026-08-30T14:17:03.363106625Z`. Its content-addressed payload SHA-256 `6c8584781f16ce42562d27c319e6c8c6e51982e4fcc75ef9bcfd5ea15e5ffb1c` matches the downloaded bytes and contains five weekday menus for `2026-08-24` through `2026-08-28`, with week range `2026-08-24` through `2026-08-30`.

### Outgoing-only tab fade - API 36 emulator verified - 2026-08-31

Evidence: commit `23b0296` restores a 180 ms fade only to the outgoing tab while keeping the incoming tab fully opaque and above it. Card-level staggered entrance remains translation-only, so its elevation surface is not faded. On the API 36 `medium_phone` emulator, the focused `CardShadowTransitionTest` and `AppBackNavigationTest` run completed 3/3 cases with zero failures.

### User-submitted dormitory meal pipeline - automated and GitHub configuration verified - 2026-08-31

Evidence: the Meal screen separates `본관 학생식당` and `기숙사`; a missing target week exposes only the concise `사진 올리기` path with system Photo Picker and camera capture. Submission preflight refreshes the static manifest, serializes duplicate checks, caps JPEG/PNG/WebP uploads at 15 MiB, and obtains an expiring GitHub user token through Device Flow stored with Android Keystore encryption. GitHub App `DIMA Now Meal Upload` is installed only on `winter1l/DimaNow`; the verified permissions page shows `Contents: Read and write` plus GitHub-mandatory `Metadata: Read-only`, Device Flow enabled, no webhook, and no other selected permission. The public Client ID is embedded; no client secret, private key, user token, or Gemini key is stored in the app or repository.

The Actions pipeline makes two schema-constrained `gemini-3.5-flash-lite` calls: HIGH-thinking dormitory/full-table validation with the exact approved Korean keys, followed by separate structured OCR. Duplicate and stale-week candidates publish a status without replacing last-good Pages data. App unit tests report 101/101 passing; pipeline tests report 31 passing plus one explicitly gated live probe; final Android-test compilation, `lintDebug`, debug/optimized assemblies, and the API 36 suite pass. The final emulator suite reports 73 cases, 70 passes, and three intentional Samsung-physical/live-network skips. Live Actions rejection, release publication, and Galaxy installation are recorded separately once completed.

## Permanently excluded

- Everytime scraping/login automation, a separately operated backend/server, and store release. Dormitory menus are accepted only from explicit user photo submissions through the verified GitHub/Gemini pipeline.
