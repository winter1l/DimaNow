# Checkpoint — Daily features installed — 2026-09-14 15:47

## The story so far
D-071 implemented: Home today tasks, dated class overrides with optional reviewed Kakao notification proposals, and current-occurrence guidance dismissal. Reviewed optimized APK installed on test-device-galaxy-api37-a; pulled APK hash matches. 246 JVM tests, 46 targeted emulator tests, lint/build pass. Emulator Home/dialog screens visually checked. Phone process launches but screen remains locked. Full evidence: daily-features-20260914.md. Earlier meal repair is complete; preserve its dirty changes and remote main divergence documented in meal-delay-fix-20260914.md.

## Decided
D-071 confirms all three annotations. Kakao default-off processing uses exact room names, local parsing, and user review before applying a dated change.

## Waiting on the user
Phone unlock for actual Home/timetable/settings observation. Exact Kakao room name and user-enabled notification special access for real new-notification verification. Questions already sent asynchronously.

## Next first action
After user unlocks, use Android CLI with ADB server port 5038 to inspect DIMA Now Home on the connected physical device; do not rerun passing tests absent new changes.

## Tried
Emulator install initially failed insufficient storage; cache trim resolved it. Tests against old APK were discarded; fresh APK tests passed. Parser date/year regression was reproduced then fixed. Phone wm dismiss-keyguard did not unlock it; do not bypass credentials.
