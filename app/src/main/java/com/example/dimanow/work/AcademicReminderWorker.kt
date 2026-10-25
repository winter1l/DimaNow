package com.example.dimanow.work

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.dimanow.DimaNowApplication
import com.example.dimanow.calendar.AcademicReminder
import com.example.dimanow.calendar.ServiceCalendarStore
import com.example.dimanow.calendar.academicReminders
import com.example.dimanow.time.MinuteTicker
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.first

/**
 * The 학사 일정 morning reminder (D-107): once a day at 08:30 it posts what starts, ends or (for exams)
 * begins tomorrow. It reads only the published calendar on the device — never a school account.
 */
object AcademicReminderScheduler {
    /** Renamed with Q-032 (c): the old periodic `academic-reminder-daily` is cancelled by `RefreshScheduler`. */
    internal const val WORK_NAME = "academic-reminder-0830"
    private val AT = LocalTime.of(8, 30)

    fun schedule(context: Context) = DailyWork.ensure(context, WORK_NAME, AT, AcademicReminderWorker::class.java)

    internal fun scheduleNext(context: Context) = DailyWork.next(context, WORK_NAME, AT, AcademicReminderWorker::class.java)
}

class AcademicReminderWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        AcademicReminderScheduler.scheduleNext(applicationContext)
        val preferences = (applicationContext as DimaNowApplication).preferences
        if (!preferences.academicReminders.first()) return Result.success()
        val now = ZonedDateTime.now(MinuteTicker.CAMPUS_ZONE)
        // A run the phone held back until the night stays quiet; tomorrow's is already booked for 08:30.
        if (now.hour !in 7..21) return Result.success()
        val today = now.toLocalDate()
        if (preferences.academicReminderLastDay.first() == today.toEpochDay()) return Result.success()
        preferences.setAcademicReminderLastDay(today.toEpochDay())
        AcademicReminderNotifier.post(applicationContext, academicReminders(today, ServiceCalendarStore.current))
        return Result.success()
    }
}

internal object AcademicReminderNotifier {
    private const val CHANNEL_ID = "academic_schedule"

    @SuppressLint("MissingPermission") // CampusNotifications.canPost checks it
    fun post(context: Context, reminders: List<AcademicReminder>) {
        if (reminders.isEmpty() || !CampusNotifications.canPost(context)) return
        CampusNotifications.channel(
            context,
            CHANNEL_ID,
            "학사 일정",
            "수강신청 정정, 수업평가, 시험 같은 학사 일정이 시작하거나 끝나는 날 아침에 알려 줘요.",
        )
        val open = CampusNotifications.openPage(context, 6201, "DASHBOARD")
        val manager = NotificationManagerCompat.from(context)
        reminders.forEach { reminder ->
            val notification = CampusNotifications.builder(context, CHANNEL_ID)
                .setContentTitle(reminder.title)
                .setContentText(reminder.text)
                .setContentIntent(open)
                .build()
            manager.notify(reminder.key.hashCode(), notification)
        }
    }
}
