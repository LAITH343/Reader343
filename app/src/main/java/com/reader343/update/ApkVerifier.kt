package com.reader343.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApkVerifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    suspend fun verify(file: File, installedVersionCode: Int): Boolean = withContext(Dispatchers.IO) {
        runCatching { check(file, installedVersionCode) }.getOrDefault(false)
    }

    private fun check(file: File, installedVersionCode: Int): Boolean {
        val pm = context.packageManager
        val archive = pm.getPackageArchiveInfo(file.absolutePath, signatureFlags()) ?: return false
        if (archive.packageName != context.packageName) return false
        if (PackageInfoCompat.getLongVersionCode(archive) <= installedVersionCode) return false
        val installed = pm.getPackageInfo(context.packageName, signatureFlags())
        return signersMatch(installed, archive)
    }

    @Suppress("DEPRECATION")
    private fun signatureFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }

    @Suppress("DEPRECATION")
    private fun signersMatch(installed: PackageInfo, archive: PackageInfo): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            val current = installed.signatures?.map { it.toCharsString() }?.toSet().orEmpty()
            val candidate = archive.signatures?.map { it.toCharsString() }?.toSet().orEmpty()
            return current.isNotEmpty() && current == candidate
        }
        val current = installed.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet().orEmpty()
        val info = archive.signingInfo ?: return false
        if (current.isEmpty()) return false
        return if (info.hasMultipleSigners()) {
            info.apkContentsSigners.map { it.toCharsString() }.toSet() == current
        } else {
            info.signingCertificateHistory.map { it.toCharsString() }.toSet().containsAll(current)
        }
    }
}
