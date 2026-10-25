package com.attune.app.ui.insights
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.attune.app.domain.*
import com.attune.app.ui.components.*
import com.attune.app.ui.theme.*
import com.attune.app.util.Quotes
import com.attune.core.model.SessionState
import com.attune.prediction.PredictionEngine

@OptIn(ExperimentalLayoutApi::class)
@Composable fun InsightsScreen(onStartFocus: () -> Unit, onOpenProfile: () -> Unit, vm: InsightsViewModel = hiltViewModel()) {
    val s by vm.state.collectAsState()
    if (s.loading) { LoadingState(); return }
    val sum = s.summary ?: return
    ScreenColumn {
        ScreenHeader("Insights", "Your focus patterns", if (s.showDemo) "Sample data for demonstration — not your activity." else "Built from your own sessions and decisions.") {
            if (s.showDemo) DemoBadge("DEMO DATA") }
        if (s.demoAvailable) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!s.showDemo, { vm.demoView.value = false }, { Text("My activity") })
            FilterChip(s.showDemo, { vm.demoView.value = true }, { Text("Demo sample") })
        }
        if (s.showDemo) InfoBanner(Icons.Outlined.Science, "You're viewing demo data", "These synthetic records show what Insights looks like after a week of use. They are not your history.", BannerTone.DEMO)

        if (!sum.hasData) {
            AttuneCard { EmptyState(Icons.Outlined.Insights, "No activity yet", "Start a focus session. Your sessions, queued notifications and decisions will appear here — all stored only on this device.", "Start focus", onStartFocus) }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(TimerMath.formatMinutes(sum.totalFocusMin), "Total focus", Icons.Outlined.Timer, Modifier.weight(1f))
                StatTile("${sum.completed}/${sum.sessions}", "Sessions completed", Icons.Outlined.TaskAlt, Modifier.weight(1f))
                StatTile(sum.interruptionsPerHour?.let { String.format("%.1f", it) } ?: "—", "Opens per focus hour", Icons.AutoMirrored.Outlined.OpenInNew, Modifier.weight(1f))
            }

            AttuneCard {
                Text("Last 7 days", style = MaterialTheme.typography.titleMedium, color = Navy)
                Text("Minutes of focus per day", style = MaterialTheme.typography.bodySmall, color = NavySoft)
                Spacer(Modifier.height(12.dp)); WeekChart(sum.week)
            }

            AttuneCard {
                Text("Notification outcomes", style = MaterialTheme.typography.titleMedium, color = Navy)
                Spacer(Modifier.height(8.dp))
                val c = sum.counts; val max = maxOf(1, c.pending, c.opened, c.delayed, c.dismissed, c.filtered)
                OutcomeBar("Pending", c.pending, max, Lavender); OutcomeBar("Opened", c.opened, max, LowRisk)
                OutcomeBar("Delayed", c.delayed, max, MedRisk); OutcomeBar("Dismissed", c.dismissed, max, NavyFaint)
                if (c.filtered > 0) OutcomeBar("Filtered", c.filtered, max, NavyFaint)
                Spacer(Modifier.height(4.dp))
                Text("${c.queued} queued in total" + (sum.queuedPerHour?.let { " · ${String.format("%.1f", it)} per focus hour" } ?: ""), style = MaterialTheme.typography.bodySmall, color = NavySoft)
            }

            AttuneCard(container = LavenderMist) {
                Text("What delaying may have protected", style = MaterialTheme.typography.titleMedium, color = Navy)
                val p = sum.protected
                if (p == null) Text("Delay or dismiss a notification during focus to see an estimate here.", style = MaterialTheme.typography.bodySmall, color = NavySoft)
                else {
                    Text("≈${PredictionEngine.fmt(p.minutes)} min", style = MaterialTheme.typography.headlineMedium, color = Navy)
                    Text("Estimated time away + recovery avoided across ${p.decisions} delayed or dismissed notification${if (p.decisions == 1) "" else "s"} during focus, assuming each would otherwise have been opened immediately.",
                        style = MaterialTheme.typography.bodySmall, color = NavySoft)
                    Spacer(Modifier.height(4.dp))
                    Text(if (p.coldStart) "Low confidence: mostly starting assumptions. A baseline estimate, not a measurement or proof of cause." else "A baseline estimate, not a measurement or proof of cause.",
                        style = MaterialTheme.typography.labelMedium, color = NavyFaint)
                }
            }

            s.profile?.let { pr -> AttuneCard(onClick = onOpenProfile) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Attention profile", style = MaterialTheme.typography.titleMedium, color = Navy, modifier = Modifier.weight(1f))
                    Icon(Icons.Outlined.ChevronRight, null, tint = NavyFaint) }
                Text(when {
                    pr.isColdStart -> "Cold start: ${pr.finishedSessions} of ${AttentionProfile.MIN_SESSIONS} sessions and ${pr.decisions} of ${AttentionProfile.MIN_DECISIONS} decisions so far."
                    pr.bestBucket != null -> "${pr.bestBucket.name} sessions complete most often (${(pr.bestBucket.completionRate * 100).toInt()}%)."
                    pr.topApps.isNotEmpty() -> "${pr.topApps.first().label} sends the most queued notifications."
                    else -> "See your patterns." }, style = MaterialTheme.typography.bodySmall, color = NavySoft)
            } }

            SectionTitle("Session history")
            if (sum.history.isEmpty()) Text("No sessions yet.", style = MaterialTheme.typography.bodySmall, color = NavySoft)
            else AttuneCard { sum.history.take(15).forEachIndexed { i, r ->
                if (i > 0) HorizontalDivider(color = CardBorder)
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(relativeTime(r.session.sessionStartMs), style = MaterialTheme.typography.labelLarge, color = Navy)
                        Text("${r.focusedMin} of ${r.plannedMin} min · ${r.queued} queued · ${r.session.interruptionCount} opened during focus", style = MaterialTheme.typography.bodySmall, color = NavySoft)
                    }
                    when (r.session.state) {
                        SessionState.COMPLETED -> Pill("Completed", LowRisk); SessionState.ENDED_EARLY -> Pill("Ended early", MedRisk)
                        else -> Pill("In progress", Lavender) }
                }
            } }
        }

        if (s.energyEnabled || s.checkins.isNotEmpty()) {
            SectionTitle("Energy check-ins")
            if (s.checkins.isEmpty()) Text("No check-ins yet. They're optional — use the card on Home or Focus.", style = MaterialTheme.typography.bodySmall, color = NavySoft)
            else AttuneCard { s.checkins.forEach { c -> KeyValueRow(relativeTime(c.timestampMs), label(c.level)) } }
        }
        val q = Quotes.forInsights(); InspirationQuote(q.text, q.author)
    }
}

