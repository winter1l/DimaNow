package com.example.dimanow.pipeline

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PublishWorkflowScheduleTest {
    @Test
    fun `본관 학생식당 자동 게시를 월요일 10시 15분 KST에 한 번만 실행한다`() {
        val workflow = Files.readString(projectRoot().resolve(".github/workflows/publish-data.yml"))

        assertTrue(workflow.contains("- cron: \"15 1 * * 1\""))
        assertFalse(workflow.contains("- cron: \"17 0,6 * * *\""))
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
