package com.example.dimanow.pipeline

import com.example.dimanow.sync.DormitoryMealSubmissionStatus
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * One image on the `dorm-submissions` branch, identified by the latest commit that touched it.
 * [commitSha] makes the published source URL immutable; [committedAt] orders the backlog oldest first.
 */
data class PendingDormitorySubmission(val commitSha: String, val committedAt: Instant, val path: String) {
    init {
        require(commitSha.matches(COMMIT_SHA_PATTERN)) { "잘못된 제출 커밋입니다." }
        require(path.matches(SUBMISSION_PATH_PATTERN)) { "잘못된 제출 경로입니다." }
    }

    val submissionId: String get() = path.substringAfterLast('/').substringBeforeLast('.')

    val mimeType: String get() = when (path.substringAfterLast('.')) {
        "png" -> "image/png"
        "webp" -> "image/webp"
        else -> "image/jpeg"
    }

    val sourceImageUrl: String
        get() = "https://raw.githubusercontent.com/${StaticDataPublisher.DORMITORY_REPOSITORY}/$commitSha/$path"

    companion object {
        private val COMMIT_SHA_PATTERN = Regex("[0-9a-f]{40}")
        private val SUBMISSION_PATH_PATTERN = Regex("dorm-submissions/[A-Za-z0-9_-]{1,64}\\.(?:jpg|jpeg|png|webp)")
        private val EPOCH_SECONDS_PATTERN = Regex("[0-9]{1,12}")

        /**
         * Parses the workflow's `<commit-sha> <commit-epoch-seconds> <path>` lines. Lines that do not match the exact
         * shape are skipped and reported, so one malformed entry cannot stop the rest of the backlog.
         */
        fun parseIndex(text: String, onInvalid: (String) -> Unit = {}): List<PendingDormitorySubmission> =
            text.lineSequence().map(String::trim).filter(String::isNotEmpty).mapNotNull { line ->
                val parts = line.split(' ')
                runCatching {
                    require(parts.size == 3 && parts[1].matches(EPOCH_SECONDS_PATTERN))
                    PendingDormitorySubmission(parts[0], Instant.ofEpochSecond(parts[1].toLong()), parts[2])
                }.getOrElse {
                    onInvalid(line)
                    null
                }
            }.toList()
    }
}

