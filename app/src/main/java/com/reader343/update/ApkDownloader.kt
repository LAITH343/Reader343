package com.reader343.update

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.reader343.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

enum class DownloadError { Network, NeedsWifi, Verification, Storage }

sealed interface DownloadState {
    data object Idle : DownloadState
    data class Downloading(val bytes: Long, val total: Long, val bytesPerSecond: Long) : DownloadState
    data class Paused(val bytes: Long, val total: Long) : DownloadState
    data object Verifying : DownloadState
    data class Ready(val file: File) : DownloadState
    data class Failed(val error: DownloadError) : DownloadState
}

@Singleton
class ApkDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
    private val verifier: ApkVerifier,
    private val installed: InstalledAppProvider,
) {

    private val _state = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val state: StateFlow<DownloadState> = _state.asStateFlow()

    private val mutex = Mutex()
    private var job: Job? = null
    private var target: ApkAsset? = null

    @Volatile
    private var generation = 0

    private val directory: File get() = File(context.cacheDir, DIRECTORY)

    fun sync(asset: ApkAsset?) {
        scope.launch {
            mutex.withLock {
                if (asset == target) return@withLock
                stopJob()
                target = asset
                clearExcept(asset)
                _state.value = DownloadState.Idle
                if (asset != null) restore(asset)
            }
        }
    }

    fun start(allowMetered: Boolean) {
        scope.launch {
            mutex.withLock {
                val asset = target ?: return@withLock
                if (job?.isActive == true) return@withLock
                if (!allowMetered && isMetered()) {
                    _state.value = DownloadState.Failed(DownloadError.NeedsWifi)
                    return@withLock
                }
                val id = ++generation
                job = scope.launch { download(asset, id) }
            }
        }
    }

    fun pause() {
        scope.launch {
            mutex.withLock {
                val asset = target ?: return@withLock
                stopJob()
                _state.value = DownloadState.Paused(partFile(asset).length(), asset.size)
            }
        }
    }

    fun cancel() {
        scope.launch {
            mutex.withLock {
                val asset = target ?: return@withLock
                stopJob()
                withContext(Dispatchers.IO) {
                    partFile(asset).delete()
                    apkFile(asset).delete()
                }
                _state.value = DownloadState.Idle
            }
        }
    }

    private fun stopJob() {
        generation++
        job?.cancel()
        job = null
    }

    private fun emit(id: Int, value: DownloadState) {
        if (id == generation) _state.value = value
    }

    private suspend fun restore(asset: ApkAsset) {
        val apk = apkFile(asset)
        val part = partFile(asset)
        when {
            apk.exists() -> verify(asset, apk, generation)
            part.exists() && part.length() > 0L -> _state.value = DownloadState.Paused(part.length(), asset.size)
        }
    }

    private suspend fun download(asset: ApkAsset, id: Int) {
        try {
            val complete = transfer(asset, id)
            emit(id, DownloadState.Verifying)
            verify(asset, complete, id)
        } catch (e: CancellationException) {
            throw e
        } catch (_: IOException) {
            emit(id, DownloadState.Failed(DownloadError.Network))
        } catch (_: SecurityException) {
            emit(id, DownloadState.Failed(DownloadError.Storage))
        }
    }

    private suspend fun transfer(asset: ApkAsset, id: Int): File = withContext(Dispatchers.IO) {
        directory.mkdirs()
        val part = partFile(asset)
        var bytes = part.length()
        val connection = (URL(asset.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", GitHubReleases.userAgent())
            setRequestProperty("Accept", "application/octet-stream")
            if (bytes > 0L) setRequestProperty("Range", "bytes=$bytes-")
        }
        try {
            val append = when (connection.responseCode) {
                HttpURLConnection.HTTP_PARTIAL -> true
                HttpURLConnection.HTTP_OK -> false
                HTTP_RANGE_NOT_SATISFIABLE -> if (asset.size in 1..bytes) return@withContext finish(asset) else {
                    part.delete()
                    throw IOException("Range not satisfiable")
                }
                else -> throw IOException("HTTP ${connection.responseCode}")
            }
            if (!append) bytes = 0L
            val total = asset.size.takeIf { it > 0L } ?: (bytes + connection.contentLengthLong.coerceAtLeast(0L))
            val meter = SpeedMeter()
            emit(id, DownloadState.Downloading(bytes, total, 0L))
            connection.inputStream.use { input ->
                FileOutputStream(part, append).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var lastEmit = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        bytes += read
                        val now = System.nanoTime()
                        meter.record(now, bytes)
                        if (now - lastEmit >= EMIT_INTERVAL_NS) {
                            lastEmit = now
                            emit(id, DownloadState.Downloading(bytes, total, meter.bytesPerSecond()))
                        }
                    }
                }
            }
            if (asset.size > 0L && bytes != asset.size) {
                part.delete()
                throw IOException("Size mismatch")
            }
            finish(asset)
        } finally {
            connection.disconnect()
        }
    }

    private fun finish(asset: ApkAsset): File {
        val apk = apkFile(asset)
        apk.delete()
        if (!partFile(asset).renameTo(apk)) throw IOException("Rename failed")
        return apk
    }

    private suspend fun verify(asset: ApkAsset, file: File, id: Int) {
        emit(id, DownloadState.Verifying)
        val ok = verifier.verify(file, installed.app.versionCode)
        if (ok) {
            emit(id, DownloadState.Ready(file))
        } else {
            withContext(Dispatchers.IO) {
                file.delete()
                partFile(asset).delete()
            }
            emit(id, DownloadState.Failed(DownloadError.Verification))
        }
    }

    private suspend fun clearExcept(asset: ApkAsset?) = withContext(Dispatchers.IO) {
        val keep = asset?.let { setOf(apkFile(it).name, partFile(it).name) }.orEmpty()
        directory.listFiles()?.filterNot { it.name in keep }?.forEach { it.delete() }
    }

    private fun isMetered(): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    private fun apkFile(asset: ApkAsset) = File(directory, asset.name)

    private fun partFile(asset: ApkAsset) = File(directory, asset.name + PART_SUFFIX)

    private class SpeedMeter {
        private val samples = ArrayDeque<Pair<Long, Long>>()

        fun record(now: Long, bytes: Long) {
            samples.addLast(now to bytes)
            while (samples.size > 1 && now - samples.first().first > WINDOW_NS) samples.removeFirst()
        }

        fun bytesPerSecond(): Long {
            if (samples.size < 2) return 0L
            val (startTime, startBytes) = samples.first()
            val (endTime, endBytes) = samples.last()
            val elapsed = endTime - startTime
            return if (elapsed <= 0L) 0L else (endBytes - startBytes) * NS_PER_SECOND / elapsed
        }
    }

    companion object {
        const val DIRECTORY = "updates"
        private const val PART_SUFFIX = ".part"
        private const val TIMEOUT_MS = 20_000
        private const val BUFFER_SIZE = 64 * 1024
        private const val HTTP_RANGE_NOT_SATISFIABLE = 416
        private const val EMIT_INTERVAL_NS = 250_000_000L
        private const val WINDOW_NS = 3_000_000_000L
        private const val NS_PER_SECOND = 1_000_000_000L
    }
}
