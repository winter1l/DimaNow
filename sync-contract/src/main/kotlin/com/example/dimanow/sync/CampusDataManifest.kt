package com.example.dimanow.sync

import kotlinx.serialization.Serializable

@Serializable
data class CampusDataManifest(
    val schemaVersion: Int = 1,
    val generatedAt: String,
    val datasets: Map<String, DatasetDescriptor>,
)

@Serializable
data class DatasetDescriptor(
    val revision: Long,
    val state: String,
    val publishedAt: String,
    val lastAttemptAt: String,
    val url: String,
    val sha256: String,
    val sourceUrl: String,
    val message: String? = null,
)

@Serializable
data class ShuttlePayload(
    val schemaVersion: Int = 1,
    val departures: List<ShuttlePayloadRecord>,
)
