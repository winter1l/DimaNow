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
            workManager.cancelUniqueWork("shuttle-weekly-launch-check")
            workManager.cancelUniqueWork("shuttle-weekly-policy")
            workManager.cancelUniqueWork("meal-expiry-launch-check")
            workManager.cancelUniqueWork("meal-expiry-policy")
            workManager.cancelUniqueWork("notice-daily-launch-check")
            workManager.cancelUniqueWork("notice-daily-policy")
            preferences.setBackgroundWorkPolicyVersion(CURRENT_POLICY_VERSION)
        }

        val syncConstraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val immediate = OneTimeWorkRequestBuilder<CampusSyncWorker>()
            .setConstraints(syncConstraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniqueWork("campus-static-sync-launch", ExistingWorkPolicy.KEEP, immediate)
        val periodic = PeriodicWorkRequestBuilder<CampusSyncWorker>(12, TimeUnit.HOURS)
            .setConstraints(syncConstraints)
            .setInitialDelay(12, TimeUnit.HOURS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniquePeriodicWork("campus-static-sync-periodic", ExistingPeriodicWorkPolicy.UPDATE, periodic)
    }

    private const val CURRENT_POLICY_VERSION = 2
}

class CampusSyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val app = applicationContext as DimaNowApplication
        val shuttle = app.shuttleSource.refresh()
        val meal = app.mealSource.refresh()
        val notice = app.noticeSource.refresh()
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
