package com.aperturelab.gesturecontrol.inference

import kotlin.math.max

/**
 * MediaPipe LIVE_STREAM requires strictly increasing timestamps. Camera
 * timestamps are normally monotonic, but two frames can share a millisecond
 * (or a clock can step), so every value is bumped past the previous one.
 *
 * Pure Kotlin; not thread-safe, call from the frame producer thread only.
 */
class MonotonicTimestampGuard {
    private var last = Long.MIN_VALUE

    /** Returns a timestamp strictly greater than every previously returned one. */
    fun next(candidateMs: Long): Long {
        val value = max(last + 1, candidateMs)
        last = value
        return value
    }

    fun reset() {
        last = Long.MIN_VALUE
    }
}
