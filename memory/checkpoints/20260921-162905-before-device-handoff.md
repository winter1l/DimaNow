# Checkpoint — Shuttle summary fixed; phone update pending — 2026-09-21 15:55

## The story so far
D-087 fixes confusing multiple shuttle departure times in the notification. Immediate boarding leg only: 원룸촌→본관,6분후출발·15:50, matching chip.18 Android tests and optimized emulator update/launch passed. New APK includes earlier meal repair. Source details: shuttle-summary-20260921.md; earlier meal production repair/CI remains verified.

## Decided
- User requested simple shuttle notification display.
- Preserve official stop names, itinerary/full schedule, class/4402 and60-minute policy.

## Waiting on the user
Phone Tailscale/wireless-debugging connection; last-known endpoint did not respond.

## Next first action
Read .local/device-connections.json privately after reconnection, then replacement-install dist/DIMA-Now-1.5-shuttle-fix-20260921.apk and observe the actual One UI notification.

## Tried
- Old title promoted following46min while chip showed first6min; deterministic test reproduced this.
- No physical phone rendering observed; emulator notification-object tests do not prove One UI placement.
