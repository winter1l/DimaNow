# Phone update and cellular ADB feasibility — 2026-09-21

## Confirmed instructions
The user authorizes launching the PC Tailscale desktop app directly when needed; do not ask them simply to open it. The user handles phone Tailscale. Keep the phone awake during active operation, snapshot temporary settings privately and restore them afterward. A requested phone-side one-click ADB control must support cellular plus Tailscale.

## Observed update
- PC app launch changed the backend from NoState to Running; the saved wireless target connected and matched the USB phone identity.
- Wireless replacement install of dist/DIMA-Now-1.5-shuttle-fix-20260921.apk succeeded. Launch returned Status ok; Home navigation and Courses card UI nodes were observed.
- Installed APK SHA256 matched 8EC0C3AA0EC705E126C0431902893987C7878E097FDBEA73FF9C15298B5E34E6. Original first-install time was preserved. No app data clear/uninstall was performed.
- Screen was awake during use. Original timeout and stay-on settings were restored and read back successfully after the session.
- This supersedes phone-install-pending notes for the shuttle and meal repairs. It does not prove live Samsung notification appearance.
- Private device targets, snapshots, raw UI and command evidence remain in the ignored 2026-09-21 helper session folder under .local/; connection handoff remains .local/device-connections.json.

## Feasibility and limits
A normal non-root application cannot start a fully disabled legacy ADB listener after reboot merely through cellular/Tailscale. Tailscale supplies reachability, not Android system privileges. Native wireless debugging uses Wi-Fi/TLS and does not reactivate the legacy TCP listener. Shizuku is not installed; its non-root shell privileges do not grant arbitrary adbd property writes. No misleading cellular-enable button was added to DIMA Now.

Primary sources checked: https://android.googlesource.com/platform/packages/modules/adb/+/HEAD/docs/dev/adb_wifi.md ; https://android.googlesource.com/platform/system/sepolicy/+/main/private/property.te ; https://shizuku.rikka.app/guide/setup/ .

A direct setprop port-control probe was rejected before execution by automatic approval review because the concrete legacy port exposure/scope lacked explicit approval. It was not retried. Already authorized USB tcpip activation had succeeded earlier; subsequent wireless installation succeeded.

## Remaining internal settings residue
The preliminary native Wi-Fi tile trial appended custom(com.android.settings/.development.qstile.AdbWirelessDebuggingDevelopmentTile) to sysui_qs_tiles. Samsung rejected enabling its component. The tile was absent from the inspected visible quick panel and edit screen. remove-tile, set-tiles and restoring the saved secure list returned without errors, but readback retained the one extra inactive spec. Original 34 specs remained in order and visible layout was preserved. Do not claim exact internal settings restoration. No reboot or SystemUI kill was used to force cleanup.
