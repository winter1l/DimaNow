# Checkpoint — Figma visual verification — 2026-09-17

## The story so far
Read-only visual audit of the existing Figma transfer is in progress. Fresh connector renders show real fidelity defects: timetable button overlaps weekday, home preview has wrong rendered fill, persistent controls missing from empty/error variants, native pickers and LMS states differ. The earlier horizontal-bound check is insufficient evidence of a faithful transfer. App and Figma content are unchanged.

## Decided
- D-084 remains: current source is the baseline for later user-directed revisions.
- This request is verification; collect evidence and report defects before any fixes.

## Waiting on the user
None for this verification.

## Next first action
Finish main-page state/tablet visual inspection and consolidate artifacts/figma-visual-verification-20260917/report.md with the three area reports.

## Tried
- Browser editor is not signed in and shows an access/not-found screen; authenticated connector renders work. Do not claim browser-canvas verification.
- Horizontal overflow checks missed vertical overflow and missing controls.
