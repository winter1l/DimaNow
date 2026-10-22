package com.example.dimanow.school

import com.example.dimanow.domain.CampusZoneId
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** D-107 readers, on made-up page data shaped like the intranet and DIMA C answers (no real personal data). */
class SchoolReadersTest {
    @Test
    fun `timetable periods merge into course blocks starting at 9 for period 1`() {
        val json = """
            {"days":["월","화","수","목","금","토"],
             "periods":[
               {"period":"1교시","cells":[[],["영상기초","가동101","김교수"],[],[],[],[]]},
               {"period":"2교시","cells":[["음향실습","나동202","이교수"],["영상기초","가동101","김교수"],[],[],[],[]]},
               {"period":"3교시","cells":[["음향실습","나동202","이교수"],["영상기초","가동101","김교수"],[],[],["연기","엔터관301","박교수"],[]]},
               {"period":"4교시","cells":[[],[],[],[],[],[]]},
               {"period":"5교시","cells":[[],["영상기초","가동101","김교수"],[],[],[],[]]}
             ]}
        """.trimIndent()
        val courses = parseIntranetTimetable(json)
        assertEquals(4, courses.size)
        val monday = courses.first { it.weekday == DayOfWeek.MONDAY }
        assertEquals(LocalTime.of(10, 0) to LocalTime.of(12, 0), monday.start to monday.end)
        // The same course after a free period is a second block.
        val tuesday = courses.filter { it.weekday == DayOfWeek.TUESDAY }
        assertEquals(listOf(LocalTime.of(9, 0) to LocalTime.of(12, 0), LocalTime.of(13, 0) to LocalTime.of(14, 0)), tuesday.map { it.start to it.end })
        val friday = courses.first { it.weekday == DayOfWeek.FRIDAY }
        assertEquals(CampusZoneId.YEIN, friday.suggestedZone)
        assertEquals(CampusZoneId.MAIN, monday.suggestedZone)
        assertEquals("이교수", monday.toCourse().professor)
    }

    @Test
    fun `late periods end at the end of the day`() {
        val course = ImportedCourse(DayOfWeek.MONDAY, 15, 15, "야간", "", "")
        assertEquals(LocalTime.of(23, 0), course.start)
        assertEquals(LocalTime.of(23, 59), course.end)
    }

    @Test
    fun `dorm points add up the latest term and keep its newest entries first`() {
        val json = """
            {"items":[
              {"year":"2026","term":"2","date":"20261001","merit":"","demerit":"5","content":"소등 위반"},
              {"year":"2026","term":"2","date":"20260931","merit":"","demerit":"","content":"날짜가 잘못된 줄"},
              {"year":"2026","term":"2","date":"20260915","merit":"3","demerit":"","content":"봉사"},
              {"year":"2026","term":"1","date":"20260410","merit":"","demerit":"2","content":"지난 학기"}
            ]}
        """.trimIndent()
        val summary = summarizeDormPoints(parseDormPoints(json))!!
        assertEquals(2026 to 2, summary.year to summary.term)
        assertEquals(3 to 5, summary.merit to summary.demerit)
        assertEquals("소등 위반", summary.entries.first().content)
        assertNull(summary.entries.first { it.content == "날짜가 잘못된 줄" }.date)
        assertNull(summarizeDormPoints(emptyList()))
    }

    @Test
    fun `equipment days follow the site's booking rule`() {
        // Units A and B; A is booked 10-26..10-28 and 10-28..10-30 (back to back), B 10-27..10-27.
        val json = """
            {"days":[
              {"day":"20261023","weekday":"6","rentable":true,"holiday":false},
              {"day":"20261024","weekday":"7","rentable":true,"holiday":false},
              {"day":"20261026","weekday":"2","rentable":true,"holiday":false},
              {"day":"20261027","weekday":"3","rentable":true,"holiday":false},
              {"day":"20261028","weekday":"4","rentable":true,"holiday":false},
              {"day":"20261030","weekday":"6","rentable":true,"holiday":false},
              {"day":"20261103","weekday":"3","rentable":false,"holiday":false}
            ],
             "units":["A","B"],
             "bookings":[{"unit":"A","from":"20261026","to":"20261028"},{"unit":"A","from":"20261028","to":"20261030"},{"unit":"B","from":"20261027","to":"20261027"}]}
        """.trimIndent()
        val days = parseEquipmentAvailability(json).associateBy { it.date }
        assertEquals(2, days.getValue(LocalDate.of(2026, 10, 23)).freeUnits)
        assertFalse("Saturday is closed", days.getValue(LocalDate.of(2026, 10, 24)).open)
        // A's pickup day alone still counts as free.
        assertEquals(2, days.getValue(LocalDate.of(2026, 10, 26)).freeUnits)
        // Strictly inside A's booking; B's one-day booking is both its pickup and return day.
        assertEquals(0, days.getValue(LocalDate.of(2026, 10, 27)).freeUnits)
        // A returns and is picked up again the same day: busy.
        assertEquals(1, days.getValue(LocalDate.of(2026, 10, 28)).freeUnits)
        assertEquals(2, days.getValue(LocalDate.of(2026, 10, 30)).freeUnits)
        assertFalse("past the booking window", days.getValue(LocalDate.of(2026, 11, 3)).open)
        assertTrue(days.values.all { it.totalUnits == 2 })
    }

    @Test
    fun `the catalog keeps only models with rentable units`() {
        val json = """
            {"groups":[
              {"name":"카메라(일반대여)","models":[{"no":3900,"name":"PXW-Z190","units":3}]},
              {"name":"조명 장비(일반대여)","models":[]}
            ]}
        """.trimIndent()
        val groups = parseEquipmentCatalog(json)
        assertEquals(listOf("카메라"), groups.map { it.displayName })
        assertEquals(EquipmentModel(3900, "PXW-Z190", 3), groups.single().models.single())
    }

    @Test
    fun `the school web view stays on the portal, the two services and the ticket hop`() {
        assertTrue(SchoolUrlPolicy.isAllowedNavigation("https://intranet.dima.ac.kr/su/timeTable_Stnt.aspx"))
        assertTrue(SchoolUrlPolicy.isAllowedNavigation("https://udimac.dima.ac.kr/ajax/ST_MPRT01_SVC/ST_MPRT01_R10.do"))
        assertTrue(SchoolUrlPolicy.isAllowedNavigation("http://sso.dima.ac.kr:8080/sso/pmi-sso.jsp?x=1"))
        assertFalse(SchoolUrlPolicy.isAllowedNavigation("http://intranet.dima.ac.kr/su/timeTable_Stnt.aspx"))
        assertFalse(SchoolUrlPolicy.isAllowedNavigation("https://lms.dima.ac.kr/"))
        assertFalse(SchoolUrlPolicy.isAllowedNavigation("http://sso.dima.ac.kr:8080/other"))
        assertFalse(SchoolUrlPolicy.isAllowedNavigation("https://evil.example/intranet.dima.ac.kr"))
        assertTrue(SchoolUrlPolicy.isPortalCredentialPage("https://portal.dima.ac.kr/?r=https://udimac.dima.ac.kr/sso/index.jsp"))
        assertTrue(SchoolUrlPolicy.isSessionConflict("https://portal.dima.ac.kr/sso/error.aspx?errorCode=3045"))
        assertEquals("sso.dima.ac.kr/sso/pmi-sso.jsp", SchoolUrlPolicy.describe("http://sso.dima.ac.kr:8080/sso/pmi-sso.jsp?ticket=secret"))
    }
}
