package com.example.dimanow.dorm

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.example.dimanow.DimaNowApplication
import com.example.dimanow.guidance.HomeBase
import com.example.dimanow.work.CampusNotifications
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * D-126(4): on a day the 자치회 announced on KakaoTalk that 점호 is held, 엔터관 students are reminded an hour before
 * the outing application closes, and the tap opens 외출·외박 신청. Days without such a notice get nothing: the
 * usual Tuesday rule alone never rings (D-125(3), "카톡 알림을 확인한 경우에만").
 */
object OutingReminder {
    private const val REQUEST_CODE = 6221
    private const val NOTIFICATION_ID = 6221
    private const val CHANNEL_ID = "dorm_outing"

    /**
     * Books the next reminder from the stored notices, after a notice arrives. The newest notice for each day
     * decides that day; the receiver checks again when it rings.
     */
    fun reschedule(context: Context, notices: List<RollCallNotice>, now: ZonedDateTime) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        val pending = alarmIntent(context)
        val next = nextReminder(notices, now) ?: return alarm.cancel(pending)
        val trigger = next.toInstant().toEpochMilli()
        if (alarm.canScheduleExactAlarms()) alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
        else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
    }

    internal fun nextReminder(notices: List<RollCallNotice>, now: ZonedDateTime): ZonedDateTime? =
        notices.groupBy { it.date }.values
            .mapNotNull { day -> remindAt(day.maxBy { it.receivedAt }, now) }
            .minOrNull()

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(alarmIntent(context))
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    /**
     * When to ring for [notice]: an hour before its deadline (21:00 when it names none), right away when that hour
     * has begun, and never for a cancelled 점호, a past day or a deadline already gone.
     */
    internal fun remindAt(notice: RollCallNotice, now: ZonedDateTime): ZonedDateTime? {
        if (!notice.held || notice.date.isBefore(now.toLocalDate())) return null
        val deadline = notice.date.atTime(deadline(notice)).atZone(now.zone)
        if (!deadline.isAfter(now)) return null
        val oneHourBefore = deadline.minusHours(1)
        return if (oneHourBefore.isAfter(now)) oneHourBefore else now
    }

    /** The minute applications still count: an announced `20시 59분까지`, or the usual 21:00 (exclusive). */
    private fun deadline(notice: RollCallNotice): LocalTime = notice.applyBy ?: RollCallRules.USUAL_APPLY_BY

    internal fun text(notice: RollCallNotice): Pair<String, String> {
        val title = notice.time?.let { "오늘 ${it.format(CLOCK)} 점호가 있어요" } ?: "오늘 점호가 있어요"
        val body = notice.applyBy?.let { "외출·외박은 ${it.format(CLOCK)}까지 신청할 수 있어요" }
            ?: "외출·외박은 ${RollCallRules.USUAL_APPLY_BY.format(CLOCK)} 전에 신청해 주세요"
        return title to body
    }

    @SuppressLint("MissingPermission") // CampusNotifications.canPost checks it
    internal fun post(context: Context, notice: RollCallNotice) {
        if (!CampusNotifications.canPost(context)) return
        CampusNotifications.channel(
            context,
            CHANNEL_ID,
            "외출·외박 신청",
            "점호가 있다는 카톡 공지를 읽은 날, 외출·외박 신청 마감 1시간 전에 알려 줘요.",
        )
        val (title, body) = text(notice)
        val notification = CampusNotifications.builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(CampusNotifications.openPage(context, REQUEST_CODE, "OUTING"))
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, OutingReminderReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private val CLOCK = DateTimeFormatter.ofPattern("HH:mm")
}

/**
 * Rings for [OutingReminder]. It checks again what holds now: the setting, the 엔터관 home base and today's newest
 * notice, so a cancelling notice that came later, or a deadline already gone, stays quiet.
 */
class OutingReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext as DimaNowApplication
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                if (!app.preferences.outingReminders.first() || app.preferences.homeBase.first() != HomeBase.YEIN) return@launch
                RollCallStore.attach(app)
                RollCallStore.awaitLoaded()
                val now = ZonedDateTime.now(DormHours.CAMPUS_ZONE)
                val today: LocalDate = now.toLocalDate()
                val notice = RollCallStore.notices.value.filter { it.date == today }.maxByOrNull { it.receivedAt } ?: return@launch
                if (OutingReminder.remindAt(notice, now) != null) OutingReminder.post(app, notice)
            } finally {
                pending.finish()
            }
        }
    }
}
