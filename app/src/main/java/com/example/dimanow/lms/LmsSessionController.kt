package com.example.dimanow.lms

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.net.URI
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CompletableDeferred

interface LmsSessionController {
    val state: StateFlow<LmsSessionState>
    fun transition(state: LmsSessionState)
}

class MutableLmsSessionController(initial: LmsSessionState = LmsSessionState.SIGNED_OUT) : LmsSessionController {
    private val mutableState = MutableStateFlow(initial)
    override val state: StateFlow<LmsSessionState> = mutableState.asStateFlow()
    override fun transition(state: LmsSessionState) {
        mutableState.value = state
    }
}

sealed interface LmsLoginResult {
    data object Success : LmsLoginResult
    data object CredentialsRejected : LmsLoginResult
    data object SessionConflict : LmsLoginResult
    data class SessionTakeoverFailed(
        val message: String = "다른 로그인 세션을 전환하지 못했습니다",
    ) : LmsLoginResult
    data object InteractiveAuthenticationRequired : LmsLoginResult
    data class NetworkError(val message: String) : LmsLoginResult
    data class Failure(val message: String) : LmsLoginResult
}

interface LmsLoginDriver {
    suspend fun authenticate(credentials: SavedLmsCredentials): LmsLoginResult
}

class LmsLoginRequest internal constructor(
    val credentials: SavedLmsCredentials,
    internal val result: CompletableDeferred<LmsLoginResult>,
)

class LmsLoginBridge : LmsLoginDriver {
    private val mutableRequest = MutableStateFlow<LmsLoginRequest?>(null)
    val request: StateFlow<LmsLoginRequest?> = mutableRequest.asStateFlow()

    override suspend fun authenticate(credentials: SavedLmsCredentials): LmsLoginResult {
        val pending = LmsLoginRequest(credentials, CompletableDeferred())
        check(mutableRequest.compareAndSet(null, pending)) { "LMS login is already in progress" }
        return try {
            pending.result.await()
        } finally {
            mutableRequest.compareAndSet(pending, null)
        }
    }

    fun complete(result: LmsLoginResult) {
        mutableRequest.value?.result?.complete(result)
    }

    fun cancel() {
        complete(LmsLoginResult.Failure("로그인이 취소되었습니다"))
    }
}

internal enum class LmsLoginPageAction { WAIT, LOAD_DASHBOARD, EXTRACT_COURSES }

internal enum class LmsLoginFlowStage {
    CREDENTIALS_PENDING,
    LOGIN_SUBMISSION,
    SESSION_CONFLICT,
    SESSION_TAKEOVER_SUBMISSION,
    LMS_MAIN_CONFIRMATION,
    COURSE_LIST_EXTRACTION,
    SUCCESS,
    TERMINAL_ERROR,
}

internal data class LmsLoginFlowState(
    val stage: LmsLoginFlowStage,
    val sessionTakeoverSubmissions: Int = 0,
    val result: LmsLoginResult? = null,
) {
    companion object {
        fun initial(): LmsLoginFlowState = LmsLoginFlowState(LmsLoginFlowStage.CREDENTIALS_PENDING)
    }
}

internal sealed interface LmsLoginFlowEvent {
    data object CredentialsAvailable : LmsLoginFlowEvent
    data class MainFrameFinished(
        val url: String,
        val authenticatedMain: Boolean = false,
    ) : LmsLoginFlowEvent
    /** Emitted only after the adapter verifies the live official 3045 page's own action. */
    data object SessionTakeoverActionVerified : LmsLoginFlowEvent
    data object SessionTakeoverActionUnavailable : LmsLoginFlowEvent
    data object InteractiveChallengeDetected : LmsLoginFlowEvent
    data object CredentialsRejected : LmsLoginFlowEvent
    data class CourseExtractionFinished(val hasCourses: Boolean) : LmsLoginFlowEvent
}

