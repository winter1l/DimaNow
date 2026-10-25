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

/** The library app (학술정보원) whose 모바일 열람증 opens the library gate; its card screen cannot be opened directly. */
internal const val LIBRARY_APP_PACKAGE = "kr.ac.dima.lib"

/** 헤이영 캠퍼스, where 전자출결 works. */
internal const val HEYOUNG_APP_PACKAGE = "com.shinhan.heyoung"

internal fun openDeveloperOptions(context: Context) {
    openFirstAvailableSettings(
        context,
        listOf(
            Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
            Intent(Settings.ACTION_SETTINGS),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
        ),
    )
}

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
