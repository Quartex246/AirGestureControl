package com.aperturelab.gesturecontrol.domain

/**
 * One landmark in normalized frame coordinates (0..1, origin top-left, X mirrored
 * for the front camera). Z is relative depth and unused by the current engine.
 */
data class Landmark(
    val x: Float,
    val y: Float,
    val z: Float = 0f,
)

/**
 * A single inference result. [timestampMs] is the frame capture time, not the
 * inference completion time, so trajectory math stays stable across variable
 * inference latency.
 */
data class HandFrame(
    val timestampMs: Long,
    val landmarks: List<Landmark>,
    val gestureLabel: String? = null,
    val gestureScore: Float = 0f,
)
