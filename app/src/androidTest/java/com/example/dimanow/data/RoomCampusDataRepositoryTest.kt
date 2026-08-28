package com.example.dimanow.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.dimanow.domain.CampusZoneId
import java.time.LocalDate
import com.example.dimanow.domain.GuidancePause
import com.example.dimanow.domain.ZoneGeometry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class RoomCampusDataRepositoryTest {
    @Test
    fun guidancePauseRangePersistsWithoutReplacingExistingNoClassDates() = runTest {
        repository.ensureSeeded()
        val legacyDate = LocalDate.of(2026, 9, 7)
        repository.addNoClassDate(legacyDate)

        repository.setGuidancePause(GuidancePause(LocalDate.of(2026, 8, 27), LocalDate.of(2026, 8, 30)))

        val schedule = repository.schedule.first()
        assertEquals(GuidancePause(LocalDate.of(2026, 8, 27), LocalDate.of(2026, 8, 30)), schedule.guidancePause)
        assertEquals(setOf(legacyDate), schedule.noClassDates)
    }

    @Test
    fun guidancePauseUntilDisabledRoundTripsThroughTheRepository() = runTest {
        repository.ensureSeeded()
        repository.setGuidancePause(GuidancePause.untilDisabled(LocalDate.of(2026, 8, 27)))

        val restored = repository.schedule.first().guidancePause

        assertEquals(LocalDate.of(2026, 8, 27), restored?.startDate)
        assertEquals(true, restored?.isUntilDisabled)
    }

    private lateinit var database: DimaDatabase
    private lateinit var repository: CampusDataRepository

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, DimaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomCampusDataRepository(database)
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun firstLaunchSeedsTheApprovedScheduleOnlyOnce() = runTest {
        repository.ensureSeeded()
        repository.ensureSeeded()

        val schedule = repository.schedule.first()
        assertEquals(LocalDate.of(2026, 8, 24), schedule.termStart)
        assertEquals(LocalDate.of(2026, 12, 18), schedule.termEnd)
        assertEquals(6, schedule.courses.size)
        assertEquals("조명기초및실습", schedule.courses.first().name)
    }

    @Test
    fun firstLaunchSeedsTheThreeApprovedFixedCampusPolygons() = runTest {
        repository.ensureSeeded()

        val zones = repository.zones.first().associateBy { it.id }
        assertEquals(setOf(CampusZoneId.YEIN, CampusZoneId.MAIN, CampusZoneId.ONE_ROOM), zones.keys)
        assertEquals(37.0609666, zones.getValue(CampusZoneId.YEIN).center.latitude, 0.0000001)
        assertEquals(127.3535671, zones.getValue(CampusZoneId.YEIN).center.longitude, 0.0000001)
        assertEquals(37.0594160, zones.getValue(CampusZoneId.MAIN).center.latitude, 0.0000001)
        assertEquals(127.3585957, zones.getValue(CampusZoneId.MAIN).center.longitude, 0.0000001)
        assertEquals(37.0558538, zones.getValue(CampusZoneId.ONE_ROOM).center.latitude, 0.0000001)
        assertEquals(127.3627537, zones.getValue(CampusZoneId.ONE_ROOM).center.longitude, 0.0000001)
        assertEquals(listOf(250, 250, 250), zones.values.map { it.radiusMeters }.sorted())
        assertEquals(listOf(5, 6, 9), zones.values.map { (it.geometry as ZoneGeometry.Polygon).vertices.size }.sorted())
        assertEquals(
            setOf("CAMPUS_ZONES_V2_USER_2026_08_27"),
            zones.values.map { (it.geometry as ZoneGeometry.Polygon).version }.toSet(),
        )
    }

    @Test
    fun bundledZoneInstallReplacesOnlyZoneRowsAndPreservesOtherCampusData() = runTest {
        repository.ensureSeeded()
        val pause = GuidancePause(LocalDate.of(2026, 8, 27), LocalDate.of(2026, 8, 30))
        repository.setGuidancePause(pause)
        database.scheduleDao().putZone(
            CampusZoneEntity(
                id = CampusZoneId.MAIN.name,
                latitude = 37.0000000,
                longitude = 127.0000000,
                radiusMeters = 75,
            ),
        )

        repository.installBundledCampusZones()

        val zones = repository.zones.first().associateBy { it.id }
        assertEquals(37.0594160, zones.getValue(CampusZoneId.MAIN).center.latitude, 0.0000001)
        assertEquals("CAMPUS_ZONES_V2_USER_2026_08_27", (zones.getValue(CampusZoneId.MAIN).geometry as ZoneGeometry.Polygon).version)
        assertEquals(6, repository.schedule.first().courses.size)
        assertEquals(pause, repository.schedule.first().guidancePause)
    }

}
