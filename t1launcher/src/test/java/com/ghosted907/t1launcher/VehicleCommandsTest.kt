package com.ghosted907.t1launcher

import org.junit.Assert.*
import org.junit.Test

class VehicleCommandsTest {
    @Test fun allowListHasOnlyNineApprovedActions() {
        assertEquals(9, VehicleCommands.all.size)
        assertEquals(9, VehicleCommands.all.map { it.id }.distinct().size)
        assertTrue(VehicleCommands.all.none { it.id.contains("tail") || it.id.contains("gate") })
    }
    @Test fun exactWindowsPayloads() {
        assertEquals(listOf(2,2,2,2), VehicleCommands.all[0].args)
        assertEquals(listOf(1,1,1,1), VehicleCommands.all[1].args)
        assertEquals(listOf(3,0,0,3), VehicleCommands.all[2].args)
        VehicleCommands.all.take(3).forEach { assertEquals(12, it.transaction) }
    }
    @Test fun percentageEnumsAreTranslatedNotSentAsVehicleKeys() {
        assertEquals(listOf(557843525,0,11), VehicleCommands.all[3].args)
        assertEquals(listOf(557843525,0,1), VehicleCommands.all[4].args)
        assertEquals(listOf(557843525,0,12), VehicleCommands.all[5].args)
        assertEquals(listOf(557843527,0,11), VehicleCommands.all[6].args)
        assertEquals(listOf(557843527,0,1), VehicleCommands.all[7].args)
        assertEquals(listOf(557843527,0,6), VehicleCommands.all[8].args)
        VehicleCommands.all.drop(3).forEach { assertEquals(1, it.transaction) }
    }
    @Test fun tailgateFailsClosed() {
        assertFalse(VehicleCommands.tailgateAllowed(0.0, 1.0, 2000, false))
        assertFalse(VehicleCommands.tailgateAllowed(null, 1.0, 2000, true))
        assertFalse(VehicleCommands.tailgateAllowed(Double.NaN, 1.0, 2000, true))
        assertFalse(VehicleCommands.tailgateAllowed(.1, 1.0, 2000, true))
        assertFalse(VehicleCommands.tailgateAllowed(0.0, 4.0, 2000, true))
        assertFalse(VehicleCommands.tailgateAllowed(0.0, 1.0, 1999, true))
        assertTrue(VehicleCommands.tailgateAllowed(0.0, 1.0, 2000, true))
        assertTrue(VehicleCommands.tailgateAllowed(0.0, null, 2000, true))
    }
}
