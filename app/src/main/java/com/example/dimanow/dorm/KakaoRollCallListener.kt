package com.example.dimanow.dorm

import android.app.Notification
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.time.Instant
import java.time.ZonedDateTime

/**
 * Reads 예인관 점호 announcements from KakaoTalk notifications (D-112, narrowly superseding D-072's removal of
 * Kakao reading). Only `com.kakao.talk` notifications are looked at, only text that mentions 점호 is parsed, and
 * only the parsed facts are kept ([RollCallStore]); no room needs choosing. A muted chat posts no notification,
 * so its announcements cannot be read.
 */
class KakaoRollCallListener : NotificationListenerService() {
    override fun onListenerConnected() {
        RollCallStore.attach(this)
        // D-115: an announcement already in the shade — posted before access was allowed, or while the listener was
        // unbound for an app update — is read on connect too. Reading one again adds nothing: the store keeps each once.
        runCatching { activeNotifications }.getOrNull().orEmpty().forEach(::read)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        read(sbn)
    }

    private fun read(sbn: StatusBarNotification) {
        if (sbn.packageName != KAKAO_TALK) return
        val notification = sbn.notification ?: return
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        RollCallStore.attach(this)
        rollCallTexts(notification.extras, sbn.postTime).forEach { (text, postedAt) ->
            val receivedAt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(postedAt), DormHours.CAMPUS_ZONE)
            RollCallNoticeParser.parse(text, receivedAt)?.let { RollCallStore.add(it, receivedAt.toLocalDate()) }
        }
    }

    private companion object {
        const val KAKAO_TALK = "com.kakao.talk"
    }
}

/**
 * The message texts in a notification that mention 점호, with their own times when the conversation style gives
 * them: KakaoTalk puts whole messages in [Notification.EXTRA_MESSAGES]; the big and plain texts are fallbacks.
 */
internal fun rollCallTexts(extras: Bundle, postedAt: Long): List<Pair<String, Long>> {
    @Suppress("DEPRECATION")
    val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES).orEmpty().mapNotNull { item ->
        val bundle = item as? Bundle ?: return@mapNotNull null
        val text = bundle.getCharSequence("text")?.toString() ?: return@mapNotNull null
        text to (bundle.getLong("time").takeIf { it > 0 } ?: postedAt)
    }
    val fallbacks = listOfNotNull(
        extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
        extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
    ).map { it to postedAt }
    return (messages.ifEmpty { fallbacks }).filter { (text, _) -> "점호" in text }.distinctBy { it.first }
}
