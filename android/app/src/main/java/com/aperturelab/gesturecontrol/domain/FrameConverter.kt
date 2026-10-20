package com.aperturelab.gesturecontrol.domain

/**
 * A camera frame copied out of an `ImageProxy` in the cheapest possible form:
 * raw bytes plus the metadata needed to interpret them.
 */
class RawFrame(
    val width: Int,
    val height: Int,
    val rowStride: Int,
    val pixelStride: Int,
    val rotationDegrees: Int,
    val plane: ByteArray,
)

/** An upright, tightly packed RGBA_8888 buffer. */
class RgbaFrame(
    val width: Int,
    val height: Int,
    val pixels: ByteArray,
)

/**
 * Converts camera planes into tightly packed RGBA buffers.
 *
 * Pure Kotlin so the pixel math (stride padding, rotation, front-camera
 * mirroring) is unit-testable without an emulator. The Android layer only has
 * to hand over the plane bytes and turn the result into a `Bitmap`.
 */
object FrameConverter {

    private const val BYTES_PER_PIXEL = 4

    /**
     * Copies [frame]'s plane into a compact buffer, rotates it upright by
     * [RawFrame.rotationDegrees], then mirrors it horizontally when
     * [mirrorHorizontally] is set (front camera).
     */
    fun convert(frame: RawFrame, mirrorHorizontally: Boolean): RgbaFrame {
        require(frame.rotationDegrees % 90 == 0) {
            "Unsupported rotation: ${frame.rotationDegrees}"
        }
        val tight = copyTightly(frame)
        val rotated = rotateClockwise(tight, frame.rotationDegrees % 360)
        return if (mirrorHorizontally) mirrorX(rotated) else rotated
    }

    private fun copyTightly(frame: RawFrame): RgbaFrame {
        require(frame.width > 0 && frame.height > 0) { "Empty frame" }
        require(frame.pixelStride >= BYTES_PER_PIXEL) { "Unsupported pixel stride: ${frame.pixelStride}" }
        require(frame.rowStride >= frame.width * frame.pixelStride) { "Row stride too small" }
        require(frame.plane.size >= frame.rowStride * frame.height) { "Plane buffer too small" }

        val pixels = ByteArray(frame.width * frame.height * BYTES_PER_PIXEL)
        var destination = 0
        for (row in 0 until frame.height) {
            val rowStart = row * frame.rowStride
            for (column in 0 until frame.width) {
                val source = rowStart + column * frame.pixelStride
                pixels[destination++] = frame.plane[source]
                pixels[destination++] = frame.plane[source + 1]
                pixels[destination++] = frame.plane[source + 2]
                pixels[destination++] = frame.plane[source + 3]
            }
        }
        return RgbaFrame(frame.width, frame.height, pixels)
    }

    private fun rotateClockwise(frame: RgbaFrame, degrees: Int): RgbaFrame {
        if (degrees == 0) return frame
        val width = frame.width
        val height = frame.height
        val source = frame.pixels

        val (outputWidth, outputHeight) = when (degrees) {
            90, 270 -> height to width
            180 -> width to height
            else -> error("Unsupported rotation: $degrees")
        }
        val output = ByteArray(outputWidth * outputHeight * BYTES_PER_PIXEL)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val (destinationX, destinationY) = when (degrees) {
                    90 -> (height - 1 - y) to x
                    180 -> (width - 1 - x) to (height - 1 - y)
                    else -> y to (width - 1 - x) // 270
                }
                val sourceIndex = (y * width + x) * BYTES_PER_PIXEL
                val destinationIndex = (destinationY * outputWidth + destinationX) * BYTES_PER_PIXEL
                for (offset in 0 until BYTES_PER_PIXEL) {
                    output[destinationIndex + offset] = source[sourceIndex + offset]
                }
            }
        }
        return RgbaFrame(outputWidth, outputHeight, output)
    }

    private fun mirrorX(frame: RgbaFrame): RgbaFrame {
        val width = frame.width
        val height = frame.height
        val output = frame.pixels.copyOf()
        for (y in 0 until height) {
            val rowStart = y * width
            for (x in 0 until width / 2) {
                val left = (rowStart + x) * BYTES_PER_PIXEL
                val right = (rowStart + width - 1 - x) * BYTES_PER_PIXEL
                for (offset in 0 until BYTES_PER_PIXEL) {
                    val temporary = output[left + offset]
                    output[left + offset] = output[right + offset]
                    output[right + offset] = temporary
                }
            }
        }
        return RgbaFrame(width, height, output)
    }
}
