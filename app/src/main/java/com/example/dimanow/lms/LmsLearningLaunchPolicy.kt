package com.example.dimanow.lms

import java.net.URI

internal fun canRenderLmsItemNatively(kind: LmsItemKind): Boolean = when (kind) {
    LmsItemKind.NOTICE,
    LmsItemKind.MATERIAL,
    LmsItemKind.ASSIGNMENT,
    -> true

    LmsItemKind.CONTENT,
    LmsItemKind.QUESTION,
    LmsItemKind.DISCUSSION,
    LmsItemKind.TEAM_PROJECT,
    LmsItemKind.QUIZ,
    LmsItemKind.EXAM,
    LmsItemKind.OTHER,
    -> false
}

internal enum class LmsLearningLaunchState {
    LOCATING,
    REQUESTED,
    PLAYER_OPENED,
    OFFICIAL_FALLBACK,
    OFFICIAL_FALLBACK_OPENED,
}

internal enum class LmsLearningLaunchEvent {
    EXACT_CANDIDATE_STARTED,
    EXACT_CANDIDATE_UNAVAILABLE,
    PLAYER_PAGE_REACHED,
    WATCHDOG_EXPIRED,
    OFFICIAL_FALLBACK_DISPLAYED,
}

internal fun reduceOfficialLearningLaunch(
    state: LmsLearningLaunchState,
    event: LmsLearningLaunchEvent,
): LmsLearningLaunchState = when (event) {
    LmsLearningLaunchEvent.EXACT_CANDIDATE_STARTED -> if (state == LmsLearningLaunchState.LOCATING) {
        LmsLearningLaunchState.REQUESTED
    } else {
        state
    }
    LmsLearningLaunchEvent.EXACT_CANDIDATE_UNAVAILABLE -> if (
        state == LmsLearningLaunchState.LOCATING || state == LmsLearningLaunchState.REQUESTED
    ) {
        LmsLearningLaunchState.OFFICIAL_FALLBACK
    } else {
        state
    }
    LmsLearningLaunchEvent.PLAYER_PAGE_REACHED -> LmsLearningLaunchState.PLAYER_OPENED
    LmsLearningLaunchEvent.WATCHDOG_EXPIRED -> if (state == LmsLearningLaunchState.REQUESTED) {
        LmsLearningLaunchState.OFFICIAL_FALLBACK
    } else {
        state
    }
    LmsLearningLaunchEvent.OFFICIAL_FALLBACK_DISPLAYED -> if (
        state == LmsLearningLaunchState.OFFICIAL_FALLBACK
    ) {
        LmsLearningLaunchState.OFFICIAL_FALLBACK_OPENED
    } else {
        state
    }
}

internal fun didConfirmedLearningBecomeOpened(
    previous: LmsLearningLaunchState,
    current: LmsLearningLaunchState,
): Boolean = previous != current && current in setOf(
    LmsLearningLaunchState.PLAYER_OPENED,
    LmsLearningLaunchState.OFFICIAL_FALLBACK_OPENED,
)

internal fun selectOfficialLearningCandidate(itemTitle: String, candidateTitles: List<String>): Int? {
    val target = normalizeOfficialLearningTitle(itemTitle)
    if (target.isBlank()) return null
    val matches = candidateTitles.mapIndexedNotNull { index, candidate ->
        index.takeIf { normalizeOfficialLearningTitle(candidate) == target }
    }
    return matches.singleOrNull()
}

internal fun normalizeOfficialLearningTitle(value: String): String = value
    .replace(COURSE_TITLE_PREFIX, "")
    .replace(LEARNING_PROGRESS_SUFFIX, "")
    .replace(COURSE_PAGE_MEDIA_SUFFIX, "")
    .replace(WHITESPACE, " ")
    .trim()

internal fun shouldConfirmOfficialLearningDialog(url: String, message: String): Boolean = runCatching {
    val uri = URI.create(url)
    uri.scheme.equals("https", ignoreCase = true) &&
        uri.host.equals("lms.dima.ac.kr", ignoreCase = true) &&
        uri.userInfo == null &&
        uri.port in setOf(-1, 443) &&
        uri.path.orEmpty().startsWith("/lms/class/courseSchedule/") &&
        message.contains("학습") && message.contains("시작")
}.getOrDefault(false)

private val COURSE_TITLE_PREFIX = Regex("^\\[[^]]+]\\s*")
private val LEARNING_PROGRESS_SUFFIX = Regex("\\s*\\(?\\s*\\d+\\s*분\\s*/\\s*\\d+\\s*분\\s*\\)?\\s*$")
private val COURSE_PAGE_MEDIA_SUFFIX = Regex(
    "\\s*\\(영상콘텐츠\\([^)]+\\)\\)\\s*\\|\\s*출석인정시간\\s*:\\s*\\d+\\s*분\\s*$",
)
private val WHITESPACE = Regex("\\s+")
