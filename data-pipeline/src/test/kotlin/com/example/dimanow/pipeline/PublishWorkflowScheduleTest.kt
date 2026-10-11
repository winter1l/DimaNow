package com.example.dimanow.pipeline

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
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

        // main's workflow sweeps the backlog; the legacy single-image CLI stays for the dorm-submissions branch workflow.
        assertTrue(workflow.contains("publish-dorm-pending \$RUNNER_TEMP/dorm-pending \$GITHUB_WORKSPACE/site \$RUNNER_TEMP/dorm-pending.txt"))
        for (removed in listOf("dorm-approve", "approve-dorm-meal", "dorm-review-candidates", "dorm-review-approvals", "candidate_sha256", "APPROVE")) {
            assertFalse(removed, workflow.contains(removed))
        }
    }

    @Test
    fun `dormitory backlog is swept oldest first with a per-run cap and without checking out the submission branch`() {
        val workflow = Files.readString(projectRoot().resolve(".github/workflows/publish-data.yml")).replace("\r\n", "\n")

        assertTrue(workflow.contains("if [[ \"\$EVENT_NAME\" == \"schedule\" ]]; then dorm=true; fi"))
        assertTrue(workflow.contains("options: [all, shuttle, meal, notice, dorm, deploy-only]"))
        assertTrue(workflow.contains("[[ \"\$path\" =~ ^dorm-submissions/[A-Za-z0-9_-]{1,64}\\.(jpg|jpeg|png|webp)\$ ]] || continue"))
        assertTrue(workflow.contains("[[ \"\$mode\" == 100644 && \"\$type\" == blob ]] || continue"))
        assertTrue(workflow.contains("head -n 10 \"\$candidates\" > \"\$index\""))
        assertFalse(workflow.contains("path: submission"))
        assertFalse(workflow.contains("diff-tree --no-commit-id"))
    }

    @Test
    fun `publish checkout keeps no credentials and only the data push receives the token`() {
        val workflow = Files.readString(projectRoot().resolve(".github/workflows/publish-data.yml")).replace("\r\n", "\n")

        val checkouts = Regex("""- uses: actions/checkout@[0-9a-f]{40}\n(?:\s{8}.*\n)+""").findAll(workflow).map { it.value }.toList()
        assertTrue(checkouts.isNotEmpty())
        checkouts.forEach { assertTrue(it, it.contains("persist-credentials: false")) }
        assertEquals(1, Regex("""\$\{\{\s*github\.token\s*}}""").findAll(workflow).count())
        assertTrue(workflow.contains("GIT_CONFIG_KEY_0=http.https://github.com/.extraheader"))
        assertFalse(workflow.contains("secrets.GITHUB_TOKEN"))
    }

    @Test
    fun `workflow expressions never expand inside shell scripts`() {
        val workflows = Files.list(projectRoot().resolve(".github/workflows")).use { paths ->
            paths.filter { it.fileName.toString().endsWith(".yml") || it.fileName.toString().endsWith(".yaml") }
                .sorted().toList()
        }
        val names = workflows.map { it.fileName.toString() }
        for (required in listOf("publish-data.yml", "validate.yml", "instrumentation.yml")) {
            assertTrue("$required is scanned", required in names)
        }
        for (workflow in workflows) assertNoExpressionsInShell(workflow)
    }

    @Test
    fun `instrumentation workflow runs the androidTest suite with least privilege`() {
        val workflow = Files.readString(projectRoot().resolve(".github/workflows/instrumentation.yml")).replace("\r\n", "\n")

        assertTrue(workflow.contains("permissions:\n  contents: read\n"))
        assertTrue(workflow.contains("cancel-in-progress: true"))
        assertTrue(workflow.contains("script: bash .github/scripts/instrumentation-shard.sh"))
        assertTrue(Files.readString(projectRoot().resolve(".github/scripts/instrumentation-shard.sh"))
            .contains(":app:connectedDebugAndroidTest"))
        // Every third-party action is pinned to a full commit SHA, never a movable tag.
        Regex("""uses:\s*(\S+)""").findAll(workflow).forEach { match ->
            assertTrue("${match.value} is not SHA-pinned", Regex("""@[0-9a-f]{40}$""").containsMatchIn(match.groupValues[1]))
        }
        // Gated physical-device, live-network and private-fixture tests stay skipped on CI.
        for (gate in listOf("physicalLivePromotion", "runNoticeNetworkProbe", "heicFixture", "secrets.")) {
            assertFalse(gate, workflow.contains(gate))
        }
    }

    /** `run:` and emulator-runner `script:` bodies are shell; expressions must reach them through `env:`. */
    private fun assertNoExpressionsInShell(workflow: Path) {
        val file = workflow.fileName.toString()
        val lines = Files.readAllLines(workflow)
        var blockIndent = -1
        lines.forEachIndexed { index, line ->
            val indent = line.length - line.trimStart().length
            if (blockIndent >= 0 && (line.isBlank() || indent > blockIndent)) {
                assertFalse("$file shell block line ${index + 1} expands an expression", line.contains("\${{"))
                return@forEachIndexed
            }
            blockIndent = -1
            val trimmed = line.trimStart().removePrefix("- ")
            val key = listOf("run:", "script:").firstOrNull { trimmed.startsWith(it) } ?: return@forEachIndexed
            val inline = trimmed.removePrefix(key).trim()
            if (inline.startsWith("|") || inline.startsWith(">")) blockIndent = indent
            else assertFalse("$file shell line ${index + 1} expands an expression", inline.contains("\${{"))
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
