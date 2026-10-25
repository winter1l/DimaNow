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
 * only the parsed facts are kept ([RollCallStore]); no room needs choosing, but plain 1:1 chats and questions are
 * left out (D-126(4)). A muted chat posts no notification,
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
        val extras = notification.extras
        // D-126(4): the 자치회 posts in a group room; a 1:1 chat ("오늘 점호 없어?") is not an announcement.
        if (isOneToOneChat(
                isGroupConversation = extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false),
                conversationTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE),
                subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT),
            )
        ) return
        RollCallStore.attach(this)
        var added = false
        rollCallTexts(extras, sbn.postTime).forEach { (text, postedAt) ->
            val receivedAt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(postedAt), DormHours.CAMPUS_ZONE)
            RollCallNoticeParser.parse(text, receivedAt)?.let {
                RollCallStore.add(it, receivedAt.toLocalDate())
                added = true
            }
        }
        if (added) {
            OutingReminder.reschedule(this, RollCallStore.notices.value, ZonedDateTime.now(DormHours.CAMPUS_ZONE))
        }
    }

    private companion object {
        const val KAKAO_TALK = "com.kakao.talk"
    }
}

/**
 * Whether a KakaoTalk notification is plainly from a 1:1 chat: not marked as a group conversation and naming no room
 * (neither a conversation title nor a sub text). Assumed from Android's MessagingStyle; which fields KakaoTalk fills
 * is not yet seen on a real 점호 notice (Q-030), so anything that may be a room still counts.
 */
internal fun isOneToOneChat(isGroupConversation: Boolean, conversationTitle: CharSequence?, subText: CharSequence?): Boolean =
    !isGroupConversation && conversationTitle.isNullOrBlank() && subText.isNullOrBlank()

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
