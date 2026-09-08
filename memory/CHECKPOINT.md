# CHECKPOINT - 2026-08-31

Current state: v1.4 is implemented and verified locally/on the API 36 emulator. It restores the original directional tab transition with a short fade on both pages, schedules main-cafeteria discovery/OCR once every Monday at 10:15 KST, and allows anonymous dormitory meal photo submissions through a server-held Cloudflare Worker gateway.

Hosted path: Worker `dima-now-meal-upload.dima-now-chc01.workers.dev/v1/dormitory-meals`; KV namespace `dima-now-meal-upload-rate-limit`; GitHub App installation scope is `winter1l/DimaNow` and writes only new `dorm-submissions/<uuid>.<image-ext>` paths. Worker source uses image signature/15 MiB validation and a ten-minute hashed-address limiter. `GITHUB_APP_PRIVATE_KEY` and `RATE_LIMIT_SALT` exist only as Cloudflare Secrets. Do not print, commit, or recreate the downloaded GitHub App private key.

Live submission evidence: submitted photo ID `2f4363e1-d2c9-4221-b44a-04a2c0ddec8c` yielded GitHub Actions run `33370821621` success and public status `REJECTED: 이번 주 기숙사 식단표가 아니에요`, correctly because the photo was for 2026-08-24 through 2026-08-28.

Verification: Worker Node tests 3/3 pass. `testDebugUnitTest`, `:data-pipeline:test`, `compileDebugAndroidTestKotlin`, `lintDebug`, `assembleDebug`, and `assembleOptimized` pass. API 36 emulator has 73 tests completed, no failures, with 3 intentional skips. `app-optimized.apk` SHA-256 is `E683D36203BA77037A7F0431D38157A791BAC3C7BBED15936966D16B40F7E5DA`; release copy target is `dist/DIMA-Now-v1.4-optimized.apk`.

Publication: source commit `8452383` is on `main`; GitHub Release v1.4 is public with a GitHub asset digest matching the local APK, and its validation/data-publish runs `33372618812`/`33372618799` both succeeded.

Galaxy smoke: `R3CW203NFSL` has v1.4 (5) via successful `adb install -r`; `MainActivity` resumed with PID 26578 and no post-launch FATAL/ANR. A visual Home screenshot is at ignored local artifact `artifacts/dimanow-v14-galaxy-smoke.png`.

Next action: observe an actual active class/Live Update on the Galaxy before claiming Samsung Now Bar/AOD acceptance. Live anonymous photo submission from the phone, real geofence behavior, widget update, and battery remain separate manual checks.

## Update - 2026-08-31 (D-044 frontend reunification)

Current state: the approved D-044 frontend pass is implemented and installed (optimized replace-install) on SM-S918N, uncommitted on top of `8452383` together with the prior session's uncommitted D-043 LMS tree. Tabs are 홈·시간표·셔틀·식단·수업; the 수업 tab now uses ScreenColumn + full Expressive motion with split loading/error/empty states; LMS login/detail are full-screen with hidden bottom navigation; TARGET_PAGE deep links are nonce events with a DASHBOARD branch (verified live with flag 0x14000000 from the Meal tab); dormitory meals gained past-day dim + 오늘 badge; shuttle regained entrances and per-pane today logic; the meal venue selector persists manual choice; dead code (GuidanceCard, hasSavedOrigin, three orphaned workers) was removed. Verification: JVM units, lint, debug+optimized assembly green; phone instrumented runner `OK (72)`; cold launch alive with empty crash buffer; graphify graph updated to 1,569 nodes. Optimized APK SHA-256 `167B213A2C7E1C0C2546434F4BF09DFD0CD30C77AF6D2B5D36F4C94630C0D674`.

Next action: the combined D-043+D-044 tree is committed as `28ba622` on main (not pushed, no release). Version 1.4 (5) is still shared by three distinct artifacts, so bump versionCode/versionName before the next release per the D-040 update rule. Live authenticated LMS parsing remains unverified (no credentials entered).

## Update - 2026-09-01 (D-045 LMS complete history)

