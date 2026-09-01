package com.example.dimanow.lms

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class LmsAgendaGroupKey { FRESH, OVERDUE, DATE, COMPLETED }

data class LmsAgendaGroup(
    val key: LmsAgendaGroupKey,
    val title: String,
    val items: List<LmsItem>,
    val date: LocalDate? = null,
)

data class LmsAgenda(val groups: List<LmsAgendaGroup>)

class LmsAgendaPlanner(
    private val clock: Clock,
    private val zoneId: ZoneId = ZoneId.of("Asia/Seoul"),
) {
    fun plan(items: List<LmsItem>, now: Instant = clock.instant()): LmsAgenda {
        val today = now.atZone(zoneId).toLocalDate()
        val completed = items.filter { it.completionState == LmsCompletionState.COMPLETE }
        val active = items.filterNot { it.completionState == LmsCompletionState.COMPLETE }
        val groups = buildList {
            active.filter { it.dueAt == null && it.changeState != LmsChangeState.NONE }
                .sortedByDescending { it.registeredAt }
                .takeIf { it.isNotEmpty() }
                ?.let { add(LmsAgendaGroup(LmsAgendaGroupKey.FRESH, "새 소식", it)) }

            active.filter { it.dueAt?.isBefore(now) == true }
                .sortedBy { it.dueAt }
                .takeIf { it.isNotEmpty() }
                ?.let { add(LmsAgendaGroup(LmsAgendaGroupKey.OVERDUE, "기한 지남", it)) }

            repeat(4) { dayOffset ->
                val date = today.plusDays(dayOffset.toLong())
                active.filter { item ->
                    val dueAt = item.dueAt ?: return@filter false
                    !dueAt.isBefore(now) && dueAt.atZone(zoneId).toLocalDate() == date
                }
                    .sortedBy { it.dueAt }
                    .takeIf { it.isNotEmpty() }
                    ?.let { dueItems ->
                        add(
                            LmsAgendaGroup(
                                key = LmsAgendaGroupKey.DATE,
                                title = if (dayOffset == 0) "오늘" else date.toString(),
                                items = dueItems,
                                date = date,
                            ),
                        )
                    }
            }

            completed.sortedByDescending { it.dueAt ?: it.registeredAt }
                .takeIf { it.isNotEmpty() }
                ?.let { add(LmsAgendaGroup(LmsAgendaGroupKey.COMPLETED, "완료한 학습", it)) }
        }
        return LmsAgenda(groups)
    }
}
