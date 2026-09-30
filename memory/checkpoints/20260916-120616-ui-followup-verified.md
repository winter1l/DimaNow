# Checkpoint — approved UI follow-up in progress — 2026-09-16

## The story so far
Confirmed: user approved all combined physical UI and official M3 review findings. Implementation is underway. Root owns Home/meal/common header; audit_main timetable/shuttle; theme_motion weekday selector/type/tests; settings_polish settings/LMS title. Existing dirty implementation is preserved. Prior build/test/device evidence applies only to the earlier snapshot.

## Decided
D-082: restore shared notice card, visible equal-width weekdays with today dot, emphasized page titles, compact shuttle summaries with expandable full times, pause actions at timetable top, quieter empty/open meal states, pinned meal date, origin supporting text and consistent exclusive settings controls. Preserve functional data/flows and Material3 1.4 compatibility.

## Waiting on the user
None.

## Next first action
Review active agents' scoped edits, finish Home/meal changes, then build and run focused instrumentation on emulator-5554 before updating the phone.

## Tried
Earlier over-distill removed useful notice containment. Unconditionally stacked route headers and scrolling day rows reduced scanability. Avoid those patterns; narrow layouts deliberately wrap. Use ignored local connection handoff for phone identity.
