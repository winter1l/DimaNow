package com.example.dimanow.sync

import kotlinx.serialization.Serializable

@Serializable
data class MealPayload(
    val schemaVersion: Int = 1,
    val weekStart: String,
    val weekEnd: String,
    val days: List<MealDayPayload>,
)

@Serializable
data class MealDayPayload(
    val date: String,
    val menuLines: List<String>,
    val hours: String,
    val sourceUrl: String,
    val sourceImageUrl: String,
)
