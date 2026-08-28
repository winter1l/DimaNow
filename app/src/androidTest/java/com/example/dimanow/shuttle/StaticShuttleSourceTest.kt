package com.example.dimanow.shuttle

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

class StaticShuttleSourceTest {
    private lateinit var database: DimaDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, DimaDatabase::class.java).build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun validServerPayloadAtomicallyReplacesShuttleCache() = runTest {
        val payload = """{"schemaVersion":1,"departures":[{"serviceDay":"MONDAY","routeId":"B","stopId":"university-headquarters","direction":"TO_YEIN","originZone":"MAIN","destinationZone":"YEIN","departureTime":"08:10","arrivalTime":"08:15"}]}"""
        val manifest = """{"schemaVersion":1,"generatedAt":"2026-08-28T01:02:03Z","datasets":{"shuttle":{"revision":7,"state":"READY","publishedAt":"2026-08-28T01:02:03Z","lastAttemptAt":"2026-08-28T01:02:03Z","url":"shuttle/2b420a660dffe819987b003c1a9a130f4921d98219003988a1009c67f50d3c65.json","sha256":"2b420a660dffe819987b003c1a9a130f4921d98219003988a1009c67f50d3c65","sourceUrl":"https://www.dima.ac.kr/?p=97"}}}"""
        val transport = StaticDataTransport { url ->
            when {
                url.endsWith("manifest.json") -> manifest.toByteArray()
                url.endsWith("2b420a660dffe819987b003c1a9a130f4921d98219003988a1009c67f50d3c65.json") -> payload.toByteArray()
                else -> error("unexpected URL: $url")
            }
        }
        val source: ShuttleSource = StaticShuttleSource(
            database = database,
            transport = transport,
            clock = Clock.fixed(Instant.parse("2026-08-28T02:00:00Z"), ZoneOffset.UTC),
        )

        assertEquals(ShuttleRefreshResult.Success(1, Instant.parse("2026-08-28T02:00:00Z")), source.refresh())
        val row = source.data.first().departures.single()
        assertEquals("B", row.sourceRouteId)
        assertEquals("university-headquarters", row.sourceStopId)
        assertEquals("08:10", row.time.toString())
    }
}
