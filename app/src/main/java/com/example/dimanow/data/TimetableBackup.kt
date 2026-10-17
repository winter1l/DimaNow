package com.example.dimanow.data

import com.example.dimanow.domain.CampusZoneId
import com.example.dimanow.domain.Course
import com.example.dimanow.domain.CourseOverride
import com.example.dimanow.domain.CourseOverrideKind
import com.example.dimanow.domain.GuidancePause
import com.example.dimanow.domain.TermSchedule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.example.dimanow.sync.TimetableBackup

object TimetableBackupCodec {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }
    private const val MAX_BYTES = 256 * 1024

    fun encode(schedule: TermSchedule): ByteArray {
        val courses = schedule.courses.sortedWith(compareBy({ it.weekday }, { it.start }, { it.name }))
        val indexById = courses.withIndex().associate { (index, course) -> course.id to index }
        val backup = TimetableBackup(
            format = TimetableBackup.FORMAT,
            termStart = schedule.termStart.toString(),
            termEnd = schedule.termEnd.toString(),
            courses = courses.map {
                TimetableBackup.BackupCourse(it.weekday.name, it.start.toString(), it.end.toString(), it.name, it.room, it.professor, it.zone.name, it.isOnline)
            },
            noClassDates = schedule.noClassDates.sorted().map(LocalDate::toString),
            pause = schedule.guidancePause?.let { TimetableBackup.BackupPause(it.startDate.toString(), it.endDateInclusive.toString()) },
            overrides = schedule.courseOverrides.mapNotNull { override ->
                val index = indexById[override.courseId] ?: return@mapNotNull null
                TimetableBackup.BackupOverride(index, override.date.toString(), override.kind.name,
                    override.start?.toString(), override.end?.toString(), override.room)
            },
        )
        return json.encodeToString(backup).toByteArray()
    }

    /** Parses and validates a file; throws [IllegalArgumentException] with a Korean reason when it is unusable. */
    fun decode(bytes: ByteArray): TimetableBackup {
        require(bytes.size in 1..MAX_BYTES) { "시간표 파일 크기가 올바르지 않아요." }
        val backup = runCatching { json.decodeFromString<TimetableBackup>(bytes.decodeToString()) }
            .getOrElse { throw IllegalArgumentException("DIMA Now 시간표 파일이 아니에요.") }
        require(backup.format == TimetableBackup.FORMAT && backup.schemaVersion == 1) { "지원하지 않는 시간표 파일이에요." }
        val start = date(backup.termStart)
        require(!date(backup.termEnd).isBefore(start)) { "학기 기간이 올바르지 않아요." }
        require(backup.courses.size <= 60) { "과목이 너무 많아요." }
        backup.courses.forEach { course ->
            runCatching { DayOfWeek.valueOf(course.weekday); CampusZoneId.valueOf(course.zone) }
                .getOrElse { throw IllegalArgumentException("과목 정보가 올바르지 않아요.") }
            require(time(course.start) < time(course.end)) { "과목 시간이 올바르지 않아요." }
            require(course.name.isNotBlank() && course.name.length <= 100 && course.room.length <= 100 && course.professor.length <= 100) {
                "과목 정보가 올바르지 않아요."
            }
        }
        backup.noClassDates.forEach(::date)
        backup.pause?.let { require(!date(it.end).isBefore(date(it.start))) { "휴강 모드 기간이 올바르지 않아요." } }
        backup.overrides.forEach { override ->
            require(override.courseIndex in backup.courses.indices) { "수업 변경 정보가 올바르지 않아요." }
            date(override.date)
            runCatching { CourseOverrideKind.valueOf(override.kind) }.getOrElse { throw IllegalArgumentException("수업 변경 정보가 올바르지 않아요.") }
        }
        return backup
    }

    /** Replaces the stored timetable with [backup]. Settings, caches and LMS data are untouched. */
    suspend fun restore(repository: CampusDataRepository, backup: TimetableBackup) {
        val current = repository.schedule.first()
        current.courses.forEach { repository.deleteCourse(it.id) }
        current.noClassDates.forEach { repository.removeNoClassDate(it) }
        repository.clearGuidancePause()
        repository.setTerm(date(backup.termStart), date(backup.termEnd))
        val ids = backup.courses.map { course ->
            repository.saveCourse(
                Course(
                    weekday = DayOfWeek.valueOf(course.weekday),
                    start = time(course.start),
                    end = time(course.end),
                    name = course.name.trim(),
                    room = course.room.trim(),
                    professor = course.professor.trim(),
                    zone = CampusZoneId.valueOf(course.zone),
                    isOnline = course.isOnline,
                ),
            )
        }
        backup.noClassDates.forEach { repository.addNoClassDate(date(it)) }
        backup.pause?.let { repository.setGuidancePause(GuidancePause(date(it.start), date(it.end))) }
        backup.overrides.forEach { override ->
            // A change outside the restored term or on another weekday is skipped instead of failing the restore.
            runCatching {
                repository.setCourseOverride(
                    CourseOverride(ids[override.courseIndex], date(override.date), CourseOverrideKind.valueOf(override.kind),
                        override.start?.let(::time), override.end?.let(::time), override.room),
                )
            }
        }
    }

    private fun date(value: String): LocalDate = runCatching { LocalDate.parse(value) }
        .getOrElse { throw IllegalArgumentException("날짜가 올바르지 않아요.") }

    private fun time(value: String): LocalTime = runCatching { LocalTime.parse(value) }
        .getOrElse { throw IllegalArgumentException("시간이 올바르지 않아요.") }
}
