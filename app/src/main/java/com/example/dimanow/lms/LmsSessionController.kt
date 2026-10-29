package com.example.dimanow.lms

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.net.URI
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException

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
    /**
     * Signed in after the portal answered 3045 (D-135). That answer had already signed the student's other device or
     * browser out of the LMS, so the student is told.
     */
    data object SuccessEndingElsewhere : LmsLoginResult
    data object CredentialsRejected : LmsLoginResult
    /** The portal answered 3045 to the second attempt too: someone signed in elsewhere again in between. */
    data class SessionTakeoverFailed(
        val message: String = "다른 곳에서 다시 로그인해서 이 기기로 로그인하지 못했어요. 잠시 후 다시 시도해 주세요",
    ) : LmsLoginResult
    data object InteractiveAuthenticationRequired : LmsLoginResult
    data class NetworkError(val message: String) : LmsLoginResult
    data class Failure(val message: String) : LmsLoginResult
}

internal val LmsLoginResult.signedIn: Boolean
    get() = this == LmsLoginResult.Success || this == LmsLoginResult.SuccessEndingElsewhere

internal fun LmsLoginResult.failureMessage(): String? = when (this) {
    LmsLoginResult.Success, LmsLoginResult.SuccessEndingElsewhere -> null
    LmsLoginResult.CredentialsRejected -> "계정 정보를 다시 확인해 주세요"
    is LmsLoginResult.SessionTakeoverFailed -> message
    LmsLoginResult.InteractiveAuthenticationRequired -> "공식 LMS에서 추가 인증이 필요해요"
    is LmsLoginResult.NetworkError -> LmsUserMessages.NETWORK
    is LmsLoginResult.Failure -> message
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
            pending.result.cancel()
            mutableRequest.compareAndSet(pending, null)
        }
    }

    fun complete(result: LmsLoginResult) {
        mutableRequest.value?.result?.complete(result)
    }

    fun complete(request: LmsLoginRequest, result: LmsLoginResult) {
        if (mutableRequest.value === request) request.result.complete(result)
    }

    fun cancel() {
        complete(LmsLoginResult.Failure("로그인을 취소했어요"))
    }
}

internal enum class LmsLoginPageAction { WAIT, LOAD_DASHBOARD, EXTRACT_COURSES }

internal enum class LmsLoginFlowStage {
    CREDENTIALS_PENDING,
    LOGIN_SUBMISSION,
    /** D-135: the portal answered 3045, which already ended the sign-in held elsewhere; signing in once more. */
    CONFLICT_RETRY,
    LMS_MAIN_CONFIRMATION,
    COURSE_LIST_EXTRACTION,
    SUCCESS,
    TERMINAL_ERROR,
}

internal data class LmsLoginFlowState(
    val stage: LmsLoginFlowStage,
    /** Sign-ins sent again after a 3045 answer: at most one. */
    val conflictRetries: Int = 0,
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
    /**
     * LMS sent the login back to its main page without a signed-in menu, or left it on a sign-in page, after the
     * credentials step: a login that did not stick.
     */
    data object LoginNotEstablished : LmsLoginFlowEvent
    data object InteractiveChallengeDetected : LmsLoginFlowEvent
    data object CredentialsRejected : LmsLoginFlowEvent
    data class CourseExtractionFinished(val hasCourses: Boolean) : LmsLoginFlowEvent
}

internal sealed interface LmsLoginFlowCommand {
    data object None : LmsLoginFlowCommand
    data object SubmitCredentials : LmsLoginFlowCommand
    /** Open the official login address again and send the saved account once more (D-135). */
    data object RetryCredentials : LmsLoginFlowCommand
    data object LoadDashboard : LmsLoginFlowCommand
    data object ExtractCourses : LmsLoginFlowCommand
    data class Complete(val result: LmsLoginResult) : LmsLoginFlowCommand
}

