package com.example.dimanow.meal

import com.example.dimanow.meal.DormitoryMealSubmissionMessages.MAX_REASON_LENGTH
import com.example.dimanow.meal.DormitoryMealSubmissionMessages.REJECTED_LEAD
import com.example.dimanow.meal.DormitoryMealSubmissionMessages.RETAKE
import com.example.dimanow.sync.DormitoryMealSubmissionStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DormitoryMealSubmissionMessagesTest {
    private fun rejected(reason: String?) = DormitoryMealSubmissionMessages.rejected(reason)

    @Test
    fun `알려진 파이프라인 사유는 정해진 해요체 안내로 바뀐다`() {
        assertEquals(
            "사진을 게시하지 못했어요. 이번 주 기숙사 식단표가 아니에요. 이번 주 식단표를 찍어 올려 주세요",
            rejected("이번 주 기숙사 식단표가 아니에요"),
        )
        assertEquals(
            "사진을 게시하지 못했어요. 날짜와 메뉴를 읽지 못했어요. 식단표 전체가 보이게 다시 찍어 올려 주세요",
            rejected("식단표의 날짜와 메뉴가 잘 보이도록 다시 촬영해 주세요"),
        )
        assertEquals(
            "사진을 게시하지 못했어요. 식단표 전체가 보이게 다시 찍어 올려 주세요",
            rejected("기숙사 식단표 전체가 보이도록 다시 촬영해 주세요."),
        )
    }

    @Test
    fun `빈 사유는 다시 찍어 올리라는 기본 안내를 보여 준다`() {
        val generic = "$REJECTED_LEAD $RETAKE"
        assertEquals(generic, rejected(null))
        assertEquals(generic, rejected(""))
        assertEquals(generic, rejected("  \n\t "))
    }

    @Test
    fun `짧은 Gemini 사유는 해요체로 다듬고 다음 행동을 붙인다`() {
        assertEquals(
            "사진을 게시하지 못했어요. 사진이 기숙사 식단표가 아니에요. $RETAKE",
            rejected("사진이 기숙사 식단표가 아닙니다."),
        )
        assertEquals(
            "사진을 게시하지 못했어요. 표의 오른쪽 가장자리가 잘렸어요. $RETAKE",
            rejected("표의 오른쪽 가장자리가 잘렸습니다."),
        )
        assertEquals(
            "사진을 게시하지 못했어요. 식단표가 흐려서 다시 촬영해 주세요",
            rejected("식단표가 흐려서 다시 촬영해 주세요"),
        )
    }

    @Test
    fun `알 수 없는 긴 사유는 첫 줄만 60자 안으로 줄인다`() {
        val longReason = "사진 속 표는 기숙사 식단표처럼 보이지만 금요일 석식 칸과 아래쪽 샐러드도시락 칸이 손가락에 가려져 " +
            "메뉴 일부를 읽을 수 없고 날짜 행도 흐릿합니다\n두 번째 줄은 보여 주지 않아요"
        val message = rejected(longReason)
        val shown = message.removePrefix("$REJECTED_LEAD ").removeSuffix(". $RETAKE")

        assertTrue(message.startsWith(REJECTED_LEAD))
        assertTrue(shown.endsWith("…"))
        assertTrue(shown.length <= MAX_REASON_LENGTH)
        assertFalse(message.contains("두 번째 줄"))
        assertFalse(message.contains('\n'))
    }

    @Test
    fun `원시 JSON이나 예외 문구는 사용자에게 보이지 않는다`() {
        val generic = "$REJECTED_LEAD $RETAKE"
        assertEquals(generic, rejected("{\"isDormitoryMeal\":false,\"reason\":null}"))
        assertEquals(generic, rejected("java.lang.IllegalStateException: Gemini 응답 오류"))
        assertEquals(generic, rejected("HTTP 503 Service Unavailable"))
    }

    @Test
    fun `서비스는 거절 사유를 서버가 보낸 그대로 넘긴다`() = runTest {
        val gateway = object : DormitoryMealSubmissionGateway {
            var status = DormitoryMealSubmissionStatus("s-1", "REJECTED", "이번 주 기숙사 식단표가 아니에요", "2026-10-01T00:00:00Z")
            override suspend fun upload(image: DormitoryMealImage) = error("unused")
            override suspend fun submissionStatus(submissionId: String) = status
        }
        val service = DormitoryMealSubmissionService(gateway)

        assertEquals(DormitoryMealSubmissionResult.Rejected("이번 주 기숙사 식단표가 아니에요"), service.status("s-1"))
        gateway.status = gateway.status.copy(message = null)
        assertEquals(DormitoryMealSubmissionResult.Rejected(null), service.status("s-1"))
    }
}
