package com.ghosted907.t1launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghosted907.t1remote.protocol.RemoteProtocol
import kotlinx.coroutines.delay

/** Settings card for "التحكم من الجوال". Everything here is off until the owner turns it on. */
@Composable
fun RemoteSettingsCard(day: Boolean) {
    val context = LocalContext.current
    val text = if (day) T1Colors.dayText else T1Colors.nightText
    val muted = if (day) T1Colors.dayMuted else T1Colors.nightMuted
    var enabled by remember { mutableStateOf(RemoteStore.enabled(context)) }
    var vehicle by remember { mutableStateOf(RemoteStore.vehicleAllowed(context)) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(enabled) { while (enabled) { delay(1000); tick++ } }
    val ips = remember(tick / 5, enabled) { if (enabled) RemoteHost.addresses() else emptyList() }

    Column(
        Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(if (day) T1Colors.dayCard else T1Colors.nightCard).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("التحكم من الجوال", color = text, fontSize = 20.sp, modifier = Modifier.weight(1f))
            Switch(checked = enabled, onCheckedChange = { enabled = it; RemoteHost.setEnabled(context, it) })
        }
        if (!enabled) {
            Text("مطفأ: لا يوجد أي اتصال. شغّله ليظهر T1 في تطبيق T1 Remote على نفس شبكة الواي فاي.", color = muted, fontSize = 14.sp)
            return@Column
        }
        Text("العنوان: ${ips.joinToString("  ·  ").ifEmpty { "غير متصل بشبكة" }}  منفذ ${RemoteProtocol.PORT}", color = text, fontSize = 15.sp)
        Text("يظهر في تطبيق T1 Remote باسم «${RemoteProtocol.SERVICE_NAME}». أي جوال على نفس الشبكة يقدر يتحكم، فاستخدم هوتسبوت جوالك.", color = muted, fontSize = 13.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("السماح بالنوافذ والسقف والستارة من الجوال (ضغط مطوّل)", color = text, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Switch(checked = vehicle, onCheckedChange = { vehicle = it; RemoteStore.setVehicleAllowed(context, it) })
        }
        Spacer(Modifier.padding(2.dp))
    }
}
