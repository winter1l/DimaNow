package com.example.dimanow.domain

import java.time.LocalDate

/** 공식 홈페이지 공지사항 한 건. */
data class CampusNotice(
    val id: String,
    val title: String,
    val url: String,
    val date: LocalDate,
    val isPinned: Boolean,
)
