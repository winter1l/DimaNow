package com.example.dimanow.sync

import kotlinx.serialization.Serializable

/**
 * Portable timetable file (D-099 clause 9): term, courses, no-class dates, pause and dated changes. It lets a user
 * move to a new phone, or to the new package after the D-099 applicationId change, without retyping. LMS data and
 * account values are never included.
 */
@Serializable
data class TimetableBackup(
    val format: String,
    val schemaVersion: Int = 1,
    val termStart: String,
    val termEnd: String,
    val courses: List<BackupCourse>,
    val noClassDates: List<String> = emptyList(),
    val pause: BackupPause? = null,
    val overrides: List<BackupOverride> = emptyList(),
) {
    @Serializable
    data class BackupCourse(
        val weekday: String,
        val start: String,
        val end: String,
        val name: String,
        val room: String,
        val professor: String,
        val zone: String,
        val isOnline: Boolean = false,
    )

    @Serializable
    data class BackupPause(val start: String, val end: String)

    /** [courseIndex] points into [courses]; database IDs are not portable. */
    @Serializable
    data class BackupOverride(
        val courseIndex: Int,
        val date: String,
        val kind: String,
        val start: String? = null,
        val end: String? = null,
        val room: String? = null,
    )

    companion object {
        const val FORMAT = "dima-now-timetable"
    }
}
