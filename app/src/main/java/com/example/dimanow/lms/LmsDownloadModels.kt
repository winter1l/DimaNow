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