Current state: complete current-term LMS board history and app-local read/unread filters are implemented. The authenticated WebView saves only a structured rendered course catalog, then the source loads bounded pagination for notice, material, and assignment boards. The Classes screen uses a keyed lazy list and preserves read state across refresh. A missing rendered catalog no longer leaves sync stuck.

Verification: app JVM 120/120; pipeline 32 pass plus one gated live probe skip; lint, test compilation, debug/optimized assembly green; API 36 instrumentation 76 pass plus three intentional skips. Final optimized APK is 5,138,489 bytes, SHA-256 `935EF62BF694B0AEB87A63FD8814D497CCD7863CECC00F62D05896AB7BA756BC`. It was replace-installed on `R3CW203NFSL`; v1.4 (5) launched with PID 29773 and no immediate FATAL/ANR.

Next action: unlock the Galaxy and keep DIMA Now's 수업 tab foregrounded so the saved portal login can complete. Then tap 새로고침 once and audit only course/item/read counts. The pre-final cache is schema v3 but still contains zero courses and one unread To-Do assignment, so live full-history acceptance remains pending. Bump versionCode/versionName before publishing any release.

## Update - 2026-09-01 (D-046 LMS `전체 학습`)

Current state: the Classes list now mirrors the official `?to_do_type=all` rows instead of enumerating per-course boards. Multi-row container parsing, all nine LMS kinds, local read preservation, internal sanitized detail, matching list-detail resolution, and same-host attachment saving are implemented without a Room migration. No LMS item opens an external browser.

Verification: JVM 125/125, data-pipeline 32 pass plus one gated skip, Android-test compilation, lint, debug/optimized builds, and the API 36 emulator suite are green. Emulator instrumentation executed 83 cases: 80 passed and three intended physical/live cases skipped. The optimized APK is 5,138,489 bytes, SHA-256 `E7EC60448EEB271C768BAC4AFA0279095EB3995B5F2388437F60410832F42C60`. The Galaxy was absent from ADB at the install gate.

Next action: reconnect and authorize the Galaxy, then `adb install -r` the optimized APK, open 수업, refresh once, and verify only aggregate item/type/read counts plus one internal detail and one attachment save. Do not log or export private LMS content. Version 1.4 (5) still needs a version bump before any release publication.
# Update - 2026-09-01 (D-047 LMS today agenda)

Current state: v1.5 (6) implements the approved LMS Today/All experience. The official `전체 학습` list remains authoritative; rendered completed/incomplete navigation adds conservative status, Room v4 adds completion/change columns without clearing prior private data, NEW/UPDATED badges clear atomically on open, and detail opening revalidates attachments with explicit changed/cached labels. LMS access remains foreground-only and read-only.

Verification: app JVM 130/130; pipeline 33 with one gated skip; Android-test compilation, lint, debug and optimized assembly green. API 36 instrumentation finished 89 cases (86 pass, three intentional skips). `dist/DIMA-Now-v1.5-optimized.apk` is 5,171,257 bytes, SHA-256 `565806F95ECE294072AE7F5D5F101BBAE7659A93924A6E6B6B00740D5FDAA94A`. It replace-installed on `emulator-5554`; MainActivity stayed resumed as PID 18648 with no captured FATAL/ANR.

Pending: no physical Galaxy was connected. After it reconnects, verify its exact serial, `adb install -r` this v1.5 artifact, open the saved LMS account, refresh once, and visually verify Today/All, completion badges, one internal detail, and one attachment download. Do not export private LMS content. GitHub Release publication was not part of this implementation.

# Update - 2026-09-01 (D-048 dormitory meal transient retry)

Physical install is complete: optimized v1.5 (6) is on `R3CW203NFSL`, cold-launched with PID 9460 and no immediate FATAL/ANR. The 2026-09-01 dormitory upload reached GitHub but Gemini validation returned HTTP 503, producing submission `ERROR` in Actions run `33488270773`.

The client now retries only 429/5xx up to three attempts. The exact 503-then-success regression test, full pipeline suite, three Worker tests, app JVM tests, Android-test compilation, lint, and optimized build pass. Commit `1517e18` is on `main`; original run `33488270773` attempt 2 published the same photo successfully. Public status is `PUBLISHED`, `dorm_meal` revision 1 is READY for 2026-08-31 through 2026-09-06, and one Galaxy refresh rendered ten meal sections while removing the empty/upload UI. No GitHub Release, branch, or worktree was created.

