package com.example.dimanow.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import com.example.dimanow.domain.CampusZoneId
import com.example.dimanow.live.LiveChipContent
import com.example.dimanow.live.LiveClassOrder
import com.example.dimanow.live.LiveDisplayOptions
import com.example.dimanow.guidance.HomeBase
import com.example.dimanow.location.LocationMode

private val Context.settingsDataStore by preferencesDataStore(name = "dima_now_settings")

class AppPreferences(private val context: Context) {
    val automaticClassGuidance: Flow<Boolean> = context.settingsDataStore.data.map { true }

    val lastResolvedZone: Flow<CampusZoneId> = context.settingsDataStore.data.map { preferences ->
        preferences[LAST_RESOLVED_ZONE]?.let { runCatching { CampusZoneId.valueOf(it) }.getOrNull() }
            ?: CampusZoneId.OUTSIDE
    }

    val activeGeofenceIds: Flow<Set<String>> = context.settingsDataStore.data.map { preferences ->
        preferences[ACTIVE_GEOFENCES] ?: emptySet()
    }

    val locationMode: Flow<LocationMode> = context.settingsDataStore.data.map { preferences ->
        preferences[LOCATION_MODE]
            ?.let { runCatching { LocationMode.valueOf(it) }.getOrNull() }
            ?: LocationMode.GPS
    }

    val testZone: Flow<CampusZoneId> = context.settingsDataStore.data.map { preferences ->
        preferences[TEST_ZONE]
            ?.let { runCatching { CampusZoneId.valueOf(it) }.getOrNull() }
            ?: CampusZoneId.OUTSIDE
    }

    val effectiveZone: Flow<CampusZoneId> = combine(lastResolvedZone, locationMode, testZone) { actual, mode, test ->
        if (mode == LocationMode.TEST) test else actual
    }

    val liveDisplayOptions: Flow<LiveDisplayOptions> = context.settingsDataStore.data.map { preferences ->
        LiveDisplayOptions(
            chipContent = preferences[LIVE_CHIP_CONTENT]
                ?.let { runCatching { LiveChipContent.valueOf(it) }.getOrNull() }
                ?: LiveChipContent.COUNTDOWN,
            classOrder = preferences[LIVE_CLASS_ORDER]
                ?.let { runCatching { LiveClassOrder.valueOf(it) }.getOrNull() }
                ?: LiveClassOrder.COURSE_FIRST,
        )
    }

    val campusZoneDefaultsVersion: Flow<Int> = context.settingsDataStore.data.map { preferences ->
        preferences[CAMPUS_ZONE_DEFAULTS_VERSION] ?: 1
    }

    val homeBase: Flow<HomeBase> = context.settingsDataStore.data.map { preferences ->
        preferences[HOME_BASE]
            ?.let { runCatching { HomeBase.valueOf(it) }.getOrNull() }
            ?: HomeBase.YEIN
    }

    val homeBaseSelectionConfirmed: Flow<Boolean> = context.settingsDataStore.data.map { preferences ->
        preferences[HOME_BASE_SELECTION_CONFIRMED] ?: false
    }

    val nowBarSetupCompleted: Flow<Boolean> = context.settingsDataStore.data.map { preferences ->
        preferences[NOW_BAR_SETUP_COMPLETED] ?: false
    }

    val backgroundWorkPolicyVersion: Flow<Int> = context.settingsDataStore.data.map { preferences ->
        preferences[BACKGROUND_WORK_POLICY_VERSION] ?: 0
    }

    @Deprecated("Automatic class guidance is always enabled")
    suspend fun setAutomaticClassGuidance(@Suppress("UNUSED_PARAMETER") enabled: Boolean) {
        context.settingsDataStore.edit { it[AUTOMATIC_CLASS_GUIDANCE] = true }
    }


    suspend fun setLocationState(zone: CampusZoneId, activeGeofences: Set<String>) {
        context.settingsDataStore.edit {
            it[LAST_RESOLVED_ZONE] = zone.name
            it[ACTIVE_GEOFENCES] = activeGeofences
        }
    }

    suspend fun setTestLocationMode(enabled: Boolean, zone: CampusZoneId) {
        context.settingsDataStore.edit {
            it[LOCATION_MODE] = if (enabled) LocationMode.TEST.name else LocationMode.GPS.name
            it[TEST_ZONE] = zone.name
        }
    }

    suspend fun setTestZone(zone: CampusZoneId) {
        context.settingsDataStore.edit { it[TEST_ZONE] = zone.name }
    }

    suspend fun setLiveChipContent(content: LiveChipContent) {
        context.settingsDataStore.edit { it[LIVE_CHIP_CONTENT] = content.name }
    }

    suspend fun setLiveClassOrder(order: LiveClassOrder) {
        context.settingsDataStore.edit { it[LIVE_CLASS_ORDER] = order.name }
    }

    suspend fun setCampusZoneDefaultsVersion(version: Int) {
        context.settingsDataStore.edit { it[CAMPUS_ZONE_DEFAULTS_VERSION] = version }
    }

    suspend fun setHomeBase(homeBase: HomeBase) {
        context.settingsDataStore.edit {
            it[HOME_BASE] = homeBase.name
            it[HOME_BASE_SELECTION_CONFIRMED] = true
        }
    }

    suspend fun setNowBarSetupCompleted(completed: Boolean) {
        context.settingsDataStore.edit { it[NOW_BAR_SETUP_COMPLETED] = completed }
    }

    suspend fun setBackgroundWorkPolicyVersion(version: Int) {
        context.settingsDataStore.edit { it[BACKGROUND_WORK_POLICY_VERSION] = version }
    }

    private companion object {
        val AUTOMATIC_CLASS_GUIDANCE = booleanPreferencesKey("automatic_class_guidance")
        val LAST_RESOLVED_ZONE = stringPreferencesKey("last_resolved_zone")
        val ACTIVE_GEOFENCES = stringSetPreferencesKey("active_geofences")
        val LOCATION_MODE = stringPreferencesKey("location_mode")
        val TEST_ZONE = stringPreferencesKey("test_zone")
        val LIVE_CHIP_CONTENT = stringPreferencesKey("live_chip_content")
        val LIVE_CLASS_ORDER = stringPreferencesKey("live_class_order")
        val CAMPUS_ZONE_DEFAULTS_VERSION = intPreferencesKey("campus_zone_defaults_version")
        val HOME_BASE = stringPreferencesKey("home_base")
        val HOME_BASE_SELECTION_CONFIRMED = booleanPreferencesKey("home_base_selection_confirmed")
        val NOW_BAR_SETUP_COMPLETED = booleanPreferencesKey("now_bar_setup_completed")
        val BACKGROUND_WORK_POLICY_VERSION = intPreferencesKey("background_work_policy_version")
    }
}
