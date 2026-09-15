# UI and first-run setup cleanup — 2026-09-14

Confirmed user scope: full D-075 plan, existing Material style and five tabs preserved. Root implemented shell/Home/timetable/meal/settings; agents implemented LMS empty state, onboarding/preferences/shared permissions, and standalone Home/4402 components. All changes remain local; unrelated earlier feature, LMS security/recovery and meal deployment changes are retained.

## Implementation

Home has one Today surface with class context and deadline subsection; duplicate class count/navigation removed, cache/error/unknown-completion information retained. Timetable has one header Add action, small term link and per-course overflow actions. Transit uses origin/direction/time hierarchy and expandable 4402 stop metadata; public stop labels follow current contributor guidance without changing topology. Meal empty cards own the single photo-upload action with existing preflight and sharing disclosure; unsupported processing deadline promise removed. Settings groups direction, notification preferences, expandable shared automatic-setup list, account, app info and folded diagnostics.

Courses Today considers non-completed agenda groups, still includes fresh/overdue/next-three-day items, and keeps completed learning expandable beneath the no-action notice. Filter/no-data/signed-out/loading/error states remain distinct. Per-course All cards omit repeated course names; mixed completed groups retain them. D-074 invisible login host and request lifecycle remain intact.

First run has three pages: welcome, draft home direction, actual permission checklist. Draft stage/selection survives restart; final DataStore transaction writes direction/confirmation/completion together and removes draft keys. Established installs retain legacy bypass and never replay on update. Permission controls require explicit taps, re-read Android state on resume/result, and notify the application to resync geofences/runtime guidance. Approximate-only and missing prerequisite states remain explicit. Initial setup includes no school login, widgets or developer options.

## Automated evidence

Two baseline instrumentation failures in artifacts/ux-onboarding-20260914/ui-red.log reproduced the completed-only empty message and first-run second-page mismatch. Core green run: 33/34 pass; remaining AppBackNavigationTest failed because its generic Settings-text selector matched all permission buttons, not because navigation failed. Replaced with existing close_settings tag; final layout run includes both back tests. New core behavior, auto-login preservation, persisted draft/atomic completion and actual snapshot presentation passed.

Final production build log final-build.log: BUILD SUCCESSFUL (2m15s), testDebugUnitTest 239 tests/0 failures/0 errors; lint 0 errors/91 warnings. Obsolete six-step planner unit tests replaced by new flow/preferences UI coverage. Latest test-only APK rebuilt after selector/copy alignment. Final layout run still pending at this entry. Optimized APK SHA256 AB8CFB7EE02A4E5B40CF8A0EBA9243A5DBF92366BFB81D8701B804A63E70656D.

## Observed emulator UI

API 36 medium_phone, explicitly selected emulator-5554 only. Fresh app state was cleared on this disposable emulator, never on the phone. Welcome, direction selection and full checklist PNGs captured and visually inspected. Actual Android notification permission dialog opened by the row action; allowing it changed the checklist to complete. Force-stop/relaunch restored Setup and the selected direction. 360dp width, font scale 2.0, dark theme: content wraps and scrolls while Start remains fixed and reachable. TalkBack was actually bound with touch exploration; keyboard Tab moved visible green focus from Back to the next enabled settings action. No spoken-audio comprehension claim. Restored font 1.0, size/density defaults, light theme and disabled temporary TalkBack before remaining UI tests.

Phone Tailscale connection was lost; user reconnected and targeted wireless ADB connect succeeded. Replacement installation and physical screenshot verification remain pending at this entry.

## Final verification and replacement install — 23:34 KST

Confirmed: final layout run covered 36 cases; 35 passed initially and the last failure was an obsolete GuidanceCardTest string selector. That selector was updated and its rerun passed. The earlier generic Settings selector was corrected and both AppBackNavigationTest cases passed in this layout run. Across core and layout runs, 68 distinct targeted instrumentation cases passed including resolved reruns. Unit result: 239 tests, zero failures/errors. Final display-copy-only rebuild (verified-copy-build.log) succeeded in 2m44s for debug, test, optimized and lint; no functional changes after the green behavioral checks. Lint has zero errors and 91 warnings. git diff --check passed.

Observed on test-device-galaxy-api37-a: the five main tabs and Settings were captured and visually inspected. Home combines Today class context/deadlines; timetable retains real classes, one Add action and the three-item overflow menu; shuttle shows origin/direction and timetable; meal empty state has one upload action. Settings follows approved section order, reports automatic account login, and keeps diagnostics folded. Final Courses screenshot shows "오늘 확인할 학습이 없어요" and "완료한 학습 · 17개" with bottom navigation. Refresh also retained the list/nav. A new physical SSO exchange was not independently forced; held-auth instrumentation establishes that pending-state behavior.

Confirmed final replacement installation at 23:32:12 KST: optimized APK SHA256 481BEFC2B42EBECB248F61B1306A6A2EFA4CC7AEA1A24EE42AD28073634052C1 equals the freshly pulled installed base APK. Original firstInstallTime remains 2026-09-03 01:04:38. Final launch goes directly to Home, retaining timetable, account and completed learning. No phone data clearing or permission/preference changes were performed. The task-started emulator was stopped after verification. No commit, push, release or server deployment performed by this UI task.

Evidence: artifacts/ux-onboarding-20260914/verified-copy-build.log, ui-green-core.log, ui-green-layout.log, ui-green-selector.log, phone-final-home.png, phone-final-courses.png, phone-timetable.png, phone-course-menu.json, phone-shuttle.png, phone-meal.png, phone-settings.png and phone-settings-bottom.png. Earlier pending entries above are superseded by this final entry. Bounded TalkBack focus was checked; spoken reading order and every device/permission combination were not exhaustively observed.
