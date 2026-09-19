package com.reader343.metadata

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppIdentity @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    val packageName: String get() = context.packageName

    val certificateSha1: String? by lazy { runCatching { signingSha1() }.getOrNull() }

    @Suppress("DEPRECATION")
    private fun signingSha1(): String? {
        val pm = context.packageManager
        val signature: Signature? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo?.apkContentsSigners?.firstOrNull()
        } else {
            pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES).signatures?.firstOrNull()
        }
        val bytes = signature?.toByteArray() ?: return null
        return MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02X".format(it) }
    }
}
