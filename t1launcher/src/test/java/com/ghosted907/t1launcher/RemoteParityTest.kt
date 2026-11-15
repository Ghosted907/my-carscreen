package com.ghosted907.t1launcher

import com.ghosted907.t1remote.protocol.RemoteProtocol
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Protocol rev 3: launcher parity, A/C limits and exclusions. */
class RemoteParityTest {
    private fun ok(body: JSONObject) = RemoteProtocol.validate(body) == null

    @Test fun climateKeysAreExactlyTheOemHvacKeys() {
        assertEquals(mapOf("power" to 1021, "ac" to 1000, "auto" to 1002, "sync" to 1022, "rear_defrost" to 1012,
            "recirc" to 1004, "blow" to 1013, "mode" to 1011, "fan" to 1014, "temp_l" to 1015, "temp_r" to 1016), Climate.keys.toMap())
        assertEquals(RemoteProtocol.CLIMATE.keys, Climate.keys.keys)
        // Steering-wheel heating (2000) and anything outside the HVAC block is never writable.
        assertFalse(2000 in Climate.keys.values)
        assertTrue(Climate.keys.values.all { it in 1000..1099 })
    }

    @Test fun climateValuesAreClampedAndSnapped() {
        assertEquals(16.0, RemoteProtocol.climateValue("temp_l", 3.0)!!, 0.0)
        assertEquals(30.0, RemoteProtocol.climateValue("temp_r", 31.7)!!, 0.0)
        assertEquals(22.5, RemoteProtocol.climateValue("temp_l", 22.4)!!, 0.0)
        assertEquals(22.0, RemoteProtocol.climateValue("temp_l", 22.2)!!, 0.0)
        assertEquals(7.0, RemoteProtocol.climateValue("fan", 12.0)!!, 0.0)
        assertEquals(1.0, RemoteProtocol.climateValue("fan", 0.0)!!, 0.0)
        assertEquals(1.0, RemoteProtocol.climateValue("power", 5.0)!!, 0.0)
        assertEquals(5.0, RemoteProtocol.climateValue("blow", 9.0)!!, 0.0)
        assertEquals(2.0, RemoteProtocol.climateValue("recirc", 3.0)!!, 0.0)
        assertNull(RemoteProtocol.climateValue("steering_heat", 1.0))
        assertNull(RemoteProtocol.climateValue("fan", Double.NaN))
    }

    @Test fun climateCommandValidation() {
        assertTrue(ok(JSONObject().put("op", "climate").put("item", "fan").put("value", 3)))
        assertTrue(ok(JSONObject().put("op", "climate").put("item", "temp_l").put("value", 21.5)))
        assertFalse(ok(JSONObject().put("op", "climate").put("item", "steering_heat").put("value", 1)))
        assertFalse(ok(JSONObject().put("op", "climate").put("item", "fan")))
    }

    @Test fun climateLabelsCoverEveryChoice() {
        assertEquals((1..5).toList(), Climate.blowLabels.map { it.first })
        assertEquals((1..2).toList(), Climate.recircLabels.map { it.first })
        assertEquals((1..3).toList(), Climate.modeLabels.map { it.first })
        assertEquals("تشغيل", Climate.describe("ac", 1.0))
        assertEquals("22.5°", Climate.describe("temp_l", 22.5))
    }

    @Test fun climateReadKeysExistInTheCatalog() {
        val keys = SignalCatalog.all.map { it.key }.toSet()
        Climate.readKeys.values.forEach { assertTrue(it, it in keys) }
        SignalCatalog.all.filter { it.key.startsWith("ac_") }.forEach {
            assertTrue(it.source == SignalSource.VEHICLE_INT || it.source == SignalSource.VEHICLE_FLOAT)
            assertTrue(it.address in Climate.keys.values)
        }
    }

    @Test fun wifiAndExcludedFeaturesAreNotReachable() {
        assertFalse(RemoteProtocol.SHORTCUTS.any { it.contains("wifi", true) })
        assertFalse(ok(JSONObject().put("op", "shortcut").put("id", "wifi")))
        assertFalse(RemoteProtocol.OEM_TARGETS.any { it.contains("wifi", true) })
        assertNotNull(RemoteProtocol.validateSetting("wifi", true))
        assertNotNull(RemoteProtocol.validateSetting("hide_apps", true))
        assertNotNull(RemoteProtocol.validateSetting("device_owner", true))
        assertNotNull(RemoteProtocol.validateSetting("pin", "1234"))
        RemoteProtocol.OPS.forEach { op ->
            listOf("tail", "trunk", "wifi", "owner", "shell", "install").forEach { bad -> assertFalse(op, op.contains(bad)) }
        }
    }

