package com.example.dimanow.meal

import com.example.dimanow.sync.DormitoryMealSubmissionStatus

interface DormitoryMealSubmissionGateway {
    suspend fun upload(image: DormitoryMealImage): DormitoryMealSubmission
    suspend fun submissionStatus(submissionId: String): DormitoryMealSubmissionStatus?
}

sealed interface DormitoryMealSubmissionResult {
    data class Submitted(val submissionId: String) : DormitoryMealSubmissionResult
    data object Processing : DormitoryMealSubmissionResult
    data object Published : DormitoryMealSubmissionResult
    data object Duplicate : DormitoryMealSubmissionResult
    data class Rejected(val reason: String) : DormitoryMealSubmissionResult
    data class Failure(val message: String) : DormitoryMealSubmissionResult
}

class DormitoryMealSubmissionService(
    private val gateway: DormitoryMealSubmissionGateway,
) {
    suspend fun submit(image: DormitoryMealImage): DormitoryMealSubmissionResult {
        return runCatching { gateway.upload(image) }.fold(
            onSuccess = { DormitoryMealSubmissionResult.Submitted(it.submissionId) },
            onFailure = { DormitoryMealSubmissionResult.Failure(it.message ?: "식단 사진 업로드에 실패했습니다.") },
        )
    }

    suspend fun status(submissionId: String): DormitoryMealSubmissionResult = runCatching {
        gateway.submissionStatus(submissionId)
    }.fold(
        onSuccess = { status -> status?.toResult() ?: DormitoryMealSubmissionResult.Processing },
        onFailure = { DormitoryMealSubmissionResult.Failure(it.message ?: "식단 처리 상태를 확인하지 못했습니다.") },
    )

    private fun DormitoryMealSubmissionStatus.toResult(): DormitoryMealSubmissionResult = when (state) {
        "PUBLISHED" -> DormitoryMealSubmissionResult.Published
        "DUPLICATE" -> DormitoryMealSubmissionResult.Duplicate
        "REJECTED" -> DormitoryMealSubmissionResult.Rejected(message ?: "기숙사 식단표를 확인할 수 없습니다.")
        "ERROR" -> DormitoryMealSubmissionResult.Failure(message ?: "식단 처리에 실패했습니다.")
        else -> DormitoryMealSubmissionResult.Processing
    }
}
