package com.aperturelab.gesturecontrol.inference

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Ensures at most one inference is in flight. A frame that arrives while the
 * recognizer is busy is dropped rather than queued, which keeps latency from
 * growing under load.
 */
class InFlightGate {
    private val busy = AtomicBoolean(false)

    /** Returns true when the caller acquired the single slot. */
    fun tryAcquire(): Boolean = busy.compareAndSet(false, true)

    fun release() {
        busy.set(false)
    }

    val isBusy: Boolean
        get() = busy.get()
}
