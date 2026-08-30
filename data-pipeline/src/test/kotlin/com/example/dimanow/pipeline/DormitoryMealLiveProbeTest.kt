package com.example.dimanow.pipeline

import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlinx.serialization.encodeToString

class DormitoryMealLiveProbeTest {
    @Test
    fun attachedDormitoryTablePassesValidationAndProducesACompleteWeeklyPayload() {
        assumeTrue(System.getenv("RUN_DORM_GEMINI_PROBE") == "true")
        val apiKey = System.getenv("GEMINI_API_KEY").orEmpty()
        val imagePath = Path.of(System.getenv("DORM_GEMINI_IMAGE"))
        val outputPath = Path.of(System.getenv("DORM_GEMINI_OUTPUT"))
        val analysis = GeminiDormitoryMealClient(apiKey).analyze(Files.readAllBytes(imagePath), "image/jpeg")
        assertTrue("사진이 기숙사 식단표로 승인되어야 합니다: $analysis", analysis is DormitoryMealAnalysis.Accepted)
        val payload = GeminiDormitoryMealPayloadBuilder().build(
            responseJson = (analysis as DormitoryMealAnalysis.Accepted).responseJson,
            referenceDate = LocalDate.parse("2026-08-27"),
            sourceImageUrl = "https://example.invalid/dormitory-meal-probe.jpg",
        )
        Files.createDirectories(outputPath.parent)
        Files.writeString(outputPath, kotlinx.serialization.json.Json { prettyPrint = true }.encodeToString(payload))
        assertTrue(payload.days.size == 5)
        assertTrue(payload.days.all { it.sections.isNotEmpty() })
    }
}
