package com.example.dimanow.domain

import java.time.LocalDate
import java.time.LocalTime

enum class CourseOverrideKind { CANCELLED, ONLINE, CHANGED }

data class CourseOverride(
    val courseId: Long,
    val date: LocalDate,
    val kind: CourseOverrideKind,
    val start: LocalTime? = null,
    val end: LocalTime? = null,
    val room: String? = null,
)
