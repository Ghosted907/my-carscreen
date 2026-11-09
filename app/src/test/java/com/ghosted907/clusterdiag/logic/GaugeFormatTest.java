package com.ghosted907.clusterdiag.logic;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GaugeFormatTest {
    @Test
    public void aospGearLetters() {
        assertEquals("P", GaugeFormat.gearLetter(4));
        assertEquals("R", GaugeFormat.gearLetter(2));
        assertEquals("N", GaugeFormat.gearLetter(1));
        assertEquals("D", GaugeFormat.gearLetter(8));
        assertEquals("", GaugeFormat.gearLetter(-1));
        assertEquals("", GaugeFormat.gearLetter(0));
    }

    @Test
    public void customMapOverridesAndDefaultPropStaysRaw() {
        assertEquals("D", GaugeFormat.letterFor(4, "P=1,R=2,N=3,D=4", GaugeFormat.GEAR_POSITION));
        assertEquals("P", GaugeFormat.letterFor(1, "P=1, R=2, N=3, D=4", GaugeFormat.GEAR_POSITION));
        assertEquals("", GaugeFormat.letterFor(0, "", GaugeFormat.GEAR_POSITION));
        assertEquals("P", GaugeFormat.letterFor(4, "", GaugeFormat.GEAR_AOSP));
        assertEquals(557843105, GaugeFormat.GEAR_POSITION);
        assertEquals(11, GaugeFormat.GEAR_CANDIDATES.length);
        java.util.HashMap<Integer, Integer> values = new java.util.HashMap<Integer, Integer>();
        values.put(Integer.valueOf(GaugeFormat.GEAR_POSITION), Integer.valueOf(7));
        String snap = GaugeFormat.gearSnap(values);
        assertTrue(snap.startsWith("GEARSNAP 557843105=7 557843106=?"));
    }

    @Test
    public void driveModeAndRanges() {
        assertEquals("NORMAL MODE", GaugeFormat.driveMode(1));
        assertEquals("ECO MODE", GaugeFormat.driveMode(0));
        assertEquals("--", GaugeFormat.driveMode(8));
        assertEquals("--", GaugeFormat.driveMode(-1));
        assertTrue(GaugeFormat.validSpeed(86f));
        assertFalse(GaugeFormat.validSpeed(-1f));
        assertFalse(GaugeFormat.validRpm(Float.NaN));
        assertEquals("1.76", GaugeFormat.rpmNumber(1760f));
        assertEquals("--", GaugeFormat.speedNumber(-1f));
    }
}
