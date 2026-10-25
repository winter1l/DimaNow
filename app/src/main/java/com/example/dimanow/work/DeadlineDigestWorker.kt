package com.example.dimanow.work

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.dimanow.DimaNowApplication
import com.example.dimanow.lms.LmsSnapshot
import com.example.dimanow.lms.kindLabel
import com.example.dimanow.time.MinuteTicker
import com.example.dimanow.ui.todayHomeTasks
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.first

/**
 * The morning deadline notification (D-125(1), D-126(3)): one notification at 08:30 for everything due today,
 * such as `오늘 마감 과제 2개, 퀴즈 1개`. LMS needs the student's sign-in, so it reads the list last loaded in the
 * Courses tab and says when that was.
 */
object DeadlineDigestScheduler {
    internal const val WORK_NAME = "deadline-digest-0830"
    private val AT = LocalTime.of(8, 30)

    fun schedule(context: Context) = DailyWork.ensure(context, WORK_NAME, AT, DeadlineDigestWorker::class.java)

    internal fun scheduleNext(context: Context) = DailyWork.next(context, WORK_NAME, AT, DeadlineDigestWorker::class.java)
}

class DeadlineDigestWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        DeadlineDigestScheduler.scheduleNext(applicationContext)
        val app = applicationContext as DimaNowApplication
        val preferences = app.preferences
        if (!preferences.deadlineDigest.first()) return Result.success()
        val now = ZonedDateTime.now(MinuteTicker.CAMPUS_ZONE)
        if (now.hour !in 7..21) return Result.success()
        val today = now.toLocalDate()
        if (preferences.deadlineDigestLastDay.first() == today.toEpochDay()) return Result.success()
        preferences.setDeadlineDigestLastDay(today.toEpochDay())
        deadlineDigest(app.lmsSource.snapshot.first(), now)?.let { DeadlineDigestNotifier.post(applicationContext, it) }
        return Result.success()
    }
}

internal data class DeadlineDigest(val title: String, val lines: List<String>, val basis: String?)

/** Today's open deadlines as one notification, or null when nothing is due today. */
internal fun deadlineDigest(snapshot: LmsSnapshot, now: ZonedDateTime): DeadlineDigest? {
    val items = todayHomeTasks(snapshot.items, now.toInstant())
    if (items.isEmpty()) return null
    val counts = items.groupBy { kindLabel(it.kind) }.map { (label, group) -> "$label ${group.size}개" }
    return DeadlineDigest(
        title = "오늘 마감 ${counts.joinToString(", ")}",
        lines = items.map { item ->
            "${requireNotNull(item.dueAt).atZone(MinuteTicker.CAMPUS_ZONE).format(DIGEST_TIME)} ${item.courseName} · ${item.title}"
        },
        basis = snapshot.lastSuccessAt?.let { "${it.atZone(MinuteTicker.CAMPUS_ZONE).format(DIGEST_BASIS)}에 불러온 목록 기준" },
    )
}

private val DIGEST_TIME = DateTimeFormatter.ofPattern("HH:mm")
private val DIGEST_BASIS = DateTimeFormatter.ofPattern("M/d HH:mm")

internal object DeadlineDigestNotifier {
    private const val CHANNEL_ID = "lms_deadlines"
    private const val NOTIFICATION_ID = 6211

    @SuppressLint("MissingPermission") // CampusNotifications.canPost checks it
    fun post(context: Context, digest: DeadlineDigest) {
        if (!CampusNotifications.canPost(context)) return
        CampusNotifications.channel(context, CHANNEL_ID, "과제 마감", "오늘 마감인 과제와 퀴즈를 아침에 한 번 모아 알려 줘요.")
        val style = NotificationCompat.InboxStyle().also { inbox -> digest.lines.forEach(inbox::addLine) }
        digest.basis?.let(style::setSummaryText)
        val notification = CampusNotifications.builder(context, CHANNEL_ID)
            .setContentTitle(digest.title)
            .setContentText(digest.lines.first())
            .setStyle(style)
            .setContentIntent(CampusNotifications.openPage(context, 6211, "COURSES"))
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}
