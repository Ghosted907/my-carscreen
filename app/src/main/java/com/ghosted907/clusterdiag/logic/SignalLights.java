package com.ghosted907.clusterdiag.logic;

/**
 * Turns a blinking 0/1 reading into a steady lamp, and decides when the hazard
 * triangle is lit. The emergency-brake flash setting is not an input.
 */
public final class SignalLights {
    public static final long BLINK_HOLD_MS = 700L;

    private SignalLights() {}

    /** Scene value wins when it is a real on/off. Otherwise the vehicle key. */
    public static int drivingValue(Integer scene, Integer vehicle) {
        if (scene != null && (scene.intValue() == 0 || scene.intValue() == 1)) {
            return scene.intValue();
        }
        if (vehicle != null && (vehicle.intValue() == 0 || vehicle.intValue() == 1)) {
            return vehicle.intValue();
        }
        return Integer.MIN_VALUE;
    }

    public static boolean lampOn(int driving, long lastOnUptime, long now) {
        if (driving == 1) {
            return true;
        }
        return lastOnUptime > 0L && now - lastOnUptime < BLINK_HOLD_MS;
    }

    /** Both turn lamps, or the cluster hazard candidate equal to 1. */
    public static boolean hazardTriangle(boolean leftOn, boolean rightOn, int hazardCandidate) {
        return (leftOn && rightOn) || hazardCandidate == 1;
    }
}
