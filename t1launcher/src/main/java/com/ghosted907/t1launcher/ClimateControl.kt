package com.ghosted907.t1launcher

import android.content.Context
import com.ghosted907.t1remote.protocol.RemoteProtocol

/**
 * A/C controls (owner-approved 2026-11-15) for the launcher page and the phone remote.
 * Keys are the OEM VehiclePropertyKey ids used by the stock A/C app (ALHvacApp, read-only reference):
 * KeyHvacPower 1021, KeyHvacAC 1000, KeyHvacACAuto 1002, KeyHvacSync 1022, KeyHvacRearDefrost 1012,
 * KeyHvacRecirculation 1004 (1 inside, 2 outside), KeyHvacFrontBlowMode 1013 (1..5),
 * KeyHvacWorkMode 1011 (1 eco, 2 comfort, 3 strong), KeyHvacFanSpeed 1014 (1..7),
 * KeyHvacTemperatureFL 1015 / FR 1016 (float 16.0..30.0 step 0.5).
 * Values are clamped by [RemoteProtocol.climateValue]. Nothing else is written.
 */
object Climate {
    val keys = linkedMapOf(
        "power" to 1021, "ac" to 1000, "auto" to 1002, "sync" to 1022, "rear_defrost" to 1012,
        "recirc" to 1004, "blow" to 1013, "mode" to 1011, "fan" to 1014, "temp_l" to 1015, "temp_r" to 1016
    )
    /** Matching read keys in SignalCatalog (VehicleService getInt/getFloat of the same ids). */
    val readKeys = mapOf(
        "power" to "ac_power", "ac" to "ac_ac", "auto" to "ac_auto", "sync" to "ac_sync",
        "rear_defrost" to "ac_rear_defrost", "recirc" to "ac_recirc", "blow" to "ac_blow", "mode" to "ac_mode",
        "fan" to "ac_fan", "temp_l" to "ac_temp_l", "temp_r" to "ac_temp_r"
    )
    val labels = mapOf(
        "power" to "تشغيل المكيف", "ac" to "A/C", "auto" to "AUTO", "sync" to "مزامنة الجهتين",
        "rear_defrost" to "مزيل ضباب الزجاج الخلفي", "recirc" to "تدوير الهواء", "blow" to "اتجاه الهواء",
        "mode" to "وضع المكيف", "fan" to "سرعة المروحة", "temp_l" to "حرارة السائق", "temp_r" to "حرارة الراكب"
    )
    val recircLabels = listOf(1 to "داخلي", 2 to "خارجي")
    val blowLabels = listOf(1 to "الوجه", 2 to "القدمين", 3 to "الزجاج", 4 to "الوجه والقدمين", 5 to "الزجاج والقدمين")
    val modeLabels = listOf(1 to "اقتصادي", 2 to "مريح", 3 to "قوي")
    private val floats = setOf("temp_l", "temp_r")

    /** Sends one A/C change. Returns (handed to the service, Arabic message). */
    fun send(context: Context, item: String, raw: Double): Pair<Boolean, String> {
        val key = keys[item] ?: return false to "أمر التكييف غير معروف"
        val value = RemoteProtocol.climateValue(item, raw) ?: return false to "قيمة التكييف غير صالحة"
        val car = (context.applicationContext as? T1App)?.car ?: return false to "خدمة السيارة غير جاهزة"
        val error = car.climateWrite(key, if (item in floats) value.toFloat() else value.toInt())
        return if (error == null) true to "أُرسل: ${labels[item]} ${describe(item, value)}؛ تأكد من شاشة المكيف"
        else false to error
    }

    fun describe(item: String, value: Double): String = when (item) {
        "power", "ac", "auto", "sync", "rear_defrost" -> if (value >= 1.0) "تشغيل" else "إيقاف"
        "recirc" -> recircLabels.firstOrNull { it.first == value.toInt() }?.second ?: value.toInt().toString()
        "blow" -> blowLabels.firstOrNull { it.first == value.toInt() }?.second ?: value.toInt().toString()
        "mode" -> modeLabels.firstOrNull { it.first == value.toInt() }?.second ?: value.toInt().toString()
        "temp_l", "temp_r" -> String.format(java.util.Locale.US, "%.1f°", value)
        else -> value.toInt().toString()
    }

    /** Current values as read from the car (null when not live). */
    fun state(snap: CarBridge.Snap): Map<String, Double?> = readKeys.mapValues { (_, k) -> snap.reading(k).current }
}