internal sealed interface LmsLoginFlowCommand {
    data object None : LmsLoginFlowCommand
    data object SubmitCredentials : LmsLoginFlowCommand
    data object InspectSessionTakeoverAction : LmsLoginFlowCommand
    data object SubmitVerifiedSessionTakeover : LmsLoginFlowCommand
    data object LoadDashboard : LmsLoginFlowCommand
    data object ExtractCourses : LmsLoginFlowCommand
    data class Complete(val result: LmsLoginResult) : LmsLoginFlowCommand
}

internal data class LmsLoginFlowTransition(
    val state: LmsLoginFlowState,
    val command: LmsLoginFlowCommand,
)

internal enum class LmsSessionTakeoverScriptResult {
    VERIFIED,
    SUBMITTED,
    INTERACTIVE,
    UNAVAILABLE,
}

/**
 * WebView callbacks are deliberately reduced to a four-token protocol. The live portal page's
 * action, form fields, and any transient authentication values never cross into Kotlin or logs.
 */
internal fun parseLmsSessionTakeoverScriptResult(raw: String?): LmsSessionTakeoverScriptResult = when (raw) {
    "\"verified\"" -> LmsSessionTakeoverScriptResult.VERIFIED
    "\"submitted\"" -> LmsSessionTakeoverScriptResult.SUBMITTED
    "\"interactive\"" -> LmsSessionTakeoverScriptResult.INTERACTIVE
    else -> LmsSessionTakeoverScriptResult.UNAVAILABLE
}

