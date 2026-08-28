# Goal — DIMA Now Android

Goal: implement the approved personal DIMA Now Android app inside this directory.

Definition of done: all feasible automated checks and an API 36 smoke check pass; physical One UI 8 checks are reported individually as passed or pending and are never inferred.

## Mobilization

| Branch | Needs | Held | Gap and first move |
|---|---|---|---|
| Pure guidance | Confirmed seams and literal examples | D-002, D-004 | Fill by vertical GuidanceEngine tests |
| Durable app | Compose/Room/DataStore project | Generated API 36 scaffold | Add dependencies only when a behavior needs them |
| Campus sensing | Permission and geofence contracts | D-003 plus Android docs | Verify APIs, then test resolver seam |
| Official sources | Current public HTML/embed shapes | User-approved URLs | Capture minimal official-shape fixtures and parser tests |
| Live surfaces | Widget, notification, promoted update contracts | D-003 plus Android docs | Implement fallback first, then API 36 promotion |
| Acceptance | Automated, emulator, and Galaxy evidence | D-004 | Run each available check; leave unavailable phone checks pending |

## Terrain map — 2026-08-26

- `observed`: Android CLI 1.0.15857036 generated an AGP 9.0.1 / Gradle 9.1.0 Compose project after API 36 platform repair.
- `observed`: the `medium_phone` API 36 emulator boots, runs the app, and completed all four instrumented tests.
- `confirmed`: promoted Live Updates require the manifest promotion permission, an ongoing request, and no custom notification RemoteViews; source is current Android developer documentation.
- `unknown`: Samsung-specific promotion eligibility and final Now Bar rendering until the physical device is connected.

## Skeleton v1

1. Buildable foundation
   1.1 Toolchain and generated project — filled (`app/build.gradle.kts`, baseline test output)
   1.2 First public-seam tracer bullet — filled (GuidanceEngine red-green evidence)
2. Shared domain behavior
   2.1 Models and seeded timetable — filled
   2.2 Class lifecycle and no-class rules — filled
   2.3 Return and transfer guidance — filled
3. On-device state and editing
   3.1 Repository persistence — filled
   3.2 Compose screens and preferences — filled
4. Location
   4.1 Zone resolution and overlap — filled
   4.2 Permissions, capture, dwell, exit — implemented; physical acceptance pending
5. Shuttle source and surface
   5.1 HTML parse/cache — filled and live-source verified
   5.2 Widget countdown lifecycle — implemented; physical timing acceptance pending
6. Live class surface
   6.1 Alarm/fallback notification — filled
   6.2 API 36 promotion and minute updater — implemented; Samsung rendering pending
7. Meal source and surface
   7.1 Discovery/embed/image/OCR validation — filled and live-source verified
   7.2 Cache protection and widget — filled
8. Recovery and acceptance
   8.1 Permission/offline/reboot/time changes — implemented and unit-tested where platform-independent
   8.2 Automated and emulator checks — filled
   8.3 One UI 8 live acceptance — partially filled; requested UI and smoke checks verified, real-site/trip/battery checks pending

Single next leaf: 8.3, complete the remaining real-campus geofence, real-trip countdown, widget resize, production guidance, and battery observations in `README.md`'s manual checklist.

Known gap: the remaining checks require physical campus travel, launcher widget placement, or elapsed real-world observation.

Done check: each acceptance item is marked pass, fail, unavailable, or blocked with evidence.
