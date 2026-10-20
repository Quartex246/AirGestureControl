package com.aperturelab.gesturecontrol.inference

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class InFlightGateTest {

    @Test
    fun `second acquire fails while busy`() {
        val gate = InFlightGate()

        assertThat(gate.tryAcquire()).isTrue()
        assertThat(gate.tryAcquire()).isFalse()
    }

    @Test
    fun `release allows a new acquire`() {
        val gate = InFlightGate()

        gate.tryAcquire()
        gate.release()

        assertThat(gate.tryAcquire()).isTrue()
        assertThat(gate.isBusy).isTrue()
    }

    @Test
    fun `release without acquire is harmless`() {
        val gate = InFlightGate()

        gate.release()

        assertThat(gate.tryAcquire()).isTrue()
    }
}
