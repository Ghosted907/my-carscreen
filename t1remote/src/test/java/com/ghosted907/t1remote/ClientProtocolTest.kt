package com.ghosted907.t1remote

import com.ghosted907.t1remote.protocol.RemoteProtocol
import com.ghosted907.t1remote.protocol.RemoteProtocol.Parsed
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClientProtocolTest {
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
            JSONObject().put("op", "vehicle").put("cmd", "shade_half").put("held_ms", 1016)
        ).forEach { body ->
            val r = RemoteProtocol.parse(RemoteProtocol.command(++seq, body).toString())
            assertTrue(r is Parsed.Ok)
            assertEquals(body.toString(), (r as Parsed.Ok).body.toString())
            assertEquals(seq, r.seq)
        }
    }
}
