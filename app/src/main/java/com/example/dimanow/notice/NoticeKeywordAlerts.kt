package com.example.dimanow.notice

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationManagerCompat
import com.example.dimanow.data.AppPreferences
import com.example.dimanow.domain.CampusNotice
import com.example.dimanow.work.CampusNotifications
import kotlinx.coroutines.flow.first

/**
 * Notice keyword alerts (D-128(3)): after the 학교 공지 list syncs, a notice the app has not seen before whose title
 * holds one of the student's keywords gets its own notification. The first sync only remembers what is there, so
 * turning the feature on never floods old notices. Keywords stay on the device.
 */
object NoticeKeywordAlerts {
    private const val CHANNEL_ID = "notice_keywords"

    /** New notices (not in [seenIds]) that match a keyword, each with the keyword it matched. */
    internal fun matches(notices: List<CampusNotice>, seenIds: Set<String>?, keywords: List<String>): List<Pair<CampusNotice, String>> {
        if (seenIds == null) return emptyList()
        return notices.filter { it.id !in seenIds }.mapNotNull { notice ->
            val title = normalize(notice.title)
            keywords.firstOrNull { keyword -> normalize(keyword).let { it.isNotEmpty() && it in title } }
                ?.let { notice to it.trim() }
        }
    }

    /** Spaces do not count: "수강 신청" matches the keyword "수강신청". */
    private fun normalize(text: String): String = text.filterNot(Char::isWhitespace).lowercase()

    /** Runs after every notice sync; always remembers the current list, alerting only when the setting is on. */
    suspend fun afterSync(context: Context, preferences: AppPreferences, notices: List<CampusNotice>) {
        if (notices.isEmpty()) return
        val seen = preferences.noticeSeenIds.first()
        if (preferences.noticeKeywordAlerts.first()) {
            matches(notices, seen, preferences.noticeKeywords.first()).forEach { (notice, keyword) -> post(context, notice, keyword) }
        }
        preferences.setNoticeSeenIds(notices.map { it.id }.toSet())
    }

    @SuppressLint("MissingPermission") // CampusNotifications.canPost checks it
    private fun post(context: Context, notice: CampusNotice, keyword: String) {
        if (!CampusNotifications.canPost(context)) return
        CampusNotifications.channel(context, CHANNEL_ID, "공지 키워드", "관심 키워드가 들어간 새 학교 공지를 알려 줘요.")
        val open = PendingIntent.getActivity(
            context,
            notice.id.hashCode(),
            Intent(Intent.ACTION_VIEW, Uri.parse(notice.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = CampusNotifications.builder(context, CHANNEL_ID)
            .setContentTitle("새 공지 · $keyword")
            .setContentText(notice.title)
            .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(notice.title))
            .setContentIntent(open)
            .build()
        NotificationManagerCompat.from(context).notify("notice_${notice.id}", NOTIFICATION_ID, notification)
    }

    private const val NOTIFICATION_ID = 6231
}
