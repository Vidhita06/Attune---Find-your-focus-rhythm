package com.attune.app.ui.home
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.attune.app.data.AccessStatus
import com.attune.app.domain.TimerMath
import com.attune.app.ui.components.*
import com.attune.app.ui.theme.*
import com.attune.app.util.Quotes
import com.attune.core.model.SessionState

@Composable fun HomeScreen(onOpenFocus: () -> Unit, onOpenQueue: () -> Unit, onOpenInsights: () -> Unit, onOpenItem: (String) -> Unit, onSetupAccess: () -> Unit, vm: HomeViewModel = hiltViewModel()) {
    val s by vm.state.collectAsState()
    val withPermission = rememberWithNotificationPermission()
    if (s.loading) { LoadingState(); return }
    ScreenColumn {
        ScreenHeader(s.date, s.greeting, "Make space for focused moments.") { if (s.demoEnabled && s.pendingDemo > 0) DemoBadge("DEMO ON") }

        // Focus status
        AttuneCard(container = LavenderMist) {
            val a = s.active
            if (a == null) {
                Text("Ready when you are", style = MaterialTheme.typography.titleLarge, color = Navy)
                Text("Notifications that arrive while you focus will wait in your queue.", style = MaterialTheme.typography.bodySmall, color = NavySoft)
                Spacer(Modifier.height(14.dp))
                Button(onClick = { withPermission { vm.startFocus(s.defaultFocusMin); onOpenFocus() } }, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("Start ${s.defaultFocusMin}-min Focus")
                }
                TextButton(onClick = onOpenFocus, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Choose another duration") }
            } else {
                val label = when (a.state) { SessionState.PAUSED -> "Paused"; SessionState.BREAK -> "On a break"; else -> "Focusing now" }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pill(label, if (a.state == SessionState.FOCUS) LowRisk else MedRisk)
                    Spacer(Modifier.weight(1f))
                    Text("${TimerMath.plannedMinutes(a)} min session", style = MaterialTheme.typography.labelMedium, color = NavySoft)
                }
                Spacer(Modifier.height(8.dp))
                Text(TimerMath.formatClock(s.remainingMs), style = MaterialTheme.typography.displayLarge, color = Navy)
                Text("remaining", style = MaterialTheme.typography.bodySmall, color = NavySoft)
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth().height(6.dp), color = Lavender, trackColor = LavenderSoft)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onOpenFocus, Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(16.dp)) {
                    Text(if (a.state == SessionState.FOCUS) "Open timer" else "Resume focus")
                }
            }
        }

        if (s.access == AccessStatus.NOT_GRANTED) InfoBanner(Icons.Outlined.NotificationsOff, "Notification access is off",
            "Real notifications won't be queued until you allow access. Demo samples still work.", BannerTone.WARNING, "Set up access", onSetupAccess)

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(TimerMath.formatMinutes(s.todayFocusMin), "Focus today", Icons.Outlined.Timer, Modifier.weight(1f))
            StatTile("${s.todayCompleted}", "Sessions completed today", Icons.Outlined.TaskAlt, Modifier.weight(1f))
            StatTile("${s.pendingReal + s.pendingDemo}", if (s.pendingDemo > 0) "Pending (${s.pendingDemo} demo)" else "Pending in queue", Icons.Outlined.Inbox, Modifier.weight(1f))
        }

        AttuneCard {
            SectionTitle("Notification queue", "Review all", onOpenQueue)
            if (s.preview.isEmpty()) Text(if (s.delayed > 0) "Nothing pending. ${s.delayed} delayed to your break." else "Nothing waiting. Notifications queued during focus appear here.",
                style = MaterialTheme.typography.bodySmall, color = NavySoft)
            else s.preview.forEach { NotificationRow(it, { onOpenItem(it.id) }, compact = true) }
        }

        AttuneCard(onClick = onOpenInsights) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Insights, null, tint = Lavender, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
                Text("Attention insight", style = MaterialTheme.typography.titleMedium, color = Navy, modifier = Modifier.weight(1f))
                if (s.insightIsDemo) DemoBadge("SAMPLE")
            }
            Spacer(Modifier.height(6.dp))
            Text(s.insight, style = MaterialTheme.typography.bodyMedium, color = NavySoft)
        }

        if (s.energyEnabled) EnergyCheckInCard(s.latestCheckIn, s.suggestion, vm::checkIn)

        val q = Quotes.forHome(); InspirationQuote(q.text, q.author)
    }
}
