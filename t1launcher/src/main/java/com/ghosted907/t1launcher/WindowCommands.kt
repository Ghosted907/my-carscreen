package com.ghosted907.t1launcher

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import org.json.JSONObject

/**
 * Sends the OEM voice assistant the same handMessage JSON its speech engine uses.
 * The assistant then runs its own window logic. This app does not call VehicleApi or HAL setters.
 */
object WindowCommands {
    private const val TAG = "T1Launcher"
    const val STOP = "stop_close"

    data class Command(val id: String, val label: String, val value: String)

    private val order = listOf(
        "windows_all_open" to "افتح كل النوافذ",
        "windows_all_close" to "سكّر كل النوافذ",
        "window_driver_open" to "نافذة السائق فتح",
        "window_driver_close" to "نافذة السائق سكّر",
        "sunroof_open" to "فتحة السقف فتح",
        "sunroof_close" to "فتحة السقف سكّر",
        "sunshade_open" to "ستارة السقف فتح",
        "sunshade_close" to "ستارة السقف سكّر"
    )

    fun buttons(context: Context): List<Command> {
        val root = load(context)
        val commands = root.getJSONObject("commands")
        return order.map { (id, label) ->
            Command(id, label, wireValue(commands.getJSONObject(id)))
        }
    }

    fun stop(context: Context): List<Command> {
        val all = buttons(context).associateBy { it.id }
        return listOf("windows_all_close", "sunroof_close", "sunshade_close").map { all.getValue(it) }
    }

    fun send(context: Context, command: Command) {
        val root = load(context)
        val intent = Intent(root.getString("action")).setClassName(
            root.getString("package"),
            root.getString("receiver")
        )
        intent.putExtra(root.getString("extra"), command.value)
        Log.i(TAG, "handMessage id=${command.id} action=${intent.action} component=${intent.component} value=${command.value}")
        try {
            context.sendBroadcast(intent)
            Toast.makeText(context, command.label, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.i(TAG, "handMessage failed ${e.javaClass.simpleName} ${e.message}")
            Toast.makeText(context, "تعذّر إرسال الأمر إلى المساعد", Toast.LENGTH_LONG).show()
        }
    }

    fun sendStop(context: Context) {
        stop(context).forEach { send(context, it) }
    }

    private fun load(context: Context): JSONObject {
        val text = context.assets.open("window_commands.json").bufferedReader().use { it.readText() }
        return JSONObject(text)
    }

    /** content must be a string. MessageInfo parses it a second time into ContentDTO. */
    private fun wireValue(command: JSONObject): String {
        val content = command.getJSONObject("content")
        val wire = JSONObject()
        wire.put("messageType", command.getString("messageType"))
        wire.put("focus", command.getString("focus"))
        wire.put("position", command.getString("position"))
        wire.put("content", content.toString())
        return wire.toString()
    }
}
