package com.example.dimanow.notice

import androidx.room.withTransaction
import com.example.dimanow.data.DimaDatabase
import com.example.dimanow.data.NoticeEntity
import com.example.dimanow.data.SourceStatusEntity
import com.example.dimanow.domain.CampusNotice
import java.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

data class NoticeData(
    val notices: List<CampusNotice>,
    val lastSuccess: Instant?,
    val lastAttempt: Instant?,
    val error: String?,
    val sourceUrl: String,
)

sealed interface NoticeRefreshResult {
    data class Success(val noticeCount: Int, val at: Instant) : NoticeRefreshResult
    data class Failure(val message: String, val cachedNoticeCount: Int) : NoticeRefreshResult
}

interface NoticeSource {
    val data: Flow<NoticeData>
    suspend fun refresh(): NoticeRefreshResult
}

class OfficialNoticeSource(
    private val database: DimaDatabase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : NoticeSource {
    private val dao = database.scheduleDao()
    private val refreshMutex = Mutex()

    override val data: Flow<NoticeData> = combine(
        dao.observeNotices(),
        dao.observeSourceStatus(SOURCE_KEY),
    ) { notices, status ->
        NoticeData(
            notices = notices.map { it.toDomain() },
            lastSuccess = status?.lastSuccessEpochMillis?.let(Instant::ofEpochMilli),
            lastAttempt = status?.lastAttemptEpochMillis?.let(Instant::ofEpochMilli),
            error = status?.error,
            sourceUrl = SOURCE_URL,
        )
    }

    override suspend fun refresh(): NoticeRefreshResult = refreshMutex.withLock { withContext(ioDispatcher) {
        val attempt = Instant.now()
        try {
            val document = Jsoup.connect(SOURCE_URL).userAgent(USER_AGENT).get()
            val parsed = DimaNoticeParser().parse(document.outerHtml())
            require(parsed.isNotEmpty()) { "공식 공지 목록을 찾지 못했습니다." }
            database.withTransaction {
                dao.clearNotices()
                dao.insertNotices(parsed.mapIndexed { index, notice -> NoticeEntity.fromDomain(notice, index) })
                dao.putSourceStatus(SourceStatusEntity(SOURCE_KEY, attempt.toEpochMilli(), attempt.toEpochMilli(), null, SOURCE_URL))
            }
            NoticeRefreshResult.Success(parsed.size, attempt)
        } catch (error: Exception) {
            val previous = dao.sourceStatus(SOURCE_KEY)
            dao.putSourceStatus(
                SourceStatusEntity(
                    source = SOURCE_KEY,
                    lastSuccessEpochMillis = previous?.lastSuccessEpochMillis,
                    lastAttemptEpochMillis = attempt.toEpochMilli(),
                    error = error.message ?: error.javaClass.simpleName,
                    sourceUrl = SOURCE_URL,
                ),
            )
            NoticeRefreshResult.Failure(error.message ?: "공지 새로고침 실패", dao.noticeCount())
        }
    } }

    companion object {
        const val SOURCE_URL = "https://www.dima.ac.kr/?p=111"
        private const val SOURCE_KEY = "notice"
        private const val USER_AGENT = "DIMA-Now/1.0 personal Android app"
    }
}
