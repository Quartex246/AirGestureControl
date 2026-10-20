package com.aperturelab.gesturecontrol.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

data class LaunchedApp(
    val packageName: String,
    val label: String,
    val icon: Drawable,
)

/**
 * Applications that appear in the launcher. Querying the launcher intent keeps
 * the app visible without the `QUERY_ALL_PACKAGES` permission.
 */
class InstalledApps(private val context: Context) {

    fun load(excludePackage: String): List<LaunchedApp> {
        val manager = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return manager.queryIntentActivities(intent, 0)
            .mapNotNull { info ->
                val packageName = info.activityInfo?.packageName ?: return@mapNotNull null
                if (packageName == excludePackage) return@mapNotNull null
                LaunchedApp(
                    packageName = packageName,
                    label = info.loadLabel(manager).toString(),
                    icon = runCatching { info.loadIcon(manager) }.getOrNull() ?: return@mapNotNull null,
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    fun labelOf(packageName: String): String = runCatching {
        val manager = context.packageManager
        manager.getApplicationLabel(manager.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)

    fun hasLauncherActivity(packageName: String): Boolean = runCatching {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setPackage(packageName)
        context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL).isNotEmpty()
    }.getOrDefault(true)
}
