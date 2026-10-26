package com.example.dimanow.lms

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
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
    fun aSecond3045InARowWaitsForTheStudentWithoutAskingForAccountReview() = runTest {
        // D-135: someone signed in elsewhere again right away, so the next automatic sign-in must not take it back.
        val store = RecordingCredentialStore(SavedLmsCredentials("student", "secret"))
        val session = MutableLmsSessionController()
        val driver = RecordingLoginDriver(LmsLoginResult.SessionTakeoverFailed())
        val coordinator = LmsAutoLoginCoordinator(store, session, driver, fixedClock())

        coordinator.ensureActive(force = false)
        coordinator.ensureActive(force = false)

        assertEquals(1, driver.attempts)
        assertEquals(LmsSessionState.ERROR, session.state.value)
        assertTrue(coordinator.signedInElsewhere.value)
    }

    @Test
    fun aSessionEndedWithinTwoHoursOfTheLastReadWaitsForTheStudent() = runTest {
        // D-135: the LMS keeps a session two hours after its last activity, so ending sooner means it was taken.
        val clock = MovableClock(Instant.parse("2026-10-26T12:00:00Z"))
        val session = MutableLmsSessionController(LmsSessionState.EXPIRED)
        val driver = RecordingLoginDriver(LmsLoginResult.Success)
        val coordinator = LmsAutoLoginCoordinator(
            RecordingCredentialStore(SavedLmsCredentials("student", "secret")), session, driver, clock,
        )
        coordinator.ensureActive(force = false)
        // Still reading three hours later: the session was alive, and the two hours run from here.
        clock.now = clock.now.plus(Duration.ofHours(3))
        coordinator.noteActivity()
        clock.now = clock.now.plus(Duration.ofMinutes(30))

        coordinator.markExpired()

        assertTrue(coordinator.signedInElsewhere.value)
        assertEquals(LmsSessionState.EXPIRED, coordinator.ensureActive(force = false))
        assertEquals(LmsSessionState.EXPIRED, coordinator.ensureActive(force = true))
        assertEquals(1, driver.attempts)

        assertEquals(LmsSessionState.ACTIVE, coordinator.ensureActive(force = true, signInHere = true))
        assertEquals(2, driver.attempts)
        assertFalse(coordinator.signedInElsewhere.value)
    }

    @Test
    fun theWaitHasNoTimeLimitAndLastsAcrossLaunches() = runTest {
        // The other place may still be in use (a video keeps its session alive), so only the student ends the wait.
        val clock = MovableClock(Instant.parse("2026-10-26T12:00:00Z"))
        val record = InMemoryLmsSessionRecord().apply { recordActivity(clock.now.minus(Duration.ofMinutes(20))) }
        val store = RecordingCredentialStore(SavedLmsCredentials("student", "secret"))
        val driver = RecordingLoginDriver(LmsLoginResult.Success)
        LmsAutoLoginCoordinator(store, MutableLmsSessionController(LmsSessionState.ACTIVE), driver, clock, record).markExpired()
        clock.now = clock.now.plus(Duration.ofHours(30))

        val relaunched = LmsAutoLoginCoordinator(store, MutableLmsSessionController(), driver, clock, record)

        assertTrue(relaunched.signedInElsewhere.value)
        assertEquals(LmsSessionState.SIGNED_OUT, relaunched.ensureActive(force = true))
        assertEquals(0, driver.attempts)
    }

    @Test
    fun aSessionEndedTwoHoursAfterTheLastReadSignsInAgainByItself() = runTest {
        val clock = MovableClock(Instant.parse("2026-10-26T12:00:00Z"))
        val session = MutableLmsSessionController(LmsSessionState.EXPIRED)
        val driver = RecordingLoginDriver(LmsLoginResult.Success)
        val coordinator = LmsAutoLoginCoordinator(
            RecordingCredentialStore(SavedLmsCredentials("student", "secret")), session, driver, clock,
        )
        coordinator.ensureActive(force = false)
        clock.now = clock.now.plus(LMS_SESSION_LIMIT).plusSeconds(60)

        coordinator.markExpired()

        assertFalse(coordinator.signedInElsewhere.value)
        assertEquals(LmsSessionState.ACTIVE, coordinator.ensureActive(force = false))
        assertEquals(2, driver.attempts)
    }

    @Test
    fun theAppsOwnSchoolSiteSignInSinceTheLastReadIsNotTakenForASignInElsewhere() = runTest {
        // Observed 2026-10-26: the school sign-in had already ended the other place, yet 수업 showed the card.
        val clock = MovableClock(Instant.parse("2026-10-26T12:00:00Z"))
        val record = InMemoryLmsSessionRecord().apply { recordActivity(clock.now) }
        val driver = RecordingLoginDriver(LmsLoginResult.Success)
        val coordinator = LmsAutoLoginCoordinator(
            RecordingCredentialStore(SavedLmsCredentials("student", "secret")),
            MutableLmsSessionController(LmsSessionState.EXPIRED),
            driver,
            clock,
            record,
        )
        clock.now = clock.now.plus(Duration.ofMinutes(10))
        coordinator.signedInThroughPortal()
        clock.now = clock.now.plus(Duration.ofMinutes(5))

        coordinator.markExpired()

        assertFalse(coordinator.signedInElsewhere.value)
        assertEquals(LmsSessionState.ACTIVE, coordinator.ensureActive(force = false))
    }

    @Test
    fun aSchoolSiteSignInBeforeTheLastReadDoesNotHideASignInElsewhere() = runTest {
        val clock = MovableClock(Instant.parse("2026-10-26T12:00:00Z"))
        val record = InMemoryLmsSessionRecord().apply {
            recordPortalSignIn(clock.now)
            recordActivity(clock.now.plus(Duration.ofMinutes(5)))
        }
        val coordinator = LmsAutoLoginCoordinator(
            RecordingCredentialStore(SavedLmsCredentials("student", "secret")),
            MutableLmsSessionController(LmsSessionState.ACTIVE),
            RecordingLoginDriver(LmsLoginResult.Success),
            clock,
            record,
        )
        clock.now = clock.now.plus(Duration.ofMinutes(20))

        coordinator.markExpired()

        assertTrue(coordinator.signedInElsewhere.value)
    }

    @Test
    fun aClockSetBackIsNotTakenForASessionEndedEarly() = runTest {
        val clock = MovableClock(Instant.parse("2026-10-26T12:00:00Z"))
        val record = InMemoryLmsSessionRecord().apply { recordActivity(clock.now.plus(Duration.ofHours(5))) }
        val coordinator = LmsAutoLoginCoordinator(
            RecordingCredentialStore(SavedLmsCredentials("student", "secret")),
            MutableLmsSessionController(LmsSessionState.ACTIVE),
            RecordingLoginDriver(LmsLoginResult.Success),
            clock,
            record,
        )

        coordinator.markExpired()

        assertFalse(coordinator.signedInElsewhere.value)
    }

    @Test
    fun withoutAnEarlierSignInAnEndedSessionIsNotTakenForASignInElsewhere() = runTest {
        val coordinator = LmsAutoLoginCoordinator(
            RecordingCredentialStore(SavedLmsCredentials("student", "secret")),
            MutableLmsSessionController(LmsSessionState.ACTIVE),
            RecordingLoginDriver(LmsLoginResult.Success),
            fixedClock(),
        )

        coordinator.markExpired()

        assertFalse(coordinator.signedInElsewhere.value)
    }

    @Test
    fun newlySavedCredentialsOrAnAllowedSchoolSignInEndTheWait() = runTest {
        val record = InMemoryLmsSessionRecord().apply { recordActivity(Instant.parse("2026-08-31T09:30:00Z")) }
        val driver = RecordingLoginDriver(LmsLoginResult.Success)
        val coordinator = LmsAutoLoginCoordinator(
            RecordingCredentialStore(SavedLmsCredentials("student", "secret")),
            MutableLmsSessionController(LmsSessionState.EXPIRED),
            driver,
            fixedClock(),
            record,
        )
        coordinator.markExpired()
        assertTrue(coordinator.signedInElsewhere.value)

        coordinator.allowSignInHere()
        assertFalse(coordinator.signedInElsewhere.value)
        assertNull(record.waitingSince())

        coordinator.markExpired()
        assertEquals(LmsSessionState.ACTIVE, coordinator.ensureActive(force = true, credentialsChanged = true))
        assertEquals(1, driver.attempts)
    }

    @Test
    fun aSignInThatEndedTheOneElsewhereSaysSo() = runTest {
        val record = InMemoryLmsSessionRecord()
        val coordinator = LmsAutoLoginCoordinator(
            RecordingCredentialStore(SavedLmsCredentials("student", "secret")),
            MutableLmsSessionController(LmsSessionState.EXPIRED),
            RecordingLoginDriver(LmsLoginResult.SuccessEndingElsewhere),
            fixedClock(),
            record,
        )
        val notices = mutableListOf<String>()
        backgroundScope.launch { coordinator.notices.toList(notices) }
        runCurrent()

        assertEquals(LmsSessionState.ACTIVE, coordinator.ensureActive(force = false))
        runCurrent()

        assertEquals(listOf(LmsUserMessages.SIGNED_OUT_ELSEWHERE), notices)
        assertEquals(Instant.parse("2026-08-31T10:00:00Z"), record.lastActivity())
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
                if (attempts++ == 0) LmsLoginResult.Failure("안전하지 않은 페이지라서 열지 않았어요")
                else LmsLoginResult.Success
        }
        val coordinator = LmsAutoLoginCoordinator(store, session, driver, fixedClock())

        coordinator.ensureActive(force = false)
        assertEquals("안전하지 않은 페이지라서 열지 않았어요", coordinator.errorMessage.value)
        coordinator.ensureActive(force = true)

        assertEquals(null, coordinator.errorMessage.value)
        assertEquals(LmsSessionState.ACTIVE, session.state.value)
    }

    private fun fixedClock(): Clock = Clock.fixed(Instant.parse("2026-08-31T10:00:00Z"), ZoneOffset.UTC)
}

private class MovableClock(var now: Instant) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = this
    override fun instant(): Instant = now
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
