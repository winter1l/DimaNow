# Launch readiness review — 2026-10-17

Request (confirmed): keep the release key (D-098) and check everything needed to actually launch the app to other students, including missing features. Read-only review: two code audits (app features and personal data; build, manifest, shipped APK and backend), primary-source research, and direct checks by the main session. No code changed. Labels: confirmed (C, code or command), observed (O), assumed (A), unknown (U).

## Decisions needed from the user
1. Distribution channel: GitHub direct APK (current) or Google Play, or both. D-001 still excludes multi-user distribution and store release; README lists both as out of scope. A launch decision supersedes those clauses.
2. applicationId `com.example.dimanow` (C, app/build.gradle.kts:12). Play rejects the `com.example` prefix (community reports; no primary source). The updater rejects a package mismatch (C, update/AppUpdateModels.kt:96), so a later change cannot update in place. v1.6 has 0 downloads (O), so now is the cheapest time.
3. A report/take-down path for auto-published dorm menus (D-097 chose none; Play UGC policy requires reporting).
4. D-073 HTTP SSO exception at multi-user scale (every user's login ticket crosses campus Wi-Fi in cleartext).
5. Whether to ask the school before launch (school name and colours, LMS automation).

## Blockers for both channels
- B1 Personal seed (C): the owner's six courses and term ship to every install (domain/DefaultSchedule.kt:51-62). `ensureSeeded()` re-inserts them whenever the course count is 0, on every process start (data/RoomCampusDataRepository.kt:46-57, DimaNowApplication.kt:175). Class notifications default to LIVE_UPDATE, so new users get the owner's class alerts. Values are not repeated here.
- B2 Term end (C): no prompt; after 2026-12-18 all class guidance stops and Home only says there are no classes.
- B3 Calendars (C/O): the campus shuttle has no public-holiday handling, while the official page says A/B run 평일(공휴일 제외) (O 2026-10-17). No vacation or exam calendar. 4402 holidays cover 2026 only (transit/Bus4402Schedule.kt:98-119). Field overrides are compiled into the APK.
- B4 Dorm photo metadata (C): JPEG/PNG/WebP are uploaded byte-for-byte (meal/DormitoryMealImageReader.kt:26-30); only HEIC is re-encoded. Raw files land on the public `dorm-submissions` branch before validation and are never deleted. Camera EXIF may include GPS (A). The consent text says the photo is shared after checking, not that it is public immediately and permanently, nor that Gemini processes it.
- B5 First-public-build forward compatibility (C): shuttle/meal/notice clients parse with `ignoreUnknownKeys = false`, so a new field in an existing manifest entry or payload breaks every shipped install; new dataset keys are safe because `datasets` is a map. The update check calls unauthenticated api.github.com and records the check time only on success (update/AppUpdateCoordinator.kt:89-116), so failures retry on every cold start; 60 requests/hour per shared campus IP (A).
- B6 Release key backup (U): `.local/release-signing/` has no verified offline backup; losing it ends in-place updates for v1.6+.
- B7 In-app disclosures (C): no unofficial-app notice, privacy policy link, open-source licence screen (jsoup MIT and others bundled), contact or bug-report channel, or warning that AI-read and crowd-sourced menus may be wrong. OSM attribution exists under 고급 및 진단.

## High
- H1 LMS consent (C): save toggle on by default with one line of text; no mention of the HTTP SSO step or that the silent login takes over a duplicate session (D-052 3045), which can log a student out elsewhere, e.g. during a quiz (A).
- H2 Repository write paths reachable by the Worker's `contents: write` token: details in private memory P-06.
- H3 Dorm UGC (C): no report or take-down; the global budget (20/day, 40/week) can be exhausted by about 20 addresses; one well-formed fake menu holds the week.
- H4 Backup (C): `allowBackup=false`; backup and data-extraction rule files are unused templates; the timetable is lost on a phone change; device-to-device transfer may still copy the LMS cache and cookies (A).
- H5 Fit for other students (C): courses are always saved with zone MAIN (ui/TimetableDialogs.kt:184); home base offers only 엔터관/원룸촌 (no commuter option).
- H6 Operations (C/A): no alert on publish failures or NEEDS_REVIEW; scheduled workflows in public repos stop after 60 days without activity (A, verify); Workers Logs observability is on and its retention is not in PRIVACY.md.
- H7 Docs (C): README (out-of-scope list, debug-signing install text, ADB-only install), PRIVACY.md (personal-use wording; lacks formal 처리방침 items) and SECURITY.md (same-certificate wording) are out of date; the Pages landing page is data-only.

## Medium and low (C unless marked)
Location test mode reachable in release; Now Bar help asks users to enable Developer Options; LMS logout only when credentials were saved; no delete-all; refresh is pull-only; first offline run leaves the notice card loading and the shuttle widget saying no service; Home shows 캠퍼스 외부 without a permission hint; `openUrl` lacks an ActivityNotFound catch; User-Agent says 1.4; merged FOREGROUND_SERVICE is unused; the portrait opt-out ends at targetSdk 37; only API 36 is tested and the R8 build never runs under test; manual one-PC release process; 10 stale Dependabot PRs; the Keystore key stays after LMS deletion; English "DIMA Portal" label.

## Google Play only
Package name; AAB with Play App Signing (both Gradle release types sign with debug); remove the self-updater and REQUEST_INSTALL_PACKAGES in a Play build; background-location declaration with video (or foreground-only location); hosted privacy policy plus Data safety (LMS login likely declared as user IDs, photos as collected); impersonation risk from school name plus credential login; UGC terms, reporting and moderation; closed test with at least 12 testers for 14 consecutive days for personal accounts created after 2023-11-13.

## Confirmed strengths
LMS credentials (Keystore AES-GCM, noBackup, FLAG_SECURE) and no app logging; updater verification chain (hosts, size, SHA-256, package, versionCode, signer lineage, user-confirmed installer); static-data integrity with last-good cache; only MainActivity exported; native libraries 16 KB aligned; monochrome launcher layer; widget preview layouts; targetSdk 36 meets Play's current requirement; Worker admission order and budgets.

## Observed service state (2026-10-17)
Manifest READY for all datasets; meal revision 12 (2026-10-12), notice 34, dorm 6 (2026-09-29; no uploads since); the latest 12 scheduled runs succeeded; Anseong's 4402 page still lists 기준일자 2025.04.28; the v1.6 asset is byte-identical to `dist/DIMA-Now-v1.6.apk`, with v2 signed by debug and v3 by the release key.

## External requirements (primary sources, checked 2026-10-17)
- Play target API: 36 required for new apps and updates since 2026-08-31; API 37 expected August 2027 (developer.android.com/google/play/requirements/target-sdk).
- Android developer verification (developer.android.com/developer-verification/guides, updated 2026-08-18): enforcement began 2026-09-30 in Brazil, Indonesia, Singapore and Thailand for installs from participating stores; global expansion to all apps on certified devices in 2027; Korea has no date (U). Limited-distribution accounts (no ID) reach at most 20 devices; wider sideload distribution needs a verified account and registered package names and signing-key fingerprints. Unregistered apps need the user's advanced flow.
- Samsung Auto Blocker: on by default for devices launched with One UI 6.1.1+ (sources differ, medium); it blocks installs and updates from outside Galaxy Store/Play, so direct APK users must turn it off for each install or update.
- 위치정보법: on-device-only location does not require 위치기반서비스사업 신고 (lbsc.kr; 방통위 해설서). Sending location off the device would.
- 개인정보 처리방침: legally required for a 개인정보처리자; whether uploaded photos and IP hashes make the developer one is unconfirmed (medium). Play requires a policy regardless.
- Now Bar: ordinary third-party apps do not appear without the developer option on stock Galaxy (O, Q-010); Samsung's admission mechanism is unknown.

## Recommended order (proposal, not decided)
0. Owner: offline key backup with a restore and dry-run signing; decide channel and package name.
1. B1/B2 empty first run with term and course setup, term-ended prompt.
2. B4 strip metadata from every upload and fix the consent text.
3. B7 About screen and disclosures; H1 LMS consent and takeover confirmation.
4. B3 service-calendar dataset (multi-year holidays, shuttle holiday exclusion, vacation periods) as a new manifest dataset.
5. B5 Pages-hosted update metadata with failure backoff; forward-compatible parsing.
6. H4 backup/export and delete-all; H5 course campus and commuter option.
7. H2/H6 repository rulesets, immutable releases, Dependabot triage, failure alerts; H3 report path (needs a decision superseding D-097).
8. H7 docs, privacy policy page and download landing page with install guidance.
9. Closed beta with real students (Q-001..Q-005 evidence, a non-Samsung device, battery).
10. Channel-specific packaging (Play flavor and Console setup, or public GitHub release).

## Implementation status — 2026-10-17 (D-099, GitHub-only)
Confirmed by code and local tests (not yet committed, pushed, deployed or released):
- B1/B2 done: no seed; `ensureSeeded` only inserts a suggested term (`calendar/AcademicTerms`); empty-timetable and term-ended guidance on Home/Timetable. Emulator (fresh install of `io.github.winter1l.dimanow`): onboarding notices, empty timetable, suggested 2026-08-24..12-18 term observed.
- B3 done: `data-source/calendar.json` (terms 2026-1/2026-2, KASI 2026–2027 holidays, empty suspension list) published as dataset `calendar`, bundled copy guarded by a unit test; campus shuttle suspended on weekday holidays (Sunday line unaffected); 4402 holiday table reads the same calendar; shuttle screen/home/widget name the holiday; outside-term note on the shuttle screen. Vacation suspension dates are unknown and left empty for the owner to fill.
- B4 done: every upload re-encoded to JPEG without metadata; consent text updated; gateway reasons mapped to plain messages.
- B5 done: shuttle/meal/notice parsing tolerates unknown fields; update metadata from Pages dataset `app_release` with GitHub API fallback; failed automatic checks back off 6 h.
- B7 done: 이 앱에 대해 card (unofficial notice, accuracy note, privacy policy, issue templates, licences screen with bundled Apache-2.0/MIT texts, source link); onboarding welcome notice.
- H1 done: LMS saving opt-in with consent text naming the HTTP SSO step; 3045 takeover only after the user approves in a dialog (one-shot approval in `LmsLoginBridge`).
- H2 partly done: rulesets, secret scanning, immutable releases, Dependabot clean-up (details P-06); residual: pushes to `data` remain possible for a contents:write token.
- H3 done in code: anonymous report endpoint `/v1/dormitory-meal-reports` (lease per address per week, daily/weekly budget, payload-free `dorm-report-*` dispatch), owner issue, `dorm-revoke` workflow, app drops REVOKED weeks. Needs Worker deploy and push.
- H4 done: Auto Backup / device transfer limited to `dima-now.db` and the settings DataStore; timetable export/import; delete-all via `clearApplicationUserData`; LMS logout without saved credentials; Keystore key deleted with the account.
- H5 done: per-course building (본관/엔터관) and a commuter home base (`HomeBase.COMMUTE`, no return-shuttle guidance).
- H6 done in workflow: one issue opened/updated on failure or NEEDS_REVIEW/ERROR and closed on recovery; privacy policy documents Workers Logs (3/7-day retention per Cloudflare docs).
- H7 done: README/SECURITY rewritten for GitHub launch; PRIVACY.md is a full 처리방침 rendered to `privacy.html` by `tools/render_privacy_page.py`; `pages/index.html` download page with Auto Blocker steps (checked in a local browser).
- Medium items done: test mode already under 고급 및 진단; Now Bar developer option marked optional; openUrl guard; User-Agent from BuildConfig; FOREGROUND_SERVICE removed from the merged manifest; accessibility refresh action; notice/widget/location empty texts; "DIMA 포털".
- Release: applicationId `io.github.winter1l.dimanow`, versionCode 8 / 2.0; `tools/Sign-Release.ps1` signs v2+v3 with the release key only and writes `dist/DIMA-Now-v<ver>-optimized.apk`.
Owner timetable import file for the phone migration: `.local/migration/my-timetable-2026-2.json` (ignored).