@Composable private fun WeekChart(days: List<DayBar>) {
    val max = (days.maxOfOrNull { it.minutes } ?: 0).coerceAtLeast(1)
    Row(Modifier.fillMaxWidth().height(130.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        days.forEach { d -> Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (d.minutes > 0) "${d.minutes}" else "", style = MaterialTheme.typography.labelSmall, color = NavySoft)
            Box(Modifier.width(18.dp).height((90 * d.minutes / max).coerceAtLeast(3).dp)
                .background(if (d.isToday) Lavender else Lavender.copy(alpha = 0.35f), RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)))
            Spacer(Modifier.height(4.dp))
            Text(d.label, style = MaterialTheme.typography.labelMedium, color = if (d.isToday) Navy else NavyFaint, textAlign = TextAlign.Center)
        } }
    }
}

@Composable private fun OutcomeBar(label: String, value: Int, max: Int, color: androidx.compose.ui.graphics.Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = NavySoft, modifier = Modifier.width(76.dp))
        Box(Modifier.weight(1f).height(10.dp).background(LavenderMist, RoundedCornerShape(5.dp))) {
            Box(Modifier.fillMaxWidth(value.toFloat() / max).fillMaxHeight().background(color, RoundedCornerShape(5.dp)))
        }
        Text("$value", style = MaterialTheme.typography.labelLarge, color = Navy, modifier = Modifier.width(32.dp), textAlign = TextAlign.End)
    }
}
