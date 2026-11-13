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
        assumeTrue(System.getenv("RUN_DORM_OPENAI_PROBE") == "true")
        val apiKey = System.getenv("OPENAI_API_KEY").orEmpty()
        val imagePath = Path.of(System.getenv("DORM_OPENAI_IMAGE"))
        val outputBase = Path.of(System.getenv("DORM_OPENAI_OUTPUT"))
        val models = (System.getenv("DORM_OPENAI_MODELS") ?: "gpt-6-luna").split(',').map(String::trim)
        require(models.all { it.matches(Regex("[a-z0-9.-]+")) })
        models.forEach { model ->
            val outputPath = if (models.size == 1) outputBase else outputBase.parent.resolve(model).resolve(outputBase.fileName)
            val extractionReasoning = System.getenv("DORM_OPENAI_EXTRACTION_REASONING") ?: "low"
            val startedAt = System.nanoTime()
            val analysis = OpenAiDormitoryMealClient(apiKey, extractionReasoningEffort = extractionReasoning, model = model)
                .analyze(Files.readAllBytes(imagePath), "image/jpeg")
            val recognitionMillis = (System.nanoTime() - startedAt) / 1_000_000
            assertTrue("사진이 기숙사 식단표로 승인되어야 합니다: $analysis", analysis is DormitoryMealAnalysis.Accepted)
            val payload = OpenAiDormitoryMealPayloadBuilder().build(
                responseJson = (analysis as DormitoryMealAnalysis.Accepted).responseJson,
                referenceDate = LocalDate.parse(System.getenv("DORM_OPENAI_REFERENCE_DATE") ?: "2026-08-27"),
                sourceImageUrl = "r2://dima-now-dorm-photos/dorm-submissions/openai-probe.jpg",
            )
            Files.createDirectories(outputPath.parent)
            Files.writeString(outputPath, kotlinx.serialization.json.Json { prettyPrint = true }.encodeToString(payload))
            Files.writeString(
                outputPath.resolveSibling("recognition-metrics.json"),
                """{"model":"$model","recognitionMillis":$recognitionMillis,"extractionReasoning":"$extractionReasoning","imageDetail":"original"}""",
            )
            assertTrue(payload.days.size == 5)
            assertTrue(payload.days.all { it.sections.isNotEmpty() })
        }
    }
}
