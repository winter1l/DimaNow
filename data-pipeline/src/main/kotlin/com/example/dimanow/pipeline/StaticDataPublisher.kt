package com.example.dimanow.pipeline

import com.example.dimanow.sync.CampusDataManifest
import com.example.dimanow.sync.DatasetDescriptor
import com.example.dimanow.sync.ShuttlePayload
import com.example.dimanow.sync.MealPayload
import com.example.dimanow.sync.isValidStudentMealMenu
import com.example.dimanow.sync.NoticePayload
import com.example.dimanow.sync.DormitoryMealPayload
import com.example.dimanow.sync.DormitoryMealSubmissionStatus
import com.example.dimanow.sync.ServiceCalendarPayload
import com.example.dimanow.sync.serviceCalendarProblem
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

class StaticDataPublisher(private val outputRoot: Path) {
    private val json = Json { prettyPrint = true }

    fun publishShuttle(csv: String, revision: Long, publishedAt: Instant) {
        val payloadBytes = json.encodeToString(ShuttlePayload(departures = ShuttleScheduleCsv.parse(csv))).toByteArray()
        publish("shuttle", "shuttle", payloadBytes, revision, publishedAt, "https://www.dima.ac.kr/?p=97")
    }

    /** Validates the admin-maintained `data-source/calendar.json` and publishes it as the `calendar` dataset. */
    fun publishCalendar(source: String, revision: Long, publishedAt: Instant) {
        val payload = Json.decodeFromString<ServiceCalendarPayload>(source)
        serviceCalendarProblem(payload)?.let { throw IllegalArgumentException(it) }
        publish("calendar", "calendar", json.encodeToString(payload).toByteArray(), revision, publishedAt, CALENDAR_SOURCE_URL)
    }

