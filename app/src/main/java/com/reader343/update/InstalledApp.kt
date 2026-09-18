package com.reader343.update

import android.content.Context
import com.reader343.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

data class InstalledApp(
    val versionName: String,
    val versionCode: Int,
    val minSdk: Int,
    val updatedAt: Instant?,
)

@Singleton
class InstalledAppProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    val app: InstalledApp by lazy {
        val info = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
        InstalledApp(
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE,
            minSdk = context.applicationInfo.minSdkVersion,
            updatedAt = info?.let { Instant.ofEpochMilli(maxOf(it.firstInstallTime, it.lastUpdateTime)) },
        )
    }
}

object AndroidVersions {

    private val Names = mapOf(
        21 to "5.0", 22 to "5.1", 23 to "6", 24 to "7.0", 25 to "7.1", 26 to "8.0", 27 to "8.1",
        28 to "9", 29 to "10", 30 to "11", 31 to "12", 32 to "12L", 33 to "13", 34 to "14",
        35 to "15", 36 to "16", 37 to "17",
    )

    fun nameOf(sdk: Int): String? = Names[sdk]
}