class DormitoryMealSubmissionProcessor(
    private val publisher: StaticDataPublisher,
    private val geminiClient: GeminiDormitoryMealClient,
    private val clock: Clock = Clock.system(ZoneId.of("Asia/Seoul")),
) {
    /**
     * Validates, transcribes and automatically publishes one dormitory meal photo (D-070 operator approval reverted).
     * Only a submission that passes both Gemini stages, payload validation, the content limits, the single KST
     * target-week rule and the duplicate current-week check becomes the new `dorm_meal` revision; everything else
     * publishes a short status. A submission that already has a final status is returned unchanged, so the legacy
     * single-image workflow and the backlog sweep can both see the same image without overwriting PUBLISHED.
     */
    fun process(
        imagePath: Path,
        mimeType: String,
        sourceImageUrl: String,
        submissionId: String,
    ): DormitoryMealSubmissionStatus {
        publisher.readDormitorySubmissionStatus(submissionId)?.takeIf { it.state in FINAL_STATES }?.let { return it }
        val now = clock.instant()
        val today = now.atZone(clock.zone).toLocalDate()
        if (publisher.hasCurrentDormitoryMeal(today)) {
            return publishStatus(submissionId, "DUPLICATE", "이번 주 기숙사 식단이 이미 등록되어 있어요")
        }
        publisher.requireValidDormitorySubmission(submissionId, sourceImageUrl)
        require(Files.isRegularFile(imagePath, LinkOption.NOFOLLOW_LINKS)) { "식단 이미지 파일이 아닙니다." }
        require(Files.size(imagePath) in 1..MAX_IMAGE_BYTES.toLong()) { "식단 이미지가 너무 큽니다." }
        val imageBytes = Files.readAllBytes(imagePath)
        return when (val analysis = geminiClient.analyze(imageBytes, mimeType)) {
            is DormitoryMealAnalysis.Rejected -> publishStatus(
                submissionId,
                "REJECTED",
                DormitoryMealContentPolicy.sanitizeRejectionReason(analysis.reason),
            )
            is DormitoryMealAnalysis.Accepted -> {
                val payload = try {
                    GeminiDormitoryMealPayloadBuilder().build(
                        responseJson = analysis.responseJson,
                        referenceDate = today,
                        sourceImageUrl = sourceImageUrl,
                    )
                } catch (error: DormitoryMealWeekMismatchException) {
                    return publishStatus(submissionId, "REJECTED", error.message)
                } catch (_: DormitoryMealContentException) {
                    return publishStatus(submissionId, "REJECTED", DormitoryMealContentPolicy.REJECTION_REASON)
                } catch (_: IllegalArgumentException) {
                    return publishStatus(submissionId, "REJECTED", RETAKE_REASON)
                }
                publisher.publishDormitoryMealSubmission(
                    payload = payload,
                    submissionId = submissionId,
                    publishedAt = now,
                )
            }
        }
    }

    /**
     * Processes the `dorm-submissions` backlog: every listed image without a published status, oldest first, at most
     * [limit] per run. A run cancelled while pending (shared concurrency group) therefore loses nothing; the next run
     * picks the image up. An image committed for an earlier target week is rejected without a Gemini call. One failing
     * image records ERROR and does not stop the others.
     */
    fun processPending(
        submissionRoot: Path,
        submissions: List<PendingDormitorySubmission>,
        limit: Int = MAX_SUBMISSIONS_PER_RUN,
    ): List<DormitoryMealSubmissionStatus> {
        require(limit > 0) { "처리 한도는 1 이상이어야 합니다." }
        val root = submissionRoot.toAbsolutePath().normalize()
        val currentWeek = dormitoryMealTargetWeekStart(clock.instant().atZone(clock.zone).toLocalDate())
        return submissions
            .sortedWith(compareBy({ it.committedAt }, { it.commitSha }, { it.path }))
            .distinctBy { it.submissionId }
            .filterNot { publisher.hasDormitorySubmissionStatus(it.submissionId) }
            .take(limit)
            .map { submission ->
                val submittedWeek = dormitoryMealTargetWeekStart(submission.committedAt.atZone(clock.zone).toLocalDate())
                if (submittedWeek != currentWeek) {
                    return@map publishStatus(submission.submissionId, "REJECTED", STALE_SUBMISSION_REASON)
                }
                runCatching {
                    val imagePath = root.resolve(submission.path).normalize()
                    require(imagePath.startsWith(root)) { "제출 경로가 작업 디렉터리를 벗어났습니다." }
                    process(imagePath, submission.mimeType, submission.sourceImageUrl, submission.submissionId)
                }.getOrElse { error ->
                    System.err.println("기숙사 식단 처리 실패 ${submission.submissionId}: ${error.javaClass.simpleName}")
                    publishStatus(submission.submissionId, "ERROR", PROCESSING_ERROR_REASON)
                }
            }
    }

    private fun publishStatus(submissionId: String, state: String, message: String?): DormitoryMealSubmissionStatus =
        DormitoryMealSubmissionStatus(submissionId, state, message, clock.instant().toString()).also {
            publisher.publishDormitorySubmissionStatus(it)
        }

    companion object {
        const val MAX_SUBMISSIONS_PER_RUN = 10
        const val PROCESSING_ERROR_REASON = "식단 사진을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요."
        const val STALE_SUBMISSION_REASON = "제출 기간이 지난 식단 사진이에요"
        private const val RETAKE_REASON = "식단표의 날짜와 메뉴가 잘 보이도록 다시 촬영해 주세요"
        private const val MAX_IMAGE_BYTES = 15 * 1024 * 1024
        private val FINAL_STATES = setOf("PUBLISHED", "REJECTED", "DUPLICATE")
    }
}
