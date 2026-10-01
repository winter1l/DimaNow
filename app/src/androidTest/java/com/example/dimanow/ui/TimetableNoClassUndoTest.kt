package com.example.dimanow.ui

import android.graphics.Bitmap
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.platform.app.InstrumentationRegistry
import com.example.dimanow.data.CampusDataRepository
import com.example.dimanow.domain.CampusZone
import com.example.dimanow.domain.CampusZoneId
import com.example.dimanow.domain.Course
import com.example.dimanow.domain.CourseOverride
import com.example.dimanow.domain.GuidancePause
import com.example.dimanow.domain.TermSchedule
import com.example.dimanow.theme.DIMANowTheme
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import org.junit.Rule
import org.junit.Test

/** D-094(14): readable dates, undo for no-class deletion, and a visible one-time change action. */
class TimetableNoClassUndoTest {
    @get:Rule val compose = createComposeRule()

    private val noClassDate = LocalDate.parse("2026-10-05")
    private val course = Course(
        weekday = DayOfWeek.MONDAY,
        start = LocalTime.of(10, 0),
        end = LocalTime.of(12, 50),
        name = "조명기초및실습",
        room = "덕성관 402",
        professor = "",
        zone = CampusZoneId.MAIN,
        id = 7,
    )
    private val repository = FakeCampusDataRepository(
        TermSchedule(
            termStart = LocalDate.parse("2026-08-24"),
            termEnd = LocalDate.parse("2026-12-18"),
            courses = listOf(course),
            noClassDates = setOf(noClassDate),
        ),
    )

    private fun show() {
        compose.setContent {
            DIMANowTheme(darkTheme = false) {
                val schedule by repository.schedule.collectAsState()
                TimetableScreen(repository, schedule, today = LocalDate.parse("2026-09-30"))
            }
        }
    }

    @Test
    fun deletingANoClassDateOffersUndoThatRestoresIt() {
        show()
        // Dates are written for people, not as ISO text.
        compose.onNodeWithText("학기 8월 24일 (월) ~ 12월 18일 (금)").assertExists()
        compose.onNodeWithTag("timetable_list").performScrollToNode(hasTestTag("delete_no_class_$noClassDate"))
        compose.onNodeWithText("10월 5일 (월)").assertExists()
        compose.onNodeWithText("2026-10-05").assertDoesNotExist()

        compose.onNodeWithTag("delete_no_class_$noClassDate").performClick()
        compose.waitUntil(5_000) { repository.schedule.value.noClassDates.isEmpty() }
        compose.onNodeWithText("휴강일을 삭제했어요").assertExists()
        capture("timetable-no-class-undo")

        compose.onNodeWithText("되돌리기").performClick()
        compose.waitUntil(5_000) { repository.schedule.value.noClassDates == setOf(noClassDate) }
        compose.onNodeWithTag("timetable_list").performScrollToNode(hasTestTag("delete_no_class_$noClassDate"))
        compose.onNodeWithText("10월 5일 (월)").assertExists()
    }

    @Test
    fun courseCardShowsTheOneTimeChangeActionForTheNextClass() {
        show()
        compose.onNodeWithTag("timetable_list").performScrollToNode(hasTestTag("change_once_7"))
        compose.onNodeWithText("이번 수업만 변경 · 10월 12일 (월)").assertExists().performClick()
        compose.onNodeWithText("수업 한 번만 변경").assertExists()
        // The dialog targets the same class date the card named: the no-class Monday is skipped.
        compose.onNodeWithText("10월 12일 (월)").assertExists()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "phase6").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}

private class FakeCampusDataRepository(initial: TermSchedule) : CampusDataRepository {
    override val schedule = MutableStateFlow(initial)
    override val zones = flowOf(emptyList<CampusZone>())

    override suspend fun ensureSeeded() = Unit
    override suspend fun saveCourse(course: Course): Long = course.id
    override suspend fun deleteCourse(id: Long) = schedule.update { it.copy(courses = it.courses.filterNot { c -> c.id == id }) }
    override suspend fun setCourseOverride(override: CourseOverride) =
        schedule.update { it.copy(courseOverrides = it.courseOverrides + override) }
    override suspend fun removeCourseOverride(courseId: Long, date: LocalDate) =
        schedule.update { it.copy(courseOverrides = it.courseOverrides.filterNot { o -> o.courseId == courseId && o.date == date }) }
    override suspend fun setTerm(start: LocalDate, end: LocalDate) = schedule.update { it.copy(termStart = start, termEnd = end) }
    override suspend fun addNoClassDate(date: LocalDate) = schedule.update { it.copy(noClassDates = it.noClassDates + date) }
    override suspend fun removeNoClassDate(date: LocalDate) = schedule.update { it.copy(noClassDates = it.noClassDates - date) }
    override suspend fun setGuidancePause(pause: GuidancePause) = schedule.update { it.copy(guidancePause = pause) }
    override suspend fun clearGuidancePause() = schedule.update { it.copy(guidancePause = null) }
    override suspend fun installBundledCampusZones() = Unit
}
