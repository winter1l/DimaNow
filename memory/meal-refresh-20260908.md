# D-062 - Student cafeteria publication watch - 2026-09-08

Status: user-approved implementation. Supersedes the one-shot student-meal collection slot in D-042 and the student-meal portion of the static sync policy; shuttle and notice sync remain on the existing schedule.

School evidence: the official August week 4 post was published Monday 2026-08-24 11:57:35 KST and the September week 2 post Monday 2026-09-07 10:41:12 KST. These are observations from post-level UTC datetime values, not a promised school deadline.

Approved behavior: the server retries collection until a complete current Monday-Friday menu is validated. Android checks on foreground/meal-tab entry after a 15-minute quiet period; missing menus are checked about every 30 minutes on Monday 09:00-14:00 KST and every two hours outside that window. A complete current week returns to the existing 12-hour correction sync. Manual refresh bypasses the short manifest cache. Failures preserve stored data and back off. The meal page distinguishes waiting for the current week from older stored dates and shows last-check time. Widgets update when data arrives.

Implementation choice: GitHub cron uses minute 7/37 to avoid the top-of-hour queue; scheduled collection skips OCR once the full week exists, while explicit publish-meal remains available for corrections. WorkManager timing is approximate and network-constrained. No push, GitHub Release, or new public deployment is included in the user's explicit commit-and-phone-update request.

Validation: 218 app JVM tests passed; 35 data-pipeline tests passed and one opt-in live probe was skipped. Lint, debug/test APKs, and optimized assembly passed. Nine targeted API 36 emulator tests passed, covering manifest-cache bypass, 15-minute suppression, concurrent source checks, stored-menu preservation, WAITING-to-READY import, and latest-week completion messages. A separate export of only the staged commit also compiled and passed the four new MealSource policy tests.

Observed phone acceptance: SM-S918N at 100.112.73.34:5555 received a data-preserving optimized replace-install. First installation remained 2026-09-03 01:04:38. The installed base.apk SHA-256 matched the final build: 254660fc8686fa9a322ed0a4bc43ef41960df967a6bed961178b5c35b8bfe95d (5,533,397 bytes). On the student-meal tab, pull refresh updated the last-check timestamp and showed 2026-09-07 as the saved week while retaining the current menu. PID 28792 remained alive and the crash buffer since the final update contained no DIMA Now mention. Screenshot: artifacts/meal-refresh-20260908/phone-student-final.png (ignored local evidence).

Boundaries: the future Monday timing and battery behavior are supported by policy tests, not an elapsed-week physical observation. The working-tree APK includes the user's pre-existing uncommitted UI/guidance changes; the commit excludes them and was independently compiled. The GitHub workflow change is local until pushed to the remote default branch.
