package com.example.dimanow.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.dimanow.domain.CampusZoneId
import com.example.dimanow.domain.CampusZone
import com.example.dimanow.domain.Course
import com.example.dimanow.domain.GeoPoint
import com.example.dimanow.domain.ZoneGeometry
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import com.example.dimanow.domain.CampusNotice
import com.example.dimanow.domain.ShuttleDeparture
import com.example.dimanow.domain.MealDay
import com.example.dimanow.domain.MealValidationState
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "courses")
data class CourseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val weekday: Int,
    val startMinute: Int,
    val endMinute: Int,
    val name: String,
    val room: String,
    val professor: String,
    val zone: String,
) {
    fun toDomain() = Course(
        weekday = DayOfWeek.of(weekday),
        start = LocalTime.of(startMinute / 60, startMinute % 60),
        end = LocalTime.of(endMinute / 60, endMinute % 60),
        name = name,
        room = room,
        professor = professor,
        zone = CampusZoneId.valueOf(zone),
        id = id,
    )

    companion object {
        fun fromDomain(course: Course) = CourseEntity(
            id = course.id,
            weekday = course.weekday.value,
            startMinute = course.start.hour * 60 + course.start.minute,
            endMinute = course.end.hour * 60 + course.end.minute,
            name = course.name,
            room = course.room,
            professor = course.professor,
            zone = course.zone.name,
        )
    }
}

@Entity(tableName = "no_class_dates")
data class NoClassDateEntity(
    @PrimaryKey val epochDay: Long,
)

@Entity(tableName = "guidance_pause")
data class GuidancePauseEntity(
    @PrimaryKey val id: Int = 1,
    val startEpochDay: Long,
    val endEpochDayInclusive: Long,
)

@Entity(tableName = "campus_zones")
data class CampusZoneEntity(
    @PrimaryKey val id: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int,
    @ColumnInfo(defaultValue = "'CIRCLE'") val geometryType: String = "CIRCLE",
    val geometryVersion: String? = null,
    val polygonVertices: String? = null,
    @ColumnInfo(defaultValue = "250") val wakeRadiusMeters: Int = radiusMeters,
) {
    fun toDomain(): CampusZone {
        val geometry = if (geometryType == "POLYGON" && !polygonVertices.isNullOrBlank()) {
            ZoneGeometry.Polygon(
                version = geometryVersion ?: "UNKNOWN",
                vertices = polygonVertices.split(';').mapNotNull { encoded ->
                    val values = encoded.split(',')
                    if (values.size != 2) null else {
                        val lat = values[0].toDoubleOrNull()
                        val lon = values[1].toDoubleOrNull()
                        if (lat == null || lon == null) null else GeoPoint(lat, lon)
                    }
                },
                wakeRadiusMeters = wakeRadiusMeters,
            )
        } else {
            ZoneGeometry.Circle(radiusMeters)
        }
        return CampusZone(
            id = CampusZoneId.valueOf(id),
            center = GeoPoint(latitude, longitude),
            radiusMeters = radiusMeters,
            geometry = geometry,
        )
    }

    companion object {
        fun fromDomain(zone: CampusZone): CampusZoneEntity = when (val geometry = zone.geometry) {
            is ZoneGeometry.Circle -> CampusZoneEntity(
                id = zone.id.name,
                latitude = zone.center.latitude,
                longitude = zone.center.longitude,
                radiusMeters = zone.radiusMeters,
                wakeRadiusMeters = geometry.radiusMeters,
            )
            is ZoneGeometry.Polygon -> CampusZoneEntity(
                id = zone.id.name,
                latitude = zone.center.latitude,
                longitude = zone.center.longitude,
                radiusMeters = zone.radiusMeters,
                geometryType = "POLYGON",
                geometryVersion = geometry.version,
                polygonVertices = geometry.vertices.joinToString(";") { "${it.latitude},${it.longitude}" },
                wakeRadiusMeters = geometry.wakeRadiusMeters,
            )
        }
    }
}