# Update - 2026-09-01 (D-049 native LMS detail)

Current state: LMS items now open a native Compose detail body with Android document-save attachment actions. The authenticated LMS landing shell is rejected instead of being shown or cached as an article. Course-session POST redirects forward response cookies, and the official portal WebView is retained only as an invisible SSO engine behind native login progress.

Verification: full app JVM, data-pipeline, Android-test compilation, lint, optimized assembly, and four focused API 36 tests on physical `R3CW203NFSL` pass. Optimized v1.5 (6) SHA-256 is `5CA13B9194D44FAD8AB2D53A90C8E9A05FC6BA7726F7B03C1FDF6698BC6E244C`; it replace-installed, launched in 485 ms, remained resumed, and had no immediate FATAL/ANR. Final authenticated visual acceptance is pending one fresh login because an earlier Gradle-connected test reset private app data.

# Update - 2026-09-01 (D-050 LMS single-session recovery)

Current state: the live `로그인이 필요합니다` loop is fixed. If direct HTTP receives a login or landing shell but the app-owned WebView session is authenticated, the official LMS form navigation runs invisibly and only sanitized HTML reaches the native detail screen. Cards now expose Android's standard click action without losing bounce motion, and the saved password field reports a password keyboard type.

Verification: Galaxy `R3CW203NFSL` opened `프로툴 사전진단` as native selectable content with no visible WebView; PID 13928 stayed alive and no FATAL/ANR was captured. Focused physical instrumentation passed 24/24. App JVM, pipeline, Android-test compilation, lint, and optimized assembly are green. Do not open a second LMS browser session during app acceptance because the LMS invalidates the previous session.

# Update - 2026-09-01 (D-051 LMS notice/material and content routing)

Current state: official notices and materials now parse their verified `table_view_basic` bodies into native Compose and expose `fncFileDown` files through authenticated document-save actions. Repeated official content IDs no longer collapse distinct rows. CONTENT is correctly treated as an LMS course/player flow and opens an explicit same-session in-app official course page rather than a fabricated native article or a separate external browser session.

Verification: focused parser tests and `RoomLmsSourceTest` pass on Galaxy `R3CW203NFSL`; the full JVM, data-pipeline, Android-test compilation, lint, and optimized build completed successfully. Optimized v1.5 (6), SHA-256 `AB827298CB5B40DB6218DB12499FACF85684A13E7D20BECB35E619A6870C49BC`, was replace-installed on that exact device; `MainActivity` remained top-resumed as PID 31212 and the post-launch diagnostic slice contained no DIMA Now FATAL or ANR. Final authenticated visual acceptance is waiting for the side-browser LMS session to be logged out because the official service allows one active session and currently returns portal error 3045 to the app.

# Update - 2026-09-02 (D-052/D-053 LMS final hardening and video status)

Current state: NOTICE, MATERIAL, and ASSIGNMENT use verified native detail containers and cache-first attachment downloads. Portal credential redirects expire the session without erasing the last-good list. Octet-stream responses that are actually HTML are rejected. The 3045 path accepts only the exact official URL plus the verified hidden-only POST/portal-root redirect shape; visible controls and extra authentication inputs terminate safely. CONTENT remains catalog-first and shows only server-provided `수강 완료` or `미수강`; player rendering is secondary and not accepted.

Verification: app JVM 179/179 and data-pipeline 33 pass plus one gated skip; Android-test compilation, lint, debug and optimized assembly pass. API 36 emulator instrumentation finished 140 cases with 137 passes and three intentional Samsung/live-network skips. Optimized v1.5 (6) is 5,351,481 bytes, SHA-256 `09A6F2D86934950A9DF15D9F4FB2F3EB61671DEC1D250C9856AD638A3B6E116D`, and was installed with explicit serial `R3CW203NFSL`; cold launch took 362 ms, PID 17965 remained resumed, and no immediate FATAL/ANR was captured. The package was absent before installation, so this was effectively a fresh install and no prior private app data could be retained. Live LMS refresh/status acceptance remains pending while the phone is locked.

