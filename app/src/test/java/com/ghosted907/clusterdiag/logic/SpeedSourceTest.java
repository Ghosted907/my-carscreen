package com.ghosted907.clusterdiag.logic;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SpeedSourceTest {
    @Test
    public void ciServiceKeepsUpdatingWhileCarIsInactive() {
        float shown = Float.NaN;
        shown = SpeedSource.choose(false, shown, 0f);
        assertEquals(0f, shown, 0f);
        shown = SpeedSource.choose(false, shown, 50f);
        assertEquals(50f, shown, 0f);
        shown = SpeedSource.choose(false, shown, 62.4f);
        assertEquals(62.4f, shown, 0f);
    }

    @Test
    public void validCarSpeedBlocksLaterCiServiceSamples() {
        float shown = SpeedSource.choose(true, 30f, 0f);
        assertEquals(30f, shown, 0f);
        shown = SpeedSource.choose(true, 30f, 50f);
        assertEquals(30f, shown, 0f);
    }

    @Test
    public void ciServiceResumesAfterCarDrops() {
        float shown = SpeedSource.choose(true, 30f, 50f);
        shown = SpeedSource.choose(false, shown, 50f);
        assertEquals(50f, shown, 0f);
        shown = SpeedSource.choose(false, shown, 80f);
        assertEquals(80f, shown, 0f);
    }

    @Test
    public void invalidCiSpeedLeavesTheLastFallback() {
        assertEquals(12f, SpeedSource.choose(false, 12f, -1f), 0f);
        assertEquals(12f, SpeedSource.choose(false, 12f, Float.NaN), 0f);
        assertTrue(Float.isNaN(SpeedSource.choose(false, Float.NaN, 401f)));
    }
}
