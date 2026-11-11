package com.ghosted907.t1launcher

import org.junit.Assert.*
import org.junit.Test

class ClusterMovePolicyTest {
    @Test fun reuseFlagsAreExact() { assertEquals(0x10220000, ClusterMovePolicy.REUSE_FLAGS) }
    @Test fun destructiveAndMultipleFlagsAbsent() {
        assertEquals(0, ClusterMovePolicy.REUSE_FLAGS and (0x08000000 or 0x00008000 or 0x04000000))
    }
    @Test fun mainUsesIdNotPosition() {
        assertEquals(0, ClusterMovePolicy.main(listOf(ClusterMovePolicy.Panel(7,1920,720), ClusterMovePolicy.Panel(0,1920,1080))))
    }
    @Test fun absentMainDoesNotFallback() { assertNull(ClusterMovePolicy.main(listOf(ClusterMovePolicy.Panel(7,1920,720)))) }
    @Test fun clusterUsesPhysicalShapeNotIdOne() {
        assertEquals(7, ClusterMovePolicy.cluster(listOf(ClusterMovePolicy.Panel(1,800,600), ClusterMovePolicy.Panel(7,1920,720))))
    }
    @Test fun neverSelectMainAsCluster() { assertNull(ClusterMovePolicy.cluster(listOf(ClusterMovePolicy.Panel(0,1920,720)))) }
    @Test fun unknownPanelIsNotCluster() { assertNull(ClusterMovePolicy.cluster(listOf(ClusterMovePolicy.Panel(1,800,600)))) }
    @Test fun ambiguousClustersFailClosed() { assertNull(ClusterMovePolicy.cluster(listOf(ClusterMovePolicy.Panel(1,1920,720), ClusterMovePolicy.Panel(9,1920,720)))) }
    @Test fun themeCannotMoveHome() { assertFalse(ClusterMovePolicy.mayMoveToMain("com.ghosted907.clusterdiag", "ours")) }
    @Test fun ownAndBlankCannotMoveHome() {
        assertFalse(ClusterMovePolicy.mayMoveToMain("ours", "ours")); assertFalse(ClusterMovePolicy.mayMoveToMain("", "ours"))
    }
    private fun allowed(pkg: String="org.videolan.vlc", system: Boolean=false, updated: Boolean=false, persistent: Boolean=false) =
        ClusterMovePolicy.mayHide(pkg,"ours",system,updated,persistent)
    @Test fun ordinaryThirdPartyMayHide() { assertTrue(allowed()) }
    @Test fun neverHideFactorySystemOrUpdatedSystem() { assertFalse(allowed(system=true)); assertFalse(allowed(updated=true)) }
    @Test fun neverHidePersistentOrOwn() { assertFalse(allowed(persistent=true)); assertFalse(allowed(pkg="ours")) }
    @Test fun neverHideOemEvenWithoutSystemFlag() {
        listOf("com.autolink.hmi.carsettings","com.chery.car","com.jetour.service","com.desaysv.foo","com.iflytek.voice","com.mega.service","com.example.navistart","com.android.settings").forEach { assertFalse(it,allowed(it)) }
    }
    @Test fun neverHideTheme() { assertFalse(allowed("com.ghosted907.clusterdiag")) }
    @Test fun hidesWaitsThenRestores() {
        val calls=mutableListOf<String>()
        assertTrue(HideRestoreCycle.run({calls+="hide";true},{calls+="wait"},{calls+="restore";true}))
        assertEquals(listOf("hide","wait","restore"),calls)
    }
    @Test fun restoreAfterHideReturnsFalse() {
        var restores=0; assertFalse(HideRestoreCycle.run({false},{fail("must not wait")},{restores++;true})); assertEquals(1,restores)
    }
    @Test fun restoreAfterHideThrows() {
        var restores=0
        try { HideRestoreCycle.run({error("hide")},{},{restores++;true}); fail() } catch (_: IllegalStateException) {}
        assertEquals(1,restores)
    }
    @Test fun restoreAfterDelayThrows() {
        var restores=0
        try { HideRestoreCycle.run({true},{error("delay")},{restores++;true}); fail() } catch (_: IllegalStateException) {}
        assertEquals(1,restores)
    }
    @Test fun retriesFailedRestores() {
        var n=0; assertTrue(HideRestoreCycle.run({true},{},{++n==3})); assertEquals(3,n)
    }
    @Test fun restoreExceptionRetried() {
        var n=0; assertTrue(HideRestoreCycle.run({true},{},{if(++n<3) error("restore");true})); assertEquals(3,n)
    }
    @Test fun failedRestoreNeverReportsSuccess() { assertFalse(HideRestoreCycle.run({true},{},{false})) }
}