package com.aperturelab.gesturecontrol.domain

/**
 * A partial gesture-to-action mapping. Missing entries fall back to the global
 * default; [GestureAction.NONE] is an explicit "do nothing" entry.
 */
data class GestureMapping(
    val actions: Map<GestureEvent, GestureAction> = emptyMap(),
) {
    operator fun get(event: GestureEvent): GestureAction? = actions[event]

    companion object {
        /** Up/down scroll, horizontal swipes go back, a held fist goes home. */
        fun default(): GestureMapping = GestureMapping(
            mapOf(
                GestureEvent.SWIPE_UP to GestureAction.SCROLL_FORWARD,
                GestureEvent.SWIPE_DOWN to GestureAction.SCROLL_BACKWARD,
                GestureEvent.SWIPE_LEFT to GestureAction.BACK,
                GestureEvent.SWIPE_RIGHT to GestureAction.BACK,
                GestureEvent.FIST_HOLD to GestureAction.HOME,
            ),
        )
    }
}

/**
 * Resolves a recognized gesture into an action for a foreground package.
 * Per-application overrides win per gesture; anything they omit falls back to
 * the global default, and anything neither defines resolves to
 * [GestureAction.NONE].
 */
class ActionResolver(
    private val defaultMapping: GestureMapping = GestureMapping.default(),
    private val overrides: Map<String, GestureMapping> = emptyMap(),
) {
    fun resolve(packageName: String?, event: GestureEvent): GestureAction {
        if (packageName != null) {
            overrides[packageName]?.get(event)?.let { return it }
        }
        return defaultMapping[event] ?: GestureAction.NONE
    }

    fun effectiveMapping(packageName: String?): GestureMapping {
        val merged = LinkedHashMap<GestureEvent, GestureAction>()
        merged.putAll(defaultMapping.actions)
        if (packageName != null) {
            overrides[packageName]?.actions?.let(merged::putAll)
        }
        return GestureMapping(merged)
    }

    fun hasOverride(packageName: String, event: GestureEvent): Boolean =
        overrides[packageName]?.actions?.containsKey(event) == true
}
