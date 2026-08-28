# DIMA Now final verification — 2026-08-27

## Build and automated tests

- Final command: `testDebugUnitTest compileDebugAndroidTestKotlin lintDebug assembleDebug connectedDebugAndroidTest` with `ANDROID_SERIAL=emulator-5554`.
- Result: `BUILD SUCCESSFUL` in 1m 42s.
- JVM: 75 tests in 18 suites, 0 failures, 0 errors, 0 skipped.
- API 36 emulator: 45 tests, 0 failures, 0 errors, 2 Samsung-physical-only tests skipped by their explicit assumptions (43 executed passes).
- Android-test Kotlin compilation, parser fixtures, `lintDebug`, and `assembleDebug`: passed.
- APK: `C:\project\dahak\DimaNow\app\build\outputs\apk\debug\app-debug.apk`
- Size: 115,407,220 bytes.
- SHA-256: `2A4ABB8DF30C6E4D28F75BD25D8BDC9B509438411B9A8535A17CBF947B9D7B1F`.

## Fixed zone evidence

- User-approved GeoJSON: `artifacts/zone-boundary-capture/dima-now-campus-zones-v2.geojson`.
- GeoJSON SHA-256: `90A0D9E51129D2481B2945CC78800B9E34D24D42AE7B33536A0A06512D8EA094`.
- Bundled version: `CAMPUS_ZONES_V2_USER_2026_08_27`.
- YEIN: center 37.0609666, 127.3535671; 5 vertices; wake radius 550 m.
- MAIN: center 37.0594160, 127.3585957; 6 vertices; wake radius 570 m.
- ONE_ROOM: center 37.0558538, 127.3627537; 9 vertices; wake radius 680 m.
- JVM tests cover polygon interior/exterior/edge behavior, school-over-ONE_ROOM priority, nearest school center, test-mode GPS override, and wake-circle containment. API 36 Room tests cover atomic replacement of only the three zone rows while preserving courses and guidance-pause state.

## Physical Galaxy verification

- Target: Samsung SM-S918N, serial `R3CW203NFSL`, Android 16/API 36, authorized.
- Install: final APK installed with explicit `adb -s R3CW203NFSL install -r`; existing app data was retained.
- Read-only Room check after migration:
  - all three rows contain the exact V2 centers, polygon versions, wake radii, and 5/6/9 vertices;
  - the user's 5 current courses, 605 raw shuttle rows, and 5 validated meal days remained present.
- Launch: the main app restarted as PID 5901 and had no immediate FATAL, ANR, Room migration failure, SQLite exception, or foreground-start exception in the relevant log window.
- Location test mode: enabling it showed `테스트 모드 · GPS 미반영`; MAIN selection reached Home; disabling it removed the banner and restored the current GPS-derived YEIN zone.
- Home: valid shuttle guidance and meal content were visible without the removed data-status footer.
- Shuttle screen: top-right refresh only, no `요일 선택`, origin-group headers with destination-only cards, both MAIN directions, first/last annotations, red `막차`, and no bottom source card. At 21:32 KST, the retained official cache selected `stadium-stop`; the physical screen showed the current MAIN-side group as `운동장`, the upcoming last departure as `막차 · 운동장`, and the visible evening timetable slots as `운동장`.
- Meal screen: top-right refresh only, no `이번 주 식단 (월 ~ 금)` heading, the validated five-day menu remained visible, and the current closed state read `오늘 운영이 끝났어요`.
- Settings: source/cache details moved to `데이터 및 원문`, including 605 raw weekly-expanded rows, 575 direction-aware user slots, meal week, source links, V2 version, and OpenStreetMap attribution.
- Course deletion: the confirmation showed `조명기초및실습` and `월요일 · 10:00–12:50`; cancellation left the course intact.
- First-run setup: the exact two-line Now Bar guide appeared after home-base confirmation, was completed once, did not reappear automatically, and remained reopenable from Settings.
- Now Bar guide actions: both buttons previously crashed because the UI deliberately passed an Application context to a shared settings launcher without `FLAG_ACTIVITY_NEW_TASK`. API 36 public-screen regressions cover both paths. The lock-screen guide action now calls the same safe promotion-settings path as `Live 설정`; both physically opened `Settings$AppNotificationSettingsActivity`. The developer action opened `DevelopmentSettingsActivity`; PID 3700 remained unchanged during both checks and neither path emitted a FATAL exception. No Developer options value was changed.
- WorkManager: daily lightweight shuttle and meal policy jobs are registered. With current valid caches, launch checks completed without a redundant network download or meal OCR. The app requests six-hour failure backoff, while Samsung/WorkManager reported an effective five-hour initial backoff due to the platform cap.
- No extra official meal refresh was triggered during this final pass, avoiding repeated heavy OCR; the retained validated cache and parser/source automated tests were used.

## Samsung promoted Live Update probe

- The gated physical test posted one synthetic eligible notification through the production `LiveSurfaceController` path.
- Samsung set `FLAG_PROMOTED_ONGOING`; the active notification also had ongoing, silent, and only-alert-once characteristics and `android.requestPromotedOngoing=true`.
- The unlocked Galaxy visibly showed the blue live countdown pill. The lock screen visibly showed a DIMA Now Now Bar item.
- The synthetic expanded lock-screen item rendered the app name only, so production class title/classroom layout and AOD presentation remain pending direct observation.
- The synthetic notification was canceled and its test-only APK was uninstalled. The main package and user data remain installed.
- This proves the app is accepted for the developer-option all-app Live Update path on this device. It does not prove membership in Samsung's ordinary Live notifications app list, which remains OEM/system-controlled.

## Remaining live acceptance

- Real 2-minute dwell, overlap, exit, and arrival-cancellation transitions at YEIN, MAIN, and ONE_ROOM.
- Production class lifecycle: pre-class, first 30 minutes, +30-minute return shuttle, scheduled class removal, adjacent-class priority, and both home-base choices.
- Two shuttle countdowns over three real minute boundaries, departure promotion, deep-Doze recovery, and end-of-service behavior.
- Production Now Bar expanded text, lock screen, and AOD layout under both pill/first-line options.
- One UI Home add/resize behavior for shuttle and meal widgets.
- Fixed-range/until-disabled pause behavior across real dates and a normal school-day battery observation.

Visual evidence is in `artifacts/final-device-smoke-v2/`.
