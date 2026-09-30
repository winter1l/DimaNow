# Physical phone UI review — 2026-09-16

## Scope and evidence

Confirmed request: explain removal of Home notice card and variable-width scrolling weekday controls; inspect other UI/UX problems directly. User prefers the previous today dot over visible today text. This is a read-only review of application code: no implementation, install, settings changes or schedule saves were performed.

Observed on the connected Galaxy SM-S918N over wireless ADB, 11:05–11:12 KST, current dark theme. Captured Home, Shuttle, student/dorm meals, timetable, course editor before/after horizontal scroll, settings top/bottom, and Courses Today/All. Used Android CLI layout trees and actual screen captures. Artifacts are local and ignored under `artifacts/phone-ui-audit-20260916/`. Closed the unsaved editor and restored meal/Courses filters. No learning item was opened, so this review does not exercise course attendance or submissions.

## What went wrong in the redesign

Official component use is not sufficient evidence of a suitable screen composition. The prior review focused on geometry, readable text, callbacks and component semantics. It did not adequately reject hidden fixed-week options, disproportionate emphasis or weakened grouping. Notice-card removal and visible today labels were discretionary app design decisions, not M3 Expressive requirements.

## Findings and recommended changes

| Priority | Observed finding | Recommended direction | Evidence |
| --- | --- | --- | --- |
| High | Shuttle shows Monday–Saturday but Sunday is almost entirely offscreen. Wednesday's today label makes it much wider. Student meal dates occupy only the left part of the available width. | Equal-width weekday/date cells. Reserve a consistent dot slot for today; preserve full date/today accessibility announcements and selected semantics. Maintain 48dp touch targets and adapt genuinely narrow cases explicitly. | `shuttle.png`, `meal.png`; DesignComponents.kt91–110 |
| High | Course editor initially shows only Monday–Friday; no clear hint that weekend choices exist. Horizontal swipe reveals Saturday/Sunday. | Show all seven choices visibly using a layout that fits the dialog or a deliberate additional row. Do not solve this by shrinking touch targets. | `course-dialog.png`, `course-days-scrolled.png` |
| Medium | Home school notices are the only comparable information group without a surrounding surface. Its outer heading and inset list rows also use different left edges. | Restore one nonclickable notice-group surface containing heading/action and native list rows. Keep individual links, two-line titles and touch areas. | `home.png`; DimaNowApp.kt1184–1222 |
| Medium | Home no-class state occupies a large saturated magenta hero despite having no immediate task. | Give no-class a quieter state-specific surface/type treatment; preserve tomorrow's preview. Reserve strongest emphasis for imminent/ongoing class information. | `home.png`; DimaNowApp.kt794–806,876–899 |
| Medium | Operating student meal uses saturated fill, bright border and a filled status badge simultaneously. | Retain operating-state emphasis but reduce competing cues, e.g. a quieter surface with a clear operating badge. | `meal.png`; DimaNowApp.kt2703–2724,2751–2757 |
| Medium | Dorm opens at upcoming lunch and full month/date heading is no longer visible, unlike the student meal page. | Preserve D-064 intentional focus on current/next meal; keep selected full date visible in the pinned header. | `dorm.png`; DimaNowApp.kt2395–2405,2646; DECISIONS.md D-064 |
| Medium | Settings repeats separate, unequal-width chips for mutually exclusive choices, resulting in a long and visually repetitive screen. | Evaluate official single-choice segmented rows for short two/three-option groups; use wrapping radio rows if long labels cannot fit. Do not turn the seven-day picker into a cramped seven-segment control. | `settings.png`, `settings-bottom.png` |
| Lower | Dorm food-origin lines have the same type size/weight as the menu names, slowing scanning. | Preserve all source content; render verified origin notes as supporting text within their dish entry. | `dorm.png`; DimaNowApp.kt2570–2586,2681–2691 |

Confirmed positives within inspected scope: primary navigation and settings Back remained reachable; timetable rows and course menu controls are distinct; Courses list preserves readable full titles and filters. No new blocker was observed in those captured views. This does not establish all-theme/all-font or Now Bar acceptance.

## Material interpretation

