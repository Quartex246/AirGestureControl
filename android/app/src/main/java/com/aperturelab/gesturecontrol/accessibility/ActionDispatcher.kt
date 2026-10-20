package com.aperturelab.gesturecontrol.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.aperturelab.gesturecontrol.domain.GestureAction
import com.aperturelab.gesturecontrol.runtime.Diagnostics

/**
 * Executes resolved actions without root.
 *
 * Scrolling prefers the accessibility node path because it is immune to touch
 * cancellation and fling physics. Synthesized swipes are the fallback, and an
 * injected gesture that the user interrupts is never retried.
 */
class ActionDispatcher(private val service: AccessibilityService) {

    fun execute(action: GestureAction) {
        when (action) {
            GestureAction.SCROLL_FORWARD -> scroll(forward = true)
            GestureAction.SCROLL_BACKWARD -> scroll(forward = false)
            GestureAction.BACK -> performGlobal(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK, "back")
            GestureAction.HOME -> performGlobal(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME, "home")
            GestureAction.PAGE_FORWARD -> injectHorizontal(forward = true)
            GestureAction.PAGE_BACKWARD -> injectHorizontal(forward = false)
            GestureAction.NONE -> Unit
        }
    }

    private fun performGlobal(action: Int, name: String) {
        val performed = service.performGlobalAction(action)
        Log.i(TAG, "global action $name: performed=$performed")
        if (!performed) record("global action $name rejected")
    }

    // ---------------------------------------------------------------- scrolling

    private fun scroll(forward: Boolean) {
        val scrollable = findScrollableNode(service.rootInActiveWindow)
        if (scrollable != null) {
            val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            val performed = runCatching { scrollable.performAction(action) }.getOrDefault(false)
            Log.i(TAG, "node scroll ${if (forward) "forward" else "backward"}: performed=$performed")
            if (!performed) {
                // Content is exhausted in that direction: do not inject a swipe.
                record("scrollable node present but could not scroll ${if (forward) "forward" else "backward"}")
            }
            return
        }
        Log.i(TAG, "no scrollable node: injecting vertical swipe ${if (forward) "up" else "down"}")
        injectVertical(forward)
    }

    private fun findScrollableNode(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.addLast(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val node = queue.removeFirst()
            visited++
            if (node.isScrollable) return node
            for (index in 0 until node.childCount) {
                val child = runCatching { node.getChild(index) }.getOrNull() ?: continue
                queue.addLast(child)
            }
        }
        return null
    }

    // ---------------------------------------------------------------- injection

    private fun injectVertical(forward: Boolean) {
        val bounds = screenBounds()
        val x = bounds.exactCenterX()
        val startY = if (forward) bounds.top + bounds.height() * 0.68f else bounds.top + bounds.height() * 0.32f
        val endY = if (forward) bounds.top + bounds.height() * 0.32f else bounds.top + bounds.height() * 0.68f
        inject(x, startY, x, endY)
    }

    private fun injectHorizontal(forward: Boolean) {
        val bounds = screenBounds()
        val y = bounds.exactCenterY()
        // Stay clear of the platform's edge gesture zones.
        val right = bounds.left + bounds.width() * 0.80f
        val left = bounds.left + bounds.width() * 0.20f
        if (forward) {
            Log.i(TAG, "injecting horizontal page forward")
            inject(right, y, left, y)
        } else {
            Log.i(TAG, "injecting horizontal page backward")
            inject(left, y, right, y)
        }
    }

    private fun inject(startX: Float, startY: Float, endX: Float, endY: Float) {
        Log.i(TAG, "inject gesture (%.0f,%.0f) -> (%.0f,%.0f)".format(startX, startY, endX, endY))
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, SWIPE_DURATION_MS))
            .build()
        val dispatched = runCatching {
            service.dispatchGesture(
                gesture,
                object : AccessibilityService.GestureResultCallback() {
                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        // Any user touch cancels the gesture; never retry.
                        record("injected swipe cancelled by user input")
                    }
                },
                null,
            )
        }.getOrDefault(false)
        if (!dispatched) record("injected swipe rejected by the system")
    }

    private fun screenBounds(): Rect {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowManager = service.getSystemService(android.view.WindowManager::class.java)
            windowManager?.currentWindowMetrics?.bounds?.let { return it }
        }
        val metrics = service.resources.displayMetrics
        return Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
    }

    private fun record(message: String) {
        Log.w(TAG, message)
        Diagnostics.recordFailure(message)
    }

    private companion object {
        const val TAG = "GestureDispatch"
        const val MAX_NODES = 400
        const val SWIPE_DURATION_MS = 220L
    }
}
