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

    private fun projectRoot(): Path {
        var current = Path.of(System.getProperty("user.dir")).toAbsolutePath()
        repeat(4) {
            if (Files.exists(current.resolve(".github/workflows/publish-data.yml"))) return current
            current = current.parent ?: return@repeat
        }
        error("프로젝트 루트의 publish-data.yml을 찾지 못했습니다.")
    }
}
