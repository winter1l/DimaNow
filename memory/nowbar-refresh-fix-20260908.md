# Folded Now Bar refresh repair — 2026-09-08

## Confirmed defect and controlled observations

The user requested investigation and repair of the folded Samsung Now Bar minute text. At 20:05 KST the actual DIMA card still read following vehicle 88 minutes / nearest vehicle 48 minutes while Android's active notification contained 85 / 45. `artifacts/nowbar-refresh-20260908/dima-stale-red-result.json` is the failed UI/payload comparison, with a visually inspected screenshot beside it. The minute computation and notification posting were already running.

Observed on the connected SM-S918N, API 36, One UI 9 (`90000`):

| Disposable notification probe | Folded card result |
| --- | --- |
| LOW importance, ordinary ongoing notification | Did not replace/update the stale card |
| DEFAULT importance, silent channel, ordinary ongoing notification | Updated 7 to 6 |
| DEFAULT importance, foreground-service notification | Did not refresh as the ordinary notification did |
| Separate LOW service keeper plus DEFAULT ordinary ongoing notification | Updated 7 to 6, matching active payload |

The device's notification filtering options for low-importance and background notifications were enabled. SystemUI placed the successful ordinary notification in its ongoing-activity section. Inference: the old LOW foreground-service card interacted with Samsung's ranking/filtering and stale folded-card state. This is a reproducible compatibility condition; the proprietary SystemUI implementation's exact cache invalidation defect is unknown. Granting the promotion app-op alone did not fix the old card. A LiveBridge Tailscale card was separately identified and was not the stale native DIMA payload.

Android's [Live Updates guidance](https://developer.android.com/develop/ui/views/notifications/live-update) permits OEM-specific eligibility criteria. These phone observations establish this repair on One UI 9 only, not new One UI 8 acceptance.

## Implemented repair

`AndroidLiveSurfaceController` now creates a silent, vibration-free DEFAULT live channel (`dima_live_guidance_v2`) because Android does not allow raising the existing channel's importance. An explicitly blocked legacy channel migrates as blocked. Existing channels are not deleted. The live guidance retains ID 6201 and all class/shuttle presentation options.

`LiveMinuteUpdateService` keeps itself active using a separate, quiet LOW notification, ID 6202. Its minute loop publishes ID 6201 through `NotificationManager.notify`; the live card no longer acquires the foreground-service flag. The keeper has no promotion request, timer, or short critical text. On completion/destruction the service removes only its keeper, preserving the final static class notification. The minute cadence and class system chronometer remain unchanged.

## Verification

- Red: the new instrumentation check expected DEFAULT channel importance but observed LOW before the fix (`channel-red.log`).
- Green: 224 JVM tests, 11 Android notification instrumentation tests, debug/test/optimized builds and optimized vital lint passed (`build-green.log`, `channel-green.log`). Instrumentation ran only on emulator-5554.
- Data-preserving optimized replacement installed on the user's phone. SHA-256: `b0581b16461fa9fa2e49b134c1395f5291c9ea075bc4a6644ec960777767ece9`.
- Actual folded DIMA card and active payload matched at 20:17 (73 / 33), 20:18 (72 / 32), and 20:19 (71 / 31). `app-split-initial`, `app-split-minute1`, and `app-split-minute2` JSON evidence is under `artifacts/nowbar-refresh-20260908`; screenshots at 20:18 and 20:19 were visually inspected. No cancel/repost, service restart, or screen cycle was needed between these minute updates.
- `app-notification-flags.txt` confirms ID 6201 is promoted, importance 3, with no FOREGROUND_SERVICE flag; ID 6202 is importance 2 and carries only the service role. Both have null sound/vibration.

Screen-off follow-up: the phone was put to sleep at 20:19 and awakened after the 20:20 boundary. The first sequential UI/payload capture crossed 20:21, yielding 70/30 versus 69/29; the immediately captured screenshot already showed 69/29. A repeat at 20:21:22 matched 69/29 on both surfaces (`app-split-wake-confirmed-result.json`). This timing race is retained as evidence and is not counted as a failed refresh.

The disposable probe was uninstalled. The app's promotion app-op was restored to its original default. Temporary snoozes of the unrelated mirrored card expired; no third-party app was removed or stopped. At 20:23, the user unlocked the phone and the app's TEST switch was turned off. `restore-confirmed.json` shows the unchecked switch and automatic-location wording; `final-home.json` shows location 엔터관. `lintDebug` also passed (`lint-green.log`).
