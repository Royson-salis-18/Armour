package com.armor.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
    else -> ArmorColors.Dim
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
            ScreenTitle("Log")
            TextButton(onClick = { scope.launch { dao.clear() } }) { Text("Clear", color = ArmorColors.Muted) }
        }
        Txt("Why ARMOR did what it did. Stored on this phone and synced when you're online.", Type.Label, ArmorColors.Muted)
        if (events.isEmpty()) Text("Nothing yet. Events appear here during a ride.", color = ArmorColors.Muted)
        LazyColumn(Modifier.fillMaxWidth()) {
            item {
                ListCard {
                    events.forEachIndexed { i, e ->
                        if (i > 0) Divider1()
                        val c = colorFor(e.type)
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.padding(top = 6.dp).size(8.dp).background(c, CircleShape))
                            Column(Modifier.weight(1f)) {
                                Txt(e.type.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }, Type.BodyStrong)
                                Txt(e.detail, Type.Label.copy(fontWeight = FontWeight.Normal), ArmorColors.Muted, Modifier.padding(top = 2.dp))
                            }
                            Txt(fmtClock(e.timeMs), Type.Caption, ArmorColors.Dim)
                        }
                    }
                }
            }
        }
    }
}
