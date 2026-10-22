package com.example.dimanow.school

import java.net.URI
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * School sites the app reads with the student's own sign-in (D-107): the intranet (timetable, dorm points) and
 * DIMA C (equipment). Both are reached through the school portal, like LMS.
 */
enum class SchoolService(val host: String, val label: String, val portalEntry: String) {
    INTRANET(
        host = "intranet.dima.ac.kr",
        label = "인트라넷",
        portalEntry = "https://portal.dima.ac.kr/?r=https://intranet.dima.ac.kr/default.aspx",
    ),
    DIMA_C(
        host = "udimac.dima.ac.kr",
        label = "DIMA C",
        portalEntry = "https://portal.dima.ac.kr/?r=https://udimac.dima.ac.kr/sso/index.jsp",
    ),
}

/**
 * Where the school-service WebView may go (D-107): HTTPS on the portal and the two service hosts, plus the
 * portal's plain-HTTP ticket hop on `sso.dima.ac.kr:8080` under `/sso/` (as D-073 allows for LMS sign-in).
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
 * One read on a school site: open [targetUrl] (signing in through the portal first when needed) and run
 * [script] there. The script must be an async function body that returns an object `{state, data, message}`:
 * `state` is `ok`, `signin` (the page is not signed in) or `error`.
 */
class SchoolServiceRequest internal constructor(
    val service: SchoolService,
    val targetUrl: String,
    val script: String,
    /** What is being read, for the sign-in screen: `시간표`, `상벌점`, `장비 대여 현황`. */
    val purpose: String,
    internal val result: CompletableDeferred<SchoolServiceResult>,
) {
    /** A per-request global the page cannot guess, where the async script leaves its answer. */
    internal val resultSlot: String = "__dimaNow_" + UUID.randomUUID().toString().replace("-", "")
}

/** The bridge of the running app shell; null in previews and screen tests without one (D-107). */
val LocalSchoolServiceBridge = androidx.compose.runtime.staticCompositionLocalOf<SchoolServiceBridge?> { null }

/** Hands one school-site read at a time to the WebView host in the app shell (D-107). */
class SchoolServiceBridge {
    private val mutableRequest = MutableStateFlow<SchoolServiceRequest?>(null)
    val request: StateFlow<SchoolServiceRequest?> = mutableRequest.asStateFlow()

    suspend fun read(service: SchoolService, targetUrl: String, script: String, purpose: String): SchoolServiceResult {
        require(SchoolUrlPolicy.isAllowedHttps(targetUrl) && java.net.URI.create(targetUrl).host == service.host)
        val pending = SchoolServiceRequest(service, targetUrl, script, purpose, CompletableDeferred())
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