internal data class LmsLoginFlowTransition(
    val state: LmsLoginFlowState,
    val command: LmsLoginFlowCommand,
)

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
        if (state.conflictRetries == 0) {
            // D-135: the portal ends the sign-in held elsewhere as it answers 3045, so asking now would come too
            // late; one more sign-in goes through.
            LmsLoginFlowTransition(
                state.copy(stage = LmsLoginFlowStage.CONFLICT_RETRY, conflictRetries = 1, result = null),
                LmsLoginFlowCommand.RetryCredentials,
            )
        } else {
            val result = LmsLoginResult.SessionTakeoverFailed()
            LmsLoginFlowTransition(
                state.copy(stage = LmsLoginFlowStage.TERMINAL_ERROR, result = result),
                LmsLoginFlowCommand.Complete(result),
            )
        }
    } else if (!LmsUrlPolicy.isAllowedLoginNavigation(event.url)) {
        val result = LmsLoginResult.Failure(LmsUserMessages.UNSAFE_PAGE_BLOCKED)
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
    LmsLoginFlowEvent.LoginNotEstablished -> {
        val result = LmsLoginResult.Failure(LmsUserMessages.LOGIN_NOT_ESTABLISHED)
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
    LmsLoginFlowEvent.CredentialsRejected -> if (
        state.stage == LmsLoginFlowStage.LOGIN_SUBMISSION || state.stage == LmsLoginFlowStage.CONFLICT_RETRY
    ) {
        val result = LmsLoginResult.CredentialsRejected
        LmsLoginFlowTransition(
            state.copy(stage = LmsLoginFlowStage.TERMINAL_ERROR, result = result),
            LmsLoginFlowCommand.Complete(result),
        )
    } else {
        LmsLoginFlowTransition(state, LmsLoginFlowCommand.None)
    }
    is LmsLoginFlowEvent.CourseExtractionFinished -> if (event.hasCourses) {
        val result = if (state.conflictRetries > 0) LmsLoginResult.SuccessEndingElsewhere else LmsLoginResult.Success
        LmsLoginFlowTransition(
            state.copy(stage = LmsLoginFlowStage.SUCCESS, result = result),
            LmsLoginFlowCommand.Complete(result),
        )
    } else {
        val result = LmsLoginResult.Failure(LmsUserMessages.COURSE_CATALOG_FAILED)
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
    // Must stay in step with PORTAL_CREDENTIAL_PAGES / LMS_CREDENTIAL_PAGES, which the injected script re-checks.
    uri.scheme == "https" && uri.userInfo == null && uri.port in setOf(-1, 443) && when (uri.host) {
        "portal.dima.ac.kr" -> uri.path.isNullOrBlank() || uri.path == "/" || uri.path == "/default.aspx"
        "lms.dima.ac.kr" -> uri.path == "/login/doLoginPage.dunet"
        else -> false
    }
}.getOrDefault(false)

internal fun shouldReviewStoredLmsCredentials(url: String, submitted: Boolean, elapsedMillis: Long): Boolean =
    submitted && elapsedMillis >= 10_000L && isOfficialLmsCredentialPage(url)

internal fun shouldConfirmOfficialLmsLoginDialog(url: String): Boolean =
    isOfficialLmsCredentialPage(url)

/**
 * D-135: whether a sign-in to the school portal now would most likely sign another device out of a sign-in it made
 * after this device's. The 수업 tab and the school-site host share the portal account, so they share this.
 */
interface PortalSignInGate {
    val signedInElsewhere: StateFlow<Boolean>

    /** The student chose to sign in on this device anyway. */
    fun allowSignInHere()

    /**
     * The app signed in to the portal itself through a school site. Any sign-in elsewhere ended then, so the 수업
     * tab's next sign-in is the app's own again, not one to wait on.
     */
    fun signedInThroughPortal()
}

/** What the app keeps of its own LMS session (D-135) across launches: times only, no account data. */
interface LmsSessionRecord {
    /** When this device last signed in to the LMS or read from it. */
    fun lastActivity(): Instant?
    fun recordActivity(at: Instant)

    /** When this device last signed in to the portal through a school site. */
    fun lastPortalSignIn(): Instant?
    fun recordPortalSignIn(at: Instant)

    /** Since when the app waits for 「이 기기로 로그인」, or null. */
    fun waitingSince(): Instant?
    fun setWaitingSince(at: Instant?)
}

internal class InMemoryLmsSessionRecord : LmsSessionRecord {
    @Volatile private var activity: Instant? = null
    @Volatile private var portal: Instant? = null
    @Volatile private var waiting: Instant? = null
    override fun lastActivity(): Instant? = activity
    override fun recordActivity(at: Instant) {
        activity = at
    }
    override fun lastPortalSignIn(): Instant? = portal
    override fun recordPortalSignIn(at: Instant) {
        portal = at
    }
    override fun waitingSince(): Instant? = waiting
    override fun setWaitingSince(at: Instant?) {
        waiting = at
    }
}

/**
 * The LMS signs a session out two hours after its last activity (the student, 2026-10-26: the limit was raised from
 * the notice's one hour and runs from the last activity).
 */
internal val LMS_SESSION_LIMIT: Duration = Duration.ofHours(2)

class LmsAutoLoginCoordinator(
    private val credentialStore: LmsCredentialStore,
    private val sessionController: LmsSessionController,
    private val loginDriver: LmsLoginDriver,
    private val clock: Clock = Clock.systemUTC(),
    private val sessionRecord: LmsSessionRecord = InMemoryLmsSessionRecord(),
) : PortalSignInGate {
    private val mutex = Mutex()
    private var retryAfter: Instant? = null
    private val mutableErrorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = mutableErrorMessage.asStateFlow()
    private val mutableSignedInElsewhere = MutableStateFlow(sessionRecord.waitingSince() != null)
    /**
     * True while the app waits for the student's 「이 기기로 로그인」 ([ensureActive] with `signInHere`, D-135): the LMS
     * ended this device's session within two hours of its last activity there, or answered 3045 twice in a row, so
     * another device most likely holds it. The wait has no time limit and lasts across launches: the app cannot see
     * whether the other place is still in use, and signing in here would end it.
     */
    override val signedInElsewhere: StateFlow<Boolean> = mutableSignedInElsewhere.asStateFlow()
    private val mutableNotices = MutableSharedFlow<String>(extraBufferCapacity = 1)
    /** One-off messages for the screen, such as a sign-in that ended the one elsewhere. */
    val notices: SharedFlow<String> = mutableNotices.asSharedFlow()

    override fun allowSignInHere() = stopWaiting()

    override fun signedInThroughPortal() {
        sessionRecord.recordPortalSignIn(clock.instant())
        stopWaiting()
    }

    /** A read from the LMS went through, so the session was alive until now. */
    fun noteActivity() {
        sessionRecord.recordActivity(clock.instant())
    }

    private fun startWaiting() {
        if (mutableSignedInElsewhere.value) return
        sessionRecord.setWaitingSince(clock.instant())
        mutableSignedInElsewhere.value = true
    }

    private fun stopWaiting() {
        if (!mutableSignedInElsewhere.value && sessionRecord.waitingSince() == null) return
        sessionRecord.setWaitingSince(null)
        mutableSignedInElsewhere.value = false
    }

    suspend fun ensureActive(
        force: Boolean,
        credentialsChanged: Boolean = false,
        signInHere: Boolean = false,
    ): LmsSessionState = mutex.withLock {
        val current = sessionController.state.value
        if (!credentialsChanged && current == LmsSessionState.ACTIVE) return@withLock current
        if (signInHere || credentialsChanged) stopWaiting()
        if (mutableSignedInElsewhere.value) return@withLock current
        if (!force && !credentialsChanged && current == LmsSessionState.CREDENTIALS_NEED_REVIEW) return@withLock current
        if (!force && !credentialsChanged && retryAfter?.isAfter(clock.instant()) == true) return@withLock current
        mutableErrorMessage.value = null
        val credentials = credentialStore.load()
        if (credentials == null || !credentials.automaticLogin) {
            sessionController.transition(LmsSessionState.SIGNED_OUT)
            return@withLock LmsSessionState.SIGNED_OUT
        }
        sessionController.transition(LmsSessionState.AUTHENTICATING)
        val result = try {
            loginDriver.authenticate(credentials)
        } catch (cancelled: CancellationException) {
            if (sessionController.state.value == LmsSessionState.AUTHENTICATING) {
                sessionController.transition(LmsSessionState.EXPIRED)
            }
            throw cancelled
        }
        mutableErrorMessage.value = result.failureMessage()
        if (result == LmsLoginResult.SuccessEndingElsewhere) mutableNotices.tryEmit(LmsUserMessages.SIGNED_OUT_ELSEWHERE)
        when (result) {
            LmsLoginResult.Success, LmsLoginResult.SuccessEndingElsewhere -> {
                retryAfter = null
                sessionRecord.recordActivity(clock.instant())
                LmsSessionState.ACTIVE
            }
            LmsLoginResult.CredentialsRejected -> LmsSessionState.CREDENTIALS_NEED_REVIEW
            is LmsLoginResult.SessionTakeoverFailed -> {
                // Someone signed in elsewhere again right after the first 3045: wait for the student instead of
                // taking the session back on the next automatic sign-in.
                startWaiting()
                LmsSessionState.ERROR
            }
            LmsLoginResult.InteractiveAuthenticationRequired -> LmsSessionState.INTERACTIVE_AUTH_REQUIRED
            is LmsLoginResult.NetworkError -> {
                retryAfter = clock.instant().plus(RETRY_SUPPRESSION)
                LmsSessionState.ERROR
            }
            is LmsLoginResult.Failure -> LmsSessionState.ERROR
        }.also(sessionController::transition)
    }

    /**
     * A read found the LMS session ended. Within [LMS_SESSION_LIMIT] of this device's last LMS activity that was not
     * the time limit: someone signed in elsewhere (D-135), unless the app itself signed in to the portal through a
     * school site since then, which ended any such sign-in. A clock set back is not taken for "within".
     */
    fun markExpired() {
        val now = clock.instant()
        val last = sessionRecord.lastActivity()
        val portal = sessionRecord.lastPortalSignIn()
        val endedEarly = last != null && !now.isBefore(last) && Duration.between(last, now) < LMS_SESSION_LIMIT
        val ownPortalSignInSince = last != null && portal != null && portal.isAfter(last)
        if (endedEarly && !ownPortalSignInSince) startWaiting()
        if (sessionController.state.value == LmsSessionState.ACTIVE) {
            sessionController.transition(LmsSessionState.EXPIRED)
        }
    }

    private companion object {
        val RETRY_SUPPRESSION: Duration = Duration.ofMinutes(15)
    }
}
