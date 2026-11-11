package com.ghosted907.t1launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.Toast

object OemSettings {
    // Same activity actions as OEM SettingWidget. Starting an activity only.
    fun open(context: Context, section: String = "") {
        val action = when (section) {
            "steering" -> "com.car.settings.steering.wheel"
            "mirrors" -> "com.car.settings.rear.mirror"
            "windows" -> "com.car.settings.car.windows"
            else -> Intent.ACTION_MAIN
        }
        try {
            LauncherDisplay.start(context, Intent(action).setComponent(ComponentName(
                "com.autolink.hmi.carsettings", "com.autolink.hmi.carsettings.activity.SettingActivity")))
        } catch (_: Exception) { Toast.makeText(context, "تعذر فتح إعدادات جيتور", Toast.LENGTH_LONG).show() }
    }
}
