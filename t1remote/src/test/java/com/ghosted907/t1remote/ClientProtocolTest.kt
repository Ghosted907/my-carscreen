package com.ghosted907.t1remote

import com.ghosted907.t1remote.protocol.RemoteProtocol
import com.ghosted907.t1remote.protocol.RemoteProtocol.Parsed
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClientProtocolTest {
    @Test fun artRejectsMissingCoordinatesAndOversizedPayload() {
        val art = JSONObject().put("op", "art").put("image", "a")
        assertTrue(RemoteProtocol.validate(art) != null)
        art.put("x", .5).put("y", .5)
        assertEquals(null, RemoteProtocol.validate(art))
        art.put("x", 2)
        assertTrue(RemoteProtocol.validate(art) != null)
        art.put("x", .5).put("image", "a".repeat(900001))
        assertTrue(RemoteProtocol.validate(art) != null)
        assertEquals(null, RemoteProtocol.validate(JSONObject().put("op", "art_clear")))
    }

    @Test fun holdFiresOnlyAfterOneSecondWithoutRelease() {
        assertFalse(HoldPolicy.fires(0, null, 999))
        assertTrue(HoldPolicy.fires(0, null, 1000))
        assertFalse(HoldPolicy.fires(0, 500, 1500))
        assertEquals(RemoteProtocol.HOLD_MS, HoldPolicy.HOLD_MS)
    }

    @Test fun clientCommandsRoundTripThroughServerParser() {
        var seq = 0L
        listOf(
            JSONObject().put("op", "apps"),
            JSONObject().put("op", "open").put("pkg", "com.waze").put("screen", "cluster"),
            JSONObject().put("op", "vehicle").put("cmd", "shade_half").put("held_ms", 1016),
            JSONObject().put("op", "climate").put("item", "temp_l").put("value", 22.5),
            JSONObject().put("op", "set").put("key", "day").put("value", true),
            JSONObject().put("op", "shortcut").put("id", "carplay"),
            JSONObject().put("op", "board_set").put("sticker_x", .3).put("sticker", false),
            JSONObject().put("op", "data")
        ).forEach { body ->
            val r = RemoteProtocol.parse(RemoteProtocol.command(++seq, body).toString())
            assertTrue(body.toString(), r is Parsed.Ok)
            assertEquals(body.toString(), (r as Parsed.Ok).body.toString())
            assertEquals(seq, r.seq)
        }
    }

    @Test fun helloAcceptsVersionTwoAndNewer() {
        assertNull(ClientRules.helloError(RemoteProtocol.hello("T1", "ab")))
        assertNull(ClientRules.helloError(JSONObject().put("t", "hello").put("v", 3)))
        assertNotNull(ClientRules.helloError(JSONObject().put("t", "hello").put("v", 1)))
        assertNotNull(ClientRules.helloError(JSONObject().put("t", "res")))
    }

    @Test fun backoffGrowsAndCaps() {
        assertEquals(1000L, ClientRules.backoffMs(0))
        assertEquals(2000L, ClientRules.backoffMs(1))
        assertEquals(8000L, ClientRules.backoffMs(3))
        assertEquals(15000L, ClientRules.backoffMs(4))
        assertEquals(15000L, ClientRules.backoffMs(40))
        assertEquals(1000L, ClientRules.backoffMs(-3))
    }

    @Test fun featureDetectionFromPing() {
        val old = JSONObject().put("vehicle", true)
        assertFalse(ClientRules.supportsRev3(old))
        assertTrue(ClientRules.supports(old, "apps"))
        assertFalse(ClientRules.supports(old, "climate"))
        val new = JSONObject().put("rev", 3).put("ops", JSONArray(RemoteProtocol.OPS.toList()))
        assertTrue(ClientRules.supportsRev3(new))
        assertTrue(ClientRules.supports(new, "climate"))
        assertFalse(ClientRules.supports(new, "wifi"))
    }

    @Test fun manualHostValidation() {
        assertTrue(ClientRules.validHost("192.168.43.20"))
        assertTrue(ClientRules.validHost("t1-car.local"))
        assertFalse(ClientRules.validHost("192.168.1.300"))
        assertFalse(ClientRules.validHost("192.168.1"))
        assertFalse(ClientRules.validHost(""))
        assertFalse(ClientRules.validHost("a b"))
        assertFalse(ClientRules.validHost("http://x"))
    }

    @Test fun arabicErrorsForNetworkFailures() {
        assertTrue(ClientRules.arabic(java.net.SocketTimeoutException()).contains("مهلة"))
        assertTrue(ClientRules.arabic(java.net.ConnectException("refused")).contains("اللانشر"))
        assertEquals("رسالة", ClientRules.arabic(java.io.IOException("رسالة")))
    }

    @Test fun climateClampOnThePhoneMatchesTheLauncherTable() {
        assertEquals(30.0, RemoteProtocol.climateValue("temp_r", 45.0)!!, 0.0)
        assertEquals(7.0, RemoteProtocol.climateValue("fan", 8.0)!!, 0.0)
        assertNull(RemoteProtocol.climateValue("wheel_heat", 1.0))
    }
}
