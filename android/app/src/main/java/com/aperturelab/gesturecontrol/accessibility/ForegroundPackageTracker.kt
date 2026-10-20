package com.aperturelab.gesturecontrol.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo

/**
 * Determines which package owns the foreground, ignoring input-method windows
 * and system overlays so the activation logic never flips to the keyboard or a
 * permission dialog.
 */
class ForegroundPackageTracker(
    private val service: AccessibilityService,
    private val ownPackage: String,
) {

    fun resolve(event: AccessibilityEvent): ForegroundState {
        val eventPackage = event.packageName?.toString()
        val eventClass = event.className?.toString()

        // Our own UI owns the foreground outright: recognition must stop rather
        // than keep honouring the application we just left.
        if (eventPackage == ownPackage) return ForegroundState.OwnApplication
        // An input method or a system overlay is only a transient layer above
        // whatever is really in front, so the previous decision still stands.
        if (eventPackage != null && isTransient(eventPackage, eventClass)) {
            return ForegroundState.Unknown
        }

        val applicationWindows = runCatching {
            service.windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
        }.getOrDefault(emptyList())

        val activeWindow = applicationWindows.firstOrNull { it.isActive }
            ?: applicationWindows.lastOrNull()

        val windowPackage = runCatching {
            activeWindow?.root?.packageName?.toString()
        }.getOrNull()

        val candidate = windowPackage ?: eventPackage
            ?: return ForegroundState.Unknown

        if (candidate == ownPackage) return ForegroundState.OwnApplication
        if (isTransient(candidate, if (windowPackage == null) eventClass else null)) {
            return ForegroundState.Unknown
        }
        return ForegroundState.Application(candidate)
    }

    private fun isTransient(packageName: String, className: String?): Boolean {
        if (className?.contains("InputMethod", ignoreCase = true) == true) return true
        if (packageName.contains("inputmethod", ignoreCase = true)) return true
        return packageName in OVERLAY_PACKAGES
    }

    private companion object {
        /** Windows that float above applications and must not count as the foreground. */
        val OVERLAY_PACKAGES = setOf(
            "com.android.systemui",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.miui.securitycenter",
        )
    }
}

sealed interface ForegroundState {
    /** A real application owns the foreground. */
    data class Application(val packageName: String) : ForegroundState

    /** Our own settings UI is in front: recognition must stop. */
    data object OwnApplication : ForegroundState

    /** The foreground is unclear or covered by a transient layer; keep the last decision. */
    data object Unknown : ForegroundState
}
