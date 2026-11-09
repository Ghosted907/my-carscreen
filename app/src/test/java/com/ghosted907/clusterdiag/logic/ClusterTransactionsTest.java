package com.ghosted907.clusterdiag.logic;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ClusterTransactionsTest {
    @Test
    public void codesMatchTheHeadUnitApk() {
        assertEquals(
                "com.autolink.adapterinterface.clusterinteraction.IClusterInteraction",
                ClusterTransactions.DESCRIPTOR);
        assertEquals(205, ClusterTransactions.SET_THEME);
        assertEquals(206, ClusterTransactions.GET_THEME);
        assertEquals(215, ClusterTransactions.SET_FONT);
        assertEquals(216, ClusterTransactions.GET_FONT);
        assertEquals(74, ClusterTransactions.SET_DAY_NIGHT);
    }

    @Test
    public void onlyThoseFiveCodesAreAllowlisted() {
        assertTrue(ClusterTransactions.isAllowlisted(205));
        assertTrue(ClusterTransactions.isAllowlisted(206));
        assertTrue(ClusterTransactions.isAllowlisted(215));
        assertTrue(ClusterTransactions.isAllowlisted(216));
        assertTrue(ClusterTransactions.isAllowlisted(74));
        assertFalse(ClusterTransactions.isAllowlisted(0));
        assertFalse(ClusterTransactions.isAllowlisted(1));
        assertFalse(ClusterTransactions.isAllowlisted(50));
        assertFalse(ClusterTransactions.isAllowlisted(78));
        assertFalse(ClusterTransactions.isAllowlisted(128));
        assertFalse(ClusterTransactions.isAllowlisted(204));
        assertFalse(ClusterTransactions.isAllowlisted(207));
    }

    @Test
    public void descriptorMustMatchExactly() {
        assertTrue(ClusterTransactions.descriptorMatches(ClusterTransactions.DESCRIPTOR));
        assertFalse(ClusterTransactions.descriptorMatches(
                "com.autolink.clusterinteraction.IClusterInteraction"));
        assertFalse(ClusterTransactions.descriptorMatches(null));
        assertFalse(ClusterTransactions.descriptorMatches(""));
    }
}
