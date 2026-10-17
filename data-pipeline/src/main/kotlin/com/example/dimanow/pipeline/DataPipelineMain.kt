package com.example.dimanow.pipeline

import java.nio.file.Path
import java.nio.file.Files
import java.time.Clock
import java.time.ZoneId
import org.jsoup.Jsoup

fun main(args: Array<String>) {
    val command = args.firstOrNull() ?: error("게시 명령이 필요합니다.")
    // publish-dorm-pending is the backlog mode used by main's workflow (photos fetched from private R2, D-100).
    val expectedSize = when (command) {
        "publish-dorm-pending" -> 4
        else -> 3
    }
    require(args.size == expectedSize) {
        "publish-shuttle <input.csv> <site> | publish-calendar <calendar.json> <site> | publish-app-release <release.json> <site> | publish-meal ignored <site> | publish-notice ignored <site> | " +
            "publish-dorm-pending <submission-checkout> <site> <index-file>"
    }
    val site = Path.of(args[2])
    val publisher = StaticDataPublisher(site)
    val now = Clock.system(ZoneId.of("Asia/Seoul")).instant()
    if (command == "collect-meal" && !publisher.shouldCollectStudentMeal(now.atZone(ZoneId.of("Asia/Seoul")).toLocalDate())) {
        println("이번 주 학생식단이 이미 게시되어 수집을 건너뜁니다.")
        return
    }
    when (command) {
        "publish-shuttle" -> publisher.publishShuttle(Files.readString(Path.of(args[1])), publisher.nextRevision("shuttle"), now)
        "publish-app-release" -> publisher.publishAppRelease(Files.readString(Path.of(args[1])), publisher.nextRevision("app_release"), now)
        "revoke-dorm-meal" -> publisher.revokeDormitoryMeal(now)
        "publish-calendar" -> publisher.publishCalendar(Files.readString(Path.of(args[1])), publisher.nextRevision("calendar"), now)
        "publish-notice" -> runCatching {
            val html = Jsoup.connect("https://www.dima.ac.kr/?p=111").userAgent("DIMA-Now GitHub data pipeline").get().outerHtml()
            publisher.publishNotices(NoticePayloadBuilder().build(html), publisher.nextRevision("notice"), now)
        }.getOrElse { error ->
            publisher.recordFailure("notice", "ERROR", error.message ?: error.javaClass.simpleName, now)
            System.err.println("공지 게시 실패: ${error.message}")
        }
        "publish-meal", "collect-meal" -> runCatching {
            when (val result = MealRemotePipeline().fetchPublication()) {
                is MealPublicationResult.Published -> publisher.publishMeal(result.payload, publisher.nextRevision("meal"), now)
                MealPublicationResult.Waiting -> publisher.recordFailure("meal", "WAITING", "아직 새 식단이 올라오지 않았어요", now)
            }
        }.getOrElse { error ->
            publisher.recordFailure("meal", "NEEDS_REVIEW", error.message ?: error.javaClass.simpleName, now)
            System.err.println("식단 게시 확인 필요: ${error.message}")
        }
        "publish-dorm-pending" -> {
            val submissions = PendingDormitorySubmission.parseIndex(Files.readString(Path.of(args[3]))) {
                System.err.println("형식이 올바르지 않은 기숙사 제출 항목을 건너뜁니다.")
            }
            val statuses = DormitoryMealSubmissionProcessor(
                publisher = publisher,
                geminiClient = GeminiDormitoryMealClient(System.getenv("GEMINI_API_KEY").orEmpty()),
            ).processPending(submissionRoot = Path.of(args[1]), submissions = submissions)
            statuses.forEach { println("기숙사 제출 ${it.submissionId}: ${it.state}") }
            println("기숙사 제출 ${statuses.size}건 처리 (대기 목록 ${submissions.size}건)")
        }
        else -> error("알 수 없는 명령: $command")
    }
}
