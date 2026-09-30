# DIMA Now UI system

Reviewed 2026-09-16. Material 3 Expressive is the design authority. Impeccable is a UX quality layer; its general web aesthetics do not override Material or Android behavior.

## Product tasks

Home answers what comes next: class, unfinished learning, departures and meals. Timetable edits personal classes and date exceptions. Shuttle compares departures and reports a missing vehicle. Meals shows a selected day's services and supports a confirmed photo submission. Courses reads official LMS learning and details. Settings controls account, guidance and updates. First run selects a return direction and offers optional permissions.

## From Material

- Use official Material components and their semantics, ripple, focus and disabled behavior.
- Use the standard type scale, paired semantic color roles, and the theme shape scale. Emphasize selected labels and important class/departure information selectively.
- Body uses `surface`; navigation uses `surfaceContainer` in both placements and themes.
- Small top app bars use their library height and title role. Settings and full-screen learning use leading Back controls.
- Compact navigation is a bar with five destinations; medium and wider windows use a rail (600dp breakpoint).
- Touch targets are at least 48dp. Text and dense data can grow with system font size.
- Spatial springs move/resize content; effects springs change opacity/colors without overshoot.

## Version boundary

Resolved BOM 2026.03.01 selects `androidx.compose.material3:material3-android:1.4.0`. Its `MotionScheme`, `MaterialExpressiveTheme` and newer expressive controls are internal. Dependencies remain unchanged. Use stable `FilterChip`, `SegmentedButton`, `ListItem`, `TopAppBar`, navigation and dialog APIs. Do not present a hand-built connected control as a shipped official Expressive component.

Verified against the Google Maven 1.4.0 source archive and [official release notes](https://developer.android.com/jetpack/androidx/releases/compose-material3). Custom spring values match that archive: default spatial 0.8/380, fast spatial 0.6/800, default effects 1/1600, fast effects 1/3800 (damping/stiffness).

## App design decisions (not prescribed Material values)

- Keep the existing DIMA magenta brand light/dark palettes rather than wallpaper-derived color.
- Bound reading content to 840dp; use 16dp page margins/section gaps and 8dp related-control gaps.
- Reserve filled/tonal emphasis for current class, next departure, operating meal, selected controls and actionable errors.
- D-083 retains the Home briefing's primaryContainer even on no-class days. Shuttle departure summaries use their established primary/secondary filled surfaces and error roles for last service; compact single-line content and full-timetable expansion remain.
- Group an independent Home summary or meal period into a card. School notices share one nonclickable surface with individual actionable list rows. Use uncontained sections for settings/forms.
- Keep ordinary metadata as text; distinguish LMS confirmed completion from inferred state. Preserve all selected-course, time, first/last service and source facts.
- A subtle 12dp page entry and 3% press response are app choices. Persistent status does not pulse. No delayed operational-list entrance.
- Date filters use equal-width visible cells and a today dot. Narrow windows or enlarged text use balanced additional rows, preserving 48dp targets and accessible today/selected states. Navigation and body colors retain the same roles as width changes.
- Page titles retain the small app-bar TitleLarge scale with Medium500 emphasis. This is a selective app choice matching the 1.4 emphasized token, not a global font change.
- Shuttle summaries prioritize the nearest departures and expand complete times in page flow without an internal horizontal scroller. Pause setup is a state/action row above the timetable, with a duration dialog before saving.
- Keep the selected meal date pinned through automatic service focus. Operating hours carry emphasis over a quiet meal surface; source origin notes remain intact as supporting text.
- Short exclusive settings use stable 1.4 segmented rows, with radio rows when labels or font size need more room. Newer Expressive connected groups are unavailable in the resolved library; do not simulate their anatomy.
- Summary widgets use a flat three-summary layout below 300dp width or 180dp times font scale in height. Larger widgets retain separated detailed sections. Ellipsized summaries keep complete accessibility descriptions.

## Content and interaction

Use `홈`, `시간표`, `셔틀`, `식단`, `수업`, `설정`. User-visible campus stops are `엔터관`, `본관`, `원룸촌`. Preserve complete learning and meal content, first/last departure facts, protected deletion and photo-sharing confirmation, optional permissions and Android Back behavior. Errors state the failed operation and an available recovery; technical diagnostics belong in the expanded diagnostics section.

## Evidence

See [the redesign review](memory/ui-redesign-20260916.md) for scope, verification and limitations. Screenshots and device artifacts remain local and ignored under `artifacts/m3-redesign-20260916/`.
