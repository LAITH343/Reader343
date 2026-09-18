package com.reader343.ui.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.reader343.reminders.canPostReminders

enum class PermissionResult { Granted, Denied, Blocked }

@Stable
class NotificationAccess internal constructor(private val context: Context) {

    var allowed by mutableStateOf(context.canPostReminders())
        private set

    var needsPrompt by mutableStateOf(permissionMissing())
        private set

    internal var launcher: ManagedActivityResultLauncher<String, Boolean>? = null
    private var pending: ((PermissionResult) -> Unit)? = null

    fun refresh() {
        allowed = context.canPostReminders()
        needsPrompt = permissionMissing()
    }

    fun request(onResult: (PermissionResult) -> Unit) {
        val launcher = launcher
        if (!permissionMissing() || launcher == null) {
            refresh()
            onResult(if (allowed) PermissionResult.Granted else PermissionResult.Blocked)
            return
        }
        pending = onResult
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    fun openSystemSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    internal fun onPermissionResult(granted: Boolean, canAskAgain: Boolean) {
        refresh()
        val result = when {
            granted -> PermissionResult.Granted
            canAskAgain -> PermissionResult.Denied
            else -> PermissionResult.Blocked
        }
        pending?.invoke(result)
        pending = null
    }

    private fun permissionMissing(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
}

@Composable
fun rememberNotificationAccess(): NotificationAccess {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val access = remember(context) { NotificationAccess(context.applicationContext) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val canAskAgain = activity != null &&
            ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
        access.onPermissionResult(granted, canAskAgain)
    }
    SideEffect { access.launcher = launcher }
    LifecycleResumeEffect(access) {
        access.refresh()
        onPauseOrDispose {}
    }
    return access
}
