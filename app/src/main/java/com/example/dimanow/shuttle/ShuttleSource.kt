package com.example.dimanow.shuttle

import androidx.room.withTransaction
import com.example.dimanow.data.DimaDatabase
import com.example.dimanow.data.ShuttleDepartureEntity
import com.example.dimanow.data.SourceStatusEntity
import com.example.dimanow.data.SyncStateEntity
import com.example.dimanow.domain.ShuttleDeparture
import java.time.Instant
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalTime
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import com.example.dimanow.sync.CampusDataManifest
import com.example.dimanow.sync.ShuttlePayload
import com.example.dimanow.sync.StaticDataTransport
import kotlinx.serialization.json.Json

data class ShuttleData(
    val departures: List<ShuttleDeparture>,
    val lastSuccess: Instant?,
    val lastAttempt: Instant?,
    val error: String?,
    val sourceUrl: String,
    val noticeUrl: String?,
    val serverPublishedAt: Instant? = null,
    val serverState: String? = null,
)

sealed interface ShuttleRefreshResult {
    data class Success(val departureCount: Int, val at: Instant) : ShuttleRefreshResult
    data class Failure(val message: String, val cachedDepartureCount: Int) : ShuttleRefreshResult
}

interface ShuttleSource {
    val data: Flow<ShuttleData>
    suspend fun refresh(): ShuttleRefreshResult
}

const val OFFICIAL_SHUTTLE_SOURCE_URL = "https://www.dima.ac.kr/?p=97"

