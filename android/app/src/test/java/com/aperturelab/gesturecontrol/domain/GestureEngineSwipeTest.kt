package com.aperturelab.gesturecontrol.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class GestureEngineSwipeTest {

    private val engine = GestureEngine(GestureConfig())

    @Test
    fun `upward palm motion emits SWIPE_UP`() {
        val events = engine.feed(startX = 0.5f, startY = 0.7f, dx = 0f, dy = -0.03f, frames = 6)

        assertThat(events).containsExactly(GestureEvent.SWIPE_UP)
    }

    @Test
    fun `downward palm motion emits SWIPE_DOWN`() {
        val events = engine.feed(startX = 0.5f, startY = 0.3f, dx = 0f, dy = 0.03f, frames = 6)

        assertThat(events).containsExactly(GestureEvent.SWIPE_DOWN)
    }

    @Test
    fun `rightward palm motion emits SWIPE_RIGHT`() {
        val events = engine.feed(startX = 0.3f, startY = 0.5f, dx = 0.03f, dy = 0f, frames = 6)

        assertThat(events).containsExactly(GestureEvent.SWIPE_RIGHT)
    }

    @Test
    fun `leftward palm motion emits SWIPE_LEFT`() {
        val events = engine.feed(startX = 0.7f, startY = 0.5f, dx = -0.03f, dy = 0f, frames = 6)

        assertThat(events).containsExactly(GestureEvent.SWIPE_LEFT)
    }

    @Test
    fun `diagonal motion with larger horizontal travel is horizontal`() {
        val events = engine.feed(startX = 0.3f, startY = 0.3f, dx = 0.03f, dy = 0.025f, frames = 6)

        assertThat(events).containsExactly(GestureEvent.SWIPE_RIGHT)
    }

    @Test
    fun `jitter below the threshold emits nothing`() {
        val events = engine.feed(startX = 0.5f, startY = 0.5f, dx = 0.005f, dy = 0f, frames = 10)

        assertThat(events).isEmpty()
    }

    @Test
    fun `back and forth motion that nets a swipe but is not straight is rejected`() {
        val frames = listOf(
            0.50f, 0.58f, 0.50f, 0.60f, 0.52f, 0.62f, 0.54f, 0.64f,
        ).mapIndexed { index, x ->
            frame(timestampMs = index * 50L, x = x, y = 0.5f)
        }

        val events = frames.mapNotNull(engine::onFrame)

        assertThat(events).isEmpty()
    }

    @Test
    fun `cooldown suppresses duplicate swipes from one continuous motion`() {
        val frames = listOf(
            frame(0L, 0.5f, 0.70f),
            frame(50L, 0.5f, 0.55f),
            frame(100L, 0.5f, 0.40f), // fires
            frame(150L, 0.5f, 0.25f), // within cooldown
            frame(200L, 0.5f, 0.10f), // within cooldown
        )

        val events = frames.mapNotNull(engine::onFrame)

        assertThat(events).containsExactly(GestureEvent.SWIPE_UP)
    }

    @Test
    fun `swipe fires again once the cooldown has elapsed`() {
        val first = listOf(
            frame(0L, 0.5f, 0.70f),
            frame(50L, 0.5f, 0.55f),
            frame(100L, 0.5f, 0.40f),
        ).mapNotNull(engine::onFrame)

        engine.reset()

        val second = listOf(
            frame(1_000L, 0.5f, 0.70f),
            frame(1_050L, 0.5f, 0.55f),
            frame(1_100L, 0.5f, 0.40f),
        ).mapNotNull(engine::onFrame)

        assertThat(first).containsExactly(GestureEvent.SWIPE_UP)
        assertThat(second).containsExactly(GestureEvent.SWIPE_UP)
    }

    @Test
    fun `missing hand resets the trajectory`() {
        // 0.70 -> 0.62 is below the 0.12 threshold, so nothing fires.
        val first = listOf(
            frame(0L, 0.5f, 0.70f),
            frame(50L, 0.5f, 0.62f),
        ).mapNotNull(engine::onFrame)

        engine.onFrame(HandFrame(timestampMs = 100L, landmarks = emptyList()))

        // After a reset the candidate restarts at 0.62, so this 0.08 move is
        // below the threshold. Without the reset it would accumulate from 0.70
        // and wrongly fire.
        val second = listOf(
            frame(150L, 0.5f, 0.62f),
            frame(200L, 0.5f, 0.54f),
        ).mapNotNull(engine::onFrame)

        assertThat(first).isEmpty()
        assertThat(second).isEmpty()
    }

    @Test
    fun `inverted vertical config swaps up and down`() {
        val inverted = GestureEngine(GestureConfig(invertVertical = true))

        val events = inverted.feed(startX = 0.5f, startY = 0.7f, dx = 0f, dy = -0.03f, frames = 6)

        assertThat(events).containsExactly(GestureEvent.SWIPE_DOWN)
    }

    @Test
    fun `identical sequences produce identical events`() {
        val first = GestureEngine()
        val second = GestureEngine()
        val sequence = listOf(
            frame(0L, 0.5f, 0.70f),
            frame(50L, 0.5f, 0.55f),
            frame(100L, 0.5f, 0.40f),
        )

        val firstEvents = sequence.mapNotNull(first::onFrame)
        val secondEvents = sequence.mapNotNull(second::onFrame)

        assertThat(firstEvents).isEqualTo(secondEvents)
        assertThat(firstEvents).containsExactly(GestureEvent.SWIPE_UP)
    }

    // ------------------------------------------------------------------ helpers

    private fun GestureEngine.feed(
        startX: Float,
        startY: Float,
        dx: Float,
        dy: Float,
        frames: Int,
    ): List<GestureEvent> {
        var x = startX
        var y = startY
        val events = mutableListOf<GestureEvent>()
        for (index in 0 until frames) {
            onFrame(frame(index * 50L, x, y))?.let(events::add)
            x += dx
            y += dy
        }
        return events
    }

    private fun frame(timestampMs: Long, x: Float, y: Float): HandFrame =
        HandFrame(timestampMs = timestampMs, landmarks = landmarksAt(x, y))

    private fun landmarksAt(x: Float, y: Float): List<Landmark> =
        List(LANDMARK_COUNT) { index ->
            if (index in PALM_INDICES) Landmark(x, y) else Landmark(x + index * 0.001f, y + index * 0.001f)
        }

    private companion object {
        const val LANDMARK_COUNT = 21
        val PALM_INDICES = intArrayOf(0, 5, 9, 13, 17)
    }
}
