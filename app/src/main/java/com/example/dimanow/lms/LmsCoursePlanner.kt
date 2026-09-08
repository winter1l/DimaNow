package com.example.dimanow.lms

/** 전체 모드에서 과목 하나가 차지하는 묶음 (D-058). */
data class LmsCourseGroup(
    val courseId: String,
    val courseName: String,
    val items: List<LmsItem>,
)

/**
 * 전체 모드 배치.
 *
 * @param groups 과목 순서대로 늘어놓은 미완료 항목 묶음.
 * @param completed 완료한 학습. 오늘 탭과 같이 목록 맨 아래에 접어서 보여준다.
 */
data class LmsCoursePlan(
    val groups: List<LmsCourseGroup>,
    val completed: List<LmsItem>,
)

/**
 * 전체 목록을 과목 순서로 묶고 완료한 학습을 아래로 내린다 (D-058).
 *
 * 순서는 공식 수강 과목 목록([courses])을 따른다. 목록에 없는 과목은 항목이 처음 나온
 * 순서대로 뒤에 붙어, 카탈로그가 아직 비어 있어도 항목이 사라지지 않는다. 과목 안에서는
 * 기한이 있는 항목을 임박한 순서로 먼저 두고, 기한이 없는 항목은 최근 등록 순으로 잇는다.
 */
fun planLmsByCourse(items: List<LmsItem>, courses: List<LmsCourse>): LmsCoursePlan {
    val completed = items.filter { it.completionState == LmsCompletionState.COMPLETE }
        .sortedByDescending { it.dueAt ?: it.registeredAt }
    val active = items.filterNot { it.completionState == LmsCompletionState.COMPLETE }

    val catalogOrder = courses.withIndex().associate { (index, course) -> course.id to index }
    val firstSeen = LinkedHashMap<String, Int>()
    active.forEach { item -> firstSeen.putIfAbsent(item.courseId, firstSeen.size) }

    val groups = active.groupBy { it.courseId }
        .toList()
        .sortedBy { (courseId, _) ->
            // 카탈로그에 있는 과목이 먼저, 없는 과목은 등장 순서대로 그 뒤에 온다
            catalogOrder[courseId] ?: (catalogOrder.size + (firstSeen[courseId] ?: 0))
        }
        .map { (courseId, courseItems) ->
            LmsCourseGroup(
                courseId = courseId,
                courseName = courses.firstOrNull { it.id == courseId }?.name
                    ?: courseItems.first().courseName,
                items = courseItems.sortedWith(
                    compareBy<LmsItem> { it.dueAt == null }
                        .thenBy { it.dueAt }
                        .thenByDescending { it.registeredAt },
                ),
            )
        }
    return LmsCoursePlan(groups = groups, completed = completed)
}
