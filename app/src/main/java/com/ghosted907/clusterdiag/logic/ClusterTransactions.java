package com.ghosted907.clusterdiag.logic;

/**
 * Literal {@code IBinder.transact} codes from the Jetour CiService APK.
 * These are the codes passed to {@code transact}, not offsets added to
 * {@code FIRST_CALL_TRANSACTION}.
 *
 * <p>Nothing else is allowlisted. {@code setThemeMode} and {@code setCarModelColor}
 * are not in this list and must not be sent.
 */
public final class ClusterTransactions {
    public static final String DESCRIPTOR =
            "com.autolink.adapterinterface.clusterinteraction.IClusterInteraction";

    public static final int SET_THEME = 205;
    public static final int GET_THEME = 206;
    public static final int SET_FONT = 215;
    public static final int GET_FONT = 216;
    public static final int SET_DAY_NIGHT = 74;

    private ClusterTransactions() {}

    public static boolean descriptorMatches(String remote) {
        return DESCRIPTOR.equals(remote);
    }

    public static boolean isAllowlisted(int code) {
        return code == SET_THEME
                || code == GET_THEME
                || code == SET_FONT
                || code == GET_FONT
                || code == SET_DAY_NIGHT;
    }
}
