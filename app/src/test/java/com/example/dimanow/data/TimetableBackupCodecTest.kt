package com.example.dimanow.data

import com.example.dimanow.domain.CourseOverride
import com.example.dimanow.domain.CourseOverrideKind
import com.example.dimanow.domain.GuidancePause
import com.example.dimanow.domain.SampleSchedule
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TimetableBackupCodecTest {
    @Test
    fun `a timetable survives export and import and carries no account data`() {
        val base = SampleSchedule.create()
        val courses = base.courses.mapIndexed { index, course -> course.copy(id = 100L + index) }
        val schedule = base.copy(
            courses = courses,
            noClassDates = setOf(LocalDate.of(2026, 10, 8)),
            guidancePause = GuidancePause(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 3)),
            courseOverrides = listOf(CourseOverride(courses[1].id, LocalDate.of(2026, 9, 15), CourseOverrideKind.ONLINE)),
        )

        val bytes = TimetableBackupCodec.encode(schedule)
        val backup = TimetableBackupCodec.decode(bytes)

        assertEquals(6, backup.courses.size)
        assertEquals("2026-08-24", backup.termStart)
        assertEquals(listOf("2026-10-08"), backup.noClassDates)
        assertEquals(1, backup.overrides.size)
        assertEquals(courses[1].name, backup.courses[backup.overrides.single().courseIndex].name)
        val text = bytes.decodeToString()
        assertFalse(text.contains("password", ignoreCase = true))
        assertTrue(text.contains("dima-now-timetable"))
    }

    @Test
    fun `foreign or broken files are rejected with a reason`() {
        assertThrows(IllegalArgumentException::class.java) { TimetableBackupCodec.decode("{\"hello\":1}".toByteArray()) }
        assertThrows(IllegalArgumentException::class.java) { TimetableBackupCodec.decode(ByteArray(0)) }
        val valid = TimetableBackupCodec.encode(SampleSchedule.create()).decodeToString()
        assertThrows(IllegalArgumentException::class.java) {
            TimetableBackupCodec.decode(valid.replace("\"start\": \"10:00\"", "\"start\": \"13:00\"").toByteArray())
        }
    }
}
