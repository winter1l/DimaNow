# UX pass from Apple HIG, Material 3 and the Toss consumer UX guide — 2026-10-22 (D-102)

## Request (confirmed)
"이 3개의 디자인, 인터페이스 가이드를 모두 확인하고 앱의 UI UX를 고쳐줘. UI 디자인은 M3Expressive로 유지하는데, UX들은 다른곳에서도 참고해서 더 사용자 친화적으로 마음대로 바꿔봐. 기존에 영향이 가지 않도록 새 워크트리에서 작업해."
Work happened only in the worktree `C:\project\dahak\DimaNow-ux`, branch `claude/ux-guide-pass` from f31e1aa. Nothing was pushed, merged or released.

## Sources read (observed 2026-10-22, by three read-only research agents)
- Toss: https://developers-apps-in-toss.toss.im/design/consumer-ux-guide (one page: 브랜딩, 다크패턴 방지, UX 라이팅, 그래픽, 해상도) plus the non-game launch checklist, the navigation-bar doc, TDS component pages and the linked accessibility guide. The Notion icon guide did not load.
- Apple HIG (Korean edition read via its data feed; key rules checked against English): onboarding, launching, privacy, feedback, loading, alerts, modality/sheets, writing, accessibility, notifications, Live Activities, widgets, settings, accounts, data entry, text fields, lists, buttons, toggles, segmented controls, tab bars, layout, typography, color, undo, help. Noted: the Korean writing page reverses the "Send" vs "Let's do it!" example; the English page is authoritative.
- Material 3 (m3.material.io rendered in a browser): Expressive blog, motion, shape, type, accessibility, layout, writing, navigation bar, top app bar, cards, lists, dialogs (incl. full-screen), bottom sheets, snackbar, chips, buttons/FAB, button groups, toolbars, progress/loading, badges, pickers, text fields, search. Library status (developer.android.com): stable 1.4.0 has no Expressive APIs; 1.5.0-beta02 shipped 2026-10-21. `OutlinedSecureTextField` and `TimePickerDialog` with `TimePickerDialogDefaults.DisplayModeToggle` exist in 1.4.0 (confirmed with javap on the resolved AAR).

## Changes made (each with the guide rule it follows)
1. First run: welcome shows what the app does (4 rows) before the unofficial/accuracy notice; the permission step names only notifications and location with one sentence each and a single `계속` that lets Android ask for both; `나중에 할게요` finishes without asking; the settings-page items (background location, exact alarms, live notifications) appear afterwards as optional rows (HIG onboarding/privacy priming screen; Toss "always a way out"; Android ask-in-context).
2. Permission rows name their action: `허용하기` (Android prompt) or `설정 열기` (system page), with a leading icon and a check mark when on (Toss CTA rule, TDS ListRow, HIG settings).
3. Home: title is today's date; the current location moved into the shuttle card header as `○○ 출발`; empty briefing offers `수업 추가하기` (opens the add form directly) or `새 학기 설정하기`; without location permission the shuttle card is a container with `위치 허용하기` (Android prompt in place, system settings after a lasting refusal) and `전체 시간표`, never a tappable card with a nested button (M3 cards; HIG ask at first use).
4. Launch-time update dialog replaced by a Home card (offer → download progress → install continue); failed background checks stay in Settings (HIG "no alert at launch", Toss "no sheet on entry").
5. Timetable: one empty state with `수업 추가` and `시간표 파일 불러오기` (shared import + confirm), no FAB while empty, pause mode hidden until there are courses, `오늘` badge on today's weekday, confirmation snackbar after adding (HIG empty states; M3 FAB "hide rather than disable").
6. Course form: M3 full-screen dialog (close X, `저장`, edge-to-edge), fields ordered name → days → time → room → building → professor, several weekdays at once when adding (one course per day), moving the start keeps the class length, inline required-name error, discard confirmation, IME next/done; time picker is the M3 `TimePickerDialog` with dial/typed-entry toggle following the phone's 12/24-hour setting.
7. Shuttle: the 교내/4402 switch and weekday selector are pinned in the header like the meal screen (consistency, D-057 pattern).
8. Navigation: re-selecting the current tab scrolls it to the top; unselected tabs use outlined icons, the selected one filled (M3 navigation bar).
9. Settings: grouped sections (알림과 위치 / 계정과 데이터 / 앱 정보) with heading semantics; `자동 안내 권한` summary ("모두 켜져 있어요" or "N개가 꺼져 있어요") opened automatically when something is off; 이 앱에 대해 links as list rows with external/internal trailing marks (M3 lists, HIG settings).
10. LMS login: one-line value statement, D-099 disclosure kept in full but as four bullets (decorative bullets hidden from screen readers), labelled show/hide password, autofill content types, keyboard next/done → login (M3 text fields, HIG accounts).
11. Countdown capsules and the Home class badge use tabular figures (M3 typography, fixed-width digits).
12. Copy: `첨부파일이 바뀌었어요`, `다른 곳에서 LMS에 로그인한 상태예요`, `오늘 운행이 끝났어요` on Home (Toss active voice, no 되어요, no 종료 for recurring endings).
13. Defect found during the pass: a course without a classroom showed `이름 ()` on Home; fixed there. The same blank-room separators in notifications/widgets were flagged as a separate task.

## Kept on purpose (standing decisions or platform conventions)
- Tab change motion stays the directional slide + fade (D-026/D-042) although M3 recommends fade-through for navigation-bar destinations → OPEN-QUESTIONS.
- Refresh stays pull-to-refresh plus retry buttons in error/empty states and the accessibility action (D-058/D-099) although M3 asks for a single-tap alternative → OPEN-QUESTIONS.
- Segmented buttons stay (Material3 1.4.0, D-094); M3 now prefers the connected button group, available in 1.5 beta → OPEN-QUESTIONS.
- Dialog dismiss buttons keep `취소` (HIG and Android convention) instead of Toss's `닫기`.
- Widgets keep their compact `운행 종료` wording.

## Tried and reverted
- Korean word-unit wrapping (`LineBreak.WordBreak.Phrase` with a `ko-KR` locale on every type role): the APK contained the change, but the API 36 emulator still split words such as `드/려요`, so it was removed instead of shipping an unverified global text change. Compose does pass the word style to `LineBreakConfig` (javap of ui-text 1.10.6).

## Verification
- JVM unit tests: 294 passed, including new `CourseEditorTimeTest` and `GuidanceSetupStateTest`.
- Emulator (API 36, en-US locale) instrumentation: 32 UI/LMS/widget classes, 130 tests passed, including the new `OnboardingFlowTest` (4), `CourseEditorDialogTest` (4) and `HomeUpdateNoticeTest` (2). One emulator system_server crash interrupted the run; the cut-off classes passed after a clean emulator reboot, and the Home/shell classes were rerun on the final build.
- Lint: 0 errors; compared with main only one new UseKtx warning (kept to match the file's `Uri.parse` idiom).
- Emulator walk-through with screenshots (session scratchpad, not committed): new welcome, home direction, permission priming → Android location and notification prompts → check marks + optional rows + `완료`; Home date title, `수업 추가하기` opening the full-screen form; two-day add with snackbar and `오늘` badge; pinned shuttle header and re-tap scroll to top; Settings sections and summary; Home `위치 허용하기` → Android prompt → card switches to the campus message without reload. The LMS login screen uses FLAG_SECURE, so it was checked through the accessibility tree only.
