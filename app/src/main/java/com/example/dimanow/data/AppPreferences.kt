package com.example.dimanow.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
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
import com.example.dimanow.live.GuidanceKind
import com.example.dimanow.live.NotificationGuidanceMode
import com.example.dimanow.live.NotificationGuidancePolicy
import com.example.dimanow.guidance.HomeBase
import com.example.dimanow.location.LocationMode
import com.example.dimanow.location.TransitStopProximityState
import com.example.dimanow.update.AppUpdateRelease
import java.time.Instant
import java.util.UUID

data class AppUpdatePreferences(
    val lastCheckedEpochMillis: Long? = null,
    val cachedRelease: AppUpdateRelease? = null,
    val dismissedVersion: String? = null,
    val preparedPath: String? = null,
    val preparedVersion: String? = null,
)

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

    val activeTransitStopNumber: Flow<String?> = context.settingsDataStore.data.map { it[ACTIVE_TRANSIT_STOP_NUMBER] }

    val transitStopProximityState: Flow<TransitStopProximityState> = context.settingsDataStore.data.map { preferences ->
        TransitStopProximityState(
            candidateStopNumber = preferences[TRANSIT_CANDIDATE_STOP_NUMBER],
            candidateSince = preferences[TRANSIT_CANDIDATE_SINCE]?.let(Instant::ofEpochMilli),
            activeStopNumber = preferences[ACTIVE_TRANSIT_STOP_NUMBER],
            lastValidAt = preferences[TRANSIT_LAST_VALID_AT]?.let(Instant::ofEpochMilli),
        )
    }

    val testTransitStopNumber: Flow<String?> = context.settingsDataStore.data.map { it[TEST_TRANSIT_STOP_NUMBER] }

    val effectiveZone: Flow<CampusZoneId> = combine(lastResolvedZone, locationMode, testZone) { actual, mode, test ->
        if (mode == LocationMode.TEST) test else actual
    }

    val effectiveTransitStopNumber: Flow<String?> = combine(
        activeTransitStopNumber,
        locationMode,
        testTransitStopNumber,
    ) { actual, mode, test -> if (mode == LocationMode.TEST) test else actual }

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

    val notificationGuidancePolicy: Flow<NotificationGuidancePolicy> = context.settingsDataStore.data.map { preferences ->
        NotificationGuidancePolicy(
            classGuidance = preferences[NOTIFICATION_CLASS_MODE].toNotificationMode(),
            campusShuttle = preferences[NOTIFICATION_CAMPUS_SHUTTLE_MODE].toNotificationMode(),
            bus4402 = preferences[NOTIFICATION_BUS_4402_MODE].toNotificationMode(),
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

    /** 최초 실행 권한 온보딩을 끝냈는지. 끝내기 전에는 앱 셸 대신 온보딩이 표시된다 (D-056). */
    val onboardingCompleted: Flow<Boolean> = context.settingsDataStore.data.map { preferences ->
        preferences[ONBOARDING_COMPLETED] ?: false
    }

    val backgroundWorkPolicyVersion: Flow<Int> = context.settingsDataStore.data.map { preferences ->
        preferences[BACKGROUND_WORK_POLICY_VERSION] ?: 0
    }

    val appUpdatePreferences: Flow<AppUpdatePreferences> = context.settingsDataStore.data.map { preferences ->
        val version = preferences[UPDATE_LATEST_VERSION]
        val release = if (
            version != null && preferences[UPDATE_RELEASE_PAGE] != null && preferences[UPDATE_DOWNLOAD_URL] != null &&
            preferences[UPDATE_SIZE] != null && preferences[UPDATE_SHA256] != null
        ) {
            AppUpdateRelease(
                versionName = version,
                releasePageUrl = preferences[UPDATE_RELEASE_PAGE]!!,
                downloadUrl = preferences[UPDATE_DOWNLOAD_URL]!!,
                sizeBytes = preferences[UPDATE_SIZE]!!,
                sha256 = preferences[UPDATE_SHA256]!!,
            )
        } else null
        AppUpdatePreferences(
            lastCheckedEpochMillis = preferences[UPDATE_LAST_CHECKED],
            cachedRelease = release,
            dismissedVersion = preferences[UPDATE_DISMISSED_VERSION],
            preparedPath = preferences[UPDATE_PREPARED_PATH],
            preparedVersion = preferences[UPDATE_PREPARED_VERSION],
        )
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

    suspend fun setTestTransitStop(stopNumber: String?) {
        context.settingsDataStore.edit {
            if (stopNumber == null) it.remove(TEST_TRANSIT_STOP_NUMBER)
            else it[TEST_TRANSIT_STOP_NUMBER] = stopNumber
        }
    }

    suspend fun setTransitStopProximityState(state: TransitStopProximityState) {
        context.settingsDataStore.edit {
            it.putOrRemove(TRANSIT_CANDIDATE_STOP_NUMBER, state.candidateStopNumber)
            it.putOrRemove(TRANSIT_CANDIDATE_SINCE, state.candidateSince?.toEpochMilli())
            it.putOrRemove(ACTIVE_TRANSIT_STOP_NUMBER, state.activeStopNumber)
            it.putOrRemove(TRANSIT_LAST_VALID_AT, state.lastValidAt?.toEpochMilli())
        }
    }

    suspend fun setLiveChipContent(content: LiveChipContent) {
        context.settingsDataStore.edit { it[LIVE_CHIP_CONTENT] = content.name }
    }

    suspend fun setLiveClassOrder(order: LiveClassOrder) {
        context.settingsDataStore.edit { it[LIVE_CLASS_ORDER] = order.name }
    }

    suspend fun setNotificationGuidanceMode(kind: GuidanceKind, mode: NotificationGuidanceMode) {
        val key = when (kind) {
            GuidanceKind.CLASS -> NOTIFICATION_CLASS_MODE
            GuidanceKind.CAMPUS_SHUTTLE -> NOTIFICATION_CAMPUS_SHUTTLE_MODE
            GuidanceKind.BUS_4402 -> NOTIFICATION_BUS_4402_MODE
        }
        context.settingsDataStore.edit { it[key] = mode.name }
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

    /** 온보딩 완료는 귀가 기준지·나우바 안내 플래그까지 함께 확정한다. */
    suspend fun setOnboardingCompleted() {
        context.settingsDataStore.edit {
            it[ONBOARDING_COMPLETED] = true
            it[NOW_BAR_SETUP_COMPLETED] = true
        }
    }

    suspend fun setBackgroundWorkPolicyVersion(version: Int) {
        context.settingsDataStore.edit { it[BACKGROUND_WORK_POLICY_VERSION] = version }
    }

    suspend fun getOrCreateShuttleReporterToken(): String {
        var result = ""
        context.settingsDataStore.edit { preferences ->
            result = preferences[SHUTTLE_REPORTER_TOKEN] ?: "install_${UUID.randomUUID().toString().replace('-', '_')}".also {
                preferences[SHUTTLE_REPORTER_TOKEN] = it
            }
        }
        return result
    }

    suspend fun recordAppUpdateCheck(atEpochMillis: Long, release: AppUpdateRelease) {
        context.settingsDataStore.edit {
            it[UPDATE_LAST_CHECKED] = atEpochMillis
            it[UPDATE_LATEST_VERSION] = release.versionName
            it[UPDATE_RELEASE_PAGE] = release.releasePageUrl
            it[UPDATE_DOWNLOAD_URL] = release.downloadUrl
            it[UPDATE_SIZE] = release.sizeBytes
            it[UPDATE_SHA256] = release.sha256
        }
    }

    suspend fun dismissAppUpdateVersion(version: String) {
        context.settingsDataStore.edit { it[UPDATE_DISMISSED_VERSION] = version }
    }

    suspend fun recordPreparedAppUpdate(path: String, version: String) {
        context.settingsDataStore.edit {
            it[UPDATE_PREPARED_PATH] = path
            it[UPDATE_PREPARED_VERSION] = version
        }
    }

    suspend fun clearPreparedAppUpdate() {
        context.settingsDataStore.edit {
            it.remove(UPDATE_PREPARED_PATH)
            it.remove(UPDATE_PREPARED_VERSION)
        }
    }

    private companion object {
        val AUTOMATIC_CLASS_GUIDANCE = booleanPreferencesKey("automatic_class_guidance")
        val LAST_RESOLVED_ZONE = stringPreferencesKey("last_resolved_zone")
        val ACTIVE_GEOFENCES = stringSetPreferencesKey("active_geofences")
        val LOCATION_MODE = stringPreferencesKey("location_mode")
        val TEST_ZONE = stringPreferencesKey("test_zone")
        val ACTIVE_TRANSIT_STOP_NUMBER = stringPreferencesKey("active_transit_stop_number")
        val TEST_TRANSIT_STOP_NUMBER = stringPreferencesKey("test_transit_stop_number")
        val TRANSIT_CANDIDATE_STOP_NUMBER = stringPreferencesKey("transit_candidate_stop_number")
        val TRANSIT_CANDIDATE_SINCE = longPreferencesKey("transit_candidate_since")
        val TRANSIT_LAST_VALID_AT = longPreferencesKey("transit_last_valid_at")
        val LIVE_CHIP_CONTENT = stringPreferencesKey("live_chip_content")
        val LIVE_CLASS_ORDER = stringPreferencesKey("live_class_order")
        val NOTIFICATION_CLASS_MODE = stringPreferencesKey("notification_class_mode")
        val NOTIFICATION_CAMPUS_SHUTTLE_MODE = stringPreferencesKey("notification_campus_shuttle_mode")
        val NOTIFICATION_BUS_4402_MODE = stringPreferencesKey("notification_bus_4402_mode")
        val CAMPUS_ZONE_DEFAULTS_VERSION = intPreferencesKey("campus_zone_defaults_version")
        val HOME_BASE = stringPreferencesKey("home_base")
        val HOME_BASE_SELECTION_CONFIRMED = booleanPreferencesKey("home_base_selection_confirmed")
        val NOW_BAR_SETUP_COMPLETED = booleanPreferencesKey("now_bar_setup_completed")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val BACKGROUND_WORK_POLICY_VERSION = intPreferencesKey("background_work_policy_version")
        val UPDATE_LAST_CHECKED = longPreferencesKey("update_last_checked")
        val UPDATE_LATEST_VERSION = stringPreferencesKey("update_latest_version")
        val UPDATE_RELEASE_PAGE = stringPreferencesKey("update_release_page")
        val UPDATE_DOWNLOAD_URL = stringPreferencesKey("update_download_url")
        val UPDATE_SIZE = longPreferencesKey("update_size")
        val UPDATE_SHA256 = stringPreferencesKey("update_sha256")
        val UPDATE_DISMISSED_VERSION = stringPreferencesKey("update_dismissed_version")
        val UPDATE_PREPARED_PATH = stringPreferencesKey("update_prepared_path")
        val UPDATE_PREPARED_VERSION = stringPreferencesKey("update_prepared_version")
        val SHUTTLE_REPORTER_TOKEN = stringPreferencesKey("shuttle_reporter_token")
    }
}

private fun String?.toNotificationMode(): NotificationGuidanceMode =
    this?.let { runCatching { NotificationGuidanceMode.valueOf(it) }.getOrNull() }
        ?: NotificationGuidanceMode.LIVE_UPDATE

private fun <T> androidx.datastore.preferences.core.MutablePreferences.putOrRemove(
    key: androidx.datastore.preferences.core.Preferences.Key<T>,
    value: T?,
) {
    if (value == null) remove(key) else this[key] = value
}
