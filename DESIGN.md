# DIMA Now UI system

Reviewed 2026-10-01 after the D-093 baseline and the D-094 improvement pass. Material 3 is the design authority; Impeccable is a UX quality layer and its general web aesthetics do not override Material or Android behavior.

## Product tasks

Home answers what comes next: class, unfinished learning, departures, meals and notices. Timetable edits personal classes, term dates and no-class dates. Shuttle compares campus departures and the 4402 bus. Meals shows a selected day's services and supports a confirmed photo submission. Courses (수업) reads official LMS learning and details. Settings controls location, guidance, Live Update/Now Bar, data sources and updates. First run selects a return direction and offers optional permissions.

## Version boundary

The resolved Compose BOM selects `androidx.compose.material3:material3-android:1.4.0` (stable). Its `MotionScheme`, `MaterialExpressiveTheme`, `LoadingIndicator` and connected button groups are internal or unavailable, so the app uses stable `TopAppBar`, `NavigationBar`, `FilterChip`, `SegmentedButton`, `RadioButton`, `ElevatedCard`, `DatePicker`, `AlertDialog` and `PullToRefreshBox`. Do not present a hand-built control as a shipped Expressive component. Verified against the [release notes](https://developer.android.com/jetpack/androidx/releases/compose-material3).

## Code layout

Package `com.example.dimanow.ui`: `DimaNowApp.kt` (root, tabs, routes), `HomeScreen.kt`, `TimetableScreen.kt` + `TimetableDialogs.kt`, `ShuttleScreen.kt` + `Bus4402Screen.kt`, `MealScreen.kt`, `SettingsScreen.kt`, and shared parts (`ScreenScaffold.kt`, `DesignComponents.kt`, `SettingsChoice.kt`, `TimetableChips.kt`, `DataLoadState.kt`, `StateMessages.kt`, `DateLabels.kt`, `SystemIntents.kt`; `@Preview`s in `ComponentPreviews.kt`). Motion lives in `ui/motion`, dialogs in `ui/schedule`, first run in `ui/onboarding`, tokens in `theme/`. The LMS UI is split into `LmsScreen.kt` (route), `LmsItemsScreen.kt`, `LmsDetailScreen.kt`, `LmsLoginScreen.kt`, `LmsOfficialCourseWebView.kt` and `LmsWebViewSupport.kt`.

## Tokens

- **Color**: the fixed DIMA magenta light/dark schemes (D-024, D-027), not wallpaper color; D-083 role assignments stand. Body uses `surface`; the navigation bar uses `surfaceContainer`.
- **Shape** (`DimaShapes`): `Card` 20dp for every card or section container, `Tile` 12dp for departure capsules, inset panels and selector cells, `Badge` 8dp for tags, status chips and timetable chips, `Dialog` 28dp (the AlertDialog default). Buttons keep their full Material shape; pills use `CircleShape`. No `RoundedCornerShape(n.dp)` literals in screens.
- **Type**: Material 3 baseline sizes. Body and label keep baseline weights; headline and title roles are Medium (500). One emphasized weight, SemiBold (600) via `TextStyle.emphasized()`, is reserved for screen titles, next-departure countdowns, the primary Home briefing line and selected state. No blanket bold.
- **Motion** (`DimaMotion`, the only motion tokens): *spatial* springs (damping 0.9; fast 1400, default 700, slow 300 stiffness) move or resize; *effects* springs (damping 1; fast 3800, default 1600, slow 800) change color or opacity without overshoot. Tabs use a 30dp shared-axis X transition on effects springs. A list rises 12dp only on its first appearance; after a tab switch the transition is the only motion. Press feedback (`expressiveBounceClick`) scales to 0.97 and is dropped when system animations are off. Persistent status never pulses.

## Structure and components

- **App bar**: every primary screen, Settings and Courses use `ScreenScaffold` with one pinned small `DimaTopAppBar` (headlineMedium emphasized title, `surface` tinting to `surfaceContainer` once content scrolls under it). An optional sub-header (mode switch, day selector) is pinned beneath and tints with it. The settings gear sits at the end of the bar; Settings has a leading Back action and no navigation bar. Timetable adds classes with an extended FAB.
- **Content** is a `LazyColumn` of keyed items with 16dp side margins; pull-to-refresh only where a source can refresh. Tabs keep their scroll state through a saveable state holder. The LMS detail pane and onboarding cap reading width at 840dp.
- **Navigation**: a five-destination bottom `NavigationBar` (홈, 시간표, 셔틀, 식단, 수업); the active indicator alone marks selection. The app is portrait-only (D-054).
- **Cards**: Home summaries are whole-card taps (`HomeSummaryCard`) with a spoken click label. Notices share one card with individually actionable rows. Settings and forms use uncontained sections.
- **Single choice** (`SettingsChoice`): a `SingleChoiceSegmentedButtonRow` for 2–5 options whose labels fit, otherwise radio rows; both at least 48dp tall. Expandable sections use `SettingsExpander` with expanded state and expand/collapse actions. Destructive confirms use error-colored actions.
- **Day/date selectors** (`DimaDaySelector`, `DimaDateSelector`): equal-width `FilterChip` cells with the weekday initial, the day of month for dates, and a today dot; cells are ≥52dp tall, announce as tabs with `오늘`/`오늘, 선택됨` state, and wrap into balanced rows on narrow widths or large text. Term and no-class dates use Material `DatePicker` dialogs.
- **Departures**: `DepartureCapsule` puts the countdown/service label on one line and the clock beneath, for Home, the campus shuttle and 4402 alike. The full timetable is an always-visible horizontal `TimetableChipRow`: chips are ≥32dp tall; tone marks next, second, first, last (error roles, D-016) and past (readable text with an outline, not a fade); every chip has a spoken description such as `다음 출발 21:30` or `21:50, 막차`, so meaning never depends on color alone.
- **Meals**: the selected date stays pinned through automatic service focus; operating hours carry emphasis over a quiet meal surface, and a closed meal card uses surface tokens in both themes. Origin notes remain as supporting text.
- **Widgets**: below 300dp width or at 180dp times the font scale in height, summary widgets use a flat three-summary layout; larger widgets keep separated sections. Ellipsized summaries keep complete accessibility descriptions.
- **Countdowns**: minutes are shown only within 60 minutes (`12분 후`, `시작까지 12분`); further away the clock is named (`다음 출발`, `13:00 시작`, `내일 13:00 시작`).
- **States**: a source that has not emitted yet shows `LoadingLine`, never an empty or error message. `EmptyState` explains an empty result with an optional action; `ErrorState` says what failed in plain words with a retry where the screen can refresh. Refresh snackbars appear only after a user-initiated refresh; undo snackbars follow no-class deletion.

## Content and voice

Use `홈`, `시간표`, `셔틀`, `식단`, `수업`, `설정`. User-visible stops are `엔터관`, `본관`, `원룸촌`, with `운동장` when the departure boards at the stadium stop (D-090). All copy uses one polite 해요체 voice. Errors name the failed operation and a recovery; technical diagnostics stay in Settings. Dates read as `9월 30일 (수)`, adding the year only when it differs. Preserve complete learning and meal content, first/last departure facts, protected deletion, photo-sharing confirmation, optional permissions and Android Back behavior.

## Accessibility

Touch targets are at least 48dp. Text and dense data grow with the system font size instead of clipping. Selection, today, expanded and past states are exposed to screen readers, and color is never the only signal. Decorative motion stops when system animations are removed.

## Evidence

See [the D-094 review](memory/ui-review-20260930.md) and [the earlier redesign review](memory/ui-redesign-20260916.md). Screenshots and device artifacts remain local and ignored under `artifacts/`.
