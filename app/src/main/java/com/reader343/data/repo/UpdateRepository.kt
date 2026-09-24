package com.reader343.data.repo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.reader343.di.ApplicationScope
import com.reader343.update.ApkDownloader
import com.reader343.update.DownloadState
import com.reader343.update.GitHubReleases
import com.reader343.update.InstalledApp
import com.reader343.update.InstalledAppProvider
import com.reader343.update.LatestResponse
import com.reader343.update.Release
import com.reader343.update.ReleaseParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

data class UpdateStatus(
    val installed: InstalledApp,
    val release: Release?,
    val checkedAt: Instant?,
    val checking: Boolean,
    val checkFailed: Boolean,
    val download: DownloadState,
) {
    val available: Boolean get() = release?.isNewerThan(installed.versionCode) == true
}

@Singleton
class UpdateRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val gitHub: GitHubReleases,
    private val downloader: ApkDownloader,
    installedApp: InstalledAppProvider,
    @ApplicationScope scope: CoroutineScope,
) {

    private val installed = installedApp.app
    private val checking = MutableStateFlow(false)
    private val checkFailed = MutableStateFlow(false)
    private val mutex = Mutex()

    private val cached: Flow<CachedRelease> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            CachedRelease(
                release = prefs[Keys.RELEASE]?.takeIf { it.isNotEmpty() }?.let(ReleaseParser::parse),
                checkedAt = prefs[Keys.CHECKED_AT]?.let(Instant::ofEpochMilli),
            )
        }
        .distinctUntilChanged()

    val status: Flow<UpdateStatus> = combine(cached, checking, checkFailed, downloader.state) { cache, busy, failed, download ->
        UpdateStatus(
            installed = installed,
            release = cache.release,
            checkedAt = cache.checkedAt,
            checking = busy,
            checkFailed = failed,
            download = download,
        )
    }.distinctUntilChanged()

    val updateAvailable: Flow<Release?> = cached
        .map { cache -> cache.release?.takeIf { it.isNewerThan(installed.versionCode) } }
        .distinctUntilChanged()

    init {
        scope.launch {
            updateAvailable.map { it?.apk }.distinctUntilChanged().collect { downloader.sync(it) }
        }
    }

    suspend fun check(): Boolean = mutex.withLock {
        checking.value = true
        try {
            val raw = when (val response = gitHub.fetchLatest()) {
                is LatestResponse.Found -> response.json.takeIf { ReleaseParser.parse(it) != null }.orEmpty()
                LatestResponse.None -> ""
            }
            dataStore.edit {
                it[Keys.RELEASE] = raw
                it[Keys.CHECKED_AT] = System.currentTimeMillis()
            }
            checkFailed.value = false
            true
        } catch (_: IOException) {
            checkFailed.value = true
            false
        } finally {
            checking.value = false
        }
    }

    suspend fun checkIfStale(): Boolean {
        val last = cached.first().checkedAt?.toEpochMilli() ?: 0L
        val now = System.currentTimeMillis()
        if (now - last in 0 until CHECK_INTERVAL_MS) return true
        return check()
    }

    suspend fun claimAvailableNotice(): Release? {
        val release = updateAvailable.first() ?: return null
        var claimed = false
        dataStore.edit {
            if ((it[Keys.NOTIFIED_CODE] ?: 0) < release.versionCode) {
                it[Keys.NOTIFIED_CODE] = release.versionCode
                claimed = true
            }
        }
        return release.takeIf { claimed }
    }

    fun startDownload(allowMetered: Boolean) = downloader.start(allowMetered)

    fun pauseDownload() = downloader.pause()

    fun cancelDownload() = downloader.cancel()

    private data class CachedRelease(val release: Release?, val checkedAt: Instant?)

    private object Keys {
        val RELEASE = stringPreferencesKey("update_release")
        val CHECKED_AT = longPreferencesKey("update_checked_at")
        val NOTIFIED_CODE = intPreferencesKey("update_notified_code")
    }

    companion object {
        const val CHECK_INTERVAL_MS = 24 * 60 * 60_000L
    }
}
