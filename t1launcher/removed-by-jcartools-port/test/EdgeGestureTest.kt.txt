package com.ghosted907.t1launcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EdgeGestureTest {
    @Test
    fun rightEdgeOpensOnALeftwardHorizontalDrag() {
        assertTrue(EdgeGesture.opens(fromRight = true, dx = -90f, dy = 12f))
    }

    @Test
    fun rightEdgeIgnoresAShortOrVerticalOrRightwardMove() {
        assertFalse(EdgeGesture.opens(fromRight = true, dx = -40f, dy = 0f))
        assertFalse(EdgeGesture.opens(fromRight = true, dx = -90f, dy = 120f))
        assertFalse(EdgeGesture.opens(fromRight = true, dx = 100f, dy = 0f))
    }

    @Test
    fun leftEdgeOpensOnlyTowardCenter() {
        assertTrue(EdgeGesture.opens(fromRight = false, dx = 90f, dy = 10f))
        assertFalse(EdgeGesture.opens(fromRight = false, dx = -90f, dy = 0f))
    }

    @Test
    fun aSmallMovementIsATap() {
        assertTrue(EdgeGesture.isTap(4f, -6f, 24f))
        assertFalse(EdgeGesture.isTap(30f, 0f, 24f))
    }
}