internal fun reduceLmsLoginFlow(
    state: LmsLoginFlowState,
    event: LmsLoginFlowEvent,
): LmsLoginFlowTransition {
    if (state.stage == LmsLoginFlowStage.SUCCESS || state.stage == LmsLoginFlowStage.TERMINAL_ERROR) {
        return LmsLoginFlowTransition(state, LmsLoginFlowCommand.None)
    }
    return when (event) {
    LmsLoginFlowEvent.CredentialsAvailable -> LmsLoginFlowTransition(
        state.copy(stage = LmsLoginFlowStage.LOGIN_SUBMISSION),
        LmsLoginFlowCommand.SubmitCredentials,
    )
    is LmsLoginFlowEvent.MainFrameFinished -> if (isExactOfficialLmsSessionConflictUrl(event.url)) {
        if (state.sessionTakeoverSubmissions == 0) {
            LmsLoginFlowTransition(
                state.copy(stage = LmsLoginFlowStage.SESSION_CONFLICT, result = LmsLoginResult.SessionConflict),
                LmsLoginFlowCommand.InspectSessionTakeoverAction,
            )
        } else {
            val result = LmsLoginResult.SessionTakeoverFailed()
            LmsLoginFlowTransition(
                state.copy(stage = LmsLoginFlowStage.TERMINAL_ERROR, result = result),
                LmsLoginFlowCommand.Complete(result),
            )
        }
    } else if (!LmsUrlPolicy.isAllowedLoginNavigation(event.url)) {
        val result = LmsLoginResult.Failure("안전하지 않은 페이지가 차단되었습니다")
        LmsLoginFlowTransition(
            state.copy(stage = LmsLoginFlowStage.TERMINAL_ERROR, result = result),
            LmsLoginFlowCommand.Complete(result),
        )
    } else {
        when (lmsLoginPageAction(event.url, pageFinished = true, authenticatedMain = event.authenticatedMain)) {
            LmsLoginPageAction.WAIT -> LmsLoginFlowTransition(state, LmsLoginFlowCommand.None)
            LmsLoginPageAction.LOAD_DASHBOARD -> LmsLoginFlowTransition(
                state.copy(stage = LmsLoginFlowStage.LMS_MAIN_CONFIRMATION, result = null),
                LmsLoginFlowCommand.LoadDashboard,
            )
            LmsLoginPageAction.EXTRACT_COURSES -> LmsLoginFlowTransition(
                state.copy(stage = LmsLoginFlowStage.COURSE_LIST_EXTRACTION, result = null),
                LmsLoginFlowCommand.ExtractCourses,
            )
        }
    }
    LmsLoginFlowEvent.SessionTakeoverActionVerified -> if (
        state.stage == LmsLoginFlowStage.SESSION_CONFLICT && state.sessionTakeoverSubmissions == 0
    ) {
        LmsLoginFlowTransition(
            state.copy(
                stage = LmsLoginFlowStage.SESSION_TAKEOVER_SUBMISSION,
                sessionTakeoverSubmissions = 1,
                result = null,
            ),
            LmsLoginFlowCommand.SubmitVerifiedSessionTakeover,
        )
    } else {
        val result = LmsLoginResult.SessionTakeoverFailed()
        LmsLoginFlowTransition(
            state.copy(stage = LmsLoginFlowStage.TERMINAL_ERROR, result = result),
            LmsLoginFlowCommand.Complete(result),
        )
    }
    LmsLoginFlowEvent.SessionTakeoverActionUnavailable -> {
        val result = LmsLoginResult.SessionTakeoverFailed()
        LmsLoginFlowTransition(
            state.copy(stage = LmsLoginFlowStage.TERMINAL_ERROR, result = result),
            LmsLoginFlowCommand.Complete(result),
        )
    }
    LmsLoginFlowEvent.InteractiveChallengeDetected -> {
        val result = LmsLoginResult.InteractiveAuthenticationRequired
        LmsLoginFlowTransition(
            state.copy(stage = LmsLoginFlowStage.TERMINAL_ERROR, result = result),
            LmsLoginFlowCommand.Complete(result),
        )
    }
    LmsLoginFlowEvent.CredentialsRejected -> if (state.stage == LmsLoginFlowStage.LOGIN_SUBMISSION) {
        val result = LmsLoginResult.CredentialsRejected
        LmsLoginFlowTransition(
            state.copy(stage = LmsLoginFlowStage.TERMINAL_ERROR, result = result),
            LmsLoginFlowCommand.Complete(result),
        )
    } else {
        LmsLoginFlowTransition(state, LmsLoginFlowCommand.None)
    }
    is LmsLoginFlowEvent.CourseExtractionFinished -> if (event.hasCourses) {
        LmsLoginFlowTransition(
            state.copy(stage = LmsLoginFlowStage.SUCCESS, result = LmsLoginResult.Success),
            LmsLoginFlowCommand.Complete(LmsLoginResult.Success),
        )
    } else {
        val result = LmsLoginResult.Failure("수업 목록을 확인하지 못했습니다")
        LmsLoginFlowTransition(
            state.copy(stage = LmsLoginFlowStage.TERMINAL_ERROR, result = result),
            LmsLoginFlowCommand.Complete(result),
        )
    }
    }
}

internal fun lmsLoginPageAction(
    url: String,
    pageFinished: Boolean,
    authenticatedMain: Boolean,
): LmsLoginPageAction {
    if (!pageFinished) return LmsLoginPageAction.WAIT
    return when (runCatching { URI.create(url).path }.getOrNull()) {
        "/lms/myLecture/doListView.dunet" -> LmsLoginPageAction.EXTRACT_COURSES
        "/main/MainView.dunet" -> if (authenticatedMain) {
            LmsLoginPageAction.LOAD_DASHBOARD
        } else {
            LmsLoginPageAction.WAIT
        }
        else -> LmsLoginPageAction.WAIT
    }
}

internal fun isCompletedLmsLogin(url: String, pageFinished: Boolean, authenticatedMain: Boolean): Boolean =
    lmsLoginPageAction(url, pageFinished, authenticatedMain) == LmsLoginPageAction.EXTRACT_COURSES

