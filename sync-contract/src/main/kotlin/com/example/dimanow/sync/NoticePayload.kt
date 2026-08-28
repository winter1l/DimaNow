package com.example.dimanow.sync

import kotlinx.serialization.Serializable

@Serializable
data class NoticePayload(
    val schemaVersion: Int = 1,
    val notices: List<NoticePayloadRecord>,
)

@Serializable
data class NoticePayloadRecord(
    val id: String,
    val title: String,
    val url: String,
    val date: String,
    val pinned: Boolean,
)
