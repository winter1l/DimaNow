package com.example.dimanow.lms

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LmsAutoLoginCoordinatorTest {
    @Test
    fun aLateCompletionFromAnAbandonedPageCannotCompleteTheNextLogin() = runTest {
        val bridge = LmsLoginBridge()
        val credentials = SavedLmsCredentials("student", "secret")
        val abandoned = async { bridge.authenticate(credentials) }
        runCurrent()
        val oldRequest = requireNotNull(bridge.request.value)
        abandoned.cancelAndJoin()
        val retry = async { bridge.authenticate(credentials) }
        runCurrent()
        val newRequest = requireNotNull(bridge.request.value)

        bridge.complete(oldRequest, LmsLoginResult.Success)
        runCurrent()

        assertFalse(retry.isCompleted)
        assertEquals(newRequest, bridge.request.value)
        bridge.complete(newRequest, LmsLoginResult.Success)
        assertEquals(LmsLoginResult.Success, retry.await())
        assertNull(bridge.request.value)
    }

    @Test
    fun cancelledAutomaticLoginCanRetryInsteadOfRemainingInProgress() = runTest {
        val credentials = SavedLmsCredentials("student", "secret")
        val store = RecordingCredentialStore(credentials)
        val session = MutableLmsSessionController(LmsSessionState.EXPIRED)
        val bridge = LmsLoginBridge()
        val coordinator = LmsAutoLoginCoordinator(store, session, bridge, fixedClock())
        val abandoned = async { coordinator.ensureActive(force = false) }
        runCurrent()
        assertEquals(LmsSessionState.AUTHENTICATING, session.state.value)

        abandoned.cancelAndJoin()

        assertTrue(abandoned.isCancelled)
        assertEquals(LmsSessionState.EXPIRED, session.state.value)
        val retry = async { coordinator.ensureActive(force = false) }
        runCurrent()
        bridge.complete(LmsLoginResult.Success)
        assertEquals(LmsSessionState.ACTIVE, retry.await())
        assertEquals(LmsSessionState.ACTIVE, session.state.value)
    }

    @Test
    fun cancellingLoginDoesNotUndoAnExplicitSignOut() = runTest {
        val store = RecordingCredentialStore(SavedLmsCredentials("student", "secret"))
        val session = MutableLmsSessionController(LmsSessionState.EXPIRED)
        val driver = object : LmsLoginDriver {
            override suspend fun authenticate(credentials: SavedLmsCredentials): LmsLoginResult = awaitCancellation()
        }
        val coordinator = LmsAutoLoginCoordinator(store, session, driver, fixedClock())
        val abandoned = async { coordinator.ensureActive(force = false) }
        runCurrent()

        session.transition(LmsSessionState.SIGNED_OUT)
        abandoned.cancelAndJoin()

        assertEquals(LmsSessionState.SIGNED_OUT, session.state.value)
    }

    @Test
    fun abandoningLoginCancelsThePendingRequestAndAllowsTheNextLoginToComplete() = runTest {
        val bridge = LmsLoginBridge()
        val credentials = SavedLmsCredentials("student", "secret")
        val abandoned = async { bridge.authenticate(credentials) }
        runCurrent()
        val oldRequest = requireNotNull(bridge.request.value)

        abandoned.cancelAndJoin()

        assertNull(bridge.request.value)
        assertTrue(oldRequest.result.isCancelled)
        val retry = async { bridge.authenticate(credentials) }
        runCurrent()
        bridge.complete(LmsLoginResult.Success)
        assertEquals(LmsLoginResult.Success, retry.await())
        assertNull(bridge.request.value)
    }

    @Test
    fun manualRefreshKeepsAnActiveSessionWithoutStartingAnotherLogin() = runTest {
        val store = RecordingCredentialStore(SavedLmsCredentials("student", "secret"))
        val session = MutableLmsSessionController(LmsSessionState.ACTIVE)
        val driver = RecordingLoginDriver(LmsLoginResult.Success)
        val coordinator = LmsAutoLoginCoordinator(store, session, driver, fixedClock())

        assertEquals(LmsSessionState.ACTIVE, coordinator.ensureActive(force = true))

        assertEquals(0, store.loads)
        assertEquals(0, driver.attempts)
    }

    @Test
    fun activeSessionDoesNotDecryptStoredCredentials() = runTest {
        val store = RecordingCredentialStore(SavedLmsCredentials("student", "secret"))
        val session = MutableLmsSessionController(LmsSessionState.ACTIVE)
        val driver = RecordingLoginDriver(LmsLoginResult.Success)
        val coordinator = LmsAutoLoginCoordinator(store, session, driver, fixedClock())

        coordinator.ensureActive(force = false)

        assertEquals(0, store.loads)
        assertEquals(0, driver.attempts)
    }

    @Test
    fun rejectedCredentialsAreAttemptedOnceAndRequireUserReview() = runTest {
        val store = RecordingCredentialStore(SavedLmsCredentials("student", "wrong"))
        val session = MutableLmsSessionController()
        val driver = RecordingLoginDriver(LmsLoginResult.CredentialsRejected)
        val coordinator = LmsAutoLoginCoordinator(store, session, driver, fixedClock())

        coordinator.ensureActive(force = false)
        coordinator.ensureActive(force = false)

        assertEquals(1, driver.attempts)
        assertEquals(LmsSessionState.CREDENTIALS_NEED_REVIEW, session.state.value)
    }

    @Test
    fun sessionConflictDoesNotMarkStoredCredentialsForReview() = runTest {
        val store = RecordingCredentialStore(SavedLmsCredentials("student", "secret"))
        val session = MutableLmsSessionController()
        val driver = RecordingLoginDriver(LmsLoginResult.SessionConflict)
        val coordinator = LmsAutoLoginCoordinator(store, session, driver, fixedClock())

        coordinator.ensureActive(force = false)

        assertEquals(1, driver.attempts)
        assertEquals(LmsSessionState.ERROR, session.state.value)
    }

    @Test
    fun networkFailureSuppressesAutomaticRetryForFifteenMinutesButManualRetryBypassesIt() = runTest {
        val store = RecordingCredentialStore(SavedLmsCredentials("student", "secret"))
        val session = MutableLmsSessionController()
        val driver = RecordingLoginDriver(LmsLoginResult.NetworkError("offline"))
        val coordinator = LmsAutoLoginCoordinator(store, session, driver, fixedClock())

        coordinator.ensureActive(force = false)
        coordinator.ensureActive(force = false)
        coordinator.ensureActive(force = true)

        assertEquals(2, driver.attempts)
        assertEquals(LmsSessionState.ERROR, session.state.value)
    }

    @Test
    fun newlySavedCredentialsRetryAfterRejectionAndReplaceAnActiveAccount() = runTest {
        val store = RecordingCredentialStore(SavedLmsCredentials("student", "secret"))
        val session = MutableLmsSessionController(LmsSessionState.CREDENTIALS_NEED_REVIEW)
        val driver = RecordingLoginDriver(LmsLoginResult.Success)
        val coordinator = LmsAutoLoginCoordinator(store, session, driver, fixedClock())

        assertEquals(LmsSessionState.ACTIVE, coordinator.ensureActive(force = true, credentialsChanged = true))
        assertEquals(LmsSessionState.ACTIVE, coordinator.ensureActive(force = true, credentialsChanged = true))

        assertEquals(2, store.loads)
        assertEquals(2, driver.attempts)
    }

    @Test
    fun failedLoginExposesItsReasonAndSuccessfulRetryClearsIt() = runTest {
        val store = RecordingCredentialStore(SavedLmsCredentials("student", "secret"))
        val session = MutableLmsSessionController()
        var attempts = 0
        val driver = object : LmsLoginDriver {
            override suspend fun authenticate(credentials: SavedLmsCredentials): LmsLoginResult =
                if (attempts++ == 0) LmsLoginResult.Failure("안전하지 않은 페이지가 차단되었습니다")
                else LmsLoginResult.Success
        }
        val coordinator = LmsAutoLoginCoordinator(store, session, driver, fixedClock())

        coordinator.ensureActive(force = false)
        assertEquals("안전하지 않은 페이지가 차단되었습니다", coordinator.errorMessage.value)
        coordinator.ensureActive(force = true)

        assertEquals(null, coordinator.errorMessage.value)
        assertEquals(LmsSessionState.ACTIVE, session.state.value)
    }

    private fun fixedClock(): Clock = Clock.fixed(Instant.parse("2026-08-31T10:00:00Z"), ZoneOffset.UTC)
}

private class RecordingCredentialStore(private val saved: SavedLmsCredentials?) : LmsCredentialStore {
    private val mutableState = MutableStateFlow(if (saved == null) CredentialState.EMPTY else CredentialState.SAVED)
    override val state: StateFlow<CredentialState> = mutableState
    var loads = 0

    override suspend fun save(credentials: SavedLmsCredentials) = Unit
    override suspend fun load(): SavedLmsCredentials? = saved.also { loads++ }
    override suspend fun delete() = Unit
}

private class RecordingLoginDriver(private val result: LmsLoginResult) : LmsLoginDriver {
    var attempts = 0
    override suspend fun authenticate(credentials: SavedLmsCredentials): LmsLoginResult = result.also { attempts++ }
}
