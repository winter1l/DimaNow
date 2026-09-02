package com.example.dimanow.lms

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LmsDocumentWriterTest {
    @Test
    fun temporaryDownloadCacheUsesRandomAppOwnedNames() {
        val directory = Files.createTempDirectory("lms-attachment-cache").toFile()

        val first = createLmsAttachmentCacheFile(directory)
        val second = createLmsAttachmentCacheFile(directory)

        assertEquals(File(directory, "lms-attachments").canonicalFile, first.parentFile.canonicalFile)
        assertEquals(File(directory, "lms-attachments").canonicalFile, second.parentFile.canonicalFile)
        assertTrue(first.name.startsWith("attachment-"))
        assertTrue(second.name.startsWith("attachment-"))
        assertFalse(first.exists())
        assertFalse(second.exists())
        assertTrue(first.name != second.name)
        directory.deleteRecursively()
    }

    @Test
    fun writesEveryVerifiedByteBeforeReportingSuccessAndCleansTheCache() {
        val directory = Files.createTempDirectory("lms-document-writer").toFile()
        val cache = File(directory, "attachment.bin").apply {
            writeBytes(byteArrayOf(0x10, 0x20, 0x30, 0x40))
        }
        val output = ByteArrayOutputStream()
        val deleted = mutableListOf<String>()
        val writer = LmsDocumentWriter<String>(
            openOutputStream = { output },
            deleteDocument = { deleted += it; true },
        )

        val result = writer.write(cache, "document://saved", expectedBytes = 4)

        assertEquals(LmsDocumentWriteResult.Success(bytesWritten = 4), result)
        assertArrayEquals(byteArrayOf(0x10, 0x20, 0x30, 0x40), output.toByteArray())
        assertTrue(deleted.isEmpty())
        assertFalse(cache.exists())
        directory.delete()
    }

    @Test
    fun nullOutputNeverReportsSuccessAndDeletesTheEmptyDocumentBestEffort() {
        val directory = Files.createTempDirectory("lms-document-writer-null").toFile()
        val cache = File(directory, "attachment.bin").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val deleted = mutableListOf<String>()
        val writer = LmsDocumentWriter<String>(
            openOutputStream = { null },
            deleteDocument = { deleted += it; true },
        )

        val result = writer.write(cache, "document://empty", expectedBytes = 3)

        assertTrue(result is LmsDocumentWriteResult.Failure)
        assertEquals(listOf("document://empty"), deleted)
        assertFalse(cache.exists())
        directory.delete()
    }

    @Test
    fun aPartialOrFailedCopyDeletesTheCreatedDocumentAndTheTemporaryCache() {
        val directory = Files.createTempDirectory("lms-document-writer-partial").toFile()
        val cache = File(directory, "attachment.bin").apply { writeBytes(byteArrayOf(1, 2, 3, 4, 5)) }
        val deleted = mutableListOf<String>()
        val failingOutput = object : OutputStream() {
            private var written = 0
            override fun write(value: Int) {
                if (written == 2) error("disk full")
                written += 1
            }
        }
        val writer = LmsDocumentWriter<String>(
            openOutputStream = { failingOutput },
            deleteDocument = { deleted += it; true },
        )

        val result = writer.write(cache, "document://partial", expectedBytes = 5)

        assertTrue(result is LmsDocumentWriteResult.Failure)
        assertEquals(listOf("document://partial"), deleted)
        assertFalse(cache.exists())
        directory.delete()
    }

    @Test
    fun mismatchedVerifiedLengthDoesNotOpenOrCreateTheDestination() {
        val directory = Files.createTempDirectory("lms-document-writer-length").toFile()
        val cache = File(directory, "attachment.bin").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        var opened = false
        val writer = LmsDocumentWriter<String>(
            openOutputStream = { opened = true; ByteArrayOutputStream() },
            deleteDocument = { true },
        )

        val result = writer.write(cache, "document://unused", expectedBytes = 4)

        assertTrue(result is LmsDocumentWriteResult.Failure)
        assertFalse(opened)
        assertFalse(cache.exists())
        directory.delete()
    }
}
