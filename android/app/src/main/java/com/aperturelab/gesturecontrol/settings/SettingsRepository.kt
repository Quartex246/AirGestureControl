package com.aperturelab.gesturecontrol.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aperturelab.gesturecontrol.domain.GestureAction
import com.aperturelab.gesturecontrol.domain.GestureConfig
import com.aperturelab.gesturecontrol.domain.GestureEvent
import com.aperturelab.gesturecontrol.domain.GestureMapping
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.gestureDataStore by preferencesDataStore(name = "gesture_settings")

/**
 * Persistent settings: enabled applications, global default mapping,
 * per-application overrides, and tuning values.
 *
 * Encodings stay human-readable in the preference store:
 * - mapping entries: `EVENT=ACTION`
 * - overrides: `package|EVENT=ACTION`
 */
class SettingsRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val store = appContext.gestureDataStore

    val enabledPackages: Flow<Set<String>> =
        store.data.map { it[KEY_ENABLED] ?: emptySet() }

    val config: Flow<GestureConfig> =
        store.data.map { preferences ->
            GestureConfig(
                swipeThresholdRatio = preferences[KEY_THRESHOLD] ?: DEFAULT_CONFIG.swipeThresholdRatio,
                consistencyRatio = preferences[KEY_CONSISTENCY] ?: DEFAULT_CONFIG.consistencyRatio,
                swipeCooldownMs = preferences[KEY_COOLDOWN] ?: DEFAULT_CONFIG.swipeCooldownMs,
                fistConfidence = preferences[KEY_FIST_CONFIDENCE] ?: DEFAULT_CONFIG.fistConfidence,
                fistDwellMs = preferences[KEY_DWELL] ?: DEFAULT_CONFIG.fistDwellMs,
                stillnessTolerance = preferences[KEY_STILLNESS] ?: DEFAULT_CONFIG.stillnessTolerance,
                invertVertical = preferences[KEY_INVERT_VERTICAL] ?: false,
                invertHorizontal = preferences[KEY_INVERT_HORIZONTAL] ?: false,
            ).clamped()
        }

    val defaultMapping: Flow<GestureMapping> =
        store.data.map { preferences ->
            preferences[KEY_DEFAULT_MAPPING]?.let(::decodeMapping) ?: GestureMapping.default()
        }

    val overrides: Flow<Map<String, GestureMapping>> =
        store.data.map { preferences ->
            decodeOverrides(preferences[KEY_OVERRIDES] ?: emptySet())
        }

    suspend fun setEnabled(packageName: String, enabled: Boolean) {
        store.edit { preferences ->
            val current = preferences[KEY_ENABLED] ?: emptySet()
            preferences[KEY_ENABLED] =
                if (enabled) current + packageName else current - packageName
        }
    }

    suspend fun setDefaultMapping(mapping: GestureMapping) {
        store.edit { it[KEY_DEFAULT_MAPPING] = encodeMapping(mapping) }
    }

    suspend fun setOverrideAction(packageName: String, event: GestureEvent, action: GestureAction) {
        store.edit { preferences ->
            val entries = (preferences[KEY_OVERRIDES] ?: emptySet())
                .filterNot { it.startsWith("$packageName|$event=") }
                .toSet() + "$packageName|$event=$action"
            preferences[KEY_OVERRIDES] = entries
        }
    }

    suspend fun clearOverrides(packageName: String) {
        store.edit { preferences ->
            preferences[KEY_OVERRIDES] =
                (preferences[KEY_OVERRIDES] ?: emptySet()).filterNot { it.startsWith("$packageName|") }.toSet()
        }
    }

    suspend fun applyPreset(packageName: String, preset: GestureMapping) {
        store.edit { preferences ->
            val entries = (preferences[KEY_OVERRIDES] ?: emptySet())
                .filterNot { it.startsWith("$packageName|") }
                .toSet() + preset.actions.map { (event, action) -> "$packageName|$event=$action" }
            preferences[KEY_OVERRIDES] = entries
        }
    }

    suspend fun setConfig(config: GestureConfig) {
        val clamped = config.clamped()
        store.edit { preferences ->
            preferences[KEY_THRESHOLD] = clamped.swipeThresholdRatio
            preferences[KEY_CONSISTENCY] = clamped.consistencyRatio
            preferences[KEY_COOLDOWN] = clamped.swipeCooldownMs
            preferences[KEY_FIST_CONFIDENCE] = clamped.fistConfidence
            preferences[KEY_DWELL] = clamped.fistDwellMs
            preferences[KEY_STILLNESS] = clamped.stillnessTolerance
            preferences[KEY_INVERT_VERTICAL] = clamped.invertVertical
            preferences[KEY_INVERT_HORIZONTAL] = clamped.invertHorizontal
        }
    }

    companion object {
        private val DEFAULT_CONFIG = GestureConfig()

        private val KEY_ENABLED = stringSetPreferencesKey("enabled_packages")
        private val KEY_OVERRIDES = stringSetPreferencesKey("overrides")
        private val KEY_DEFAULT_MAPPING = stringSetPreferencesKey("default_mapping")
        private val KEY_THRESHOLD = floatPreferencesKey("swipe_threshold")
        private val KEY_CONSISTENCY = floatPreferencesKey("consistency")
        private val KEY_COOLDOWN = longPreferencesKey("cooldown_ms")
        private val KEY_FIST_CONFIDENCE = floatPreferencesKey("fist_confidence")
        private val KEY_DWELL = longPreferencesKey("fist_dwell_ms")
        private val KEY_STILLNESS = floatPreferencesKey("stillness")
        private val KEY_INVERT_VERTICAL = booleanPreferencesKey("invert_vertical")
        private val KEY_INVERT_HORIZONTAL = booleanPreferencesKey("invert_horizontal")

        @Volatile
        private var instance: SettingsRepository? = null

        fun get(context: Context): SettingsRepository =
            instance ?: synchronized(this) {
                instance ?: SettingsRepository(context).also { instance = it }
            }

        private fun encodeMapping(mapping: GestureMapping): Set<String> =
            mapping.actions.map { (event, action) -> "$event=$action" }.toSet()

        private fun decodeMapping(entries: Set<String>): GestureMapping =
            GestureMapping(
                entries.mapNotNull { entry ->
                    val parts = entry.split('=', limit = 2)
                    if (parts.size != 2) return@mapNotNull null
                    val event = runCatching { GestureEvent.valueOf(parts[0]) }.getOrNull()
                    val action = runCatching { GestureAction.valueOf(parts[1]) }.getOrNull()
                    if (event == null || action == null) null else event to action
                }.toMap(),
            )

        private fun decodeOverrides(entries: Set<String>): Map<String, GestureMapping> {
            val byPackage = mutableMapOf<String, MutableMap<GestureEvent, GestureAction>>()
            for (entry in entries) {
                val separator = entry.indexOf('|')
                if (separator <= 0) continue
                val packageName = entry.substring(0, separator)
                val mapping = decodeMapping(setOf(entry.substring(separator + 1)))
                byPackage.getOrPut(packageName) { mutableMapOf() }.putAll(mapping.actions)
            }
            return byPackage.mapValues { (_, actions) -> GestureMapping(actions) }
        }
    }
}
