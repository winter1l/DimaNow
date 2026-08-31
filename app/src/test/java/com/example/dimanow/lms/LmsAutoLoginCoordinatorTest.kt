package com.example.dimanow.lms

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LmsAutoLoginCoordinatorTest {
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
