package com.example.dimanow.school

import com.example.dimanow.lms.PortalSignInGate
import java.net.URI
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * School sites the app reads with the student's own sign-in (D-107): the intranet (timetable, dorm points),
 * DIMA C (equipment) and the library (loans, D-131). All are reached through the school portal, like LMS: the
 * portal sends the student on to the site's own SSO entry, which takes the portal's ticket through
 * `sso.dima.ac.kr:8080`.
 */
enum class SchoolService(
    val host: String,
    val label: String,
    val portalEntry: String,
    /** An SSO entry page that moves itself on by script once signed in; the host waits for where it goes. */
    val selfForwardingSsoPath: String? = null,
    /** Run on each shown page: presentation only, it never fills in or submits anything. */
    val pageFocusScript: String? = null,
    /**
     * A hidden read on this site needs none of the target page's own scripts: its read scripts only `fetch` the
     * site's JSON with the page's cookies. Skipping them keeps the page from loading its libraries and making
     * its own requests on every read (Q-031). Pages that may need their scripts (sign-in hops) still get them.
     */
    val hiddenReadSkipsPageScripts: Boolean = false,
) {
    INTRANET(
        host = "intranet.dima.ac.kr",
        label = "인트라넷",
        // Observed 2026-10-23: `default.aspx` reached straight from the portal has no intranet session and
        // answers with `/errors/WrongAccess.aspx`; `/sso/main.aspx` signs in and then goes to `default.aspx`.
        portalEntry = "https://portal.dima.ac.kr/?r=https://intranet.dima.ac.kr/sso/main.aspx",
        selfForwardingSsoPath = "/sso/main.aspx",
        pageFocusScript = INTRANET_PAGE_FOCUS_SCRIPT,
    ),
    DIMA_C(
        host = "udimac.dima.ac.kr",
        label = "DIMA C",
        portalEntry = "https://portal.dima.ac.kr/?r=https://udimac.dima.ac.kr/sso/index.jsp",
        hiddenReadSkipsPageScripts = true,
    ),
    /**
     * The library (D-131): Livetek "Sponge", server-rendered ASP.NET with a session cookie. Observed 2026-10-25: its
     * 로그인 link is the portal with `r=` the library's own `/sso/main.aspx`, the same pattern as the intranet, so
     * the host waits on that page while it moves on. A library page that answers signed out (its login page or a
     * page without the `대출중 N건` header) counts as needing the sign-in; `/Account/LogonDacl` is for external
     * users and is never used.
     */
    LIBRARY(
        host = "lib.dima.ac.kr",
        label = "도서관",
        portalEntry = "https://portal.dima.ac.kr/?r=https://lib.dima.ac.kr/sso/main.aspx",
        selfForwardingSsoPath = "/sso/main.aspx",
    ),
}

/**
 * Fits an intranet page to a phone: the desktop layout puts a side menu beside the page's own content
 * (`ctl00_cphContainer_*`) under a header (`ctl00_header_*`), which leaves the form half the width. This hides
 * the menu and the header and lets the content take the width. Tables keep the school's layout: the outing
 * form is about twice a phone's width and scrolls sideways (the user chose the table over a card layout,
 * 2026-10-23). Nothing on the page is filled in or sent.
 */
private const val INTRANET_PAGE_FOCUS_SCRIPT = """
    (function(){
      var content=document.querySelector('[id^="ctl00_cphContainer_"]');
      if(!content)return 'no content';
      function outermost(node){
        while(node&&node.parentElement&&!node.parentElement.contains(content))node=node.parentElement;
        return node;
      }
      ['[id^="ctl00_sideMenu_"]','[id^="ctl00_header_"]'].forEach(function(selector){
        var part=outermost(document.querySelector(selector));
        if(part&&!part.contains(content))part.style.display='none';
      });
      for(var node=content.parentElement;node&&node!==document.body;node=node.parentElement){
        node.style.marginLeft='0';
        node.style.width='auto';
        node.removeAttribute('width');
      }
      return 'ok';
    })()
"""

