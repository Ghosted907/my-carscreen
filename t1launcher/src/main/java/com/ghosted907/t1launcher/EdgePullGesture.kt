package com.ghosted907.t1launcher

import kotlin.math.abs

/** Pixel-based geometry, shared by the mirrored main-display pull tabs. */
internal object EdgePullGesture {
    const val TIP_PX = 10
    const val TAP_SLOP_PX = 8f
    const val FLING_PX_PER_SECOND = 900f

    fun inward(fromRight: Boolean, dx: Float): Float = if (fromRight) -dx else dx

    fun visibleWidth(inward: Float): Float =
        (TIP_PX + inward.coerceAtLeast(0f)).coerceIn(TIP_PX.toFloat(), EdgeTab.WIDTH_PX.toFloat())

    fun opens(inward: Float, dy: Float, velocity: Float): Boolean {
        val tap = abs(inward) <= TAP_SLOP_PX && abs(dy) <= TAP_SLOP_PX
        val horizontal = inward > abs(dy)
        val pull = horizontal && inward > EdgeTab.WIDTH_PX / 2f
        val fling = horizontal && inward > TAP_SLOP_PX && velocity > FLING_PX_PER_SECOND
        return tap || pull || fling
    }
}