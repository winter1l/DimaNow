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

private fun openFirstAvailableSettings(context: Context, intents: List<Intent>) {
    intents.firstOrNull { it.resolveActivity(context.packageManager) != null }?.let { intent ->
        val launchIntent = Intent(intent).apply {
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(launchIntent)
    }
}
