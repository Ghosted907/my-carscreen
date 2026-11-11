package com.ghosted907.t1launcher
import org.junit.Assert.*
import org.junit.Test
class ButtonJsonTest {
    @Test fun roundTrip() {
        val buttons = ButtonConfig.defaults() + ButtonConfig("free", "com.waze/.MainActivity", "ويز", .35f, .6f, false)
        assertEquals(buttons, ButtonJson.decode(ButtonJson.encode(buttons)))
        assertEquals(emptyList<ButtonConfig>(), ButtonJson.decode(ButtonJson.encode(emptyList())))
    }
    @Test fun rejectsBadImportWithoutRepairingIt() {
        val good = ButtonJson.encode(ButtonConfig.defaults())
        val version = org.json.JSONObject(good).put("schema", 99).toString()
        val position = org.json.JSONObject(good).also { it.getJSONArray("buttons").getJSONObject(0).put("y", 9.64) }.toString()
        listOf("bad-json", version, position, " ".repeat(65537)).forEach { bad ->
            assertTrue("Should reject: " + bad.take(30), runCatching { ButtonJson.decode(bad) }.isFailure)
        }
    }
}
