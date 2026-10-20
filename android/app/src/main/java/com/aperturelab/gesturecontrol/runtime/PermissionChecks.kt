package com.aperturelab.gesturecontrol.runtime

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** The four things the user must grant before recognition can run. */
enum class PermissionItem {
    CAMERA,
    ACCESSIBILITY,
    OVERLAY,
    NOTIFICATIONS,
}

data class PermissionStatus(
    val camera: Boolean = false,
    val accessibility: Boolean = false,
    val overlay: Boolean = false,
    val notifications: Boolean = false,
) {
    val ready: Boolean get() = camera && accessibility && overlay && notifications

    val missing: List<PermissionItem>
        get() = buildList {
            if (!camera) add(PermissionItem.CAMERA)
            if (!accessibility) add(PermissionItem.ACCESSIBILITY)
            if (!overlay) add(PermissionItem.OVERLAY)
            if (!notifications) add(PermissionItem.NOTIFICATIONS)
        }
}

/**
 * Reads the live permission state. The accessibility check goes through
 * [AccessibilityManager] rather than the secure settings string so a service
 * that the user just revoked is reported immediately.
 */
object PermissionChecks {

    fun read(context: Context): PermissionStatus = PermissionStatus(
        camera = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED,
        accessibility = isAccessibilityEnabled(context),
        overlay = Settings.canDrawOverlays(context),
        notifications = NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED),
    )

    fun isAccessibilityEnabled(context: Context): Boolean = runCatching {
        val manager = context.getSystemService(AccessibilityManager::class.java) ?: return false
        manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo?.serviceInfo?.packageName == context.packageName }
    }.getOrDefault(false)

    /** Platform screen that lets the user grant [item], or null when none exists. */
    fun settingsIntent(context: Context, item: PermissionItem): Intent? = when (item) {
        PermissionItem.CAMERA,
        PermissionItem.NOTIFICATIONS,
        -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", context.packageName, null))

        PermissionItem.ACCESSIBILITY -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

        PermissionItem.OVERLAY -> Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.fromParts("package", context.packageName, null),
        )
    }
}
