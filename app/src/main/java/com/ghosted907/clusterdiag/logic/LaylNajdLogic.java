package com.ghosted907.clusterdiag.logic;

/**
 * Pure display mapping for the Layl Najd cluster. No binder calls live here.
 * Missing, NaN, and -1 stay unknown so the view can show a dash and rest the needle.
 */
public final class LaylNajdLogic {
    public static final float SPEED_MAX = 220f;
    public static final float RPM_MAX = 8000f;
    public static final float COOLANT_COLD = 50f;
    public static final float COOLANT_HOT = 130f;
    public static final int GEAR_POSITION = 557843105;

    public static final int LANE_PLAIN = 0;
    public static final int LANE_LKA = 1;
    public static final int LANE_LDW = 2;

    public static final int ASSIST_UNKNOWN = -1;
    public static final int ASSIST_OFF = 0;
    public static final int ASSIST_ON = 1;

    private static final String[] HEADING = {
            "N", "NE", "E", "SE", "S", "SW", "W", "NW"
    };

    private LaylNajdLogic() {}

    /** Compass letter. Degrees wrap; the slice is 45° centered on each cardinal. */
    public static String headingLetter(float degrees) {
        if (Float.isNaN(degrees)) {
            return "";
        }
        float wrapped = degrees % 360f;
        if (wrapped < 0f) {
            wrapped += 360f;
        }
        int index = Math.round(wrapped / 45f) % 8;
        return HEADING[index];
    }

    public static float clamp01(float value) {
        if (value < 0f) {
            return 0f;
        }
        if (value > 1f) {
            return 1f;
        }
        return value;
    }

    /** 0 at the 0 mark. Unknown speed stays at rest instead of inventing a reading. */
    public static float speedFraction(float kmh) {
        if (!GaugeFormat.validSpeed(kmh)) {
            return 0f;
        }
        return clamp01(kmh / SPEED_MAX);
    }

    public static float rpmFraction(float rpm) {
        if (!GaugeFormat.validRpm(rpm)) {
            return 0f;
        }
        return clamp01(rpm / RPM_MAX);
    }

    /** One decimal thousand-rpm, matching a hub reading such as 1.3. */
    public static String rpmLabel(float rpm) {
        if (!GaugeFormat.validRpm(rpm)) {
            return "—";
        }
        return String.format(java.util.Locale.US, "%.1f", rpm / 1000f);
    }

    public static String speedLabel(float kmh) {
        if (!GaugeFormat.validSpeed(kmh)) {
            return "—";
        }
        return Integer.toString(Math.round(kmh));
    }

    /** TCU display gear 557843105: P=1, R=2, N=3, D=4. */
    public static String tcuGear(int value) {
        switch (value) {
            case 1:
                return "P";
            case 2:
                return "R";
            case 3:
                return "N";
            case 4:
                return "D";
            default:
                return "";
        }
    }

    public static String gearLetter(String mapped, int gearProp, int gearValue) {
        if (mapped != null && mapped.length() > 0) {
            return mapped;
        }
        if (gearProp == GEAR_POSITION) {
            return tcuGear(gearValue);
        }
        return "";
    }

    public static boolean driveModeVisible(int mode) {
        return mode >= 0 && mode <= 7;
    }

    public static String driveModeLabel(int mode) {
        switch (mode) {
            case 0:
                return "ECO";
            case 1:
                return "NORMAL";
            case 2:
                return "SPORT";
            case 3:
                return "SNOW";
            case 4:
                return "MUD";
            case 5:
                return "ROCK";
            case 6:
                return "OFF-ROAD";
            case 7:
                return "SAND";
            default:
                return "";
        }
    }

    /** 0 is ECO (green). Other visible modes stay neutral in the view. */
    public static boolean ecoMode(int mode) {
        return mode == 0;
    }

    /**
     * Coolant arc. About 50°C sits on C and about 130°C sits on H.
     * Returns NaN when the reading is not a plausible temperature.
     */
    public static float coolantFraction(float celsius) {
        if (!validEngine(celsius)) {
            return Float.NaN;
        }
        return clamp01((celsius - COOLANT_COLD) / (COOLANT_HOT - COOLANT_COLD));
    }

    public static boolean validEngine(float celsius) {
        return !Float.isNaN(celsius) && celsius != -1f && celsius > -40f && celsius < 200f;
    }

    public static boolean validOdometer(float km) {
        return !Float.isNaN(km) && km >= 0f && km < 10000000f && km != -1f;
    }

    /** Hidden at 0 and at any invalid reading. */
    public static boolean showCruise(float kmh) {
        return GaugeFormat.validSpeed(kmh) && kmh > 0f;
    }

    public static float outsideTemp(float primary, float fallback) {
        if (GaugeFormat.validTemp(primary)) {
            return primary;
        }
        if (GaugeFormat.validTemp(fallback)) {
            return fallback;
        }
        return Float.NaN;
    }

    public static String tempLabel(float celsius) {
        if (!GaugeFormat.validTemp(celsius)) {
            return "—";
        }
        return String.format(java.util.Locale.US, "%.1f°C", celsius);
    }

    public static String odometerLabel(float km) {
        if (!validOdometer(km)) {
            return "";
        }
        return "إجمالي المسافة المقطوعة " + Math.round(km) + " km";
    }

    public static String rangeLabel(int km) {
        if (km < 0 || km == Integer.MIN_VALUE) {
            return "—";
        }
        return km + " km";
    }

    public static float fuelFraction(int percent) {
        if (percent < 0 || percent > 100) {
            return 0f;
        }
        return percent / 100f;
    }

    public static boolean fuelLow(int percent) {
        return percent >= 0 && percent <= 100 && percent < 15;
    }

    /** -1 and a missing value are unknown. 0 is off. Any other value is active. */
    public static int assistState(int raw) {
        if (raw == Integer.MIN_VALUE || raw == -1) {
            return ASSIST_UNKNOWN;
        }
        if (raw == 0) {
            return ASSIST_OFF;
        }
        return ASSIST_ON;
    }

    /** LDW warning wins over an active LKA so the lane lines turn amber. */
    public static int laneTint(int lka, int ldw) {
        if (assistState(ldw) == ASSIST_ON) {
            return LANE_LDW;
        }
        if (assistState(lka) == ASSIST_ON) {
            return LANE_LKA;
        }
        return LANE_PLAIN;
    }
}
