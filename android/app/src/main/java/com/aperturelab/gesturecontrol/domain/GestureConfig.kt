package com.aperturelab.gesturecontrol.domain

/**
 * Tuning values shared by the recognition engine. Coordinates are normalized
 * to [0, 1] with the origin at the top-left of the analysis frame, after the
 * front-camera mirror has been applied.
 *
 * All values are clamped to supported ranges by [clamped] so a bad persisted
 * value can never put the engine into an unusable state.
 */
data class GestureConfig(
    /** Accumulated palm displacement, as a fraction of frame height, needed to fire a swipe. */
    val swipeThresholdRatio: Float = 0.12f,
    /** How straight (net / path length) a swipe must be on its dominant axis. */
    val consistencyRatio: Float = 0.6f,
    /** Minimum time between two emitted swipe events. */
    val swipeCooldownMs: Long = 500L,
    /** Classifier confidence needed to consider a fist. */
    val fistConfidence: Float = 0.8f,
    /** How long a fist must be held still before it fires. */
    val fistDwellMs: Long = 800L,
    /** Maximum palm drift, normalized, that still counts as "still". */
    val stillnessTolerance: Float = 0.025f,
    /** Swap up/down mapping at the event level. */
    val invertVertical: Boolean = false,
    /** Swap left/right mapping at the event level. */
    val invertHorizontal: Boolean = false,
) {
    fun clamped(): GestureConfig = copy(
        swipeThresholdRatio = swipeThresholdRatio.coerceIn(MIN_SWIPE_THRESHOLD, MAX_SWIPE_THRESHOLD),
        consistencyRatio = consistencyRatio.coerceIn(MIN_CONSISTENCY, MAX_CONSISTENCY),
        swipeCooldownMs = swipeCooldownMs.coerceIn(MIN_COOLDOWN_MS, MAX_COOLDOWN_MS),
        fistConfidence = fistConfidence.coerceIn(MIN_FIST_CONFIDENCE, MAX_FIST_CONFIDENCE),
        fistDwellMs = fistDwellMs.coerceIn(MIN_DWELL_MS, MAX_DWELL_MS),
        stillnessTolerance = stillnessTolerance.coerceIn(MIN_STILLNESS, MAX_STILLNESS),
    )

    companion object {
        const val MIN_SWIPE_THRESHOLD = 0.04f
        const val MAX_SWIPE_THRESHOLD = 0.30f
        const val MIN_CONSISTENCY = 0.30f
        const val MAX_CONSISTENCY = 0.95f
        const val MIN_COOLDOWN_MS = 100L
        const val MAX_COOLDOWN_MS = 2_000L
        const val MIN_FIST_CONFIDENCE = 0.50f
        const val MAX_FIST_CONFIDENCE = 0.99f
        const val MIN_DWELL_MS = 300L
        const val MAX_DWELL_MS = 3_000L
        const val MIN_STILLNESS = 0.005f
        const val MAX_STILLNESS = 0.10f
    }
}
