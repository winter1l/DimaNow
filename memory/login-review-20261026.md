# Sign-in review — 2026-10-26 (read-only, user-requested)

The student asked, after the 3045 test (PRODUCT-TRUTH 2026-10-26), "로그인 로직을 더 자세히 확인해봐. 다른 곳이라던지 전체적으로. 그 후 계획을 세워줘". Code read on `claude/ux-guide-pass` at e9b0e90; nothing changed.

## Where the app signs in (confirmed, code)
- One portal account (`LmsCredentialStore`, opt-in saving) serves LMS and the school sites. All WebViews and the LMS HTTP transport share the app's cookie jar (`CookieManager`), so a live portal session is reused without sending the account.
- LMS (`LmsRoute`, `LmsAutoLoginCoordinator.ensureActive`, hidden `LmsAuthenticationWebView` on `portal.dima.ac.kr/?r=https://lms.dima.ac.kr/sso/index.jsp`): opening 수업 once the 5-minute cache has lapsed and the LMS answers signed out; pull-to-refresh and the refresh button; opening an item (`loadDetail` → SessionExpired → `ensureActive(force = true)`); downloading an attachment (`LmsDetailScreen`); 다시 시도. A not-saved manual sign-in goes through `manualLogin`.
- School sites (`SchoolServiceHost`, D-107/D-131): any read or opened page whose site answers signed out goes through `SchoolService.portalEntry` and submits the saved account (or asks on the sign-in form). Automatic on opening a screen: 도서관 대출 현황 (`libraryAutoReadDue`), 장비 대여 catalog (no catalog and no recent failure). On a button: 상벌점, 시간표 가져오기, 외출·외박 신청, 성적 보기, 장비 대여 신청, library pages.
- No background work signs in (workers read caches only).

## Findings
1. Confirmed (test): the 3045 attempt itself ends the other session, so the D-099 pop-up (`LmsScreen`, 「다른 기기에서 LMS를 쓰고 있어요」) asks after the fact; 나중에 protects nothing.
2. Code: `SessionConflict` sets no retry pause (`retryAfter` is only for network errors), so after 나중에 the next automatic sign-in (opening 수업 after the cache, any refresh) signs in again, and ends the other device again if it signed back in meanwhile.
3. Code + observed in Chrome: on 3045 the school host reveals the official page (`reveal()`, 「학교 포털이 확인을 요청했어요」), which renders blank (broken `top.location.href='…';}` script). Not observed in the app yet.
4. Assumed: a school-site sign-in through the portal ends another device's LMS session the same way (3045 is per portal account; the PC's LMS session ended after the tablet's portal attempt). So opening 도서관 or 장비 대여 can sign the PC out.
5. Code: an approved takeover follows the official page's redirect to the bare portal root (no `r=`) and submits again; where that lands was observed working on 2026-09-02 but not since.
6. Wording now untrue: the sign-in notice line 「다른 기기에서 로그인 중이면 그 로그인을 끊기 전에 먼저 물어봐요」 (`LmsLoginScreen`) and SECURITY.md 「승인 없이 다른 기기의 로그인을 끊지 않습니다」. PRIVACY.md names only the school sites as able to sign the PC out.
7. Unknown: whether 3045 also answers when the only earlier session is this device's own, ended by idle timeout or lost cookies, with no other device (the student's "원래 가끔씩 에러나" in Chrome); and the LMS/portal idle timeout.

## Plan proposed (awaiting the student)
- Step 1 (tablet + PC, before code): (a) does 3045 come from the device's own stale session (sign the tablet in, clear only the app's cookies, refresh) and how long an idle session lasts; (b) where an approved takeover lands (PC signed in, tablet 이 기기로 로그인, page list watched).
- Step 2 (code): A. LMS 3045 → no question; sign in once more from the `?r=` login URL (not the official redirect), then say the other place's LMS sign-in ended; a second 3045 fails with a message. B. Ask before taking the session back: when the app's session ended earlier than its idle timeout, or soon after a 3045, do not sign in automatically; keep the cached list with a 「이 기기로 로그인」 card (exact rule from step 1). C. School sites: same handling instead of the blank page; screen-open automatic reads do not sign in under B's condition. D. Wording (sign-in notice, SECURITY.md, PRIVACY.md) and a decision superseding D-099(6). E. Tests (reducer, coordinator, school host) and the PC + tablet re-run.

## Step 1 results — 2026-10-26 (PRODUCT-TRUTH "3045 follow-up")
- (b) An approved takeover fails at the app's own check of the 3045 page (re-encoded form query), which is the red 「넘겨받지 못했어요」 banner; it only ever "works" when the first attempt already signed the other device out and the retry meets no conflict.
- (a) No 3045 for the same device after losing its cookies; the portal distinguishes clients (address and/or browser). A network change on one device is untested.
- The LMS notice gives one hour as the session limit; a session found ended well within an hour of the app's last LMS use was most likely taken by another sign-in.
- Plan update: A retries from the `?r=` login URL instead of the official page's action; B's "ended early" rule uses the one-hour limit from the app's last successful LMS read; an optional check (c) is a network change on one client (the student toggling WARP on the PC, or the phone's Wi-Fi/LTE).

## Step 2 — implemented as D-135 (2026-10-26)
`LmsSessionController` (reducer `CONFLICT_RETRY`/`RetryCredentials`, `SuccessEndingElsewhere`, `PortalSignInGate`, `LmsSignInRecord`, `LMS_SESSION_LIMIT`, coordinator `signedInElsewhere`/`notices`/`markExpired`), `AndroidLmsSignInRecord`, `LmsLoginScreen` (retry from the login URL, takeover scripts removed, submission numbers for the 10 s check, notice line), `LmsScreen` (dialog removed, card, hints, cached-path fix), `LmsItemsScreen` (`LmsSignedInElsewhereCard`), `LmsDetailScreen`, `SchoolService` (`automatic`, `schoolSignInStep`), `SchoolServiceHost` (3045 retry with a toast, confirm dialog), `LibraryScreen`/`EquipmentScreen` (automatic reads), SECURITY/PRIVACY/README. Tablet check in PRODUCT-TRUTH.
