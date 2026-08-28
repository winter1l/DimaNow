package com.example.dimanow.sync

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CachingStaticDataTransportTest {
    @Test
    fun `한 동기화 묶음은 manifest를 한 번만 받는다`() = runTest {
        var calls = 0
        val transport = CachingStaticDataTransport(
            delegate = StaticDataTransport { url -> calls++; url.toByteArray() },
            nowMillis = { 1_000L },
        )

        transport.get("https://winter1l.github.io/DimaNow/data/v1/manifest.json")
        transport.get("https://winter1l.github.io/DimaNow/data/v1/manifest.json")
        transport.get("https://winter1l.github.io/DimaNow/data/v1/shuttle/example.json")

        assertEquals(2, calls)
    }
}
