package com.example.dimanow.lms

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** D-132: opening a 자료/공지 completes it in the cache at once; the LMS's lists settle it at the next sync. */
class LmsViewCompletionTest {
    private val now = Instant.parse("2026-10-26T03:00:00Z")
    private val planner = LmsAgendaPlanner(Clock.fixed(now, ZoneId.of("Asia/Seoul")))

    private fun item(
        id: String,
        kind: LmsItemKind,
        dueAt: String? = "2026-10-30T14:59:00Z",
        completion: LmsCompletionState = LmsCompletionState.INCOMPLETE,
    ) = LmsItem(
        id = id,
        courseId = "audio",
        courseName = "음향기초실습",
        kind = kind,
        title = id,
        dueAt = dueAt?.let(Instant::parse),
        detailUrl = "https://lms.dima.ac.kr/lms/class/boardItem/doViewBoardItem.dunet?boarditem_no=$id",
        completionState = completion,
    )

    /** What [RoomLmsSource.loadDetail] stores once the item's official view page has loaded. */
    private fun opened(item: LmsItem) = item.copy(
        isRead = true,
        completionState = completionRecordedByView(item.kind) ?: item.completionState,
    )

    /** What [RoomLmsSource.refresh] stores for each cached item, given the LMS's 완료/미완료 lists. */
    private fun synced(
        items: List<LmsItem>,
        reported: Map<String, LmsCompletionState>,
        readBothLists: Boolean = true,
    ) = items.map { item ->
        item.copy(
            completionState = reconcileLmsCompletion(item.kind, reported[item.id], readBothLists, item.completionState),
        )
    }

    private fun todo(items: List<LmsItem>): Map<LmsAgendaGroupKey, List<String>> =
        planner.plan(items).groups.associate { group -> group.key to group.items.map { it.id } }

    @Test
    fun onlyMaterialsAndNoticesAreCompletedByOpeningThem() {
        LmsItemKind.entries.forEach { kind ->
            val expected = if (kind == LmsItemKind.NOTICE || kind == LmsItemKind.MATERIAL) {
                LmsCompletionState.COMPLETE
            } else {
                null
            }
            assertEquals(kind.name, expected, completionRecordedByView(kind))
        }
    }

    @Test
    fun openingAMaterialOrNoticeTakesItOutOfTodoAtOnceWhileAnAssignmentStays() {
        val material = item("material", LmsItemKind.MATERIAL)
        val notice = item("notice", LmsItemKind.NOTICE, dueAt = "2026-10-20T00:00:00Z")
        val assignment = item("assignment", LmsItemKind.ASSIGNMENT)
        val video = item("video", LmsItemKind.CONTENT)

        val before = listOf(material, notice, assignment, video)
        assertEquals(
            mapOf(
                LmsAgendaGroupKey.UPCOMING to listOf("material", "assignment", "video"),
                LmsAgendaGroupKey.OVERDUE to listOf("notice"),
            ),
            todo(before),
        )

        val after = before.map(::opened)

        assertEquals(
            mapOf(
                LmsAgendaGroupKey.UPCOMING to listOf("assignment", "video"),
                LmsAgendaGroupKey.COMPLETED to listOf("material", "notice"),
            ),
            todo(after),
        )
        // 과목별 moves them into the course's 완료한 학습 fold the same way.
        val course = planLmsByCourse(after, listOf(LmsCourse("audio", "음향기초실습")), now).groups.single()
        assertEquals(listOf("assignment", "video"), course.items.map { it.id })
        assertEquals(emptyList<String>(), course.overdue.map { it.id })
        assertEquals(setOf("material", "notice"), course.completed.map { it.id }.toSet())
    }

    @Test
    fun theNextSyncShowsItAgainWhenTheLmsStillListsItAsNotDone() {
        val material = opened(item("material", LmsItemKind.MATERIAL))
        assertEquals(LmsCompletionState.COMPLETE, material.completionState)

        val afterSync = synced(listOf(material), mapOf("material" to LmsCompletionState.INCOMPLETE))

        assertEquals(LmsCompletionState.INCOMPLETE, afterSync.single().completionState)
        assertEquals(mapOf(LmsAgendaGroupKey.UPCOMING to listOf("material")), todo(afterSync))
    }

