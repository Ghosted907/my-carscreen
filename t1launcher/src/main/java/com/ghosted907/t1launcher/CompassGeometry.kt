package com.ghosted907.t1launcher

/** Stock CLASSIC compass disc on the 1920×720 cluster (S9). */
object CompassGeometry {
    const val DEFAULT_CX = 1556
    const val DEFAULT_CY = 384
    const val DEFAULT_D = 232

    fun origin(cx: Int, cy: Int, diameter: Int): Pair<Int, Int> {
        val size = diameter.coerceAtLeast(1)
        return (cx - size / 2) to (cy - size / 2)
    }
}
