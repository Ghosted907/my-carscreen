package com.ghosted907.t1launcher
import org.junit.Assert.*
import org.junit.Test
class ClusterTaskPolicyTest {
    @Test fun onlyTrackedDisplayOneTasks() {
        val all = setOf("com.waze", "com.youtube", "launcher", "com.ghosted907.clusterdiag")
        assertTrue(ClusterTaskPolicy.removable(1, 1, "com.waze", all, "launcher"))
        assertTrue(ClusterTaskPolicy.removable(1, 1, "com.youtube", all, "launcher"))
        assertFalse(ClusterTaskPolicy.removable(0, 1, "com.waze", all, "launcher"))
        assertFalse(ClusterTaskPolicy.removable(-1, 1, "com.waze", all, "launcher"))
        assertFalse(ClusterTaskPolicy.removable(2, 2, "com.waze", all, "launcher"))
        assertFalse(ClusterTaskPolicy.removable(1, 1, "launcher", all, "launcher"))
        assertFalse(ClusterTaskPolicy.removable(1, 1, "com.ghosted907.clusterdiag", all, "launcher"))
        assertFalse(ClusterTaskPolicy.removable(1, 1, "oem.cluster", all, "launcher"))
        assertFalse(ClusterTaskPolicy.removable(1, 1, null, all, "launcher"))
    }
}
