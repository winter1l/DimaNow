package com.example.dimanow.lms

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RoomLmsAttachmentDownloadTest {
    @Test
    fun sourceReturnsTheTypedVerifiedDownloadResultAndPreservesTheOfficialRequest() = runTest {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            LmsCacheDatabase::class.java,
        ).allowMainThreadQueries().build()
        val request = LmsAttachmentRequest(
            method = LmsHttpMethod.POST,
            url = "https://lms.dima.ac.kr/lms/class/report/stud/doDownloadAttachFile.dunet",
            fields = linkedMapOf("attach_no" to "501", "report_no" to "301"),
            refererUrl = "https://lms.dima.ac.kr/lms/class/report/stud/doViewReportStudent.dunet?report_no=301",
        )
        val transport = RecordingAttachmentTransport(
            result = LmsAttachmentDownloadResult.Success(
                bytesWritten = 264_542,
                fileName = "assignment.pdf",
                contentType = "application/octet-stream",
            ),
        )
        val source: LmsSource = RoomLmsSource(
            database = database,
            sessionController = MutableLmsSessionController(LmsSessionState.ACTIVE),
            transport = transport,
        )
        val destination = File(
            ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir,
            "lms-source-download-${System.nanoTime()}.part",
        )

        try {
            val result = source.downloadAttachment(
                attachment = LmsAttachment(
                    id = "501",
                    fileName = "assignment.pdf",
                    downloadUrl = request.url,
                    sizeBytes = 264_542,
                    request = request,
                ),
                destination = destination,
            )

            assertEquals(transport.result, result)
            assertEquals(request, transport.recordedRequest)
        } finally {
            destination.delete()
            database.close()
        }
    }

    @Test
    fun legacyCachedAttachmentUsesAnOfficialDashboardReferer() = runTest {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            LmsCacheDatabase::class.java,
        ).allowMainThreadQueries().build()
        val transport = RecordingAttachmentTransport(
            result = LmsAttachmentDownloadResult.Success(
                bytesWritten = 264_542,
                fileName = "legacy.pdf",
                contentType = "application/octet-stream",
            ),
        )
        val source: LmsSource = RoomLmsSource(
            database = database,
            sessionController = MutableLmsSessionController(LmsSessionState.ACTIVE),
            transport = transport,
        )
        val downloadUrl = "https://lms.dima.ac.kr/lms/common/doDownloadFile.dunet?attach_no=501"
        val destination = File(
            ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir,
            "lms-legacy-download-${System.nanoTime()}.part",
        )

        try {
            source.downloadAttachment(
                attachment = LmsAttachment(
                    id = "501",
                    fileName = "legacy.pdf",
                    downloadUrl = downloadUrl,
                    sizeBytes = 264_542,
                    request = null,
                ),
                destination = destination,
            )

            assertEquals(
                LmsAttachmentRequest(
                    method = LmsHttpMethod.GET,
                    url = downloadUrl,
                    refererUrl = "https://lms.dima.ac.kr/lms/myLecture/doListView.dunet?to_do_type=all",
                ),
                transport.recordedRequest,
            )
        } finally {
            destination.delete()
            database.close()
        }
    }
}

private class RecordingAttachmentTransport(
    val result: LmsAttachmentDownloadResult,
) : LmsHttpTransport {
    var recordedRequest: LmsAttachmentRequest? = null

    override suspend fun get(url: String, maxBytes: Long): LmsHttpResponse = error("not used")

    override suspend fun postForm(
        url: String,
        fields: Map<String, String>,
        maxBytes: Long,
    ): LmsHttpResponse = error("not used")

    override suspend fun downloadAttachment(
        request: LmsAttachmentRequest,
        destination: File,
        suggestedFileName: String,
        maxBytes: Long,
        onProgress: (Long, Long?) -> Unit,
    ): LmsAttachmentDownloadResult {
        recordedRequest = request
        return result
    }
}
