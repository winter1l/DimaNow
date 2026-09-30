package com.example.dimanow.pipeline

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PublishWorkflowScheduleTest {
    @Test
    fun `student collection retries through Monday lunch and every two hours afterwards`() {
        val workflow = Files.readString(projectRoot().resolve(".github/workflows/publish-data.yml"))

        assertTrue(workflow.contains("- cron: \"7,37 0-4 * * 1\"")) // Mon 09:07 through 13:37 KST
        assertTrue(workflow.contains("- cron: \"7 5-23/2 * * 1\"")) // Mon 14:07 through Tue 08:07 KST
        assertTrue(workflow.contains("- cron: \"7 1-23/2 * * 0,2-6\"")) // Other days, every 2h
        assertFalse(workflow.contains("- cron: \"15 1 * * 1\""))
        assertTrue(workflow.contains("- cron: \"43 0 * * *\""))
    }

    @Test
    fun `dormitory submissions publish automatically without an operator approval path`() {
        val workflow = Files.readString(projectRoot().resolve(".github/workflows/publish-data.yml"))

        assertTrue(workflow.contains("publish-dorm-meal \$IMAGE_PATH \$GITHUB_WORKSPACE/site \$SUBMISSION_ID \$SOURCE_IMAGE_URL \$MIME_TYPE"))
        for (removed in listOf("dorm-approve", "approve-dorm-meal", "dorm-review-candidates", "dorm-review-approvals", "candidate_sha256", "APPROVE")) {
            assertFalse(removed, workflow.contains(removed))
        }
    }

    @Test
    fun `workflow expressions never expand inside shell scripts`() {
        val lines = Files.readAllLines(projectRoot().resolve(".github/workflows/publish-data.yml"))
        var blockIndent = -1
        lines.forEachIndexed { index, line ->
            val indent = line.length - line.trimStart().length
            if (blockIndent >= 0 && (line.isBlank() || indent > blockIndent)) {
                assertFalse("run block line ${index + 1} expands an expression", line.contains("\${{"))
                return@forEachIndexed
            }
            blockIndent = -1
            val trimmed = line.trimStart().removePrefix("- ")
            if (trimmed.startsWith("run:")) {
                val inline = trimmed.removePrefix("run:").trim()
                if (inline == "|" || inline == ">") blockIndent = indent
                else assertFalse("run line ${index + 1} expands an expression", inline.contains("\${{"))
            }
        }
    }

    private fun projectRoot(): Path {
        var current = Path.of(System.getProperty("user.dir")).toAbsolutePath()
        repeat(4) {
            if (Files.exists(current.resolve(".github/workflows/publish-data.yml"))) return current
            current = current.parent ?: return@repeat
        }
        error("프로젝트 루트의 publish-data.yml을 찾지 못했습니다.")
    }
}
