package com.example.dimanow.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultScheduleTest {
    @Test
    fun `default 2026 term contains the six approved classes`() {
        val schedule = DefaultSchedule.create()

        assertEquals(LocalDate.of(2026, 8, 24), schedule.termStart)
        assertEquals(LocalDate.of(2026, 12, 18), schedule.termEnd)
        assertEquals(
            listOf(
                "MONDAY|10:00|12:50|조명기초및실습|덕성관 402|이용창|MAIN",
                "TUESDAY|09:00|11:50|스튜디오기초실습|기예관 122|이상운|MAIN",
                "TUESDAY|13:00|15:50|카메라기초및실습|덕성관 210|김재호|MAIN",
                "TUESDAY|16:00|18:50|음향기초실습|덕성관 303|이화현|MAIN",
                "THURSDAY|13:00|14:50|프리젠테이션영어|덕성관 510-1|이효정|MAIN",
                "FRIDAY|10:00|12:50|방송시스템전기|기예관 412|박창묵|MAIN",
            ),
            schedule.courses.map {
                "${it.weekday}|${it.start}|${it.end}|${it.name}|${it.room}|${it.professor}|${it.zone}"
            },
        )
    }
}