class StaticShuttleSource(
    private val database: DimaDatabase,
    private val transport: StaticDataTransport,
    private val clock: Clock = Clock.systemUTC(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ShuttleSource {
    private val dao = database.scheduleDao()
    private val refreshMutex = Mutex()
    private val json = Json { ignoreUnknownKeys = false }

    override val data: Flow<ShuttleData> = combine(
        dao.observeShuttleDepartures().map { rows -> rows.map { it.toDomain() } }.distinctUntilChanged(),
        dao.observeSourceStatus(SOURCE_KEY),
        dao.observeSyncState(SOURCE_KEY),
    ) { departures, status, sync ->
        ShuttleData(
            departures,
            status?.lastSuccessEpochMillis?.let(Instant::ofEpochMilli),
            status?.lastAttemptEpochMillis?.let(Instant::ofEpochMilli),
            status?.error,
            status?.sourceUrl ?: OFFICIAL_SHUTTLE_SOURCE_URL,
            status?.noticeUrl,
            sync?.serverPublishedEpochMillis?.let(Instant::ofEpochMilli),
            sync?.serverState,
        )
    }

    override suspend fun refresh(): ShuttleRefreshResult = refreshMutex.withLock { withContext(ioDispatcher) {
        val attempt = clock.instant()
        try {
            val manifest = json.decodeFromString<CampusDataManifest>(transport.get(MANIFEST_URL).decodeToString())
            require(manifest.schemaVersion == 1) { "지원하지 않는 동기화 스키마입니다." }
            val descriptor = manifest.datasets.getValue(SOURCE_KEY)
            require(descriptor.state == "READY") { descriptor.message ?: "셔틀 데이터가 준비되지 않았습니다." }
            require(descriptor.sourceUrl == OFFICIAL_SHUTTLE_SOURCE_URL) { "허용되지 않은 셔틀 원문 주소입니다." }
            val previousSync = dao.syncState(SOURCE_KEY)
            if (previousSync?.revision == descriptor.revision && previousSync.sha256 == descriptor.sha256) {
                val previousStatus = dao.sourceStatus(SOURCE_KEY)
                database.withTransaction {
                    dao.putSyncState(
                        previousSync.copy(
                            lastCheckedEpochMillis = attempt.toEpochMilli(),
                            serverState = descriptor.state,
                            error = null,
                        ),
                    )
                    dao.putSourceStatus(
                        SourceStatusEntity(
                            source = SOURCE_KEY,
                            lastSuccessEpochMillis = attempt.toEpochMilli(),
                            lastAttemptEpochMillis = attempt.toEpochMilli(),
                            error = null,
                            sourceUrl = descriptor.sourceUrl,
                            noticeUrl = previousStatus?.noticeUrl,
                        ),
                    )
                }
                return@withContext ShuttleRefreshResult.Success(dao.shuttleDepartureCount(), attempt)
            }
            val relativeUrl = requirePayloadUrl(descriptor.url, "shuttle")
            val payloadBytes = transport.get("$DATA_ROOT/$relativeUrl")
            require(payloadBytes.sha256() == descriptor.sha256) { "셔틀 데이터 무결성 검사에 실패했습니다." }
            val payload = json.decodeFromString<ShuttlePayload>(payloadBytes.decodeToString())
            require(payload.schemaVersion == 1 && payload.departures.isNotEmpty() && payload.departures.size <= 2_000) {
                "셔틀 데이터 행 수가 올바르지 않습니다."
            }
            val entities = payload.departures.map { row ->
                require(row.routeId.matches(Regex("[A-Za-z0-9_-]{1,40}"))) { "셔틀 노선 ID가 올바르지 않습니다." }
                require(row.stopId.matches(Regex("[A-Za-z0-9_-]{1,80}"))) { "셔틀 정류장 ID가 올바르지 않습니다." }
                val origin = com.example.dimanow.domain.CampusZoneId.valueOf(row.originZone)
                val destination = com.example.dimanow.domain.CampusZoneId.valueOf(row.destinationZone)
                require(origin != com.example.dimanow.domain.CampusZoneId.OUTSIDE && destination != com.example.dimanow.domain.CampusZoneId.OUTSIDE) {
                    "셔틀 구역이 올바르지 않습니다."
                }
                require(row.direction == "TO_${destination.name}") { "셔틀 방향이 목적지와 일치하지 않습니다." }
                ShuttleDepartureEntity.fromDomain(
                    ShuttleDeparture(
                        sourceRouteId = row.routeId,
                        sourceStopId = row.stopId,
                        direction = row.direction,
                        serviceDay = DayOfWeek.valueOf(row.serviceDay),
                        time = LocalTime.parse(row.departureTime),
                        originZone = origin,
                        destinationZone = destination,
                        arrivalTime = row.arrivalTime?.let(LocalTime::parse),
                    ),
                )
            }
            require(entities.distinctBy(ShuttleDepartureEntity::key).size == entities.size) { "셔틀 데이터에 중복 행이 있습니다." }
            database.withTransaction {
                dao.clearShuttleDepartures()
                dao.insertShuttleDepartures(entities)
                dao.putSourceStatus(SourceStatusEntity(SOURCE_KEY, attempt.toEpochMilli(), attempt.toEpochMilli(), null, descriptor.sourceUrl))
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
            ShuttleRefreshResult.Success(entities.size, attempt)
        } catch (error: Exception) {
            val previous = dao.sourceStatus(SOURCE_KEY)
            val previousSync = dao.syncState(SOURCE_KEY)
            dao.putSourceStatus(
                SourceStatusEntity(
                    source = SOURCE_KEY,
                    lastSuccessEpochMillis = previous?.lastSuccessEpochMillis,
                    lastAttemptEpochMillis = attempt.toEpochMilli(),
                    error = error.message ?: error.javaClass.simpleName,
                    sourceUrl = previous?.sourceUrl ?: OFFICIAL_SHUTTLE_SOURCE_URL,
                    noticeUrl = previous?.noticeUrl,
                ),
            )
            dao.putSyncState(
                (previousSync ?: SyncStateEntity(SOURCE_KEY, 0, null, null, null, attempt.toEpochMilli(), null, null, null)).copy(
                    lastCheckedEpochMillis = attempt.toEpochMilli(),
                    error = error.message ?: error.javaClass.simpleName,
                ),
            )
            ShuttleRefreshResult.Failure(error.message ?: "셔틀 동기화 실패", dao.shuttleDepartureCount())
        }
    } }

    private fun requirePayloadUrl(value: String, directory: String): String {
        require(value.matches(Regex("$directory/[0-9a-f]{64}\\.json"))) { "잘못된 데이터 경로입니다." }
        return value
    }

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { "%02x".format(it) }

    private companion object {
        const val DATA_ROOT = "https://winter1l.github.io/DimaNow/data/v1"
        const val MANIFEST_URL = "$DATA_ROOT/manifest.json"
        const val SOURCE_KEY = "shuttle"
    }
}
