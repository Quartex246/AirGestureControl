package com.aperturelab.gesturecontrol.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.aperturelab.gesturecontrol.domain.GestureEvent
import com.aperturelab.gesturecontrol.domain.label
import com.aperturelab.gesturecontrol.runtime.ActivationState
import com.aperturelab.gesturecontrol.runtime.GestureRuntime

/**
 * Small always-on-top badge that reports what recognition is doing and doubles
 * as the pause control.
 *
 * The window is exactly as large as the badge, so every touch outside it reaches
 * the application underneath; the badge itself is the pause control.
 */
object StatusOverlay {

    private val main = Handler(Looper.getMainLooper())

    private var context: Context? = null
    private var manager: WindowManager? = null
    private var root: LinearLayout? = null
    private var dot: TextView? = null
    private var label: TextView? = null
    private var feedbackJob: Runnable? = null

    private var state: ActivationState = ActivationState.INACTIVE_NOT_ENABLED
    private var paused = false

    val isShowing: Boolean get() = root != null

    fun attach(context: Context) {
        this.context = context.applicationContext
        this.manager = context.getSystemService(WindowManager::class.java)
        show()
    }

    fun detach() {
        feedbackJob?.let(main::removeCallbacks)
        feedbackJob = null
        root?.let { view -> runCatching { manager?.removeView(view) } }
        root = null
        dot = null
        label = null
        context = null
        manager = null
    }

    fun setState(state: ActivationState, paused: Boolean) {
        this.state = state
        this.paused = paused
        feedbackJob?.let(main::removeCallbacks)
        feedbackJob = null
        render()
    }

    /** Briefly replaces the status text with what just happened. */
    fun showAction(event: GestureEvent, actionName: String) {
        if (root == null) return
        label?.text = "${event.label()} · $actionName"
        dot?.setTextColor(Color.parseColor("#66E0A3"))
        feedbackJob?.let(main::removeCallbacks)
        val restore = Runnable { feedbackJob = null; render() }
        feedbackJob = restore
        main.postDelayed(restore, FEEDBACK_MS)
    }

    private fun show() {
        val context = context ?: return
        val manager = manager ?: return
        if (root != null || !Settings.canDrawOverlays(context)) return

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(context, 10), dp(context, 6), dp(context, 12), dp(context, 6))
            background = rounded(Color.argb(220, 22, 22, 24), dp(context, 20))
        }
        dot = TextView(context).apply {
            text = "●"
            textSize = 14f
        }
        label = TextView(context).apply {
            textSize = 12f
            setTextColor(Color.WHITE)
            setPadding(dp(context, 6), 0, 0, 0)
        }
        container.addView(dot, LinearLayout.LayoutParams(WRAP, WRAP))
        container.addView(label, LinearLayout.LayoutParams(WRAP, WRAP))

        val metrics = context.resources.displayMetrics
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(context, 16)
            y = (metrics.heightPixels * 0.72f).toInt()
        }

        attachDragBehaviour(container, params)

        runCatching { manager.addView(container, params) }
            .onSuccess { root = container }
            .onFailure { root = null }
        render()
    }

    /**
     * Drag moves the badge anywhere; a tap without movement toggles pause. The
     * window stays exactly badge-sized, so touches outside it pass through.
     */
    private fun attachDragBehaviour(container: LinearLayout, params: WindowManager.LayoutParams) {
        val manager = manager ?: return
        val touchSlop = android.view.ViewConfiguration.get(container.context).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var originX = 0
        var originY = 0
        var dragging = false

        container.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    originX = params.x
                    originY = params.y
                    dragging = false
                    true
                }

                android.view.MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!dragging && (kotlin.math.abs(dx) > touchSlop || kotlin.math.abs(dy) > touchSlop)) {
                        dragging = true
                    }
                    if (dragging) {
                        params.x = originX + dx.toInt()
                        params.y = originY + dy.toInt()
                        runCatching { manager.updateViewLayout(view, params) }
                    }
                    true
                }

                android.view.MotionEvent.ACTION_UP -> {
                    if (!dragging) {
                        GestureRuntime.paused.value = !GestureRuntime.paused.value
                    }
                    true
                }

                else -> false
            }
        }
    }

    private fun render() {
        val view = root ?: return
        val effective = if (paused) ActivationState.PAUSED else state
        val (text, color) = when (effective) {
            ActivationState.ACTIVE -> "就绪" to "#66E0A3"
            ActivationState.WARMING -> "准备中" to "#7FB8FF"
            ActivationState.PAUSED -> "已暂停" to "#FFC66D"
            ActivationState.UNAVAILABLE -> "不可用" to "#FF7A7A"
            ActivationState.INACTIVE_NOT_ENABLED -> "未启用" to "#9E9E9E"
        }
        label?.text = text
        dot?.setTextColor(Color.parseColor(color))
        view.contentDescription = "手势控制：$text，点按${if (paused) "恢复" else "暂停"}"
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radius.toFloat()
        setColor(color)
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private const val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT
    private const val FEEDBACK_MS = 1_600L
}
