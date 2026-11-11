package com.ghosted907.t1launcher

import org.junit.Assert.*
import org.junit.Test

class TelemetryTest {
    private val speed = SignalCatalog.byKey.getValue("speed")
    private fun read(store: TelemetryStore, time: Long) = store.snapshot(listOf(speed), time).getValue(speed.key)

    @Test fun zeroIsARealSampleButNotAnInitialValue() {
        val store = TelemetryStore()
        assertNull(read(store, 0).current)
        assertEquals(ReadingStatus.WAITING, read(store, 0).status)
        store.record(speed, 0.0, 100)
        assertEquals(0.0, read(store, 100).current!!, 0.0)
    }

    @Test fun blockedPollingExpiresWithoutAnotherSample() {
        val store = TelemetryStore()
        store.record(speed, 81.0, 100)
        assertEquals(ReadingStatus.LIVE, read(store, 2100).status)
        assertEquals(ReadingStatus.STALE, read(store, 2101).status)
        assertNull(read(store, 2101).current)
        assertEquals(81.0, read(store, 2101).value!!, 0.0)
    }

    @Test fun failureImmediatelyRemovesOldValueFromLiveDisplay() {
        val store = TelemetryStore()
        store.record(speed, 90.0, 100)
        store.record(speed, null, 101, "الخدمة رفضت القراءة")
        assertNull(read(store, 101).current)
        assertEquals(100L, read(store, 101).sampledAtMs)
        assertEquals(ReadingStatus.STALE, read(store, 101).status)
    }

    @Test fun sentinelsAreNotEmptyFuelOrNegativeSpeed() {
        for (value in listOf(-1.0, -127.0)) {
            val store = TelemetryStore()
            store.record(speed, value, 1)
            assertEquals(ReadingStatus.UNAVAILABLE, read(store, 1).status)
            assertNull(read(store, 1).value)
        }
    }

    @Test fun rejectsNaNInfinityAndImpossibleSpeed() {
        for (value in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 999.0, -5.0)) {
            val store = TelemetryStore()
            store.record(speed, value, 1)
            assertEquals(ReadingStatus.INVALID, read(store, 1).status)
        }
    }

    @Test fun disconnectInvalidatesValuesAndRecoveryRequiresANewSample() {
        val store = TelemetryStore()
        store.record(speed, 70.0, 100)
        store.disconnect(listOf(speed))
        assertNull(read(store, 101).current)
        store.record(speed, 12.0, 200)
        assertEquals(12.0, read(store, 200).current!!, 0.0)
        assertEquals(200L, read(store, 200).sampledAtMs)
    }

    @Test fun losingOneSourceDoesNotEraseOtherSource() {
        val store = TelemetryStore()
        val fuel = SignalCatalog.byKey.getValue("fuel")
        store.record(speed, 70.0, 100)
        store.record(fuel, 60.0, 100)
        store.disconnect(listOf(speed))
        assertEquals(60.0, store.snapshot(listOf(fuel), 101).getValue("fuel").current!!, 0.0)
    }

    @Test fun invalidGearOrModeNeverLooksLikeParkOrEco() {
        val gear = SignalCatalog.byKey.getValue("gear")
        assertFalse(gear.accepts(0.0))
        assertFalse(gear.accepts(1.5))
        assertEquals("P", gear.format(1.0))
        assertEquals("R", gear.format(2.0))
        assertEquals("N", gear.format(3.0))
        assertEquals("D", gear.format(4.0))
        assertFalse(SignalCatalog.byKey.getValue("drive_mode").accepts(8.0))
    }

    @Test fun slowReadingsHaveTheirOwnExpiryWindow() {
        val store = TelemetryStore()
        val fuel = SignalCatalog.byKey.getValue("fuel")
        store.record(fuel, 10.0, 0)
        assertNotNull(store.snapshot(listOf(fuel), 15000).getValue("fuel").current)
        assertNull(store.snapshot(listOf(fuel), 15001).getValue("fuel").current)
    }

    @Test fun catalogPreservesMetadataButDoesNotReadZonedOrVectorValues() {
        val catalog = OemCatalogParser.parse(sequenceOf("# test", "559941888\tSPEED\tFLOAT\tGLOBAL\tread",
            "392168201\tTIRE_PRESSURE\tFLOAT\tZONED\tmetadata"))
        assertEquals(SignalSource.CAR_FLOAT, catalog.first().definition()!!.source)
        assertNull(catalog.last().definition())
        assertNull(OemProperty(392168201, "TIRE_PRESSURE", "FLOAT", "ZONED", true).definition())
        assertNull(OemProperty(123, "VECTOR", "FLOAT_VEC", "GLOBAL", true).definition())
    }

    @Test fun everyHalGetterMatchesTheOemTypeBits() {
        SignalCatalog.all.forEach { definition ->
            if (definition.source == SignalSource.CAR_INT) assertEquals(definition.key, 0x400000, definition.address and 0xff0000)
            if (definition.source == SignalSource.CAR_FLOAT) assertEquals(definition.key, 0x600000, definition.address and 0xff0000)
        }
        assertEquals(SignalCatalog.all.size, SignalCatalog.all.map { it.key }.distinct().size)
    }

    @Test fun scalarClusterReadAllowlistExcludesCommandsAndVoidGetters() {
        for (code in listOf(67, 74, 104, 105, 116, 205, 215, 219)) assertFalse(SignalCatalog.clusterReads.containsKey(code))
        assertEquals(SignalSource.CLUSTER_FLOAT, SignalCatalog.clusterReads.getValue(102).source)
        assertEquals(SignalSource.CLUSTER_FLOAT, SignalCatalog.clusterReads.getValue(186).source)
        assertEquals(SignalSource.CLUSTER_INT, SignalCatalog.clusterReads.getValue(77).source)
    }

    @Test fun validHalSpeedHasPriorityEvenWhenZero() {
        val display = TelemetryPresentation.speed(mapOf("speed" to SignalReading(0.0, status = ReadingStatus.LIVE),
            "speed_cluster" to SignalReading(20.0, status = ReadingStatus.LIVE)))
        assertEquals(0.0, display.value!!, 0.0)
        assertEquals("كم/س", display.unit)
    }

    @Test fun fallbackDoesNotPretendClusterUnitsAreKmh() {
        val display = TelemetryPresentation.speed(mapOf("speed" to SignalReading(80.0, status = ReadingStatus.STALE),
            "speed_cluster" to SignalReading(30.0, status = ReadingStatus.LIVE)))
        assertEquals(30.0, display.value!!, 0.0)
        assertEquals("وحدة العدادات", display.unit)
    }

    @Test fun losingBothSourcesCannotKeepAnOldSpeedLive() {
        val display = TelemetryPresentation.speed(mapOf("speed" to SignalReading(80.0, status = ReadingStatus.STALE),
            "speed_cluster" to SignalReading(30.0, status = ReadingStatus.STALE)))
        assertNull(display.value)
    }
}
