package com.example.dimanow.live

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.dimanow.MainActivity
import com.example.dimanow.R
import com.example.dimanow.domain.GuidanceSnapshot

enum class LiveSurfaceResult {
    PROMOTION_REQUESTED,
    FALLBACK_NOTIFICATION,
    NOTIFICATION_PERMISSION_DENIED,
    CANCELLED,
}

enum class LiveSettingsDestination {
    PROMOTED_NOTIFICATION_SETTINGS,
    APP_NOTIFICATION_SETTINGS,
    APP_DETAILS,
    UNAVAILABLE,
}

enum class LivePromotionReadiness {
    READY,
    API_UNSUPPORTED,
    NOTIFICATION_PERMISSION_REQUIRED,
    APP_CONFIGURATION_INCOMPLETE,
    SYSTEM_NOT_ALLOWED,
}

enum class LiveChipContent {
    COUNTDOWN,
    CLASSROOM,
}

enum class LiveClassOrder {
    COURSE_FIRST,
    CLASSROOM_FIRST,
}

data class LiveDisplayOptions(
    val chipContent: LiveChipContent = LiveChipContent.COUNTDOWN,
    val classOrder: LiveClassOrder = LiveClassOrder.COURSE_FIRST,
)

data class LiveSurfaceDiagnostics(
    val readiness: LivePromotionReadiness,
    val notificationPermissionAllowed: Boolean,
    val manifestPermissionDeclared: Boolean,
    val promotionRequested: Boolean,
    val promotableCharacteristics: Boolean,
    val channelImportance: Int,
    val canPostPromotedNotifications: Boolean,
    val activeNotificationPresent: Boolean,
    val activeNotificationPromoted: Boolean,
    val specializedSettingsAvailable: Boolean,
)

interface LiveSurfaceController {
    fun show(snapshot: GuidanceSnapshot, presentation: LiveDisplayOptions = LiveDisplayOptions()): LiveSurfaceResult
    fun cancel(): LiveSurfaceResult
    fun openPromotionSettings(): LiveSettingsDestination
    fun diagnostics(): LiveSurfaceDiagnostics

    companion object {
        fun startUpdaterSafely(start: () -> Unit): Boolean = try {
            start()
            true
        } catch (_: IllegalStateException) {
            false
        } catch (_: SecurityException) {
            false
        }

        @Suppress("UNUSED_PARAMETER")
        fun statusChipText(
            snapshot: GuidanceSnapshot,
            presentation: LiveDisplayOptions,
            deviceLocked: Boolean,
        ): String? {
            if (presentation.chipContent != LiveChipContent.CLASSROOM) return null
            val classContent = snapshot.classContent ?: return null
            return classContent.room
        }
    }
}

object LiveSettingsPlanner {
    fun plan(
        promotedSettingsAvailable: Boolean,
        appNotificationSettingsAvailable: Boolean,
        appDetailsAvailable: Boolean,
    ): LiveSettingsDestination = when {
        promotedSettingsAvailable -> LiveSettingsDestination.PROMOTED_NOTIFICATION_SETTINGS
        appNotificationSettingsAvailable -> LiveSettingsDestination.APP_NOTIFICATION_SETTINGS
        appDetailsAvailable -> LiveSettingsDestination.APP_DETAILS
        else -> LiveSettingsDestination.UNAVAILABLE
    }
}

object LivePromotionReadinessEvaluator {
    fun evaluate(
        apiLevel: Int,
        notificationsAllowed: Boolean,
        manifestPermissionDeclared: Boolean,
        promotableCharacteristics: Boolean,
        channelImportance: Int,
        canPostPromotedNotifications: Boolean,
    ): LivePromotionReadiness = when {
        apiLevel < 36 -> LivePromotionReadiness.API_UNSUPPORTED
        !notificationsAllowed -> LivePromotionReadiness.NOTIFICATION_PERMISSION_REQUIRED
        !manifestPermissionDeclared || !promotableCharacteristics || channelImportance <= 1 ->
            LivePromotionReadiness.APP_CONFIGURATION_INCOMPLETE
        !canPostPromotedNotifications -> LivePromotionReadiness.SYSTEM_NOT_ALLOWED
        else -> LivePromotionReadiness.READY
    }
}

