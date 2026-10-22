# Handoff — D-107 school features follow-up — 2026-10-23

Single-use: read this once at the start of the next session, act on it, then delete this file (ballast checkpoint convention).

## Where things are
- Work happens in the worktree `C:\project\dahak\DimaNow-ux` on branch `claude/ux-guide-pass` (head a8a9b6f). main (`C:\project\dahak\DimaNow`, 2baac30, unpushed) has D-102..D-105 only; D-106/D-107 live on the branch. Do not merge or push unless the user asks.
- Read first: `memory/00-INDEX.md`, `memory/DECISIONS.md` (D-106, D-107 and its 2026-10-23 amendment), `memory/OPEN-QUESTIONS.md` (Q-026, Q-027), `memory/CHECKPOINT.md`, `DESIGN.md` (School sites, Academic schedule).
- The phone runs a release-signed build of this branch (`C:\project\dahak\DimaNow\dist\d107\`, versionName 2.1 / versionCode 9, data kept).

## The user's request (verbatim, Korean), to carry out in the new session
> 시간표 불러오기와 예인관 상벌점 불러오기가 동작하지 않아. 또한 외출외박신청은 별로 브라우저로 열면 로그인 세션 이슈로 안되니까 웹뷰로 열도록 해줘. 장비 대여는 잘 작동하고 있어. 이부분에 UI UX 설계를 다시 해줘. 상단 설명도 상세히 보기처럼 아래 내용을 알아서 담아줘.
> https://www.dima.ac.kr/?p=111&searchText=%EA%B8%B0%EC%9E%90%EC%9E%AC+%EB%B0%8F+%EC%8B%A4%EC%8A%B5%EC%8B%A4&page=1&viewMode=view&reqIdx=2608190111427785
> (링크는 매 학기마다 올라와)
>
> 1. 대여사유 작성 요령
>   1) 소속(프로젝트)명, 대여사유를 상세히 기재 바랍니다.
>   2) 대여사유 예시 (아래 예시 외에도 신청가능)
>    ex) 000동아리 00스터디를 위해 대여합니다.
>    ex) 00계열/과 00수업 과제를 위해 대여합니다.
>    ex) 000공연/행사 중계를 위해 대여합니다.
>    ex) DSV-50 신청시 : EOS 200Dii 카메라 거치를 위해 신청합니다.
>   3) 대여 사유에 정확한 사용 목적 확인이 불가능할시 대여 신청이 반려될 수 있습니다. (ex. 프로젝트 명 미기재, 약어사용 등)
>
> 2. 유의사항
>  가) 장비 수령 직후 구성품 확인 및 장비 상태 확인 후 가져가야 합니다. (수령 직후 문제 발견 시 조교에게 말씀해주세요.)
>  나) 장비 수령 후부터 다음 대여자 반출 이전에 발견된 문제에 대해서는 대여자가 전적으로 배상하는 것을 원칙으로 합니다.
>  다) 수령 및 반납은 본인이 직접 하는 것이 원칙입니다.
>  라) 대여 당일에 장비를 수령 하지 않으면 대여가 취소됨을 알려드립니다.
>  마) 동일 장비를 2개 이상 신청하는 경우 승인이 나더라도 대여 시 중복이 확인될 경우 모두 취소됩니다.
>  바) 교내외 행사, 장비 파손으로 인해 기자재, 실습실 등의 대여가 제한, 변경 될 수 있습니다.

So the work items are:
1. **Fix intranet reads** (timetable import `INTRANET_TIMETABLE_URL`, 예인관 상벌점 `INTRANET_DORM_POINTS_URL`). DIMA C works, so the shared host, credential injection and portal sign-in are fine; the intranet route is the suspect. Code: `app/src/main/java/com/example/dimanow/school/SchoolService.kt` (`SchoolService.INTRANET.portalEntry = https://portal.dima.ac.kr/?r=https://intranet.dima.ac.kr/default.aspx`), `SchoolServiceHost.kt` (stage machine: target → portal sign-in → back to target; failure messages name `host/path`), `IntranetTimetable.kt`, `DormPoints.kt` (page scripts). Ask the user for the exact on-screen error message first. Observed earlier in the user's signed-in browsers: opening `https://intranet.dima.ac.kr/default.aspx` directly gave `/errors/WrongAccess.aspx?c=2` (session-error page), while `/su/timeTable_Stnt.aspx` and `/su/academicCalendar.aspx` opened directly; the intranet header links to `lms.dima.ac.kr/sso/index.jsp` and `udimac.dima.ac.kr/sso/index.jsp`, and DIMA C's top bar labels `https://portal.dima.ac.kr` as 인트라넷 — the portal login may land on the intranet only without `?r=`, or the intranet may need its own SSO entry. The WebView blocks any host outside portal/intranet/udimac and `http://sso.dima.ac.kr:8080/sso/*` (`SchoolUrlPolicy`); a blocked hop also ends the read with a message. Never enter the user's credentials yourself; the user tests on the phone.
2. **외출·외박 신청 in an in-app WebView** signed in with the same school session (today `DormLifeCard` opens `INTRANET_DORM_OUTING_URL` in the external browser, which has no session). This is a page the student operates; D-107 says the app itself only reads — record a short amendment that the outing page is shown for the student to use, not submitted by the app.
3. **Redesign the 장비 대여 UI/UX** (`app/src/main/java/com/example/dimanow/ui/EquipmentScreen.kt`): a top section with a short summary and an expandable "자세히 보기" carrying the notice above (대여사유 작성 요령 with examples, 유의사항 가~바), plus a link to that semester's notice. The notice URL changes every semester: link to the board search (`https://www.dima.ac.kr/?p=111&searchText=기자재 및 실습실`) rather than a fixed `reqIdx`, or keep the given link as the current one with the search as fallback. Keep the M3 Expressive look of the app (D-104/D-105: elevated cards, 20dp corners, single pinned app bar, the D-106 fonts).

## Working rules that bind the next session
- Reply to the user in Korean, including short progress notes (saved Claude memory `reply-in-korean`).
- Ballast: standing decisions are followed; changes get an append-only superseding note in `memory/DECISIONS.md`; record facts in `memory/` the same session; no unrequested releases, branches or worktrees; the main version number is the user's call.
- Never print device serials, IPs, endpoints, secrets, tokens or passwords; secrets stay in gitignored `.local/`; never commit APKs; personal values (P-01, intranet data) never go into Git, logs or fixtures.
- Device rules: emulators stay off; test on the USB Galaxy Tab S9 (model SM-X710) with screen brightness at manual minimum during the work and restored to automatic after (`settings put system screen_brightness_mode 1`, brightness 96). If the tablet shows its lock screen, ask the user to unlock it — never enter a PIN. Phone (SM-S918N, wireless adb) installs need the user's go-ahead in that session.
- Commit only your own files (another graphify session may touch main's checkout).

## How to build, test and install
- Build from PowerShell with absolute paths (Git Bash `cmd //c gradlew-local.bat` silently fails to find the script): `& C:\project\dahak\DimaNow-ux\gradlew-local.bat -p C:\project\dahak\DimaNow-ux :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --console=plain`. Lint (`:app:lintDebug`) can leave the Gradle daemon holding `classes.jar`; if a later build fails with "다른 프로세스가 파일을 사용 중", stop only the daemon whose log under `~/.gradle/daemon/9.3.1/` mentions `DimaNow-ux`.
- Release for the phone: `:app:assembleOptimized`, then `powershell -NoProfile -ExecutionPolicy Bypass -File C:\project\dahak\DimaNow\tools\Sign-Release.ps1 -RepositoryRoot C:\project\dahak\DimaNow-ux -SigningDirectory C:\project\dahak\DimaNow\.local\release-signing -OutputDirectory C:\project\dahak\DimaNow\dist\<name>` (signer a2c49114…), then `adb install -r`.
- Device helper scripts (serials are resolved by model and never printed) are in `C:\Users\chc01\AppData\Local\Temp\claude\C--project-dahak-DimaNow\24aa4fbe-69c7-4c25-b169-4dac5fa89628\scratchpad\`: `tablet.sh` (sets `$ADB`/`$TAB`), `run3.sh <classes…>` (runs instrumentation classes one by one), `ui-classes.txt` (the 32 UI/LMS/widget classes), `tap.sh <resource-id|text>` (prints tap coordinates from a uiautomator dump), `phone-install.sh <apk>`. Copy them into the new session's scratchpad if that folder is gone. In Git Bash, export `MSYS_NO_PATHCONV=1` for adb device paths and give adb Windows-style host paths (`C:/…`).
- Baseline: 309 JVM tests and 83 pipeline tests pass; tablet 31/32 UI classes pass (CampusSummaryWidgetLayoutTest's two failures are pre-existing, Q-022). Screens behind the sign-in form are FLAG_SECURE, so screenshots are black there; use `uiautomator dump` to read them.
