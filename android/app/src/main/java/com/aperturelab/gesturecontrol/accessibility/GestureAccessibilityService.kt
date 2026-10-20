package com.aperturelab.gesturecontrol.accessibility

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.aperturelab.gesturecontrol.domain.ActionResolver
import com.aperturelab.gesturecontrol.domain.GestureEvent
import com.aperturelab.gesturecontrol.domain.GestureMapping
import com.aperturelab.gesturecontrol.domain.shortLabel
import com.aperturelab.gesturecontrol.overlay.StatusOverlay
import com.aperturelab.gesturecontrol.runtime.ActivationState
import com.aperturelab.gesturecontrol.runtime.GestureRuntime
import com.aperturelab.gesturecontrol.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Tracks the foreground application, decides when recognition should run, and
 * executes resolved actions.
 *
 * The camera foreground service stays armed; this service only flips
 * [GestureRuntime.cameraRequested] when an enabled application is in front.
 */
class GestureAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var tracker: ForegroundPackageTracker
    private lateinit var dispatcher: ActionDispatcher

    private var enabledPackages: Set<String> = emptySet()
    private var defaultMapping: GestureMapping = GestureMapping.default()
    private var overrides: Map<String, GestureMapping> = emptyMap()
    private var currentPackage: String? = null
    private var unmountJob: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        tracker = ForegroundPackageTracker(this, packageName)
        dispatcher = ActionDispatcher(this)
        val settings = SettingsRepository.get(this)
        Log.i(TAG, "service connected")

        scope.launch {
            settings.enabledPackages.collect {
                enabledPackages = it
                reevaluate("enabled set changed")
            }
        }
        scope.launch {
            settings.defaultMapping.collect { defaultMapping = it }
        }
        scope.launch {
            settings.overrides.collect { overrides = it }
        }
        scope.launch {
            GestureRuntime.paused.collect { reevaluate("pause changed") }
        }
        // Arming the camera service from the settings surface must re-run the
        // activation decision, otherwise the camera waits for the next app switch.
        scope.launch {
            GestureRuntime.armed.collect { reevaluate("armed changed") }
        }
        scope.launch {
            GestureRuntime.gestureEvents.collect(::dispatch)
        }
        // Reconnecting (reboot, service restart) can leave the user sitting in an
        // enabled application with no pending window event to react to.
        scope.launch {
            delay(INITIAL_SYNC_MS)
            if (currentPackage != null) return@launch
            val initial = runCatching { rootInActiveWindow?.packageName?.toString() }.getOrNull()
            if (initial == null || initial == packageName) return@launch
            currentPackage = initial
            Log.i(TAG, "initial foreground: $initial")
            reevaluate("initial foreground")
        }

        // The camera foreground service must be armed while the app is in the
        // foreground (Android 14+ while-in-use rule), so it is started by the
        // settings surface, never from here. This layer only flips the mount
        // request below.
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            -> {
                val foreground = tracker.resolve(event)
                Log.d(TAG, "window event pkg=${event.packageName} resolved=$foreground")
                val packageName = when (foreground) {
                    // A transient layer (keyboard, permission dialog) says nothing
                    // about the application underneath, so keep the last decision.
                    ForegroundState.Unknown -> return
                    // Our own settings UI is in front: stop recognising instead of
                    // acting on the application that was previously enabled.
                    ForegroundState.OwnApplication -> null
                    is ForegroundState.Application -> foreground.packageName
                }
                if (packageName != currentPackage) {
                    val previous = currentPackage
                    currentPackage = packageName
                    Log.i(TAG, "foreground: $previous -> $packageName")
                    reevaluate("foreground changed")
                }
            }
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        unmountJob?.cancel()
        scope.cancel()
        GestureRuntime.cameraRequested.value = false
        super.onDestroy()
    }

    // --------------------------------------------------------------- activation

    private fun reevaluate(reason: String) {
        val foreground = currentPackage
        val paused = GestureRuntime.paused.value
        val shouldRun = GestureRuntime.armed.value && !paused &&
            foreground != null && foreground in enabledPackages

        if (shouldRun) {
            unmountJob?.cancel()
            unmountJob = null
            GestureRuntime.cameraRequested.value = true
            Log.d(TAG, "activate ($reason) for $foreground")
            return
        }

        if (GestureRuntime.cameraRequested.value) {
            // Leaving an enabled application: wait out the settle delay so a
            // quick switch back does not remount the camera.
            if (unmountJob?.isActive == true) return
            unmountJob = scope.launch {
                delay(SETTLE_MS)
                val stillShouldRun = GestureRuntime.armed.value &&
                    !GestureRuntime.paused.value &&
                    currentPackage != null && currentPackage in enabledPackages
                if (!stillShouldRun) {
                    GestureRuntime.cameraRequested.value = false
                    Log.d(TAG, "deactivate ($reason) for $foreground")
                }
            }
        } else {
            GestureRuntime.activation.value = if (paused) ActivationState.PAUSED
            else ActivationState.INACTIVE_NOT_ENABLED
        }
    }

    // ---------------------------------------------------------------- dispatch

    private fun dispatch(event: GestureEvent) {
        val foreground = currentPackage
        if (GestureRuntime.paused.value) return
        if (!GestureRuntime.armed.value) return
        if (foreground == null || foreground !in enabledPackages) return

        val resolver = ActionResolver(defaultMapping, overrides)
        val action = resolver.resolve(foreground, event)
        Log.i(TAG, "dispatch $event -> $action for $foreground")
        StatusOverlay.showAction(event, action.shortLabel())
        dispatcher.execute(action)
    }

    private companion object {
        const val TAG = "GestureAccessibility"
        const val SETTLE_MS = 500L
        const val INITIAL_SYNC_MS = 300L
    }
}
