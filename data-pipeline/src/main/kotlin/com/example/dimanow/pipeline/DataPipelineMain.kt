package com.example.dimanow.pipeline

import java.nio.file.Path
import java.nio.file.Files
import java.time.Clock
import java.time.ZoneId
import org.jsoup.Jsoup

fun main(args: Array<String>) {
    val command = args.firstOrNull() ?: error("게시 명령이 필요합니다.")
    val expectedSize = if (command == "publish-dorm-meal") 6 else 3
    require(args.size == expectedSize) {
        "publish-shuttle <input.csv> <site> | publish-meal ignored <site> | publish-notice ignored <site> | publish-dorm-meal <image> <site> <submission-id> <source-image-url> <mime-type>"
    }
    val site = Path.of(args[2])
    val publisher = StaticDataPublisher(site)
    val now = Clock.system(ZoneId.of("Asia/Seoul")).instant()
    when (command) {
        "publish-shuttle" -> publisher.publishShuttle(Files.readString(Path.of(args[1])), publisher.nextRevision("shuttle"), now)
        "publish-notice" -> runCatching {
            val html = Jsoup.connect("https://www.dima.ac.kr/?p=111").userAgent("DIMA-Now/1.3 GitHub data pipeline").get().outerHtml()
            publisher.publishNotices(NoticePayloadBuilder().build(html), publisher.nextRevision("notice"), now)
        }.getOrElse { error ->
            publisher.recordFailure("notice", "ERROR", error.message ?: error.javaClass.simpleName, now)
            System.err.println("공지 게시 실패: ${error.message}")
        }
        "publish-meal" -> runCatching {
            when (val result = MealRemotePipeline().fetchPublication()) {
                is MealPublicationResult.Published -> publisher.publishMeal(result.payload, publisher.nextRevision("meal"), now)
                MealPublicationResult.Waiting -> publisher.recordFailure("meal", "WAITING", "아직 새 식단이 올라오지 않았어요", now)
            }
        }.getOrElse { error ->
            publisher.recordFailure("meal", "NEEDS_REVIEW", error.message ?: error.javaClass.simpleName, now)
            System.err.println("식단 게시 확인 필요: ${error.message}")
        }
        "publish-dorm-meal" -> runCatching {
            DormitoryMealSubmissionProcessor(
                publisher = publisher,
                geminiClient = GeminiDormitoryMealClient(System.getenv("GEMINI_API_KEY").orEmpty()),
            ).process(
                imagePath = Path.of(args[1]),
                mimeType = args[5],
                sourceImageUrl = args[4],
                submissionId = args[3],
            )
        }.getOrElse { error ->
            publisher.publishDormitorySubmissionStatus(
                com.example.dimanow.sync.DormitoryMealSubmissionStatus(
                    submissionId = args[3],
                    state = "ERROR",
                    message = "식단 사진을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.",
                    updatedAt = now.toString(),
                ),
            )
            System.err.println("기숙사 식단 처리 실패: ${error.message}")
        }
        else -> error("알 수 없는 명령: $command")
    }
}
