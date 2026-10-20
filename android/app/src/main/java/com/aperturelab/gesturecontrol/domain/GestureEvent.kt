package com.aperturelab.gesturecontrol.domain

/**
 * Gestures recognized by the engine. Target-agnostic: the same physical
 * gesture produces the same event regardless of the foreground application.
 */
enum class GestureEvent {
    SWIPE_UP,
    SWIPE_DOWN,
    SWIPE_LEFT,
    SWIPE_RIGHT,
    FIST_HOLD,
}