    @Test fun settingsValidation() {
        assertNull(RemoteProtocol.validateSetting("day", true))
        assertNotNull(RemoteProtocol.validateSetting("day", "yes"))
        assertNull(RemoteProtocol.validateSetting("language", "en"))
        assertNotNull(RemoteProtocol.validateSetting("language", "fr"))
        assertNull(RemoteProtocol.validateSetting("tasks_delay", 20))
        assertNotNull(RemoteProtocol.validateSetting("tasks_delay", 21))
        assertNotNull(RemoteProtocol.validateSetting("floating_style", "3"))
        assertNull(RemoteProtocol.validateSetting("floating_action", "enum:RUN_BLACK"))
        assertNull(RemoteProtocol.validateSetting("zone_action_LEFT_TOP", "app:com.waze"))
        assertNotNull(RemoteProtocol.validateSetting("zone_action_LEFT_TOP", "app:com.waze;rm"))
        assertNull(RemoteProtocol.validateSetting("split_1", "split:com.waze,com.google.android.youtube,1,50"))
        assertNull(RemoteProtocol.validateSetting("split_1", ""))
        assertNotNull(RemoteProtocol.validateSetting("split_1", "app:com.waze"))
        val tasks = JSONArray().put(JSONObject().put("action", "app:com.waze").put("enabled", true))
        assertNull(RemoteProtocol.validateSetting("tasks", tasks))
        val many = JSONArray().apply { repeat(21) { put(JSONObject().put("action", "enum:GLOBAL_HOME")) } }
        assertNotNull(RemoteProtocol.validateSetting("tasks", many))
        // Every setting key the launcher exposes parses as a known action shape.
        assertTrue(RemoteProtocol.SETTINGS.keys.containsAll(RemoteProtocol.ZONES.map { "zone_on_$it" }))
        assertEquals(JctZone.values().map { it.name }.toSet(), RemoteProtocol.ZONES.toSet())
        assertEquals(JctFloatingStyle.choices.map { it.toString() },
            (RemoteProtocol.SETTINGS["floating_style"] as RemoteProtocol.Setting.Choice).values)
        assertEquals(JctLang.choices, (RemoteProtocol.SETTINGS["language"] as RemoteProtocol.Setting.Choice).values)
    }

    @Test fun boardValidation() {
        assertTrue(ok(JSONObject().put("op", "board_set").put("sticker_x", .5).put("sticker_size", 120)))
        assertFalse(ok(JSONObject().put("op", "board_set").put("sticker_size", 400)))
        assertFalse(ok(JSONObject().put("op", "board_set").put("caption_font", 13)))
        assertFalse(ok(JSONObject().put("op", "board_set").put("caption", "x".repeat(121))))
        val stroke = JSONObject().put("color", -1).put("points", JSONArray().put(JSONArray().put(.1).put(.2)))
        assertTrue(ok(JSONObject().put("op", "board_set").put("strokes", JSONArray().put(stroke))))
        val bad = JSONObject().put("color", -1).put("points", JSONArray().put(JSONArray().put(1.5).put(.2)))
        assertFalse(ok(JSONObject().put("op", "board_set").put("strokes", JSONArray().put(bad))))
        assertEquals(RemoteProtocol.BOARD_FONTS, RemoteParity.BOARD_FONT_LABELS.size)
    }

    @Test fun parityCommandsValidate() {
        RemoteProtocol.SHORTCUTS.forEach { assertTrue(ok(JSONObject().put("op", "shortcut").put("id", it))) }
        RemoteProtocol.PAGES.forEach { assertTrue(ok(JSONObject().put("op", "page").put("page", it))) }
        RemoteProtocol.OEM_TARGETS.forEach { assertTrue(ok(JSONObject().put("op", "oem").put("target", it))) }
        RemoteProtocol.RUN_IDS.forEach { assertTrue(ok(JSONObject().put("op", "run").put("id", it))) }
        assertTrue(ok(JSONObject().put("op", "cluster").put("action", "theme").put("value", 3)))
        assertFalse(ok(JSONObject().put("op", "cluster").put("action", "theme").put("value", 4)))
        assertFalse(ok(JSONObject().put("op", "page").put("page", "wifi")))
        assertTrue(ok(JSONObject().put("op", "pin").put("pkg", "com.waze").put("on", true)))
        assertTrue(ok(JSONObject().put("op", "wallpaper").put("clear", true)))
        assertFalse(ok(JSONObject().put("op", "wallpaper")))
        assertTrue(ok(JSONObject().put("op", "asset").put("name", "emblem")))
        assertFalse(ok(JSONObject().put("op", "asset").put("name", "../../etc")))
    }
}
