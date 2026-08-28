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
            preferences.setBackgroundWorkPolicyVersion(CURRENT_POLICY_VERSION)
        }

        val shuttleConstraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val immediate = OneTimeWorkRequestBuilder<ShuttleRefreshWorker>()
            .setConstraints(shuttleConstraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 6, TimeUnit.HOURS)
            .build()
        workManager.enqueueUniqueWork("shuttle-weekly-launch-check", ExistingWorkPolicy.KEEP, immediate)
        val daily = PeriodicWorkRequestBuilder<ShuttleRefreshWorker>(24, TimeUnit.HOURS)
            .setConstraints(shuttleConstraints)
            .setInitialDelay(24, TimeUnit.HOURS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 6, TimeUnit.HOURS)
            .build()
        workManager.enqueueUniquePeriodicWork("shuttle-weekly-policy", ExistingPeriodicWorkPolicy.UPDATE, daily)

        val mealConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
        val mealImmediate = OneTimeWorkRequestBuilder<MealRefreshWorker>()
            .setConstraints(mealConstraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 6, TimeUnit.HOURS)
            .build()
        workManager.enqueueUniqueWork("meal-expiry-launch-check", ExistingWorkPolicy.KEEP, mealImmediate)
        val mealDaily = PeriodicWorkRequestBuilder<MealRefreshWorker>(24, TimeUnit.HOURS)
            .setConstraints(mealConstraints)
            .setInitialDelay(24, TimeUnit.HOURS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 6, TimeUnit.HOURS)
            .build()
        workManager.enqueueUniquePeriodicWork("meal-expiry-policy", ExistingPeriodicWorkPolicy.UPDATE, mealDaily)

        val noticeConstraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val noticeImmediate = OneTimeWorkRequestBuilder<NoticeRefreshWorker>()
            .setConstraints(noticeConstraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 6, TimeUnit.HOURS)
            .build()
        workManager.enqueueUniqueWork("notice-daily-launch-check", ExistingWorkPolicy.KEEP, noticeImmediate)
        val noticeDaily = PeriodicWorkRequestBuilder<NoticeRefreshWorker>(24, TimeUnit.HOURS)
            .setConstraints(noticeConstraints)
            .setInitialDelay(24, TimeUnit.HOURS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 6, TimeUnit.HOURS)
            .build()
        workManager.enqueueUniquePeriodicWork("notice-daily-policy", ExistingPeriodicWorkPolicy.UPDATE, noticeDaily)
    }

    private const val CURRENT_POLICY_VERSION = 1
}

class NoticeRefreshWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val app = applicationContext as DimaNowApplication
        val now = ZonedDateTime.now(MinuteTicker.CAMPUS_ZONE)
        if (!RefreshPolicy.shouldRefreshNotices(app.noticeSource.data.first(), now)) return Result.success()
        return when (app.noticeSource.refresh()) {
            is NoticeRefreshResult.Success -> Result.success()
            is NoticeRefreshResult.Failure -> Result.retry()
        }
    }
}

class MealRefreshWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val app = applicationContext as DimaNowApplication
        val now = ZonedDateTime.now(MinuteTicker.CAMPUS_ZONE)
        if (!RefreshPolicy.shouldRefreshMeal(app.mealSource.data.first(), now)) return Result.success()
        return when (val result = app.mealSource.refresh()) {
            is MealRefreshResult.Success -> {
                MealWidgetProvider.updateAll(applicationContext)
                CampusSummaryWidgetProvider.updateAll(applicationContext)
                if (result.weekStart.plusDays(4).isBefore(now.toLocalDate())) Result.retry() else Result.success()
            }
            is MealRefreshResult.NeedsReview -> Result.retry()
            is MealRefreshResult.Failure -> Result.retry()
        }
    }
}

class ShuttleRefreshWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val app = applicationContext as DimaNowApplication
        val now = ZonedDateTime.now(MinuteTicker.CAMPUS_ZONE)
        if (!RefreshPolicy.shouldRefreshShuttle(app.shuttleSource.data.first(), now)) return Result.success()
        return when (app.shuttleSource.refresh()) {
            is ShuttleRefreshResult.Success -> {
                ShuttleWidgetProvider.updateAll(applicationContext)
                CampusSummaryWidgetProvider.updateAll(applicationContext)
                Result.success()
            }
            is ShuttleRefreshResult.Failure -> Result.retry()
        }
    }
}
