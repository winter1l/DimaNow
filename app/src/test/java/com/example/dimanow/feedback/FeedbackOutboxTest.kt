package com.example.dimanow.feedback

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FeedbackOutboxTest {
    private val test = TestScope(StandardTestDispatcher())
    private val outbox = FeedbackOutbox(CoroutineScope(SupervisorJob() + StandardTestDispatcher(test.testScheduler)))
    private fun shot() = FeedbackScreenshot(ByteArray(1), 1, 1)

    @Test
    fun `a recreated form re-attaches to the send in flight and its result`() {
        val report = outbox.open(null)
        val answer = CompletableDeferred<FeedbackResult>()
        var sends = 0
        assertTrue(outbox.send(report) { sends++; answer.await() })
        test.advanceUntilIdle()
        assertEquals(FeedbackOutbox.State.Sending, outbox.state.value)

        // The activity is recreated: the form comes back with its saved id while the POST is still out.
        assertEquals(report, outbox.open(report))
        assertEquals(FeedbackOutbox.State.Sending, outbox.state.value)
        assertFalse("no second send while one is in flight", outbox.send(report) { sends++; FeedbackResult.Failed })

        val posted = FeedbackResult.Posted(42, "https://github.com/winter1l/DimaNow/issues/42")
        answer.complete(posted)
        test.advanceUntilIdle()
        assertEquals(FeedbackOutbox.State.Done(posted), outbox.state.value)
        assertEquals(1, sends)
    }

    @Test
    fun `screenshots survive a recreation and a new report starts empty`() {
        val report = outbox.open(null)
        val first = shot()
        outbox.addScreenshots(report, listOf(first, null, shot(), shot())) { it }
        test.advanceUntilIdle()
        // Three slots: the unreadable one is left out with a note, the fourth pick does not fit.
        assertEquals(2, outbox.screenshots.value.size)
        assertTrue(outbox.skippedUnreadable.value)

        assertEquals(report, outbox.open(report))
        assertEquals(2, outbox.screenshots.value.size)
        outbox.removeScreenshot(report, first)
        assertEquals(1, outbox.screenshots.value.size)

        val next = outbox.open(null)
        assertNotEquals(report, next)
        assertEquals(emptyList<FeedbackScreenshot>(), outbox.screenshots.value)
        assertFalse(outbox.skippedUnreadable.value)
        assertEquals(FeedbackOutbox.State.Idle, outbox.state.value)
    }

    @Test
    fun `a closed report ignores its late answer`() {
        val report = outbox.open(null)
        val answer = CompletableDeferred<FeedbackResult>()
        outbox.send(report) { answer.await() }
        test.advanceUntilIdle()
        outbox.close(report)
        val next = outbox.open(null)
        answer.complete(FeedbackResult.Posted(1, "https://github.com/winter1l/DimaNow/issues/1"))
        test.advanceUntilIdle()
        assertEquals(FeedbackOutbox.State.Idle, outbox.state.value)
        // The old id no longer sends, the new report does.
        assertFalse(outbox.send(report) { FeedbackResult.Failed })
        assertTrue(outbox.send(next) { FeedbackResult.TooOften })
        test.advanceUntilIdle()
        assertEquals(FeedbackOutbox.State.Done(FeedbackResult.TooOften), outbox.state.value)
    }

    @Test
    fun `an id from an earlier process starts afresh under that id`() {
        assertEquals("from-saved-state", outbox.open("from-saved-state"))
        assertEquals(FeedbackOutbox.State.Idle, outbox.state.value)
        assertTrue(outbox.send("from-saved-state") { error("network stack threw") })
        test.advanceUntilIdle()
        assertEquals(FeedbackOutbox.State.Done(FeedbackResult.Failed), outbox.state.value)
    }
}
