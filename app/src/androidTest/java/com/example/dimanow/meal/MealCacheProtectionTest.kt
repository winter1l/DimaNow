package com.example.dimanow.meal

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.dimanow.data.DimaDatabase
import java.time.Instant
import com.example.dimanow.sync.StaticDataTransport
import java.time.Clock
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class MealCacheProtectionTest {
    @Test
    fun failedCandidateDoesNotOverwriteLastValidWeek() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, DimaDatabase::class.java).allowMainThreadQueries().build()
        try {
            val payload = """{"schemaVersion":1,"weekStart":"2026-08-24","weekEnd":"2026-08-30","days":[{"date":"2026-08-24","menuLines":["밥","국"],"hours":"11:30 ~ 14:00","sourceUrl":"https://www.instagram.com/p/current/","sourceImageUrl":"https://scontent.example/valid.jpg"}]}"""
            val hash = java.security.MessageDigest.getInstance("SHA-256")
                .digest(payload.toByteArray())
                .joinToString("") { "%02x".format(it) }
            val validManifest = """{"schemaVersion":1,"generatedAt":"2026-08-24T01:00:00Z","datasets":{"meal":{"revision":1,"state":"READY","publishedAt":"2026-08-24T01:00:00Z","lastAttemptAt":"2026-08-24T01:00:00Z","url":"meal/$hash.json","sha256":"$hash","sourceUrl":"https://www.dima.ac.kr/?p=1"}}}"""
            val source = StaticMealSource(
                database = database,
                transport = StaticDataTransport { url -> if (url.endsWith("manifest.json")) validManifest.toByteArray() else payload.toByteArray() },
                clock = Clock.fixed(Instant.parse("2026-08-24T01:00:00Z"), ZoneOffset.UTC),
            )
            val firstSuccess = Instant.parse("2026-08-24T01:00:00Z")
            source.refresh()

            val failedManifest = """{"schemaVersion":1,"generatedAt":"2026-08-24T03:00:00Z","datasets":{"meal":{"revision":2,"state":"NEEDS_REVIEW","publishedAt":"2026-08-24T01:00:00Z","lastAttemptAt":"2026-08-24T03:00:00Z","url":"meal/$hash.json","sha256":"$hash","sourceUrl":"https://www.dima.ac.kr/?p=1","message":"메뉴 확인 필요: 날짜 헤더 오류"}}}"""
            val failingSource = StaticMealSource(
                database = database,
                transport = StaticDataTransport { failedManifest.toByteArray() },
                clock = Clock.fixed(Instant.parse("2026-08-24T03:00:00Z"), ZoneOffset.UTC),
            )
            failingSource.refresh()

            val data = failingSource.data.first()
            assertEquals(1, data.days.size)
            assertEquals(listOf("밥", "국"), data.days.first().menuLines)
            assertEquals(firstSuccess, data.lastSuccess)
            assertEquals("메뉴 확인 필요: 날짜 헤더 오류", data.error)
            assertEquals("https://scontent.example/valid.jpg", data.sourceImageUrl)
        } finally {
            database.close()
        }
    }
}
