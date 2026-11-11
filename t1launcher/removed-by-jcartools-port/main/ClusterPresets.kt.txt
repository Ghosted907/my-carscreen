package com.ghosted907.t1launcher

/** Cluster rectangles in 1920×720 pixels. Center matches the resize that worked on the car. */
object ClusterPresets {
    const val DESIGN_W = 1920
    const val DESIGN_H = 720

    data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width: Int get() = right - left
        val height: Int get() = bottom - top
    }

    fun full(width: Int = DESIGN_W, height: Int = DESIGN_H) = Box(0, 0, width, height)

    fun center(width: Int = DESIGN_W, height: Int = DESIGN_H): Box {
        if (width == DESIGN_W && height == DESIGN_H) return Box(560, 80, 1360, 640)
        val w = (width * 800f / DESIGN_W).toInt().coerceAtLeast(240)
        val h = (height * 560f / DESIGN_H).toInt().coerceAtLeast(160)
        val left = ((width - w) / 2).coerceAtLeast(0)
        val top = ((height - h) / 2).coerceAtLeast(0)
        return Box(left, top, left + w, top + h)
    }

    fun leftHalf(width: Int = DESIGN_W, height: Int = DESIGN_H) = Box(0, 0, width / 2, height)

    fun rightHalf(width: Int = DESIGN_W, height: Int = DESIGN_H) = Box(width / 2, 0, width, height)

    /** 800px window flush to the right edge (1120,0–1920,720 on the design panel). */
    fun rightSide(width: Int = DESIGN_W, height: Int = DESIGN_H): Box {
        val boxW = sideWidth(width)
        val left = (width - boxW).coerceAtLeast(0)
        return Box(left, 0, left + boxW, height)
    }

    /** Mirror of [rightSide], flush to the left edge. */
    fun leftSide(width: Int = DESIGN_W, height: Int = DESIGN_H): Box {
        val boxW = sideWidth(width)
        return Box(0, 0, boxW, height)
    }

    private fun sideWidth(width: Int): Int {
        val panel = width.coerceAtLeast(1)
        if (panel == DESIGN_W) return 800
        return (panel * 800f / DESIGN_W).toInt().coerceIn(minOf(240, panel), panel)
    }

    fun custom(x: Int, y: Int, w: Int, h: Int, width: Int = DESIGN_W, height: Int = DESIGN_H): Box {
        val maxW = width.coerceAtLeast(1)
        val maxH = height.coerceAtLeast(1)
        val boxW = w.coerceIn(minOf(240, maxW), maxW)
        val boxH = h.coerceIn(minOf(160, maxH), maxH)
        val left = x.coerceIn(0, (width - boxW).coerceAtLeast(0))
        val top = y.coerceIn(0, (height - boxH).coerceAtLeast(0))
        return Box(left, top, left + boxW, top + boxH)
    }
}
