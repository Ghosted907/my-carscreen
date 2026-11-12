package com.ghosted907.t1launcher

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val oemShortcuts = listOf(
    "المكيف" to ComponentName("com.autolink.aircontroller", "com.autolink.aircontroller.ui.HomeActivity"),
    "وسائط جيتور" to ComponentName("com.autolink.music", "com.autolink.main.views.MainActivity"),
    "هاتف البلوتوث" to ComponentName("com.autolink.bluetooth", "com.autolink.bluetooth.MainActivity"),
    "Android Auto" to ComponentName("com.autolink.androidautolink", "com.autolink.androidautolink.MainActivity"),
    "CarPlay" to ComponentName("com.autolink.carplaylink", "com.autolink.carplaylink.MainActivity"),
    "دليل المالك" to ComponentName("com.autolink.manual", "com.autolink.manual.MainActivity")
)

@Composable
fun OemAppShortcuts(day: Boolean, apps: List<LaunchableApp>) {
    val context = LocalContext.current
    val available = remember(apps) {
        val components = apps.mapTo(hashSetOf()) { it.component }
        oemShortcuts.filter { (_, component) -> component in components }
    }
    if (available.isEmpty()) return

    val fg = if (day) T1Colors.dayText else T1Colors.nightText
    val bg = if (day) T1Colors.dayCard else T1Colors.nightCard
    Column(
        Modifier.fillMaxWidth().background(bg, RoundedCornerShape(12.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("تطبيقات جيتور", color = fg, fontSize = 16.sp)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            available.forEach { (label, component) ->
                OutlinedButton(onClick = { openOemApp(context, label, component) }) {
                    Text(label, color = fg, maxLines = 1)
                }
            }
        }
    }
}

private fun openOemApp(context: Context, label: String, component: ComponentName) {
    val intent = Intent.makeMainActivity(component)
    try {
        LauncherDisplay.start(context, intent)
        return
    } catch (_: ActivityNotFoundException) {
        // The app can be disabled or removed after the shortcut is displayed.
    } catch (_: SecurityException) {
        // OEM firmware can restrict access to its activities.
    }
    Toast.makeText(context, "تعذر فتح $label", Toast.LENGTH_SHORT).show()
}
