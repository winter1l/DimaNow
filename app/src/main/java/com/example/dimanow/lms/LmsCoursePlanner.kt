package com.example.dimanow.lms

import java.time.Instant

/**
 * 과목별 묶음 하나 (D-058, D-118): 아직 할 수 있는 항목, 기한이 지난 항목, 완료한 학습을 과목 안에서 나눈다.
 */
data class LmsCourseGroup(
    val courseId: String,
    val courseName: String,
    /** 기한이 남았거나 기한이 없는 항목. 임박한 기한 먼저, 그다음 최근 등록 순. */
    val items: List<LmsItem>,
    /** 기한이 지난 미완료 항목. 가장 최근에 지난 것부터. 화면에서는 접어 둔다. */
    val overdue: List<LmsItem> = emptyList(),
    /** 완료한 학습. 최근 것부터. 화면에서는 접어 둔다. */
    val completed: List<LmsItem> = emptyList(),
)

data class LmsCoursePlan(val groups: List<LmsCourseGroup>)

/**
 * 과목별 배치 (D-058, D-118).
 *
 * 순서는 공식 수강 과목 목록([courses])을 따른다. 목록에 없는 과목은 항목이 처음 나온 순서대로 뒤에 붙어,
 * 카탈로그가 아직 비어 있어도 항목이 사라지지 않는다. 완료한 학습만 남은 과목도 묶음으로 남는다. 과목 안에서는
 * 남은 항목, 기한 지남, 완료한 학습으로 나눈다 (D-118: 이전에는 완료한 학습을 모든 과목에서 모아 맨 아래에 뒀다).
 */
fun planLmsByCourse(items: List<LmsItem>, courses: List<LmsCourse>, now: Instant): LmsCoursePlan {
    val catalogOrder = courses.withIndex().associate { (index, course) -> course.id to index }
    val firstSeen = LinkedHashMap<String, Int>()
    items.forEach { item -> firstSeen.putIfAbsent(item.courseId, firstSeen.size) }

    val groups = items.groupBy { it.courseId }
        .toList()
        .sortedBy { (courseId, _) ->
            // 카탈로그에 있는 과목이 먼저, 없는 과목은 등장 순서대로 그 뒤에 온다
            catalogOrder[courseId] ?: (catalogOrder.size + (firstSeen[courseId] ?: 0))
        }
        .map { (courseId, courseItems) ->
            val completed = courseItems.filter { it.completionState == LmsCompletionState.COMPLETE }
            val active = courseItems.filterNot { it.completionState == LmsCompletionState.COMPLETE }
            LmsCourseGroup(
                courseId = courseId,
                courseName = courses.firstOrNull { it.id == courseId }?.name ?: courseItems.first().courseName,
                items = active.filterNot { it.dueAt?.isBefore(now) == true }.sortedWith(
                    compareBy<LmsItem> { it.dueAt == null }
                        .thenBy { it.dueAt }
                        .thenByDescending { it.registeredAt },
                ),
                overdue = active.filter { it.dueAt?.isBefore(now) == true }.sortedByDescending { it.dueAt },
                completed = completed.sortedByDescending { it.dueAt ?: it.registeredAt },
            )
        }
    return LmsCoursePlan(groups)
}
