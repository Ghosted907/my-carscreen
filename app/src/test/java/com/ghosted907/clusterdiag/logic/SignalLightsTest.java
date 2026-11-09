package com.ghosted907.clusterdiag.logic;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SignalLightsTest {
    @Test
    public void onlyReadCodesAreAllowed() {
        assertTrue(SignalIds.carTransactionAllowed(2));
        assertTrue(SignalIds.carTransactionAllowed(5));
        assertTrue(SignalIds.carTransactionAllowed(6));
        assertFalse(SignalIds.carTransactionAllowed(1));
        assertFalse(SignalIds.carTransactionAllowed(3));
        assertFalse(SignalIds.carTransactionAllowed(4));
        assertFalse(SignalIds.carTransactionAllowed(7));
        assertEquals(557843645, SignalIds.SCENE_LEFT);
        assertEquals(557843646, SignalIds.SCENE_RIGHT);
        assertEquals(557843908, SignalIds.HAZARD_SETTING);
        assertEquals(3006, SignalIds.VEHICLE_LEFT);
        assertEquals(3007, SignalIds.VEHICLE_RIGHT);
    }

    @Test
    public void sceneOnOffBeatsTheVehicleFallback() {
        assertEquals(1, SignalLights.drivingValue(Integer.valueOf(1), Integer.valueOf(0)));
        assertEquals(0, SignalLights.drivingValue(Integer.valueOf(0), Integer.valueOf(1)));
        assertEquals(1, SignalLights.drivingValue(Integer.valueOf(-1), Integer.valueOf(1)));
        assertEquals(0, SignalLights.drivingValue(null, Integer.valueOf(0)));
        assertEquals(Integer.MIN_VALUE, SignalLights.drivingValue(Integer.valueOf(-127), Integer.valueOf(-1)));
    }

    @Test
    public void blinkOffHoldsTheLampFor700ms() {
        assertTrue(SignalLights.lampOn(1, 0L, 1000L));
        assertTrue(SignalLights.lampOn(0, 1000L, 1699L));
        assertFalse(SignalLights.lampOn(0, 1000L, 1700L));
        assertFalse(SignalLights.lampOn(0, 0L, 5000L));
        assertFalse(SignalLights.lampOn(-1, 0L, 5000L));
    }

    @Test
    public void hazardTriangleUsesBothLampsOrTheCandidateNotTheSetting() {
        assertFalse(SignalLights.hazardTriangle(false, false, 0));
        assertFalse(SignalLights.hazardTriangle(true, false, 0));
        assertTrue(SignalLights.hazardTriangle(true, true, 0));
        assertTrue(SignalLights.hazardTriangle(false, false, 1));
        assertFalse(SignalLights.hazardTriangle(false, false, SignalIds.HAZARD_SETTING));
        assertFalse(SignalIds.isWatchedCarId(3006));
        assertTrue(SignalIds.isWatchedCarId(SignalIds.HAZARD_SETTING));
    }
}
