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
    fun todoPutsEveryComingDeadlineFirstThenNewsThenMissedThenCompleted() {
        val items = listOf(
            item("fresh", registeredAt = "2026-08-30T04:30:00Z", change = LmsChangeState.NEW),
            item("long-overdue", dueAt = "2026-08-01T00:00:00Z", completion = LmsCompletionState.INCOMPLETE),
            item("overdue", dueAt = "2026-09-01T02:59:59Z", completion = LmsCompletionState.INCOMPLETE),
            item("tomorrow", dueAt = "2026-09-02T00:00:00Z"),
            item("today", dueAt = "2026-09-01T14:59:59Z"),
            // D-117: a deadline weeks away is part of 할 일 too, not only of 과목별.
            item("next-month", dueAt = "2026-10-05T00:00:00Z"),
            item("quiet", registeredAt = "2026-08-30T04:30:00Z"),
            item("done", dueAt = "2026-08-20T00:00:00Z", completion = LmsCompletionState.COMPLETE),
        )

        val agenda = planner.plan(items)

        assertEquals(
            listOf(LmsAgendaGroupKey.UPCOMING, LmsAgendaGroupKey.FRESH, LmsAgendaGroupKey.OVERDUE, LmsAgendaGroupKey.COMPLETED),
            agenda.groups.map { it.key },
        )
        assertEquals(
            listOf(
                listOf("today", "tomorrow", "next-month"),
                listOf("fresh"),
                listOf("overdue", "long-overdue"),
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
