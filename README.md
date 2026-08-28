# DIMA Now

Personal Android 16 campus companion for a single DIMA student. The app keeps the timetable, saved campus locations, cached shuttle/meal data, OCR results, and preferences on the device. It has no backend.

## Local build

The repository-contained launcher selects the project-local Microsoft OpenJDK 17 without changing the machine-wide Java configuration:

```powershell
.\gradlew-local.bat testDebugUnitTest assembleDebug lintDebug
```

The debug APK is produced at `app/build/outputs/apk/debug/app-debug.apk`. For connected API 31+ devices or emulators:

```powershell
.\gradlew-local.bat connectedDebugAndroidTest
```

The locally installable optimized build keeps the debug package identity, version, and signing key so a replace install preserves existing app data. It enables R8/resource shrinking without name obfuscation and packages the generated Baseline/Startup Profiles:

```powershell
.\gradlew-local.bat assembleOptimized
adb -s <device-serial> install -r app\build\outputs\apk\optimized\app-optimized.apk
```

Performance/profile generation lives in the `benchmark` module. Run it only against an explicitly selected API 36 test device; its journey does not refresh the network sources or start meal OCR.

## Behavior and data boundaries

- `GuidanceEngine` is the single pure source for dashboard, notification, and live-guidance content.
- Room stores the editable timetable, term, no-class dates, fixed polygon zones, source status, shuttle departures, and validated weekly meals. DataStore stores current-zone/geofence state and the durable GPS/test location mode. Automatic class guidance is always active; users can stop its notifications in Android app notification settings.
- Shuttle data comes from the official DIMA A/B/C tables. Meal discovery starts at the official DIMA page; if its five-post window has rolled over, the app uses the official Instagram profile's public feed metadata to find the latest cafeteria post, then follows that post's public embed. The weekly table is OCRed on-device, and implausible weeks never replace the last valid cache.
- The shuttle page shows the complete cached week. Service is grouped under an origin header with destination-only cards, route-variant duplicates at the same time are collapsed, every first/last time is annotated, and a last departure uses a red warning treatment. When the official MAIN-side source stop changes from `university-headquarters` to `stadium-stop`, the first changed slot says `운동장 전환` and subsequent active boarding labels say `운동장`. The meal page shows the current Monday-through-Friday menu; dates without a validated menu say `제공 식단 없음`.
- Shuttle and meal pages expose one refresh icon in the top-right. Source status, cache meaning, official links, the source image, and OSM attribution live in Settings > Data and sources. Refreshes remain serialized and report progress/results through accessible UI feedback.
- The shuttle worker downloads only when the last successful cache predates the current KST week. The meal worker starts after every validated menu date has passed and retries with backoff until a current/future validated week is available; failed OCR candidates never replace the last valid week.
- Timetable pause mode offers exactly `오늘만`, `기간 지정`, `학기 종료일까지`, and `휴강모드를 다시 끌 때까지`. The last choice persists without a calendar end date and ends only when the user turns pause mode off.
- Android 16 requests promoted ongoing notification treatment. Eligibility and final rendering are decided by the OS; older or ineligible devices receive a normal silent ongoing notification.
- The settings page offers two durable Live Update presentation choices: the compact pill can show either the countdown or the classroom, and the expanded lock-screen card can put either the course name or classroom first. Before class the card says `시작까지 N분`. During the first 30 minutes it says `수업 중` without an unreliable end countdown; after 30 minutes it changes to the selected home-base shuttle countdowns. Classroom-chip mode always keeps only the classroom in Android's chip-only `shortCriticalText`, including after screen off/on and while the keyguard is showing.
- Course start/end editing uses Android's 24-hour Material time picker; free-form time text is not accepted.
- Home, the shuttle page, both shuttle-bearing widgets, and Live Update use the same KST minute rounding and route-deduplicated `ShuttleBoard`. Official `stadium-stop` departures use the same dynamic `운동장` boarding label on the compact live surfaces without changing the underlying MAIN location zone. Exact-alarm permission allows minute-boundary widget updates; delayed deep-Doze delivery is corrected on the next wake/update without an always-on service.
- Guidance inputs are combined once into an application-scoped runtime snapshot. The 605 source rows are indexed only when the cache changes, app routes subscribe to the minute ticker only while Home/Shuttle/Meal is visible, and the two shuttle-bearing widgets share one minute-boundary alarm.

## Fixed campus polygons

The user finalized all three boundaries with the development-only OSM tool. The APK has no map editor and no coordinate/radius apply controls. Version `CAMPUS_ZONES_V2_USER_2026_08_27` is bundled offline and a one-time versioned install replaces only the three campus-zone rows; timetable, cache, home-base, pause, Live-display, and test-mode state are preserved.

| Zone | Center latitude | Center longitude | Vertices | Wake-up radius |
|---|---:|---:|---:|---:|
| 예인관 | 37.0609666 | 127.3535671 | 5 | 550 m |
| 본관 | 37.0594160 | 127.3585957 | 6 | 570 m |
| 원룸촌 | 37.0558538 | 127.3627537 | 9 | 680 m |

