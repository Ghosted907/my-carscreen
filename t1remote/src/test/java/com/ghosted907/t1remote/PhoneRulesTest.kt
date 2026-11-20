package com.ghosted907.t1remote

import com.ghosted907.t1remote.protocol.RemoteProtocol
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** T1 Remote 1.4.0: capability negotiation, freshness, sync, de-duplication and disconnect rules. */
class PhoneRulesTest {
    private fun ping(rev: Int?, vararg ops: String, runIds: List<String>? = null) = JSONObject().apply {
        if (rev != null) put("rev", rev)
        if (ops.isNotEmpty()) put("ops", JSONArray(ops.toList()))
        if (runIds != null) put("run_ids", JSONArray(runIds))
    }

    // ---- capability negotiation
    @Test fun rev3LauncherIsNotCalledOutdatedByRev4Phone() {
        assertTrue(ClientRules.supportsRev3(ping(3)))
        assertFalse(ClientRules.supportsRev4(ping(3)))
        assertTrue(ClientRules.supportsRev3(ping(4)))
        assertTrue(ClientRules.supportsRev4(ping(4)))
        assertFalse(ClientRules.supportsRev3(ping(null)))   // 1.2 launcher: no rev field
    }

    @Test fun newOpsAreOfferedOnlyWhenAdvertised() {
        val rev3 = ping(3, *RemoteProtocol.OPS_REV3.toTypedArray())
        assertFalse(ClientRules.supports(rev3, RemoteProtocol.OP_HOME))
        assertFalse(ClientRules.supports(rev3, RemoteProtocol.OP_OFFROAD))
        assertTrue(ClientRules.supports(rev3, RemoteProtocol.OP_DATA))
        val rev4 = ping(4, *RemoteProtocol.OPS.toTypedArray())
        assertTrue(ClientRules.supports(rev4, RemoteProtocol.OP_NOTE))
        assertTrue(ClientRules.supports(rev4, RemoteProtocol.OP_OEM_PROPS))
        // Legacy launcher without an ops list: only the 1.1 ops.
        assertFalse(ClientRules.supports(JSONObject(), RemoteProtocol.OP_HOME))
    }

    @Test fun runIdsFollowTheLauncherList() {
        assertTrue(ClientRules.supportsRun(ping(4, runIds = listOf("media_next")), "media_next"))
        assertFalse(ClientRules.supportsRun(ping(4, runIds = listOf("tasks_test")), "media_next"))
        // A rev-3 launcher (no run_ids) gets the old buttons but never the rev-4 media/system ones.
        assertTrue(ClientRules.supportsRun(ping(3), "tasks_test"))
        assertFalse(ClientRules.supportsRun(ping(3), "media_next"))
        assertFalse(ClientRules.supportsRun(ping(null), "tasks_test"))
    }

    // ---- readings / freshness (no fake values)
    @Test fun missingReadingIsUnavailableNeverZero() {
        val r = Reading.parse(JSONObject().put("key", "fuel").put("label", "الوقود").put("group", "x").put("value", "—").put("unit", "%").put("status", "UNAVAILABLE"))
        assertEquals("غير متاح", r.display())
        assertNull(r.raw)
        assertNull(r.ageMs)
        assertFalse(r.display().contains("0"))
    }

    @Test fun staleReadingShowsLastValueMarkedOld() {
        val r = Reading.parse(JSONObject().put("key", "speed").put("value", "—").put("unit", "كم/س").put("status", "STALE")
            .put("last", "42").put("age_ms", 9000))
        assertEquals("42 كم/س (قديمة)", r.display())
        assertTrue(Freshness.line(r).contains("منذ 9 ث"))
    }

    @Test fun liveReadingParsesRev4Fields() {
        val r = Reading.parse(JSONObject().put("key", "rpm").put("value", "800").put("unit", "د/د").put("status", "LIVE")
            .put("raw", 800.0).put("age_ms", 200).put("confirmed", true).put("status_label", "مباشر"))
        assertEquals("800 د/د", r.display())
        assertEquals(800.0, r.raw!!, 0.0)
        assertTrue(r.confirmed)
        assertEquals("مباشر · الآن", Freshness.line(r))
    }

    @Test fun rev3ReadingWithoutAgeStillParses() {
        val r = Reading.parse(JSONObject().put("key", "gear").put("value", "D").put("unit", "").put("status", "LIVE"))
        assertEquals("D", r.display())
        assertNull(Freshness.age(r.ageMs))
    }

    @Test fun replyFreshness() {
        assertTrue(Freshness.replyStale(0, 10_000))
        assertFalse(Freshness.replyStale(9_000, 10_000))
        assertTrue(Freshness.replyStale(1_000, 10_000))
    }

    @Test fun tyrePressureConversionRejectsBrokenValues() {
        assertEquals(36.26, Tyres.psi(2.5)!!, 0.01)
        assertNull(Tyres.psi(null))
        assertNull(Tyres.psi(0.0))
        assertNull(Tyres.psi(Double.NaN))
        assertNull(Tyres.psi(9.0))
        assertEquals(listOf("fl", "fr", "rl", "rr"), Tyres.ORDER)
    }

    // ---- A/C: sent vs confirmed, stale replies
    @Test fun climateConfirmsOnlyFromReadback() {
        val s = ClimateSync()
        val stamp0 = s.stamp()
        s.onState(stamp0, mapOf("temp_l" to 21.0), 0)
        assertEquals(ClimateSync.State.LIVE, s.state("temp_l", 0))
        s.beginSend("temp_l", 23.0, 100)
        assertEquals(23.0, s.shown("temp_l")!!, 0.0)
        assertEquals(ClimateSync.State.PENDING, s.state("temp_l", 100))
        s.onState(s.stamp(), mapOf("temp_l" to 23.0), 900)
        assertEquals(ClimateSync.State.CONFIRMED, s.state("temp_l", 1000))
        assertEquals(ClimateSync.State.LIVE, s.state("temp_l", 9000))
    }

