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
import com.example.dimanow.school.LibraryDueList
import com.example.dimanow.school.SchoolReadCache
import com.example.dimanow.time.MinuteTicker
import com.example.dimanow.ui.todayHomeTasks
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.first

/**
 * The morning deadline notification (D-125(1), D-126(3)): one notification at 08:30 for everything due today,
 * such as `오늘 마감 과제 2개, 퀴즈 1개`, and since D-131 the library books due back today (`오늘 마감 과제 2개,
 * 반납 1권`). LMS and the library need the student's sign-in, so it reads the lists last loaded in the Courses tab
 * and on the 도서관 page (no account access in the background, D-074) and says when they were read.
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
        val library = SchoolReadCache.savedLibraryDue()
        deadlineDigest(app.lmsSource.snapshot.first(), now, library)?.let { DeadlineDigestNotifier.post(applicationContext, it) }
        return Result.success()
    }
}

/** [page] is the app page the notification opens: 수업, or 도서관 when only books are due. */
internal data class DeadlineDigest(val title: String, val lines: List<String>, val basis: String?, val page: String = "COURSES")

/**
 * Today's open deadlines and the library books due back today (D-131) as one notification, or null when nothing is
 * due today. [library] is the due list saved by the last 도서관 read.
 */
internal fun deadlineDigest(snapshot: LmsSnapshot, now: ZonedDateTime, library: LibraryDueList? = null): DeadlineDigest? {
    val items = todayHomeTasks(snapshot.items, now.toInstant())
    val returns = library?.books.orEmpty().filter { it.dueOn == now.toLocalDate() }
    if (items.isEmpty() && returns.isEmpty()) return null
    val counts = items.groupBy { kindLabel(it.kind) }.map { (label, group) -> "$label ${group.size}개" } +
        listOfNotNull(returns.size.takeIf { it > 0 }?.let { "반납 ${it}권" })
    val lmsReadAt = snapshot.lastSuccessAt?.takeIf { items.isNotEmpty() }?.atZone(MinuteTicker.CAMPUS_ZONE)?.format(DIGEST_BASIS)
    val libraryReadAt = library?.readAt?.takeIf { returns.isNotEmpty() }?.atZone(MinuteTicker.CAMPUS_ZONE)?.format(DIGEST_BASIS)
    return DeadlineDigest(
        title = if (items.isEmpty()) "오늘 반납 ${returns.size}권" else "오늘 마감 ${counts.joinToString(", ")}",
        lines = items.map { item ->
            "${requireNotNull(item.dueAt).atZone(MinuteTicker.CAMPUS_ZONE).format(DIGEST_TIME)} ${item.courseName} · ${item.title}"
        } + returns.map { "반납 · ${it.title}" },
        basis = when {
            lmsReadAt != null && libraryReadAt != null -> "과제 $lmsReadAt · 도서관 $libraryReadAt 기준"
            lmsReadAt != null -> "${lmsReadAt}에 불러온 목록 기준"
            libraryReadAt != null -> "${libraryReadAt}에 불러온 도서관 목록 기준"
            else -> null
        },
        page = if (items.isEmpty()) "LIBRARY" else "COURSES",
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
        CampusNotifications.channel(context, CHANNEL_ID, "과제 마감", "오늘 마감인 과제와 퀴즈, 반납일인 도서관 책을 아침에 한 번 모아 알려 줘요.")
        val style = NotificationCompat.InboxStyle().also { inbox -> digest.lines.forEach(inbox::addLine) }
        digest.basis?.let(style::setSummaryText)
        val notification = CampusNotifications.builder(context, CHANNEL_ID)
            .setContentTitle(digest.title)
            .setContentText(digest.lines.first())
            .setStyle(style)
            .setContentIntent(CampusNotifications.openPage(context, 6211, digest.page))
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}