Android circular geofences use the wake-up radii; final classification uses the offline polygons. YEIN/MAIN win overlaps with ONE_ROOM, and YEIN/MAIN overlap uses the nearest center from a fresh sample. Test mode ignores GPS/geofence events until it is turned off and can switch among all four zones. The approved GeoJSON and hash are recorded in `artifacts/zone-boundary-capture/`. © OpenStreetMap contributors.

## Galaxy smoke result — 2026-08-27

- Installed with `adb install -r` on the authorized SM-S918N (Android 16/API 36); existing data was retained and the activity/process stayed alive without a FATAL exception or ANR.
- Home displayed valid shuttle guidance and meal content without the removed source-status footer. The retained official cache contains 605 weekly-expanded rows; the direction-preserving projection contains 575 distinct service-day/origin/destination/time slots. The older 505 figure ignored destination and is not used by the current UI.
- A single physical-device shuttle refresh visibly entered `새로고침 중`, then succeeded with 605 rows. A single meal refresh also entered the disabled loading state, found the rolled-off 2026-08-24 official post through the public-profile fallback, OCRed the second carousel slide, and stored five validated weekdays. The KST success state and menu survived a process restart.
- Notification, promoted-notification, exact-alarm, fine-location, and background-location permissions were granted. `canPostPromotedNotifications()` and promotable characteristics were true. Samsung has no dedicated promoted-notification settings Activity, and the tested action safely opened app notification settings instead.
- Both first-run guide actions are safe from the Application context used by the app root: non-Activity launches receive `FLAG_ACTIVITY_NEW_TASK`. On the SM-S918N the lock-screen action opened Samsung's notification-app list and the developer action opened Developer options while the DIMA Now PID stayed alive; the test did not change any Developer options value.
- A controlled physical probe received Samsung's promoted-ongoing flag and visibly rendered the countdown pill plus a DIMA Now lock-screen/AOD bar while Samsung's all-app live-information developer option was enabled. In the latest synthetic probe, One UI's expanded lock-screen bar rendered only the app name, not the notification's class title/detail; production class-detail layout therefore remains a separate manual check. Both classroom-pill lock-screen combinations are covered by API 36 payload tests.
- The final replace install applied the exact user-approved `CAMPUS_ZONES_V2_USER_2026_08_27` polygons: YEIN/MAIN/ONE_ROOM contain 5/6/9 vertices with 550/570/680 m wake radii. A read-only Room check proved that the user's 5 current courses, 605 shuttle rows, and 5 meal days were retained.
- The physical data screen rendered the retained 605-row/575 direction-aware-slot shuttle cache as the complete weekday schedule and rendered the validated 2026-08-24–2026-08-30 meal week without filter controls.
- First/last time chips and the red `막차` treatment were visually checked on the Galaxy. Shuttle/meal source cards are absent from those tabs; each has only its top-right refresh action, while cache/source management is consolidated in Settings. All four pause-duration choices remain available. A background foreground-service start denial discovered during replace-install testing safely degrades to the posted Live Update notification instead of crashing the app.
- With the compact pill set to classroom, the previous Galaxy build changed `shortCriticalText` from `덕성관 510-1` to `시작까지 53분 · 덕성관 510-1` when the screen woke over the keyguard. The corrected build retained `덕성관 510-1` through five consecutive screen off/on cycles. The visible collapsed AOD/lock-screen Now Bar showed `13:00 · 프리젠테이션영어` followed by `덕성관 510-1`, while the notification body retained `시작까지 42분 · 덕성관 510-1`.

## One UI 8 manual acceptance (pending until observed)

Use the user's target Galaxy; emulator success does not satisfy these checks.

- During eligible production guidance, finish observing the class title/detail on the expanded lock-screen bar. The countdown pill and DIMA Now lock-screen/AOD bar are physically verified, while the latest synthetic expanded bar showed only the app name. Classroom-pill persistence across screen off/on, AOD, and the keyguard is physically verified. System/OEM list membership and final layout are not controlled by the app.
- At 예인관, 본관, and 원룸촌, compare the fixed V2 polygon boundaries with the real area, then verify the 2-minute geofence dwell, exit behavior, overlap resolution, and arrival cancellation.
- Verify automatic class guidance during a real class window. Confirm start/end transitions are silent and professor names never appear.
- Verify both shuttle countdowns remain within one minute for three minute boundaries, the first promotes after departure, MAIN omits the pre-class shuttle row, and return guidance follows the chosen 예인관/원룸촌 base and cancels on arrival/OUTSIDE/end of service.
- Verify the `수업 중` first-30-minute state, the +30-minute return-shuttle transition, class removal at the scheduled end, and next-class priority.
- Verify all four pause modes. A fixed range must resume the next day after its inclusive end; `휴강모드를 다시 끌 때까지` must remain active across a restart and resume only after the switch is turned off.
- Add and resize both widgets in One UI Home; confirm the shuttle widget contains whole minutes only and the compact meal widget remains readable.
- Add the meal widget and compare its compact layout against the already verified in-app menu. The Galaxy OCR result has been compared with the official 2026-08-24 source slide and all five dates, hours, and menu lines matched. Automated testing also verifies that a failed OCR candidate leaves the prior valid week intact.
- Observe one normal school day for unexpected foreground-service use and battery drain.

Never record a live-device item as passed without direct observation.
