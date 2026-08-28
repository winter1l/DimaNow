package com.example.dimanow.pipeline

import java.nio.file.Path
import java.nio.file.Files
import java.time.Clock
import java.time.ZoneId
import org.jsoup.Jsoup

fun main(args: Array<String>) {
    require(args.size == 3) { "publish-shuttle <input.csv> <site> | publish-meal ignored <site> | publish-notice ignored <site>" }
    val command = args[0]
    val site = Path.of(args[2])
    val publisher = StaticDataPublisher(site)
    val now = Clock.system(ZoneId.of("Asia/Seoul")).instant()
    when (command) {
        "publish-shuttle" -> publisher.publishShuttle(Files.readString(Path.of(args[1])), publisher.nextRevision("shuttle"), now)
        "publish-notice" -> runCatching {
            val html = Jsoup.connect("https://www.dima.ac.kr/?p=111").userAgent("DIMA-Now/1.1 GitHub data pipeline").get().outerHtml()
            publisher.publishNotices(NoticePayloadBuilder().build(html), publisher.nextRevision("notice"), now)
        }.getOrElse { error ->
            publisher.recordFailure("notice", "ERROR", error.message ?: error.javaClass.simpleName, now)
            System.err.println("공지 게시 실패: ${error.message}")
        }
        "publish-meal" -> runCatching {
            publisher.publishMeal(MealRemotePipeline().fetch(), publisher.nextRevision("meal"), now)
        }.getOrElse { error ->
            publisher.recordFailure("meal", "NEEDS_REVIEW", error.message ?: error.javaClass.simpleName, now)
            System.err.println("식단 게시 확인 필요: ${error.message}")
        }
        else -> error("알 수 없는 명령: $command")
    }
}
