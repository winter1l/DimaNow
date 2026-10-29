package com.example.dimanow.lms

import android.content.Context
import java.time.Instant

/** [LmsSessionRecord] in the app's private preferences: three times, no account data (D-135). */
class AndroidLmsSessionRecord(context: Context) : LmsSessionRecord {
    private val preferences = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override fun lastActivity(): Instant? = read(KEY_LAST_ACTIVITY)
    override fun recordActivity(at: Instant) = write(KEY_LAST_ACTIVITY, at)
    override fun lastPortalSignIn(): Instant? = read(KEY_PORTAL_SIGN_IN)
    override fun recordPortalSignIn(at: Instant) = write(KEY_PORTAL_SIGN_IN, at)
    override fun waitingSince(): Instant? = read(KEY_WAITING_SINCE)
    override fun setWaitingSince(at: Instant?) = write(KEY_WAITING_SINCE, at)

    private fun read(key: String): Instant? = preferences.getLong(key, 0L).takeIf { it > 0L }?.let(Instant::ofEpochMilli)

    private fun write(key: String, at: Instant?) {
        preferences.edit().apply { if (at == null) remove(key) else putLong(key, at.toEpochMilli()) }.apply()
    }

    private companion object {
        const val FILE_NAME = "lms_session"
        const val KEY_LAST_ACTIVITY = "last_activity_millis"
        const val KEY_PORTAL_SIGN_IN = "portal_sign_in_millis"
        const val KEY_WAITING_SINCE = "waiting_since_millis"
    }
}
