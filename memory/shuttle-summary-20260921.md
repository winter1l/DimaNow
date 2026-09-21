# Shuttle notification simplification — 2026-09-21

## Confirmed request and cause
User supplied lock-screen/status-chip photos at15:44 and requested a simple fix for 원룸촌 travel toward the dormitory area. Current CSV and GuidanceEngine explain all displayed values: 원룸촌→본관15:50 (6min), subsequent first-leg16:30 (46min), and 본관→엔터관15:55 transfer (11min), followed by16:45. The controller promoted followingMinutes into the title and printed the transfer's raw text without its destination.

## Implemented presentation
Campus shuttle-only notifications now summarize the immediate boarding leg: title `원룸촌 → 본관`, body `6분 후 출발 · 15:50`, chip `본관행 6분`. The same first vehicle drives all three. Later vehicles and transfer lines are omitted from this notification summary. Existing engine itinerary, full timetable, official stop names, sixty-minute visibility threshold, occurrence dismissal, 4402 and class presentation are unchanged. At the departure instant the body becomes `곧 출발 · 15:50`; once it passes all surfaces advance to the next first-leg vehicle.

## Observed verification
- Added deterministic engine→Android notification regression using the photo's time/route structure; before modification both tests failed, including expected route title vs actual `원룸촌 출발 · 다음 차46분`.
- After modification ShuttleNotificationSummaryTest2 and LiveSurfacePresentationTest16 passed on API36 emulator (18 total). Coverage includes locked/unlocked options, transfer omission, departure rollover, class/4402 behavior and occurrence action.
- Debug/test/optimized builds passed. Optimized replacement installation succeeded on emulator, launch Status ok and process alive.
- APK: dist/DIMA-Now-1.5-shuttle-fix-20260921.apk; SHA2568EC0C3AA0EC705E126C0431902893987C7878E097FDBEA73FF9C15298B5E34E6. Includes the earlier meal repair.
- Phone last-known endpoint did not respond. Physical One UI rendering and phone replacement are unverified; user reconnection remains pending. Do not claim Samsung Now Bar visual acceptance from notification-object tests.
- Photos, ADB logs and builds remain ignored; evidence in .local/shuttle-summary-20260921/. Independent focused source review found no blocking issue.
