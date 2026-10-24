package com.example.dimanow.lms

import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LmsLoginBridgeTest {
    private val credentials = SavedLmsCredentials("student", "secret")

    @Test
    fun `an approved takeover applies to the next login only`() = runTest {
        val bridge = LmsLoginBridge()
        bridge.approveSessionTakeoverOnce()

        assertTrue(allowsTakeover(bridge))
        assertFalse(allowsTakeover(bridge))
    }

    @Test
    fun `an approval the login did not use never reaches a later automatic login`() = runTest {
        val bridge = LmsLoginBridge()
        bridge.approveSessionTakeoverOnce()
        // The approved login returned early (the session was active again), so nothing consumed the approval.
        bridge.clearSessionTakeoverApproval()

        assertFalse(allowsTakeover(bridge))
    }

    private suspend fun kotlinx.coroutines.test.TestScope.allowsTakeover(bridge: LmsLoginBridge): Boolean {
        val login = async { bridge.authenticate(credentials) }
        runCurrent()
        val allowed = requireNotNull(bridge.request.value).allowSessionTakeover
        bridge.complete(LmsLoginResult.Success)
        assertEquals(LmsLoginResult.Success, login.await())
        return allowed
    }
}
