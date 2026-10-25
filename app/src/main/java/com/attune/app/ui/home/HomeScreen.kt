package com.attune.app.ui.home
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.attune.app.data.AccessStatus
import com.attune.app.domain.TimerMath
import com.attune.app.ui.components.*
import com.attune.app.ui.theme.*
import com.attune.app.util.Quotes
import com.attune.core.model.SessionState

@Composable fun HomeScreen(onOpenFocus: () -> Unit, onOpenQueue: () -> Unit, onOpenInsights: () -> Unit, onOpenItem: (String) -> Unit, onSetupAccess: () -> Unit,
                           onOpenAi: () -> Unit, onOpenGoals: () -> Unit, vm: HomeViewModel = hiltViewModel()) {
    val s by vm.state.collectAsState()
    val withPermission = rememberWithNotificationPermission()
    if (s.loading) { LoadingState(); return }
    ScreenColumn {
        ScreenHeader(s.date, s.greeting, null) { if (s.demoEnabled) DemoBadge("DEMO ON") }

        // AI Mode status — tappable route to its screen
        Surface(onClick = onOpenAi, shape = RoundedCornerShape(16.dp), color = if (s.aiMode && s.modelLoaded) LavenderSoft else Color(0xFFF1F1F5), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AutoAwesome, null, tint = if (s.aiMode) LavenderDeep else NavyFaint, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(when { !s.aiMode -> "AI Mode is off"; !s.modelLoaded -> "AI Mode: model unavailable"; else -> "AI Mode is on" }, style = MaterialTheme.typography.labelLarge, color = Navy)
                    Text(when { !s.aiMode -> "Category rules decide what waits"; !s.modelLoaded -> "Using category rules as a fallback"; else -> "On-device model reads each notification · nothing leaves your phone" },
                        style = MaterialTheme.typography.bodySmall, color = NavySoft)
                }
                Switch(s.aiMode, vm::setAiMode)
            }
        }

        // Focus hero
        AttuneCard(container = Navy) {
            val a = s.active
            if (a == null) {
                Text("Ready when you are", style = MaterialTheme.typography.titleLarge, color = Color.White)
                Text("Likely distractions wait in your queue; anything that looks important or urgent still reaches you.", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.75f))
                Spacer(Modifier.height(14.dp))
                Button(onClick = { withPermission { vm.startFocus(s.defaultFocusMin); onOpenFocus() } }, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Navy)) {
                    Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("Start ${s.defaultFocusMin}-min focus")
                }
                TextButton(onClick = onOpenFocus, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Choose another length", color = Color.White.copy(alpha = 0.85f)) }
            } else {
                val label = when (a.state) { SessionState.PAUSED -> "Paused"; SessionState.BREAK -> "On a break"; else -> "Focusing now" }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pill(label, Color.White, Color.White.copy(alpha = 0.16f)); Spacer(Modifier.weight(1f))
                    Text("${TimerMath.plannedMinutes(a)} min session", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.75f))
                }
                Spacer(Modifier.height(8.dp))
                Text(TimerMath.formatClock(s.remainingMs), style = MaterialTheme.typography.displayLarge, color = Color.White)
                Text("remaining", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.75f))
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)), color = LavenderSoft, trackColor = Color.White.copy(alpha = 0.2f))
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (a.state == SessionState.FOCUS) OutlinedButton(onClick = vm::pause, Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Icon(Icons.Filled.Pause, null); Spacer(Modifier.width(6.dp)); Text("Pause") }
                    else Button(onClick = vm::resume, Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Navy)) { Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Resume") }
                    Button(onClick = onOpenFocus, Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Navy)) { Text("Open timer") }
                }
            }
        }

        if (s.access == AccessStatus.NOT_GRANTED) InfoBanner(Icons.Outlined.NotificationsOff, "Notification access is off",
            "Without it Attune can't see or hold real notifications. Demo samples still work.", BannerTone.WARNING, "Set up access", onSetupAccess)

        // Today
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(TimerMath.formatMinutes(s.todayFocusMin) + if (s.dailyGoalMin > 0) " / ${TimerMath.formatMinutes(s.dailyGoalMin)}" else "", "Focus today", Icons.Outlined.Timer, Modifier.weight(1f))
            StatTile("${s.heldToday}", "Held during focus today", Icons.Outlined.Shield, Modifier.weight(1f))
            StatTile("${s.pendingReal + s.pendingDemo}", if (s.pendingDemo > 0) "Waiting (${s.pendingDemo} demo)" else "Waiting in queue", Icons.Outlined.Inbox, Modifier.weight(1f))
        }

        // Trend + score + streak
        AttuneCard(onClick = onOpenInsights) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Your week", style = MaterialTheme.typography.titleMedium, color = Navy, modifier = Modifier.weight(1f))
                Icon(Icons.Outlined.ChevronRight, null, tint = NavyFaint)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                MiniBars(s.last7, Modifier.weight(1f).height(48.dp))
                Spacer(Modifier.width(14.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(s.score?.let { "${it.total}" } ?: "—", style = MaterialTheme.typography.headlineMedium, color = Navy)
                    Text("Focus score", style = MaterialTheme.typography.labelMedium, color = NavySoft)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(s.streak?.let { st -> if (st.current > 0) "${st.current}-day streak · ${st.rule}. Best: ${st.best}." else "Every session counts — a streak starts with ${st.rule.removePrefix("days ")}." }
                ?: "Complete a session to start your weekly score.", style = MaterialTheme.typography.bodySmall, color = NavySoft)
            if (s.dailyGoalMin == 0) TextButton(onClick = onOpenGoals, contentPadding = PaddingValues(0.dp)) { Text("Set a daily goal (optional)") }
        }

        AttuneCard {
            SectionTitle("Waiting for you", "Open queue", onOpenQueue)
            if (s.preview.isEmpty()) Text(if (s.delayed > 0) "Nothing new. ${s.delayed} saved for your break." else "Nothing waiting. Notifications held during focus appear here.",
                style = MaterialTheme.typography.bodySmall, color = NavySoft)
            else s.preview.forEach { NotificationRow(it, { onOpenItem(it.id) }, compact = true, hideSensitive = s.hideSensitive) }
        }

        s.tip?.let { tip -> AttuneCard(container = LavenderMist, onClick = onOpenInsights) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Lightbulb, null, tint = Lavender, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
                Text("Focus coach", style = MaterialTheme.typography.titleMedium, color = Navy, modifier = Modifier.weight(1f))
                if (s.tipIsDemo) DemoBadge("SAMPLE")
            }
            Spacer(Modifier.height(4.dp))
            Text(tip.title, style = MaterialTheme.typography.labelLarge, color = Navy)
            Text(tip.evidence, style = MaterialTheme.typography.bodySmall, color = NavySoft)
        } }

        if (s.energyEnabled) EnergyCheckInCard(s.latestCheckIn, s.suggestion, vm::checkIn)

        val q = Quotes.forHome(); InspirationQuote(q.text, q.author)
    }
}

/** Tiny 7-day focus bars (today highlighted); exact numbers live in Insights. */
@Composable private fun MiniBars(values: List<Int>, modifier: Modifier) {
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        values.forEachIndexed { i, v ->
            Box(Modifier.weight(1f).fillMaxHeight(((v.toFloat() / max) * 0.95f + 0.05f)).clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                .background(if (i == values.lastIndex) Lavender else LavenderSoft))
        }
    }
}
