package com.example.dimanow.pipeline

import com.example.dimanow.sync.DormitoryMealSubmissionStatus
import java.nio.file.Path
import java.nio.file.Files
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

class DormitoryMealSubmissionProcessor(
    private val publisher: StaticDataPublisher,
    private val geminiClient: GeminiDormitoryMealClient,
    private val clock: Clock = Clock.system(ZoneId.of("Asia/Seoul")),
) {
    fun process(
        imagePath: Path,
        mimeType: String,
        sourceImageUrl: String,
        submissionId: String,
    ): DormitoryMealSubmissionStatus {
        if (publisher.hasCurrentDormitoryMeal(LocalDate.now(clock))) {
            return publishStatus(submissionId, "DUPLICATE", "이번 주 기숙사 식단이 이미 등록되어 있어요")
        }
        require(sourceImageUrl.startsWith("https://")) { "제출 이미지 주소가 HTTPS가 아닙니다." }
        require(Files.size(imagePath) in 1..MAX_IMAGE_BYTES.toLong()) { "식단 이미지가 너무 큽니다." }
        val imageBytes = Files.readAllBytes(imagePath)
        return when (val analysis = geminiClient.analyze(imageBytes, mimeType)) {
            is DormitoryMealAnalysis.Rejected -> publishStatus(submissionId, "REJECTED", analysis.reason)
            is DormitoryMealAnalysis.Accepted -> {
                val payload = try {
                    GeminiDormitoryMealPayloadBuilder().build(
                        responseJson = analysis.responseJson,
                        referenceDate = LocalDate.now(clock),
                        sourceImageUrl = sourceImageUrl,
                    )
                } catch (error: DormitoryMealWeekMismatchException) {
                    return publishStatus(submissionId, "REJECTED", error.message)
                }
                publisher.publishDormitoryMeal(
                    payload = payload,
                    revision = publisher.nextRevision("dorm_meal"),
                    publishedAt = clock.instant(),
                )
                publishStatus(submissionId, "PUBLISHED", null)
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
