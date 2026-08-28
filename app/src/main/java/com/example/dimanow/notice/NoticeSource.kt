package com.example.dimanow.notice

import androidx.room.withTransaction
import com.example.dimanow.data.DimaDatabase
import com.example.dimanow.data.NoticeEntity
import com.example.dimanow.data.SourceStatusEntity
import com.example.dimanow.data.SyncStateEntity
import com.example.dimanow.domain.CampusNotice
import java.time.Instant
import java.time.Clock
import java.time.LocalDate
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import com.example.dimanow.sync.CampusDataManifest
import com.example.dimanow.sync.NoticePayload
import com.example.dimanow.sync.StaticDataTransport
import kotlinx.serialization.json.Json

data class NoticeData(
    val notices: List<CampusNotice>,
    val lastSuccess: Instant?,
    val lastAttempt: Instant?,
    val error: String?,
    val sourceUrl: String,
    val serverPublishedAt: Instant? = null,
    val serverState: String? = null,
)

sealed interface NoticeRefreshResult {
    data class Success(val noticeCount: Int, val at: Instant) : NoticeRefreshResult
    data class Failure(val message: String, val cachedNoticeCount: Int) : NoticeRefreshResult
}

interface NoticeSource {
    val data: Flow<NoticeData>
    suspend fun refresh(): NoticeRefreshResult
}

const val OFFICIAL_NOTICE_SOURCE_URL = "https://www.dima.ac.kr/?p=111"

class StaticNoticeSource(
    private val database: DimaDatabase,
    private val transport: StaticDataTransport,
    private val clock: Clock = Clock.systemUTC(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : NoticeSource {
    private val dao = database.scheduleDao()
    private val refreshMutex = Mutex()
    private val json = Json { ignoreUnknownKeys = false }

    override val data: Flow<NoticeData> = combine(
        dao.observeNotices(),
        dao.observeSourceStatus(SOURCE_KEY),
        dao.observeSyncState(SOURCE_KEY),
    ) { notices, status, sync ->
        NoticeData(
            notices = notices.map { it.toDomain() },
            lastSuccess = status?.lastSuccessEpochMillis?.let(Instant::ofEpochMilli),
            lastAttempt = status?.lastAttemptEpochMillis?.let(Instant::ofEpochMilli),
            error = status?.error,
            sourceUrl = status?.sourceUrl ?: OFFICIAL_NOTICE_SOURCE_URL,
            serverPublishedAt = sync?.serverPublishedEpochMillis?.let(Instant::ofEpochMilli),
            serverState = sync?.serverState,
        )
    }

    override suspend fun refresh(): NoticeRefreshResult = refreshMutex.withLock { withContext(ioDispatcher) {
        val attempt = clock.instant()
        try {
            val manifest = json.decodeFromString<CampusDataManifest>(transport.get(MANIFEST_URL).decodeToString())
            require(manifest.schemaVersion == 1) { "지원하지 않는 동기화 스키마입니다." }
            val descriptor = manifest.datasets.getValue(SOURCE_KEY)
            require(descriptor.state == "READY") { descriptor.message ?: "공지 데이터가 준비되지 않았습니다." }
            require(descriptor.sourceUrl == OFFICIAL_NOTICE_SOURCE_URL) { "허용되지 않은 공지 원문 주소입니다." }
            val previousSync = dao.syncState(SOURCE_KEY)
            if (previousSync?.revision == descriptor.revision && previousSync.sha256 == descriptor.sha256) {
                database.withTransaction {
                    dao.putSyncState(previousSync.copy(lastCheckedEpochMillis = attempt.toEpochMilli(), serverState = descriptor.state, error = null))
                    dao.putSourceStatus(SourceStatusEntity(SOURCE_KEY, attempt.toEpochMilli(), attempt.toEpochMilli(), null, descriptor.sourceUrl))
                }
                return@withContext NoticeRefreshResult.Success(dao.noticeCount(), attempt)
            }
            val relativeUrl = descriptor.url
            require(relativeUrl.matches(Regex("notices/[0-9a-f]{64}\\.json"))) { "잘못된 공지 데이터 경로입니다." }
            val payloadBytes = transport.get("$DATA_ROOT/$relativeUrl")
            require(payloadBytes.sha256() == descriptor.sha256) { "공지 데이터 무결성 검사에 실패했습니다." }
            val payload = json.decodeFromString<NoticePayload>(payloadBytes.decodeToString())
            require(payload.schemaVersion == 1 && payload.notices.isNotEmpty() && payload.notices.size <= 10) {
                "공지 데이터가 올바르지 않습니다."
            }
            require(payload.notices.distinctBy { it.id }.size == payload.notices.size) { "공지 ID가 중복되었습니다." }
            val entities = payload.notices.mapIndexed { index, row ->
                require(row.id.matches(Regex("[0-9]{1,32}"))) { "공지 ID가 올바르지 않습니다." }
                require(row.url.startsWith("https://www.dima.ac.kr/")) { "허용되지 않은 공지 주소입니다." }
                NoticeEntity(
                    id = row.id,
                    title = row.title,
                    url = row.url,
                    dateEpochDay = LocalDate.parse(row.date).toEpochDay(),
                    isPinned = row.pinned,
                    orderIndex = index,
                )
            }
            database.withTransaction {
                dao.clearNotices()
                dao.insertNotices(entities)
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
            NoticeRefreshResult.Success(entities.size, attempt)
        } catch (error: Exception) {
            val previous = dao.sourceStatus(SOURCE_KEY)
            val previousSync = dao.syncState(SOURCE_KEY)
            dao.putSourceStatus(
                SourceStatusEntity(
                    source = SOURCE_KEY,
                    lastSuccessEpochMillis = previous?.lastSuccessEpochMillis,
                    lastAttemptEpochMillis = attempt.toEpochMilli(),
                    error = error.message ?: error.javaClass.simpleName,
                    sourceUrl = previous?.sourceUrl ?: OFFICIAL_NOTICE_SOURCE_URL,
                ),
            )
            dao.putSyncState(
                (previousSync ?: SyncStateEntity(SOURCE_KEY, 0, null, null, null, attempt.toEpochMilli(), null, null, null)).copy(
                    lastCheckedEpochMillis = attempt.toEpochMilli(),
                    error = error.message ?: error.javaClass.simpleName,
                ),
            )
            NoticeRefreshResult.Failure(error.message ?: "공지 동기화 실패", dao.noticeCount())
        }
    } }

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { "%02x".format(it) }

    private companion object {
        const val DATA_ROOT = "https://winter1l.github.io/DimaNow/data/v1"
        const val MANIFEST_URL = "$DATA_ROOT/manifest.json"
        const val SOURCE_KEY = "notice"
    }
}
