package com.example.dimanow.meal

import androidx.room.withTransaction
import com.example.dimanow.data.DimaDatabase
import com.example.dimanow.data.DormitoryMealDayEntity
import com.example.dimanow.data.MealDayEntity
import com.example.dimanow.data.SourceStatusEntity
import com.example.dimanow.data.SyncStateEntity
import com.example.dimanow.domain.MealDay
import com.example.dimanow.domain.MealValidationState
import com.example.dimanow.time.MinuteTicker
import java.time.DayOfWeek
import java.time.Clock
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import com.example.dimanow.sync.CampusDataManifest
import com.example.dimanow.sync.MealPayload
import com.example.dimanow.sync.DormitoryMealPayload
import com.example.dimanow.sync.DormitoryMealSectionPayload
import com.example.dimanow.sync.StaticDataTransport
import com.example.dimanow.sync.CachingStaticDataTransport
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class MealData(
    val days: List<MealDay>,
    val lastSuccess: Instant?,
    val lastAttempt: Instant?,
    val error: String?,
    val sourceUrl: String,
    val sourceImageUrl: String?,
    val hours: String?,
    val serverPublishedAt: Instant? = null,
    val serverState: String? = null,
) {
    fun serviceStatusAt(now: ZonedDateTime): MealServiceStatus = mealServiceStatus(
        day = days.firstOrNull { it.date == now.toLocalDate() && it.validationState == MealValidationState.VALID },
        time = now.toLocalTime(),
    )

    val cachedWeeks: List<MealCachedWeek>
        get() = days
            .asSequence()
            .filter { it.validationState == MealValidationState.VALID }
            .groupBy { it.date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
            .map { (weekStart, cachedDays) ->
                MealCachedWeek(
                    weekStart = weekStart,
                    weekEnd = weekStart.plusDays(6),
                    cachedDayCount = cachedDays.size,
                )
            }
            .sortedBy(MealCachedWeek::weekStart)

}

data class DormitoryMealSection(
    val name: String,
    val hours: String?,
    val menuLines: List<String>,
)

data class DormitoryMealDay(
    val date: LocalDate,
    val sections: List<DormitoryMealSection>,
    val sourceImageUrl: String,
)

data class DormitoryMealData(
    val days: List<DormitoryMealDay>,
    val lastSuccess: Instant?,
    val lastAttempt: Instant?,
    val error: String?,
    val sourceUrl: String = DORMITORY_MEAL_SOURCE_URL,
    val sourceImageUrl: String? = null,
    val serverPublishedAt: Instant? = null,
    val serverState: String? = null,
) {
    fun hasCurrentWeek(today: LocalDate): Boolean {
        val weekStart = dormitoryMealWeekStart(today)
        return days.any { it.date in weekStart..weekStart.plusDays(6) }
    }
}

internal fun dormitoryMealWeekStart(today: LocalDate): LocalDate = when (today.dayOfWeek) {
    DayOfWeek.SATURDAY, DayOfWeek.SUNDAY -> today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
    else -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
}

enum class MealServiceState {
    BEFORE_OPEN,
    OPEN,
    CLOSED,
    NO_MENU,
    UNKNOWN_HOURS,
}

data class MealServiceStatus(
    val state: MealServiceState,
    val label: String,
)

object MealRefreshClock {
    fun today(clock: Clock): LocalDate = LocalDate.now(clock.withZone(MinuteTicker.CAMPUS_ZONE))
}

private val MEAL_HOURS_PATTERN = Regex("(\\d{2}:\\d{2})\\s*[~～-]\\s*(\\d{2}:\\d{2})")

fun mealServiceStatus(day: MealDay?, time: LocalTime): MealServiceStatus {
    day ?: return MealServiceStatus(MealServiceState.NO_MENU, "오늘은 제공 식단이 없습니다")
    val match = MEAL_HOURS_PATTERN.matchEntire(day.hours.trim())
        ?: return MealServiceStatus(MealServiceState.UNKNOWN_HOURS, day.hours)
    val start = LocalTime.parse(match.groupValues[1])
    val end = LocalTime.parse(match.groupValues[2])
    return when {
        time.isBefore(start) -> MealServiceStatus(MealServiceState.BEFORE_OPEN, "운영 전 · ${start}부터")
        time.isBefore(end) -> MealServiceStatus(MealServiceState.OPEN, "운영 중 · ${end}까지")
        else -> MealServiceStatus(MealServiceState.CLOSED, "운영 종료")
    }
}

data class MealCachedWeek(
    val weekStart: LocalDate,
    val weekEnd: LocalDate,
    val cachedDayCount: Int,
)

sealed interface MealRefreshResult {
    data class Success(val weekStart: LocalDate, val at: Instant) : MealRefreshResult
    data object NotPublishedYet : MealRefreshResult
    data class NeedsReview(val reason: String, val sourceImageUrl: String?) : MealRefreshResult
    data class Failure(val message: String) : MealRefreshResult
}

interface MealSource {
    val data: Flow<MealData>
    val dormitoryData: Flow<DormitoryMealData>
        get() = flowOf(DormitoryMealData(emptyList(), null, null, null))
    suspend fun refresh(): MealRefreshResult
    suspend fun refreshDormitory(): MealRefreshResult = MealRefreshResult.NotPublishedYet
    suspend fun beginDormitoryUploadAuthorization(): DormitoryMealAuthorization =
        DormitoryMealAuthorization.Failed("GitHub 업로드 연결이 준비되지 않았습니다.")
    suspend fun pollDormitoryUploadAuthorization(authorization: DormitoryDeviceAuthorization): DormitoryMealAuthorization =
        DormitoryMealAuthorization.Failed("GitHub 업로드 연결이 준비되지 않았습니다.")
    suspend fun submitDormitoryMeal(image: DormitoryMealImage): DormitoryMealSubmissionResult =
        DormitoryMealSubmissionResult.Failure("GitHub 업로드 연결이 준비되지 않았습니다.")
    suspend fun dormitorySubmissionStatus(submissionId: String): DormitoryMealSubmissionResult =
        DormitoryMealSubmissionResult.Failure("식단 처리 상태를 확인하지 못했습니다.")
}

const val OFFICIAL_MEAL_SOURCE_URL = "https://www.dima.ac.kr/?p=1"
const val DORMITORY_MEAL_SOURCE_URL = "https://github.com/winter1l/DimaNow/tree/dorm-submissions/dorm-submissions"

class StaticMealSource(
    private val database: DimaDatabase,
    private val transport: StaticDataTransport,
    private val clock: Clock = Clock.systemUTC(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val dormitorySubmissionService: DormitoryMealSubmissionService? = null,
) : MealSource {
    private val dao = database.scheduleDao()
    private val refreshMutex = Mutex()
    private val json = Json { ignoreUnknownKeys = false }

    override val data: Flow<MealData> = combine(
        dao.observeMealDays(),
        dao.observeSourceStatus(SOURCE_KEY),
        dao.observeSyncState(SOURCE_KEY),
    ) { days, status, sync ->
        MealData(
            days = days.map { it.toDomain() },
            lastSuccess = status?.lastSuccessEpochMillis?.let(Instant::ofEpochMilli),
            lastAttempt = status?.lastAttemptEpochMillis?.let(Instant::ofEpochMilli),
            error = status?.error,
            sourceUrl = status?.sourceUrl ?: OFFICIAL_MEAL_SOURCE_URL,
            sourceImageUrl = status?.candidateImageUrl,
            hours = status?.hours,
            serverPublishedAt = sync?.serverPublishedEpochMillis?.let(Instant::ofEpochMilli),
            serverState = sync?.serverState,
        )
    }

    override val dormitoryData: Flow<DormitoryMealData> = combine(
        dao.observeDormitoryMealDays(),
        dao.observeSourceStatus(DORMITORY_SOURCE_KEY),
        dao.observeSyncState(DORMITORY_SOURCE_KEY),
    ) { days, status, sync ->
        DormitoryMealData(
            days = days.map { entity ->
                DormitoryMealDay(
                    date = LocalDate.ofEpochDay(entity.epochDay),
                    sections = json.decodeFromString<List<DormitoryMealSectionPayload>>(entity.sectionsJson).map {
                        DormitoryMealSection(it.name, it.hours, it.menuLines)
                    },
                    sourceImageUrl = entity.sourceImageUrl,
                )
            },
            lastSuccess = status?.lastSuccessEpochMillis?.let(Instant::ofEpochMilli),
            lastAttempt = status?.lastAttemptEpochMillis?.let(Instant::ofEpochMilli),
            error = status?.error,
            sourceUrl = status?.sourceUrl ?: DORMITORY_MEAL_SOURCE_URL,
            sourceImageUrl = status?.candidateImageUrl,
            serverPublishedAt = sync?.serverPublishedEpochMillis?.let(Instant::ofEpochMilli),
            serverState = sync?.serverState,
        )
    }

    override suspend fun refresh(): MealRefreshResult = refreshMutex.withLock { withContext(ioDispatcher) {
        val attempt = clock.instant()
        try {
            val manifest = json.decodeFromString<CampusDataManifest>(transport.get(MANIFEST_URL).decodeToString())
            require(manifest.schemaVersion == 1) { "지원하지 않는 동기화 스키마입니다." }
            val descriptor = manifest.datasets.getValue(SOURCE_KEY)
            require(descriptor.sourceUrl == OFFICIAL_MEAL_SOURCE_URL) { "허용되지 않은 식단 원문 주소입니다." }
            val previousSync = dao.syncState(SOURCE_KEY)
            if (descriptor.state != "READY") {
                val previousStatus = dao.sourceStatus(SOURCE_KEY)
                val reason = descriptor.message ?: when (descriptor.state) {
                    "WAITING" -> NOT_PUBLISHED_MESSAGE
                    "NEEDS_REVIEW" -> "메뉴 확인 필요"
                    else -> "식단 데이터가 준비되지 않았습니다."
                }
                database.withTransaction {
                    dao.putSourceStatus(
                        SourceStatusEntity(
                            source = SOURCE_KEY,
                            lastSuccessEpochMillis = previousStatus?.lastSuccessEpochMillis,
                            lastAttemptEpochMillis = attempt.toEpochMilli(),
                            error = if (descriptor.state == "NEEDS_REVIEW") reason else null,
                            sourceUrl = descriptor.sourceUrl,
                            candidateImageUrl = previousStatus?.candidateImageUrl,
                            hours = previousStatus?.hours,
                        ),
                    )
                    dao.putSyncState(
                        (previousSync ?: SyncStateEntity(
                            dataset = SOURCE_KEY,
                            revision = descriptor.revision,
                            etag = null,
                            sha256 = descriptor.sha256,
                            serverPublishedEpochMillis = runCatching { Instant.parse(descriptor.publishedAt).toEpochMilli() }.getOrNull(),
                            lastCheckedEpochMillis = attempt.toEpochMilli(),
                            lastImportedEpochMillis = null,
                            serverState = descriptor.state,
                            error = null,
                        )).copy(
                            revision = descriptor.revision,
                            sha256 = descriptor.sha256,
                            lastCheckedEpochMillis = attempt.toEpochMilli(),
                            serverState = descriptor.state,
                            error = if (descriptor.state == "NEEDS_REVIEW") reason else null,
                        ),
                    )
                }
                return@withContext when (descriptor.state) {
                    "WAITING" -> MealRefreshResult.NotPublishedYet
                    "NEEDS_REVIEW" -> MealRefreshResult.NeedsReview(reason, previousStatus?.candidateImageUrl)
                    else -> MealRefreshResult.Failure(reason)
                }
            }
            if (previousSync?.revision == descriptor.revision && previousSync.sha256 == descriptor.sha256) {
                val previousStatus = dao.sourceStatus(SOURCE_KEY)
                val cachedDays = dao.observeMealDays().first()
                val week = cachedDays.firstOrNull()?.epochDay?.let(LocalDate::ofEpochDay)
                    ?: throw IllegalStateException("식단 캐시가 비어 있습니다.")
                val cachedLastDate = cachedDays.maxOf { LocalDate.ofEpochDay(it.epochDay) }
                if (cachedLastDate.isBefore(MealRefreshClock.today(clock))) {
                    database.withTransaction {
                        dao.putSyncState(previousSync.copy(lastCheckedEpochMillis = attempt.toEpochMilli(), serverState = "WAITING", error = null))
                        dao.putSourceStatus(
                            SourceStatusEntity(
                                source = SOURCE_KEY,
                                lastSuccessEpochMillis = previousStatus?.lastSuccessEpochMillis,
                                lastAttemptEpochMillis = attempt.toEpochMilli(),
                                error = null,
                                sourceUrl = descriptor.sourceUrl,
                                candidateImageUrl = previousStatus?.candidateImageUrl,
                                hours = previousStatus?.hours,
                            ),
                        )
                    }
                    return@withContext MealRefreshResult.NotPublishedYet
                }
                database.withTransaction {
                    dao.putSyncState(previousSync.copy(lastCheckedEpochMillis = attempt.toEpochMilli(), serverState = descriptor.state, error = null))
                    dao.putSourceStatus(
                        SourceStatusEntity(
                            source = SOURCE_KEY,
                            lastSuccessEpochMillis = attempt.toEpochMilli(),
                            lastAttemptEpochMillis = attempt.toEpochMilli(),
                            error = null,
                            sourceUrl = descriptor.sourceUrl,
                            candidateImageUrl = previousStatus?.candidateImageUrl,
                            hours = previousStatus?.hours,
                        ),
                    )
                }
                return@withContext MealRefreshResult.Success(week.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)), attempt)
            }
            val relativeUrl = descriptor.url
            require(relativeUrl.matches(Regex("meal/[0-9a-f]{64}\\.json"))) { "잘못된 식단 데이터 경로입니다." }
            val payloadBytes = transport.get("$DATA_ROOT/$relativeUrl")
            require(payloadBytes.sha256() == descriptor.sha256) { "식단 데이터 무결성 검사에 실패했습니다." }
            val payload = json.decodeFromString<MealPayload>(payloadBytes.decodeToString())
            require(payload.schemaVersion == 1) { "지원하지 않는 식단 스키마입니다." }
            val weekStart = LocalDate.parse(payload.weekStart)
            val weekEnd = LocalDate.parse(payload.weekEnd)
            require(weekEnd == weekStart.plusDays(6) && payload.days.isNotEmpty() && payload.days.size <= 7) { "식단 주차가 올바르지 않습니다." }
            require(payload.days.distinctBy { it.date }.size == payload.days.size) { "식단 날짜가 중복되었습니다." }
            val entities = payload.days.map { row ->
                val date = LocalDate.parse(row.date)
                require(date in weekStart..weekEnd) { "식단 날짜가 주차 범위를 벗어났습니다." }
                require(row.menuLines.size >= 2 && row.menuLines.all { it.isNotBlank() }) { "식단 메뉴 줄 수가 부족합니다." }
                require(row.sourceUrl.startsWith("https://www.instagram.com/") || row.sourceUrl.startsWith("https://www.dima.ac.kr/")) {
                    "허용되지 않은 식단 원문 주소입니다."
                }
                com.example.dimanow.data.MealDayEntity(
                    epochDay = date.toEpochDay(),
                    menuText = row.menuLines.joinToString("\n"),
                    hours = row.hours,
                    sourceUrl = row.sourceUrl,
                    sourceImageUrl = row.sourceImageUrl,
                    validationState = MealValidationState.VALID.name,
                )
            }
            val first = payload.days.first()
            database.withTransaction {
                dao.deleteMealWeek(weekStart.toEpochDay(), weekEnd.toEpochDay())
                dao.putMealDays(entities)
                dao.putSourceStatus(
                    SourceStatusEntity(
                        source = SOURCE_KEY,
                        lastSuccessEpochMillis = attempt.toEpochMilli(),
                        lastAttemptEpochMillis = attempt.toEpochMilli(),
                        error = null,
                        sourceUrl = descriptor.sourceUrl,
                        candidateImageUrl = first.sourceImageUrl,
                        hours = first.hours,
                    ),
                )
                dao.putSyncState(
                    SyncStateEntity(
                        dataset = SOURCE_KEY,
                        revision = descriptor.revision,
                        etag = null,
                        sha256 = descriptor.sha256,
                        serverPublishedEpochMillis = Instant.parse(descriptor.publishedAt).toEpochMilli(),
                        lastCheckedEpochMillis = attempt.toEpochMilli(),
                        lastImportedEpochMillis = attempt.toEpochMilli(),
                        serverState = descriptor.state,
                        error = null,
                    ),
                )
            }
            MealRefreshResult.Success(weekStart, attempt)
        } catch (error: Exception) {
            val previous = dao.sourceStatus(SOURCE_KEY)
            val previousSync = dao.syncState(SOURCE_KEY)
            dao.putSourceStatus(
                SourceStatusEntity(
                    source = SOURCE_KEY,
                    lastSuccessEpochMillis = previous?.lastSuccessEpochMillis,
                    lastAttemptEpochMillis = attempt.toEpochMilli(),
                    error = error.message ?: error.javaClass.simpleName,
                    sourceUrl = previous?.sourceUrl ?: OFFICIAL_MEAL_SOURCE_URL,
                    candidateImageUrl = previous?.candidateImageUrl,
                    hours = previous?.hours,
                ),
            )
            dao.putSyncState(
                (previousSync ?: SyncStateEntity(SOURCE_KEY, 0, null, null, null, attempt.toEpochMilli(), null, null, null)).copy(
                    lastCheckedEpochMillis = attempt.toEpochMilli(),
                    error = error.message ?: error.javaClass.simpleName,
                ),
            )
            MealRefreshResult.Failure(error.message ?: "식단 동기화 실패")
        }
    } }

    override suspend fun refreshDormitory(): MealRefreshResult = refreshMutex.withLock { withContext(ioDispatcher) {
        val attempt = clock.instant()
        try {
            (transport as? CachingStaticDataTransport)?.invalidateManifest()
            val manifest = json.decodeFromString<CampusDataManifest>(transport.get(MANIFEST_URL).decodeToString())
            require(manifest.schemaVersion == 1) { "지원하지 않는 동기화 스키마입니다." }
            val descriptor = manifest.datasets[DORMITORY_SOURCE_KEY]
                ?: return@withContext recordDormitoryWaiting(attempt, "등록된 식단 없음")
            require(descriptor.sourceUrl == DORMITORY_MEAL_SOURCE_URL) { "허용되지 않은 기숙사 식단 원문 주소입니다." }
            val previousSync = dao.syncState(DORMITORY_SOURCE_KEY)
            if (descriptor.state != "READY") {
                return@withContext recordDormitoryWaiting(attempt, descriptor.message ?: "등록된 식단 없음", descriptor.state)
            }
            val cachedDays = dao.observeDormitoryMealDays().first()
            if (previousSync?.revision == descriptor.revision && previousSync.sha256 == descriptor.sha256 && cachedDays.isNotEmpty()) {
                val firstDate = LocalDate.ofEpochDay(cachedDays.first().epochDay)
                val weekStart = firstDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                if (weekStart != dormitoryMealWeekStart(MealRefreshClock.today(clock))) {
                    return@withContext recordDormitoryWaiting(attempt, "등록된 식단 없음")
                }
                database.withTransaction {
                    dao.putSyncState(previousSync.copy(lastCheckedEpochMillis = attempt.toEpochMilli(), serverState = "READY", error = null))
                    dao.putSourceStatus(
                        SourceStatusEntity(DORMITORY_SOURCE_KEY, attempt.toEpochMilli(), attempt.toEpochMilli(), null, descriptor.sourceUrl, candidateImageUrl = cachedDays.first().sourceImageUrl),
                    )
                }
                return@withContext MealRefreshResult.Success(weekStart, attempt)
            }
            require(descriptor.url.matches(Regex("dorm-meal/[0-9a-f]{64}\\.json"))) { "잘못된 기숙사 식단 데이터 경로입니다." }
            val payloadBytes = transport.get("$DATA_ROOT/${descriptor.url}")
            require(payloadBytes.sha256() == descriptor.sha256) { "기숙사 식단 데이터 무결성 검사에 실패했습니다." }
            val payload = json.decodeFromString<DormitoryMealPayload>(payloadBytes.decodeToString())
            require(payload.schemaVersion == 1) { "지원하지 않는 기숙사 식단 스키마입니다." }
            val weekStart = LocalDate.parse(payload.weekStart)
            val weekEnd = LocalDate.parse(payload.weekEnd)
            require(weekEnd == weekStart.plusDays(6) && payload.days.isNotEmpty() && payload.days.size <= 7) { "기숙사 식단 주차가 올바르지 않습니다." }
            require(payload.days.distinctBy { it.date }.size == payload.days.size) { "기숙사 식단 날짜가 중복되었습니다." }
            val entities = payload.days.map { row ->
                val date = LocalDate.parse(row.date)
                require(date in weekStart..weekEnd) { "기숙사 식단 날짜가 주차 범위를 벗어났습니다." }
                require(row.sections.isNotEmpty() && row.sections.all { it.name.isNotBlank() && it.menuLines.isNotEmpty() && it.menuLines.all(String::isNotBlank) }) {
                    "기숙사 식단 내용이 비어 있습니다."
                }
                DormitoryMealDayEntity(
                    epochDay = date.toEpochDay(),
                    sectionsJson = json.encodeToString(row.sections),
                    sourceImageUrl = payload.sourceImageUrl,
                    validationState = MealValidationState.VALID.name,
                )
            }
            database.withTransaction {
                dao.deleteDormitoryMealWeek(weekStart.toEpochDay(), weekEnd.toEpochDay())
                dao.putDormitoryMealDays(entities)
                dao.putSourceStatus(
                    SourceStatusEntity(DORMITORY_SOURCE_KEY, attempt.toEpochMilli(), attempt.toEpochMilli(), null, descriptor.sourceUrl, candidateImageUrl = payload.sourceImageUrl),
                )
                dao.putSyncState(
                    SyncStateEntity(
                        dataset = DORMITORY_SOURCE_KEY,
                        revision = descriptor.revision,
                        etag = null,
                        sha256 = descriptor.sha256,
                        serverPublishedEpochMillis = Instant.parse(descriptor.publishedAt).toEpochMilli(),
                        lastCheckedEpochMillis = attempt.toEpochMilli(),
                        lastImportedEpochMillis = attempt.toEpochMilli(),
                        serverState = descriptor.state,
                        error = null,
                    ),
                )
            }
            MealRefreshResult.Success(weekStart, attempt)
        } catch (error: Exception) {
            val previous = dao.sourceStatus(DORMITORY_SOURCE_KEY)
            dao.putSourceStatus(
                SourceStatusEntity(
                    source = DORMITORY_SOURCE_KEY,
                    lastSuccessEpochMillis = previous?.lastSuccessEpochMillis,
                    lastAttemptEpochMillis = attempt.toEpochMilli(),
                    error = error.message ?: error.javaClass.simpleName,
                    sourceUrl = previous?.sourceUrl ?: DORMITORY_MEAL_SOURCE_URL,
                    candidateImageUrl = previous?.candidateImageUrl,
                ),
            )
            MealRefreshResult.Failure(error.message ?: "기숙사 식단 동기화 실패")
        }
    } }

    override suspend fun beginDormitoryUploadAuthorization(): DormitoryMealAuthorization =
        dormitorySubmissionService?.beginAuthorization()
            ?: DormitoryMealAuthorization.Failed("GitHub 업로드 연결이 준비되지 않았습니다.")

    override suspend fun pollDormitoryUploadAuthorization(authorization: DormitoryDeviceAuthorization): DormitoryMealAuthorization =
        dormitorySubmissionService?.pollAuthorization(authorization)
            ?: DormitoryMealAuthorization.Failed("GitHub 업로드 연결이 준비되지 않았습니다.")

    override suspend fun submitDormitoryMeal(image: DormitoryMealImage): DormitoryMealSubmissionResult =
        dormitorySubmissionService?.submit(image)
            ?: DormitoryMealSubmissionResult.Failure("GitHub 업로드 연결이 준비되지 않았습니다.")

    override suspend fun dormitorySubmissionStatus(submissionId: String): DormitoryMealSubmissionResult =
        dormitorySubmissionService?.status(submissionId)
            ?: DormitoryMealSubmissionResult.Failure("식단 처리 상태를 확인하지 못했습니다.")

    private suspend fun recordDormitoryWaiting(attempt: Instant, message: String, state: String = "WAITING"): MealRefreshResult {
        val previous = dao.sourceStatus(DORMITORY_SOURCE_KEY)
        val previousSync = dao.syncState(DORMITORY_SOURCE_KEY)
        database.withTransaction {
            dao.putSourceStatus(
                SourceStatusEntity(
                    source = DORMITORY_SOURCE_KEY,
                    lastSuccessEpochMillis = previous?.lastSuccessEpochMillis,
                    lastAttemptEpochMillis = attempt.toEpochMilli(),
                    error = null,
                    sourceUrl = DORMITORY_MEAL_SOURCE_URL,
                    candidateImageUrl = previous?.candidateImageUrl,
                ),
            )
            dao.putSyncState(
                (previousSync ?: SyncStateEntity(DORMITORY_SOURCE_KEY, 0, null, null, null, attempt.toEpochMilli(), null, state, null)).copy(
                    lastCheckedEpochMillis = attempt.toEpochMilli(),
                    serverState = state,
                    error = null,
                ),
            )
        }
        return if (state == "NEEDS_REVIEW") MealRefreshResult.NeedsReview(message, previous?.candidateImageUrl) else MealRefreshResult.NotPublishedYet
    }

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { "%02x".format(it) }

    private companion object {
        const val DATA_ROOT = "https://winter1l.github.io/DimaNow/data/v1"
        const val MANIFEST_URL = "$DATA_ROOT/manifest.json"
        const val SOURCE_KEY = "meal"
        const val DORMITORY_SOURCE_KEY = "dorm_meal"
        const val NOT_PUBLISHED_MESSAGE = "아직 새 식단이 올라오지 않았어요"
    }
}
