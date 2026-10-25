package com.armor.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.armor.app.ArmorApp
import kotlinx.coroutines.launch

private fun colorFor(type: String): Color = when (type) {
    "SOS_SENT", "CRASH", "GROUP_ALERT" -> ArmorColors.Red
    "STATIONARY_CHECK", "CRASH_DISMISSED" -> ArmorColors.Amber
    "RIDER_OK", "RIDER_RESUMED", "CRASH_CANCELLED", "SOS_CANCELLED", "GROUP_REACHED_RIDER", "GROUP_ACK" -> ArmorColors.Green
    else -> ArmorColors.Cyan
}

/** The audit trail: every decision the safety logic made, with the reason, stored on the phone. */
@Composable
fun EventsScreen() {
    val dao = ArmorApp.instance.db.events()
    val eventsFlow = remember { dao.recent(300) }
    val events by eventsFlow.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            GlowText("SYSTEM LOG", ArmorColors.OnBg, 22, glow = 16f, mono = false, spacing = 4, weight = FontWeight.Black)
            TextButton(onClick = { scope.launch { dao.clear() } }) { Text("Clear", color = ArmorColors.Muted) }
        }
        Text("Why ARMOR did what it did. Stored on this phone and synced when you're online.", color = ArmorColors.Muted, fontSize = 12.sp)
        if (events.isEmpty()) Text("Nothing yet. Events appear here during a ride.", color = ArmorColors.Muted)
        LazyColumn(Modifier.fillMaxWidth()) {
            items(events, key = { it.id }) { e ->
                val c = colorFor(e.type)
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.padding(top = 10.dp).size(11.dp).border(1.5.dp, c, CutShape(3.dp)))
                        Box(Modifier.padding(top = 4.dp).width(1.dp).weight(1f).background(Brush.verticalGradient(listOf(c, Color.Transparent))))
                    }
                    HudPanel(Modifier.weight(1f).padding(bottom = 8.dp), accent = c, cut = 10.dp, padding = 10.dp) {
                        Column(Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                MonoText(e.type.replace('_', ' '), c, 10, spacing = 1.5, weight = FontWeight.Bold)
                                MonoText(fmtClock(e.timeMs) + if (!e.synced) " · LOCAL" else "", ArmorColors.Muted, 9)
                            }
                            Text(e.detail, color = ArmorColors.OnBg, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
                        }
                    }
                }
            }
        }
    }
}
