package com.reader343.update

import com.reader343.data.repo.UpdateStatus

enum class UpdateStage { Checking, CheckFailed, UpToDate, Available, Downloading, Paused, Verifying, Ready, Failed }

fun UpdateStatus.stage(): UpdateStage = when {
    available -> when (download) {
        DownloadState.Idle -> UpdateStage.Available
        is DownloadState.Downloading -> UpdateStage.Downloading
        is DownloadState.Paused -> UpdateStage.Paused
        DownloadState.Verifying -> UpdateStage.Verifying
        is DownloadState.Ready -> UpdateStage.Ready
        is DownloadState.Failed -> UpdateStage.Failed
    }
    checking -> UpdateStage.Checking
    checkFailed -> UpdateStage.CheckFailed
    checkedAt == null -> UpdateStage.Checking
    else -> UpdateStage.UpToDate
}

data class UpdateSummary(
    val available: Boolean,
    val versionName: String,
    val versionCode: Int,
    val sizeBytes: Long,
)

fun UpdateStatus.summary(): UpdateSummary? {
    val latest = release
    return when {
        available && latest != null -> UpdateSummary(true, latest.versionName, latest.versionCode, latest.apk.size)
        checkedAt != null -> UpdateSummary(false, installed.versionName, installed.versionCode, 0L)
        else -> null
    }
}

fun etaSeconds(bytes: Long, total: Long, bytesPerSecond: Long): Long? {
    if (bytesPerSecond <= 0L || total <= 0L) return null
    val remaining = (total - bytes).coerceAtLeast(0L)
    return ((remaining + bytesPerSecond - 1) / bytesPerSecond).coerceAtLeast(1L)
}
