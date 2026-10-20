package com.aperturelab.gesturecontrol.domain

/** User-facing names shared by the overlay and the settings surface. */
fun GestureEvent.label(): String = when (this) {
    GestureEvent.SWIPE_UP -> "上滑"
    GestureEvent.SWIPE_DOWN -> "下滑"
    GestureEvent.SWIPE_LEFT -> "左滑"
    GestureEvent.SWIPE_RIGHT -> "右滑"
    GestureEvent.FIST_HOLD -> "握拳"
}

fun GestureAction.label(): String = when (this) {
    GestureAction.SCROLL_FORWARD -> "向下滚动"
    GestureAction.SCROLL_BACKWARD -> "向上滚动"
    GestureAction.BACK -> "返回"
    GestureAction.HOME -> "回主页"
    GestureAction.PAGE_FORWARD -> "下一页"
    GestureAction.PAGE_BACKWARD -> "上一页"
    GestureAction.NONE -> "无动作"
}

fun GestureAction.shortLabel(): String = when (this) {
    GestureAction.SCROLL_FORWARD -> "滚动↓"
    GestureAction.SCROLL_BACKWARD -> "滚动↑"
    GestureAction.BACK -> "返回"
    GestureAction.HOME -> "主页"
    GestureAction.PAGE_FORWARD -> "下一页"
    GestureAction.PAGE_BACKWARD -> "上一页"
    GestureAction.NONE -> "无动作"
}
