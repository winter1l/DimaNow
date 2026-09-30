package com.example.dimanow.pipeline

import com.example.dimanow.sync.CampusDataManifest
import com.example.dimanow.sync.DatasetDescriptor
import com.example.dimanow.sync.ShuttlePayload
import com.example.dimanow.sync.MealPayload
import com.example.dimanow.sync.isValidStudentMealMenu
import com.example.dimanow.sync.NoticePayload
import com.example.dimanow.sync.DormitoryMealPayload
import com.example.dimanow.sync.DormitoryMealSubmissionStatus
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.net.URI
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class StaticDataPublisher(private val outputRoot: Path) {
    private val json = Json { prettyPrint = true }

    fun publishShuttle(csv: String, revision: Long, publishedAt: Instant) {
        val payloadBytes = json.encodeToString(ShuttlePayload(departures = ShuttleScheduleCsv.parse(csv))).toByteArray()
        publish("shuttle", "shuttle", payloadBytes, revision, publishedAt, "https://www.dima.ac.kr/?p=97")
    }

    fun publishMeal(payload: MealPayload, revision: Long, publishedAt: Instant) {
        publish("meal", "meal", json.encodeToString(payload).toByteArray(), revision, publishedAt, "https://www.dima.ac.kr/?p=1")
    }

    /** Scheduled retries stop for a complete week; explicit publication can still import corrections. */
    fun shouldCollectStudentMeal(today: LocalDate): Boolean = !runCatching {
        val descriptor = readManifest()?.datasets?.get("meal") ?: return@runCatching false
        if (descriptor.state != "READY" || !descriptor.url.matches(Regex("meal/[0-9a-f]{64}\\.json"))) return@runCatching false
        val bytes = Files.readAllBytes(outputRoot.resolve("data/v1").resolve(descriptor.url))
        if (bytes.sha256() != descriptor.sha256) return@runCatching false
        val payload = json.decodeFromString<MealPayload>(bytes.decodeToString())
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        payload.schemaVersion == 1 && payload.weekStart == monday.toString() &&
            payload.weekEnd == monday.plusDays(6).toString() &&
            payload.days.map { it.date }.sorted() == (0L..4L).map { monday.plusDays(it).toString() } &&
            payload.days.all { isValidStudentMealMenu(it.menuLines) }
    }.getOrDefault(false)

    /**
     * Publishes a validated OCR payload as the next `dorm_meal` revision and records the submission as PUBLISHED.
     * The payload must belong to the single KST target week at [publishedAt] and cite the submission's immutable
     * GitHub source image; anything else fails closed without touching the last-good payload.
     */
    fun publishDormitoryMealSubmission(
        payload: DormitoryMealPayload,
        submissionId: String,
        publishedAt: Instant,
    ): DormitoryMealSubmissionStatus {
        requireValidDormitorySubmission(submissionId, payload.sourceImageUrl)
        require(payload.schemaVersion == 1) { "지원하지 않는 기숙사 식단 형식입니다." }
        val weekStart = dormitoryMealTargetWeekStart(publishedAt.atZone(KST).toLocalDate())
        require(runCatching { LocalDate.parse(payload.weekStart) }.getOrNull() == weekStart) {
            "현재 게시 대상 주차의 기숙사 식단이 아닙니다."
        }
        require(runCatching { LocalDate.parse(payload.weekEnd) }.getOrNull() == weekStart.plusDays(6)) {
            "기숙사 식단의 주차 범위가 올바르지 않습니다."
        }
        require(
            payload.days.isNotEmpty() &&
                payload.days.all { day ->
                    runCatching { LocalDate.parse(day.date) }.getOrNull()?.let { it in weekStart..weekStart.plusDays(6) } == true
                },
        ) { "기숙사 식단 날짜가 게시 주차를 벗어났습니다." }
        publish(
            "dorm_meal",
            "dorm-meal",
            json.encodeToString(payload).toByteArray(),
            nextRevision("dorm_meal"),
            publishedAt,
            DORMITORY_SOURCE_URL,
        )
        return DormitoryMealSubmissionStatus(
            submissionId = submissionId,
            state = "PUBLISHED",
            message = null,
            updatedAt = publishedAt.toString(),
        ).also(::writeDormitorySubmissionStatus)
    }

    /** Rejects a submission ID or source image URL that is not the exact immutable `dorm-submissions` commit path. */
    fun requireValidDormitorySubmission(submissionId: String, sourceImageUrl: String) {
        requireValidSubmissionId(submissionId)
        requireValidDormitorySourceImageUrl(sourceImageUrl, submissionId)
    }

    fun publishDormitorySubmissionStatus(status: DormitoryMealSubmissionStatus) {
        // PUBLISHED is written only by publishDormitoryMealSubmission, after the payload is actually live.
        require(status.state in setOf("DUPLICATE", "REJECTED", "ERROR")) {
            "허용되지 않은 제출 상태입니다."
        }
        writeDormitorySubmissionStatus(status)
    }

    private fun writeDormitorySubmissionStatus(status: DormitoryMealSubmissionStatus) {
        requireValidSubmissionId(status.submissionId)
        val directory = outputRoot.resolve("data/v1/dorm-submissions")
        Files.createDirectories(directory)
        val target = directory.resolve("${status.submissionId}.json")
        writeAtomically(target, json.encodeToString(status).toByteArray())
    }

    fun hasCurrentDormitoryMeal(today: LocalDate): Boolean {
        val descriptor = readManifest()?.datasets?.get("dorm_meal")?.takeIf { it.state == "READY" } ?: return false
        val payloadPath = outputRoot.resolve("data/v1").resolve(descriptor.url)
        if (!Files.exists(payloadPath)) return false
        val payload = runCatching { json.decodeFromString<DormitoryMealPayload>(Files.readString(payloadPath)) }.getOrNull() ?: return false
        return LocalDate.parse(payload.weekStart) == dormitoryMealTargetWeekStart(today)
    }

    fun publishNotices(payload: NoticePayload, revision: Long, publishedAt: Instant) {
        publish("notice", "notices", json.encodeToString(payload).toByteArray(), revision, publishedAt, "https://www.dima.ac.kr/?p=111")
    }

    fun recordFailure(dataset: String, state: String, message: String, attemptedAt: Instant) {
        require(state in setOf("WAITING", "NEEDS_REVIEW", "ERROR")) { "잘못된 데이터 상태입니다." }
        val current = readManifest()
        val previous = current?.datasets?.get(dataset)
        val sourceUrl = previous?.sourceUrl ?: when (dataset) {
            "shuttle" -> "https://www.dima.ac.kr/?p=97"
            "meal" -> "https://www.dima.ac.kr/?p=1"
            "notice" -> "https://www.dima.ac.kr/?p=111"
            "dorm_meal" -> DORMITORY_SOURCE_URL
            else -> error("알 수 없는 데이터셋입니다.")
        }
        val descriptor = (previous ?: DatasetDescriptor(0, state, attemptedAt.toString(), attemptedAt.toString(), "", "", sourceUrl))
            .copy(state = state, lastAttemptAt = attemptedAt.toString(), message = message)
        writeManifest(CampusDataManifest(1, attemptedAt.toString(), current.orEmptyDatasets() + (dataset to descriptor)))
    }

    fun nextRevision(dataset: String): Long = (readManifest()?.datasets?.get(dataset)?.revision ?: 0L) + 1L

    private fun publish(
        dataset: String,
        directory: String,
        payloadBytes: ByteArray,
        revision: Long,
        publishedAt: Instant,
        sourceUrl: String,
    ) {
        require(revision > 0) { "revision은 1 이상이어야 합니다." }
        val dataRoot = outputRoot.resolve("data/v1")
        Files.createDirectories(dataRoot.resolve(directory))
        val hash = payloadBytes.sha256()
        val relativeUrl = "$directory/$hash.json"
        Files.write(dataRoot.resolve(relativeUrl), payloadBytes)
        val timestamp = publishedAt.toString()
        val current = readManifest()
        val previous = current?.datasets?.get(dataset)
        val unchanged = previous?.sha256 == hash && previous.url == relativeUrl
        val descriptor = DatasetDescriptor(
            revision = if (unchanged) previous.revision else revision,
            state = "READY",
            publishedAt = if (unchanged) previous.publishedAt else timestamp,
            lastAttemptAt = timestamp,
            url = relativeUrl,
            sha256 = hash,
            sourceUrl = sourceUrl,
        )
        writeManifest(CampusDataManifest(1, timestamp, current.orEmptyDatasets() + (dataset to descriptor)))
    }

    private fun CampusDataManifest?.orEmptyDatasets(): Map<String, DatasetDescriptor> = this?.datasets.orEmpty()

    private fun readManifest(): CampusDataManifest? {
        val path = outputRoot.resolve("data/v1/manifest.json")
        return if (Files.exists(path)) json.decodeFromString<CampusDataManifest>(Files.readString(path)) else null
    }

    private fun writeManifest(manifest: CampusDataManifest) {
        val dataRoot = outputRoot.resolve("data/v1")
        Files.createDirectories(dataRoot)
        val target = dataRoot.resolve("manifest.json")
        val temporary = dataRoot.resolve("manifest.json.tmp")
        Files.writeString(temporary, json.encodeToString(manifest))
        runCatching { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE) }
            .getOrElse { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING) }
        Files.writeString(
            outputRoot.resolve("index.html"),
            """<!doctype html><meta charset="utf-8"><title>DIMA Now 데이터</title><h1>DIMA Now 데이터</h1><p>동아방송예술대학교의 공식 앱이 아닙니다.</p><p><a href="data/v1/manifest.json">manifest.json</a></p>""",
        )
    }

    private fun writeAtomically(target: Path, bytes: ByteArray) {
        val temporary = target.resolveSibling("${target.fileName}.tmp")
        Files.write(temporary, bytes)
        runCatching { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE) }
            .getOrElse { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING) }
    }

    private fun requireValidSubmissionId(submissionId: String) {
        require(submissionId.matches(SUBMISSION_ID_PATTERN)) { "잘못된 제출 ID입니다." }
    }

    private fun requireValidDormitorySourceImageUrl(value: String, submissionId: String) {
        val valid = runCatching {
            val uri = URI.create(value)
            uri.scheme.equals("https", ignoreCase = true) &&
                uri.host.equals("raw.githubusercontent.com", ignoreCase = true) &&
                uri.userInfo == null && uri.port == -1 && uri.rawQuery == null && uri.rawFragment == null &&
                uri.rawPath.matches(
                    Regex("^/winter1l/DimaNow/[0-9a-f]{40}/dorm-submissions/${Regex.escape(submissionId)}\\.(?:jpg|jpeg|png|webp)$"),
                )
        }.getOrDefault(false)
        require(valid) { "제출 이미지의 불변 GitHub 원본 주소가 올바르지 않습니다." }
    }

    private companion object {
        const val DORMITORY_SOURCE_URL = "https://github.com/winter1l/DimaNow/tree/dorm-submissions/dorm-submissions"
        val SUBMISSION_ID_PATTERN = Regex("[A-Za-z0-9_-]{1,64}")
        val KST: ZoneId = ZoneId.of("Asia/Seoul")
    }
}

internal fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(this)
    .joinToString("") { "%02x".format(it) }
