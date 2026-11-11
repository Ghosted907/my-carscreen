package com.ghosted907.t1launcher

/** Decides when a finger on the edge strip should bring T1 forward. */
object EdgeGesture {
    const val MIN_TRAVEL_PX = 80f

    fun opens(fromRight: Boolean, dx: Float, dy: Float): Boolean {
        val towardCenter = if (fromRight) -dx else dx
        return towardCenter > MIN_TRAVEL_PX && kotlin.math.abs(dx) > kotlin.math.abs(dy)
    }

    fun isTap(dx: Float, dy: Float, slopPx: Float): Boolean {
        return kotlin.math.abs(dx) < slopPx && kotlin.math.abs(dy) < slopPx
    }
}