/**
 * Where the school-service WebView may go (D-107): HTTPS on the portal and every [SchoolService] host (the
 * intranet, DIMA C and, since D-131, the library `lib.dima.ac.kr`), plus the portal's plain-HTTP ticket hop on
 * `sso.dima.ac.kr:8080` under `/sso/` (as D-073 allows for LMS sign-in).
 */
object SchoolUrlPolicy {
    const val PORTAL_HOST = "portal.dima.ac.kr"
    private val httpsHosts = setOf(PORTAL_HOST) + SchoolService.entries.map { it.host }

    fun isAllowedHttps(value: String): Boolean = runCatching {
        val uri = URI.create(value)
        uri.scheme == "https" && uri.host in httpsHosts && uri.userInfo == null && uri.port in setOf(-1, 443)
    }.getOrDefault(false)

    fun isAllowedSsoHop(value: String): Boolean = runCatching {
        val uri = URI.create(value)
        uri.scheme == "http" && uri.host == "sso.dima.ac.kr" && uri.port == 8080 &&
            uri.rawPath.orEmpty().startsWith("/sso/") && uri.userInfo == null && uri.rawFragment == null
    }.getOrDefault(false)

    fun isAllowedNavigation(value: String): Boolean = isAllowedHttps(value) || isAllowedSsoHop(value)

    /** `host/path` with no query, so a failure can say where the site went without leaking tickets. */
    fun describe(value: String): String = runCatching {
        val uri = URI.create(value)
        "${uri.host}${uri.rawPath.orEmpty()}"
    }.getOrDefault("알 수 없는 주소")

    fun isPortalCredentialPage(value: String): Boolean = runCatching {
        val uri = URI.create(value)
        uri.scheme == "https" && uri.host == PORTAL_HOST && uri.rawPath.orEmpty() in setOf("", "/", "/default.aspx")
    }.getOrDefault(false)

    /** The portal's duplicate-login notice (the school keeps one session per account). */
    fun isSessionConflict(value: String): Boolean = runCatching {
        val uri = URI.create(value)
        uri.host == PORTAL_HOST && uri.rawPath == "/sso/error.aspx" && uri.rawQuery.orEmpty().contains("errorCode=3045")
    }.getOrDefault(false)
}

sealed interface SchoolServiceResult {
    /** [json] is what the page script returned under `data`. */
    data class Success(val json: String) : SchoolServiceResult
    data class Failure(val message: String) : SchoolServiceResult
    data object Cancelled : SchoolServiceResult
}

/**
 * One visit to a school site: open [targetUrl] (signing in through the portal first when needed) and run
 * [script] there. The script must be an async function body that returns an object `{state, data, message}`:
 * `state` is `ok`, `signin` (the page is not signed in) or `error`. Without a script the signed-in page is
 * shown for the student to use themselves (the outing application, D-107 amendment of 2026-10-23).
 */
class SchoolServiceRequest internal constructor(
    val service: SchoolService,
    val targetUrl: String,
    val script: String?,
    /** What is read or opened, for the sign-in screen: `시간표`, `상벌점`, `장비 대여 현황`, `외출·외박 신청`. */
    val purpose: String,
    internal val result: CompletableDeferred<SchoolServiceResult>,
    /** False keeps the site's own menu and header (D-131(3): finding the grade page needs the intranet menu). */
    val fitsPage: Boolean = true,
    /**
     * D-135: a read the app starts by itself (on opening a screen). It never signs in while the portal sign-in is most
     * likely held elsewhere; a read the student starts asks first instead.
     */
    val automatic: Boolean = false,
) {
    /** The page itself is shown instead of read. */
    val opensPage: Boolean get() = script == null

    /** A per-request global the page cannot guess, where the async script leaves its answer. */
    internal val resultSlot: String = "__dimaNow_" + UUID.randomUUID().toString().replace("-", "")
}

/** [word] followed by 을 or 를, chosen by whether its last syllable ends in a consonant. */
internal fun withObjectParticle(word: String): String {
    val last = word.lastOrNull { it in '가'..'힣' } ?: return "${word}을"
    return word + if ((last - '가') % 28 > 0) "을" else "를"
}

