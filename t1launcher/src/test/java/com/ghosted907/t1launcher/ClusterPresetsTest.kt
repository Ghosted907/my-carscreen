package com.ghosted907.t1launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class ClusterPresetsTest {
    @Test
    fun centerMatchesTheResizeThatWorkedOnTheCar() {
        val box = ClusterPresets.center()
        assertEquals(560, box.left)
        assertEquals(80, box.top)
        assertEquals(1360, box.right)
        assertEquals(640, box.bottom)
    }

    @Test
    fun halvesSplitTheClusterPanel() {
        assertEquals(ClusterPresets.Box(0, 0, 960, 720), ClusterPresets.leftHalf())
        assertEquals(ClusterPresets.Box(960, 0, 1920, 720), ClusterPresets.rightHalf())
        assertEquals(ClusterPresets.Box(0, 0, 1920, 720), ClusterPresets.full())
    }

    @Test
    fun customStaysInsideThePanel() {
        val box = ClusterPresets.custom(1800, 700, 400, 200)
        assertEquals(1520, box.left)
        assertEquals(1920, box.right)
        assertEquals(520, box.top)
        assertEquals(720, box.bottom)
    }
}
