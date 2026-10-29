package com.example.dimanow.lms

sealed interface LmsAttachmentDownloadResult {
    data class Success(
        val bytesWritten: Long,
        val fileName: String,
        val contentType: String?,
    ) : LmsAttachmentDownloadResult

    data object SessionExpired : LmsAttachmentDownloadResult

    data class Failure(val message: String) : LmsAttachmentDownloadResult
}

/** What asking for an attachment without keeping it found (D-133). */
sealed interface LmsAttachmentTouchResult {
    /** The LMS answered with the file itself, so it has counted the download. */
    data object Accepted : LmsAttachmentTouchResult

    data object SessionExpired : LmsAttachmentTouchResult

    data class Failure(val message: String) : LmsAttachmentTouchResult
}
