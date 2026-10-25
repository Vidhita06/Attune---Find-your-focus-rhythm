package com.attune.app.ui.focus
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.attune.app.domain.TimerMath
import com.attune.app.ui.components.*
import com.attune.app.ui.theme.*
import com.attune.app.util.Quotes
import com.attune.core.model.SessionState

private val DURATIONS = listOf(15, 25, 45, 60, 90)

@OptIn(ExperimentalLayoutApi::class)
@Composable fun FocusScreen(onOpenQueue: () -> Unit, onOpenSession: (String) -> Unit, onSetupAccess: () -> Unit, vm: FocusViewModel = hiltViewModel()) {
    val s by vm.state.collectAsState()
    val snackbar = LocalSnackbar.current
    val withPermission = rememberWithNotificationPermission()
    var confirmEnd by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    if (s.loading) { LoadingState(); return }
    val a = s.active

    ScreenColumn {
        ScreenHeader("Focus", if (a == null) "Plan a focus session" else when (a.state) { SessionState.PAUSED -> "Paused"; SessionState.BREAK -> "Break time"; else -> "Deep work mode" },
            if (a == null) "Pick a length. The timer keeps running even if you leave the app." else if (a.state == SessionState.FOCUS) "Likely distractions wait in your queue." else "Paused — notifications reach you normally.")

        if (a == null) {
            var selected by rememberSaveable { mutableIntStateOf(s.defaultFocusMin) }
            if (s.energyEnabled) EnergyCheckInCard(s.latestCheckIn, null, { vm.checkIn(it, "BEFORE") }, "Energy before you start")
            AttuneCard {
                Text("Duration", style = MaterialTheme.typography.titleMedium, color = Navy)
                s.durationSuggestion?.let { d ->
                    Spacer(Modifier.height(6.dp))
                    Surface(onClick = { selected = d.minutes }, color = LavenderMist, shape = RoundedCornerShape(12.dp)) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Outlined.BatteryChargingFull, null, tint = Lavender, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                            Column { Text("Suggested for your energy: ${d.minutes} min", style = MaterialTheme.typography.labelLarge, color = Navy)
                                Text(d.basis + ". Tap to use it, or ignore it.", style = MaterialTheme.typography.bodySmall, color = NavySoft) }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (DURATIONS + s.defaultFocusMin).distinct().sorted().forEach { m -> FilterChip(selected == m, { selected = m }, { Text("$m min") }) }
                }
                Spacer(Modifier.height(14.dp))
                Button(onClick = { withPermission { vm.start(selected) } }, enabled = !s.busy, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("Start Focus · $selected min")
                }
                if (s.dailyGoalMin > 0) { Spacer(Modifier.height(8.dp))
                    Text("Today: ${TimerMath.formatMinutes(s.todayMin)} of your ${TimerMath.formatMinutes(s.dailyGoalMin)} goal", style = MaterialTheme.typography.bodySmall, color = NavySoft)
                    LinearProgressIndicator(progress = { (s.todayMin.toFloat() / s.dailyGoalMin).coerceIn(0f, 1f) }, Modifier.fillMaxWidth().padding(top = 4.dp), color = Lavender, trackColor = LavenderSoft) }
            }
            s.lastFinished?.let { l ->
                AttuneCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Last session", style = MaterialTheme.typography.titleMedium, color = Navy, modifier = Modifier.weight(1f))
                        Pill(if (l.state == SessionState.COMPLETED) "Completed" else "Ended early", if (l.state == SessionState.COMPLETED) LowRisk else MedRisk)
                    }
                    Spacer(Modifier.height(6.dp))
                    KeyValueRow("Focused", "${TimerMath.focusedMs(l, l.actualEndMs ?: 0) / 60_000} of ${TimerMath.plannedMinutes(l)} min")
                    KeyValueRow("Started", relativeTime(l.sessionStartMs))
                    KeyValueRow("Notifications queued", "${s.lastFinishedQueued}")
                    KeyValueRow("Interruptions (opened during focus)", "${l.interruptionCount}")
                    KeyValueRow("Pauses / breaks", "${l.pauseCount}")
                    Row { TextButton(onClick = { onOpenSession(l.id) }) { Text("View timeline") }
                        if (s.lastFinishedQueued > 0) TextButton(onClick = onOpenQueue) { Text("Review queue") } }
                }
                if (s.energyEnabled && System.currentTimeMillis() - (l.actualEndMs ?: 0) < 30 * 60_000L && (s.latestCheckIn?.timestampMs ?: 0) < (l.actualEndMs ?: 0))
                    EnergyCheckInCard(null, null, { vm.checkIn(it, "AFTER") }, "Energy after that session")
            }
        } else {
            val sweep by animateFloatAsState(s.progress * 360f, tween(600), label = "ring")
            Box(Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(240.dp)) {
                    val st = Stroke(12.dp.toPx(), cap = StrokeCap.Round)
                    drawArc(LavenderSoft, -90f, 360f, false, style = st)
                    drawArc(if (a.state == SessionState.FOCUS) Lavender else MedRisk, -90f, sweep, false, style = st)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(TimerMath.formatClock(s.remainingMs), style = MaterialTheme.typography.displayLarge, color = Navy)
                    Text(when (a.state) { SessionState.PAUSED -> "paused"; SessionState.BREAK -> "on a break"; else -> "remaining" }, style = MaterialTheme.typography.bodyMedium, color = NavySoft)
                    Text("${s.focusedMs / 60_000} of ${TimerMath.plannedMinutes(a)} min focused", style = MaterialTheme.typography.labelMedium, color = NavyFaint)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("${s.queuedThisSession}", "Held this session", Icons.Outlined.Shield, Modifier.weight(1f))
                StatTile("${s.allowedThisSession + s.reviewThisSession}", "Let through or kept visible", Icons.Outlined.NotificationsActive, Modifier.weight(1f))
                StatTile("${a.interruptionCount}", "Opened during focus", Icons.AutoMirrored.Outlined.OpenInNew, Modifier.weight(1f))
            }
            HandlingStatusCard(s, onSetupAccess)
            TextButton(onClick = { onOpenSession(a.id) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Timeline, null); Spacer(Modifier.width(6.dp)); Text("Session timeline") }
            if (a.state == SessionState.FOCUS) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = vm::pause, enabled = !s.busy, modifier = Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(16.dp)) {
                        Icon(Icons.Filled.Pause, null); Spacer(Modifier.width(6.dp)); Text("Pause") }
                    Button(onClick = { vm.takeBreak(); onOpenQueue() }, enabled = !s.busy, modifier = Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(16.dp)) {
                        Icon(Icons.Filled.Coffee, null); Spacer(Modifier.width(6.dp)); Text("Take a break") }
                }
            } else {
                Button(onClick = vm::resume, enabled = !s.busy, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("Resume focus") }
                if (a.state == SessionState.BREAK) TextButton(onClick = onOpenQueue, modifier = Modifier.fillMaxWidth()) { Text("Review delayed notifications") }
            }
            OutlinedButton(onClick = { confirmEnd = true }, enabled = !s.busy, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Filled.Stop, null, tint = HighRisk); Spacer(Modifier.width(6.dp)); Text("End session", color = HighRisk) }
            if (s.demoEnabled) AttuneCard(container = DemoAmberBg) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DemoBadge(); Spacer(Modifier.width(8.dp))
                    Text("No other app sending notifications? Add a labelled sample to the queue.", style = MaterialTheme.typography.bodySmall, color = Navy, modifier = Modifier.weight(1f))
                }
                TextButton(onClick = vm::sendDemoNotification, enabled = !s.busy) { Icon(Icons.Outlined.AddAlert, null); Spacer(Modifier.width(6.dp)); Text("Send demo notification") }
            }
        }
        if (s.energyEnabled && a != null) EnergyCheckInCard(s.latestCheckIn, s.suggestion, { vm.checkIn(it, null) })
        val q = Quotes.forFocus(); InspirationQuote(q.text, q.author)
    }

    if (confirmEnd && a != null) {
        val done = s.remainingMs <= 0
        AlertDialog(onDismissRequest = { confirmEnd = false },
            title = { Text(if (done) "Finish session?" else "End session early?") },
            text = { Text(if (done) "You reached your planned time. The session will be saved as completed."
                else "You've focused ${s.focusedMs / 60_000} of ${TimerMath.plannedMinutes(a)} min. It will be saved as ended early, not completed.") },
            confirmButton = { TextButton(onClick = { confirmEnd = false; vm.end() }) { Text(if (done) "Finish" else "End early") } },
            dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text("Keep focusing") } })
    }
}

