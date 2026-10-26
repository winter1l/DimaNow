package com.example.dimanow.pipeline

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** D-131(2): the weekly 학사일정 import proposes a reviewed change and holds write access only where no build runs. */
class CalendarImportWorkflowTest {
    private val workflow = Files.readString(projectRoot().resolve(".github/workflows/import-calendar.yml")).replace("\r\n", "\n")

    @Test
    fun `runs weekly and on demand without inputs or other triggers`() {
        assertTrue(workflow.contains("on:\n  schedule:\n    # Tuesday 10:23 KST, off the hour.\n    - cron: \"23 1 * * 2\"\n  workflow_dispatch:\n\n"))
        for (trigger in listOf("push:", "pull_request", "repository_dispatch", "inputs:", "client_payload")) {
            assertFalse(trigger, workflow.contains(trigger))
        }
    }

    @Test
    fun `the token is read-only by default and write scopes live only in the job that runs no build`() {
        assertTrue(workflow.contains("\npermissions: {}\n"))
        val jobs = jobs()
        assertEquals(setOf("import", "propose", "status"), jobs.keys)
        assertTrue(jobs.getValue("import").contains("    permissions:\n      contents: read\n    outputs:"))
        assertTrue(jobs.getValue("propose").contains("    permissions:\n      contents: write\n      pull-requests: write\n      issues: write\n    steps:"))
        assertTrue(jobs.getValue("status").contains("    permissions:\n      issues: write\n    steps:"))
        // The build and the parser run only in the read-only job, which never sees the token.
        assertTrue(jobs.getValue("import").contains("bash ./gradlew :data-pipeline:run --args=\"import-calendar "))
        assertFalse(jobs.getValue("import").contains("github.token"))
        for (name in listOf("propose", "status")) assertFalse(name, jobs.getValue(name).contains("gradlew"))
        assertFalse(workflow.contains("secrets."))
    }

    @Test
    fun `actions are SHA-pinned and checkouts keep no credentials`() {
        val uses = Regex("""uses:\s*(\S+)""").findAll(workflow).map { it.groupValues[1] }.toList()
        assertEquals(6, uses.size)
        uses.forEach { assertTrue("$it is not SHA-pinned", Regex("""@[0-9a-f]{40}$""").containsMatchIn(it)) }
        val checkouts = Regex("""- uses: actions/checkout@[0-9a-f]{40}\n(?:\s{8}.*\n)+""").findAll(workflow).map { it.value }.toList()
        assertEquals(2, checkouts.size)
        checkouts.forEach { assertTrue(it, it.contains("persist-credentials: false") && it.contains("ref: main")) }
        assertTrue(workflow.contains("GIT_CONFIG_KEY_0=http.https://github.com/.extraheader"))
    }

    @Test
    fun `the candidate goes to its own year branch as a pull request, or an issue when that is not allowed`() {
        val propose = jobs().getValue("propose")

        assertTrue(propose.contains("if [[ ! \"\$YEAR\" =~ ^20[0-9]{2}\$ ]]; then"))
        assertTrue(propose.contains("branch=\"calendar/auto-\$YEAR\""))
        assertTrue(propose.contains("git push origin \"HEAD:refs/heads/\$branch\""))
        assertFalse(propose.contains("--force"))
        assertTrue(propose.contains("gh pr create --repo \"\$repo\" --base main --head \"\$branch\" --title \"\$title\" --body-file \"\$body\""))
        assertTrue(propose.contains("gh issue create --repo \"\$repo\" --title \"\$title\" --body-file"))
        assertTrue(propose.contains("compare/main...\$branch?expand=1"))
        // Nothing is pushed to main or published from here.
        assertFalse(propose.contains("HEAD:main"))
        assertFalse(workflow.contains("deploy-pages"))
    }

    private fun jobs(): Map<String, String> {
        val body = workflow.substringAfter("\njobs:\n")
        return Regex("""(?m)^  ([a-z-]+):\n""").findAll(body).toList().let { headers ->
            headers.mapIndexed { index, header ->
                val end = headers.getOrNull(index + 1)?.range?.first ?: body.length
                header.groupValues[1] to body.substring(header.range.first, end)
            }.toMap()
        }
    }

    private fun projectRoot(): Path {
        var current = Path.of(System.getProperty("user.dir")).toAbsolutePath()
        repeat(4) {
            if (Files.exists(current.resolve(".github/workflows/import-calendar.yml"))) return current
            current = current.parent ?: return@repeat
        }
        error("프로젝트 루트의 import-calendar.yml을 찾지 못했습니다.")
    }
}
