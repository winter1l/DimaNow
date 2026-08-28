package com.example.dimanow.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val KST_SOURCE_TIME = DateTimeFormatter
    .ofPattern("yyyy년 M월 d일 HH:mm 'KST'", Locale.KOREAN)
    .withZone(ZoneId.of("Asia/Seoul"))

fun formatSourceSuccessTime(instant: Instant): String = KST_SOURCE_TIME.format(instant)
