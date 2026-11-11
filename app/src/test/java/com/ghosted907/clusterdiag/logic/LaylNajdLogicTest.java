package com.ghosted907.clusterdiag.logic;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LaylNajdLogicTest {
    @Test
    public void headingLettersWrapAroundNorth() {
        assertEquals("N", LaylNajdLogic.headingLetter(0f));
        assertEquals("N", LaylNajdLogic.headingLetter(359f));
        assertEquals("N", LaylNajdLogic.headingLetter(-10f));
        assertEquals("NE", LaylNajdLogic.headingLetter(45f));
        assertEquals("E", LaylNajdLogic.headingLetter(90f));
        assertEquals("S", LaylNajdLogic.headingLetter(180f));
        assertEquals("W", LaylNajdLogic.headingLetter(270f));
        assertEquals("NW", LaylNajdLogic.headingLetter(315f));
        assertEquals("", LaylNajdLogic.headingLetter(Float.NaN));
    }

    @Test
    public void coolantMapsColdToCAndHotToH() {
        assertEquals(0f, LaylNajdLogic.coolantFraction(50f), 0.001f);
        assertEquals(0.5f, LaylNajdLogic.coolantFraction(90f), 0.001f);
        assertEquals(1f, LaylNajdLogic.coolantFraction(130f), 0.001f);
        assertEquals(1f, LaylNajdLogic.coolantFraction(180f), 0.001f);
        assertTrue(Float.isNaN(LaylNajdLogic.coolantFraction(-1f)));
        assertTrue(Float.isNaN(LaylNajdLogic.coolantFraction(Float.NaN)));
    }

    @Test
    public void needlesRestAndLabelsDashWhenUnknown() {
        assertEquals(0f, LaylNajdLogic.speedFraction(Float.NaN), 0f);
        assertEquals(0f, LaylNajdLogic.speedFraction(-1f), 0f);
        assertEquals(0.5f, LaylNajdLogic.speedFraction(110f), 0.001f);
        assertEquals(1f, LaylNajdLogic.speedFraction(240f), 0f);
        assertEquals("—", LaylNajdLogic.speedLabel(-1f));
        assertEquals("86", LaylNajdLogic.speedLabel(86.2f));
        assertEquals(0f, LaylNajdLogic.rpmFraction(-1f), 0f);
        assertEquals("1.3", LaylNajdLogic.rpmLabel(1300f));
        assertEquals("—", LaylNajdLogic.rpmLabel(Float.NaN));
    }

    @Test
    public void gearDriveModeCruiseAndAssistStayHonest() {
        assertEquals("P", LaylNajdLogic.tcuGear(1));
        assertEquals("R", LaylNajdLogic.tcuGear(2));
        assertEquals("N", LaylNajdLogic.tcuGear(3));
        assertEquals("D", LaylNajdLogic.tcuGear(4));
        assertEquals("", LaylNajdLogic.tcuGear(0));
        assertEquals("", LaylNajdLogic.tcuGear(-1));
        assertEquals("D", LaylNajdLogic.gearLetter("", LaylNajdLogic.GEAR_POSITION, 4));
        assertEquals("P", LaylNajdLogic.gearLetter("P", LaylNajdLogic.GEAR_POSITION, 4));
        assertFalse(LaylNajdLogic.driveModeVisible(8));
        assertFalse(LaylNajdLogic.driveModeVisible(-1));
        assertEquals("ECO", LaylNajdLogic.driveModeLabel(0));
        assertEquals("SAND", LaylNajdLogic.driveModeLabel(7));
        assertTrue(LaylNajdLogic.ecoMode(0));
        assertFalse(LaylNajdLogic.showCruise(0f));
        assertFalse(LaylNajdLogic.showCruise(-1f));
        assertFalse(LaylNajdLogic.showCruise(Float.NaN));
        assertTrue(LaylNajdLogic.showCruise(90f));
        assertEquals(LaylNajdLogic.ASSIST_UNKNOWN, LaylNajdLogic.assistState(-1));
        assertEquals(LaylNajdLogic.ASSIST_UNKNOWN, LaylNajdLogic.assistState(Integer.MIN_VALUE));
        assertEquals(LaylNajdLogic.ASSIST_OFF, LaylNajdLogic.assistState(0));
        assertEquals(LaylNajdLogic.ASSIST_ON, LaylNajdLogic.assistState(2));
        assertEquals(LaylNajdLogic.LANE_LDW, LaylNajdLogic.laneTint(1, 1));
        assertEquals(LaylNajdLogic.LANE_LKA, LaylNajdLogic.laneTint(1, 0));
        assertEquals(LaylNajdLogic.LANE_PLAIN, LaylNajdLogic.laneTint(0, -1));
    }

    @Test
    public void fuelTempAndOdometerRejectPlaceholders() {
        assertTrue(LaylNajdLogic.fuelLow(14));
        assertFalse(LaylNajdLogic.fuelLow(15));
        assertFalse(LaylNajdLogic.fuelLow(-1));
        assertEquals(0f, LaylNajdLogic.fuelFraction(-1), 0f);
        assertEquals(0.4f, LaylNajdLogic.fuelFraction(40), 0.001f);
        assertEquals(21f, LaylNajdLogic.outsideTemp(Float.NaN, 21f), 0f);
        assertEquals(18f, LaylNajdLogic.outsideTemp(18f, 21f), 0f);
        assertTrue(Float.isNaN(LaylNajdLogic.outsideTemp(-1f, -1f)));
        assertEquals("21.0°C", LaylNajdLogic.tempLabel(21f));
        assertEquals("—", LaylNajdLogic.tempLabel(Float.NaN));
        assertEquals("", LaylNajdLogic.odometerLabel(-1f));
        assertEquals("إجمالي المسافة المقطوعة 12000 km", LaylNajdLogic.odometerLabel(12000.4f));
        assertEquals("—", LaylNajdLogic.rangeLabel(-1));
        assertEquals("430 km", LaylNajdLogic.rangeLabel(430));
        assertFalse(LaylNajdLogic.validOdometer(Float.NaN));
    }
}
