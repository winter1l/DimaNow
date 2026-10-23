package com.example.dimanow.lms

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class LmsCoursePlannerTest {
    private val now = Instant.parse("2026-09-01T03:00:00Z")

    private val courses = listOf(
        LmsCourse(id = "audio", name = "음향기초실습"),
        LmsCourse(id = "video", name = "영상제작"),
    )

    private fun item(
        id: String,
        courseId: String,
        courseName: String = courseId,
        dueAt: Instant? = null,
        registeredAt: Instant? = null,
        completion: LmsCompletionState = LmsCompletionState.INCOMPLETE,
    ) = LmsItem(
        id = id,
        courseId = courseId,
        courseName = courseName,
        kind = LmsItemKind.ASSIGNMENT,
        title = id,
        dueAt = dueAt,
        registeredAt = registeredAt,
        detailUrl = "https://lms.dima.ac.kr/$id",
        completionState = completion,
    )

    @Test
    fun `groups follow the official course order regardless of item order`() {
        val plan = planLmsByCourse(
            items = listOf(item("v1", "video"), item("a1", "audio"), item("v2", "video")),
            courses = courses,
            now = now,
        )

        assertEquals(listOf("음향기초실습", "영상제작"), plan.groups.map { it.courseName })
        assertEquals(listOf("a1"), plan.groups[0].items.map { it.id })
        assertEquals(listOf("v1", "v2"), plan.groups[1].items.map { it.id })
    }

    @Test
    fun `each course splits what is left, missed deadlines and completed learning`() {
        val plan = planLmsByCourse(
            items = listOf(
                item("a1", "audio", dueAt = Instant.parse("2026-09-03T00:00:00Z")),
                item("a2", "audio", completion = LmsCompletionState.COMPLETE, dueAt = Instant.parse("2026-08-20T00:00:00Z")),
                item("missed-long-ago", "audio", dueAt = Instant.parse("2026-08-01T00:00:00Z")),
                item("missed-yesterday", "audio", dueAt = Instant.parse("2026-08-31T00:00:00Z")),
                // D-118: a course with nothing left but completed learning keeps its group.
                item("v-done", "video", completion = LmsCompletionState.COMPLETE),
            ),
            courses = courses,
            now = now,
        )

        val audio = plan.groups[0]
        assertEquals(listOf("a1"), audio.items.map { it.id })
        assertEquals(listOf("missed-yesterday", "missed-long-ago"), audio.overdue.map { it.id })
        assertEquals(listOf("a2"), audio.completed.map { it.id })
        val video = plan.groups[1]
        assertEquals(emptyList<String>(), video.items.map { it.id })
        assertEquals(listOf("v-done"), video.completed.map { it.id })
    }

    @Test
    fun `a course missing from the catalog still appears, after the known ones`() {
        val plan = planLmsByCourse(
            items = listOf(item("x1", "unknown", courseName = "미등록 과목"), item("a1", "audio")),
            courses = courses,
            now = now,
        )

        assertEquals(listOf("음향기초실습", "미등록 과목"), plan.groups.map { it.courseName })
    }

    @Test
    fun `within a course the nearest deadline leads and undated items follow by recency`() {
        val plan = planLmsByCourse(
            items = listOf(
                item("later", "audio", dueAt = Instant.parse("2026-09-10T00:00:00Z")),
                item("old-note", "audio", registeredAt = Instant.parse("2026-09-01T00:00:00Z")),
                item("sooner", "audio", dueAt = Instant.parse("2026-09-03T00:00:00Z")),
                item("new-note", "audio", registeredAt = Instant.parse("2026-09-02T00:00:00Z")),
            ),
            courses = courses,
            now = now,
        )

        assertEquals(
            listOf("sooner", "later", "new-note", "old-note"),
            plan.groups.single().items.map { it.id },
        )
    }
}