/** The bridge of the running app shell; null in previews and screen tests without one (D-107). */
val LocalSchoolServiceBridge = androidx.compose.runtime.staticCompositionLocalOf<SchoolServiceBridge?> { null }

/** Hands one school-site read at a time to the WebView host in the app shell (D-107). */
class SchoolServiceBridge(
    /** D-135: the 수업 tab's knowledge of a sign-in elsewhere, shared because the portal account is the same. */
    val portalGate: PortalSignInGate? = null,
) {
    private val mutableRequest = MutableStateFlow<SchoolServiceRequest?>(null)
    val request: StateFlow<SchoolServiceRequest?> = mutableRequest.asStateFlow()

    suspend fun read(
        service: SchoolService,
        targetUrl: String,
        script: String,
        purpose: String,
        automatic: Boolean = false,
    ): SchoolServiceResult = start(service, targetUrl, script, purpose, automatic = automatic)

    /**
     * Shows [targetUrl] signed in, full screen, for the student to use on the school's own page; the app enters
     * nothing there. Returns [SchoolServiceResult.Cancelled] once the student closes it, or a failure when the
     * page could not be opened.
     */
    suspend fun open(service: SchoolService, targetUrl: String, title: String, fitsPage: Boolean = true): SchoolServiceResult =
        start(service, targetUrl, null, title, fitsPage)

    private suspend fun start(
        service: SchoolService,
        targetUrl: String,
        script: String?,
        purpose: String,
        fitsPage: Boolean = true,
        automatic: Boolean = false,
    ): SchoolServiceResult {
        require(SchoolUrlPolicy.isAllowedHttps(targetUrl) && java.net.URI.create(targetUrl).host == service.host)
        val pending = SchoolServiceRequest(service, targetUrl, script, purpose, CompletableDeferred(), fitsPage, automatic)
        if (!mutableRequest.compareAndSet(null, pending)) {
            return SchoolServiceResult.Failure("다른 학교 정보를 불러오는 중이에요. 잠시 후 다시 시도해 주세요.")
        }
        return try {
            pending.result.await()
        } finally {
            mutableRequest.compareAndSet(pending, null)
        }
    }

    fun complete(request: SchoolServiceRequest, result: SchoolServiceResult) {
        request.result.complete(result)
    }
}

/** Wraps [body] so the WebView can start it with `evaluateJavascript` and collect its answer by polling. */
internal fun schoolScriptLauncher(request: SchoolServiceRequest): String = """
    (function(){
      window['${request.resultSlot}']=null;
      (async function(){
        ${request.script}
      })().then(function(value){
        window['${request.resultSlot}']=JSON.stringify(value||{state:'error',message:'empty'});
      },function(error){
        window['${request.resultSlot}']=JSON.stringify({state:'error',message:String(error&&error.message||error)});
      });
      return 'started';
    })()
""".trimIndent()

internal fun schoolScriptPoll(request: SchoolServiceRequest): String = "window['${request.resultSlot}']"

/** What the school-site host does on the portal's sign-in page (D-107, D-135). */
internal enum class SchoolSignInStep {
    /** Send the saved account. */
    SUBMIT,
    /** No saved account: show the sign-in form. */
    ASK_ACCOUNT,
    /** Another device most likely holds the sign-in: ask before signing it out. */
    CONFIRM,
    /** Same, for a read the app started by itself: do not sign in at all. */
    STOP,
}

internal fun schoolSignInStep(
    hasSavedAccount: Boolean,
    signedInElsewhere: Boolean,
    approved: Boolean,
    automatic: Boolean,
): SchoolSignInStep = when {
    !hasSavedAccount -> SchoolSignInStep.ASK_ACCOUNT
    !signedInElsewhere || approved -> SchoolSignInStep.SUBMIT
    automatic -> SchoolSignInStep.STOP
    else -> SchoolSignInStep.CONFIRM
}

/** D-135: an automatic read stopped before signing in, as the 수업 tab saw a sign-in elsewhere. */
internal const val SCHOOL_SIGNED_IN_ELSEWHERE_MESSAGE =
    "다른 곳에서 학교 계정으로 로그인한 것 같아 자동으로 로그인하지 않았어요. 새로고침하면 로그인할지 물어봐요."
