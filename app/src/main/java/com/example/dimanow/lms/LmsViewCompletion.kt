package com.example.dimanow.lms

/**
 * What opening an item counts for on the LMS (D-132). A 자료 or 공지 is taken to be done once the student opens it,
 * so when its official view page (`doViewBoardItem`, the page the site's own `fnGoContent` opens) has loaded in the
 * signed-in session, the cache records it complete right away and it leaves 할 일 without waiting for the next sync.
 * One with files counts only once they are downloaded ([completionAfterOpening], D-133). Every other kind needs
 * real work on the LMS (submitting, answering, watching), so opening it records nothing.
 *
 * Returns the completion to store after that view, or null to leave the stored completion as it is.
 */
internal fun completionRecordedByView(kind: LmsItemKind): LmsCompletionState? = when (kind) {
    LmsItemKind.NOTICE, LmsItemKind.MATERIAL -> LmsCompletionState.COMPLETE
    LmsItemKind.ASSIGNMENT,
    LmsItemKind.CONTENT,
    LmsItemKind.QUESTION,
    LmsItemKind.DISCUSSION,
    LmsItemKind.TEAM_PROJECT,
    LmsItemKind.QUIZ,
    LmsItemKind.EXAM,
    LmsItemKind.OTHER,
    -> null
}

/**
 * D-133: the completion to store once an opened 자료/공지's files were asked for. The LMS counts a 자료 with files only
 * when a file is downloaded: observed 2026-10-26, its official view page alone left 「화면구성 I」 on the LMS's to-do
 * list, and one `doDownloadFile` request for its PDF, cut off after the headers, took it off. So with files it is
 * complete only when every file was [LmsAttachmentTouchResult.Accepted]; otherwise it stays as it was and the next
 * open asks again. Without files the view is all there is (D-132).
 *
 * [asked] is how many of its files were asked for and [accepted] how many came back as files.
 */
internal fun completionAfterOpening(kind: LmsItemKind, asked: Int, accepted: Int): LmsCompletionState? {
    val viewed = completionRecordedByView(kind) ?: return null
    return viewed.takeIf { accepted == asked }
}

/**
 * The completion one sync stores for an item (D-047's authoritative lists, with D-132's mark on view).
 *
 * - The LMS's answer wins whenever it gives one: an item on its 완료한 학습 list is complete, one on its 미완료한 학습
 *   list is not, even if the app marked it complete when it was opened (it shows in 할 일 again).
 * - When either list could not be read, nothing is known, so the stored state stays, the app's mark included.
 * - When both lists were read and the item is on neither, the LMS does not track it: NOT_TRACKED, except that a
 *   자료/공지 already stored complete (opened in the app, or reported complete before) stays complete, because the LMS
 *   has nothing to say against it and the student has opened it.
 *
 * [reported] is the list the item was found on in this sync (null when on neither or the list was not read),
 * [readBothLists] whether this sync read both lists, and [stored] the completion the cache held before it.
 */
internal fun reconcileLmsCompletion(
    kind: LmsItemKind,
    reported: LmsCompletionState?,
    readBothLists: Boolean,
    stored: LmsCompletionState?,
): LmsCompletionState = when {
    reported != null -> reported
    !readBothLists -> stored ?: LmsCompletionState.UNKNOWN
    stored == LmsCompletionState.COMPLETE && completionRecordedByView(kind) == LmsCompletionState.COMPLETE ->
        LmsCompletionState.COMPLETE
    else -> LmsCompletionState.NOT_TRACKED
}
