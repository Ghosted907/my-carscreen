package com.ghosted907.t1launcher

import org.junit.Assert.*
import org.junit.Test

class EdgePullGestureTest {
    @Test fun mirrorDirections() {
        assertEquals(165f, EdgePullGesture.inward(true, -165f), 0f)
        assertEquals(165f, EdgePullGesture.inward(false, 165f), 0f)
    }
    @Test fun revealFollowsFingerAndClamps() {
        assertEquals(10f, EdgePullGesture.visibleWidth(-20f), 0f)
        assertEquals(30f, EdgePullGesture.visibleWidth(20f), 0f)
        assertEquals(52f, EdgePullGesture.visibleWidth(165f), 0f)
    }
    @Test fun tapStillOpens() { assertTrue(EdgePullGesture.opens(0f, 0f, 0f)) }
    @Test fun shortSlowDragDoesNotOpen() { assertFalse(EdgePullGesture.opens(20f, 0f, 50f)) }
    @Test fun halfWidthThreshold() {
        assertFalse(EdgePullGesture.opens(26f, 0f, 50f))
        assertTrue(EdgePullGesture.opens(27f, 0f, 50f))
    }
    @Test fun quickInwardFlingOpens() { assertTrue(EdgePullGesture.opens(20f, 0f, 1200f)) }
    @Test fun outwardFlingDoesNotOpen() { assertFalse(EdgePullGesture.opens(-20f, 0f, -1200f)) }
    @Test fun verticalDragDoesNotOpen() { assertFalse(EdgePullGesture.opens(30f, 80f, 1200f)) }
}