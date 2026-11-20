package com.ghosted907.t1launcher

import com.ghosted907.t1remote.protocol.RemoteProtocol
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Protocol rev 4 (launcher 1.16.0 / T1 Remote 1.4.0): validation and backward compatibility. */
class RemoteRev4Test {
    private fun body(op: String) = JSONObject().put("op", op)
    private fun ok(b: JSONObject) = RemoteProtocol.validate(b) == null

    @Test fun revisionAndVersion() {
        assertEquals(4, RemoteProtocol.REV)
        assertEquals(3, RemoteProtocol.REV_3)
        assertEquals(2, RemoteProtocol.VERSION)   // unchanged: older phones and launchers keep talking
    }

    @Test fun rev3OpsAreUnchangedAndNewOpsAreSeparate() {
        val new = setOf(RemoteProtocol.OP_HOME, RemoteProtocol.OP_NOTE, RemoteProtocol.OP_OFFROAD, RemoteProtocol.OP_OEM_PROPS)
        assertEquals(RemoteProtocol.OPS, RemoteProtocol.OPS_REV3 + new)
        new.forEach { assertFalse(it, it in RemoteProtocol.OPS_REV3) }
        assertTrue(RemoteProtocol.OP_VEHICLE in RemoteProtocol.OPS_REV3)
    }

    @Test fun readOnlyOpsNeedNoArguments() {
        assertTrue(ok(body(RemoteProtocol.OP_HOME)))
        assertTrue(ok(body(RemoteProtocol.OP_OFFROAD)))
        assertTrue(ok(body(RemoteProtocol.OP_OEM_PROPS)))
    }

    @Test fun noteIsTextOnlyAndBounded() {
        assertTrue(ok(body(RemoteProtocol.OP_NOTE).put("text", "")))
        assertTrue(ok(body(RemoteProtocol.OP_NOTE).put("text", "ملاحظة")))
        assertTrue(ok(body(RemoteProtocol.OP_NOTE).put("text", "a".repeat(RemoteProtocol.MAX_NOTE))))
        assertFalse(ok(body(RemoteProtocol.OP_NOTE).put("text", "a".repeat(RemoteProtocol.MAX_NOTE + 1))))
        assertFalse(ok(body(RemoteProtocol.OP_NOTE)))
        assertFalse(ok(body(RemoteProtocol.OP_NOTE).put("text", 5)))
        assertFalse(ok(body(RemoteProtocol.OP_NOTE).put("text", JSONObject())))
    }

    @Test fun oemCatalogArgumentsAreChecked() {
        assertTrue(ok(body(RemoteProtocol.OP_OEM_PROPS).put("query", "HVAC").put("page", 3).put("watch", true)))
        assertFalse(ok(body(RemoteProtocol.OP_OEM_PROPS).put("query", "x".repeat(RemoteProtocol.MAX_QUERY + 1))))
        assertFalse(ok(body(RemoteProtocol.OP_OEM_PROPS).put("query", 12)))
        assertFalse(ok(body(RemoteProtocol.OP_OEM_PROPS).put("page", -1)))
        assertFalse(ok(body(RemoteProtocol.OP_OEM_PROPS).put("page", 100000)))
        assertFalse(ok(body(RemoteProtocol.OP_OEM_PROPS).put("page", "2")))
        assertFalse(ok(body(RemoteProtocol.OP_OEM_PROPS).put("watch", "yes")))
        assertEquals(24, RemoteProtocol.OEM_PAGE)
    }

    @Test fun newRunIdsMapToLauncherActionsOnly() {
        assertTrue(RemoteProtocol.RUN_IDS.containsAll(RemoteProtocol.RUN_IDS_REV4))
        assertEquals(RemoteProtocol.RUN_IDS_REV4, RemoteParity.RUN_ENUMS.keys)
        RemoteProtocol.RUN_IDS_REV4.forEach { assertTrue(ok(body(RemoteProtocol.OP_RUN).put("id", it))) }
        assertFalse(ok(body(RemoteProtocol.OP_RUN).put("id", "media_volume_up")))
        // No vehicle, black-screen or kill enums are reachable through the new ids.
        val forbidden = setOf(JctEnum.RUN_BLACK)
        assertTrue(RemoteParity.RUN_ENUMS.values.none { it in forbidden })
        RemoteParity.RUN_ENUMS.values.forEach { e ->
            assertTrue(e.name, e.name.startsWith("MEDIA_") || e.name.startsWith("GLOBAL_") || e in setOf(JctEnum.RECENT_TASK_MANAGER, JctEnum.RUN_START_APP_MENU))
        }
    }

    @Test fun excludedFeaturesStayUnreachableInRev4() {
        RemoteProtocol.OPS.forEach { op ->
            listOf("tail", "trunk", "wifi", "owner", "shell", "install", "light", "hal", "binder").forEach { bad -> assertFalse(op, op.contains(bad)) }
        }
        RemoteProtocol.RUN_IDS.forEach { id -> listOf("wifi", "owner", "hide", "tail", "light", "kill").forEach { assertFalse(id, id.contains(it)) } }
        assertEquals(9, RemoteProtocol.VEHICLE_IDS.size)
        assertNotNull(RemoteProtocol.validate(body(RemoteProtocol.OP_VEHICLE).put("cmd", "tailgate_open").put("held_ms", 2000)))
    }

    @Test fun unknownOpFromANewerPhoneIsRefusedNotCrashed() {
        assertNotNull(RemoteProtocol.validate(body("stream_camera")))
        assertNull(RemoteProtocol.validate(body(RemoteProtocol.OP_PING)))
    }
}