    /**
     * Publishes the latest stable GitHub release as the `app_release` dataset (D-099 clause 8), so installed apps
     * check for updates through GitHub Pages instead of the rate-limited unauthenticated GitHub API.
     * The payload keeps the GitHub release shape (only the fields the app's parser reads) and is validated with the
     * same naming rules the app applies.
     */
    fun publishAppRelease(releaseJson: String, revision: Long, publishedAt: Instant) {
        val root = Json.parseToJsonElement(releaseJson).jsonObject
        fun text(obj: JsonObject, key: String) = obj[key]?.jsonPrimitive?.contentOrNull ?: error("릴리스 응답에 $key 값이 없습니다.")
        val tag = text(root, "tag_name")
        require(tag.matches(Regex("""v[0-9]+\.[0-9]+(?:\.[0-9]+)?"""))) { "지원하지 않는 릴리스 태그입니다: $tag" }
        require(root["draft"]?.jsonPrimitive?.booleanOrNull == false && root["prerelease"]?.jsonPrimitive?.booleanOrNull == false) {
            "안정 릴리스가 아닙니다."
        }
        val htmlUrl = text(root, "html_url")
        require(htmlUrl.startsWith("https://github.com/$DORMITORY_REPOSITORY/releases/")) { "허용되지 않은 릴리스 주소입니다." }
        val assetName = "DIMA-Now-$tag-optimized.apk"
        val asset = root["assets"]?.jsonArray.orEmpty().map { it.jsonObject }.singleOrNull { text(it, "name") == assetName }
            ?: error("$assetName asset이 하나 있어야 합니다.")
        val digest = text(asset, "digest")
        require(digest.matches(Regex("sha256:[0-9a-fA-F]{64}"))) { "asset SHA-256이 없습니다." }
        val downloadUrl = text(asset, "browser_download_url")
        require(downloadUrl.startsWith("https://github.com/$DORMITORY_REPOSITORY/releases/download/$tag/")) { "허용되지 않은 다운로드 주소입니다." }
        val size = asset["size"]?.jsonPrimitive?.longOrNull ?: error("asset 크기가 없습니다.")
        require(size in 1..128L * 1024 * 1024) { "asset 크기가 올바르지 않습니다." }
        val trimmed = buildJsonObject {
            put("tag_name", tag)
            put("draft", false)
            put("prerelease", false)
            put("html_url", htmlUrl)
            root["published_at"]?.jsonPrimitive?.contentOrNull?.let { put("published_at", it) }
            put("assets", buildJsonArray {
                add(buildJsonObject {
                    put("name", assetName)
                    put("size", size)
                    put("browser_download_url", downloadUrl)
                    put("digest", digest.lowercase())
                })
            })
        }
        publish("app_release", "app-release", json.encodeToString(JsonObject.serializer(), trimmed).toByteArray(), revision, publishedAt, RELEASES_SOURCE_URL)
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
        // Automatic publication (D-089) has no human reviewer: the content limits are enforced again at the boundary.
        DormitoryMealContentPolicy.requireAllowed(payload)
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

    /** The status already published for [submissionId], or null when it has none (or an unreadable one). */
    fun readDormitorySubmissionStatus(submissionId: String): DormitoryMealSubmissionStatus? {
        requireValidSubmissionId(submissionId)
        val path = outputRoot.resolve("data/v1/dorm-submissions/$submissionId.json")
        if (!Files.exists(path)) return null
        return runCatching { json.decodeFromString<DormitoryMealSubmissionStatus>(Files.readString(path)) }.getOrNull()
    }

    fun hasDormitorySubmissionStatus(submissionId: String): Boolean {
        requireValidSubmissionId(submissionId)
        return Files.exists(outputRoot.resolve("data/v1/dorm-submissions/$submissionId.json"))
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

    /**
     * Owner take-down after a report (D-099 clause 10): marks the dormitory dataset REVOKED so apps drop the cached
     * week and uploads reopen. The payload file stays in the data branch history.
     */
    fun revokeDormitoryMeal(at: Instant) {
        require(readManifest()?.datasets?.get("dorm_meal")?.state == "READY") { "내릴 기숙사 식단이 없습니다." }
        recordFailure("dorm_meal", "REVOKED", REVOKED_MESSAGE, at)
    }

    fun publishNotices(payload: NoticePayload, revision: Long, publishedAt: Instant) {
        publish("notice", "notices", json.encodeToString(payload).toByteArray(), revision, publishedAt, "https://www.dima.ac.kr/?p=111")
    }

    fun recordFailure(dataset: String, state: String, message: String, attemptedAt: Instant) {
        require(state in setOf("WAITING", "NEEDS_REVIEW", "ERROR", "REVOKED")) { "잘못된 데이터 상태입니다." }
        val current = readManifest()
        val previous = current?.datasets?.get(dataset)
        val sourceUrl = previous?.sourceUrl ?: when (dataset) {
            "shuttle" -> "https://www.dima.ac.kr/?p=97"
            "meal" -> "https://www.dima.ac.kr/?p=1"
            "notice" -> "https://www.dima.ac.kr/?p=111"
            "dorm_meal" -> DORMITORY_SOURCE_URL
            "calendar" -> CALENDAR_SOURCE_URL
            "app_release" -> RELEASES_SOURCE_URL
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

    internal companion object {
        /** The only repository whose immutable `dorm-submissions` commits may be cited as a dormitory source image. */
        const val DORMITORY_REPOSITORY = "winter1l/DimaNow"
        const val REVOKED_MESSAGE = "신고를 확인해 이번 주 식단을 내렸어요. 새 사진을 올려 주세요"
        const val RELEASES_SOURCE_URL = "https://github.com/winter1l/DimaNow/releases"
        const val CALENDAR_SOURCE_URL = "https://www.dima.ac.kr/?p=78"
        const val DORMITORY_SOURCE_URL = "https://github.com/winter1l/DimaNow/tree/dorm-submissions/dorm-submissions"
        val SUBMISSION_ID_PATTERN = Regex("[A-Za-z0-9_-]{1,64}")
        val KST: ZoneId = ZoneId.of("Asia/Seoul")
    }
}

internal fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(this)
    .joinToString("") { "%02x".format(it) }
