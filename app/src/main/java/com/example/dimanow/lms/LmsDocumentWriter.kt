package com.example.dimanow.lms

import java.io.File
import java.io.OutputStream

internal fun createLmsAttachmentCacheFile(cacheDirectory: File): File {
    val attachmentDirectory = File(cacheDirectory, "lms-attachments").apply {
        check(isDirectory || mkdirs()) { "첨부파일 임시 폴더를 만들지 못했습니다" }
    }
    val reserved = File.createTempFile("attachment-", ".cache", attachmentDirectory)
    check(reserved.delete()) { "첨부파일 임시 경로를 준비하지 못했습니다" }
    return reserved
}

sealed interface LmsDocumentWriteResult {
    data class Success(val bytesWritten: Long) : LmsDocumentWriteResult
    data class Failure(val message: String) : LmsDocumentWriteResult
}

class LmsDocumentWriter<T>(
    private val openOutputStream: (T) -> OutputStream?,
    private val deleteDocument: (T) -> Boolean,
) {
    fun write(source: File, destination: T, expectedBytes: Long): LmsDocumentWriteResult {
        if (expectedBytes <= 0L || !source.isFile || source.length() != expectedBytes) {
            source.delete()
            return LmsDocumentWriteResult.Failure("첨부파일 크기를 확인하지 못했습니다")
        }
        return try {
            val output = openOutputStream(destination)
                ?: return failedWrite(source, destination, "저장 위치를 열지 못했습니다")
            val copied = output.use { target ->
                source.inputStream().use { input -> input.copyTo(target) }
                    .also { target.flush() }
            }
            if (copied != expectedBytes) {
                failedWrite(source, destination, "첨부파일을 모두 저장하지 못했습니다")
            } else {
                source.delete()
                LmsDocumentWriteResult.Success(copied)
            }
        } catch (error: Throwable) {
            failedWrite(source, destination, error.message ?: "첨부파일을 저장하지 못했습니다")
        }
    }

    private fun failedWrite(source: File, destination: T, message: String): LmsDocumentWriteResult.Failure {
        runCatching { deleteDocument(destination) }
        source.delete()
        return LmsDocumentWriteResult.Failure(message)
    }
}
