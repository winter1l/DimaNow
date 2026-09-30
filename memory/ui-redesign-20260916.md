# UI redesign — 2026-09-16

## 1. Analysis (confirmed from source; incumbent appearance observed in local captures)

Reviewed Home, Timetable, Shuttle, 4402, Meals, Settings, permission setup, three-step onboarding, LMS login/list/detail/browser surfaces, schedule dialogs, theme and motion. Existing screenshot references were checked against current source; an unchanged APK was also run on API 36 emulator before the new build.

| Category | Finding | Decision |
| --- | --- | --- |
| Good Expressive use | Home class priority, nearest departure, active meal; paired brand colors | Preserve selective emphasis |
| Material without useful hierarchy | Repeated elevated cards, many equivalent bold titles and badges | Quiet lists and metadata |
| Incorrect Material use | Nonstandard type/shape defaults; black-blended closed meal; low-alpha historical times | Restore type/shape scale and semantic colors |
| Inconsistency | Two different header/inset/scroll systems, duplicated day selectors | One pinned official app bar and shared selector |
| Custom behavior divergence | Pointer gesture callbacks bypass native click semantics; radio selections lack roles | Native clickable/ripple and selection semantics |
| Structural redesign | Course/LMS/notice cards, settings card maze, stretched wide screens | List rows, uncontained setting sections, rail and bounded reading pane |

## 2. Design

Confirmed request: execute the seven ordered stages, with Material 3 Expressive taking precedence over Impeccable. [DESIGN.md](../DESIGN.md) separates published Material behavior from project-specific choices.

All existing feature routes remain in scope: schedule CRUD/one-off changes/pause, shuttle and 4402 calendars/reports, meal selection/photo confirmation/upload state, LMS account/list/filter/completion/detail/attachments, notification modes, permission setup, app updates and diagnostics. UI work does not change repositories, network authentication, planners or database schemas.

## 3. Material implementation

Observed: initial implementation compiled with `:app:compileDebugKotlin :app:compileDebugAndroidTestKotlin` (BUILD SUCCESSFUL). Resolved Material3 1.4.0 verified through Gradle dependencyInsight and version-matched official sources. No dependency upgrade.

## 4. Impeccable distill

The context launcher could not create its external cache. No PRODUCT.md or DESIGN.md existed at that point. Continued using project decisions/source and the skill's distill, craft-floor, Operate and Android references; authored DESIGN.md from the resolved design. The native audit applies to Compose source, not CSS detectors.

Removed redundant detail title/body heading, decorative status containers, repeated onboarding icons, duplicate Home learning shortcut, and unnecessary settings/permission/empty-state card surfaces. Meaningful meal-period and actionable Home surfaces remain.

## 5. Impeccable clarify

Reviewed visible Kotlin UI copy across main destinations, settings/setup, onboarding and LMS, including empty/loading/error states and helper labels. Combined meal hours and operational state, named refresh outcomes, removed repeated Today labels, shortened onboarding language, clarified account storage, update permission and learning actions. Technical source timestamps remain in diagnostics. School-origin learning/menu content is not rewritten. Exact text tests now follow the combined visible labels.

## 6. Impeccable final quality

Observed initial capture batch: 45 images across light phone, dark phone at 1.3 font scale, and expanded window. Inspected Home, timetable, shuttle, meals, Courses, settings/diagnostics, synthetic LMS list/cache/error/loading/empty and all three onboarding steps. Fixed duplicate loading indicators, duplicate meal status, awkward welcome wrapping and an inaccurate outside-campus shuttle heading. Course/term forms scroll with the keyboard; term date errors are inline and prevent invalid saves. Reading panes are bounded, navigation switches to a scrollable rail, and day controls scroll rather than shrink touch targets.

Observed additional defect: the summary widget's declared minimum 250x110dp clipped its middle section. A real RemoteViews measurement test reproduced the failure before a presentation-only compact layout was introduced. Planner, minute update scheduling and click routing remain unchanged.

## 7. Material 3 Expressive revalidation

| Area | Final rule and evidence |
| --- | --- |
| Component choice | Official app bars, navigation, chips, list rows, dialogs and fields from the resolved 1.4.0 API; newer internal APIs are not falsely represented as supported |
| Anatomy | Leading Back in subordinate views; native selection/disabled/ripple; meaningful independent cards only |
| Tokens | Central reading width/margins/day selection; theme shape scale replaces arbitrary card radii |
| Typography | Standard Material type scale with selective importance; long metadata wraps |
| Color | Paired semantic roles in both themes; removed low-alpha departure text and black-blended closed surfaces; audited text pairs at least 5.51:1 |
| Shape | Theme shapes for grouped surfaces, native component shapes for routine controls |
| Motion | Version-matched spatial/effects springs; persistent state remains still; click gestures use native semantics |
| Interaction | Existing routes and actions retained; focused tests cover nested clicks, keyboard activation, date errors, refresh and navigation |
| Adaptive | Bar below 600dp, rail above; 840dp reading bound, horizontal day scrolling, keyboard-safe forms and compact summary widget |
| Accessibility | 48dp touch bounds, selectable groups, full-row switch/radio semantics, live error announcements and complete widget descriptions |

## Verification status

Final result details follow below. Evidence is from the API 36 test emulator and synthetic fixtures; no new physical phone/One UI Now Bar acceptance or live school authentication is claimed.

### Final verification — 2026-09-16

- Confirmed build: `:app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` passed after the last widget change (`widget-text-final-build.log`). Unit test XML: 46 suites, 246 tests, zero failures/errors (`final-build.log`).
- Observed API 36 emulator functional coverage: 91 distinct UI/widget tests have passing latest results. Initial 89-test run had nine failures; corrected expectations for combined labels and renamed copy, updated help navigation to its retained diagnostics route, and replaced the old tween midpoint assumption with actual spring-frame/opaque-endpoint assertions. Focused rerun 38/38 passed. Dark-large and expanded checks each 5/5 passed; final LMS smoke 8/8 passed. This is combined coverage across runs, not one clean 91-test invocation.
- Observed screenshots: first45 inspected before fixes, then57 confirmation images across phone-light, phone-dark at1.3 font scale and expanded-light. A visible partial second line remained in the tiny light widget despite the first geometry test. Compact text now always uses one-line ellipsis; strengthened tests check rendered text height, and final widget checks passed2/2 in each theme. Four final widget images reviewed; all three summaries remain visible. Complete strings remain available through accessibility and the app.
- Material final check also removed duplicate toolbar progress from manual login and rendered-page loading while keeping centered progress and callbacks. These two live web shells were source-reviewed; authenticated school sessions were not exercised.
- Functional coverage includes selected-day scrolling, narrow/wide/short navigation, nested touch and keyboard activation, official IconButton touch bounds, term validation, refresh, schedule display, meal service states, settings system intents, LMS input/filter/native details and widget layout. Screenshot checks are bounded examples, not proof of every content/device combination.
- Confirmed `git diff --check` and tracked-private-data check passed. All generated screenshots/logs/APKs remain ignored. No backend, database schema, authentication policy, dependency, or publication changes.
- Final APK: `app/build/outputs/apk/debug/app-debug.apk`, SHA256 `1E32BD7B33AF1223BD3D37E5D3BC59B13490A56277BE741434034E9F24B1F9A3`. Installed on the explicitly selected test emulator. Window size/density, font1.0 and light theme restored after tests.
- Unknown: physical phone appearance, One UI Now Bar acceptance, live school login and launcher-specific widget presentation. No physical phone update was performed in this task.
