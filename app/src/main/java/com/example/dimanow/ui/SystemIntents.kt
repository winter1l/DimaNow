package com.example.dimanow.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.material.icons.filled.Settings

internal fun openUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    try {
        context.startActivity(intent)
    } catch (_: android.content.ActivityNotFoundException) {
        android.widget.Toast.makeText(context, "링크를 열 앱이 없어요", android.widget.Toast.LENGTH_SHORT).show()
    }
}

/**
 * Opens another installed app (D-126(6)), or its Play Store page when it is missing. The package must be listed in
 * the manifest's `<queries>`, or Android 11+ reports it as not installed.
 */
internal fun openApp(context: Context, packageName: String) {
    context.packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
        try {
            context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: android.content.ActivityNotFoundException) {
        }
    }
    val store = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(store)
    } catch (_: android.content.ActivityNotFoundException) {
        openUrl(context, "https://play.google.com/store/apps/details?id=$packageName")
    }
}

/**
 * D-132: 예인관 meals are paid with PAYCO 식권. `payco://open/mealticket` opens PAYCO's 기업복지 home, the screen of the
 * 식권 shortcut the student keeps on the home screen, where the 기숙사 식권 card's `사용하기` shows the 식권 barcode
 * (observed 2026-10-26 on the student's phone). D-133: that barcode screen is not exported and no PAYCO link reaches
 * it, and `payco://open/offline/main` is PAYCO's ordinary payment barcode, not the 식권, so the home is as close as
 * an app can go. Without PAYCO the Play Store page opens.
 */
internal fun openPaycoMealTicket(context: Context) {
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(PAYCO_MEAL_TICKET_URI)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (_: android.content.ActivityNotFoundException) {
        openApp(context, PAYCO_APP_PACKAGE)
    }
}

internal const val PAYCO_APP_PACKAGE = "com.nhnent.payapp"
internal const val PAYCO_MEAL_TICKET_URI = "payco://open/mealticket"

/** The library app (학술정보원) whose 모바일 열람증 opens the library gate; its card screen cannot be opened directly. */
internal const val LIBRARY_APP_PACKAGE = "kr.ac.dima.lib"

/** 헤이영 캠퍼스, where 전자출결 works. */
internal const val HEYOUNG_APP_PACKAGE = "com.shinhan.heyoung"

/**
 * 개발자 옵션. With [highlightKey] the page scrolls to that row and highlights it, through the extra Android's own
 * Settings search uses (`:settings:fragment_args_key`). D-140: Samsung keeps `모든 앱의 실시간 정보 보기` on the
 * `추가 설정` sub-page (`sec_developer_options`), whose activity is not exported (SecSettings manifest read on
 * 2026-10-29), so the closest an app can go is 개발자 옵션 with that row highlighted.
 */
internal fun openDeveloperOptions(context: Context, highlightKey: String? = null) {
    openFirstAvailableSettings(
        context,
        listOf(
            Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
                if (highlightKey != null) {
                    putExtra(SETTINGS_FRAGMENT_ARG_KEY, highlightKey)
                    putExtra(SETTINGS_SHOW_FRAGMENT_ARGS, android.os.Bundle().apply { putString(SETTINGS_FRAGMENT_ARG_KEY, highlightKey) })
                }
            },
            Intent(Settings.ACTION_SETTINGS),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
        ),
    )
}

private const val SETTINGS_FRAGMENT_ARG_KEY = ":settings:fragment_args_key"
private const val SETTINGS_SHOW_FRAGMENT_ARGS = ":settings:show_fragment_args"

/** Samsung's 개발자 옵션 → 추가 설정 row, which holds 모든 앱의 실시간 정보 보기. */
internal const val SAMSUNG_MORE_DEVELOPER_SETTINGS_KEY = "sec_developer_options"

/** This app's page in the system settings, where refused permissions can be turned back on. */
internal fun openAppDetailsSettings(context: Context) {
    openFirstAvailableSettings(
        context,
        listOf(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
            Intent(Settings.ACTION_SETTINGS),
        ),
    )
}

private fun openFirstAvailableSettings(context: Context, intents: List<Intent>) {
    intents.firstOrNull { it.resolveActivity(context.packageManager) != null }?.let { intent ->
        val launchIntent = Intent(intent).apply {
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(launchIntent)
    }
}
