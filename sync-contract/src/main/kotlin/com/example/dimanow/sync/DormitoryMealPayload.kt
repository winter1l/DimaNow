package com.example.dimanow.sync

import kotlinx.serialization.Serializable

@Serializable
data class DormitoryMealPayload(
    val schemaVersion: Int = 1,
    val weekStart: String,
    val weekEnd: String,
    val sourceImageUrl: String,
    val days: List<DormitoryMealDayPayload>,
)

@Serializable
data class DormitoryMealDayPayload(
    val date: String,
    val sections: List<DormitoryMealSectionPayload>,
)

@Serializable
data class DormitoryMealSectionPayload(
    val name: String,
    val hours: String? = null,
    val menuLines: List<String>,
)

@Serializable
data class DormitoryMealSubmissionStatus(
    val submissionId: String,
    val state: String,
    val message: String? = null,
    val updatedAt: String,
)
