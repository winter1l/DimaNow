package com.example.dimanow.meal

import com.example.dimanow.sync.DormitoryMealSubmissionStatus
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DormitoryMealSubmissionServiceTest {
    @Test
    fun `사진은 사용자 로그인 없이 익명 업로드 게이트웨이로 제출된다`() = runTest {
        val gateway = FakeGateway()
        val service = DormitoryMealSubmissionService(gateway)

        assertEquals(
            DormitoryMealSubmissionResult.Submitted("submission-123"),
            service.submit(DormitoryMealImage(byteArrayOf(1, 2, 3), "image/jpeg", "jpg")),
        )
        assertEquals(byteArrayOf(1, 2, 3).toList(), gateway.uploadedImage?.bytes?.toList())
        assertEquals(DormitoryMealSubmissionResult.Published, service.status("submission-123"))
    }

    @Test
    fun `익명 업로드 실패는 로그인 요구 없이 사용자 오류로 반환한다`() = runTest {
        val gateway = FakeGateway().apply { uploadFailure = IllegalStateException("업로드 서비스 응답 503") }
        val service = DormitoryMealSubmissionService(gateway)

        assertEquals(
            DormitoryMealSubmissionResult.Failure("업로드 서비스 응답 503"),
            service.submit(DormitoryMealImage(byteArrayOf(1), "image/jpeg", "jpg")),
        )
    }

    private class FakeGateway : DormitoryMealSubmissionGateway {
        var uploadedImage: DormitoryMealImage? = null
        var uploadFailure: Exception? = null

        override suspend fun upload(image: DormitoryMealImage): DormitoryMealSubmission {
            uploadedImage = image
            uploadFailure?.let { throw it }
            return DormitoryMealSubmission("submission-123", Instant.parse("2026-08-31T03:04:05Z"))
        }

        override suspend fun submissionStatus(submissionId: String) =
            DormitoryMealSubmissionStatus(submissionId, "PUBLISHED", null, "2026-08-31T03:05:05Z")
    }
}
