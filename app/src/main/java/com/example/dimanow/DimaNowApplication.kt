package com.example.dimanow

import android.app.Application
import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.room.Room
import androidx.core.content.ContextCompat
import com.example.dimanow.data.AppPreferences
import com.example.dimanow.data.DimaDatabase
import com.example.dimanow.data.RoomCampusDataRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import com.example.dimanow.location.CampusGeofenceManager
import com.example.dimanow.shuttle.ShuttleSource
import com.example.dimanow.shuttle.StaticShuttleSource
import com.example.dimanow.shuttle.HttpShuttleReportSource
import com.example.dimanow.shuttle.ShuttleReportSource
import com.example.dimanow.work.RefreshScheduler
import com.example.dimanow.live.GuidanceOrchestrator
import com.example.dimanow.live.GuidanceRuntimeCoordinator
import com.example.dimanow.live.GuidanceRuntimeSnapshot
import com.example.dimanow.guidance.GuidanceEngine
import com.example.dimanow.guidance.ShuttleScheduleIndex
import com.example.dimanow.domain.ShuttleDeparture
import com.example.dimanow.live.AndroidLiveSurfaceController
import com.example.dimanow.live.GuidanceAlarmScheduler
import com.example.dimanow.widget.SharedWidgetMinuteCoordinator
import com.example.dimanow.widget.RuntimeWidgetRefreshPlanner
import com.example.dimanow.widget.ShuttleWidgetProvider
import com.example.dimanow.widget.CampusSummaryWidgetProvider
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.delay
import com.example.dimanow.meal.MealSource
import com.example.dimanow.meal.StaticMealSource
import com.example.dimanow.meal.DormitoryMealSubmissionService
import com.example.dimanow.meal.AnonymousDormitoryMealApi
import com.example.dimanow.notice.NoticeSource
import com.example.dimanow.notice.StaticNoticeSource
import com.example.dimanow.sync.UrlConnectionStaticDataTransport
import com.example.dimanow.sync.CachingStaticDataTransport
import com.example.dimanow.location.LocationMode
import com.example.dimanow.location.LocationResolver
import com.example.dimanow.location.LocationSample
import com.example.dimanow.domain.CampusZoneId
import com.example.dimanow.domain.GeoPoint
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import com.example.dimanow.update.AndroidAppUpdateInstaller
import com.example.dimanow.update.AppUpdateCoordinator
import com.example.dimanow.update.GitHubAppUpdateSource
import com.example.dimanow.lms.AndroidLmsCredentialStore
import com.example.dimanow.lms.LmsAutoLoginCoordinator
import com.example.dimanow.lms.LmsCacheDatabase
import com.example.dimanow.lms.LmsLoginBridge
import com.example.dimanow.lms.MutableLmsSessionController
import com.example.dimanow.lms.RoomLmsSource
import com.example.dimanow.location.NearbyTransitStop
import com.example.dimanow.location.TransitStopProximityState
import com.example.dimanow.transit.Bus4402Schedule
import com.example.dimanow.live.NotificationGuidanceMode

class DimaNowApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lockStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action !in LOCK_STATE_ACTIONS) return
            guidanceRuntimeCoordinator.requestRefresh()
        }
    }

    val database: DimaDatabase by lazy {
        Room.databaseBuilder(this, DimaDatabase::class.java, "dima-now.db")
            .addMigrations(DimaDatabase.MIGRATION_1_2, DimaDatabase.MIGRATION_2_3, DimaDatabase.MIGRATION_3_4, DimaDatabase.MIGRATION_4_5)
            .build()
    }
    val repository: RoomCampusDataRepository by lazy { RoomCampusDataRepository(database) }
    val preferences: AppPreferences by lazy { AppPreferences(this) }
    private val staticDataTransport by lazy { CachingStaticDataTransport(UrlConnectionStaticDataTransport()) }
    val shuttleSource: ShuttleSource by lazy { StaticShuttleSource(database, staticDataTransport) }
    val shuttleReportSource: ShuttleReportSource by lazy {
        HttpShuttleReportSource(
            rootUrl = getString(R.string.shuttle_report_api_url),
            reporterTokenProvider = { preferences.getOrCreateShuttleReporterToken() },
        )
    }
    private val dormitoryMealSubmissionService by lazy {
        DormitoryMealSubmissionService(
            gateway = AnonymousDormitoryMealApi(
                uploadRoot = getString(R.string.dormitory_meal_upload_url),
            ),
        )
    }
    val mealSource: MealSource by lazy {
        StaticMealSource(
            database = database,
            transport = staticDataTransport,
            dormitorySubmissionService = dormitoryMealSubmissionService,
        )
    }
    val noticeSource: NoticeSource by lazy { StaticNoticeSource(database, staticDataTransport) }
    val lmsDatabase: LmsCacheDatabase by lazy {
        Room.databaseBuilder(this, LmsCacheDatabase::class.java, "lms-cache.db")
            .addMigrations(
                com.example.dimanow.lms.LMS_CACHE_MIGRATION_1_2,
                com.example.dimanow.lms.LMS_CACHE_MIGRATION_2_3,
                com.example.dimanow.lms.LMS_CACHE_MIGRATION_3_4,
                com.example.dimanow.lms.LMS_CACHE_MIGRATION_4_5,
            )
            .build()
    }
    val lmsCredentialStore: AndroidLmsCredentialStore by lazy { AndroidLmsCredentialStore(this) }
    val lmsSessionController: MutableLmsSessionController by lazy { MutableLmsSessionController() }
    val lmsLoginBridge: LmsLoginBridge by lazy { LmsLoginBridge() }
    val lmsRenderedPageBridge: com.example.dimanow.lms.LmsRenderedPageBridge by lazy {
        com.example.dimanow.lms.LmsRenderedPageBridge()
    }
    val lmsAutoLoginCoordinator: LmsAutoLoginCoordinator by lazy {
        LmsAutoLoginCoordinator(lmsCredentialStore, lmsSessionController, lmsLoginBridge)
    }
    val lmsSource: RoomLmsSource by lazy {
        RoomLmsSource(
            database = lmsDatabase,
            sessionController = lmsSessionController,
            renderedPageLoader = lmsRenderedPageBridge,
        )
    }
    val guidanceEngine: GuidanceEngine by lazy { GuidanceEngine() }
    val liveSurfaceController: AndroidLiveSurfaceController by lazy { AndroidLiveSurfaceController(this) }
    val guidanceAlarmScheduler: GuidanceAlarmScheduler by lazy { GuidanceAlarmScheduler(this) }
    val widgetMinuteCoordinator: SharedWidgetMinuteCoordinator by lazy { SharedWidgetMinuteCoordinator(this) }
    private val shuttleIndexCache: ShuttleIndexCache by lazy { ShuttleIndexCache(guidanceEngine) }
    val guidanceOrchestrator: GuidanceOrchestrator by lazy {
        GuidanceOrchestrator(guidanceEngine, liveSurfaceController, guidanceAlarmScheduler)
    }
    val guidanceRuntimeCoordinator: GuidanceRuntimeCoordinator<GuidanceRuntimeSnapshot> by lazy {
        GuidanceRuntimeCoordinator(applicationScope) { guidanceOrchestrator.refresh(it) }
    }
    val appUpdateCoordinator: AppUpdateCoordinator by lazy {
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        AppUpdateCoordinator(
            scope = applicationScope,
            preferences = preferences,
            source = GitHubAppUpdateSource(),
            installer = AndroidAppUpdateInstaller(this),
            currentVersion = packageInfo.versionName.orEmpty(),
        )
    }
    private val geofenceManager: CampusGeofenceManager by lazy { CampusGeofenceManager(this) }
    private val locationResolver: LocationResolver by lazy { LocationResolver() }

    override fun onCreate() {
        super.onCreate()
        appUpdateCoordinator.initialize()
        val lockStateFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(lockStateReceiver, lockStateFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(lockStateReceiver, lockStateFilter)
        }
        applicationScope.launch {
            repository.ensureSeeded()
            if (preferences.campusZoneDefaultsVersion.first() < 4) {
                repository.installBundledCampusZones()
                preferences.setCampusZoneDefaultsVersion(4)
            }
        }
        applicationScope.launch {
            combine(
                repository.zones,
                preferences.locationMode,
                preferences.notificationGuidancePolicy,
            ) { zones, mode, policy -> Triple(zones, mode, policy) }
                .collectLatest { (zones, mode, policy) ->
                    val includeBus4402 = policy.bus4402 != NotificationGuidanceMode.OFF
                    if (!includeBus4402) {
                        preferences.setTransitStopProximityState(TransitStopProximityState())
                        val activeCampusGeofences = preferences.activeGeofenceIds.first()
                            .filterNot { it.startsWith(BUS_4402_GEOFENCE_PREFIX) }
                            .toSet()
                        preferences.setLocationState(preferences.lastResolvedZone.first(), activeCampusGeofences)
                    }
                    if (mode == LocationMode.TEST) geofenceManager.clear()
                    else geofenceManager.sync(zones, includeBus4402)
                }
        }
        applicationScope.launch {
            combine(
                preferences.activeGeofenceIds,
                preferences.transitStopProximityState,
                preferences.notificationGuidancePolicy,
            ) { activeGeofences, proximity, policy ->
                locationResolver.shouldPollNearbyTransitStop(
                    activeGeofenceIds = activeGeofences,
                    previous = proximity,
                    guidanceEnabled = policy.bus4402 != NotificationGuidanceMode.OFF,
                )
            }
                .distinctUntilChanged()
                .collectLatest { shouldPoll ->
                    if (!shouldPoll) return@collectLatest
                    while (true) {
                        delay(30_000)
                        refreshTransitStopProximity()
                    }
                }
        }
        applicationScope.launch { RefreshScheduler.schedule(this@DimaNowApplication, preferences) }
        applicationScope.launch {
            val widgetRefreshPlanner = RuntimeWidgetRefreshPlanner()
            var previousWidgetZone: com.example.dimanow.domain.CampusZoneId? = null
            val scheduleAndShuttle = combine(repository.schedule, shuttleSource.data) { schedule, shuttle ->
                schedule to shuttle
            }
            val runtimePreferences = combine(
                preferences.effectiveZone,
                preferences.liveDisplayOptions,
                preferences.homeBase,
                preferences.notificationGuidancePolicy,
                preferences.effectiveTransitStopNumber,
            ) { zone, displayOptions, homeBase, notificationPolicy, transitStopNumber ->
                RuntimePreferences(zone, displayOptions, homeBase, notificationPolicy, transitStopNumber)
            }
            combine(scheduleAndShuttle, runtimePreferences) { (schedule, shuttle), settings ->
                val transitStop = Bus4402Schedule.official.stops
                    .firstOrNull { it.stopNumber == settings.transitStopNumber }
                GuidanceRuntimeSnapshot(
                    schedule = schedule,
                    shuttle = shuttle,
                    shuttleIndex = shuttleIndexCache.get(shuttle.departures),
                    resolvedZone = settings.zone,
                    displayOptions = settings.displayOptions,
                    homeBase = settings.homeBase,
                    notificationPolicy = settings.notificationPolicy,
                    nearbyTransitStop = transitStop?.let { NearbyTransitStop(it.stopNumber, it.displayName) },
                )
            }
                .collectLatest { snapshot ->
                    guidanceRuntimeCoordinator.update(snapshot)
                    if (widgetRefreshPlanner.shouldRefresh(previousWidgetZone, snapshot.resolvedZone)) {
                        previousWidgetZone = snapshot.resolvedZone
                        ShuttleWidgetProvider.updateAll(this@DimaNowApplication)
                        CampusSummaryWidgetProvider.updateAll(this@DimaNowApplication)
                    }
                }
            }
    }

    suspend fun isAtShuttleReportZone(expectedZone: CampusZoneId): Boolean {
        if (preferences.locationMode.first() != LocationMode.GPS) return false
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return false
        val cancellation = CancellationTokenSource()
        val location = runCatching {
            withTimeoutOrNull(10_000) {
                LocationServices.getFusedLocationProviderClient(this@DimaNowApplication)
                    .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellation.token)
                    .await()
            }
        }.getOrNull().also { if (it == null) cancellation.cancel() } ?: return false
        val sample = LocationSample(
            point = GeoPoint(location.latitude, location.longitude),
            accuracyMeters = location.accuracy,
            capturedAt = Instant.ofEpochMilli(location.time),
        )
        return LocationResolver().isFreshSampleAtZone(
            sample = sample,
            now = Instant.now(),
            configuredZones = repository.zones.first(),
            expectedZone = expectedZone,
        )
    }

    private suspend fun refreshTransitStopProximity() {
        if (preferences.locationMode.first() != LocationMode.GPS) return
        if (preferences.notificationGuidancePolicy.first().bus4402 == NotificationGuidanceMode.OFF) return
        val state = preferences.transitStopProximityState.first()
        val cancellation = CancellationTokenSource()
        val location = if (
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        ) {
            runCatching {
                withTimeoutOrNull(10_000) {
                    LocationServices.getFusedLocationProviderClient(this@DimaNowApplication)
                        .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellation.token)
                        .await()
                }
            }.getOrNull()
        } else null
        if (location == null) cancellation.cancel()
        val sample = location?.let {
            LocationSample(
                point = GeoPoint(it.latitude, it.longitude),
                accuracyMeters = it.accuracy,
                capturedAt = Instant.ofEpochMilli(it.time),
            )
        }
        val result = locationResolver.resolveNearbyTransitStop(
            sample = sample,
            now = Instant.now(),
            stops = Bus4402Schedule.official.stops,
            previous = state,
        )
        preferences.setTransitStopProximityState(result.state)
    }

    private companion object {
        val LOCK_STATE_ACTIONS = setOf(Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT)
        const val BUS_4402_GEOFENCE_PREFIX = "BUS_4402_"
    }
}

private data class RuntimePreferences(
    val zone: CampusZoneId,
    val displayOptions: com.example.dimanow.live.LiveDisplayOptions,
    val homeBase: com.example.dimanow.guidance.HomeBase,
    val notificationPolicy: com.example.dimanow.live.NotificationGuidancePolicy,
    val transitStopNumber: String?,
)

private class ShuttleIndexCache(private val engine: GuidanceEngine) {
    private var departures: List<ShuttleDeparture>? = null
    private var index: ShuttleScheduleIndex? = null

    @Synchronized
    fun get(current: List<ShuttleDeparture>): ShuttleScheduleIndex {
        if (departures != current || index == null) {
            departures = current
            index = engine.prepareShuttleSchedule(current)
        }
        return index!!
    }
}
