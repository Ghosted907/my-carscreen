package com.ghosted907.t1launcher
import org.junit.Assert.*
import org.junit.Test
class OemTelemetryTest {
    @Test fun fractionalVehicleReadingsRemainLive() {
        val store = TelemetryStore()
        val def = SignalCatalog.byKey.getValue("pressure_fl")
        store.record(def, 2.42, 1000)
        assertEquals(2.42, store.snapshot(listOf(def), 1001).getValue(def.key).current!!, .0001)
        assertNull(store.snapshot(listOf(def), 20000).getValue(def.key).current)
    }
    @Test fun oemIdsAndTypes() {
        assertEquals(2501, SignalCatalog.byKey.getValue("pressure_fl").address)
        assertEquals(2503, SignalCatalog.byKey.getValue("pressure_fr").address)
        assertEquals(2502, SignalCatalog.byKey.getValue("pressure_rl").address)
        assertEquals(2504, SignalCatalog.byKey.getValue("pressure_rr").address)
        assertEquals(SignalSource.VEHICLE_FLOAT, SignalCatalog.byKey.getValue("pressure_fl").source)
        assertEquals(2511, SignalCatalog.byKey.getValue("temp_rl").address)
        assertEquals(2513, SignalCatalog.byKey.getValue("temp_rr").address)
        assertEquals(1300, SignalCatalog.byKey.getValue("outside_oem").address)
        assertTrue(SignalCatalog.byKey.getValue("steering").accepts(-2.5))
    }
}
