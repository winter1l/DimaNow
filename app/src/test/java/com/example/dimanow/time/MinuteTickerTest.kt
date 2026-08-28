package com.example.dimanow.time

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class MinuteTickerTest {
    @Test
    fun `system change restarts the wait at the new minute boundary`() = runTest {
        val clock = MutableClock("2026-08-28T10:00:30+09:00[Asia/Seoul]")
        val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val values = mutableListOf<ZonedDateTime>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            MinuteTicker(clock).ticksWithChanges(changes).take(3).toList(values)
        }

        clock.set("2026-08-28T10:05:10+09:00[Asia/Seoul]")
        changes.emit(Unit)
        clock.set("2026-08-28T10:06:00+09:00[Asia/Seoul]")
        advanceTimeBy(50_001)

        assertEquals(
            listOf("10:00:30", "10:05:10", "10:06:00"),
            values.map { it.format(DateTimeFormatter.ofPattern("HH:mm:ss")) },
        )
        job.cancel()
    }
}

private class MutableClock(initial: String) : Clock() {
    private var current = ZonedDateTime.parse(initial)

    fun set(value: String) {
        current = ZonedDateTime.parse(value)
    }

    override fun getZone(): ZoneId = current.zone

    override fun withZone(zone: ZoneId): Clock = this

    override fun instant(): Instant = current.toInstant()
}
