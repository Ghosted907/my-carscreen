package com.ghosted907.t1launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class CompassGeometryTest {
    @Test
    fun defaultDiscCoversTheTachometerCompass() {
        val (x, y) = CompassGeometry.origin(
            CompassGeometry.DEFAULT_CX,
            CompassGeometry.DEFAULT_CY,
            CompassGeometry.DEFAULT_D
        )
        assertEquals(1440, x)
        assertEquals(268, y)
        assertEquals(232, CompassGeometry.DEFAULT_D)
    }
}