/** What happens to notifications right now, in plain words, including the Android limits. */
@Composable private fun HandlingStatusCard(s: FocusUiState, onSetupAccess: () -> Unit) {
    val a = s.active ?: return
    val connected = s.access == com.attune.app.data.AccessStatus.CONNECTED
    AttuneCard(container = LavenderMist) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (s.aiMode && s.modelLoaded) Icons.Outlined.AutoAwesome else Icons.Outlined.Rule, null, tint = Lavender, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
            Text("Notification handling", style = MaterialTheme.typography.titleMedium, color = Navy)
        }
        Spacer(Modifier.height(6.dp))
        val lines = buildList {
            if (!connected) add("Notification access isn't active, so real notifications can't be held.")
            else if (a.state != SessionState.FOCUS) add("Paused: everything reaches you normally until you resume.")
            else {
                add(if (s.aiMode && s.modelLoaded) "AI Mode: the on-device model holds likely distractions." else "Rules mode: categories you chose wait in the queue.")
                add("Your allowed apps, priority contacts, codes and security alerts always come through.")
                add("Unclear or possibly important messages stay visible and are copied to the queue for review.")
                add(if (s.suppress) "Held notifications are removed from the shade. This is best-effort: one may still appear briefly." else "Held notifications also stay in the shade (removal is off).")
            }
            add(if (s.afterFocus == com.attune.app.data.AfterFocus.SUMMARY_NOTIFICATION) "When you pause or finish, Attune sends one summary notification." else "When you finish, held items wait quietly in the queue.")
        }
        lines.forEach { Row(Modifier.padding(vertical = 2.dp)) { Text("•  ", color = NavySoft); Text(it, style = MaterialTheme.typography.bodySmall, color = NavySoft) } }
        if (!connected) TextButton(onClick = onSetupAccess, contentPadding = PaddingValues(0.dp)) { Text("Set up access") }
    }
}
