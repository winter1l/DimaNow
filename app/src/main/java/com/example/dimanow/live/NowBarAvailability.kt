package com.example.dimanow.live

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.provider.Settings

/** Whether this phone can show DIMA Now in the Now Bar (D-140, D-143). */
enum class NowBarSupport {
    AVAILABLE,

    /** Android before 16 has no Live Updates at all. */
    UNSUPPORTED,
}

/**
 * D-143 (supersedes D-140's dimming on Samsung): every promoted guidance notification carries Samsung's undocumented
 * extras `android.ongoingActivityNoti.automation` / `.automationPackage`, with which One UI draws the Now Bar card and
 * the status chip although the developer option `모든 앱의 실시간 정보 보기` (`Settings.Secure`
 * `enable_notification_nowbar_test`) is off — observed on 2026-10-29 on the SM-S918N (One UI 9) and the SM-X710
 * (One UI 8.5). They are not a published Samsung interface and may stop working, and an app cannot tell whether the
 * card was drawn, so 설정 keeps the developer-option steps as a "나우바가 안 보이면" fallback. The Now Bar therefore
 * counts as available on every phone with Android 16 or later; before that a 나우바 choice goes out as 일반 알림.
 */
object NowBarAvailability {
    internal const val SAMSUNG_OPTION_KEY = "enable_notification_nowbar_test"
    internal const val SAMSUNG_AUTOMATION_EXTRA = "android.ongoingActivityNoti.automation"
    internal const val SAMSUNG_AUTOMATION_PACKAGE_EXTRA = "android.ongoingActivityNoti.automationPackage"

    fun evaluate(apiLevel: Int): NowBarSupport =
        if (apiLevel < 36) NowBarSupport.UNSUPPORTED else NowBarSupport.AVAILABLE

    /** A 나우바 choice the phone cannot show is delivered as 일반 알림; the stored choice is kept for later. */
    fun effectiveMode(mode: NotificationGuidanceMode, support: NowBarSupport): NotificationGuidanceMode =
        if (mode == NotificationGuidanceMode.LIVE_UPDATE && support != NowBarSupport.AVAILABLE) NotificationGuidanceMode.STANDARD
        else mode

    fun read(@Suppress("UNUSED_PARAMETER") context: Context): NowBarSupport = evaluate(Build.VERSION.SDK_INT)

    /** The extras that let One UI show another app's Live Update without the developer option (see above). */
    fun samsungNowBarExtras(packageName: String): Bundle = Bundle().apply {
        putBoolean(SAMSUNG_AUTOMATION_EXTRA, true)
        putString(SAMSUNG_AUTOMATION_PACKAGE_EXTRA, packageName)
    }

    fun isSamsung(): Boolean = Build.MANUFACTURER.equals("samsung", ignoreCase = true)

    /** Whether Android's 개발자 옵션 itself is on, so the fallback guide can show its first step as done. */
    fun developerOptionsOn(context: Context): Boolean =
        Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1

    /** Samsung's `모든 앱의 실시간 정보 보기`: true when on, false when off or never set, null when the phone refuses the read. */
    fun samsungOptionOn(context: Context): Boolean? = try {
        Settings.Secure.getString(context.contentResolver, SAMSUNG_OPTION_KEY)?.trim() == "1"
    } catch (_: SecurityException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}
