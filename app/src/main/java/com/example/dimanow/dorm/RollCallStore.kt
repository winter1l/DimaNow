package com.example.dimanow.dorm

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * 점호 announcements read from KakaoTalk (D-112), kept in no-backup storage for two weeks so the 기숙사 page shows
 * them after a restart. Only the parsed facts are stored; messages, rooms and senders never are.
 */
object RollCallStore {
    private const val KEEP_DAYS = 14L
    // One file, one writer: the load, every save and the delete run in launch order on a single IO lane, and a save
    // writes the notices as they are when it runs (after the load merged), so concurrent adds cannot interleave
    // the file or drop what was saved before.
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    @Volatile private var file: File? = null

    private val mutableNotices = MutableStateFlow<List<RollCallNotice>>(emptyList())
    val notices: StateFlow<List<RollCallNotice>> = mutableNotices.asStateFlow()
    private val loaded = CompletableDeferred<Unit>()

    /** Waits for the saved notices after [attach], for code that runs in a cold process (the outing reminder). */
    suspend fun awaitLoaded() = loaded.await()

    /** Called from the application and the listener; loads the saved notices once. */
    fun attach(context: Context) {
        if (file != null) return
        val target = File(context.applicationContext.noBackupFilesDir, "dorm/roll-call.json")
        file = target
        io.launch {
            val saved = runCatching { decodeRollCallNotices(target.readText()) }.getOrDefault(emptyList())
            mutableNotices.update { current -> (saved + current).distinct() }
            loaded.complete(Unit)
        }
    }

    fun add(notice: RollCallNotice, today: LocalDate) {
        mutableNotices.update { current ->
            (current + notice).distinct().filter { !it.date.isBefore(today.minusDays(KEEP_DAYS)) }.sortedBy { it.receivedAt }
        }
        save()
    }

    fun clear() {
        mutableNotices.value = emptyList()
        file?.let { target -> io.launch { target.delete() } }
    }

    private fun save() {
        val target = file ?: return
        io.launch {
            runCatching {
                target.parentFile?.mkdirs()
                val partial = File(target.parentFile, target.name + ".tmp")
                partial.writeText(encodeRollCallNotices(mutableNotices.value))
                if (!partial.renameTo(target)) {
                    target.writeText(partial.readText())
                    partial.delete()
                }
            }
        }
    }

    /** Whether the student allowed DIMA Now to read notifications (the only switch for this feature). */
    fun accessGranted(context: Context): Boolean =
        context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

    /**
     * Opens the notification-access page for this app, or the general list where a build has no per-app page
     * (some OEM and work-profile builds), instead of crashing.
     */
    fun openAccessSettings(context: Context) {
        runCatching { context.startActivity(accessSettingsIntent(context)) }.onFailure {
            runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        }
    }

    /** The system page where notification access for this app is turned on or off. */
    fun accessSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                ComponentName(context, KakaoRollCallListener::class.java).flattenToString(),
            )
}

internal fun encodeRollCallNotices(notices: List<RollCallNotice>): String = buildJsonArray {
    notices.forEach { notice ->
        addJsonObject {
            put("date", notice.date.toString())
            put("held", notice.held)
            notice.time?.let { put("time", it.toString()) }
            notice.applyBy?.let { put("applyBy", it.toString()) }
            notice.reason?.let { put("reason", it) }
            put("receivedAt", notice.receivedAt.toEpochMilli())
        }
    }
}.toString()

internal fun decodeRollCallNotices(text: String): List<RollCallNotice> =
    (Json.parseToJsonElement(text) as? JsonArray).orEmpty().mapNotNull { element ->
        val item = element as? JsonObject ?: return@mapNotNull null
        fun string(name: String) = item[name]?.jsonPrimitive?.content
        RollCallNotice(
            date = string("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@mapNotNull null,
            held = item["held"]?.jsonPrimitive?.booleanOrNull ?: return@mapNotNull null,
            time = string("time")?.let { runCatching { LocalTime.parse(it) }.getOrNull() },
            applyBy = string("applyBy")?.let { runCatching { LocalTime.parse(it) }.getOrNull() },
            reason = string("reason"),
            receivedAt = item["receivedAt"]?.jsonPrimitive?.longOrNull?.let(Instant::ofEpochMilli) ?: return@mapNotNull null,
        )
    }
