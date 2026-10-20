package com.aperturelab.gesturecontrol.domain

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Pure-Kotlin gesture recognizer. It is deliberately unaware of the foreground
 * application: identical landmark sequences always produce identical events.
 *
 * Swipes are detected from the accumulated palm trajectory (wrist plus the four
 * metacarpophalangeal joints) rather than the classifier label, because motion
 * blur makes labels unreliable exactly during a swipe.
 *
 * Static gestures (fist) require classifier confidence, stillness, and dwell,
 * and must be released before they can fire again.
 *
 * Not thread-safe; call from a single thread (the inference thread).
 */
class GestureEngine(config: GestureConfig = GestureConfig()) {

    private var config: GestureConfig = config.clamped()

    // --- swipe candidate state ---
    private var lastPalm: Point? = null
    private var startPalm: Point? = null
    private var sumDx = 0f
    private var sumDy = 0f
    private var pathX = 0f
    private var pathY = 0f
    private var lastSwipeAtMs: Long? = null

    // --- fist state ---
    private var fistAnchor: Point? = null
    private var fistStartMs = 0L
    private var fistFired = false

    fun updateConfig(newConfig: GestureConfig) {
        config = newConfig.clamped()
    }

    /**
     * Consumes one inference frame and returns at most one event.
     *
     * @param frame landmarks are expected in normalized coordinates, X already
     *   mirrored for the front camera (see `FrameConverter`).
     */
    fun onFrame(frame: HandFrame): GestureEvent? {
        val palm = palmCenter(frame.landmarks)
        if (palm == null) {
            resetTrajectory()
            resetFist()
            return null
        }

        val event = detectFist(frame, palm) ?: detectSwipe(frame, palm)
        applyInversion(event)?.let { return it }
        return null
    }

    /** Clears all motion state, e.g. when the camera is unmounted. */
    fun reset() {
        resetTrajectory()
        resetFist()
        lastSwipeAtMs = null
    }

    // ------------------------------------------------------------------ swipes

    private fun detectSwipe(frame: HandFrame, palm: Point): GestureEvent? {
        val previous = lastPalm
        lastPalm = palm
        if (previous == null) {
            startCandidate(palm)
            return null
        }

        val dx = palm.x - previous.x
        val dy = palm.y - previous.y
        sumDx += dx
        sumDy += dy
        pathX += abs(dx)
        pathY += abs(dy)

        val origin = startPalm ?: palm.also { startCandidate(it) }
        // Measure displacement from the candidate origin, not frame to frame.
        val netDx = palm.x - origin.x
        val netDy = palm.y - origin.y

        val horizontal = abs(netDx) >= abs(netDy)
        val dominant = if (horizontal) abs(netDx) else abs(netDy)
        val path = if (horizontal) pathX else pathY
        val straightness = if (path < MIN_PATH_EPSILON) 1f else dominant / path

        val threshold = config.swipeThresholdRatio
        if (dominant < threshold) {
            // Still short of a swipe. If the hand has reversed direction hard,
            // restart the candidate so a jittery back-and-forth cannot accumulate.
            if (dominant < max(abs(sumDx), abs(sumDy)) * 0.25f) startCandidate(palm)
            return null
        }

        // A swipe longer than the threshold but not straight enough is jitter.
        if (straightness < config.consistencyRatio) {
            startCandidate(palm)
            return null
        }

        val lastSwipe = lastSwipeAtMs
        if (lastSwipe != null && frame.timestampMs - lastSwipe < config.swipeCooldownMs) {
            startCandidate(palm)
            return null
        }

        val direction = when {
            horizontal && netDx > 0f -> GestureEvent.SWIPE_RIGHT
            horizontal -> GestureEvent.SWIPE_LEFT
            netDy < 0f -> GestureEvent.SWIPE_UP
            else -> GestureEvent.SWIPE_DOWN
        }

        lastSwipeAtMs = frame.timestampMs
        startCandidate(palm)
        return direction
    }

    private fun startCandidate(palm: Point) {
        startPalm = palm
        sumDx = 0f
        sumDy = 0f
        pathX = 0f
        pathY = 0f
    }

    private fun resetTrajectory() {
        lastPalm = null
        startPalm = null
        sumDx = 0f
        sumDy = 0f
        pathX = 0f
        pathY = 0f
    }

    // -------------------------------------------------------------------- fist

    private fun detectFist(frame: HandFrame, palm: Point): GestureEvent? {
        val isFist = frame.gestureLabel == LABEL_FIST && frame.gestureScore >= config.fistConfidence
        if (!isFist) {
            resetFist()
            return null
        }

        val anchor = fistAnchor
        if (anchor == null) {
            fistAnchor = palm
            fistStartMs = frame.timestampMs
            fistFired = false
            return null
        }

        // Any meaningful drift restarts the dwell window, so a fist formed or
        // carried through a swipe can never count as a held fist.
        if (distance(palm, anchor) > config.stillnessTolerance) {
            fistAnchor = palm
            fistStartMs = frame.timestampMs
            fistFired = false
            return null
        }

        if (!fistFired && frame.timestampMs - fistStartMs >= config.fistDwellMs) {
            fistFired = true
            return GestureEvent.FIST_HOLD
        }
        return null
    }

    private fun resetFist() {
        fistAnchor = null
        fistStartMs = 0L
        fistFired = false
    }

    // ------------------------------------------------------------------ shared

    private fun applyInversion(event: GestureEvent?): GestureEvent? = when (event) {
        null -> null
        GestureEvent.SWIPE_UP -> if (config.invertVertical) GestureEvent.SWIPE_DOWN else event
        GestureEvent.SWIPE_DOWN -> if (config.invertVertical) GestureEvent.SWIPE_UP else event
        GestureEvent.SWIPE_LEFT -> if (config.invertHorizontal) GestureEvent.SWIPE_RIGHT else event
        GestureEvent.SWIPE_RIGHT -> if (config.invertHorizontal) GestureEvent.SWIPE_LEFT else event
        GestureEvent.FIST_HOLD -> event
    }

    private fun palmCenter(landmarks: List<Landmark>): Point? {
        if (landmarks.size <= PALM_INDICES.max()) return null
        var x = 0f
        var y = 0f
        for (index in PALM_INDICES) {
            x += landmarks[index].x
            y += landmarks[index].y
        }
        val count = PALM_INDICES.size
        return Point(x / count, y / count)
    }

    private fun distance(a: Point, b: Point): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }

    private data class Point(val x: Float, val y: Float)

    companion object {
        const val LABEL_FIST = "Closed_Fist"

        /** Wrist plus index/middle/ring/pinky metacarpophalangeal joints. */
        private val PALM_INDICES = intArrayOf(0, 5, 9, 13, 17)

        private const val MIN_PATH_EPSILON = 1e-4f
    }
}
