package com.aperturelab.gesturecontrol.inference

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class MonotonicTimestampGuardTest {

    @Test
    fun `increasing timestamps pass through`() {
        val guard = MonotonicTimestampGuard()

        assertThat(guard.next(100L)).isEqualTo(100L)
        assertThat(guard.next(150L)).isEqualTo(150L)
    }

    @Test
    fun `equal timestamp is bumped`() {
        val guard = MonotonicTimestampGuard()

        guard.next(100L)

        assertThat(guard.next(100L)).isEqualTo(101L)
    }

    @Test
    fun `decreasing timestamp is bumped`() {
        val guard = MonotonicTimestampGuard()

        guard.next(100L)

        assertThat(guard.next(90L)).isEqualTo(101L)
    }

    @Test
    fun `sequence is always strictly increasing`() {
        val guard = MonotonicTimestampGuard()
        val candidates = listOf(10L, 10L, 5L, 12L, 12L, 11L, 100L)
        val emitted = candidates.map(guard::next)

        for (index in 1 until emitted.size) {
            assertThat(emitted[index]).isGreaterThan(emitted[index - 1])
        }
    }

    @Test
    fun `reset starts a fresh sequence`() {
        val guard = MonotonicTimestampGuard()

        guard.next(1_000L)
        guard.reset()

        assertThat(guard.next(10L)).isEqualTo(10L)
    }
}