- From Material: cards group related content, and filter chips are valid selectable/filter components. A card is not prohibited merely because list rows appear inside it. [Card](https://developer.android.com/develop/ui/compose/components/card), [Chip](https://developer.android.com/develop/ui/compose/components/chip).
- From Material: a single-choice segmented group communicates one selection among options. [Segmented button](https://developer.android.com/develop/ui/compose/components/segmented-button).
- App decision: equal-width weekly cells, a today dot, notice grouping and quieter empty-state styling. These are recommendations for this product, not claims of exact universal Expressive mandates.
- Resolved project Material3 remains1.4.0. Newer internal Expressive APIs are not needed to fix these composition problems.

## Recommended order

1. Restore notice grouping and redesign shared day selection with the today dot, including the course editor.
2. Rebalance Home empty-state and active meal emphasis.
3. Keep dorm date context visible and clarify menu/metadata hierarchy.
4. Consolidate short exclusive settings controls.
5. Verify the changed views on this physical phone, with dates/today/selection distinct and no hidden weekly choices.

Status: review complete; recommendations are not implemented.

## Approved implementation follow-up — 2026-09-16 (D-082)

Confirmed: the user approved applying this review together with the later official-site audit. The earlier read-only status above describes the original review, not the current implementation.

Implemented changes:
- One nonclickable school-notice card groups individual native list links and the all-notices action.
- Shared equal-width weekday/date cells keep every choice visible; a today dot retains full accessibility text. Narrow width and measured large text use additional balanced rows.
- Native small app bars retain TitleLarge geometry with selective Medium500 page-title emphasis. Main destinations and LMS share this style.
- Campus shuttle and4402 prioritize clock time plus countdown only within60 minutes. Compact first/last metadata and native expansion retain complete wrapped departure lists, first/last markers, past/next roles and boarding/report warnings.
- Pause setup is above term dates and courses. Configure/change opens the duration flow; clear acts directly. Future ranges remain visible and editable; no-class dates stay separate.
- Home no-class content uses a quiet surface and smaller title; next-course preview and independent learning rules remain.
- Meal state emphasis centers on hours/status rather than simultaneous fill/border/badge. Selected full date stays in the pinned header during service auto-focus. Original origin-note strings render as supporting text.
- Exclusive settings share official stable1.4 segmented rows, switching to full-width radio rows when labels cannot fit.

Official-source review: [app bars](https://m3.material.io/components/app-bars/guidelines), [typography](https://m3.material.io/styles/typography/applying-type), [cards](https://m3.material.io/components/cards/guidelines), [switches](https://m3.material.io/components/switch/guidelines). The previous live rendered-site review confirmed small-bar hierarchy, meaningful card containment, avoiding mobile internal-card scrolling, and immediate switch behavior. New Expressive connected groups are unavailable as public APIs in the resolved1.4 library; stable segmented rows are a compatibility choice, not a claim of the latest Expressive anatomy.

Quality review: Impeccable distill retained useful notice grouping and removed repeated departure labels/large nested status boxes. Clarify kept source menu text verbatim and used explicit setup/change/clear and expand/collapse actions. Material rules remain authoritative for component shapes, tokens and typography. Verification results follow when completed; prior test counts do not certify this snapshot.

### Final verification — 2026-09-16, 12:05 KST

Observed automated results: JVM246/246, main UI plus native LMS/history97/97 in one clean run, dark150% visual routes3/3, and a strengthened large-font course-editor scroll/weekend/cancel check1/1. The final test-only addition did not change the app APK. Build/debug/test APK/lint passed; diff and tracking guard passed. Emulator font1.0 and light mode restored. Evidence: ignored artifacts/m3-followup-20260916/{build.log,ui-tests.log,large-dark.log,form-large.log,tracking-check.log}.

Observed phone update: selected Galaxy over existing Tailscale ADB, replacement install Success, launch Status ok, app process alive. Version1.5 updated at11:55:13; first-install date2026-09-03 retained. APK SHA256 16ED83B8F0E23A04FB1414E2C1D8377ED260AFF60A7333D71F767C88451A098C. Current phone screenshots show retained course schedule and learning counts; no personal course/pause/settings edits were saved during inspection.

Observed physical UI (11:56–12:05): quiet Home empty state and restored notices; seven equal-width weekdays/today dot; three complete compact campus routes in the initial viewport; expandable wrapped full departures including first/last; five full-width meal dates; subdued operating meal; dorm date remains pinned when lunch is focused and origin text is subordinate; pause settings above courses with four choices; all seven weekdays visible in course editor; aligned exclusive settings. Captured nine distinct verified views. An intermediate settings attempt captured the preceding timetable during dialog dismissal; fresh layout and phone-settings-final.png establish the final settings view. Editors/dialogs closed unsaved and the phone returned Home.

Material revalidation: native1.4 anatomy and selection behavior retained; TitleLarge emphasized weight only; semantic colors/shape tokens; no new motion scheme or artificial connected-control implementation; equal touch targets and measured text adaptation. Large-font forms use normal vertical scrolling with fixed dialog actions; the strengthened test selects Sunday after scrolling and cancels successfully. Unknown: actual TalkBack speech, every font/content combination and new One UI Now Bar acceptance. No new authentication/attendance action was performed.

Status: D-082 combined findings implemented and verified. Changes remain local and uncommitted; no publication.

## D-083 color restoration — 2026-09-16
Confirmed user request: restore previous shuttle and Home upper color emphasis while retaining the recent compact layout and interaction changes. Home briefing returns to unconditional primaryContainer/onPrimaryContainer. Campus departures restore first primary/onPrimary, second secondaryContainer/onSecondaryContainer and first-last error/onError or second-last errorContainer/onErrorContainer. Campus outer route surfaces were already unchanged. 4402 restores the nearest-stop primaryContainer and filled first/second departure colors. Keep one-line clock/countdown information and expandable full schedules.
Observed local verification: debug/test APK build passed, three focused existing UI checks passed, and Home/campus shuttle/4402 emulator captures reviewed. APK SHA256 DB8F36FA93C09F322A871D4BF76966B960BC3EB2015806F50FB9E1B7DE8081B6. Physical update evidence follows; initial streamed install failed, so transfer/hash verification/pm replacement install is in progress. Artifacts: ignored artifacts/color-restore-20260916/.
D-083 final verification: transfer SHA256 matched, pm install -r returned Success, launch Status ok, package update2026-09-16 12:16:39 and original install date retained. Temporary transferred APK removed. Physical screenshot was the locked/AOD screen, so this revision's app colors were visually verified on emulator only; do not claim new phone UI acceptance. Android CLI layout encountered transient EOF; screenshot capture succeeded. No functional flow or stored user data was edited.
