package com.example.dimanow.meal

import com.example.dimanow.sync.DormitoryMealSubmissionStatus

interface DormitoryMealSubmissionGateway {
    suspend fun upload(image: DormitoryMealImage): DormitoryMealSubmission
    suspend fun submissionStatus(submissionId: String): DormitoryMealSubmissionStatus?
    /** Anonymous report of a published week (D-099 clause 10). */
    suspend fun report(weekStart: java.time.LocalDate, reason: DormitoryMealReportReason) {
        error("식단 신고를 지원하지 않아요.")
    }
}

/** Reason codes the report gateway accepts; labels are what the user picks. */
enum class DormitoryMealReportReason(val label: String) {
    WRONG_MENU("메뉴가 사진이나 실제와 달라요"),
    NOT_DORMITORY("기숙사 식단이 아니에요"),
    OFFENSIVE("부적절한 내용이 있어요"),
    OTHER("기타"),
}

sealed interface DormitoryMealSubmissionResult {
    data class Submitted(val submissionId: String) : DormitoryMealSubmissionResult
    data object Processing : DormitoryMealSubmissionResult
    data object Published : DormitoryMealSubmissionResult
    data object Duplicate : DormitoryMealSubmissionResult
    /** [reason] is the server text as sent; show it through [DormitoryMealSubmissionMessages.rejected]. */
    data class Rejected(val reason: String?) : DormitoryMealSubmissionResult
    data class Failure(val message: String) : DormitoryMealSubmissionResult
}

class DormitoryMealSubmissionService(
    private val gateway: DormitoryMealSubmissionGateway,
) {
    suspend fun submit(image: DormitoryMealImage): DormitoryMealSubmissionResult {
        return runCatching { gateway.upload(image) }.fold(
            onSuccess = { DormitoryMealSubmissionResult.Submitted(it.submissionId) },
            onFailure = { DormitoryMealSubmissionResult.Failure(it.message ?: "식단 사진 업로드에 실패했어요.") },
        )
    }

    suspend fun report(weekStart: java.time.LocalDate, reason: DormitoryMealReportReason): Boolean =
        runCatching { gateway.report(weekStart, reason) }.isSuccess

    suspend fun status(submissionId: String): DormitoryMealSubmissionResult = runCatching {
        gateway.submissionStatus(submissionId)
    }.fold(
        onSuccess = { status -> status?.toResult() ?: DormitoryMealSubmissionResult.Processing },
        onFailure = { DormitoryMealSubmissionResult.Failure(it.message ?: "식단 처리 상태를 확인하지 못했어요.") },
    )

    private fun DormitoryMealSubmissionStatus.toResult(): DormitoryMealSubmissionResult = when (state) {
        "PUBLISHED" -> DormitoryMealSubmissionResult.Published
        "DUPLICATE" -> DormitoryMealSubmissionResult.Duplicate
        "REJECTED" -> DormitoryMealSubmissionResult.Rejected(message)
        "ERROR" -> DormitoryMealSubmissionResult.Failure(message ?: "식단 처리에 실패했어요.")
        else -> DormitoryMealSubmissionResult.Processing
    }
}
