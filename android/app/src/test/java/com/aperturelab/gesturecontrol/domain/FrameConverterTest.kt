package com.aperturelab.gesturecontrol.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class FrameConverterTest {

    @Test
    fun `row stride padding is dropped`() {
        val width = 2
        val height = 2
        val rowStride = width * 4 + 4 // 4 bytes of padding per row
        val plane = ByteArray(rowStride * height)
        // Pixel (x, y) encodes its index in the red channel; padding is 0xFF.
        for (y in 0 until height) {
            for (x in 0 until width) {
                writePixel(plane, y * rowStride + x * 4, red = (y * width + x).toByte())
            }
            for (padding in 0 until 4) {
                plane[y * rowStride + width * 4 + padding] = 0xFF.toByte()
            }
        }

        val result = FrameConverter.convert(
            RawFrame(width, height, rowStride, pixelStride = 4, rotationDegrees = 0, plane = plane),
            mirrorHorizontally = false,
        )

        assertThat(result.width).isEqualTo(2)
        assertThat(result.height).isEqualTo(2)
        assertThat(result.pixels.size).isEqualTo(width * height * 4)
        assertThat(redAt(result, 0)).isEqualTo(0)
        assertThat(redAt(result, 1)).isEqualTo(1)
        assertThat(redAt(result, 2)).isEqualTo(2)
        assertThat(redAt(result, 3)).isEqualTo(3)
    }

    @Test
    fun `rotation 90 moves the top-left corner to the top-right`() {
        val result = convert(width = 3, height = 3, rotationDegrees = 90)

        assertThat(result.width).isEqualTo(3)
        assertThat(result.height).isEqualTo(3)
        assertThat(colorAt(result, 2, 0)).isEqualTo(0) // src(0,0)
        assertThat(colorAt(result, 2, 1)).isEqualTo(1) // src(1,0)
        assertThat(colorAt(result, 2, 2)).isEqualTo(2) // src(2,0)
        assertThat(colorAt(result, 0, 0)).isEqualTo(6) // src(0,2)
    }

    @Test
    fun `rotation 180 reverses both axes`() {
        val result = convert(width = 3, height = 3, rotationDegrees = 180)

        assertThat(result.width).isEqualTo(3)
        assertThat(result.height).isEqualTo(3)
        assertThat(colorAt(result, 2, 2)).isEqualTo(0) // src(0,0)
        assertThat(colorAt(result, 0, 0)).isEqualTo(8) // src(2,2)
    }

    @Test
    fun `rotation 270 moves the top-left corner to the bottom-left`() {
        val result = convert(width = 3, height = 3, rotationDegrees = 270)

        assertThat(result.width).isEqualTo(3)
        assertThat(result.height).isEqualTo(3)
        assertThat(colorAt(result, 0, 2)).isEqualTo(0) // src(0,0)
        assertThat(colorAt(result, 2, 0)).isEqualTo(8) // src(2,2)
    }

    @Test
    fun `rotation swaps width and height for quarter turns`() {
        val result = convert(width = 3, height = 2, rotationDegrees = 90)

        assertThat(result.width).isEqualTo(2)
        assertThat(result.height).isEqualTo(3)
    }

    @Test
    fun `front camera mirroring flips rows`() {
        val result = convert(width = 3, height = 1, rotationDegrees = 0, mirror = true)

        assertThat(colorAt(result, 0, 0)).isEqualTo(2)
        assertThat(colorAt(result, 1, 0)).isEqualTo(1)
        assertThat(colorAt(result, 2, 0)).isEqualTo(0)
    }

    @Test
    fun `mirroring is applied after rotation`() {
        // After a 90 degree turn src(0,0) sits at the far right; mirroring
        // must bring it back to the far left of the top row.
        val result = convert(width = 3, height = 3, rotationDegrees = 90, mirror = true)

        assertThat(colorAt(result, 0, 0)).isEqualTo(0)
        assertThat(colorAt(result, 0, 2)).isEqualTo(2) // src(2,0) crossed to the left
    }

    @Test
    fun `unsupported rotation is rejected`() {
        val failure = runCatching { convert(width = 2, height = 1, rotationDegrees = 45) }

        assertThat(failure.isFailure).isTrue()
    }

    // ------------------------------------------------------------------ helpers

    private fun convert(
        width: Int,
        height: Int,
        rotationDegrees: Int,
        mirror: Boolean = false,
    ): RgbaFrame {
        val plane = ByteArray(width * height * 4)
        for (y in 0 until height) {
            for (x in 0 until width) {
                writePixel(plane, (y * width + x) * 4, red = (y * width + x).toByte())
            }
        }
        return FrameConverter.convert(
            RawFrame(width, height, width * 4, pixelStride = 4, rotationDegrees = rotationDegrees, plane = plane),
            mirrorHorizontally = mirror,
        )
    }

    private fun writePixel(buffer: ByteArray, offset: Int, red: Byte) {
        buffer[offset] = red
        buffer[offset + 1] = 0
        buffer[offset + 2] = 0
        buffer[offset + 3] = 0xFF.toByte()
    }

    private fun redAt(frame: RgbaFrame, pixelIndex: Int): Int =
        frame.pixels[pixelIndex * 4].toInt() and 0xFF

    private fun colorAt(frame: RgbaFrame, x: Int, y: Int): Int =
        redAt(frame, y * frame.width + x)
}