object LiveSurfacePlanner {
    fun plan(apiLevel: Int, notificationsAllowed: Boolean, promotedEligible: Boolean): LiveSurfaceResult = when {
        !notificationsAllowed -> LiveSurfaceResult.NOTIFICATION_PERMISSION_DENIED
        apiLevel >= 36 && promotedEligible -> LiveSurfaceResult.PROMOTION_REQUESTED
        else -> LiveSurfaceResult.FALLBACK_NOTIFICATION
    }
}

class AndroidLiveSurfaceController(private val context: Context) : LiveSurfaceController {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override fun show(snapshot: GuidanceSnapshot, presentation: LiveDisplayOptions): LiveSurfaceResult {
        if (!hasNotificationPermission()) {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
            stopUpdater()
            return LiveSurfaceResult.NOTIFICATION_PERMISSION_DENIED
        }
        ensureChannel()
        val promotionAllowed = Build.VERSION.SDK_INT >= 36 && notificationManager.canPostPromotedNotifications()
        try {
            NotificationManagerCompat.from(context).notify(
                NOTIFICATION_ID,
                buildNotification(snapshot, requestPromotion = Build.VERSION.SDK_INT >= 36, presentation = presentation),
            )
        } catch (_: SecurityException) {
            stopUpdater()
            return LiveSurfaceResult.NOTIFICATION_PERMISSION_DENIED
        }
        if (promotionAllowed && snapshot.requiresMinuteUpdates) startUpdater() else stopUpdater()
        return LiveSurfacePlanner.plan(Build.VERSION.SDK_INT, notificationsAllowed = true, promotedEligible = promotionAllowed)
    }

    override fun cancel(): LiveSurfaceResult {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        stopUpdater()
        return LiveSurfaceResult.CANCELLED
    }

    override fun openPromotionSettings(): LiveSettingsDestination {
        var promotedAvailable = Build.VERSION.SDK_INT >= 36 && canResolve(promotedSettingsIntent())
        var appNotificationsAvailable = canResolve(appNotificationSettingsIntent())
        var appDetailsAvailable = canResolve(appDetailsIntent())
        while (true) {
            val destination = LiveSettingsPlanner.plan(
                promotedSettingsAvailable = promotedAvailable,
                appNotificationSettingsAvailable = appNotificationsAvailable,
                appDetailsAvailable = appDetailsAvailable,
            )
            val intent = when (destination) {
                LiveSettingsDestination.PROMOTED_NOTIFICATION_SETTINGS -> promotedSettingsIntent()
                LiveSettingsDestination.APP_NOTIFICATION_SETTINGS -> appNotificationSettingsIntent()
                LiveSettingsDestination.APP_DETAILS -> appDetailsIntent()
                LiveSettingsDestination.UNAVAILABLE -> return destination
            }
            if (startSafely(intent)) return destination
            when (destination) {
                LiveSettingsDestination.PROMOTED_NOTIFICATION_SETTINGS -> promotedAvailable = false
                LiveSettingsDestination.APP_NOTIFICATION_SETTINGS -> appNotificationsAvailable = false
                LiveSettingsDestination.APP_DETAILS -> appDetailsAvailable = false
                LiveSettingsDestination.UNAVAILABLE -> Unit
            }
        }
    }

    override fun diagnostics(): LiveSurfaceDiagnostics {
        ensureChannel()
        val preview = buildNotification(
            GuidanceSnapshot(classContent = null, shuttleLines = emptyList(), phase = com.example.dimanow.domain.GuidancePhase.NONE),
            requestPromotion = Build.VERSION.SDK_INT >= 36,
        )
        val notificationsAllowed = hasNotificationPermission()
        val manifestPermissionDeclared = context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions
            ?.contains(PERMISSION_POST_PROMOTED_NOTIFICATIONS) == true
        val promotableCharacteristics = Build.VERSION.SDK_INT >= 36 && preview.hasPromotableCharacteristics()
        val promotionRequested = preview.extras.getBoolean(EXTRA_REQUEST_PROMOTED_ONGOING)
        val channelImportance = notificationManager.getNotificationChannel(CHANNEL_ID)?.importance
            ?: NotificationManager.IMPORTANCE_UNSPECIFIED
        val canPostPromoted = Build.VERSION.SDK_INT >= 36 && notificationManager.canPostPromotedNotifications()
        val active = notificationManager.activeNotifications.firstOrNull { it.id == NOTIFICATION_ID }
        val activePromoted = (active?.notification?.flags?.and(Notification.FLAG_PROMOTED_ONGOING) ?: 0) != 0
        return LiveSurfaceDiagnostics(
            readiness = LivePromotionReadinessEvaluator.evaluate(
                apiLevel = Build.VERSION.SDK_INT,
                notificationsAllowed = notificationsAllowed,
                manifestPermissionDeclared = manifestPermissionDeclared,
                promotableCharacteristics = promotableCharacteristics,
                channelImportance = channelImportance,
                canPostPromotedNotifications = canPostPromoted,
            ),
            notificationPermissionAllowed = notificationsAllowed,
            manifestPermissionDeclared = manifestPermissionDeclared,
            promotionRequested = promotionRequested,
            promotableCharacteristics = promotableCharacteristics,
            channelImportance = channelImportance,
            canPostPromotedNotifications = canPostPromoted,
            activeNotificationPresent = active != null,
            activeNotificationPromoted = activePromoted,
            specializedSettingsAvailable = Build.VERSION.SDK_INT >= 36 && canResolve(promotedSettingsIntent()),
        )
    }

