package com.aperturelab.gesturecontrol.runtime

import kotlinx.coroutines.flow.MutableStateFlow

/** Lightweight, in-memory record of dispatch failures for diagnostics. */
object Diagnostics {
    val lastFailure = MutableStateFlow<String?>(null)

    fun recordFailure(message: String) {
        lastFailure.value = message
    }
}
