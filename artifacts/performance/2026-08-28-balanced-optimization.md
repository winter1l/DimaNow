# DIMA Now balanced optimization evidence — 2026-08-28

## Scope

- Kept the five-tab UI, Korean copy, DIMA pink theme, motion, widget layouts, Live Update payload rules, Room schema, fixed campus polygons, raw 605-row shuttle cache, and user preferences unchanged.
- Removed duplicate runtime work through an application-scoped guidance snapshot/coordinator, cached shuttle index, active-tab minute subscriptions, and one shared widget minute alarm.
- Added an R8/resource-shrunk, non-obfuscated `optimized` build plus generated Baseline/Startup Profiles and ProfileInstaller.

## API 36 emulator macrobenchmark

Device: `medium_phone` AVD, Android 16/API 36, headless emulator. Five cold-start iterations per mode. Network refresh and meal OCR were excluded from the journey.

| Metric | Compilation none | Baseline Profile | Change |
|---|---:|---:|---:|
| Cold start median (`timeToInitialDisplayMs`) | 13,877.2 ms | 7,617.3 ms | 45.1% lower |
| Frame CPU P50 | 266.5 ms | 193.4 ms | 27.4% lower |
| Frame overrun P50 | 472.0 ms | 302.1 ms | 36.0% lower |
| Median frame count | 24 | 24 | unchanged |

The emulator numbers are comparative development evidence, not a claim about physical-device latency. Raw benchmark messages, JSON, and Perfetto traces remain under `benchmark/build/outputs/connected_android_test_additional_output/benchmarkRelease/connected/medium_phone(AVD) - 16/`.

## Profiles

- `app/src/main/generated/baselineProfiles/baseline-prof.txt`
- `app/src/main/generated/baselineProfiles/startup-prof.txt`
- Journey: cold launch, Home → Shuttle → Meal → Settings → Home, plus shuttle timetable scrolling.

## Final artifact and device evidence

- Optimized APK: `app/build/outputs/apk/optimized/app-optimized.apk`
- Current post-D-035 size: 48,676,281 bytes (46.42 MiB); the optimized build remains substantially smaller than debug.
- Current SHA-256: `693CD96CE71BCDEE341254CC530691B9FECA11533D7915B96FF97A4D20F8C816`
- Both APKs use signing certificate SHA-256 `A8FBB7A36627A4E986241EB22E67E71E8255D616B67AEF6EBEB3215CEA2B5136`.
- `adb install -r` succeeded on exact physical serial `R3CW203NFSL` (`SM-S918N`, API 36). Cold launch reported 324 ms; the process remained alive while all five tabs were opened.
- Existing course, shuttle, validated meal, notices, and effective zone were visible after the replace install. No immediate DIMA Now FATAL/ANR was found.
- Current AlarmManager state contained one shared `UPDATE_ALL_WIDGETS_MINUTE` alarm and the independent next guidance boundary. Legacy per-provider minute alarms appeared only in historical statistics, not the active alarm list.
- OEM-controlled Now Bar/AOD rendering, physical geofence transitions, and one-day battery behavior were not re-certified by this optimization smoke.
