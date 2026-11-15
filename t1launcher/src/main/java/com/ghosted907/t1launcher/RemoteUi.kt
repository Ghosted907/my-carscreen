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

/** Settings card for "التحكم من الجوال". 1.15.8: always on, no on/off switch. */
@Composable
fun RemoteSettingsCard(day: Boolean) {
    val context = LocalContext.current
    val text = t1CardText(day)   // 1.15.11: card text, cream in day mode on the dark fill
    val muted = t1CardMuted(day)
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(1000); tick++ } }
    val ips = remember(tick / 5) { RemoteHost.addresses() }

    Column(
        Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .t1Bg(T1Colors.fill(if (day) T1Colors.dayCard else T1Colors.nightCard)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("التحكم من الجوال", color = text, fontSize = 20.sp)
        Text("العنوان: ${ips.joinToString("  ·  ").ifEmpty { "غير متصل بشبكة" }}  منفذ ${RemoteProtocol.PORT}", color = text, fontSize = 15.sp)
        Text("يظهر في تطبيق T1 Remote باسم «${RemoteProtocol.SERVICE_NAME}». أي جوال على نفس الشبكة يقدر يتحكم، فاستخدم هوتسبوت جوالك.", color = muted, fontSize = 13.sp)
        Text("النوافذ والسقف والستارة مسموحة من الجوال دائماً (ضغط مطوّل في التطبيق).", color = text, fontSize = 15.sp)
        Spacer(Modifier.padding(2.dp))
    }
}
