package com.example.dimanow.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.dimanow.DimaNowApplication
import com.example.dimanow.MainActivity
import com.example.dimanow.R
import com.example.dimanow.calendar.AcademicReminder
import com.example.dimanow.calendar.ServiceCalendarStore
import com.example.dimanow.calendar.academicReminders
import com.example.dimanow.time.MinuteTicker
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

/**
 * The 학사 일정 morning reminder (D-107): once a day around 08:30 it posts what starts, ends or (for exams)
 * begins tomorrow. It reads only the published calendar on the device — never a school account.
 */
object AcademicReminderScheduler {
    private const val WORK_NAME = "academic-reminder-daily"

    fun schedule(context: Context) {
        val now = ZonedDateTime.now(MinuteTicker.CAMPUS_ZONE)
        var next = now.toLocalDate().atTime(8, 30).atZone(MinuteTicker.CAMPUS_ZONE)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val request = PeriodicWorkRequestBuilder<AcademicReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}

class AcademicReminderWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val preferences = (applicationContext as DimaNowApplication).preferences
        if (!preferences.academicReminders.first()) return Result.success()
        val now = ZonedDateTime.now(MinuteTicker.CAMPUS_ZONE)
        // A periodic run that drifted into the night waits for the next one rather than waking anyone.
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

    fun post(context: Context, reminders: List<AcademicReminder>) {
        if (reminders.isEmpty()) return
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "학사 일정", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "수강신청 정정, 수업평가, 시험 같은 학사 일정이 시작하거나 끝나는 날 아침에 알려 줘요."
            },
        )
        val open = PendingIntent.getActivity(
            context,
            6201,
            Intent(context, MainActivity::class.java).putExtra("TARGET_PAGE", "DASHBOARD"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        reminders.forEach { reminder ->
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_dima)
                .setColor(0xFFEC268FL.toInt())
                .setContentTitle(reminder.title)
                .setContentText(reminder.text)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            manager.notify(reminder.key.hashCode(), notification)
        }
    }
}
