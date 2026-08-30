package com.example.dimanow.meal

import com.example.dimanow.sync.DormitoryMealSubmissionStatus
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DormitoryMealSubmissionServiceTest {
    @Test
    fun `인증 완료 토큰은 저장되고 사진 제출과 공개 상태 조회에 재사용된다`() = runTest {
        val gateway = FakeGateway()
        val tokens = FakeTokenStore()
        val service = DormitoryMealSubmissionService("client-123", gateway, tokens)

        val started = service.beginAuthorization() as DormitoryMealAuthorization.Started
        assertEquals("ABCD-EFGH", started.authorization.userCode)
        assertEquals(DormitoryMealAuthorization.Authorized, service.pollAuthorization(started.authorization))
        assertEquals("token-123", tokens.read())

        assertEquals(
            DormitoryMealSubmissionResult.Submitted("submission-123"),
            service.submit(DormitoryMealImage(byteArrayOf(1, 2, 3), "image/jpeg", "jpg")),
        )
        assertEquals(DormitoryMealSubmissionResult.Published, service.status("submission-123"))
        assertEquals("token-123", gateway.uploadToken)
    }

    @Test
    fun `인증 전에는 사진을 전송하지 않고 연결 필요를 반환한다`() = runTest {
        val gateway = FakeGateway()
        val service = DormitoryMealSubmissionService("client-123", gateway, FakeTokenStore())

        assertEquals(
            DormitoryMealSubmissionResult.AuthorizationRequired,
            service.submit(DormitoryMealImage(byteArrayOf(1), "image/jpeg", "jpg")),
        )
        assertNull(gateway.uploadToken)
    }

    @Test
    fun `만료된 GitHub 인증은 지우고 다시 연결하도록 반환한다`() = runTest {
        val gateway = FakeGateway().apply { uploadFailure = DormitoryMealAuthorizationException() }
        val tokens = FakeTokenStore().apply { write("expired-token") }
        val service = DormitoryMealSubmissionService("client-123", gateway, tokens)

        assertEquals(
            DormitoryMealSubmissionResult.AuthorizationRequired,
            service.submit(DormitoryMealImage(byteArrayOf(1), "image/jpeg", "jpg")),
        )
        assertNull(tokens.read())
    }

    private class FakeTokenStore : DormitoryMealTokenStore {
        private var token: String? = null
        override fun read(): String? = token
        override fun write(token: String) { this.token = token }
        override fun clear() { token = null }
    }

    private class FakeGateway : DormitoryMealSubmissionGateway {
        var uploadToken: String? = null
        var uploadFailure: Exception? = null

        override suspend fun beginAuthorization(clientId: String) = DormitoryDeviceAuthorization(
            deviceCode = "device-123",
            userCode = "ABCD-EFGH",
            verificationUri = "https://github.com/login/device",
            expiresInSeconds = 900,
            intervalSeconds = 5,
        )

        override suspend fun pollAuthorization(clientId: String, deviceCode: String) =
            DormitoryAuthorizationPoll.Authorized("token-123")

        override suspend fun upload(token: String, image: DormitoryMealImage): DormitoryMealSubmission {
            uploadToken = token
            uploadFailure?.let { throw it }
            return DormitoryMealSubmission("submission-123", Instant.parse("2026-08-31T03:04:05Z"))
        }

        override suspend fun submissionStatus(submissionId: String) =
            DormitoryMealSubmissionStatus(submissionId, "PUBLISHED", null, "2026-08-31T03:05:05Z")
    }
}