@Entity(tableName = "shuttle_departures")
data class ShuttleDepartureEntity(
    @PrimaryKey val key: String,
    val sourceRouteId: String,
    val sourceStopId: String,
    val direction: String,
    val serviceDay: Int,
    val timeMinute: Int,
    val originZone: String,
    val destinationZone: String?,
    val arrivalMinute: Int?,
) {
    fun toDomain() = ShuttleDeparture(
        sourceRouteId = sourceRouteId,
        sourceStopId = sourceStopId,
        direction = direction,
        serviceDay = DayOfWeek.of(serviceDay),
        time = LocalTime.of(timeMinute / 60, timeMinute % 60),
        originZone = CampusZoneId.valueOf(originZone),
        destinationZone = destinationZone?.let(CampusZoneId::valueOf),
        arrivalTime = arrivalMinute?.let { LocalTime.of(it / 60, it % 60) },
    )

    companion object {
        fun fromDomain(value: ShuttleDeparture): ShuttleDepartureEntity {
            val timeMinute = value.time.hour * 60 + value.time.minute
            return ShuttleDepartureEntity(
                key = "${value.sourceRouteId}|${value.sourceStopId}|${value.serviceDay.value}|$timeMinute|${value.direction}",
                sourceRouteId = value.sourceRouteId,
                sourceStopId = value.sourceStopId,
                direction = value.direction,
                serviceDay = value.serviceDay.value,
                timeMinute = timeMinute,
                originZone = value.originZone.name,
                destinationZone = value.destinationZone?.name,
                arrivalMinute = value.arrivalTime?.let { it.hour * 60 + it.minute },
            )
        }
    }
}

@Entity(tableName = "source_status")
data class SourceStatusEntity(
    @PrimaryKey val source: String,
    val lastSuccessEpochMillis: Long?,
    val lastAttemptEpochMillis: Long,
    val error: String?,
    val sourceUrl: String,
    val noticeUrl: String? = null,
    val candidateImageUrl: String? = null,
    val hours: String? = null,
)

@Entity(tableName = "meal_days")
data class MealDayEntity(
    @PrimaryKey val epochDay: Long,
    val menuText: String,
    val hours: String,
    val sourceUrl: String,
    val sourceImageUrl: String,
    val validationState: String,
) {
    fun toDomain() = MealDay(
        date = LocalDate.ofEpochDay(epochDay),
        menuLines = menuText.lineSequence().filter(String::isNotBlank).toList(),
        hours = hours,
        sourceUrl = sourceUrl,
        sourceImageUrl = sourceImageUrl,
        validationState = MealValidationState.valueOf(validationState),
    )
}

@Entity(tableName = "notices")
data class NoticeEntity(
    @PrimaryKey val id: String,
    val title: String,
    val url: String,
    val dateEpochDay: Long,
    val isPinned: Boolean,
    val orderIndex: Int,
) {
    fun toDomain() = CampusNotice(
        id = id,
        title = title,
        url = url,
        date = LocalDate.ofEpochDay(dateEpochDay),
        isPinned = isPinned,
    )

    companion object {
        fun fromDomain(notice: CampusNotice, orderIndex: Int) = NoticeEntity(
            id = notice.id,
            title = notice.title,
            url = notice.url,
            dateEpochDay = notice.date.toEpochDay(),
            isPinned = notice.isPinned,
            orderIndex = orderIndex,
        )
    }
}

