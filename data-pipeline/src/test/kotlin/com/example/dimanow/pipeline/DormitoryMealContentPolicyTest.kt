package com.example.dimanow.pipeline

import com.example.dimanow.sync.DormitoryMealDayPayload
import com.example.dimanow.sync.DormitoryMealPayload
import com.example.dimanow.sync.DormitoryMealSectionPayload
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DormitoryMealContentPolicyTest {
    private val policy = DormitoryMealContentPolicy

    @Test
    fun `실제 게시된 식단 형태와 휴일 한 줄은 허용한다`() {
        for (line in listOf(
            "추석 공휴일", "추석", "휴무", "떡국", "완자전&소스", "라면 1개", "치즈2장",
            "바베큐닭가슴살샐러드도시락\n-계육:국내산-",
            "모듬까스&소스-돈까스:돈육:국내산 치킨까스:계육:국내산 생선까스:명태:중국산-",
            "부대찌개-햄:계육,돈육:국내산 소시지:계육:국내산-",
            "햄버거세트(빵,패티,치즈 각1)",
        )) {
            assertTrue(line, policy.isAllowedMenuLine(line))
        }
        for (hours in listOf(null, "", "08:00~09:30", "12:00 ~ 14:00", "18:00-19:30", "7시~9시", "07:30～09:00")) {
            assertTrue("$hours", policy.isAllowedHours(hours))
        }
        for (name in listOf("조식", "간편식", "라면", "샐러드도시락", "안내")) assertTrue(name, policy.isAllowedSectionName(name))
        assertTrue(policy.isAllowed(payload(sections = List(8) { section(name = "라면", lines = List(9) { "메뉴$it" }) })))
    }

    @Test
    fun `URL과 도메인과 단축 주소는 거절한다`() {
        for (line in listOf(
            "https://example.com", "http://x", "hxxp 없이 www.example.org", "bit.ly/abc", "naver.me/xyz",
            "dima.ac.kr 참고", "쿠폰 example . com", "링크 example[.]kr", "ＷＷＷ．ＥＸＡＭＰＬＥ．ＣＯＭ", "ftp://host",
        )) {
            assertFalse(line, policy.isAllowedMenuLine(line))
        }
        assertFalse(policy.isAllowedSectionName("bit.ly"))
    }

    @Test
    fun `전화번호와 이메일과 긴 숫자열은 거절한다`() {
        for (line in listOf(
            "문의 010-1234-5678", "02 123 4567", "1588-1234", "01012345678", "연락 a@b", "주문번호 12345",
            "계좌 123-45-67890", "０１０－１２３４－５６７８",
        )) {
            assertFalse(line, policy.isAllowedMenuLine(line))
        }
    }

    @Test
    fun `제어 문자와 보이지 않는 문자는 거절하고 줄바꿈은 두 번까지만 허용한다`() {
        for (line in listOf("떡국\u0000", "떡\r국", "떡\t국", "떡​국", "떡‮국", "떡 국", "떡국", "a\nb\nc\nd")) {
            assertFalse(line.map { it.code.toString(16) }.toString(), policy.isAllowedMenuLine(line))
        }
        assertTrue(policy.isAllowedMenuLine("메뉴\n-돈육:국내산-"))
        assertFalse(policy.isAllowedSectionName("조\n식"))
        assertFalse(policy.isAllowedHours("08:00~09:30\n"))
    }

    @Test
    fun `길이와 개수 상한을 넘으면 자르지 않고 거절한다`() {
        assertFalse(policy.isAllowedMenuLine("가".repeat(DormitoryMealContentPolicy.MAX_MENU_LINE_LENGTH + 1)))
        assertTrue(policy.isAllowedMenuLine("가".repeat(DormitoryMealContentPolicy.MAX_MENU_LINE_LENGTH)))
        assertFalse(policy.isAllowedSectionName("가".repeat(DormitoryMealContentPolicy.MAX_SECTION_NAME_LENGTH + 1)))
        assertFalse(policy.isAllowedHours("08:00~09:30 (주말 09:00~10:00)"))
        assertFalse(policy.isAllowedHours("아침 시간"))
        assertFalse(policy.isAllowed(payload(sections = listOf(section(lines = List(16) { "메뉴$it" })))))
        assertFalse(policy.isAllowed(payload(sections = List(11) { section() })))
        assertThrows(DormitoryMealContentException::class.java) {
            policy.requireAllowed(payload(sections = listOf(section(lines = listOf("https://example.com")))))
        }
    }

    @Test
    fun `거절 사유는 한 줄 60자 이하로 제한하고 위험한 내용은 고정 사유로 바꾼다`() {
        assertEquals("기숙사 식단표가 아니에요", policy.sanitizeRejectionReason("기숙사 식단표가 아니에요"))
        assertEquals("사진이 흐려요", policy.sanitizeRejectionReason("\n  사진이​ 흐려요\n두 번째 줄 https://x.y"))
        val fallback = DormitoryMealContentPolicy.FALLBACK_REASON
        assertEquals(fallback, policy.sanitizeRejectionReason(null))
        assertEquals(fallback, policy.sanitizeRejectionReason(" \n\t"))
        assertEquals(fallback, policy.sanitizeRejectionReason("자세한 내용은 https://evil.example 참고"))
        assertEquals(fallback, policy.sanitizeRejectionReason("bit.ly/abc 에서 확인"))
        assertEquals(fallback, policy.sanitizeRejectionReason("문의 010-1234-5678"))
        assertEquals(fallback, policy.sanitizeRejectionReason("가".repeat(61)))
        assertEquals("가".repeat(60), policy.sanitizeRejectionReason("가".repeat(60)))
        assertTrue(fallback.length <= DormitoryMealContentPolicy.MAX_REJECTION_REASON_LENGTH)
    }

    @Test
    fun `빌더는 금지된 내용이 있는 OCR 결과를 내용 예외로 거절한다`() {
        val response = """{"days":[""" + (0L..4L).joinToString(",") { offset ->
            val date = LocalDate.of(2026, 8, 24).plusDays(offset)
            val line = if (offset == 2L) "할인 쿠폰 bit.ly/abc" else "메뉴$offset"
            """{"month":${date.monthValue},"day":${date.dayOfMonth},"sections":[{"name":"조식","menuLines":["$line"]}]}"""
        } + "]}"
        assertThrows(DormitoryMealContentException::class.java) {
            GeminiDormitoryMealPayloadBuilder().build(response, LocalDate.of(2026, 8, 27), "https://example.invalid/x.jpg")
        }
    }

    private fun section(name: String = "조식", lines: List<String> = listOf("메뉴")) =
        DormitoryMealSectionPayload(name = name, hours = "08:00~09:30", menuLines = lines)

    private fun payload(sections: List<DormitoryMealSectionPayload>) = DormitoryMealPayload(
        weekStart = "2026-08-24",
        weekEnd = "2026-08-30",
        sourceImageUrl = "https://example.invalid/x.jpg",
        days = (0L..4L).map { DormitoryMealDayPayload(LocalDate.of(2026, 8, 24).plusDays(it).toString(), sections) },
    )
}
