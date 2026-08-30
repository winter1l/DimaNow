package com.example.dimanow.update

import com.example.dimanow.data.AppPreferences
import java.io.File
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class AppUpdatePhase {
    IDLE,
    CHECKING,
    UP_TO_DATE,
    AVAILABLE,
    DOWNLOADING,
    READY_TO_INSTALL,
    PERMISSION_REQUIRED,
    INSTALLER_OPENED,
    ERROR,
}

data class AppUpdateUiState(
    val currentVersion: String,
    val phase: AppUpdatePhase = AppUpdatePhase.IDLE,
    val latestRelease: AppUpdateRelease? = null,
    val downloadProgress: Int? = null,
    val promptVersion: String? = null,
    val message: String? = null,
    val preparedUpdate: PreparedUpdate? = null,
)

class AppUpdateCoordinator(
    private val scope: CoroutineScope,
    private val preferences: AppPreferences,
    private val source: AppUpdateSource,
    private val installer: AppUpdateInstaller,
    private val currentVersion: String,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val checkMutex = Mutex()
    private val policy = AppUpdatePolicy()
    private val mutableState = MutableStateFlow(AppUpdateUiState(currentVersion))
    val state: StateFlow<AppUpdateUiState> = mutableState.asStateFlow()
    private var downloadJob: Job? = null

    fun initialize() {
        installer.cleanupIncompleteDownloads()
        scope.launch {
            var saved = preferences.appUpdatePreferences.first()
            if (saved.preparedVersion != null && !policy.shouldKeepPrepared(saved.preparedVersion, currentVersion)) {
                saved.preparedPath?.let(::File)?.delete()
                preferences.clearPreparedAppUpdate()
                saved = saved.copy(preparedPath = null, preparedVersion = null)
            }
            val release = saved.cachedRelease
            val prepared = release?.takeIf { it.versionName == saved.preparedVersion }
                ?.let { saved.preparedPath?.takeIf { path -> File(path).isFile }?.let { path -> PreparedUpdate(it, path) } }
            if (release != null && AppVersion(release.versionName) > AppVersion(currentVersion)) {
                mutableState.value = AppUpdateUiState(
                    currentVersion = currentVersion,
                    phase = if (prepared != null) AppUpdatePhase.READY_TO_INSTALL else AppUpdatePhase.AVAILABLE,
                    latestRelease = release,
                    promptVersion = release.versionName.takeIf { policy.shouldPrompt(it, saved.dismissedVersion) },
                    preparedUpdate = prepared,
                )
            }
            checkForUpdate(manual = false)
        }
    }

    fun checkManually() {
        scope.launch { checkForUpdate(manual = true) }
    }

    suspend fun checkForUpdate(manual: Boolean) = checkMutex.withLock {
        val saved = preferences.appUpdatePreferences.first()
        val now = clock.instant()
        val lastChecked = saved.lastCheckedEpochMillis?.let(Instant::ofEpochMilli)
        if (!policy.shouldCheck(lastChecked, now, manual)) return@withLock
        mutableState.value = mutableState.value.copy(phase = AppUpdatePhase.CHECKING, message = null)
        runCatching { source.latestStableRelease() }
            .onSuccess { release ->
                preferences.recordAppUpdateCheck(now.toEpochMilli(), release)
                val newer = AppVersion(release.versionName) > AppVersion(currentVersion)
                mutableState.value = if (newer) {
                    val prepared = release.takeIf { it.versionName == saved.preparedVersion }
                        ?.let { saved.preparedPath?.takeIf { path -> File(path).isFile }?.let { path -> PreparedUpdate(it, path) } }
                    AppUpdateUiState(
                        currentVersion = currentVersion,
                        phase = if (prepared != null) AppUpdatePhase.READY_TO_INSTALL else AppUpdatePhase.AVAILABLE,
                        latestRelease = release,
                        promptVersion = release.versionName.takeIf { policy.shouldPrompt(it, saved.dismissedVersion) },
                        preparedUpdate = prepared,
                    )
                } else {
                    saved.preparedPath?.let(::File)?.delete()
                    preferences.clearPreparedAppUpdate()
                    AppUpdateUiState(currentVersion, AppUpdatePhase.UP_TO_DATE, release, message = "최신 버전입니다")
                }
            }
            .onFailure { error ->
                mutableState.value = mutableState.value.copy(phase = AppUpdatePhase.ERROR, message = error.message ?: "업데이트 확인에 실패했습니다")
            }
    }

    fun dismissPrompt() {
        val version = mutableState.value.promptVersion ?: return
        mutableState.value = mutableState.value.copy(promptVersion = null)
        scope.launch { preferences.dismissAppUpdateVersion(version) }
    }

    fun downloadAndInstall() {
        val release = mutableState.value.latestRelease ?: return
        if (downloadJob?.isActive == true) return
        dismissPrompt()
        downloadJob = scope.launch {
            mutableState.value = mutableState.value.copy(phase = AppUpdatePhase.DOWNLOADING, downloadProgress = 0, message = null)
            runCatching {
                installer.downloadAndVerify(release) { progress ->
                    mutableState.value = mutableState.value.copy(downloadProgress = progress)
                }
            }.onSuccess { prepared ->
                preferences.recordPreparedAppUpdate(prepared.absolutePath, release.versionName)
                mutableState.value = mutableState.value.copy(
                    phase = AppUpdatePhase.READY_TO_INSTALL,
                    downloadProgress = 100,
                    preparedUpdate = prepared,
                )
                requestInstall(prepared)
            }.onFailure { error ->
                if (error is CancellationException) throw error
                mutableState.value = mutableState.value.copy(
                    phase = AppUpdatePhase.ERROR,
                    downloadProgress = null,
                    message = error.message ?: "업데이트 다운로드에 실패했습니다",
                )
            }
            downloadJob = null
        }
    }

    fun continueInstall() {
        mutableState.value.preparedUpdate?.let(::requestInstall)
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        installer.cleanupIncompleteDownloads()
        mutableState.value = mutableState.value.copy(phase = AppUpdatePhase.AVAILABLE, downloadProgress = null, message = "다운로드를 취소했습니다")
    }

    private fun requestInstall(prepared: PreparedUpdate) {
        mutableState.value = when (installer.requestInstall(prepared)) {
            AppInstallRequestResult.INSTALLER_OPENED -> mutableState.value.copy(phase = AppUpdatePhase.INSTALLER_OPENED, message = "Android 설치 화면을 확인하세요")
            AppInstallRequestResult.PERMISSION_REQUIRED -> mutableState.value.copy(phase = AppUpdatePhase.PERMISSION_REQUIRED, message = "이 앱에서 설치를 허용한 뒤 설치를 계속하세요")
            AppInstallRequestResult.UNAVAILABLE -> mutableState.value.copy(phase = AppUpdatePhase.ERROR, message = "Android 설치 화면을 열 수 없습니다")
        }
    }
}