    @Test fun staleReplyNeverOverridesNewerEdit() {
        val s = ClimateSync()
        val before = s.stamp()                    // request leaves before the edit
        s.beginSend("fan", 5.0, 100)
        s.onState(before, mapOf("fan" to 2.0), 200) // old reply arrives after the edit
        assertEquals(5.0, s.shown("fan")!!, 0.0)
        assertEquals(ClimateSync.State.PENDING, s.state("fan", 200))
        // Two quick edits: a reply for the first does not confirm the second.
        val mid = s.stamp()
        s.beginSend("fan", 6.0, 300)
        s.onState(mid, mapOf("fan" to 5.0), 400)
        assertEquals(6.0, s.shown("fan")!!, 0.0)
    }

    @Test fun unconfirmedAfterTimeoutAndNoGuessWhenUnknown() {
        val s = ClimateSync(confirmMs = 1000)
        assertNull(s.shown("temp_r"))
        assertEquals(ClimateSync.State.UNKNOWN, s.state("temp_r", 0))
        s.beginSend("temp_r", 24.0, 0)
        s.onState(s.stamp(), mapOf("temp_r" to null), 2000)
        assertEquals(ClimateSync.State.UNCONFIRMED, s.state("temp_r", 2000))
        assertNull(s.shown("temp_r"))   // no fake value: the car never confirmed 24
    }

    @Test fun refusedSendIsDroppedAndDisconnectClearsEverything() {
        val s = ClimateSync()
        val g = s.beginSend("ac", 1.0, 0)
        s.sendFailed("ac", g)
        assertNull(s.shown("ac"))
        s.onState(s.stamp(), mapOf("power" to 1.0), 0)
        s.beginSend("mode", 2.0, 0)
        s.clearOnDisconnect()
        assertNull(s.shown("power"))
        assertNull(s.shown("mode"))
    }

    // ---- vehicle: one at a time, no repeat, no resend
    @Test fun vehicleGateAllowsOneCommandAtATime() {
        val g = VehicleGate()
        assertTrue(g.tryStart("windows_open"))
        assertFalse(g.tryStart("windows_open"))   // no repeat while pending
        assertFalse(g.tryStart("roof_close"))
        g.finish()
        assertTrue(g.tryStart("roof_close"))
    }

    @Test fun vehicleGateRejectsUnapprovedIdsAndNeverResends() {
        val g = VehicleGate()
        assertFalse(g.tryStart("tailgate_open"))
        assertFalse(g.tryStart("lights_on"))
        assertTrue(g.tryStart("shade_half"))
        val msg = g.onDisconnect()
        assertNotNull(msg)
        assertTrue(msg!!.contains("لن يُعاد"))
        assertNull(g.pending)
        assertNull(g.onDisconnect())                // nothing queued for after the reconnect
        RemoteProtocol.VEHICLE_IDS.forEach { val v = VehicleGate(); assertTrue(it, v.tryStart(it)) }
    }

    // ---- home / voice
    @Test fun greetingByRiyadhHour() {
        assertEquals("صباح الخير", Greeting.forHour(7))
        assertEquals("مساء الخير", Greeting.forHour(13))
        assertEquals("مساء الخير", Greeting.forHour(1))
    }

    @Test fun compassLabels() {
        assertEquals("شمال", Compass.label(0.0))
        assertEquals("شمال", Compass.label(359.0))
        assertEquals("شرق", Compass.label(90.0))
        assertEquals("جنوب غرب", Compass.label(-135.0))
        assertNull(Compass.label(Double.NaN))
        assertNull(Compass.label(null))
    }

    @Test fun voiceOnlyMapsToWhitelistedNonVehicleCommands() {
        assertEquals("camera", VoiceMap.match("افتح الكاميرا")!!.body.optString("page"))
        assertEquals("youtube", VoiceMap.match("شغل يوتيوب")!!.body.optString("id"))
        assertEquals("classic", VoiceMap.match("كلاسيك")!!.body.optString("action"))
        assertNull(VoiceMap.match("افتح الشباك"))
        assertNull(VoiceMap.match("افتح الشنطة"))
        assertNull(VoiceMap.match(""))
        listOf("افتح الكاميرا", "كلاسيك", "اخفاء", "يوتيوب", "ويز", "كاربلي", "بلوتوث", "المكيف", "الرئيسية").forEach { t ->
            val m = VoiceMap.match(t)!!
            assertNull(t, RemoteProtocol.validate(m.body))
            assertFalse(t, m.body.optString("op") in setOf(RemoteProtocol.OP_VEHICLE, RemoteProtocol.OP_CLIMATE, RemoteProtocol.OP_SET))
        }
    }

    @Test fun rev4CommandsRoundTrip() {
        var seq = 0L
        listOf(JSONObject().put("op", "home"), JSONObject().put("op", "note").put("text", "مرحبا"),
            JSONObject().put("op", "offroad"), JSONObject().put("op", "oem_props").put("query", "hvac").put("page", 1).put("watch", false),
            JSONObject().put("op", "run").put("id", "media_next")).forEach { b ->
            val r = RemoteProtocol.parse(RemoteProtocol.command(++seq, b).toString())
            assertTrue(b.toString(), r is RemoteProtocol.Parsed.Ok)
            assertNull(b.toString(), RemoteProtocol.validate(b))
        }
    }
}
