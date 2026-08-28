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

class RoomCampusDataRepository(
    private val database: DimaDatabase,
) : CampusDataRepository {
    private val dao = database.scheduleDao()

    override val schedule: Flow<TermSchedule> = combine(
        dao.observeTerm().filterNotNull(),
        dao.observeCourses(),
        dao.observeNoClassDates(),
        dao.observeGuidancePause(),
    ) { term, courses, noClassDates, pause ->
        TermSchedule(
            termStart = term.startDate,
            termEnd = term.endDate,
            courses = courses.map { it.toDomain() },
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

    override suspend fun deleteCourse(id: Long) = dao.deleteCourse(id)

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