# Update - 2026-09-02 (D-054 portrait-only tablet surface)

Current state: `MainActivity` is portrait-only and declares the Android 16 large-screen compatibility property required for targetSdk 36 tablets. A physical rotation test on `SM-X710` serial `R54W703V2TZ` held the activity in portrait while the display was rotated 90 degrees, and restored the original `lock 0` rotation state.

Verification: app JVM 179/179, data-pipeline 33 pass plus one gated skip, Android-test compilation, lint, debug and optimized assembly pass. Final API 36 instrumentation completed 143 cases with three intentional Samsung/live-network skips and no failures. Optimized v1.5 (6), SHA-256 `E68A3898E616393EFD51272532D0F16A382FCDF20CBFA80CF2F68F0CF75332C8`, is freshly installed on the tablet; cold launch took 297 ms and PID 9028 had no immediate FATAL/ANR. Unlock is still required for visual five-tab acceptance.

# Update - 2026-09-02 (D-054 unlocked tablet acceptance)

Current state: the optimized v1.5 install on exact `SM-X710` serial `R54W703V2TZ` has now passed real five-tab navigation and visual portrait smoke. Home, Timetable, Shuttle, Meal, and Classes each rendered the expected installed state; the new tablet has no saved LMS account, so Classes stopped at the native login surface and no credential was entered.

Verification: Settings back returned to Meal, the next back returned directly to Home, and Home back returned to Samsung launcher without replaying older tab actions. The app stayed alive as PID 9028. A 90-degree rotation request still left the app at 1600 x 2560/rotation 0 and the original `wm user-rotation lock 0` was restored. Installed package is v1.5 (6), and no DIMA Now FATAL/ANR was found. Remaining device-specific acceptance is limited to features not exercised on this tablet: authenticated LMS/download, widgets, real geofence transitions, Samsung Now Bar, lock screen, and AOD.

# Update - 2026-09-02 (D-056 first-run onboarding and frontend reunification)

Current state: on branch `codex/wip`, a fresh install now opens a full-screen permission onboarding instead of two stacked forced dialogs. It explains and requests notifications, precise location, background location (staged after precise), and exact alarm, then takes the home-base choice; every permission step is skippable. Home drops the duplicated shuttle prose and the copy-pasted class-preview block, lists all missed-arrival destinations, and stops allocating a GuidanceEngine per destination. The dormitory meal week shows one explanatory empty card with an in-body upload CTA instead of five identical empty cards, and keeps a progress card for the whole submission poll. The Classes tab shares one full-screen pane shell across its four panes, cascades item cards, applies filters in agenda mode, keeps an inline refresh-error banner over cached items, and tracks downloads per attachment.

Verification: app JVM 189/189 (including three new `OnboardingStepsTest` cases), `compileDebugAndroidTestKotlin`, `lintDebug`, and `assembleDebug` pass; the API 36 emulator instrumentation finished `OK (144 tests)` with no failures. Emulator `medium_phone` API 36 only - no physical device was attached this session. A fresh install walked the whole onboarding on device: the system notification and location dialogs appeared, `ACCESS_FINE_LOCATION` ended `granted=true`, the background-location step changed its copy only after precise location was granted, and finishing home-base selection entered the Dashboard with an empty crash buffer.

Next action: nothing is committed - the work sits uncommitted on `codex/wip` on top of `8bb1e44`. Re-run the full emulator instrumentation after any further edits, and note that physical Galaxy acceptance (Now Bar, widgets, geofence, authenticated LMS) is untouched by this session and still stands where D-054/D-055 left it.

# Update - 2026-09-02 (D-056 phone install and upgrade-path fix)

Upgrade-path fix: the first D-056 gate used `!onboardingCompleted || !homeBaseConfirmed`, which would have replayed the whole onboarding for every existing install because `onboardingCompleted` is a new key that reads false on upgrade. The gate is now the pure `shouldShowOnboarding(onboardingCompleted, homeBaseConfirmed)` = `!onboardingCompleted && !homeBaseConfirmed`, so onboarding is a first-install-only surface and an update keeps the user where they were. A JVM case pins all three states.

