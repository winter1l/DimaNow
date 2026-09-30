# Meal publication recovery — 2026-09-21

## Confirmed causes

- At 08:10 KST the collector retained the Sep14–20 payload after Instagram returned HTTP401. The school homepage later exposed official student-cafeteria post https://www.instagram.com/p/Ddh9VzXk93h/ for Sep21–27.
- Cloudflare scheduled retries consistently failed before fetching the manifest. The exact deployed module reproduced in workerd: redirect:error is unsupported. Node-only replay had passed, so runtime verification was essential.
- Revision10 contains Thursday ["추석 공휴일"] and Friday ["추석"]. Producer allowed one nonblank line but app import, pipeline completion and worker completeness required two lines, rejecting the valid holiday week.

## Confirmed fixes

- Worker uses redirect:manual; non-success public responses and all dispatch responses other than204 remain rejected. Bounds, SHA256 and schema validation stay in place.
- App, publisher completion and producer use one shared Kotlin student-menu policy. Ordinary menus still need two nonblank lines; exact explicit holiday/closure labels may have one. JavaScript watcher uses matching labels and Unicode whitespace normalization.
- Dormitory menus already accepted one-line closure labels. OCR instructions now preserve explicit closures and clearly scoped merged holiday cells without guessing blank cells. A malformed OCR result with an empty section list now records REJECTED/rephotograph instead of aborting without terminal status. This was reproduced using a local API fixture, not a live Gemini response from a new photo.
- Public source remains unchanged. No fabricated menu lines or new dormitory meal were published for this test.

## Observed publication and runtime evidence

- Manual recovery run https://github.com/winter1l/DimaNow/actions/runs/35559819410 succeeded. Revision10 READY published2026-09-21T04:08:03Z (13:08 KST), weekSep21–27; SHA2567ac7601efb4698ef7eefb3a96fb7d9b3754358fd3a80a7d75ba7ce543361a32f.
- Cloudflare worker dima-now-meal-upload updated04:27 UTC, version f721083c-f0e7-472a-9093-f2b0d488567a. Worker module/binding details are recorded privately (P-04).
- Real scheduled invocation at04:37:24 UTC succeeded (4015us CPU), following failed04:07:24 and earlier invocations. Local workerd also fetched the real public week and skipped unnecessary dispatch.
- Remote main is materially behind the working checkout. A separate ignored clone of remote4a37f31 received only the meal repair; production commit e0ce29e77dda34738a55bbb1f56db79950cfae94. Unrelated local UI/security changes were not pushed. Existing remote dormitory publication policy was retained; local review policy was retained separately.
- Production data workflow after the commit succeeded: https://github.com/winter1l/DimaNow/actions/runs/35561839678. General CI run35561839693 was still running at this entry; append its conclusion after checking.

## Observed validation

- Emulator import regression reproduced the original "식단 메뉴 줄 수가 부족합니다." failure before the fix; student10 + dormitory2 Android import tests then passed.
- Local pipeline full suite51 passed, one existing skip. Unicode closure regressions separately passed in builder/publisher19 tests.
- Local worker suite60 passed. Production-base worker40 passed; production-base pipeline41 passed, one skip; Android test compilation succeeded.
- Final debug/test/optimized APK build succeeded. Optimized replacement install and launch succeeded on API36 emulator. Real public Sep21 Monday menu and Sep24 holiday row were observed in the UI. Update retained the cached real menu.
- Final optimized APK: dist/DIMA-Now-1.5-meal-fix-20260921.apk; SHA256197BC20677BA0D297CB5923F836ABF5AA8A825D0D5F9231D8645D7677900191D.
- Phone last-known endpoint did not respond; user was asked to reconnect Tailscale/wireless debugging. Physical-phone installation/appearance is not yet verified.
- Existing UI shows the source's generic opening hours alongside the holiday label; this work verifies ingestion and recovery, not a holiday service-hours redesign.

## Local evidence

Ignored .local/meal-delay-20260921/ holds public before snapshots, deployed-source backup, workerd probe, regression logs, isolated release clone and emulator captures. Actual device connection values remain only in ignored .local/device-connections.json. Wrangler state is excluded from tracking.

## Final CI confirmation — 2026-09-21 13:46 KST
Observed: production CI run35561839693 completed successfully, including tracking/privacy checks, JVM/pipeline tests, Android test compilation, lint, optimized assembly and worker tests. Local source commit57d9d5e retains this repair separately from the scoped production-base commite0ce29e. Physical-phone update remains pending user reconnection; APK and optimized emulator verification are complete.
