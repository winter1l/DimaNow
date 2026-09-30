package com.example.dimanow.pipeline

import com.example.dimanow.sync.DormitoryMealSubmissionStatus
import java.nio.file.Path
import java.nio.file.Files
import java.time.Clock
import java.time.ZoneId

class DormitoryMealSubmissionProcessor(
    private val publisher: StaticDataPublisher,
    private val geminiClient: GeminiDormitoryMealClient,
    private val clock: Clock = Clock.system(ZoneId.of("Asia/Seoul")),
) {
    /**
     * Validates, transcribes and automatically publishes one dormitory meal photo (D-070 operator approval reverted).
     * Only a submission that passes both Gemini stages, payload validation, the single KST target-week rule and the
     * duplicate current-week check becomes the new `dorm_meal` revision; everything else publishes a short status.
     */
    fun process(
        imagePath: Path,
        mimeType: String,
        sourceImageUrl: String,
        submissionId: String,
    ): DormitoryMealSubmissionStatus {
        val now = clock.instant()
        val today = now.atZone(clock.zone).toLocalDate()
        if (publisher.hasCurrentDormitoryMeal(today)) {
            return publishStatus(submissionId, "DUPLICATE", "이번 주 기숙사 식단이 이미 등록되어 있어요")
        }
        publisher.requireValidDormitorySubmission(submissionId, sourceImageUrl)
        require(Files.size(imagePath) in 1..MAX_IMAGE_BYTES.toLong()) { "식단 이미지가 너무 큽니다." }
        val imageBytes = Files.readAllBytes(imagePath)
        return when (val analysis = geminiClient.analyze(imageBytes, mimeType)) {
            is DormitoryMealAnalysis.Rejected -> publishStatus(submissionId, "REJECTED", analysis.reason)
            is DormitoryMealAnalysis.Accepted -> {
                val payload = try {
                    GeminiDormitoryMealPayloadBuilder().build(
                        responseJson = analysis.responseJson,
                        referenceDate = today,
                        sourceImageUrl = sourceImageUrl,
                    )
                } catch (error: DormitoryMealWeekMismatchException) {
                    return publishStatus(submissionId, "REJECTED", error.message)
                } catch (_: IllegalArgumentException) {
                    return publishStatus(submissionId, "REJECTED", "식단표의 날짜와 메뉴가 잘 보이도록 다시 촬영해 주세요")
                }
                publisher.publishDormitoryMealSubmission(
                    payload = payload,
                    submissionId = submissionId,
                    publishedAt = now,
                )
            }
        }
    }

    private fun publishStatus(submissionId: String, state: String, message: String?): DormitoryMealSubmissionStatus =
        DormitoryMealSubmissionStatus(submissionId, state, message, clock.instant().toString()).also {
            publisher.publishDormitorySubmissionStatus(it)
        }

    private companion object {
        const val MAX_IMAGE_BYTES = 15 * 1024 * 1024
    }
}