Verification on exact serial `R3CW203NFSL` (SM-S918N, Android 16, wireless adb `100.112.73.34:5555`): optimized v1.5 (6) is 5,417,489 bytes, SHA-256 `4622DC9BF0441395F402B65C1612149C17469AE8B87B7AE32D9127D25C42A13D`, replace-installed over the previous v1.5 (6). Cold launch stayed resumed as PID 28836 with an empty crash buffer and no FATAL/ANR. Onboarding correctly did not appear. Durable state survived: campus zone `본관`, `공식 주간 시간표 605행 · 현장 추가 20행 · 사용자 출발 슬롯 595개`, the published dormitory week, and the real 9.2 notices.

Observed on the phone: Home renders the unified `HeroClassPreviewRow` with no duplicated shuttle prose, and its shuttle card shows both destinations. The Classes tab exercised the new inline banner for real - the saved LMS session had expired, so the list shows `로그인이 필요합니다` with `마지막 갱신 2026년 9월 2일 19:21` and a `다시 시도` action over the cached agenda instead of a snackbar that disappears. Retry was deliberately not tapped, so live authenticated LMS refresh remains unverified this session.

App JVM is 190/190 with the new upgrade-path case; lint and `assembleOptimized` pass. The emulator instrumentation `OK (144 tests)` was run before this fix; the fix is a pure-function gate change covered by JVM tests.

# Update - 2026-09-02 (D-057 pinned headers, one-day meal view, single-row Classes filter)

Current state: still uncommitted on `codex/wip` on top of `8bb1e44`, now including D-057. `ScreenScaffold` pins each screen's header (title, refresh, gear, plus a per-screen sub-header) above the list, and `ScreenLazyColumn` is deleted. The Meal tab shows one day at a time behind a pinned Mon–Fri selector and renders dormitory days as 조식/중식/석식 cards with hourless corners attached, one menu item per line. The Classes tab pins 오늘/전체 and collapses its three chip rows into a single scrollable filter row with a `필터 해제` chip.

Verification: `testDebugUnitTest` (now including three `DormitoryMealBlocksTest` cases), `lintDebug`, `assembleDebug`, and `assembleOptimized` pass. The instrumented suite ran on user-authorised wireless `SM-S918N` (`100.112.73.34:5555`) after the API 36 emulator's system server crashed and its launcher held a focus-stealing ANR dialog through a reboot: 144 tests, 3 intentional skips, 3 environmental focus failures that all passed on rerun (10/10) with Proton Pass autofill temporarily disabled and then restored. Optimized v1.5 (6) is 5,433,873 bytes, SHA-256 `2C9B5D68C043809618C60AB3642E3C5FE527BB1996426B30B3AF6646DF12A441`, installed on the phone and a cold-booted emulator with empty crash buffers on both. The emulator screenshots confirm the grouped dormitory cards and the header staying pinned through two full-screen scrolls.

Next action: the phone's install is fresh (instrumentation removed the package) and sits on the onboarding welcome step, so home-base selection is waiting on the user. The redesigned Classes filter bar has no on-device visual evidence because a fresh install has no LMS session and no credentials were entered - `LmsHistoryScreenTest` is its only proof so far. Nothing from D-056 or D-057 is committed yet.

# Update - 2026-09-03 (D-058 entrance motion, Now Bar chip, Classes rework, pull-to-refresh)

Current state: still uncommitted on `codex/wip` on top of `8bb1e44`, now including D-057 and D-058. Only the shuttle screen staggers its entrance; every other tab settles at once and lazy-list cards no longer re-animate on scroll. The Now Bar chip reads `본관행 12분` with a bus or mortarboard icon chosen by guidance kind. The Classes tab has no read/unread filter or badge, orders 전체 by official course order with completed learning collapsed at the bottom, and compresses 종류/과목 into two dropdown chips. Refresh across 식단, 셔틀 and 수업 is M3 pull-to-refresh instead of a top icon button.

Verification: JVM tests, lint, debug and optimized assembly pass; the instrumented suite on wireless `SM-S918N` finished 144 tests, 0 failures, 3 intentional skips. Optimized v1.5 (6) SHA-256 `908976C50D554F69A30B4F95E22D66A9AE847954D8403839C24088F5E6966C08` is installed on the phone and the emulator with empty crash buffers.

