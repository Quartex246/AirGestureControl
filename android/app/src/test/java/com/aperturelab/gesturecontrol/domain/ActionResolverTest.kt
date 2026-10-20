package com.aperturelab.gesturecontrol.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ActionResolverTest {

    @Test
    fun `default mapping matches the spec`() {
        val resolver = ActionResolver()

        assertThat(resolver.resolve(BROWSER, GestureEvent.SWIPE_UP)).isEqualTo(GestureAction.SCROLL_FORWARD)
        assertThat(resolver.resolve(BROWSER, GestureEvent.SWIPE_DOWN)).isEqualTo(GestureAction.SCROLL_BACKWARD)
        assertThat(resolver.resolve(BROWSER, GestureEvent.SWIPE_LEFT)).isEqualTo(GestureAction.BACK)
        assertThat(resolver.resolve(BROWSER, GestureEvent.SWIPE_RIGHT)).isEqualTo(GestureAction.BACK)
        assertThat(resolver.resolve(BROWSER, GestureEvent.FIST_HOLD)).isEqualTo(GestureAction.HOME)
    }

    @Test
    fun `application override wins for the overridden gesture`() {
        val resolver = ActionResolver(
            overrides = mapOf(
                READER to GestureMapping(mapOf(GestureEvent.SWIPE_LEFT to GestureAction.PAGE_FORWARD)),
            ),
        )

        assertThat(resolver.resolve(READER, GestureEvent.SWIPE_LEFT)).isEqualTo(GestureAction.PAGE_FORWARD)
    }

    @Test
    fun `override affects only its own application`() {
        val resolver = ActionResolver(
            overrides = mapOf(
                READER to GestureMapping(mapOf(GestureEvent.SWIPE_LEFT to GestureAction.PAGE_FORWARD)),
            ),
        )

        assertThat(resolver.resolve(BROWSER, GestureEvent.SWIPE_LEFT)).isEqualTo(GestureAction.BACK)
    }

    @Test
    fun `gestures missing from an override fall back to the default`() {
        val resolver = ActionResolver(
            overrides = mapOf(
                READER to GestureMapping(mapOf(GestureEvent.SWIPE_LEFT to GestureAction.PAGE_FORWARD)),
            ),
        )

        assertThat(resolver.resolve(READER, GestureEvent.SWIPE_UP)).isEqualTo(GestureAction.SCROLL_FORWARD)
        assertThat(resolver.resolve(READER, GestureEvent.FIST_HOLD)).isEqualTo(GestureAction.HOME)
    }

    @Test
    fun `explicit no-action override disables the gesture`() {
        val resolver = ActionResolver(
            overrides = mapOf(
                READER to GestureMapping(mapOf(GestureEvent.SWIPE_RIGHT to GestureAction.NONE)),
            ),
        )

        assertThat(resolver.resolve(READER, GestureEvent.SWIPE_RIGHT)).isEqualTo(GestureAction.NONE)
    }

    @Test
    fun `global default change propagates to applications without an override`() {
        val customDefault = GestureMapping(
            GestureMapping.default().actions + (GestureEvent.SWIPE_LEFT to GestureAction.PAGE_BACKWARD),
        )
        val resolver = ActionResolver(
            defaultMapping = customDefault,
            overrides = mapOf(
                READER to GestureMapping(mapOf(GestureEvent.SWIPE_LEFT to GestureAction.PAGE_FORWARD)),
            ),
        )

        assertThat(resolver.resolve(BROWSER, GestureEvent.SWIPE_LEFT)).isEqualTo(GestureAction.PAGE_BACKWARD)
        assertThat(resolver.resolve(READER, GestureEvent.SWIPE_LEFT)).isEqualTo(GestureAction.PAGE_FORWARD)
    }

    @Test
    fun `unmapped gesture resolves to none`() {
        val resolver = ActionResolver(defaultMapping = GestureMapping())

        assertThat(resolver.resolve(BROWSER, GestureEvent.FIST_HOLD)).isEqualTo(GestureAction.NONE)
    }

    @Test
    fun `null package uses the global default`() {
        val resolver = ActionResolver()

        assertThat(resolver.resolve(null, GestureEvent.SWIPE_UP)).isEqualTo(GestureAction.SCROLL_FORWARD)
    }

    @Test
    fun `effective mapping merges overrides over the default`() {
        val resolver = ActionResolver(
            overrides = mapOf(
                READER to GestureMapping(mapOf(GestureEvent.SWIPE_LEFT to GestureAction.PAGE_FORWARD)),
            ),
        )

        val effective = resolver.effectiveMapping(READER)

        assertThat(effective[GestureEvent.SWIPE_LEFT]).isEqualTo(GestureAction.PAGE_FORWARD)
        assertThat(effective[GestureEvent.SWIPE_UP]).isEqualTo(GestureAction.SCROLL_FORWARD)
        assertThat(resolver.hasOverride(READER, GestureEvent.SWIPE_LEFT)).isTrue()
        assertThat(resolver.hasOverride(READER, GestureEvent.SWIPE_UP)).isFalse()
    }

    private companion object {
        const val BROWSER = "com.example.browser"
        const val READER = "com.example.reader"
    }
}
