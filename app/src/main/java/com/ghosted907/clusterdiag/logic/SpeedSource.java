package com.ghosted907.clusterdiag.logic;

/**
 * Displayed speed. A valid IALCar sample wins. CiService is the fallback and
 * keeps replacing the shown value for as long as IALCar is not the active source.
 */
public final class SpeedSource {
    private SpeedSource() {}

    /**
     * @param carActive latest IALCar speed sample is valid and the binder is still up
     * @param current speed already stored for the cluster, or {@link Float#NaN}
     * @param ciSpeed latest CiService speed sample
     */
    public static float choose(boolean carActive, float current, float ciSpeed) {
        if (carActive) {
            return current;
        }
        if (GaugeFormat.validSpeed(ciSpeed)) {
            return ciSpeed;
        }
        return current;
    }
}
