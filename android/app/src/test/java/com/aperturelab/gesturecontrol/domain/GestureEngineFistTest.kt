package com.aperturelab.gesturecontrol.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class GestureEngineFistTest {

    private val engine = GestureEngine(GestureConfig())

    @Test
    fun `stationary fist fires once after the dwell`() {
        val events = engine.hold(timestamps = listOf(0L, 200L, 400L, 600L, 800L, 1_000L))

        assertThat(events).containsExactly(GestureEvent.FIST_HOLD)
    }

    @Test
    fun `fist formed while the hand is moving does not fire`() {
        val events = mutableListOf<GestureEvent>()
        var y = 0.60f
        for (index in 0..20) {
            val event = engine.onFrame(
                frame(timestampMs = index * 100L, x = 0.5f, y = y, label = FIST, score = 0.95f),
            )
            event?.let(events::add)
            // 0.03 per frame keeps resetting the stillness anchor.
            y -= 0.03f
        }

        assertThat(events).doesNotContain(GestureEvent.FIST_HOLD)
    }

    @Test
    fun `no re-fire while the fist stays held`() {
        val first = engine.hold(listOf(0L, 300L, 600L, 900L))
        val second = engine.hold(listOf(1_200L, 1_500L, 2_000L, 2_500L))

        assertThat(first).containsExactly(GestureEvent.FIST_HOLD)
        assertThat(second).isEmpty()
    }

    @Test
    fun `release then re-hold fires again`() {
        val first = engine.hold(listOf(0L, 300L, 600L, 900L))

        // Release: label changes, dwell state resets.
        engine.onFrame(frame(1_000L, 0.5f, 0.5f, label = "Open_Palm", score = 0.9f))

        val second = engine.hold(listOf(1_100L, 1_400L, 1_700L, 2_000L))

        assertThat(first).containsExactly(GestureEvent.FIST_HOLD)
        assertThat(second).containsExactly(GestureEvent.FIST_HOLD)
    }

    @Test
    fun `low confidence fist does not fire`() {
        val events = mutableListOf<GestureEvent>()
        for (index in 0..10) {
            engine.onFrame(frame(index * 100L, 0.5f, 0.5f, label = FIST, score = 0.6f))
                ?.let(events::add)
        }

        assertThat(events).isEmpty()
    }

    @Test
    fun `hand leaving the frame resets the dwell`() {
        val events = mutableListOf<GestureEvent>()
        listOf(0L, 300L, 600L).forEach { timestamp ->
            engine.onFrame(frame(timestamp, 0.5f, 0.5f, label = FIST, score = 0.95f))
                ?.let(events::add)
        }
        engine.onFrame(HandFrame(timestampMs = 700L, landmarks = emptyList()))
        // Re-acquired fist starts a fresh dwell window, so 800 ms after the gap
        // it fires only if the full dwell is observed again.
        listOf(800L, 1_000L, 1_200L).forEach { timestamp ->
            engine.onFrame(frame(timestamp, 0.5f, 0.5f, label = FIST, score = 0.95f))
                ?.let(events::add)
        }

        assertThat(events).isEmpty()
    }

    // ------------------------------------------------------------------ helpers

    /** Feeds stationary fist frames and returns the events emitted. */
    private fun GestureEngine.hold(timestamps: List<Long>): List<GestureEvent> =
        timestamps.mapNotNull { timestamp ->
            onFrame(frame(timestamp, 0.5f, 0.5f, label = FIST, score = 0.95f))
        }

    private fun frame(timestampMs: Long, x: Float, y: Float, label: String?, score: Float): HandFrame =
        HandFrame(
            timestampMs = timestampMs,
            landmarks = List(LANDMARK_COUNT) { index ->
                if (index in PALM_INDICES) Landmark(x, y)
                else Landmark(x + index * 0.001f, y + index * 0.001f)
            },
            gestureLabel = label,
            gestureScore = score,
        )

    private companion object {
        const val FIST = GestureEngine.LABEL_FIST
        const val LANDMARK_COUNT = 21
        val PALM_INDICES = intArrayOf(0, 5, 9, 13, 17)
    }
}
