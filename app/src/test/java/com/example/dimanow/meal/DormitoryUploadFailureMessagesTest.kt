package com.example.dimanow.meal

import org.junit.Assert.assertEquals
import org.junit.Test

class DormitoryUploadFailureMessagesTest {
    @Test
    fun `gateway reasons become plain next steps`() {
        assertEquals("방금 올린 사진이 있어요. 10분 뒤에 다시 올려 주세요.", DormitoryMealSubmissionMessages.uploadFailure("잠시 후 다시 시도해 주세요."))
        assertEquals("오늘은 올라온 사진이 많아 잠시 쉬고 있어요. 내일 다시 올려 주세요.", DormitoryMealSubmissionMessages.uploadFailure("현재 익명 식단 처리 한도에 도달했습니다."))
        assertEquals("이번 주 기숙사 식단이 이미 올라와 있어요.", DormitoryMealSubmissionMessages.uploadFailure("이번 주 기숙사 식단이 이미 등록되어 있습니다."))
        assertEquals(DormitoryMealSubmissionMessages.UPLOAD_NETWORK, DormitoryMealSubmissionMessages.uploadFailure("사진을 올리지 못했습니다. 잠시 후 다시 시도해 주세요."))
        assertEquals(DormitoryMealSubmissionMessages.UPLOAD_NETWORK, DormitoryMealSubmissionMessages.uploadFailure(null))
    }
}