internal fun isExactOfficialLmsSessionConflictUrl(url: String): Boolean = runCatching {
    val uri = URI.create(url)
    val queryParts = uri.rawQuery?.split('&').orEmpty()
    val errorCodeParts = queryParts.filter { it == "errorCode=3045" }
    val errorMessageParts = queryParts.filter { it.startsWith("errorMsg=") && it.length > "errorMsg=".length }
    uri.scheme.equals("https", ignoreCase = true) &&
        uri.host.equals("portal.dima.ac.kr", ignoreCase = true) &&
        uri.userInfo == null &&
        uri.port in setOf(-1, 443) &&
        uri.rawPath == "/sso/error.aspx" &&
        errorCodeParts.size == 1 &&
        errorMessageParts.size <= 1 &&
        queryParts.size == errorCodeParts.size + errorMessageParts.size &&
        uri.rawFragment == null
}.getOrDefault(false)

internal fun isOfficialLmsCredentialPage(url: String): Boolean = runCatching {
    val uri = URI.create(url)
    uri.scheme == "https" && when (uri.host) {
        "portal.dima.ac.kr" -> uri.path.isNullOrBlank() || uri.path == "/" || uri.path == "/default.aspx"
        "lms.dima.ac.kr" -> uri.path == "/login/doLoginPage.dunet"
        else -> false
    }
}.getOrDefault(false)

internal fun shouldReviewStoredLmsCredentials(url: String, submitted: Boolean, elapsedMillis: Long): Boolean =
    submitted && elapsedMillis >= 10_000L && isOfficialLmsCredentialPage(url)

internal fun shouldConfirmOfficialLmsLoginDialog(url: String): Boolean =
    isOfficialLmsCredentialPage(url)

internal fun shouldConfirmOfficialLmsSessionTakeoverDialog(url: String, message: String): Boolean {
    if (!isExactOfficialLmsSessionConflictUrl(url)) return false
    val normalized = message.replace(Regex("\\s+"), " ").trim()
    return normalized.contains("로그인") &&
        (normalized.contains("세션") || normalized.contains("중복")) &&
        (normalized.contains("종료") || normalized.contains("전환") || normalized.contains("계속"))
}

class LmsAutoLoginCoordinator(
    private val credentialStore: LmsCredentialStore,
    private val sessionController: LmsSessionController,
    private val loginDriver: LmsLoginDriver,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val mutex = Mutex()
    private var retryAfter: Instant? = null

    suspend fun ensureActive(force: Boolean): LmsSessionState = mutex.withLock {
        val current = sessionController.state.value
        if (!force && current == LmsSessionState.ACTIVE) return@withLock current
        if (!force && current == LmsSessionState.CREDENTIALS_NEED_REVIEW) return@withLock current
        if (!force && retryAfter?.isAfter(clock.instant()) == true) return@withLock current
        val credentials = credentialStore.load()
        if (credentials == null || !credentials.automaticLogin) {
            sessionController.transition(LmsSessionState.SIGNED_OUT)
            return@withLock LmsSessionState.SIGNED_OUT
        }
        sessionController.transition(LmsSessionState.AUTHENTICATING)
        when (loginDriver.authenticate(credentials)) {
            LmsLoginResult.Success -> {
                retryAfter = null
                LmsSessionState.ACTIVE
            }
            LmsLoginResult.CredentialsRejected -> LmsSessionState.CREDENTIALS_NEED_REVIEW
            LmsLoginResult.SessionConflict, is LmsLoginResult.SessionTakeoverFailed -> LmsSessionState.ERROR
            LmsLoginResult.InteractiveAuthenticationRequired -> LmsSessionState.INTERACTIVE_AUTH_REQUIRED
            is LmsLoginResult.NetworkError -> {
                retryAfter = clock.instant().plus(RETRY_SUPPRESSION)
                LmsSessionState.ERROR
            }
            is LmsLoginResult.Failure -> LmsSessionState.ERROR
        }.also(sessionController::transition)
    }

    fun markExpired() {
        if (sessionController.state.value == LmsSessionState.ACTIVE) {
            sessionController.transition(LmsSessionState.EXPIRED)
        }
    }

    private companion object {
        val RETRY_SUPPRESSION: Duration = Duration.ofMinutes(15)
    }
}
