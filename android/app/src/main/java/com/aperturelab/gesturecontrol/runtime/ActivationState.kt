package com.aperturelab.gesturecontrol.runtime

/** What the recognition pipeline is doing right now, shown in the status surface. */
enum class ActivationState {
    /** The foreground application is not enabled (or nothing is enabled). */
    INACTIVE_NOT_ENABLED,

    /** Camera is mounted but recognition has not produced a result yet. */
    WARMING,

    /** Recognition is running and gestures are being acted on. */
    ACTIVE,

    /** The user paused recognition. */
    PAUSED,

    /** The camera could not be used (preempted, permission missing, error). */
    UNAVAILABLE,
}
