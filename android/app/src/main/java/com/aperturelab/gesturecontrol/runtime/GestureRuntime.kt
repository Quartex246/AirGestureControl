package com.aperturelab.gesturecontrol.runtime

import com.aperturelab.gesturecontrol.domain.GestureEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-process state shared by the accessibility service (which decides when
 * recognition should run and executes actions) and the camera foreground
 * service (which owns the camera).
 *
 * Both services live in the same process, so a singleton is sufficient and
 * avoids the overhead and races of cross-process communication.
 */
object GestureRuntime {

    /** Current recognition state, observed by the overlay and settings UI. */
    val activation = MutableStateFlow(ActivationState.INACTIVE_NOT_ENABLED)

    /** True while recognition should run for the current foreground application. */
    val cameraRequested = MutableStateFlow(false)

    /** Recognized gestures, consumed by the action dispatcher. */
    val gestureEvents = MutableSharedFlow<GestureEvent>(extraBufferCapacity = 16)

    /** True when the user paused recognition from the overlay or notification. */
    val paused = MutableStateFlow(false)

    /** True while the camera foreground service is running. */
    val armed = MutableStateFlow(false)
}
