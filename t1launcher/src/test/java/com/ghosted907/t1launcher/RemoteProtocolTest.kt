package com.ghosted907.t1launcher

import com.ghosted907.t1remote.protocol.RemoteProtocol
import com.ghosted907.t1remote.protocol.RemoteProtocol.Parsed
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteProtocolTest {
    private fun line(seq: Long, body: JSONObject) = RemoteProtocol.command(seq, body).toString()

    @Test fun plainCommandIsAcceptedWithoutAnyKey() {
        val r = RemoteProtocol.parse(line(7, JSONObject().put("op", "apps")))
        assertTrue(r is Parsed.Ok)
        assertEquals(7L, (r as Parsed.Ok).seq)
        assertEquals("apps", r.body.getString("op"))
    }

    @Test fun garbageAndUnknownMessagesAreRejected() {
        assertTrue(RemoteProtocol.parse("not json") is Parsed.Rejected)
        assertTrue(RemoteProtocol.parse("{\"t\":\"pair\",\"code\":\"123456\"}") is Parsed.Rejected)
        assertTrue(RemoteProtocol.parse("{\"t\":\"cmd\",\"seq\":1}") is Parsed.Rejected)
        val r = RemoteProtocol.parse(line(3, JSONObject().put("op", "shell").put("cmd", "reboot")))
        assertEquals(3L, (r as Parsed.Rejected).seq)
    }

    @Test fun helloCarriesNameAndVersionOnly() {
        val h = RemoteProtocol.hello("T1 Launcher", "abcd")
        assertEquals(RemoteProtocol.VERSION, h.getInt("v"))
        assertEquals("T1 Launcher", h.getString("name"))
        assertTrue(!h.has("nonce") && !h.has("token"))
    }

    @Test fun onlyTheNineApprovedVehicleCommandsPass() {
        assertEquals(9, RemoteProtocol.VEHICLE_IDS.size)
        assertEquals(VehicleCommands.all.map { it.id }.toSet(), RemoteProtocol.VEHICLE_IDS.toSet())
        RemoteProtocol.VEHICLE_IDS.forEach {
            assertNull(RemoteProtocol.validate(JSONObject().put("op", "vehicle").put("cmd", it).put("held_ms", 1000)))
        }
        listOf("tailgate_open", "tailgate", "trunk_open", "door_unlock", "").forEach {
            assertNotNull(RemoteProtocol.validate(JSONObject().put("op", "vehicle").put("cmd", it).put("held_ms", 5000)))
        }
        assertTrue(RemoteProtocol.VEHICLE_IDS.none { it.contains("tail") || it.contains("trunk") })
    }

    @Test fun vehicleCommandNeedsDeliberateHold() {
        assertNotNull(RemoteProtocol.validate(JSONObject().put("op", "vehicle").put("cmd", "roof_open")))
        assertNotNull(RemoteProtocol.validate(JSONObject().put("op", "vehicle").put("cmd", "roof_open").put("held_ms", 999)))
        val r = RemoteProtocol.parse(line(1, JSONObject().put("op", "vehicle").put("cmd", "roof_open").put("held_ms", 200)))
        assertTrue(r is Parsed.Rejected)
        assertTrue(RemoteProtocol.parse(line(2, JSONObject().put("op", "vehicle").put("cmd", "roof_open").put("held_ms", 1000))) is Parsed.Ok)
    }

    @Test fun noAudioOperationsExist() {
        listOf("volume", "media", "play", "pause", "next", "bose", "sound").forEach {
            assertTrue(it !in RemoteProtocol.OPS)
            assertNotNull(RemoteProtocol.validate(JSONObject().put("op", it)))
        }
    }

    @Test fun windowCommandsValidatePackageAndScreen() {
        assertNull(RemoteProtocol.validate(JSONObject().put("op", "open").put("pkg", "com.waze").put("screen", "cluster")))
        assertNull(RemoteProtocol.validate(JSONObject().put("op", "move").put("pkg", "com.google.android.youtube").put("screen", "main")))
        assertNotNull(RemoteProtocol.validate(JSONObject().put("op", "open").put("pkg", "com.waze").put("screen", "display2")))
        assertNotNull(RemoteProtocol.validate(JSONObject().put("op", "open").put("pkg", "../etc").put("screen", "main")))
        assertNotNull(RemoteProtocol.validate(JSONObject().put("op", "front")))
        assertNotNull(RemoteProtocol.validate(JSONObject().put("op", "resize").put("pkg", "com.waze").put("preset", "huge")))
    }

    @Test fun resizePresetBounds() {
        assertArrayEquals(intArrayOf(960, 0, 1920, 720), RemoteProtocol.presetBounds("right_half", 1920, 720))
        assertArrayEquals(intArrayOf(0, 0, 960, 720), RemoteProtocol.presetBounds("left_half", 1920, 720))
        assertArrayEquals(intArrayOf(320, 90, 1600, 630), RemoteProtocol.presetBounds("center", 1920, 720))
        assertNull(RemoteProtocol.presetBounds("full", 1920, 720))
    }
}
