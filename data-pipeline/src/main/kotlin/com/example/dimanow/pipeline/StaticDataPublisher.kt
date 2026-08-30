package com.example.dimanow.pipeline

import com.example.dimanow.sync.CampusDataManifest
import com.example.dimanow.sync.DatasetDescriptor
import com.example.dimanow.sync.ShuttlePayload
import com.example.dimanow.sync.MealPayload
import com.example.dimanow.sync.NoticePayload
import com.example.dimanow.sync.DormitoryMealPayload
import com.example.dimanow.sync.DormitoryMealSubmissionStatus
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.DayOfWeek
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

    fun publishDormitoryMeal(payload: DormitoryMealPayload, revision: Long, publishedAt: Instant) {
        publish(
            "dorm_meal",
            "dorm-meal",
            json.encodeToString(payload).toByteArray(),
            revision,
            publishedAt,
            DORMITORY_SOURCE_URL,
        )
    }

    fun publishDormitorySubmissionStatus(status: DormitoryMealSubmissionStatus) {
        require(status.submissionId.matches(Regex("[A-Za-z0-9_-]{1,64}"))) { "잘못된 제출 ID입니다." }
        val directory = outputRoot.resolve("data/v1/dorm-submissions")
        Files.createDirectories(directory)
        val target = directory.resolve("${status.submissionId}.json")
        val temporary = directory.resolve("${status.submissionId}.json.tmp")
        Files.writeString(temporary, json.encodeToString(status))
        runCatching { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE) }
            .getOrElse { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING) }
    }

    fun hasCurrentDormitoryMeal(today: LocalDate): Boolean {
        val descriptor = readManifest()?.datasets?.get("dorm_meal")?.takeIf { it.state == "READY" } ?: return false
        val payloadPath = outputRoot.resolve("data/v1").resolve(descriptor.url)
        if (!Files.exists(payloadPath)) return false
        val payload = runCatching { json.decodeFromString<DormitoryMealPayload>(Files.readString(payloadPath)) }.getOrNull() ?: return false
        val targetWeekStart = when (today.dayOfWeek) {
            DayOfWeek.SATURDAY, DayOfWeek.SUNDAY -> today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
            else -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        }
        return LocalDate.parse(payload.weekStart) == targetWeekStart
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

    private companion object {
        const val DORMITORY_SOURCE_URL = "https://github.com/winter1l/DimaNow/tree/dorm-submissions/dorm-submissions"
    }
}

internal fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(this)
    .joinToString("") { "%02x".format(it) }