    @Test
    fun theNextSyncKeepsItDoneWhenTheLmsListsItAsDone() {
        val notice = opened(item("notice", LmsItemKind.NOTICE))

        val afterSync = synced(listOf(notice), mapOf("notice" to LmsCompletionState.COMPLETE))

        assertEquals(LmsCompletionState.COMPLETE, afterSync.single().completionState)
        assertEquals(mapOf(LmsAgendaGroupKey.COMPLETED to listOf("notice")), todo(afterSync))
    }

    @Test
    fun aSyncThatCouldNotReadBothListsKeepsTheMarkUntilOneDoes() {
        val notice = opened(item("notice", LmsItemKind.NOTICE))

        val unanswered = synced(listOf(notice), reported = emptyMap(), readBothLists = false)
        assertEquals(LmsCompletionState.COMPLETE, unanswered.single().completionState)

        // Only the 미완료한 학습 list was read, and it still lists the notice: the LMS has answered.
        val answered = synced(
            unanswered,
            reported = mapOf("notice" to LmsCompletionState.INCOMPLETE),
            readBothLists = false,
        )
        assertEquals(LmsCompletionState.INCOMPLETE, answered.single().completionState)
    }

    @Test
    fun anOpenedMaterialOnNeitherListStaysDoneWhileOtherUntrackedItemsAreNotTracked() {
        val openedMaterial = opened(item("opened-material", LmsItemKind.MATERIAL))
        val unopenedNotice = item("unopened-notice", LmsItemKind.NOTICE)
        val openedAssignment = opened(item("opened-assignment", LmsItemKind.ASSIGNMENT))
        val reportedAssignment = item("reported-assignment", LmsItemKind.ASSIGNMENT, completion = LmsCompletionState.COMPLETE)

        val afterSync = synced(
            listOf(openedMaterial, unopenedNotice, openedAssignment, reportedAssignment),
            reported = emptyMap(),
        ).associate { it.id to it.completionState }

        assertEquals(
            mapOf(
                "opened-material" to LmsCompletionState.COMPLETE,
                "unopened-notice" to LmsCompletionState.NOT_TRACKED,
                "opened-assignment" to LmsCompletionState.NOT_TRACKED,
                "reported-assignment" to LmsCompletionState.NOT_TRACKED,
            ),
            afterSync,
        )
    }

    @Test
    fun aNewItemTakesWhatTheListsSayOrStaysUnknown() {
        assertEquals(
            LmsCompletionState.INCOMPLETE,
            reconcileLmsCompletion(LmsItemKind.NOTICE, LmsCompletionState.INCOMPLETE, readBothLists = true, stored = null),
        )
        assertEquals(
            LmsCompletionState.NOT_TRACKED,
            reconcileLmsCompletion(LmsItemKind.NOTICE, reported = null, readBothLists = true, stored = null),
        )
        assertEquals(
            LmsCompletionState.UNKNOWN,
            reconcileLmsCompletion(LmsItemKind.MATERIAL, reported = null, readBothLists = false, stored = null),
        )
        assertNull(completionRecordedByView(LmsItemKind.QUIZ))
    }

    @Test
    fun aMaterialWithFilesIsCompleteOnlyWhenEveryFileCameBack() {
        // D-133: no files, the view alone; with files, all of them.
        assertEquals(LmsCompletionState.COMPLETE, completionAfterOpening(LmsItemKind.MATERIAL, asked = 0, accepted = 0))
        assertEquals(LmsCompletionState.COMPLETE, completionAfterOpening(LmsItemKind.MATERIAL, asked = 2, accepted = 2))
        assertNull(completionAfterOpening(LmsItemKind.MATERIAL, asked = 2, accepted = 1))
        assertNull(completionAfterOpening(LmsItemKind.MATERIAL, asked = 1, accepted = 0))
        assertEquals(LmsCompletionState.COMPLETE, completionAfterOpening(LmsItemKind.NOTICE, asked = 1, accepted = 1))
        // Other kinds are never completed by opening, files or not.
        assertNull(completionAfterOpening(LmsItemKind.ASSIGNMENT, asked = 0, accepted = 0))
        assertNull(completionAfterOpening(LmsItemKind.CONTENT, asked = 1, accepted = 1))
    }
}
