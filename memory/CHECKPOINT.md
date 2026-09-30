# Checkpoint — Wireless phone update installed — 2026-09-21

## The story so far
Observed: launching the PC Tailscale desktop app changed NoState to Running. USB-assisted wireless ADB activation, phone identity match, and replacement install of the shuttle/meal APK succeeded. Installed APK hash matches, original first-install time is preserved, launch is OK and Home/Courses UI nodes were observed. No new Samsung notification visual acceptance is claimed. See device-update-20260921.md.

## Decided
- User authorizes directly launching the PC Tailscale app; the user handles phone Tailscale.
- Keep the phone awake during active agent use, then restore temporary screen settings.
- Requested phone ADB button must work through cellular and Tailscale; a Wi-Fi-only tile does not satisfy it.

## Waiting on the user
None for the completed update. Fully disabled ADB reactivation through a normal phone app is unsupported on the observed stock non-root setup.

## Next first action
Read memory/device-update-20260921.md before further device changes; inspect the ignored session's quick-settings residue only if continuing cleanup.

## Tried
- Native Wi-Fi debugging tile does not control cellular legacy TCP ADB.
- Samsung rejected component enable. Add-tile appended one inactive internal spec; removal/settings restore returned success but the spec remains, absent from the inspected visible panel. Original visible layout was preserved.
- A direct port-property write probe was blocked by automatic approval review and was not executed or retried.
- Screen timeout and stay-awake settings were restored and read back successfully.
