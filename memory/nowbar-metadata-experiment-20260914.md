# Samsung Now Bar metadata-only experiment - 2026-09-14

## Scope and controlled variables

Confirmed: continue the user-requested side-thread investigation into showing DIMA Now without Samsung's developer test option. Keep applicationId `com.example.dimanow`; test only application metadata `com.samsung.android.support.ongoing_activity=true`. Do not borrow LiveBridge's Samsung-build package ID or alter other apps. This experiment tests sufficiency of that metadata; it does not determine all Samsung eligibility rules.

Observed device: `test-device-galaxy-api37-a`, SM-S918N, Android API 37, One UI property 90000. This is One UI 9 evidence, not One UI 8 acceptance. Initial package was v1.5/code 6, target 36, Debug, last updated September 13 at 21:23:53. The pulled installed APK exactly matched the local Debug APK, SHA-256 `09C4AA3606970162565DB2E73B14C36735E162D75C5CC1D49DDA536DE6114EB1`.

Observed control: a temporary, explicitly selected instrumentation method called the existing `AndroidLiveSurfaceController.buildNotification` seam. It posted only ID 6229 on the existing silent DEFAULT live channel, with a five-minute timeout and the title `DIMA Now · 표시 확인`. It used a 20-minute shuttle-shaped snapshot, did not start the guidance updater, and did not edit timetable, preferences, location, clock, LMS data, or LiveBridge rules. The notification met `hasPromotableCharacteristics()`. QuickStar's ongoing-chip hiding value was held at 0 during comparisons, from original 1.

## Results

| Installed APK | Developer test option | Android promoted flag | Samsung Showing list | Physical lock-screen result |
|---|---|---|---|---|
| Original, no metadata | OFF | Present | DIMA absent | No DIMA card; initial capture was AOD |
| Same original APK | ON | Present | DIMA present, promoted=true, icon/primary/secondary fields present | DIMA test card visibly rendered with title and 20-minute text |
| Metadata-only APK | OFF | Present | DIMA absent immediately and after settling | DIMA card absent on the awake lock screen |

Observed: app notification settings had no `실시간 정보` switch before or after metadata installation. Both snapshots exposed the same ordinary notification controls. An exhaustive all-app support-list inspection was not repeated in this main-thread experiment; the side-thread report supplied that earlier observation.

Observed APK isolation: the candidate hash was `2E480A3901A09369E394F3540DB677885A60DACA764CEE809AD8DDFC5B37871C`. SHA-256 comparison of every uncompressed ZIP entry found only `AndroidManifest.xml` changed. All DEX and resource entries matched the installed baseline. AAPT2 confirmed the metadata boolean true in the packaged manifest. Application ID, target, version, signing identity, and notification construction remained unchanged.

Conclusion (observed): on this device under package-replacement testing, that metadata alone did not remove dependence on the developer test option. Android's promoted flag alone is insufficient evidence of Samsung Now Bar display. Unknown: whether a cold device reboot changes Samsung's eligibility cache, which additional OEM criteria apply, or whether an official admission path exists for this package. No reboot or package-identity workaround was attempted.

## Evidence

All raw artifacts are local and ignored by Git under `artifacts/nowbar-metadata-20260914/`:

- `baseline-off.txt`, `baseline-on.txt`, `metadata-off.txt`, `metadata-off-settled.txt`: bounded notification/SystemUI observations. Lines timestamped as historical ADDED/INFLATED/REMOVED events are logger history, not current Hidden-list membership.
- `baseline-off-settings.json`, `metadata-off-settings.json`: app-notification settings controls.
- `baseline-on-lock.png`: positive physical control; `metadata-off-lock.png`: negative physical metadata result. `baseline-off-lock.png` is AOD, not an awake-lock-screen capture.
- `apk-entry-diff.txt`, `metadata-build.log`, `probe-build.log`: artifact isolation and narrow build evidence.
- `installed-before.apk`, `metadata-only.apk`, `installed-restored.apk`: exact comparison/rollback artifacts.
- `baseline-off-probe.log`, `baseline-on-probe.log`, `metadata-off-probe.log`, and clear logs: explicitly selected single-probe runs. No full regression suite ran on the phone.

## Restoration

Observed: the test notification was canceled, the temporary test package uninstalled, and original settings restored (`enable_notification_nowbar_test=1`, `quickstar_indicator_hide_ongoing_chip=1`). The original installed APK was restored with `install -r`; pulling it again produced the exact original SHA-256. This preserves the pre-experiment Debug installation; it does not resolve the separate Q-009 optimized-artifact question. The metadata source change and temporary probe source were removed, and normal Debug/test APKs rebuilt successfully without the probe. No product source diff, release, push, commit, or other-app changes were retained.

## Primary-source context

[Android Live Updates documentation](https://developer.android.com/develop/ui/views/notifications/live-update) explicitly distinguishes promotable characteristics, permission, and actual promotion, and allows additional OEM eligibility criteria. It does not document Samsung's per-package eligibility rules.

[LiveBridge's author FAQ](https://appsfolder.github.io/livebridge/#faq-samsung-build) describes its Samsung-specific package replacement flow. The side-thread APK observation combined a different package ID with metadata, so that example did not isolate metadata sufficiency. The present experiment does.
