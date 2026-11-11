package com.ghosted907.t1launcher

import org.junit.Assert.*
import org.junit.Test

class ButtonConfigTest {
    @Test fun defaultsKeepBothExistingBehaviors() {
        val all = ButtonConfig.defaults()
        assertTrue(ButtonConfig.validate(all))
        assertEquals(ButtonConfig.LAUNCHER, all[0].target)
        assertTrue(all[0].fromRight)
        assertEquals("app.revanced.android.youtube", all[1].target)
        assertFalse(all[1].fromRight)
        assertEquals(.64f, all[0].y)
    }
    @Test fun positionsAndCountsAreBounded() {
        val b = ButtonConfig.defaults().first()
        assertTrue(ButtonConfig.validate(emptyList()))
        assertFalse(ButtonConfig.validate(listOf(b,b)))
        assertFalse(b.copy(x = Float.NaN).valid())
        assertFalse(b.copy(y = 1.01f).valid())
        assertFalse(b.copy(x = -.01f).valid())
        assertFalse(b.copy(target = "https://example.org").valid())
        assertTrue(b.copy(target = "com.waze/.MainActivity", x = .45f).valid())
        assertFalse(ButtonConfig.validate((1..17).map { b.copy(id = "button$it") }))
    }
    @Test fun presetCoordinatesArePhysicalNotRtl() {
        assertEquals(1f to .2f, ButtonConfig.preset("يمين فوق"))
        assertEquals(1f to .8f, ButtonConfig.preset("يمين تحت"))
        assertEquals(0f to .64f, ButtonConfig.preset("يسار"))
        assertEquals(0f to .2f, ButtonConfig.preset("يسار فوق"))
        assertEquals(0f to .8f, ButtonConfig.preset("يسار تحت"))
    }
}
