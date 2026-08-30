package com.example.dimanow.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.content.pm.SigningInfo
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

class AndroidAppUpdateInstaller(private val context: Context) : AppUpdateInstaller {
    private val updateDirectory = File(context.cacheDir, "updates")

    override suspend fun downloadAndVerify(release: AppUpdateRelease, onProgress: (Int) -> Unit): PreparedUpdate = withContext(Dispatchers.IO) {
        require(release.sizeBytes in 1..UpdateDownloadPolicy.MAX_APK_BYTES) { "APK 크기가 올바르지 않습니다." }
        require(UpdateDownloadPolicy.isAllowedUrl(release.downloadUrl)) { "허용되지 않은 APK 주소입니다." }
        updateDirectory.mkdirs()
        cleanupIncompleteDownloads()
        val partial = File(updateDirectory, "DIMA-Now-${release.versionName}.apk.part")
        val target = File(updateDirectory, "DIMA-Now-${release.versionName}.apk")
        target.delete()
        try {
            download(release, partial, onProgress)
            val actualHash = partial.inputStream().use(::sha256)
            require(actualHash == release.sha256) { "APK SHA-256 검증에 실패했습니다." }
            val preparedCandidate = PreparedUpdate(release, partial.absolutePath)
            require(validatePrepared(preparedCandidate) == ApkValidationResult.Valid) { "APK 패키지 또는 서명 검증에 실패했습니다." }
            require(partial.renameTo(target)) { "검증된 APK를 확정하지 못했습니다." }
            PreparedUpdate(release, target.absolutePath)
        } catch (error: Exception) {
            partial.delete()
            target.delete()
            throw error
        }
    }

    override fun requestInstall(update: PreparedUpdate): AppInstallRequestResult {
        val file = File(update.absolutePath)
        if (!file.isFile || validatePrepared(update) != ApkValidationResult.Valid) {
            file.delete()
            return AppInstallRequestResult.UNAVAILABLE
        }
        if (!context.packageManager.canRequestPackageInstalls()) {
            val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return if (settingsIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(settingsIntent)
                AppInstallRequestResult.PERMISSION_REQUIRED
            } else AppInstallRequestResult.UNAVAILABLE
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        val install = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return if (install.resolveActivity(context.packageManager) != null) {
            context.startActivity(install)
            AppInstallRequestResult.INSTALLER_OPENED
        } else AppInstallRequestResult.UNAVAILABLE
    }

    override fun cleanupIncompleteDownloads() {
        updateDirectory.listFiles { file -> file.name.endsWith(".part") }?.forEach(File::delete)
    }

    private suspend fun download(release: AppUpdateRelease, target: File, onProgress: (Int) -> Unit) {
        var currentUrl = release.downloadUrl
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            coroutineContext.ensureActive()
            require(UpdateDownloadPolicy.isAllowedUrl(currentUrl)) { "허용되지 않은 APK redirect입니다." }
            val connection = URL(currentUrl).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("Accept", "application/vnd.android.package-archive")
                connection.setRequestProperty("User-Agent", "DIMA-Now/1.2")
                val code = connection.responseCode
                if (code in 300..399) {
                    require(redirectCount < MAX_REDIRECTS) { "APK redirect가 너무 많습니다." }
                    currentUrl = connection.getHeaderField("Location") ?: error("APK redirect 주소가 없습니다.")
                    return@repeat
                }
                require(code == HttpURLConnection.HTTP_OK) { "APK 다운로드 응답 $code" }
                val contentLength = connection.contentLengthLong
                require(contentLength == -1L || contentLength in 1..UpdateDownloadPolicy.MAX_APK_BYTES) { "APK가 너무 큽니다." }
                var total = 0L
                target.outputStream().use { output ->
                    connection.inputStream.use { input ->
                        val buffer = ByteArray(32 * 1024)
                        while (true) {
                            coroutineContext.ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            require(total <= UpdateDownloadPolicy.MAX_APK_BYTES) { "APK가 너무 큽니다." }
                            output.write(buffer, 0, read)
                            if (release.sizeBytes > 0) onProgress(((total * 100) / release.sizeBytes).coerceIn(0, 100).toInt())
                        }
                    }
                }
                require(total == release.sizeBytes) { "APK 다운로드 크기가 GitHub asset과 다릅니다." }
                onProgress(100)
                return
            } finally {
                connection.disconnect()
            }
        }
        error("APK 다운로드에 실패했습니다.")
    }

    private fun validatePrepared(update: PreparedUpdate): ApkValidationResult = runCatching {
        val current = installedPackageInfo().toIdentity()
        val archive = archivePackageInfo(update.absolutePath)
            ?: return ApkValidationResult.PackageMismatch
        val verifiedSigningInfo = if (Build.VERSION.SDK_INT >= 36) {
            PackageManager.getVerifiedSigningInfo(update.absolutePath, SigningInfo.VERSION_SIGNING_BLOCK_V2)
        } else archive.signingInfo
        val candidate = archive.toIdentity(verifiedSigningInfo)
        ApkValidationPolicy().validate(update.release, current, candidate)
    }.getOrDefault(ApkValidationResult.SignerMismatch)

    private fun installedPackageInfo(): PackageInfo = if (Build.VERSION.SDK_INT >= 33) {
        context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
        )
    } else {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
    }

    private fun archivePackageInfo(path: String): PackageInfo? = if (Build.VERSION.SDK_INT >= 33) {
        context.packageManager.getPackageArchiveInfo(
            path,
            PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
        )
    } else {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageArchiveInfo(path, PackageManager.GET_SIGNING_CERTIFICATES)
    }

    private fun PackageInfo.toIdentity(verifiedSigningInfo: SigningInfo? = signingInfo): InstalledAppIdentity {
        val signers = verifiedSigningInfo?.apkContentsSigners.orEmpty().map { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02x".format(it) }
        }.toSet()
        return InstalledAppIdentity(packageName, versionName.orEmpty(), longVersionCode, signers)
    }

    private fun sha256(input: java.io.InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(32 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object { const val MAX_REDIRECTS = 5 }
}
