package com.ghosted907.clusterdiag.logic;

/** Display helpers for the cluster. Gear mapping is configurable; AOSP is only the fallback for 289408000. */
public final class GaugeFormat {
    public static final int GEAR_AOSP = 289408000;
    public static final int GEAR_PRND = 557867187;
    public static final int GEAR_GBC = 557843716;
    public static final int GEAR_HCU = 557843719;
    public static final int GEAR_REVERSE = 557871124;
    /** Most likely TCU display position from the HAL dump. */
    public static final int GEAR_POSITION = 557843105;
    public static final int GEAR_LEVER = 557843106;
    public static final int GEAR_TARGET = 557843107;
    public static final int GEAR_SHIFT_INFO = 557843332;
    public static final int GEAR_START_PN = 557843321;
    public static final int GEAR_PARK_LAMP = 557844494;

    public static final int[] GEAR_CANDIDATES = {
            GEAR_POSITION,
            GEAR_LEVER,
            GEAR_TARGET,
            GEAR_SHIFT_INFO,
            GEAR_START_PN,
            GEAR_PARK_LAMP,
            GEAR_PRND,
            GEAR_GBC,
            GEAR_HCU,
            GEAR_AOSP,
            GEAR_REVERSE
    };

    private GaugeFormat() {}

    /**
     * Uses {@code gearMap} ("P=1,R=2,N=3,D=4") when it is set.
     * With an empty map, only the AOSP property 289408000 is translated (P=4, R=2, N=1, D=8).
     * The default property 557843105 has no confirmed map, so the raw number is shown instead.
     */
    public static String letterFor(int value, String gearMap, int gearProp) {
        if (gearMap != null && gearMap.trim().length() > 0) {
            String[] parts = gearMap.split(",");
            for (int i = 0; i < parts.length; i++) {
                String token = parts[i].trim();
                int eq = token.indexOf('=');
                if (eq <= 0 || eq == token.length() - 1) {
                    continue;
                }
                String letter = token.substring(0, eq).trim().toUpperCase(java.util.Locale.US);
                if (!isGearLetter(letter)) {
                    continue;
                }
                try {
                    if (Integer.parseInt(token.substring(eq + 1).trim()) == value) {
                        return letter;
                    }
                } catch (NumberFormatException ignored) {
                    // Skip a bad token and keep looking.
                }
            }
            return "";
        }
        if (gearProp == GEAR_AOSP) {
            return gearLetter(value);
        }
        return "";
    }

    public static String gearSnap(java.util.Map<Integer, Integer> values) {
        StringBuilder builder = new StringBuilder("GEARSNAP");
        for (int i = 0; i < GEAR_CANDIDATES.length; i++) {
            int id = GEAR_CANDIDATES[i];
            Integer value = values == null ? null : values.get(Integer.valueOf(id));
            builder.append(' ').append(id).append('=');
            builder.append(value == null ? "?" : value.toString());
        }
        return builder.toString();
    }

    private static boolean isGearLetter(String letter) {
        return "P".equals(letter) || "R".equals(letter) || "N".equals(letter) || "D".equals(letter);
    }

    /** P=4, R=2, N=1, D=8. Anything else is unknown. */
    public static String gearLetter(int aospValue) {
        switch (aospValue) {
            case 4:
                return "P";
            case 2:
                return "R";
            case 1:
                return "N";
            case 8:
                return "D";
            default:
                return "";
        }
    }

    public static String driveMode(int mode) {
        switch (mode) {
            case 0:
                return "ECO MODE";
            case 1:
                return "NORMAL MODE";
            case 2:
                return "SPORT MODE";
            case 3:
                return "SNOW MODE";
            case 4:
                return "MUD MODE";
            case 5:
                return "ROCK MODE";
            case 6:
                return "OFF-ROAD";
            case 7:
                return "SAND MODE";
            default:
                return "--";
        }
    }

    public static boolean validSpeed(float kmh) {
        return !Float.isNaN(kmh) && kmh >= 0f && kmh <= 400f && kmh != -1f;
    }

    public static boolean validRpm(float rpm) {
        return !Float.isNaN(rpm) && rpm >= 0f && rpm <= 20000f && rpm != -1f;
    }

    public static boolean validTemp(float celsius) {
        return !Float.isNaN(celsius) && celsius > -80f && celsius < 80f && celsius != -1f;
    }

    public static String speedNumber(float kmh) {
        if (!validSpeed(kmh)) {
            return "--";
        }
        return Integer.toString(Math.round(kmh));
    }

    public static String rpmNumber(float rpm) {
        if (!validRpm(rpm)) {
            return "--";
        }
        return String.format(java.util.Locale.US, "%.2f", rpm / 1000f);
    }
}
