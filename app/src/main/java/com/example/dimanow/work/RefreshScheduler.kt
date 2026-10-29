package com.example.dimanow.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.dimanow.DimaNowApplication
import com.example.dimanow.data.AppPreferences
import com.example.dimanow.shuttle.ShuttleRefreshResult
import com.example.dimanow.widget.ShuttleWidgetProvider
import com.example.dimanow.meal.MealRefreshResult
import com.example.dimanow.notice.NoticeRefreshResult
import com.example.dimanow.widget.MealWidgetProvider
import com.example.dimanow.widget.CampusSummaryWidgetProvider
import com.example.dimanow.time.MinuteTicker
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

object RefreshScheduler {
    suspend fun schedule(context: Context, preferences: AppPreferences) {
        val workManager = WorkManager.getInstance(context)
        if (preferences.backgroundWorkPolicyVersion.first() < CURRENT_POLICY_VERSION) {
            workManager.cancelUniqueWork("shuttle-launch-refresh")
            workManager.cancelUniqueWork("shuttle-daily-refresh")
            workManager.cancelUniqueWork("meal-launch-refresh")
            workManager.cancelUniqueWork("meal-twice-daily-refresh")
            workManager.cancelUniqueWork("shuttle-weekly-launch-check")
            workManager.cancelUniqueWork("shuttle-weekly-policy")
            workManager.cancelUniqueWork("meal-expiry-launch-check")
            workManager.cancelUniqueWork("meal-expiry-policy")
            workManager.cancelUniqueWork("notice-daily-launch-check")
            workManager.cancelUniqueWork("notice-daily-policy")
            // Q-032 (c): the drifting 24-hour periodic reminder, replaced by an 08:30 one-time chain.
            workManager.cancelUniqueWork("academic-reminder-daily")
            preferences.setBackgroundWorkPolicyVersion(CURRENT_POLICY_VERSION)
        }

        val syncConstraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        // D-131 (Q-032 (g)): the process also starts for widgets, alarms and the geofence, not only when the student
        // opens the app; a sync that every source finished moments ago is not repeated for each of those starts.
        val app = context.applicationContext as DimaNowApplication
        val lastSuccesses = listOf(
            app.shuttleSource.data.first().lastSuccess,
            app.mealSource.data.first().lastSuccess,
            app.noticeSource.data.first().lastSuccess,
        )
        if (launchSyncDue(Instant.now(), lastSuccesses)) {
            val immediate = OneTimeWorkRequestBuilder<CampusSyncWorker>()
                .setConstraints(syncConstraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()
            workManager.enqueueUniqueWork("campus-static-sync-launch", ExistingWorkPolicy.KEEP, immediate)
        }
        val periodic = PeriodicWorkRequestBuilder<CampusSyncWorker>(12, TimeUnit.HOURS)
            .setConstraints(syncConstraints)
            .setInitialDelay(12, TimeUnit.HOURS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniquePeriodicWork("campus-static-sync-periodic", ExistingPeriodicWorkPolicy.UPDATE, periodic)
        StudentMealSync.schedule(context, app.mealSource)
        AcademicReminderScheduler.schedule(context)
        DeadlineDigestScheduler.schedule(context)
    }

    private const val CURRENT_POLICY_VERSION = 3
}

/**
 * How recent a sync must be for a process start to skip the launch sync (D-131, Q-032 (g)): 30 minutes, the shortest
 * interval at which the data pipeline publishes (`publish-data.yml`: every 30 minutes on Monday mornings while the
 * week's meals are awaited, every two hours otherwise). A sync younger than that can have missed at most one
 * publication, which the next process start after it or the 12-hour sync picks up. It also matches the sync's
 * 30-minute retry backoff.
 */
internal val LAUNCH_SYNC_FRESH_FOR: Duration = Duration.ofMinutes(30)

/**
 * Pure choice of the launch sync (D-131): it runs unless every source it refreshes (shuttle, meal, notice) last
 * succeeded within [freshFor]. A source that never succeeded (the first sync) or whose success lies in the future
 * (the clock was changed) always lets it run, so the first sync is never skipped.
 */
internal fun launchSyncDue(now: Instant, lastSuccesses: List<Instant?>, freshFor: Duration = LAUNCH_SYNC_FRESH_FOR): Boolean =
    lastSuccesses.isEmpty() || lastSuccesses.any { last ->
        last == null || last.isAfter(now) || Duration.between(last, now) >= freshFor
    }

class CampusSyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val app = applicationContext as DimaNowApplication
        val shuttle = app.shuttleSource.refresh()
        val meal = app.mealSource.refreshIfDue(com.example.dimanow.meal.MealRefreshTrigger.FOREGROUND)
        if (RefreshPolicy.shouldRefreshDormitory(app.mealSource.dormitoryData.first(), ZonedDateTime.now(MinuteTicker.CAMPUS_ZONE))) {
            app.mealSource.refreshDormitory()
        }
        val notice = app.noticeSource.refresh()
        if (notice is NoticeRefreshResult.Success) {
            com.example.dimanow.notice.NoticeKeywordAlerts.afterSync(applicationContext, app.preferences, app.noticeSource.data.first().notices)
        }
        if (shuttle is ShuttleRefreshResult.Success) ShuttleWidgetProvider.updateAll(applicationContext)
        if (meal is MealRefreshResult.Success) MealWidgetProvider.updateAll(applicationContext)
        if (shuttle is ShuttleRefreshResult.Success || meal is MealRefreshResult.Success) {
            CampusSummaryWidgetProvider.updateAll(applicationContext)
        }
        return if (
            shuttle is ShuttleRefreshResult.Failure &&
            meal is MealRefreshResult.Failure &&
            notice is NoticeRefreshResult.Failure
        ) Result.retry() else Result.success()
    }
}

// StudentMealWatchWorker supplements the shared 12-hour sync only while a new meal week is missing.
