package com.example.dimanow.lms

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlConnectionLmsTransportTest {
    @Test
    fun emptyAttachmentIsRejectedWithoutCreatingFinalFile() = runTest {
        val connection = FakeConnection(
            url = URL("https://lms.dima.ac.kr/lms/common/doDownloadFile.dunet"),
            status = 200,
            body = ByteArray(0),
            contentType = "application/octet-stream",
        )
        val transport = UrlConnectionLmsTransport(
            cookieProvider = { "SESSION=ready" },
            connectionFactory = { connection },
        )
        val directory = File(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "lms-download-empty-${System.nanoTime()}",
        ).apply { mkdirs() }
        val destination = File(directory, "lecture.pdf")

        try {
            val result = transport.downloadAttachment(
                request = LmsAttachmentRequest(
                    method = LmsHttpMethod.GET,
                    url = connection.url.toString(),
                    refererUrl = "https://lms.dima.ac.kr/lms/class/boardItem/doViewBoardItem.dunet?boarditem_no=91",
                ),
                destination = destination,
                suggestedFileName = "lecture.pdf",
                maxBytes = 512L * 1024 * 1024,
                onProgress = { _, _ -> },
            )

            assertTrue(result is LmsAttachmentDownloadResult.Failure)
            assertFalse(destination.exists())
            assertFalse(File(directory, "lecture.pdf.part").exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun attachmentWithMismatchedDeclaredLengthIsRejected() = runTest {
        val connection = FakeConnection(
            url = URL("https://lms.dima.ac.kr/lms/common/doDownloadFile.dunet"),
            status = 200,
            body = byteArrayOf(1, 2, 3),
            headers = mapOf("Content-Length" to listOf("4")),
            contentType = "application/octet-stream",
            declaredLength = 4,
        )
        val transport = UrlConnectionLmsTransport(
            cookieProvider = { "SESSION=ready" },
            connectionFactory = { connection },
        )
        val directory = File(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "lms-download-length-${System.nanoTime()}",
        ).apply { mkdirs() }
        val destination = File(directory, "lecture.pdf")

        try {
            val result = transport.downloadAttachment(
                request = LmsAttachmentRequest(
                    method = LmsHttpMethod.GET,
                    url = connection.url.toString(),
                    refererUrl = "https://lms.dima.ac.kr/lms/class/boardItem/doViewBoardItem.dunet?boarditem_no=91",
                ),
                destination = destination,
                suggestedFileName = "lecture.pdf",
                maxBytes = 512L * 1024 * 1024,
                onProgress = { _, _ -> },
            )

            assertTrue(result is LmsAttachmentDownloadResult.Failure)
            assertFalse(destination.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun connectionDeclaredLengthMismatchIsRejectedEvenWhenHeaderMapOmitsIt() = runTest {
        val connection = FakeConnection(
            url = URL("https://lms.dima.ac.kr/lms/common/doDownloadFile.dunet"),
            status = 200,
            body = byteArrayOf(1, 2, 3),
            contentType = "application/octet-stream",
            declaredLength = 4,
        )
        val transport = UrlConnectionLmsTransport(
            cookieProvider = { "SESSION=ready" },
            connectionFactory = { connection },
        )
        val directory = File(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "lms-download-connection-length-${System.nanoTime()}",
        ).apply { mkdirs() }
        val destination = File(directory, "lecture.pdf")

        try {
            val result = transport.downloadAttachment(
                request = LmsAttachmentRequest(
                    method = LmsHttpMethod.GET,
                    url = connection.url.toString(),
                    refererUrl = "https://lms.dima.ac.kr/lms/class/boardItem/doViewBoardItem.dunet?boarditem_no=91",
                ),
                destination = destination,
                suggestedFileName = "lecture.pdf",
                maxBytes = 512L * 1024 * 1024,
                onProgress = { _, _ -> },
            )

            assertTrue(result is LmsAttachmentDownloadResult.Failure)
            assertFalse(destination.exists())
            assertFalse(File(directory, "lecture.pdf.part").exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun loginHtmlAttachmentResponseExpiresTheSessionWithoutSavingIt() = runTest {
        val body = """
            <!doctype html><html><body>
              <form action="/login/doLogin.dunet"><input id="id"><input id="pass"></form>
            </body></html>
        """.trimIndent().toByteArray()
        val connection = FakeConnection(
            url = URL("https://lms.dima.ac.kr/lms/common/doDownloadFile.dunet"),
            status = 200,
            body = body,
            contentType = "text/html; charset=UTF-8",
        )
        val transport = UrlConnectionLmsTransport(
            cookieProvider = { "SESSION=expired" },
            connectionFactory = { connection },
        )
        val directory = File(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "lms-download-login-${System.nanoTime()}",
        ).apply { mkdirs() }
        val destination = File(directory, "login.html")

        try {
            val result = transport.downloadAttachment(
                request = LmsAttachmentRequest(
                    method = LmsHttpMethod.GET,
                    url = connection.url.toString(),
                    refererUrl = "https://lms.dima.ac.kr/lms/class/boardItem/doViewBoardItem.dunet?boarditem_no=91",
                ),
                destination = destination,
                suggestedFileName = "lecture.pdf",
                maxBytes = 512L * 1024 * 1024,
                onProgress = { _, _ -> },
            )

            assertEquals(LmsAttachmentDownloadResult.SessionExpired, result)
            assertFalse(destination.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun loginHtmlDisguisedAsOctetStreamStillExpiresTheSession() = runTest {
        val body = "<html><body><form><input id='id'><input id='pass'></form></body></html>".toByteArray()
        val connection = FakeConnection(
            url = URL("https://lms.dima.ac.kr/lms/common/doDownloadFile.dunet"),
            status = 200,
            body = body,
            contentType = "application/octet-stream",
        )
        val transport = UrlConnectionLmsTransport(
            cookieProvider = { "SESSION=expired" },
            connectionFactory = { connection },
        )
        val directory = File(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "lms-download-octet-login-${System.nanoTime()}",
        ).apply { mkdirs() }
        val destination = File(directory, "login.bin")

        try {
            val result = transport.downloadAttachment(
                request = LmsAttachmentRequest(
                    method = LmsHttpMethod.GET,
                    url = connection.url.toString(),
                    refererUrl = "https://lms.dima.ac.kr/lms/class/boardItem/doViewBoardItem.dunet?boarditem_no=91",
                ),
                destination = destination,
                suggestedFileName = "lecture.pdf",
                maxBytes = 512L * 1024 * 1024,
                onProgress = { _, _ -> },
            )

            assertEquals(LmsAttachmentDownloadResult.SessionExpired, result)
            assertFalse(destination.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun documentMarkupDisguisedAsOctetStreamIsRejectedWithoutSavingIt() = runTest {
        val markupBodies = listOf(
            "<body><h1>Internal server error</h1></body>",
            "<head><title>Download failed</title></head>",
            "<meta http-equiv='refresh' content='0;url=/error'>",
        )

        markupBodies.forEachIndexed { index, markup ->
            val connection = FakeConnection(
                url = URL("https://lms.dima.ac.kr/lms/common/doDownloadFile.dunet"),
                status = 200,
                body = markup.toByteArray(),
                contentType = "application/octet-stream",
            )
            val transport = UrlConnectionLmsTransport(
                cookieProvider = { "SESSION=ready" },
                connectionFactory = { connection },
            )
            val directory = File(
                InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
                "lms-download-markup-$index-${System.nanoTime()}",
            ).apply { mkdirs() }
            val destination = File(directory, "error.bin")

            try {
                val result = transport.downloadAttachment(
                    request = LmsAttachmentRequest(
                        method = LmsHttpMethod.GET,
                        url = connection.url.toString(),
                        refererUrl = "https://lms.dima.ac.kr/lms/class/boardItem/doViewBoardItem.dunet?boarditem_no=91",
                    ),
                    destination = destination,
                    suggestedFileName = "lecture.pdf",
                    maxBytes = 512L * 1024 * 1024,
                    onProgress = { _, _ -> },
                )

                assertTrue(result is LmsAttachmentDownloadResult.Failure)
                assertFalse(destination.exists())
                assertFalse(File(directory, "error.bin.part").exists())
            } finally {
                directory.deleteRecursively()
            }
        }
    }

    @Test
    fun bomXmlAndCommentWrappedLoginHtmlIsRejectedWithoutSavingIt() = runTest {
        val body = (
            "\uFEFF<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<!-- LMS gateway -->" +
                "<html><body><form><input id='id'><input id='pass'></form></body></html>"
            ).toByteArray()
        val connection = FakeConnection(
            url = URL("https://lms.dima.ac.kr/lms/common/doDownloadFile.dunet"),
            status = 200,
            body = body,
            contentType = "application/octet-stream",
        )
        val transport = UrlConnectionLmsTransport(
            cookieProvider = { "SESSION=expired" },
            connectionFactory = { connection },
        )
        val directory = File(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "lms-download-wrapped-login-${System.nanoTime()}",
        ).apply { mkdirs() }
        val destination = File(directory, "wrapped-login.bin")

        try {
            val result = transport.downloadAttachment(
                request = LmsAttachmentRequest(
                    method = LmsHttpMethod.GET,
                    url = connection.url.toString(),
                    refererUrl = "https://lms.dima.ac.kr/lms/class/boardItem/doViewBoardItem.dunet?boarditem_no=91",
                ),
                destination = destination,
                suggestedFileName = "lecture.pdf",
                maxBytes = 512L * 1024 * 1024,
                onProgress = { _, _ -> },
            )

            assertEquals(LmsAttachmentDownloadResult.SessionExpired, result)
            assertFalse(destination.exists())
            assertFalse(File(directory, "wrapped-login.bin.part").exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun scriptRedirectToTheOfficialLoginPageExpiresTheSessionWithoutSavingIt() = runTest {
        val body = (
            "<!-- expired session -->" +
                "<script>window.location.replace('/login/doLoginPage.dunet');</script>"
            ).toByteArray()
        val connection = FakeConnection(
            url = URL("https://lms.dima.ac.kr/lms/common/doDownloadFile.dunet"),
            status = 200,
            body = body,
            contentType = "application/octet-stream",
        )
        val transport = UrlConnectionLmsTransport(
            cookieProvider = { "SESSION=expired" },
            connectionFactory = { connection },
        )
        val directory = File(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "lms-download-script-login-${System.nanoTime()}",
        ).apply { mkdirs() }
        val destination = File(directory, "script-login.bin")

        try {
            val result = transport.downloadAttachment(
                request = LmsAttachmentRequest(
                    method = LmsHttpMethod.GET,
                    url = connection.url.toString(),
                    refererUrl = "https://lms.dima.ac.kr/lms/class/boardItem/doViewBoardItem.dunet?boarditem_no=91",
                ),
                destination = destination,
                suggestedFileName = "lecture.pdf",
                maxBytes = 512L * 1024 * 1024,
                onProgress = { _, _ -> },
            )

            assertEquals(LmsAttachmentDownloadResult.SessionExpired, result)
            assertFalse(destination.exists())
            assertFalse(File(directory, "script-login.bin.part").exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun loginFormUsingInputNamesExpiresTheSessionWithoutSavingIt() = runTest {
        val body = (
            "<html><body><form action='/login/doLogin.dunet'>" +
                "<input name='id'><input name='pass' type='password'>" +
                "</form></body></html>"
            ).toByteArray()
        val connection = FakeConnection(
            url = URL("https://lms.dima.ac.kr/lms/common/doDownloadFile.dunet"),
            status = 200,
            body = body,
            contentType = "application/octet-stream",
        )
        val transport = UrlConnectionLmsTransport(
            cookieProvider = { "SESSION=expired" },
            connectionFactory = { connection },
        )
        val directory = File(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "lms-download-named-login-${System.nanoTime()}",
        ).apply { mkdirs() }
        val destination = File(directory, "named-login.bin")

        try {
            val result = transport.downloadAttachment(
                request = LmsAttachmentRequest(
                    method = LmsHttpMethod.GET,
                    url = connection.url.toString(),
                    refererUrl = "https://lms.dima.ac.kr/lms/class/boardItem/doViewBoardItem.dunet?boarditem_no=91",
                ),
                destination = destination,
                suggestedFileName = "lecture.pdf",
                maxBytes = 512L * 1024 * 1024,
                onProgress = { _, _ -> },
            )

            assertEquals(LmsAttachmentDownloadResult.SessionExpired, result)
            assertFalse(destination.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun htmlErrorAttachmentResponseIsRejectedAsFailure() = runTest {
        val body = "<!doctype html><html><body><h1>Internal server error</h1></body></html>".toByteArray()
        val connection = FakeConnection(
            url = URL("https://lms.dima.ac.kr/lms/common/doDownloadFile.dunet"),
            status = 200,
            body = body,
            contentType = "text/html; charset=UTF-8",
        )
        val transport = UrlConnectionLmsTransport(
            cookieProvider = { "SESSION=ready" },
            connectionFactory = { connection },
        )
        val directory = File(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "lms-download-error-${System.nanoTime()}",
        ).apply { mkdirs() }
        val destination = File(directory, "error.html")

        try {
            val result = transport.downloadAttachment(
                request = LmsAttachmentRequest(
                    method = LmsHttpMethod.GET,
                    url = connection.url.toString(),
                    refererUrl = "https://lms.dima.ac.kr/lms/class/boardItem/doViewBoardItem.dunet?boarditem_no=91",
                ),
                destination = destination,
                suggestedFileName = "lecture.pdf",
                maxBytes = 512L * 1024 * 1024,
                onProgress = { _, _ -> },
            )

            assertTrue(result is LmsAttachmentDownloadResult.Failure)
            assertFalse(destination.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun octetStreamAttachmentUsesItsDetailRefererAndReturnsVerifiedBytes() = runTest {
        val body = "%PDF-1.7\nverified".toByteArray()
        val connection = FakeConnection(
            url = URL("https://lms.dima.ac.kr/lms/common/doDownloadFile.dunet"),
            status = 200,
            body = body,
            headers = mapOf(
                "Content-Length" to listOf(body.size.toString()),
                "Content-Disposition" to listOf("attachment; filename*=UTF-8''lecture-notes.pdf"),
            ),
            contentType = "application/octet-stream",
        )
        val referer = "https://lms.dima.ac.kr/lms/class/boardItem/doViewBoardItem.dunet?boarditem_no=91"
        val transport = UrlConnectionLmsTransport(
            cookieProvider = { "SESSION=ready" },
            connectionFactory = { connection },
        )
        val directory = File(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "lms-download-success-${System.nanoTime()}",
        ).apply { mkdirs() }
        val destination = File(directory, "lecture.pdf")

        try {
            val result = transport.downloadAttachment(
                request = LmsAttachmentRequest(
                    method = LmsHttpMethod.GET,
                    url = connection.url.toString(),
                    refererUrl = referer,
                ),
                destination = destination,
                suggestedFileName = "fallback.pdf",
                maxBytes = 512L * 1024 * 1024,
                onProgress = { _, _ -> },
            )

            assertEquals(referer, connection.getRequestProperty("Referer"))
            assertEquals(
                LmsAttachmentDownloadResult.Success(
                    bytesWritten = body.size.toLong(),
                    fileName = "lecture-notes.pdf",
                    contentType = "application/octet-stream",
                ),
                result,
            )
            assertTrue(destination.readBytes().contentEquals(body))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun attachmentWithoutAnOfficialRefererIsRejectedBeforeConnecting() = runTest {
        var connectionAttempts = 0
        val connection = FakeConnection(
            url = URL("https://lms.dima.ac.kr/lms/common/doDownloadFile.dunet"),
            status = 200,
            body = byteArrayOf(1),
            contentType = "application/octet-stream",
        )
        val transport = UrlConnectionLmsTransport(
            connectionFactory = {
                connectionAttempts += 1
                connection
            },
        )
        val directory = File(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "lms-download-referer-${System.nanoTime()}",
        ).apply { mkdirs() }
        val destination = File(directory, "lecture.pdf")

        try {
            val result = transport.downloadAttachment(
                request = LmsAttachmentRequest(
                    method = LmsHttpMethod.GET,
                    url = connection.url.toString(),
                    refererUrl = null,
                ),
                destination = destination,
                suggestedFileName = "lecture.pdf",
                maxBytes = 512L * 1024 * 1024,
                onProgress = { _, _ -> },
            )

            assertTrue(result is LmsAttachmentDownloadResult.Failure)
            assertEquals(0, connectionAttempts)
            assertFalse(destination.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun attachmentRequestOutsideTheLmsHostIsRejectedBeforeConnecting() = runTest {
        var connectionAttempts = 0
        val connection = FakeConnection(
            url = URL("https://portal.dima.ac.kr/sso/download"),
            status = 200,
            body = byteArrayOf(1),
            contentType = "application/octet-stream",
        )
        val transport = UrlConnectionLmsTransport(
            connectionFactory = {
                connectionAttempts += 1
                connection
            },
        )
        val directory = File(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "lms-download-host-${System.nanoTime()}",
        ).apply { mkdirs() }
        val destination = File(directory, "lecture.pdf")

        try {
            val result = transport.downloadAttachment(
                request = LmsAttachmentRequest(
                    method = LmsHttpMethod.GET,
                    url = connection.url.toString(),
                    refererUrl = "https://lms.dima.ac.kr/lms/class/boardItem/doViewBoardItem.dunet?boarditem_no=91",
                ),
                destination = destination,
                suggestedFileName = "lecture.pdf",
                maxBytes = 512L * 1024 * 1024,
                onProgress = { _, _ -> },
            )

            assertTrue(result is LmsAttachmentDownloadResult.Failure)
            assertEquals(0, connectionAttempts)
            assertFalse(destination.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun attachmentRefererOutsideTheLmsHostIsRejectedBeforeConnecting() = runTest {
        var connectionAttempts = 0
        val connection = FakeConnection(
            url = URL("https://lms.dima.ac.kr/lms/common/doDownloadFile.dunet"),
            status = 200,
            body = byteArrayOf(1),
            contentType = "application/octet-stream",
        )
        val transport = UrlConnectionLmsTransport(
            connectionFactory = {
                connectionAttempts += 1
                connection
            },
        )
        val directory = File(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "lms-download-referer-host-${System.nanoTime()}",
        ).apply { mkdirs() }
        val destination = File(directory, "lecture.pdf")

        try {
            val result = transport.downloadAttachment(
                request = LmsAttachmentRequest(
                    method = LmsHttpMethod.GET,
                    url = connection.url.toString(),
                    refererUrl = "https://portal.dima.ac.kr/sso/error.aspx?errorCode=3045",
                ),
                destination = destination,
                suggestedFileName = "lecture.pdf",
                maxBytes = 512L * 1024 * 1024,
                onProgress = { _, _ -> },
            )

            assertTrue(result is LmsAttachmentDownloadResult.Failure)
            assertEquals(0, connectionAttempts)
            assertFalse(destination.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun attachmentRedirectOutsideTheLmsHostIsRejected() = runTest {
        val first = FakeConnection(
            url = URL("https://lms.dima.ac.kr/lms/common/doDownloadFile.dunet"),
            status = 302,
            headers = mapOf("Location" to listOf("https://portal.dima.ac.kr/sso/login")),
        )
        val escaped = FakeConnection(
            url = URL("https://portal.dima.ac.kr/sso/login"),
            status = 200,
            body = byteArrayOf(1),
            contentType = "application/octet-stream",
        )
        val connections = ArrayDeque(listOf(first, escaped))
        val transport = UrlConnectionLmsTransport(
            cookieProvider = { "SESSION=ready" },
            connectionFactory = { connections.removeFirst() },
        )
        val directory = File(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "lms-download-redirect-${System.nanoTime()}",
        ).apply { mkdirs() }
        val destination = File(directory, "lecture.pdf")

        try {
            val result = transport.downloadAttachment(
                request = LmsAttachmentRequest(
                    method = LmsHttpMethod.GET,
                    url = first.url.toString(),
                    refererUrl = "https://lms.dima.ac.kr/lms/class/boardItem/doViewBoardItem.dunet?boarditem_no=91",
                ),
                destination = destination,
                suggestedFileName = "lecture.pdf",
                maxBytes = 512L * 1024 * 1024,
                onProgress = { _, _ -> },
            )

            assertTrue(result is LmsAttachmentDownloadResult.Failure)
            assertEquals(1, connections.size)
            assertFalse(destination.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun attachmentRedirectToTheExactPortal3045PageExpiresTheSessionWithoutFollowingIt() = runTest {
        val first = FakeConnection(
            url = URL("https://lms.dima.ac.kr/lms/common/doDownloadFile.dunet"),
            status = 302,
            headers = mapOf(
                "Location" to listOf("https://portal.dima.ac.kr/sso/error.aspx?errorCode=3045"),
            ),
        )
        val conflict = FakeConnection(
            url = URL("https://portal.dima.ac.kr/sso/error.aspx?errorCode=3045"),
            status = 200,
            body = "<html>conflict</html>".toByteArray(),
        )
        val connections = ArrayDeque(listOf(first, conflict))
        val transport = UrlConnectionLmsTransport(
            cookieProvider = { "SESSION=expired" },
            connectionFactory = { connections.removeFirst() },
        )
        val directory = File(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "lms-download-3045-${System.nanoTime()}",
        ).apply { mkdirs() }
        val destination = File(directory, "lecture.pdf")

        try {
            val result = transport.downloadAttachment(
                request = LmsAttachmentRequest(
                    method = LmsHttpMethod.GET,
                    url = first.url.toString(),
                    refererUrl = "https://lms.dima.ac.kr/lms/class/boardItem/doViewBoardItem.dunet?boarditem_no=91",
                ),
                destination = destination,
                suggestedFileName = "lecture.pdf",
                maxBytes = 512L * 1024 * 1024,
                onProgress = { _, _ -> },
            )

            assertEquals(LmsAttachmentDownloadResult.SessionExpired, result)
            assertEquals(1, connections.size)
            assertFalse(destination.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun getAttachmentRequestAppendsItsStructuredQueryFields() = runTest {
        val endpoint = "https://lms.dima.ac.kr/lms/common/doDownloadFile.dunet"
        val connection = FakeConnection(
            url = URL(endpoint),
            status = 200,
            body = byteArrayOf(1, 2),
            contentType = "application/octet-stream",
        )
        var requestedUrl: URL? = null
        val transport = UrlConnectionLmsTransport(
            cookieProvider = { "SESSION=ready" },
            connectionFactory = { url ->
                requestedUrl = url
                connection
            },
        )
        val directory = File(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "lms-download-query-${System.nanoTime()}",
        ).apply { mkdirs() }
        val destination = File(directory, "lecture.pdf")

        try {
            val result = transport.downloadAttachment(
                request = LmsAttachmentRequest(
                    method = LmsHttpMethod.GET,
                    url = endpoint,
                    fields = linkedMapOf("attach_no" to "501", "boarditem_no" to "91"),
                    refererUrl = "https://lms.dima.ac.kr/lms/class/boardItem/doViewBoardItem.dunet?boarditem_no=91",
                ),
                destination = destination,
                suggestedFileName = "lecture.pdf",
                maxBytes = 512L * 1024 * 1024,
                onProgress = { _, _ -> },
            )

            assertTrue(result is LmsAttachmentDownloadResult.Success)
            assertEquals(
                "$endpoint?attach_no=501&boarditem_no=91",
                requestedUrl.toString(),
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun postAttachmentRequestSendsItsStructuredFormFieldsAndReferer() = runTest {
        val endpoint = "https://lms.dima.ac.kr/lms/class/report/stud/doDownloadAttachFile.dunet"
        val connection = FakeConnection(
            url = URL(endpoint),
            status = 200,
            body = byteArrayOf(1, 2, 3),
            contentType = "application/octet-stream",
        )
        val referer = "https://lms.dima.ac.kr/lms/class/report/stud/doViewReportStudent.dunet?report_no=301"
        val transport = UrlConnectionLmsTransport(
            cookieProvider = { "SESSION=ready" },
            connectionFactory = { connection },
        )
        val directory = File(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "lms-download-post-${System.nanoTime()}",
        ).apply { mkdirs() }
        val destination = File(directory, "assignment.pdf")

        try {
            val result = transport.downloadAttachment(
                request = LmsAttachmentRequest(
                    method = LmsHttpMethod.POST,
                    url = endpoint,
                    fields = linkedMapOf("attach_no" to "501", "report_no" to "301"),
                    refererUrl = referer,
                ),
                destination = destination,
                suggestedFileName = "assignment.pdf",
                maxBytes = 512L * 1024 * 1024,
                onProgress = { _, _ -> },
            )

            assertTrue(result is LmsAttachmentDownloadResult.Success)
            assertEquals("POST", connection.requestMethod)
            assertEquals("attach_no=501&report_no=301", connection.postedBody())
            assertEquals("application/x-www-form-urlencoded", connection.getRequestProperty("Content-Type"))
            assertEquals(referer, connection.getRequestProperty("Referer"))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun cancelledAttachmentDownloadDeletesItsTemporaryFileAndPropagatesCancellation() = runTest {
        val connection = FakeConnection(
            url = URL("https://lms.dima.ac.kr/lms/common/doDownloadFile.dunet"),
            status = 200,
            body = ByteArray(32) { it.toByte() },
            contentType = "application/octet-stream",
        )
        val transport = UrlConnectionLmsTransport(
            cookieProvider = { "SESSION=ready" },
            connectionFactory = { connection },
        )
        val directory = File(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "lms-download-cancel-${System.nanoTime()}",
        ).apply { mkdirs() }
        val destination = File(directory, "lecture.pdf")
        var cancellation: CancellationException? = null

        try {
            try {
                transport.downloadAttachment(
                    request = LmsAttachmentRequest(
                        method = LmsHttpMethod.GET,
                        url = connection.url.toString(),
                        refererUrl = "https://lms.dima.ac.kr/lms/class/boardItem/doViewBoardItem.dunet?boarditem_no=91",
                    ),
                    destination = destination,
                    suggestedFileName = "lecture.pdf",
                    maxBytes = 512L * 1024 * 1024,
                    onProgress = { _, _ -> throw CancellationException("cancelled") },
                )
            } catch (expected: CancellationException) {
                cancellation = expected
            }

            assertEquals("cancelled", cancellation?.message)
            assertFalse(destination.exists())
            assertFalse(File(directory, "lecture.pdf.part").exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun attachmentLargerThanThe512MiBLimitIsRejectedBeforeReading() = runTest {
        val limit = 512L * 1024 * 1024
        val connection = FakeConnection(
            url = URL("https://lms.dima.ac.kr/lms/common/doDownloadFile.dunet"),
            status = 200,
            body = byteArrayOf(1),
            contentType = "application/octet-stream",
            declaredLength = limit + 1,
        )
        val transport = UrlConnectionLmsTransport(
            cookieProvider = { "SESSION=ready" },
            connectionFactory = { connection },
        )
        val directory = File(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "lms-download-limit-${System.nanoTime()}",
        ).apply { mkdirs() }
        val destination = File(directory, "lecture.pdf")

        try {
            val result = transport.downloadAttachment(
                request = LmsAttachmentRequest(
                    method = LmsHttpMethod.GET,
                    url = connection.url.toString(),
                    refererUrl = "https://lms.dima.ac.kr/lms/class/boardItem/doViewBoardItem.dunet?boarditem_no=91",
                ),
                destination = destination,
                suggestedFileName = "lecture.pdf",
                maxBytes = limit,
                onProgress = { _, _ -> },
            )

            assertTrue(result is LmsAttachmentDownloadResult.Failure)
            assertFalse(destination.exists())
            assertFalse(File(directory, "lecture.pdf.part").exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun interruptedAttachmentStreamDeletesItsPartialFile() = runTest {
        val connection = FakeConnection(
            url = URL("https://lms.dima.ac.kr/lms/common/doDownloadFile.dunet"),
            status = 200,
            body = byteArrayOf(1, 2, 3, 4),
            contentType = "application/octet-stream",
            inputStreamFactory = { FailingInputStream(byteArrayOf(1, 2)) },
        )
        val transport = UrlConnectionLmsTransport(
            cookieProvider = { "SESSION=ready" },
            connectionFactory = { connection },
        )
        val directory = File(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "lms-download-partial-${System.nanoTime()}",
        ).apply { mkdirs() }
        val destination = File(directory, "lecture.pdf")

        try {
            val result = transport.downloadAttachment(
                request = LmsAttachmentRequest(
                    method = LmsHttpMethod.GET,
                    url = connection.url.toString(),
                    refererUrl = "https://lms.dima.ac.kr/lms/class/boardItem/doViewBoardItem.dunet?boarditem_no=91",
                ),
                destination = destination,
                suggestedFileName = "lecture.pdf",
                maxBytes = 512L * 1024 * 1024,
                onProgress = { _, _ -> },
            )

            assertTrue(result is LmsAttachmentDownloadResult.Failure)
            assertFalse(destination.exists())
            assertFalse(File(directory, "lecture.pdf.part").exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun failedCacheDownloadDoesNotDeleteOrOverwriteAnExistingFile() = runTest {
        var connectionAttempts = 0
        val connection = FakeConnection(
            url = URL("https://lms.dima.ac.kr/lms/common/doDownloadFile.dunet"),
            status = 200,
            body = byteArrayOf(1, 2, 3),
            contentType = "application/octet-stream",
        )
        val transport = UrlConnectionLmsTransport(
            connectionFactory = {
                connectionAttempts += 1
                connection
            },
        )
        val directory = File(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "lms-download-existing-${System.nanoTime()}",
        ).apply { mkdirs() }
        val destination = File(directory, "lecture.pdf").apply { writeText("keep me") }

        try {
            val result = transport.downloadAttachment(
                request = LmsAttachmentRequest(
                    method = LmsHttpMethod.GET,
                    url = connection.url.toString(),
                    refererUrl = "https://lms.dima.ac.kr/lms/class/boardItem/doViewBoardItem.dunet?boarditem_no=91",
                ),
                destination = destination,
                suggestedFileName = "lecture.pdf",
                maxBytes = 512L * 1024 * 1024,
                onProgress = { _, _ -> },
            )

            assertTrue(result is LmsAttachmentDownloadResult.Failure)
            assertEquals(0, connectionAttempts)
            assertEquals("keep me", destination.readText())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun normalFormPostDoesNotAdvertiseItselfAsAnAjaxRequest() = runTest {
        val connection = FakeConnection(
            url = URL("https://lms.dima.ac.kr/lms/class/boardItem/doViewBoardItem.dunet"),
            status = 200,
            body = "<main>본문</main>".toByteArray(),
        )
        val transport = UrlConnectionLmsTransport(
            cookieProvider = { "SESSION=ready" },
            connectionFactory = { connection },
        )

        transport.postForm(connection.url.toString(), mapOf("boarditem_no" to "91"))

        assertEquals("POST", connection.requestMethod)
        assertEquals(null, connection.getRequestProperty("X-Requested-With"))
        assertEquals("https://lms.dima.ac.kr", connection.getRequestProperty("Origin"))
        assertEquals(
            "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            connection.getRequestProperty("Accept"),
        )
        assertEquals("application/x-www-form-urlencoded", connection.getRequestProperty("Content-Type"))
        assertEquals("navigate", connection.getRequestProperty("Sec-Fetch-Mode"))
        assertEquals("document", connection.getRequestProperty("Sec-Fetch-Dest"))
    }

    @Test
    fun postFormFollowsTheOfficialRedirectAndKeepsItsSessionCookie() = runTest {
        val first = FakeConnection(
            url = URL("https://lms.dima.ac.kr/lms/class/classroom/doSetSessionClassRoom.dunet"),
            status = 302,
            headers = mapOf(
                "Location" to listOf("/lms/class/courseSchedule/doListView.dunet"),
                "Set-Cookie" to listOf("CLASS_CONTEXT=ready; Path=/; Secure"),
            ),
        )
        val second = FakeConnection(
            url = URL("https://lms.dima.ac.kr/lms/class/courseSchedule/doListView.dunet"),
            status = 200,
            body = "<main>수업 자료</main>".toByteArray(),
        )
        val connections = ArrayDeque(listOf(first, second))
        val storedCookies = mutableListOf<Pair<String, String>>()
        val transport = UrlConnectionLmsTransport(
            cookieProvider = { null },
            cookieSink = { url, cookie -> storedCookies += url to cookie },
            connectionFactory = { connections.removeFirst() },
        )

        val response = transport.postAjax(
            first.url.toString(),
            mapOf("course_id" to "COURSE-A", "class_no" to "D"),
        )

        assertEquals("POST", first.requestMethod)
        assertEquals("XMLHttpRequest", first.getRequestProperty("X-Requested-With"))
        assertEquals("*/*", first.getRequestProperty("Accept"))
        assertEquals("cors", first.getRequestProperty("Sec-Fetch-Mode"))
        assertEquals("https://lms.dima.ac.kr", first.getRequestProperty("Origin"))
        assertEquals(
            "https://lms.dima.ac.kr/lms/myLecture/doListView.dunet?mnid=201008840728",
            first.getRequestProperty("Referer"),
        )
        assertEquals("GET", second.requestMethod)
        assertEquals("CLASS_CONTEXT=ready", second.getRequestProperty("Cookie"))
        assertEquals(second.url.toString(), response.finalUrl)
        assertEquals("<main>수업 자료</main>", response.body.decodeToString())
        assertEquals(listOf(first.url.toString() to "CLASS_CONTEXT=ready; Path=/; Secure"), storedCookies)
    }
}

private class FakeConnection(
    url: URL,
    private val status: Int,
    private val body: ByteArray = ByteArray(0),
    private val headers: Map<String, List<String>> = emptyMap(),
    private val contentType: String = "text/html; charset=UTF-8",
    private val declaredLength: Long = body.size.toLong(),
    private val inputStreamFactory: (() -> InputStream)? = null,
) : HttpURLConnection(url) {
    private val posted = ByteArrayOutputStream()

    override fun connect() = Unit
    override fun disconnect() = Unit
    override fun usingProxy(): Boolean = false
    override fun getResponseCode(): Int = status
    override fun getInputStream(): InputStream = inputStreamFactory?.invoke() ?: ByteArrayInputStream(body)
    override fun getOutputStream() = posted
    override fun getHeaderFields(): Map<String, List<String>> = headers
    override fun getHeaderField(name: String?): String? = headers.entries
        .firstOrNull { it.key.equals(name, ignoreCase = true) }
        ?.value
        ?.firstOrNull()
    override fun getContentType(): String = contentType
    override fun getContentLengthLong(): Long = declaredLength

    fun postedBody(): String = posted.toByteArray().decodeToString()
}

private class FailingInputStream(private val prefix: ByteArray) : InputStream() {
    private var index = 0

    override fun read(): Int {
        if (index < prefix.size) return prefix[index++].toInt() and 0xff
        throw IOException("stream interrupted")
    }
}
