package com.example.dimanow.data

import androidx.room.withTransaction
import com.example.dimanow.domain.DefaultSchedule
import com.example.dimanow.domain.DefaultCampusZones
import com.example.dimanow.domain.CampusZone
import com.example.dimanow.domain.Course
import com.example.dimanow.domain.GuidancePause
import com.example.dimanow.domain.TermSchedule
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first

class RoomCampusDataRepository(
    private val database: DimaDatabase,
) : CampusDataRepository {
    private val dao = database.scheduleDao()

    override val schedule: Flow<TermSchedule> = combine(
        dao.observeTerm().filterNotNull(),
        dao.observeCourses(),
        dao.observeNoClassDates(),
        dao.observeGuidancePause(),
        dao.observeCourseOverrides(),
    ) { term, courses, noClassDates, pause, overrides ->
        TermSchedule(
            termStart = term.startDate,
            termEnd = term.endDate,
            courses = courses.map { it.toDomain() },
            courseOverrides = overrides.map { it.toDomain() },
            noClassDates = noClassDates.map { LocalDate.ofEpochDay(it.epochDay) }.toSet(),
            guidancePause = pause?.let {
                GuidancePause(
                    startDate = LocalDate.ofEpochDay(it.startEpochDay),
                    endDateInclusive = LocalDate.ofEpochDay(it.endEpochDayInclusive),
                )
            },
        )
    }

    override val zones: Flow<List<CampusZone>> = dao.observeZones().map { zones -> zones.map { it.toDomain() } }

    override suspend fun ensureSeeded() {
        database.withTransaction {
            if (dao.courseCount() == 0) {
                val seed = DefaultSchedule.create()
                dao.putTerm(
                    TermSettingsEntity(
                        startEpochDay = seed.termStart.toEpochDay(),
                        endEpochDay = seed.termEnd.toEpochDay(),
                    ),
                )
                dao.insertCourses(seed.courses.map(CourseEntity::fromDomain))
            }
            val savedZoneIds = dao.campusZoneIds().toSet()
            DefaultCampusZones.all
                .filterNot { it.id.name in savedZoneIds }
                .forEach { zone ->
                    dao.putZone(
                        CampusZoneEntity.fromDomain(zone),
                    )
                }
        }
    }


    override suspend fun saveCourse(course: Course): Long = dao.putCourse(CourseEntity.fromDomain(course))

    override suspend fun deleteCourse(id: Long) = database.withTransaction {
        dao.removeCourseOverrides(id)
        dao.deleteCourse(id)
    }

    override suspend fun setCourseOverride(override: com.example.dimanow.domain.CourseOverride) {
        database.withTransaction {
            val term = dao.currentTerm() ?: error("학기를 찾을 수 없습니다.")
            val course = dao.courseById(override.courseId)?.toDomain() ?: error("수업을 찾을 수 없습니다.")
            require(override.date in term.startDate..term.endDate && override.date.dayOfWeek == course.weekday) { "학기 중 해당 수업 요일을 선택해 주세요." }
            val start = override.start ?: course.start
            val end = override.end ?: course.end
            require(start < end) { "종료 시각은 시작 시각보다 늦어야 합니다." }
            require(override.room == null || override.room.isNotBlank() && override.room.length <= 100) { "강의실을 확인해 주세요." }
            dao.putCourseOverride(CourseOverrideEntity(override.courseId, override.date.toEpochDay(), override.kind.name,
                override.start?.let { it.hour * 60 + it.minute }, override.end?.let { it.hour * 60 + it.minute }, override.room?.trim()))
        }
    }

    override suspend fun removeCourseOverride(courseId: Long, date: LocalDate) = dao.removeCourseOverride(courseId, date.toEpochDay())

    override suspend fun setTerm(start: LocalDate, end: LocalDate) {
        require(!end.isBefore(start))
        dao.putTerm(TermSettingsEntity(startEpochDay = start.toEpochDay(), endEpochDay = end.toEpochDay()))
    }

    override suspend fun addNoClassDate(date: LocalDate) {
        dao.addNoClassDate(NoClassDateEntity(date.toEpochDay()))
    }

    override suspend fun removeNoClassDate(date: LocalDate) {
        dao.removeNoClassDate(date.toEpochDay())
    }

    override suspend fun setGuidancePause(pause: GuidancePause) {
        dao.putGuidancePause(
            GuidancePauseEntity(
                startEpochDay = pause.startDate.toEpochDay(),
                endEpochDayInclusive = pause.endDateInclusive.toEpochDay(),
            ),
        )
    }

    override suspend fun clearGuidancePause() = dao.clearGuidancePause()

    override suspend fun installBundledCampusZones() {
        database.withTransaction {
            DefaultCampusZones.all.forEach { dao.putZone(CampusZoneEntity.fromDomain(it)) }
        }
    }

}
