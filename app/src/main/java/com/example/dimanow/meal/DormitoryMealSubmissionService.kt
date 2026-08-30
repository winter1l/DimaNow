package com.example.dimanow.meal

import com.example.dimanow.sync.DormitoryMealSubmissionStatus

interface DormitoryMealSubmissionGateway {
    suspend fun beginAuthorization(clientId: String): DormitoryDeviceAuthorization
    suspend fun pollAuthorization(clientId: String, deviceCode: String): DormitoryAuthorizationPoll
    suspend fun upload(token: String, image: DormitoryMealImage): DormitoryMealSubmission
    suspend fun submissionStatus(submissionId: String): DormitoryMealSubmissionStatus?
}

interface DormitoryMealTokenStore {
    fun read(): String?
    fun write(token: String)
    fun clear()
}

class DormitoryMealAuthorizationException : Exception("GitHub 연결이 만료되었습니다.")

sealed interface DormitoryMealAuthorization {
    data object AlreadyAuthorized : DormitoryMealAuthorization
    data class Started(val authorization: DormitoryDeviceAuthorization) : DormitoryMealAuthorization
    data object Pending : DormitoryMealAuthorization
    data object Authorized : DormitoryMealAuthorization
    data class Failed(val message: String) : DormitoryMealAuthorization
}

sealed interface DormitoryMealSubmissionResult {
    data object AuthorizationRequired : DormitoryMealSubmissionResult
    data class Submitted(val submissionId: String) : DormitoryMealSubmissionResult
    data object Processing : DormitoryMealSubmissionResult
    data object Published : DormitoryMealSubmissionResult
    data object Duplicate : DormitoryMealSubmissionResult
    data class Rejected(val reason: String) : DormitoryMealSubmissionResult
    data class Failure(val message: String) : DormitoryMealSubmissionResult
}

class DormitoryMealSubmissionService(
    private val clientId: String,
    private val gateway: DormitoryMealSubmissionGateway,
    private val tokenStore: DormitoryMealTokenStore,
) {
    suspend fun beginAuthorization(): DormitoryMealAuthorization {
        if (tokenStore.read() != null) return DormitoryMealAuthorization.AlreadyAuthorized
        if (clientId.isBlank()) return DormitoryMealAuthorization.Failed("GitHub 업로드 연결이 준비되지 않았습니다.")
        return runCatching { DormitoryMealAuthorization.Started(gateway.beginAuthorization(clientId)) }
            .getOrElse { DormitoryMealAuthorization.Failed(it.message ?: "GitHub 연결에 실패했습니다.") }
    }

    suspend fun pollAuthorization(authorization: DormitoryDeviceAuthorization): DormitoryMealAuthorization =
        when (val result = runCatching { gateway.pollAuthorization(clientId, authorization.deviceCode) }
            .getOrElse { DormitoryAuthorizationPoll.Failed(it.message ?: "GitHub 연결에 실패했습니다.") }) {
            is DormitoryAuthorizationPoll.Authorized -> {
                tokenStore.write(result.token)
                DormitoryMealAuthorization.Authorized
            }
            DormitoryAuthorizationPoll.Pending,
            DormitoryAuthorizationPoll.SlowDown,
            -> DormitoryMealAuthorization.Pending
            is DormitoryAuthorizationPoll.Failed -> DormitoryMealAuthorization.Failed(result.message)
        }

    suspend fun submit(image: DormitoryMealImage): DormitoryMealSubmissionResult {
        val token = tokenStore.read() ?: return DormitoryMealSubmissionResult.AuthorizationRequired
        return runCatching { gateway.upload(token, image) }
            .fold(
                onSuccess = { DormitoryMealSubmissionResult.Submitted(it.submissionId) },
                onFailure = {
                    if (it is DormitoryMealAuthorizationException) {
                        tokenStore.clear()
                        DormitoryMealSubmissionResult.AuthorizationRequired
                    } else {
                        DormitoryMealSubmissionResult.Failure(it.message ?: "식단 사진 업로드에 실패했습니다.")
                    }
                },
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
