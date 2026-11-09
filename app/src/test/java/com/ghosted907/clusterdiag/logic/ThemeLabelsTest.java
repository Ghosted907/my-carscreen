package com.ghosted907.clusterdiag.logic;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ThemeLabelsTest {
    @Test
    public void themePresets() {
        assertEquals("رقمي", ThemeLabels.arabic(1));
        assertEquals("DIGITAL", ThemeLabels.english(1));
        assertEquals("كلاسيكي", ThemeLabels.arabic(2));
        assertEquals("CLASSIC", ThemeLabels.english(2));
        assertEquals("ملاحة", ThemeLabels.arabic(3));
        assertEquals("NAVIGATION", ThemeLabels.english(3));
        assertEquals("غير معروف", ThemeLabels.arabic(9));
        assertEquals("UNKNOWN", ThemeLabels.english(null));
    }

    @Test
    public void dayPresets() {
        assertEquals("نهاري", ThemeLabels.dayArabic(0));
        assertEquals("DAY", ThemeLabels.dayEnglish(0));
        assertEquals("ليلي", ThemeLabels.dayArabic(1));
        assertEquals("NIGHT", ThemeLabels.dayEnglish(1));
        assertEquals("DAY_MODE", ThemeLabels.dayHint(0));
        assertEquals("NIGHT_MODE", ThemeLabels.dayHint(1));
    }

    @Test
    public void enumNamesDoNotMapTheOtherThemeApi() {
        assertEquals(Integer.valueOf(1), ThemeLabels.fromEnumName("DIGITAL"));
        assertEquals(Integer.valueOf(2), ThemeLabels.fromEnumName("classic"));
        assertEquals(Integer.valueOf(3), ThemeLabels.fromEnumName("com.example.NAVIGATION"));
        assertEquals(Integer.valueOf(0), ThemeLabels.fromEnumName("DAY_MODE"));
        assertEquals(Integer.valueOf(1), ThemeLabels.fromEnumName("NIGHT"));
        assertNull(ThemeLabels.fromEnumName("STANDARD"));
        assertNull(ThemeLabels.fromEnumName("CROSS_COUNTRY"));
        assertNull(ThemeLabels.fromEnumName("INVALID_MODE"));
        assertNull(ThemeLabels.fromEnumName(""));
        assertNull(ThemeLabels.fromEnumName(null));
    }
}
