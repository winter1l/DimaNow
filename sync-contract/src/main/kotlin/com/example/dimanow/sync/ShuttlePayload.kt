package com.example.dimanow.sync

import kotlinx.serialization.Serializable

@Serializable
data class ShuttlePayloadRecord(
    val serviceDay: String,
    val routeId: String,
    val stopId: String,
    val direction: String,
    val originZone: String,
    val destinationZone: String,
    val departureTime: String,
    val arrivalTime: String? = null,
)
