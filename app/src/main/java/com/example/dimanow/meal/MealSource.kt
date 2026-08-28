package com.example.dimanow.meal

import android.content.Context
import android.graphics.Rect
import androidx.core.net.toUri
import androidx.room.withTransaction
import com.example.dimanow.data.DimaDatabase
import com.example.dimanow.data.MealDayEntity
import com.example.dimanow.data.SourceStatusEntity
import com.example.dimanow.domain.MealDay
import com.example.dimanow.domain.MealValidationState
import com.example.dimanow.time.MinuteTicker
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import java.io.File
import java.net.URL
import java.time.DayOfWeek
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

data class MealData(
    val days: List<MealDay>,
    val lastSuccess: Instant?,
    val lastAttempt: Instant?,
    val error: String?,
    val sourceUrl: String,
    val sourceImageUrl: String?,
    val hours: String?,
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
    data class NeedsReview(val reason: String, val sourceImageUrl: String?) : MealRefreshResult
    data class Failure(val message: String) : MealRefreshResult
}

interface MealSource {
    val data: Flow<MealData>
    suspend fun refresh(): MealRefreshResult
}

class OfficialMealSource(
    private val context: Context,
    private val database: DimaDatabase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: Clock = Clock.system(MinuteTicker.CAMPUS_ZONE),
) : MealSource {
    private val dao = database.scheduleDao()
    private val refreshMutex = Mutex()

    override val data: Flow<MealData> = combine(
        dao.observeMealDays(),
        dao.observeSourceStatus(SOURCE_KEY),
    ) { days, status ->
        MealData(
            days = days.map { it.toDomain() },
            lastSuccess = status?.lastSuccessEpochMillis?.let(Instant::ofEpochMilli),
            lastAttempt = status?.lastAttemptEpochMillis?.let(Instant::ofEpochMilli),
            error = status?.error,
            sourceUrl = status?.sourceUrl ?: DISCOVERY_URL,
            sourceImageUrl = status?.candidateImageUrl,
            hours = status?.hours,
        )
    }

    override suspend fun refresh(): MealRefreshResult = refreshMutex.withLock { withContext(ioDispatcher) {
        val attempt = Instant.now()
        var post: MealPost? = null
        var imageUrl: String? = null
        try {
            val discoveryHtml = Jsoup.connect(DISCOVERY_URL).userAgent(USER_AGENT).get().outerHtml()
            post = MealDiscoveryParser().parse(discoveryHtml) ?: discoverFromOfficialProfile()
                ?: error("[DIMA 학생식당] 게시물을 찾지 못했습니다.")
            val embedHtml = Jsoup.connect(post.embedUrl).userAgent(USER_AGENT).get().outerHtml()
            imageUrl = InstagramCarouselParser().parse(embedHtml).mealTableImageUrl
                ?: error("식단표 캐러셀 이미지를 찾지 못했습니다.")
            val imageFile = downloadCandidate(imageUrl)
            val recognized = recognize(imageFile)
            val validation = MealWeekValidator().validate(recognized, MealRefreshClock.today(clock))
            when (validation) {
                is MealValidationResult.Valid -> {
                    acceptValid(validation, post, imageUrl, attempt)
                    MealRefreshResult.Success(validation.weekStart, attempt)
                }
                is MealValidationResult.Invalid -> {
                    recordFailure("메뉴 확인 필요: ${validation.reason}", post, imageUrl, attempt)
                    MealRefreshResult.NeedsReview(validation.reason, imageUrl)
                }
            }
        } catch (error: Exception) {
            recordFailure(error.message ?: error.javaClass.simpleName, post, imageUrl, attempt)
            MealRefreshResult.Failure(error.message ?: "식단 새로고침 실패")
        }
    } }

    internal suspend fun acceptValid(validation: MealValidationResult.Valid, post: MealPost, imageUrl: String, attempt: Instant) {
        val entities = validation.days.map { (date, lines) ->
            MealDayEntity(
                epochDay = date.toEpochDay(),
                menuText = lines.joinToString("\n"),
                hours = post.hours,
                sourceUrl = post.sourceUrl,
                sourceImageUrl = imageUrl,
                validationState = MealValidationState.VALID.name,
            )
        }
        database.withTransaction {
            dao.deleteMealWeek(validation.weekStart.toEpochDay(), validation.weekStart.plusDays(6).toEpochDay())
            dao.putMealDays(entities)
            dao.putSourceStatus(
                SourceStatusEntity(SOURCE_KEY, attempt.toEpochMilli(), attempt.toEpochMilli(), null, post.sourceUrl, candidateImageUrl = imageUrl, hours = post.hours),
            )
        }
    }

    internal suspend fun recordFailure(message: String, post: MealPost?, imageUrl: String?, attempt: Instant) {
        val previous = dao.sourceStatus(SOURCE_KEY)
        dao.putSourceStatus(
            SourceStatusEntity(
                source = SOURCE_KEY,
                lastSuccessEpochMillis = previous?.lastSuccessEpochMillis,
                lastAttemptEpochMillis = attempt.toEpochMilli(),
                error = message,
                sourceUrl = post?.sourceUrl ?: previous?.sourceUrl ?: DISCOVERY_URL,
                candidateImageUrl = imageUrl ?: previous?.candidateImageUrl,
                hours = post?.hours ?: previous?.hours,
            ),
        )
    }

    private fun downloadCandidate(imageUrl: String): File {
        val file = File(context.cacheDir, "meal-candidate.jpg")
        URL(imageUrl).openConnection().apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", USER_AGENT)
        }.getInputStream().use { input -> file.outputStream().use(input::copyTo) }
        return file
    }

    private fun discoverFromOfficialProfile(): MealPost? {
        val payload = Jsoup.connect(PROFILE_FEED_URL)
            .ignoreContentType(true)
            .userAgent(USER_AGENT)
            .header("X-IG-App-ID", INSTAGRAM_PUBLIC_APP_ID)
            .execute()
            .body()
        return MealProfileFeedParser().parse(payload)
    }

    private suspend fun recognize(file: File): List<OcrLine> {
        val recognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
        return try {
            val text = recognizer.process(InputImage.fromFilePath(context, file.toUri())).await()
            text.textBlocks.flatMap(Text.TextBlock::getLines).mapNotNull { line ->
                line.boundingBox?.let { box -> OcrLine(line.text, box.left, box.top, box.right, box.bottom) }
            }
        } finally {
            recognizer.close()
        }
    }

    companion object {
        const val DISCOVERY_URL = "https://www.dima.ac.kr/?p=1"
        const val PROFILE_FEED_URL = "https://www.instagram.com/api/v1/feed/user/30891067635/?count=12"
        private const val SOURCE_KEY = "meal"
        private const val USER_AGENT = "DIMA-Now/1.0 personal Android app"
        private const val INSTAGRAM_PUBLIC_APP_ID = "936619743392459"
    }
}
