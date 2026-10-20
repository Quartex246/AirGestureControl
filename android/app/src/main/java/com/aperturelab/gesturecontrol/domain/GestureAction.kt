package com.aperturelab.gesturecontrol.domain

/**
 * Actions the dispatcher can execute. [NONE] is an explicit "do nothing"
 * mapping, distinct from a missing mapping (which falls back to the default).
 */
enum class GestureAction {
    SCROLL_FORWARD,
    SCROLL_BACKWARD,
    BACK,
    HOME,
    PAGE_FORWARD,
    PAGE_BACKWARD,
    NONE,
}
