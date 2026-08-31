package com.example.dimanow.lms

import java.time.Instant
import java.time.LocalDate

data class SavedLmsCredentials(
    val username: String,
    val password: String,
    val automaticLogin: Boolean = true,
)

enum class CredentialState { EMPTY, SAVED, INVALIDATED }

enum class LmsSessionState {
    SIGNED_OUT,
    AUTHENTICATING,
    ACTIVE,
    EXPIRED,
    CREDENTIALS_NEED_REVIEW,
    INTERACTIVE_AUTH_REQUIRED,
    ERROR,
}

enum class LmsItemKind { NOTICE, ASSIGNMENT, MATERIAL, OTHER }

enum class LmsSyncState { IDLE, SYNCING, READY, ERROR }

data class LmsTerm(val id: String, val label: String)

data class LmsCourse(
    val id: String,
    val name: String,
    val professor: String? = null,
)

data class LmsItem(
    val id: String,
    val courseId: String,
    val courseName: String,
    val kind: LmsItemKind,
    val title: String,
    val registeredAt: Instant? = null,
    val dueAt: Instant? = null,
    val detailUrl: String,
)

data class LmsAttachment(
    val id: String,
    val fileName: String,
    val downloadUrl: String,
    val sizeBytes: Long? = null,
)

data class LmsItemDetail(
    val item: LmsItem,
    val sanitizedHtml: String,
    val attachments: List<LmsAttachment> = emptyList(),
)

data class LmsSnapshot(
    val term: LmsTerm? = null,
    val courses: List<LmsCourse> = emptyList(),
    val items: List<LmsItem> = emptyList(),
    val syncState: LmsSyncState = LmsSyncState.IDLE,
    val lastSuccessAt: Instant? = null,
    val errorMessage: String? = null,
)

data class LmsAssignmentDates(
    val start: LocalDate? = null,
    val end: LocalDate? = null,
)
