package com.example.dimanow.lms

import java.time.Clock
import java.time.Instant

/** The 할 일 sections in the order they are shown (D-117). */
enum class LmsAgendaGroupKey { UPCOMING, FRESH, OVERDUE, COMPLETED }

data class LmsAgendaGroup(
    val key: LmsAgendaGroupKey,
    val title: String,
    val items: List<LmsItem>,
)

data class LmsAgenda(val groups: List<LmsAgendaGroup>)

/**
 * The 수업 tab's 할 일 (D-117, reworking D-047's today agenda): every deadline still ahead, nearest first, so a
 * deadline next week shows here and not only under 과목별; then undated news; then missed deadlines, most recently
 * missed first, below what can still be done on time; completed learning last.
 */
class LmsAgendaPlanner(private val clock: Clock) {
    fun plan(items: List<LmsItem>, now: Instant = clock.instant()): LmsAgenda {
        val completed = items.filter { it.completionState == LmsCompletionState.COMPLETE }
        val active = items.filterNot { it.completionState == LmsCompletionState.COMPLETE }
        val groups = buildList {
            active.filter { it.dueAt?.isBefore(now) == false }
                .sortedBy { it.dueAt }
                .takeIf { it.isNotEmpty() }
                ?.let { add(LmsAgendaGroup(LmsAgendaGroupKey.UPCOMING, "다가오는 마감", it)) }

            active.filter { it.dueAt == null && it.changeState != LmsChangeState.NONE }
                .sortedByDescending { it.registeredAt }
                .takeIf { it.isNotEmpty() }
                ?.let { add(LmsAgendaGroup(LmsAgendaGroupKey.FRESH, "새 소식", it)) }

            active.filter { it.dueAt?.isBefore(now) == true }
                .sortedByDescending { it.dueAt }
                .takeIf { it.isNotEmpty() }
                ?.let { add(LmsAgendaGroup(LmsAgendaGroupKey.OVERDUE, "기한 지남", it)) }

            completed.sortedByDescending { it.dueAt ?: it.registeredAt }
                .takeIf { it.isNotEmpty() }
                ?.let { add(LmsAgendaGroup(LmsAgendaGroupKey.COMPLETED, "완료한 학습", it)) }
        }
        return LmsAgenda(groups)
    }
}
