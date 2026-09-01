package com.example.dimanow.lms

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class LmsAgendaPlannerTest {
    private val clock = Clock.fixed(Instant.parse("2026-09-01T03:00:00Z"), ZoneId.of("Asia/Seoul"))
    private val planner = LmsAgendaPlanner(clock)

    @Test
    fun todayAgendaSeparatesFreshOverdueFourCalendarDatesAndCompleted() {
        val items = listOf(
            item("fresh", registeredAt = "2026-08-30T04:30:00Z", change = LmsChangeState.NEW),
            item("overdue", dueAt = "2026-09-01T02:59:59Z", completion = LmsCompletionState.INCOMPLETE),
            item("today", dueAt = "2026-09-01T14:59:59Z"),
            item("tomorrow", dueAt = "2026-09-02T00:00:00Z"),
            item("third", dueAt = "2026-09-03T00:00:00Z"),
            item("fourth", dueAt = "2026-09-04T00:00:00Z"),
            item("outside", dueAt = "2026-09-05T00:00:00Z"),
            item("done", dueAt = "2026-08-20T00:00:00Z", completion = LmsCompletionState.COMPLETE),
        )

        val agenda = planner.plan(items)

        assertEquals(
            listOf(
                LmsAgendaGroupKey.FRESH,
                LmsAgendaGroupKey.OVERDUE,
                LmsAgendaGroupKey.DATE,
                LmsAgendaGroupKey.DATE,
                LmsAgendaGroupKey.DATE,
                LmsAgendaGroupKey.DATE,
                LmsAgendaGroupKey.COMPLETED,
            ),
            agenda.groups.map { it.key },
        )
        assertEquals(
            listOf(
                listOf("fresh"),
                listOf("overdue"),
                listOf("today"),
                listOf("tomorrow"),
                listOf("third"),
                listOf("fourth"),
                listOf("done"),
            ),
            agenda.groups.map { group -> group.items.map { it.id } },
        )
    }

    @Test
    fun unknownAndNotTrackedDueItemsRemainActionableWithoutACompletionBadge() {
        val agenda = planner.plan(
            listOf(
                item("unknown", dueAt = "2026-09-01T13:00:00Z", completion = LmsCompletionState.UNKNOWN),
                item("not-tracked", dueAt = "2026-09-01T14:00:00Z", completion = LmsCompletionState.NOT_TRACKED),
            ),
        )

        assertEquals(listOf("unknown", "not-tracked"), agenda.groups.single().items.map { it.id })
    }

    private fun item(
        id: String,
        registeredAt: String? = null,
        dueAt: String? = null,
        completion: LmsCompletionState = LmsCompletionState.UNKNOWN,
        change: LmsChangeState = LmsChangeState.NONE,
    ) = LmsItem(
        id = id,
        courseId = "course",
        courseName = "과목",
        kind = LmsItemKind.ASSIGNMENT,
        title = id,
        registeredAt = registeredAt?.let(Instant::parse),
        dueAt = dueAt?.let(Instant::parse),
        detailUrl = "https://lms.dima.ac.kr/item/$id",
        completionState = completion,
        changeState = change,
    )
}
