package com.ghosted907.t1launcher

import org.junit.Assert.*
import org.junit.Test

class JctActionTest {
    @Test fun parsesNaviStartPrefixes() {
        assertEquals(JctAction(JctType.APP, pkg = "com.waze"), JctActions.parse("app:com.waze"))
        assertEquals(JctEnum.MEDIA_NEXT, JctActions.parse("enum:MEDIA_NEXT").enumValue)
        assertEquals(JctEnum.GLOBAL_HOME, JctActions.parse("GLOBAL_HOME").enumValue)
        assertEquals(JctAction.NOT_VALUE, JctActions.parse(null))
        assertEquals(JctAction.NOT_VALUE, JctActions.parse("app:  "))
        assertEquals(JctEnum.Not_value, JctActions.parse("enum:UNKNOWN").enumValue)
        assertEquals("run_app_more_settings", JctActions.parse("profile:run_app_more_settings").profile)
    }

    @Test fun splitNormalisesLikeNaviStart() {
        val a = JctActions.parse("split:a.b,c.d,5,40")
        assertEquals(1, a.orientation); assertEquals(50, a.ratio)
        assertEquals("split:a.b,c.d,2,70", JctActions.encode(JctActions.parse("split:a.b,c.d,2,70")))
        assertEquals(JctAction.NOT_VALUE, JctActions.parse("split:a.b,c.d"))
    }

    @Test fun encodeRoundTrips() {
        listOf("enum:Not_value", "app:com.waze", "profile:run_app_more_settings", "enum:RUN_START_APP_MENU")
            .forEach { assertEquals(it, JctActions.encode(JctActions.parse(it))) }
    }

    @Test fun validity() {
        assertFalse(JctActions.valid(JctAction.NOT_VALUE))
        assertTrue(JctActions.valid(JctActions.parse("app:x.y")))
    }

    @Test fun zoneGeometryMatchesNaviStart() {
        val t = JctZone.thickness(1.33f)
        assertEquals(43, t)
        assertEquals(JctZone.Rect(0, 0, 43, 540), JctZone.geometry(JctZone.LEFT_TOP, 1920, 1080, t))
        assertEquals(JctZone.Rect(1877, 540, 43, 540), JctZone.geometry(JctZone.RIGHT_BOTTOM, 1920, 1080, t))
        assertEquals(JctZone.Rect(43, 1037, 917, 43), JctZone.geometry(JctZone.BOTTOM_LEFT, 1920, 1080, t))
        assertEquals(JctZone.Rect(960, 1037, 917, 43), JctZone.geometry(JctZone.BOTTOM_RIGHT, 1920, 1080, t))
        assertNull(JctZone.geometry(JctZone.BOTTOM_LEFT, 80, 1080, 43))
    }

    @Test fun zoneThreshold() {
        assertFalse(JctZone.fires(60f, 60f))   // 84.8 px
        assertTrue(JctZone.fires(91f, 0f))
        assertTrue(JctZone.fires(5000f, 0f))   // capped at 500 still fires
    }
}
