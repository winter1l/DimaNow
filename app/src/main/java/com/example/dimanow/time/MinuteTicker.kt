package com.example.dimanow.time

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import java.time.Clock
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.channels.awaitClose

class MinuteTicker(
    private val clock: Clock = Clock.system(CAMPUS_ZONE),
) {
    val ticks: Flow<ZonedDateTime> = flow {
        while (true) {
            val now = ZonedDateTime.now(clock).withZoneSameInstant(CAMPUS_ZONE)
            emit(now)
            val nextMinute = now.plusMinutes(1).withSecond(0).withNano(0)
            delay(Duration.between(now, nextMinute).toMillis().coerceAtLeast(1L))
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun ticksWithChanges(changes: Flow<Unit>): Flow<ZonedDateTime> = changes
        .onStart { emit(Unit) }
        .flatMapLatest { ticks }

    fun ticksWithSystemChanges(context: Context): Flow<ZonedDateTime> = ticksWithChanges(
        callbackFlow {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    trySend(Unit)
                }
            }
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
                addAction(Intent.ACTION_DATE_CHANGED)
            }
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(receiver, filter)
            }
            awaitClose { runCatching { context.unregisterReceiver(receiver) } }
        },
    )

    companion object {
        val CAMPUS_ZONE: ZoneId = ZoneId.of("Asia/Seoul")
    }
}
