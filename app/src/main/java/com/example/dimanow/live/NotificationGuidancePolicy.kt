package com.example.dimanow.live

import com.example.dimanow.domain.GuidanceSnapshot

enum class NotificationGuidanceMode {
    LIVE_UPDATE,
    STANDARD,
    OFF,
}

typealias GuidanceKind = com.example.dimanow.domain.GuidanceKind

data class NotificationGuidancePolicy(
    val classGuidance: NotificationGuidanceMode = NotificationGuidanceMode.LIVE_UPDATE,
    val campusShuttle: NotificationGuidanceMode = NotificationGuidanceMode.LIVE_UPDATE,
    val bus4402: NotificationGuidanceMode = NotificationGuidanceMode.LIVE_UPDATE,
) {
    fun modeFor(kind: GuidanceKind): NotificationGuidanceMode = when (kind) {
        GuidanceKind.CLASS -> classGuidance
        GuidanceKind.CAMPUS_SHUTTLE -> campusShuttle
        GuidanceKind.BUS_4402 -> bus4402
    }

    fun modeFor(snapshot: GuidanceSnapshot): NotificationGuidanceMode {
        val kind = snapshot.kind ?: when {
            snapshot.classContent != null -> GuidanceKind.CLASS
            snapshot.shuttleLines.isNotEmpty() -> GuidanceKind.CAMPUS_SHUTTLE
            else -> GuidanceKind.CLASS
        }
        return modeFor(kind)
    }

    fun withMode(kind: GuidanceKind, mode: NotificationGuidanceMode): NotificationGuidancePolicy = when (kind) {
        GuidanceKind.CLASS -> copy(classGuidance = mode)
        GuidanceKind.CAMPUS_SHUTTLE -> copy(campusShuttle = mode)
        GuidanceKind.BUS_4402 -> copy(bus4402 = mode)
    }
}

data class LiveDeliveryPlan(
    val shouldPost: Boolean,
    val requestPromotion: Boolean,
    val startMinuteUpdater: Boolean,
)

object LiveDeliveryPlanner {
    fun plan(mode: NotificationGuidanceMode, requiresMinuteUpdates: Boolean): LiveDeliveryPlan = when (mode) {
        NotificationGuidanceMode.LIVE_UPDATE -> LiveDeliveryPlan(true, true, requiresMinuteUpdates)
        NotificationGuidanceMode.STANDARD -> LiveDeliveryPlan(true, false, false)
        NotificationGuidanceMode.OFF -> LiveDeliveryPlan(false, false, false)
    }
}
