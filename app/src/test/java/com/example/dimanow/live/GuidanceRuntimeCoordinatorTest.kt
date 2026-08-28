package com.example.dimanow.live

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class GuidanceRuntimeCoordinatorTest {
    @Test
    fun `screen on and user present burst produces one refresh`() = runTest {
        val refreshedSnapshots = mutableListOf<Int>()
        val coordinator = GuidanceRuntimeCoordinator<Int>(
            scope = backgroundScope,
            coalesceMillis = 250,
            refresh = { refreshedSnapshots += it },
        )

        coordinator.update(7)
        coordinator.requestRefresh()
        advanceTimeBy(100)
        coordinator.requestRefresh()
        advanceTimeBy(100)
        coordinator.requestRefresh()
        advanceTimeBy(251)
        runCurrent()

        assertEquals(listOf(7), refreshedSnapshots)
    }
}
