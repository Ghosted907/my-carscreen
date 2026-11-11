package com.ghosted907.t1launcher

import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EdgeTabTest {
    @Test
    fun tabIsATallWedgeNotACircle() {
        assertTrue(EdgeTab.WIDTH_PX in 44..56)
        assertTrue(EdgeTab.HEIGHT_PX in 140..180)
        assertTrue(EdgeTab.HEIGHT_PX > EdgeTab.WIDTH_PX * 2)
        val pts = EdgeTab.outline()
        val cx = pts.map { it.x }.average()
        val cy = pts.map { it.y }.average()
        val radii = pts.map { hypot((it.x - cx).toDouble(), (it.y - cy).toDouble()) }
        assertTrue(radii.max() - radii.min() > 20.0)
    }

    @Test
    fun topEdgeSlantsInwardAndTheRightSideSitsOnTheScreenEdge() {
        val pts = EdgeTab.outline()
        assertEquals(EdgeTab.WIDTH_PX.toFloat(), pts.maxOf { it.x }, 1.5f)
        assertTrue(pts.minOf { it.x } < 8f)
        val crest = pts.minBy { it.y }
        val edgeJoin = pts.filter { it.x >= EdgeTab.WIDTH_PX - 1.5f }.minBy { it.y }
        assertTrue(crest.y + 10f < edgeJoin.y)
        assertTrue(crest.x < edgeJoin.x - 10f)
    }

    @Test
    fun hitTestFollowsTheWedge() {
        assertTrue(EdgeTab.contains(30f, 90f))
        assertFalse(EdgeTab.contains(2f, 2f))
        assertFalse(EdgeTab.contains(-4f, 90f))
        assertFalse(EdgeTab.contains(30f, 200f))
    }

    @Test
    fun onA1080ScreenTheTabSitsFullyAboveCenter() {
        val top = EdgeTab.topOnScreen(1080)
        val mid = top + EdgeTab.HEIGHT_PX / 2
        assertTrue(top > 0)
        assertTrue(top + EdgeTab.HEIGHT_PX < 1080)
        assertTrue(mid < 1080 / 2)
    }
}
