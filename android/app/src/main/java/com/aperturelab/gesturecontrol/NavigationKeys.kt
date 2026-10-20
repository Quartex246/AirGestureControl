package com.aperturelab.gesturecontrol

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Main : NavKey

@Serializable data object Permissions : NavKey

@Serializable data object Tuning : NavKey

/** [packageName] null means the global default mapping. */
@Serializable data class Mapping(val packageName: String?) : NavKey
