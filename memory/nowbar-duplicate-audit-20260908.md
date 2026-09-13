# Locked Now Bar repeated shuttle countdown - 2026-09-08

Scope: investigation only of the user's second screenshot (Tuesday 18:50). The image shows one Now Bar card with the next two departure countdowns above and the first destination/countdown below. ADB listed no connected devices during this investigation. No app code, tests, device settings, installed apps, or posted notifications were changed.

## Confirmed app-side cause

- GuidanceEngine's MAIN return branch (around 438-475) creates one ShuttleLine with the boarding-origin text plus both remaining times, as well as separate destination and first-minutes metadata.
- AndroidLiveSurfaceController.buildNotification (around 283-328) maps the first ShuttleLine.text to ContentTitle, excludes that first line from ContentText, and separately supplies LiveSurfaceController.statusChipText as shortCriticalText.
- statusChipText (102-120) returns the first shuttle's destination plus minutes for SHUTTLE_DEPARTURE. Its deviceLocked argument is deliberately unused, so the same short text is supplied in both states.
- The regular title/body duplicate was already handled by dropping the first shuttle line from the body. That does not prevent the nearest countdown appearing in both title and shortCriticalText.
- Controller posts use NOTIFICATION_ID 6201 (around 181-203 and 392); LiveMinuteUpdateService.startForeground uses that same ID. Source inspection does not suggest a second notification for the two text lines in this image. Current device active-notification count was not available to verify.

## Reproduction and evidence limits

The standalone ignored NowBarDuplicateProbe.java invokes the real compiled GuidanceEngine.snapshot and LiveSurfaceController.statusChipText at 2026-09-08 18:50 KST, using an ended fixture class and MAIN-to-YEIN departures at 18:55 and 19:25. The fixture class merely selects the post-class return branch; it is not a statement about the user's course data.

    PHASE=RETURN
    SHUTTLE_LINE_COUNT=1
    TITLE_SOURCE=<boarding-origin>  5 minutes, 35 minutes
    SHORT_CRITICAL_TEXT_LOCKED=<YEIN-bound> 5 minutes
    SHORT_CRITICAL_TEXT_UNLOCKED=<YEIN-bound> 5 minutes
    REMAINING_SHUTTLE_BODY_LINES=[]
    SYMPTOM_REPRODUCED: the nearest departure is repeated in title and shortCriticalText

Actual Korean output is preserved in artifacts/nowbar-duplicate-audit-20260908/reproduction.log. Exit 1 deliberately flags the duplicate-content condition. The strings match the supplied image. Their exact mapping into Samsung's two locked rows is strongly supported by the image plus source, but was not re-observed on a live connected device this turn. No claim is made that the app posted two active notifications.

## Why prior tests missed this case

LiveSurfacePresentationTest.shuttleOnlyGuidanceDoesNotRepeatTheSameLineAsTitleAndBody uses a ShuttleLine without destination/minutes and a snapshot without SHUTTLE_DEPARTURE, so it asserts an empty body and null shortCriticalText. Separate newer tests intentionally assert a destination-bearing shuttle shortCriticalText, but do not test the combined title-plus-short-text presentation on the locked Samsung card. These independent expectations leave the actual combined duplication unchecked.

## Next implementation boundary

The fields should carry complementary information rather than repeat the first countdown. A change must retain useful destination/countdown information in the unlocked compact status chip, and verify the resulting locked card separately on the user's Samsung device. Simply removing shortCriticalText may undo D-061's destination-bearing shuttle chip. Investigation only: no content policy, notification payload, or UI fix was applied.
