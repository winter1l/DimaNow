package com.example.dimanow.notice

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.dimanow.data.DimaDatabase
import com.example.dimanow.sync.StaticDataTransport
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class StaticNoticeSourceTest {
    private lateinit var database: DimaDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, DimaDatabase::class.java).build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun validServerNoticesReplaceNoticeCache() = runTest {
        val payload = """{"schemaVersion":1,"notices":[{"id":"2608280001","title":"개강 안내","url":"https://www.dima.ac.kr/?p=111&viewMode=view&reqIdx=2608280001","date":"2026-08-28","pinned":true}]}"""
        val manifest = """{"schemaVersion":1,"generatedAt":"2026-08-28T01:02:03Z","datasets":{"notice":{"revision":2,"state":"READY","publishedAt":"2026-08-28T01:02:03Z","lastAttemptAt":"2026-08-28T01:02:03Z","url":"notices/53b85796e1718905b087465a3a2dc9c2c3ee4301cfb38d14d1af39735bed905f.json","sha256":"53b85796e1718905b087465a3a2dc9c2c3ee4301cfb38d14d1af39735bed905f","sourceUrl":"https://www.dima.ac.kr/?p=111"}}}"""
        val source: NoticeSource = StaticNoticeSource(
            database = database,
            transport = StaticDataTransport { url -> if (url.endsWith("manifest.json")) manifest.toByteArray() else payload.toByteArray() },
            clock = Clock.fixed(Instant.parse("2026-08-28T02:00:00Z"), ZoneOffset.UTC),
        )

        assertEquals(NoticeRefreshResult.Success(1, Instant.parse("2026-08-28T02:00:00Z")), source.refresh())
        val notice = source.data.first().notices.single()
        assertEquals("개강 안내", notice.title)
        assertEquals(true, notice.isPinned)
    }
}
