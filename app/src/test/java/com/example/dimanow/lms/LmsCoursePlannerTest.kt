package com.example.dimanow.lms

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class LmsCoursePlannerTest {
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
        )

        assertEquals(listOf("음향기초실습", "영상제작"), plan.groups.map { it.courseName })
        assertEquals(listOf("a1"), plan.groups[0].items.map { it.id })
        assertEquals(listOf("v1", "v2"), plan.groups[1].items.map { it.id })
    }

    @Test
    fun `completed learning leaves the course groups and collects at the bottom`() {
        val plan = planLmsByCourse(
            items = listOf(
                item("a1", "audio"),
                item("a2", "audio", completion = LmsCompletionState.COMPLETE),
            ),
            courses = courses,
        )

        assertEquals(listOf("a1"), plan.groups.single().items.map { it.id })
        assertEquals(listOf("a2"), plan.completed.map { it.id })
    }

    @Test
    fun `a course missing from the catalog still appears, after the known ones`() {
        val plan = planLmsByCourse(
            items = listOf(item("x1", "unknown", courseName = "미등록 과목"), item("a1", "audio")),
            courses = courses,
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
        )

        assertEquals(
            listOf("sooner", "later", "new-note", "old-note"),
            plan.groups.single().items.map { it.id },
        )
    }
}
