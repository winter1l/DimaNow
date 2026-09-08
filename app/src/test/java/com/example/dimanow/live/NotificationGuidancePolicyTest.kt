package com.example.dimanow.live

import org.junit.Assert.assertEquals
import org.junit.Test
import com.example.dimanow.domain.ClassContent
import com.example.dimanow.domain.GuidancePhase
import com.example.dimanow.domain.GuidanceSnapshot
import com.example.dimanow.domain.ShuttleLine

class NotificationGuidancePolicyTest {
    @Test
    fun `every guidance category is live by default and can be changed independently`() {
        val changed = NotificationGuidancePolicy().withMode(
            GuidanceKind.BUS_4402,
            NotificationGuidanceMode.OFF,
        )

        assertEquals(NotificationGuidanceMode.LIVE_UPDATE, changed.classGuidance)
        assertEquals(NotificationGuidanceMode.LIVE_UPDATE, changed.campusShuttle)
        assertEquals(NotificationGuidanceMode.OFF, changed.bus4402)
    }

    @Test
    fun `delivery mode follows the active guidance category`() {
        val policy = NotificationGuidancePolicy(
            classGuidance = NotificationGuidanceMode.OFF,
            campusShuttle = NotificationGuidanceMode.STANDARD,
            bus4402 = NotificationGuidanceMode.LIVE_UPDATE,
        )
        val classSnapshot = GuidanceSnapshot(
            ClassContent("수업", "시작까지 8분"), emptyList(), GuidancePhase.BEFORE_CLASS,
        )
        val campusSnapshot = GuidanceSnapshot(
            null, listOf(ShuttleLine("본관  8분", "엔터관행", 8)), GuidancePhase.RETURN,
        )
        val busSnapshot = campusSnapshot.copy(kind = GuidanceKind.BUS_4402)

        assertEquals(NotificationGuidanceMode.OFF, policy.modeFor(classSnapshot))
        assertEquals(NotificationGuidanceMode.STANDARD, policy.modeFor(campusSnapshot))
        assertEquals(NotificationGuidanceMode.LIVE_UPDATE, policy.modeFor(busSnapshot))
    }
}
