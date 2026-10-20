package com.aperturelab.gesturecontrol.inference

import android.content.Context
import android.graphics.Bitmap
import com.aperturelab.gesturecontrol.domain.HandFrame
import com.aperturelab.gesturecontrol.domain.Landmark
import com.aperturelab.gesturecontrol.domain.RgbaFrame
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizer
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult

/**
 * Thin wrapper around MediaPipe's gesture recognizer in LIVE_STREAM mode.
 *
 * Guarantees:
 * - at most one inference in flight; frames arriving while busy are dropped
 * - strictly increasing timestamps derived from capture time
 * - the capture timestamp travels with the result, so trajectory math never
 *   sees inference latency
 */
class MediaPipeGestureRecognizer(
    context: Context,
    private val listener: Listener,
) : AutoCloseable {

    interface Listener {
        fun onFrame(frame: HandFrame)
        fun onError(message: String)
    }

    private val gate = InFlightGate()
    private val timestamps = MonotonicTimestampGuard()

    @Volatile
    private var pendingCaptureMs = 0L

    private val recognizer: GestureRecognizer

    init {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath(MODEL_ASSET)
            .build()

        val options = GestureRecognizer.GestureRecognizerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumHands(1)
            .setMinHandDetectionConfidence(0.5f)
            .setMinHandPresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setResultListener { result, _ -> handleResult(result) }
            .setErrorListener { error ->
                gate.release()
                listener.onError("Gesture recognizer error: ${error.message}")
            }
            .build()

        recognizer = GestureRecognizer.createFromOptions(context, options)
    }

    /**
     * Submits an upright frame for recognition.
     *
     * @return false when the frame was dropped because inference is busy or the
     *   call failed; callers may count drops but must not retry.
     */
    fun tryRecognize(frame: RgbaFrame, captureTimestampMs: Long): Boolean {
        if (!gate.tryAcquire()) return false
        return try {
            val timestamp = timestamps.next(captureTimestampMs)
            pendingCaptureMs = captureTimestampMs
            recognizer.recognizeAsync(toMpImage(frame), timestamp)
            true
        } catch (error: RuntimeException) {
            gate.release()
            listener.onError("Failed to submit frame: ${error.message}")
            false
        }
    }

    override fun close() {
        recognizer.close()
    }

    private fun handleResult(result: GestureRecognizerResult) {
        try {
            val captureTimestamp = pendingCaptureMs
            val landmarks = result.landmarks().firstOrNull()
                ?.map { Landmark(it.x(), it.y(), it.z()) }
                .orEmpty()
            val gesture = result.gestures().firstOrNull()?.firstOrNull()
            listener.onFrame(
                HandFrame(
                    timestampMs = captureTimestamp,
                    landmarks = landmarks,
                    gestureLabel = gesture?.categoryName(),
                    gestureScore = gesture?.score() ?: 0f,
                ),
            )
        } finally {
            gate.release()
        }
    }

    private fun toMpImage(frame: RgbaFrame): MPImage {
        val pixelCount = frame.width * frame.height
        val colors = IntArray(pixelCount)
        val bytes = frame.pixels
        var offset = 0
        for (index in 0 until pixelCount) {
            val red = bytes[offset].toInt() and 0xFF
            val green = bytes[offset + 1].toInt() and 0xFF
            val blue = bytes[offset + 2].toInt() and 0xFF
            val alpha = bytes[offset + 3].toInt() and 0xFF
            colors[index] = (alpha shl 24) or (red shl 16) or (green shl 8) or blue
            offset += 4
        }
        val bitmap = Bitmap.createBitmap(frame.width, frame.height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(colors, 0, frame.width, 0, 0, frame.width, frame.height)
        return BitmapImageBuilder(bitmap).build()
    }

    companion object {
        const val MODEL_ASSET = "gesture_recognizer.task"
    }
}