Next action: the phone is on the onboarding welcome step again because instrumentation removed the package - home-base selection is the user's. Two D-058 changes still lack device evidence: the removed 읽음/안읽음 card badge (screenshot predates the edit) and the Now Bar countdown chip (needs a live class or shuttle window). Nothing from D-056, D-057 or D-058 is committed.

# Update - 2026-09-03 (D-059 onboarding resume re-check)

Current state: uncommitted on `codex/wip` on top of `8bb1e44`, now covering D-056 through D-059. The first-run exact-alarm step no longer hangs: onboarding re-reads every permission on `ON_RESUME` and advances only when the settings-only exact-alarm permission flips off→on while that step is showing.

Verification: JVM tests, lint and optimized assembly pass; instrumented suite on wireless `SM-S918N` is 144 tests, 0 failures, 3 intentional skips. Optimized v1.5 (6) SHA-256 `E01098B5D68058E51AE7DF48ADDB65BE799D7B3EF173D756977F90D9C48530E3` is installed on the phone, crash buffer empty. The emulator reproduced both the grant and the no-grant return paths end to end.

Next action: still nothing committed from D-056 through D-059. The two previously open D-058 items are now closed by instrumented assertions run on both devices — the card asserts zero 읽음/안읽음 nodes, and the built `Notification` asserts `ic_stat_shuttle`/`ic_stat_class` plus `본관행 12분` as its short critical text. Suites are 145 tests, 0 failures, 3 intentional skips on wireless `SM-S918N` and on the API 36 emulator. What remains unobserved is only the Now Bar chip as the system itself renders it, which needs a real class or shuttle window; the emulator's app data is cleared and mid-onboarding from the D-059 reproduction.

# Update - 2026-09-04 (D-060 moving Now Bar countdown)

Current state: uncommitted on `codex/wip` on top of `8bb1e44`, now covering D-056 through D-060. Countdown-mode notifications no longer set static `shortCriticalText`; Android's existing `when` count-down chronometer owns the compact chip, so the displayed time can move between the app's minute-boundary payload refreshes. The D-058 shuttle/class icons, route metadata, and the explicitly static classroom chip option remain.

Verification: red before green at `LiveSurfaceController`; focused JVM 11/11, full JVM 202/202, and Android-test compilation pass. No device is connected, so a real One UI 8 Now Bar observation is the next acceptance step and must check that the countdown visibly advances between two app minute boundaries. Nothing from D-056 through D-060 is committed.

Physical follow-up: wireless ADB connected to the user's SM-S918N at `100.112.73.34:5555`. Optimized SHA-256 `80536603427D3A2C056264B1D9549FBDFAE96220766F372A9DDED338B32F2267` replace-installed without changing the original first-install time. The active promoted notification changed from static `shortCriticalText=본관행 21분` before install to null afterward while retaining the count-down chronometer. The One UI 8 Home status chip visibly moved `23:44` -> `23:16` during wall-clock minute `9:36`; PID 23807 stayed alive with no filtered AndroidRuntime crash. The collapsed lock-screen Now Bar showed only the DIMA Now label, so status-chip motion is accepted but a countdown inside that separate collapsed card is not claimed.

## Update - 2026-09-08 (reviewed commit and push batch)

Current implementation commits are `11679c8` (completed onboarding/screen/4402 work) and `4023ecd` (D-063 minute refresh), on top of student-meal commit `39035df`. The user explicitly authorized publication to the existing repository after checking the other DIMA Now conversation. App source matches the version validated with 220 JVM tests, ten Android notification tests, lint/builds, and direct One UI 9 screenshots. Installed optimized APK SHA-256 is `7d1cb4c1f192ee7c6378c5acdcfc27d2eb99bfcb33d3404be523a9a0ec642266`.

The publication target is `origin/main`, using a fast-forward from its fetched head. Local binary/screenshot evidence stays under ignored `artifacts/`; this batch does not create a GitHub Release or change app version 1.5 (6). Actual outdoor 4402 proximity and prolonged deep-idle behavior remain separate acceptance work.
