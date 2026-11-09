package com.ghosted907.clusterdiag.logic;

/**
 * Read-only signal ids and the only binder codes this panel may use.
 * There is no setIntProperty code here, and none may be added.
 */
public final class SignalIds {
    public static final String CAR_DESCRIPTOR = "com.autolink.adapterinterface.car.IALCar";
    public static final String LISTENER_DESCRIPTOR =
            "com.autolink.adapterinterface.car.IALCarVehicleControlPropertyListener";
    public static final String VEHICLE_DESCRIPTOR = "com.autolink.app.vehicleservice.IVehicleControl";

    public static final int CAR_GET_INT = 2;
    public static final int CAR_REGISTER = 5;
    public static final int CAR_UNREGISTER = 6;
    public static final int LISTENER_EVENT = 1;
    public static final int VEHICLE_GET_INT = 5;

    /** Scene turn lamps. 1 = on, 0 = off. */
    public static final int SCENE_LEFT = 557843645;
    public static final int SCENE_RIGHT = 557843646;
    /** Cluster copies. Semantics are not confirmed, so the UI shows the raw number. */
    public static final int CLUSTER_LEFT = 557842986;
    public static final int CLUSTER_RIGHT = 557842987;
    /** Candidate for the cluster hazard lamp. Raw value is shown. 1 lights the triangle. */
    public static final int CLUSTER_HAZARD = 557843346;
    /**
     * Emergency-brake flash setting, not the lamp. Shown in the table only.
     * It must not light the hazard triangle.
     */
    public static final int HAZARD_SETTING = 557843908;

    public static final int VEHICLE_LEFT = 3006;
    public static final int VEHICLE_RIGHT = 3007;

    public static final int UNSUPPORTED = -1;
    public static final int UNAVAILABLE = -127;

    public static final int[] CAR_WATCH = {
            SCENE_LEFT,
            SCENE_RIGHT,
            CLUSTER_LEFT,
            CLUSTER_RIGHT,
            CLUSTER_HAZARD,
            HAZARD_SETTING
    };

    private SignalIds() {}

    public static boolean carTransactionAllowed(int code) {
        return code == CAR_GET_INT || code == CAR_REGISTER || code == CAR_UNREGISTER;
    }

    public static boolean isWatchedCarId(int id) {
        for (int watched : CAR_WATCH) {
            if (watched == id) {
                return true;
            }
        }
        return false;
    }
}
