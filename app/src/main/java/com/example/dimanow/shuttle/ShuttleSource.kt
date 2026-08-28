package com.example.dimanow.shuttle

import androidx.room.withTransaction
import com.example.dimanow.data.DimaDatabase
import com.example.dimanow.data.ShuttleDepartureEntity
import com.example.dimanow.data.SourceStatusEntity
import com.example.dimanow.domain.ShuttleDeparture
import java.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

data class ShuttleData(
    val departures: List<ShuttleDeparture>,
    val lastSuccess: Instant?,
    val lastAttempt: Instant?,
    val error: String?,
    val sourceUrl: String,
    val noticeUrl: String?,
)

sealed interface ShuttleRefreshResult {
    data class Success(val departureCount: Int, val at: Instant) : ShuttleRefreshResult
    data class Failure(val message: String, val cachedDepartureCount: Int) : ShuttleRefreshResult
}

interface ShuttleSource {
    val data: Flow<ShuttleData>
    suspend fun refresh(): ShuttleRefreshResult
}

class OfficialShuttleSource(
    private val database: DimaDatabase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ShuttleSource {
    private val dao = database.scheduleDao()
    private val refreshMutex = Mutex()

    private val mappedDepartures = dao.observeShuttleDepartures()
        .map { rows -> rows.map { it.toDomain() } }
        .distinctUntilChanged()

    override val data: Flow<ShuttleData> = combine(
        mappedDepartures,
        dao.observeSourceStatus(SOURCE_KEY),
    ) { departures, status ->
        ShuttleData(
            departures = departures,
            lastSuccess = status?.lastSuccessEpochMillis?.let(Instant::ofEpochMilli),
            lastAttempt = status?.lastAttemptEpochMillis?.let(Instant::ofEpochMilli),
            error = status?.error,
            sourceUrl = SOURCE_URL,
            noticeUrl = status?.noticeUrl,
        )
    }

    override suspend fun refresh(): ShuttleRefreshResult = refreshMutex.withLock { withContext(ioDispatcher) {
        val attempt = Instant.now()
        try {
            val document = Jsoup.connect(SOURCE_URL).userAgent("DIMA-Now/1.0 personal Android app").get()
            val parsed = DimaShuttleParser().parse(document.outerHtml())
            require(parsed.isNotEmpty()) { "공식 셔틀 표를 찾지 못했습니다." }
            database.withTransaction {
                dao.clearShuttleDepartures()
                dao.insertShuttleDepartures(parsed.map(ShuttleDepartureEntity::fromDomain))
                dao.putSourceStatus(SourceStatusEntity(SOURCE_KEY, attempt.toEpochMilli(), attempt.toEpochMilli(), null, SOURCE_URL))
            }
            ShuttleRefreshResult.Success(parsed.size, attempt)
        } catch (error: Exception) {
            val previous = dao.sourceStatus(SOURCE_KEY)
            dao.putSourceStatus(
                SourceStatusEntity(
                    source = SOURCE_KEY,
                    lastSuccessEpochMillis = previous?.lastSuccessEpochMillis,
                    lastAttemptEpochMillis = attempt.toEpochMilli(),
                    error = error.message ?: error.javaClass.simpleName,
                    sourceUrl = SOURCE_URL,
                    noticeUrl = previous?.noticeUrl,
                ),
            )
            ShuttleRefreshResult.Failure(error.message ?: "셔틀 새로고침 실패", dao.shuttleDepartureCount())
        }
    } }

    companion object {
        const val SOURCE_URL = "https://www.dima.ac.kr/?p=97"
        private const val SOURCE_KEY = "shuttle"
    }
}