    fun buildNotification(
        snapshot: GuidanceSnapshot,
        requestPromotion: Boolean,
        presentation: LiveDisplayOptions = LiveDisplayOptions(),
        deviceLocked: Boolean = isDeviceLocked(),
    ): Notification {
        val classContent = snapshot.classContent
        val classroomFirst = presentation.classOrder == LiveClassOrder.CLASSROOM_FIRST
        val title = if (classroomFirst && classContent?.startTime != null && classContent.room != null) {
            "${classContent.startTime} · ${classContent.room}"
        } else {
            classContent?.title ?: snapshot.shuttleLines.firstOrNull()?.text ?: context.getString(R.string.app_name)
        }
        val classDetail = when {
            classroomFirst && classContent?.remainingText != null && classContent.courseName != null ->
                "${classContent.remainingText} · ${classContent.courseName}"
            else -> classContent?.detail
        }
        val detail = buildList {
            classDetail?.let(::add)
            addAll(
                snapshot.shuttleLines
                    .drop(if (classContent == null) 1 else 0)
                    .map { it.text },
            )
        }.joinToString("\n")
        val contentIntent = PendingIntent.getActivity(
            context,
            6101,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_dima)
            .setColor(0xFFEC268FL.toInt())
            .setContentTitle(title)
            .setContentText(detail.lineSequence().firstOrNull().orEmpty())
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setShowWhen(snapshot.countdownTarget != null)
        snapshot.countdownTarget?.let {
            builder.setWhen(it.toEpochMilli()).setUsesChronometer(true).setChronometerCountDown(true)
        }
        if (Build.VERSION.SDK_INT >= 36 && presentation.chipContent == LiveChipContent.CLASSROOM) {
            val criticalText = LiveSurfaceController.statusChipText(snapshot, presentation, deviceLocked)
            criticalText?.takeIf(String::isNotBlank)?.let(builder::setShortCriticalText)
            snapshot.classContent?.remainingText?.takeIf(String::isNotBlank)?.let(builder::setSubText)
        }
        if (Build.VERSION.SDK_INT >= 36 && requestPromotion) builder.setRequestPromotedOngoing(true)
        return builder.build()
    }

    private fun hasNotificationPermission(): Boolean =
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    private fun isDeviceLocked(): Boolean =
        (context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked

    private fun promotedSettingsIntent() = Intent(ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS).apply {
        data = Uri.parse("package:${context.packageName}")
        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun appNotificationSettingsIntent() = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun appDetailsIntent() = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:${context.packageName}")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun canResolve(intent: Intent): Boolean =
        context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null

    private fun startSafely(intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

    internal fun ensureChannel() {
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "수업·셔틀 실시간 안내", NotificationManager.IMPORTANCE_LOW).apply {
                description = "사용자가 켠 수업 및 셔틀의 조용한 진행 안내"
                setSound(null, null)
                enableVibration(false)
            },
        )
    }

    private fun startUpdater(): Boolean = LiveSurfaceController.startUpdaterSafely {
        ContextCompat.startForegroundService(context, Intent(context, LiveMinuteUpdateService::class.java))
    }

    private fun stopUpdater() {
        context.stopService(Intent(context, LiveMinuteUpdateService::class.java))
    }

    companion object {
        private const val ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS = "android.settings.MANAGE_APP_PROMOTED_NOTIFICATIONS"
        private const val PERMISSION_POST_PROMOTED_NOTIFICATIONS = "android.permission.POST_PROMOTED_NOTIFICATIONS"
        private const val EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"
        const val CHANNEL_ID = "dima_live_guidance"
        const val NOTIFICATION_ID = 6201
    }
}
