package com.ghosted907.t1launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class ClusterPlacementTest {
    @Test
    fun newAppsDefaultToTheRight() {
        assertEquals(ClusterPlacement.Spot.RIGHT, ClusterPlacement.fromId(null))
        assertEquals(ClusterPlacement.Spot.RIGHT, ClusterPlacement.fromId(""))
        assertEquals(ClusterPlacement.Spot.RIGHT, ClusterPlacement.fromId("nope"))
        assertEquals(ClusterPlacement.Spot.RIGHT, ClusterPlacement.resolve(null, null, false))
        assertEquals(
            ClusterPlacement.Spot.RIGHT,
            ClusterPlacement.resolve(null, ClusterPlacement.Spot.LEFT, false)
        )
    }

    @Test
    fun savedPlacementIsUsedUntilAChipIsArmed() {
        assertEquals(
            ClusterPlacement.Spot.LEFT,
            ClusterPlacement.resolve("left", ClusterPlacement.Spot.RIGHT, false)
        )
        assertEquals(
            ClusterPlacement.Spot.CENTER,
            ClusterPlacement.resolve("center", null, false)
        )
        assertEquals(
            ClusterPlacement.Spot.FULL,
            ClusterPlacement.resolve("left", ClusterPlacement.Spot.FULL, true)
        )
        assertEquals(
            ClusterPlacement.Spot.RIGHT,
            ClusterPlacement.resolve("left", ClusterPlacement.Spot.RIGHT, true)
        )
    }

    @Test
    fun boundsMatchTheClusterPlacements() {
        assertEquals(ClusterPresets.Box(1120, 0, 1920, 720), ClusterPlacement.bounds(ClusterPlacement.Spot.RIGHT))
        assertEquals(ClusterPresets.Box(0, 0, 800, 720), ClusterPlacement.bounds(ClusterPlacement.Spot.LEFT))
        assertEquals(800, ClusterPlacement.bounds(ClusterPlacement.Spot.RIGHT).width)
        assertEquals(800, ClusterPlacement.bounds(ClusterPlacement.Spot.LEFT).width)
        assertEquals(ClusterPresets.center(), ClusterPlacement.bounds(ClusterPlacement.Spot.CENTER))
        assertEquals(ClusterPresets.full(), ClusterPlacement.bounds(ClusterPlacement.Spot.FULL))
        val custom = ClusterPresets.Box(100, 40, 500, 400)
        assertEquals(custom, ClusterPlacement.bounds(ClusterPlacement.Spot.CUSTOM, custom))
        assertEquals(
            ClusterPresets.rightSide(),
            ClusterPlacement.bounds(ClusterPlacement.Spot.CUSTOM, null)
        )
    }

    @Test
    fun arabicLabels() {
        assertEquals("يمين", ClusterPlacement.Spot.RIGHT.labelAr)
        assertEquals("يسار", ClusterPlacement.Spot.LEFT.labelAr)
        assertEquals("وسط", ClusterPlacement.Spot.CENTER.labelAr)
        assertEquals("ملء الشاشة", ClusterPlacement.Spot.FULL.labelAr)
    }
}
