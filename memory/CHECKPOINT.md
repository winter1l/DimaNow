# CHECKPOINT - 2026-08-28

Current state: D-034 balanced optimization plus D-035 deterministic Back navigation are implemented, fully built, and replace-installed on the authorized SM-S918N/API 36. Product behavior/design, raw 605-row shuttle cache, fixed polygons, Room schema, and saved settings are unchanged.

Runtime changes: application-scoped coherent guidance snapshot, conflated/serialized refresh triggers, cached `ShuttleScheduleIndex`, explicit stale guidance-alarm cancellation, KST ticker resynchronization on time/date/time-zone changes, minute subscriptions only on visible Home/Shuttle/Meal routes, shared widget minute alarm, and one-time obsolete background-work cleanup. The existing two-countdown special-use service condition remains unchanged.

Build/performance: `benchmark` module, generated Baseline/Startup Profiles, ProfileInstaller, and same-ID/same-signature non-debuggable `optimized` R8 build. API 36 emulator Macrobenchmark cold-start median is 13,877.2 ms compilation-none versus 7,617.3 ms Baseline Profile (45.1% lower; emulator comparison only). Compose report: strong skipping enabled, 159 skippable composables, only expected minute/animation helpers non-skippable.

Back behavior: a current dialog dismisses first; any non-home tab returns directly Home without replaying tab history; Back on Home finishes `MainActivity` and returns to the launcher. Verification: 88 JVM tests, `compileDebugAndroidTestKotlin`, lint, debug/optimized assembly green; API 36 instrumentation 46 passed and 3 intentional Samsung/network skips. Galaxy replace-install retained course, shuttle, meal, notice, and location state. Physical ADB/UI checks passed Settings -> Home -> launcher and Timetable -> editor dismiss -> Home; no immediate FATAL/ANR.

Final artifact: `app/build/outputs/apk/optimized/app-optimized.apk` and `dist/DIMA-Now-v1.0-optimized-20260828.apk`, 48,676,281 bytes (46.42 MiB), SHA-256 `693CD96CE71BCDEE341254CC530691B9FECA11533D7915B96FF97A4D20F8C816`.

Still pending by evidence class: observe production Samsung Now Bar/AOD after this build, real three-site geofence dwell/exit, two-countdown trip accuracy, One UI add/resize for all registered widgets, and one school-day battery behavior. Do not infer these from automated tests.
