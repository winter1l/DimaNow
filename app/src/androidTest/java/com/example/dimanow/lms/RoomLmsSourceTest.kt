package com.example.dimanow.lms

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomLmsSourceTest {
    @Test
    fun failedRefreshPreservesTheLastGoodCourseItems() = runTest {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            LmsCacheDatabase::class.java,
        ).allowMainThreadQueries().build()
        val transport = RecordingLmsTransport()
        val session = MutableLmsSessionController(LmsSessionState.ACTIVE)
        val source = RoomLmsSource(database, session, transport)

        assertEquals(LmsRefreshResult.Success, source.refresh(force = true))
        assertEquals("1주차 안내", source.snapshot.first { it.items.isNotEmpty() }.items.single().title)

        transport.failDashboard = true
        assertTrue(source.refresh(force = true) is LmsRefreshResult.Failure)
        assertEquals("1주차 안내", source.snapshot.first().items.single().title)

        database.close()
    }
}

private class RecordingLmsTransport : LmsHttpTransport {
    var failDashboard = false

    override suspend fun get(url: String, maxBytes: Long): LmsHttpResponse {
        if (url.contains("myLecture") && failDashboard) error("offline")
        val html = if (url.contains("myLecture")) {
            """
                <div class="lecture_info" data-course-id="C101"><a>음향기초실습(D반)</a><span>교수명 : 이화현</span></div>
                <div class="learn_element_detail_list"><span class="badge">공지</span>
                  <a href="/lms/class/boardItem/doViewBoardItem.dunet?board_no=7&amp;boarditem_no=91">[음향기초실습(D반)] 1주차 안내</a>
                  <span>등록일 : 2026.08.30 13:30:00</span>
                </div>
            """.trimIndent()
        } else "<table></table>"
        return LmsHttpResponse(url, 200, "text/html; charset=UTF-8", emptyMap(), html.toByteArray())
    }

    override suspend fun postForm(url: String, fields: Map<String, String>, maxBytes: Long) =
        LmsHttpResponse(url, 200, "application/json", emptyMap(), "{}".toByteArray())

    override suspend fun download(
        url: String,
        destination: File,
        maxBytes: Long,
        onProgress: (Long, Long?) -> Unit,
    ): LmsHttpResponse = error("not used")
}
