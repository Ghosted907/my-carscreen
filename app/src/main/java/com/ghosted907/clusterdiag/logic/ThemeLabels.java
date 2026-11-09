package com.ghosted907.clusterdiag.logic;

import java.util.Locale;

/**
 * Preset ids from the Autolink cluster theme / font / day-night tables.
 * These are display labels only. They are not binder transaction codes.
 */
public final class ThemeLabels {
    private ThemeLabels() {}

    public static String arabic(Integer id) {
        if (id == null) {
            return "غير معروف";
        }
        switch (id) {
            case 1:
                return "رقمي";
            case 2:
                return "كلاسيكي";
            case 3:
                return "ملاحة";
            default:
                return "غير معروف";
        }
    }

    public static String english(Integer id) {
        if (id == null) {
            return "UNKNOWN";
        }
        switch (id) {
            case 1:
                return "DIGITAL";
            case 2:
                return "CLASSIC";
            case 3:
                return "NAVIGATION";
            default:
                return "UNKNOWN";
        }
    }

    public static String themeHint(int id) {
        switch (id) {
            case 1:
                return "DIGITAL";
            case 2:
                return "CLASSIC";
            case 3:
                return "NAVIGATION";
            default:
                return null;
        }
    }

    public static String dayArabic(Integer id) {
        if (id == null) {
            return "غير معروف";
        }
        switch (id) {
            case 0:
                return "نهاري";
            case 1:
                return "ليلي";
            default:
                return "غير معروف";
        }
    }

    public static String dayEnglish(Integer id) {
        if (id == null) {
            return "UNKNOWN";
        }
        switch (id) {
            case 0:
                return "DAY";
            case 1:
                return "NIGHT";
            default:
                return "UNKNOWN";
        }
    }

    public static String dayHint(int id) {
        switch (id) {
            case 0:
                return "DAY_MODE";
            case 1:
                return "NIGHT_MODE";
            default:
                return null;
        }
    }

    /**
     * Maps an enum constant name to a known preset id.
     * STANDARD / CROSS_COUNTRY are intentionally unmapped: that is {@code setThemeMode},
     * a different API from the cluster gauge theme.
     */
    public static Integer fromEnumName(String name) {
        if (name == null) {
            return null;
        }
        String normalized = name.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        normalized = normalized.toUpperCase(Locale.US);
        int dot = normalized.lastIndexOf('.');
        if (dot >= 0 && dot < normalized.length() - 1) {
            normalized = normalized.substring(dot + 1);
        }
        switch (normalized) {
            case "DIGITAL":
                return 1;
            case "CLASSIC":
                return 2;
            case "NAVIGATION":
                return 3;
            case "DAY":
            case "DAY_MODE":
                return 0;
            case "NIGHT":
            case "NIGHT_MODE":
                return 1;
            default:
                return null;
        }
    }
}
