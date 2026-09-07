package com.example.dimanow.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.dimanow.DimaNowApplication
import com.example.dimanow.meal.MealRefreshResult
import com.example.dimanow.meal.MealRefreshTrigger
import com.example.dimanow.meal.MealSource
import com.example.dimanow.widget.CampusSummaryWidgetProvider
import com.example.dimanow.widget.MealWidgetProvider
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

/** One durable watch chain, independent of shuttle/notice refresh and ordinary 12h sync. */
object StudentMealSync {
    suspend fun refresh(context: Context, source: MealSource, trigger: MealRefreshTrigger): MealRefreshResult? {
        val result = source.refreshIfDue(trigger)
        updateWidgets(context, result)
        schedule(context, source)
        return result
    }

    suspend fun schedule(context: Context, source: MealSource, afterRunningCheck: Boolean = false) {
        val now = Instant.now()
        val delay = Duration.between(now, source.nextBackgroundCheckAt(now)).toMillis().coerceAtLeast(0)
        val request = OneTimeWorkRequestBuilder<StudentMealWatchWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()
        // KEEP never replaces a pending retry on app launch. Only the running worker appends its successor.
        WorkManager.getInstance(context).enqueueUniqueWork(
            "student-meal-publication-watch",
            if (afterRunningCheck) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.KEEP,
            request,
        )
    }

    internal suspend fun updateWidgets(context: Context, result: MealRefreshResult?) {
        if (result is MealRefreshResult.Success) {
            MealWidgetProvider.updateAll(context)
            CampusSummaryWidgetProvider.updateAll(context)
        }
    }
}

class StudentMealWatchWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val source = (applicationContext as DimaNowApplication).mealSource
        val result = source.refreshIfDue(MealRefreshTrigger.PUBLICATION_WATCH)
        StudentMealSync.updateWidgets(applicationContext, result)
        if (result is MealRefreshResult.Failure) return Result.retry()
        StudentMealSync.schedule(applicationContext, source, afterRunningCheck = true)
        return Result.success()
    }
}
