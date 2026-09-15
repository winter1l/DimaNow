package com.example.dimanow.live

import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import androidx.datastore.preferences.core.edit
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.dimanow.DimaNowApplication
import com.example.dimanow.domain.GuidanceSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDate
import com.example.dimanow.time.MinuteTicker

private val Context.guidanceDismissals by preferencesDataStore(name = "guidance_dismissals")

/** Local, durable opt-out for a dated occurrence, independent of notification preferences. */
class GuidanceDismissalStore internal constructor(private val dataStore: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.guidanceDismissals)

    private val keys = stringSetPreferencesKey("occurrences")
    val dismissedKeys = dataStore.data.map { it[keys].orEmpty() }

    suspend fun dismiss(snapshot: GuidanceSnapshot): Boolean =
        snapshot.occurrenceKey?.let { dismissKey(it) } ?: false

    internal suspend fun dismissKey(key: String): Boolean {
        if (key.length > 1024) return false
        val date = runCatching { LocalDate.parse(key.substringBefore('|')) }.getOrNull() ?: return false
        val today = LocalDate.now(MinuteTicker.CAMPUS_ZONE)
        if (date != today) return false
        dataStore.edit { preferences ->
            preferences[keys] = preferences[keys].orEmpty()
                .filter { it.substringBefore('|') >= today.minusDays(7).toString() }.toSet() + key
        }
        return true
    }
}

class DismissGuidanceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra(EXTRA_OCCURRENCE) ?: return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as DimaNowApplication
                app.guidanceDismissalStore.dismissKey(key)
                app.guidanceRuntimeCoordinator.requestRefresh()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_OCCURRENCE = "guidance_occurrence"
    }
}
