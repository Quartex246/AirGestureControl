package com.aperturelab.gesturecontrol.ui.settings

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aperturelab.gesturecontrol.domain.GestureAction
import com.aperturelab.gesturecontrol.domain.GestureConfig
import com.aperturelab.gesturecontrol.domain.GestureEvent
import com.aperturelab.gesturecontrol.domain.GestureMapping
import com.aperturelab.gesturecontrol.launcher.InstalledApps
import com.aperturelab.gesturecontrol.launcher.LaunchedApp
import com.aperturelab.gesturecontrol.runtime.ActivationState
import com.aperturelab.gesturecontrol.runtime.GestureRuntime
import com.aperturelab.gesturecontrol.runtime.PermissionChecks
import com.aperturelab.gesturecontrol.runtime.PermissionStatus
import com.aperturelab.gesturecontrol.service.CameraForegroundService
import com.aperturelab.gesturecontrol.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsUiState(
    val activation: ActivationState = ActivationState.INACTIVE_NOT_ENABLED,
    val armed: Boolean = false,
    val paused: Boolean = false,
    val enabledPackages: Set<String> = emptySet(),
    val defaultMapping: GestureMapping = GestureMapping.default(),
    val overrides: Map<String, GestureMapping> = emptyMap(),
    val config: GestureConfig = GestureConfig(),
    val permissions: PermissionStatus = PermissionStatus(),
    val apps: List<LaunchedApp> = emptyList(),
) {
    fun labelOf(packageName: String): String =
        apps.firstOrNull { it.packageName == packageName }?.label ?: packageName

    fun enabledApps(): List<LaunchedApp> =
        apps.filter { it.packageName in enabledPackages }
}

private data class SettingsSnapshot(
    val enabled: Set<String>,
    val config: GestureConfig,
    val defaultMapping: GestureMapping,
    val overrides: Map<String, GestureMapping>,
)

private data class RuntimeSnapshot(
    val activation: ActivationState,
    val armed: Boolean,
    val paused: Boolean,
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SettingsRepository.get(application)
    private val installedApps = InstalledApps(application)
    private val appPackage: String = application.packageName

    private val apps = MutableStateFlow<List<LaunchedApp>>(emptyList())
    private val permissions = MutableStateFlow(PermissionChecks.read(application))

    private val settings = combine(
        repository.enabledPackages,
        repository.config,
        repository.defaultMapping,
        repository.overrides,
    ) { enabled, config, defaultMapping, overrides ->
        SettingsSnapshot(enabled, config, defaultMapping, overrides)
    }

    private val runtime = combine(
        GestureRuntime.activation,
        GestureRuntime.armed,
        GestureRuntime.paused,
    ) { activation, armed, paused -> RuntimeSnapshot(activation, armed, paused) }

    val uiState: StateFlow<SettingsUiState> =
        combine(settings, runtime, apps, permissions) { settings, runtime, apps, permissions ->
            SettingsUiState(
                activation = runtime.activation,
                armed = runtime.armed,
                paused = runtime.paused,
                enabledPackages = settings.enabled,
                defaultMapping = settings.defaultMapping,
                overrides = settings.overrides,
                config = settings.config,
                permissions = permissions,
                apps = apps,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    init {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) { installedApps.load(appPackage) }
            apps.value = loaded
        }
        viewModelScope.launch {
            // Health check: a revoked accessibility or overlay grant must show
            // up without the user having to restart the settings surface.
            while (true) {
                permissions.value = PermissionChecks.read(getApplication())
                delay(HEALTH_CHECK_MS)
            }
        }
    }

    val applicationPackage: String get() = appPackage

    fun start() {
        // Only start the service here; the activation layer decides when the
        // camera mounts, so arming from our own settings screen never leaves
        // the camera running behind the settings UI.
        CameraForegroundService.ensureStarted(getApplication())
    }

    fun stop() {
        val context = getApplication<Application>()
        CameraForegroundService.requestUnmount()
        CameraForegroundService.stop(context)
    }

    fun setPaused(paused: Boolean) {
        GestureRuntime.paused.value = paused
    }

    fun setEnabled(packageName: String, enabled: Boolean) {
        viewModelScope.launch { repository.setEnabled(packageName, enabled) }
    }

    fun setOverrideAction(packageName: String, event: GestureEvent, action: GestureAction) {
        viewModelScope.launch { repository.setOverrideAction(packageName, event, action) }
    }

    fun clearOverrides(packageName: String) {
        viewModelScope.launch { repository.clearOverrides(packageName) }
    }

    fun applyPreset(packageName: String, preset: GestureMapping) {
        viewModelScope.launch { repository.applyPreset(packageName, preset) }
    }

    fun setDefaultAction(event: GestureEvent, action: GestureAction) {
        viewModelScope.launch {
            val current = uiState.value.defaultMapping.actions.toMutableMap()
            current[event] = action
            repository.setDefaultMapping(GestureMapping(current))
        }
    }

    fun setConfig(config: GestureConfig) {
        viewModelScope.launch { repository.setConfig(config) }
    }

    fun launchApp(packageName: String) {
        val context = getApplication<Application>()
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    private companion object {
        const val HEALTH_CHECK_MS = 1_500L
    }
}
