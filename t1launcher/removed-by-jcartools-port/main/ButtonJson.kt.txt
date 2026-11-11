package com.ghosted907.t1launcher

import org.json.JSONArray
import org.json.JSONObject

object ButtonJson {
    fun encode(buttons: List<ButtonConfig>): String {
        require(ButtonConfig.validate(buttons))
        val array = JSONArray()
        buttons.forEach { b -> array.put(JSONObject().put("id", b.id).put("target", b.target).put("label", b.label)
            .put("x", b.x.toDouble()).put("y", b.y.toDouble()).put("enabled", b.enabled)) }
        return JSONObject().put("schema", 1).put("buttons", array).toString(2)
    }
    fun decode(text: String): List<ButtonConfig> {
        require(text.length <= 65536) { "الملف كبير جداً" }
        val root = JSONObject(text)
        require(root.getInt("schema") == 1) { "نسخة ملف غير مدعومة" }
        val array = root.getJSONArray("buttons")
        require(array.length() <= ButtonConfig.MAX_BUTTONS) { "الحد الأقصى ١٦ زرًا" }
        val result = (0 until array.length()).map { i ->
            val b = array.getJSONObject(i)
            ButtonConfig(b.getString("id"), b.getString("target"), b.getString("label"),
                b.getDouble("x").toFloat(), b.getDouble("y").toFloat(), b.getBoolean("enabled"))
        }
        require(ButtonConfig.validate(result)) { "بيانات الأزرار أو المواضع غير صحيحة" }
        return result
    }
}