@Entity(tableName = "term_settings")
data class TermSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val startEpochDay: Long,
    val endEpochDay: Long,
) {
    val startDate: LocalDate get() = LocalDate.ofEpochDay(startEpochDay)
    val endDate: LocalDate get() = LocalDate.ofEpochDay(endEpochDay)
}

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM courses ORDER BY weekday, startMinute")
    fun observeCourses(): Flow<List<CourseEntity>>

    @Query("SELECT * FROM term_settings WHERE id = 1")
    fun observeTerm(): Flow<TermSettingsEntity?>

    @Query("SELECT * FROM no_class_dates ORDER BY epochDay")
    fun observeNoClassDates(): Flow<List<NoClassDateEntity>>

    @Query("SELECT * FROM guidance_pause WHERE id = 1")
    fun observeGuidancePause(): Flow<GuidancePauseEntity?>

    @Query("SELECT * FROM campus_zones ORDER BY id")
    fun observeZones(): Flow<List<CampusZoneEntity>>

    @Query("SELECT id FROM campus_zones")
    suspend fun campusZoneIds(): List<String>

    @Query("SELECT * FROM campus_zones ORDER BY id")
    suspend fun campusZones(): List<CampusZoneEntity>

    @Query("SELECT COUNT(*) FROM courses")
    suspend fun courseCount(): Int

    @Insert
    suspend fun insertCourses(courses: List<CourseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putCourse(course: CourseEntity): Long

    @Query("DELETE FROM courses WHERE id = :id")
    suspend fun deleteCourse(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putTerm(term: TermSettingsEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addNoClassDate(date: NoClassDateEntity)

    @Query("DELETE FROM no_class_dates WHERE epochDay = :epochDay")
    suspend fun removeNoClassDate(epochDay: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putGuidancePause(pause: GuidancePauseEntity)

    @Query("DELETE FROM guidance_pause WHERE id = 1")
    suspend fun clearGuidancePause()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putZone(zone: CampusZoneEntity)


    @Query("SELECT * FROM shuttle_departures ORDER BY serviceDay, timeMinute, sourceRouteId")
    fun observeShuttleDepartures(): Flow<List<ShuttleDepartureEntity>>

    @Query("SELECT COUNT(*) FROM shuttle_departures")
    suspend fun shuttleDepartureCount(): Int

    @Query("SELECT * FROM source_status WHERE source = :source")
    fun observeSourceStatus(source: String): Flow<SourceStatusEntity?>

    @Query("SELECT * FROM source_status WHERE source = :source")
    suspend fun sourceStatus(source: String): SourceStatusEntity?

    @Query("DELETE FROM shuttle_departures")
    suspend fun clearShuttleDepartures()

    @Insert
    suspend fun insertShuttleDepartures(departures: List<ShuttleDepartureEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSourceStatus(status: SourceStatusEntity)

    @Query("SELECT * FROM notices ORDER BY orderIndex")
    fun observeNotices(): Flow<List<NoticeEntity>>

    @Query("SELECT COUNT(*) FROM notices")
    suspend fun noticeCount(): Int

    @Query("DELETE FROM notices")
    suspend fun clearNotices()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotices(notices: List<NoticeEntity>)

    @Query("SELECT * FROM meal_days ORDER BY epochDay")
    fun observeMealDays(): Flow<List<MealDayEntity>>

    @Query("DELETE FROM meal_days WHERE epochDay BETWEEN :startEpochDay AND :endEpochDay")
    suspend fun deleteMealWeek(startEpochDay: Long, endEpochDay: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putMealDays(days: List<MealDayEntity>)
}

@Database(
    entities = [CourseEntity::class, TermSettingsEntity::class, NoClassDateEntity::class, GuidancePauseEntity::class, CampusZoneEntity::class, ShuttleDepartureEntity::class, SourceStatusEntity::class, MealDayEntity::class, NoticeEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class DimaDatabase : RoomDatabase() {
    abstract fun scheduleDao(): ScheduleDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `guidance_pause` (`id` INTEGER NOT NULL, `startEpochDay` INTEGER NOT NULL, `endEpochDayInclusive` INTEGER NOT NULL, PRIMARY KEY(`id`))""",
                )
                db.execSQL("ALTER TABLE `campus_zones` ADD COLUMN `geometryType` TEXT NOT NULL DEFAULT 'CIRCLE'")
                db.execSQL("ALTER TABLE `campus_zones` ADD COLUMN `geometryVersion` TEXT")
                db.execSQL("ALTER TABLE `campus_zones` ADD COLUMN `polygonVertices` TEXT")
                db.execSQL("ALTER TABLE `campus_zones` ADD COLUMN `wakeRadiusMeters` INTEGER NOT NULL DEFAULT 250")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `notices` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `url` TEXT NOT NULL, `dateEpochDay` INTEGER NOT NULL, `isPinned` INTEGER NOT NULL, `orderIndex` INTEGER NOT NULL, PRIMARY KEY(`id`))""",
                )
            }
        }
    }
}
