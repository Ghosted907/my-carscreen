package com.ghosted907.t1launcher

/**
 * Right-edge launcher tab for the 1920×1080 main screen.
 * A slanted half-moon wedge, taller than it is wide, flush with the screen edge.
 * Coordinates: x grows toward the edge, y grows downward.
 */
object EdgeTab {
    const val WIDTH_PX = 52
    const val HEIGHT_PX = 164
    const val ABOVE_CENTER_PX = 48

    data class Pt(val x: Float, val y: Float)

    data class Cubic(val a: Pt, val b: Pt, val c: Pt, val d: Pt) {
        fun samples(steps: Int = 14): List<Pt> {
            val out = ArrayList<Pt>(steps + 1)
            for (i in 0..steps) {
                val t = i / steps.toFloat()
                val u = 1f - t
                out += Pt(
                    u * u * u * a.x + 3f * u * u * t * b.x + 3f * u * t * t * c.x + t * t * t * d.x,
                    u * u * u * a.y + 3f * u * u * t * b.y + 3f * u * t * t * c.y + t * t * t * d.y
                )
            }
            return out
        }
    }

    /** Top of the overlay on a display of [screenHeight]. Slightly above the vertical center. */
    fun topOnScreen(screenHeight: Int): Int {
        val panel = screenHeight.coerceAtLeast(HEIGHT_PX)
        val raw = panel / 2 - HEIGHT_PX / 2 - ABOVE_CENTER_PX
        return raw.coerceIn(0, panel - HEIGHT_PX)
    }

    fun curves(width: Float = WIDTH_PX.toFloat(), height: Float = HEIGHT_PX.toFloat()): List<Cubic> {
        fun x(v: Float) = v / WIDTH_PX * width
        fun y(v: Float) = v / HEIGHT_PX * height
        fun p(px: Float, py: Float) = Pt(x(px), y(py))
        return listOf(
            // Top edge tilts: the crest sits inward and higher than the join on the screen edge.
            Cubic(p(52f, 40f), p(44f, 8f), p(22f, 0f), p(10f, 46f)),
            // Half-moon belly coming in from the right edge.
            Cubic(p(10f, 46f), p(0f, 74f), p(0f, 118f), p(12f, 140f)),
            // Bottom returns to the edge.
            Cubic(p(12f, 140f), p(26f, 163f), p(44f, 160f), p(52f, 136f))
        )
    }

    fun outline(width: Float = WIDTH_PX.toFloat(), height: Float = HEIGHT_PX.toFloat()): List<Pt> {
        val out = ArrayList<Pt>()
        for (curve in curves(width, height)) {
            val sampled = curve.samples()
            if (out.isEmpty()) out += sampled else out += sampled.drop(1)
        }
        return out
    }

    fun contains(x: Float, y: Float, width: Float = WIDTH_PX.toFloat(), height: Float = HEIGHT_PX.toFloat()): Boolean {
        val pts = outline(width, height)
        if (pts.isEmpty()) return false
        var inside = false
        var j = pts.lastIndex
        for (i in pts.indices) {
            val pi = pts[i]
            val pj = pts[j]
            val crosses = (pi.y > y) != (pj.y > y)
            if (crosses) {
                val span = pj.y - pi.y
                val at = if (span == 0f) pi.x else (pj.x - pi.x) * (y - pi.y) / span + pi.x
                if (x < at) inside = !inside
            }
            j = i
        }
        return inside
    }
}
