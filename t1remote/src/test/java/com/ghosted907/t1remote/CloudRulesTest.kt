package com.ghosted907.t1remote

import com.ghosted907.t1remote.protocol.CloudConfig
import com.ghosted907.t1remote.protocol.CloudGate
import org.junit.Assert.*
import org.junit.Test

class CloudRulesTest {
    @Test fun pairingCannotChangeEndpointOrCarryAHostKey() {
        val room = "a".repeat(24); val phone = "b".repeat(32)
        val pair = CloudConfig.pair("  $room:$phone  ")!!
        assertEquals("${CloudConfig.RELAY}/v1/rooms/$room/phone", pair.url)
        assertNull(CloudConfig.pair("https://evil.example/$room:$phone"))
        assertNull(CloudConfig.pair("$room:${"c".repeat(64)}"))
        assertNull(CloudConfig.pair("$room:$phone/host"))
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", CloudConfig.sha256(""))
    }
    @Test fun expiredReplayedAndPreviousSessionCommandsAreRejected() {
        val g = CloudGate(); g.ready(100000, 500); g.begin("one")
        assertTrue(g.accept("one", 1, 110000, 600))
        assertFalse(g.accept("one", 1, 110000, 700))
        assertFalse(g.accept("one", 2, 110000, 11000))
        g.begin("two")
        assertFalse(g.accept("one", 3, 120000, 11001))
        assertTrue(g.accept("two", 1, 120000, 11001))
        g.end("one") // A late close from an old phone must not end the current session.
        assertTrue(g.accept("two", 2, 120000, 11002))
        g.end("two")
        assertFalse(g.accept("two", 3, 120000, 11003))
    }
    @Test fun noCommandBeforeServerClockAndNoUnboundedDeadline() {
        val g = CloudGate(); g.begin("s")
        assertFalse(g.accept("s", 1, Long.MAX_VALUE, 100))
        g.ready(100000, 100); g.begin("s")
        assertFalse(g.accept("s", 1, Long.MAX_VALUE, 101))
        assertTrue(g.accept("s", 1, 110000, 101))
        g.reset(); assertFalse(g.accept("s", 2, 110000, 102))
    }
}
