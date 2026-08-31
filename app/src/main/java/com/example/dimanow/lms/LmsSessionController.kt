package com.example.dimanow.lms

import java.time.Clock
import java.time.Duration
import java.time.Instant
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
