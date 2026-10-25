package com.attune.app.ui.insights
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.attune.app.domain.*
import com.attune.app.ui.components.*
import com.attune.app.ui.theme.*
import com.attune.core.model.SessionState
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable fun InsightsScreen(onStartFocus: () -> Unit, onOpenSession: (String) -> Unit, vm: InsightsViewModel = hiltViewModel()) {
    val s by vm.state.collectAsState()
    val tab by vm.tab.collectAsState()
    val period by vm.period.collectAsState()
    LaunchedEffect(Unit) { InsightsNav.request.collect { t -> if (t != null) { vm.tab.value = t; InsightsNav.request.value = null } } }
    if (s.loading) { LoadingState(); return }
    ScreenColumn {
        ScreenHeader("Insights", if (tab == InsightsTab.REPORTS) "Reports" else "Attention profile",
            if (s.showDemo) "Sample data for demonstration — not your activity." else "Built only from what Attune recorded on this device.") { if (s.showDemo) DemoBadge("DEMO DATA") }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(tab == InsightsTab.REPORTS, { vm.tab.value = InsightsTab.REPORTS }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Reports") }
            SegmentedButton(tab == InsightsTab.PROFILE, { vm.tab.value = InsightsTab.PROFILE }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("Profile") }
        }
        if (s.demoAvailable) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!s.showDemo, { vm.demoView.value = false }, { Text("My activity") })
            FilterChip(s.showDemo, { vm.demoView.value = true }, { Text("Demo sample") })
        }
        if (tab == InsightsTab.REPORTS) s.report?.let { ReportsContent(it, period, s.canGoForward, s.showDemo, vm, onStartFocus, onOpenSession) }
        else s.profile?.let { ProfileContent(it, s.history, s.showDemo, onStartFocus, onOpenSession) }
    }
}

// ------------------------------------------------------------------------------------------------------------- reports

@Composable private fun ReportsContent(r: Report, period: Period, canForward: Boolean, demo: Boolean, vm: InsightsViewModel, onStartFocus: () -> Unit, onOpenSession: (String) -> Unit) {
    val ctx = LocalContext.current
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        Period.entries.forEachIndexed { i, p -> SegmentedButton(period == p, { vm.setPeriod(p) }, SegmentedButtonDefaults.itemShape(i, 3)) {
            Text(when (p) { Period.DAY -> "Daily"; Period.WEEK -> "Weekly"; Period.MONTH -> "Monthly" }) } }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { vm.step(-1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous ${period.name.lowercase()}") }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(r.range.label, style = MaterialTheme.typography.titleMedium, color = Navy)
            if (r.range.isCurrent) Text("In progress — figures so far", style = MaterialTheme.typography.labelMedium, color = NavyFaint)
        }
        IconButton(onClick = { vm.step(1) }, enabled = canForward) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next ${period.name.lowercase()}") }
    }
    if (!r.hasData) { AttuneCard { EmptyState(Icons.Outlined.BarChart, "Nothing recorded in this ${period.name.lowercase()}",
        "Reports fill in from your focus sessions, the notifications Attune evaluated during focus, and optional energy check-ins.", "Start focus", onStartFocus) }; return }

    val t = r.totals; val p = r.previous
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatTile(TimerMath.formatMinutes(t.focusMin), "Focus time" + delta(t.focusMin, p?.focusMin, "m"), Icons.Outlined.Timer, Modifier.weight(1f))
        StatTile("${t.completed}/${t.sessions}", "Completed as planned", Icons.Outlined.TaskAlt, Modifier.weight(1f))
        StatTile("${t.openedDuringFocus}", "Opened during focus" + delta(t.openedDuringFocus, p?.openedDuringFocus, ""), Icons.Outlined.TouchApp, Modifier.weight(1f))
    }
    if (p != null) Text("Change is compared with the same stretch of the previous ${period.name.lowercase()}${if (r.range.isCurrent) " (equal time elapsed)" else ""}.",
        style = MaterialTheme.typography.labelMedium, color = NavyFaint)

    val labels = r.buckets.map { AxisLabel(it.label, it.showLabel) }
    val unitX = if (period == Period.DAY) "hour" else "day"
    ChartCard("Focus time", "Minutes focused, by the $unitX each session started") {
        BarChart(listOf(ChartSeries("Focus", SeriesA, r.focusMin.map { it.toFloat() })), labels, "min", description = "Focus minutes per $unitX")
    }
    ChartCard("Notifications during focus", "Evaluated by Attune vs held vs opened from Attune, per $unitX") {
        BarChart(listOf(ChartSeries("Received", SeriesA, r.received.map { it.toFloat() }), ChartSeries("Held", SeriesB, r.held.map { it.toFloat() }),
            ChartSeries("Opened", SeriesC, r.opened.map { it.toFloat() })), labels, "notifications")
        Spacer(Modifier.height(6.dp))
        KeyValueRow("Received during focus", "${t.received}"); KeyValueRow("Held (incl. filtered)", "${t.held}")
        KeyValueRow("Kept visible for review", "${t.review}"); KeyValueRow("Let through", "${t.allowed}"); KeyValueRow("Opened from Attune", "${t.opened}")
    }
    ChartCard("Interruptions", "Notifications you opened from Attune during focus. Opens from the notification shade can't be seen, so this undercounts.") {
        BarChart(listOf(ChartSeries("Interruptions", SeriesC, r.interruptions.map { it.toFloat() })), labels, "opens")
    }
    ChartCard("By app", "Notifications during focus per app (held · opened shown below each)") {
        if (r.apps.isEmpty()) Text("No notifications were evaluated during focus in this range.", style = MaterialTheme.typography.bodySmall, color = NavySoft)
        else RankedBars(r.apps.take(6).map { Triple(it.label, it.received, "${it.held} held · ${it.opened} opened") }) { i -> AppIcon(r.apps[i].packageName, r.apps[i].label, 28.dp); Spacer(Modifier.width(10.dp)) }
    }
    ChartCard("Focus risk", "How notifications during focus were classified (${r.range.label})") {
        RiskBar(r.risk)
    }
    ChartCard("Session length", "Focused minutes per finished session, in order") {
        if (r.sessions.size < 2) Text("Needs at least 2 finished sessions in this range.", style = MaterialTheme.typography.bodySmall, color = NavySoft)
        else LineChart(r.sessions.map { it.focusedMin.toFloat() }, r.sessions.mapIndexed { i, sp -> AxisLabel(SimpleDateFormat("d/M", Locale.getDefault()).format(Date(sp.startMs)), i == 0 || i == r.sessions.lastIndex || r.sessions.size <= 7) },
            0f, (r.sessions.maxOf { maxOf(it.plannedMin, it.focusedMin) }).toFloat(), "min", description = "Focused minutes per session")
    }
    ChartCard("Completion", "Share of finished sessions completed as planned, per $unitX") {
        if (r.completion.all { it == null }) Text("No finished sessions in this range.", style = MaterialTheme.typography.bodySmall, color = NavySoft)
        else LineChart(r.completion.map { it?.times(100f) }, labels, 0f, 100f, "%", color = SeriesB, yLabels = listOf("100%", "50%", "0%"))
    }
    ChartCard("Energy", "Average self-reported energy (1–5) per $unitX") {
        if (r.energy.all { it == null }) Text("No energy check-ins in this range. They're optional.", style = MaterialTheme.typography.bodySmall, color = NavySoft)
        else LineChart(r.energy, labels, 1f, 5f, "/ 5", yLabels = listOf("5", "3", "1"))
    }
    OutlinedButton(onClick = {
        val text = ReportCalculator.shareText(r) + if (demo) "\n(DEMO DATA — not real activity)" else ""
        runCatching { ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share summary")) }
    }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Outlined.Share, null); Spacer(Modifier.width(8.dp)); Text("Share summary (counts only)") }
    if (r.sessions.isNotEmpty()) {
        SectionTitle("Sessions in this range")
        AttuneCard { r.sessions.reversed().take(10).forEachIndexed { i, sp -> if (i > 0) HorizontalDivider(color = CardBorder); SessionRowItem(sp) { onOpenSession(sp.id) } } }
    }
}

private fun delta(cur: Int, prev: Int?, unit: String): String {
    if (prev == null) return ""
    val d = cur - prev
    return if (d == 0) " · same" else " · ${if (d > 0) "+" else "−"}${kotlin.math.abs(d)}$unit"
}

@Composable private fun ChartCard(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    AttuneCard {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Navy)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = NavySoft)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable fun RiskBar(risk: Map<String, Int>) {
    val keys = listOf("LOW", "MEDIUM", "HIGH", "UNKNOWN")
    StackedShareBar(keys.map { Segment(focusRiskLabel(it), risk[it] ?: 0, focusRiskColor(it)) },
        icons = keys.map { k -> @Composable { Icon(focusRiskIcon(k), null, tint = focusRiskColor(k), modifier = Modifier.size(14.dp)); Spacer(Modifier.width(6.dp)) } })
}

@Composable private fun SessionRowItem(sp: SessionPoint, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(relativeTime(sp.startMs), style = MaterialTheme.typography.labelLarge, color = Navy)
                Text("${sp.focusedMin} of ${sp.plannedMin} min focused", style = MaterialTheme.typography.bodySmall, color = NavySoft)
            }
            if (sp.state == SessionState.COMPLETED) Pill("Completed", LowRisk) else Pill("Ended early", MedRisk)
            Icon(Icons.Outlined.ChevronRight, null, tint = NavyFaint)
        }
    }
}

// ------------------------------------------------------------------------------------------------------------- profile

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun ProfileContent(p: AttentionProfileV2, history: List<SessionPoint>, demo: Boolean, onStartFocus: () -> Unit, onOpenSession: (String) -> Unit) {
    val o = p.overview
    if (!p.hasSessions && p.distraction.receivedDuringFocus == 0) {
        AttuneCard { EmptyState(Icons.Outlined.Insights, "Your profile starts with your first session",
            "It will show your focus time, completion, peak hours, what interrupts you and how your energy relates to sessions — all from records kept on this device.", "Start focus", onStartFocus) }
        return
    }
    // Score + streak
    AttuneCard(container = Navy) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Weekly focus score", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.75f))
                Text(p.score?.let { "${it.total}" } ?: "—", style = MaterialTheme.typography.displayLarge, color = Color.White)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${p.streak.current}", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                Text("day streak", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.75f))
                Text("best ${p.streak.best}", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
            }
        }
        val sc = p.score
        if (sc != null) sc.explanation.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f)) }
        else Text("Finish a session this week to get a score.", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f))
        Spacer(Modifier.height(4.dp))
        Text("A progress indicator from your sessions — not a scientific measure of attention. Streaks count ${p.streak.rule}; missing a day just starts a new one.",
            style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
    }

    // A. focus overview
    SectionTitle("Focus overview")
    AttuneCard {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MiniStat(TimerMath.formatMinutes(o.totalFocusMin), "total focus", Modifier.weight(1f))
            MiniStat("${o.sessions}", "sessions", Modifier.weight(1f))
            MiniStat(o.avgSessionMin?.let { "${it}m" } ?: "—", "average", Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        StackedShareBar(listOf(Segment("Completed as planned", o.completed, SeriesB), Segment("Ended early", o.endedEarly, SeriesC)))
        if (o.dailyGoalMin > 0) GoalLine("Today", o.todayMin, o.dailyGoalMin)
        if (o.weeklyGoalMin > 0) GoalLine("Last 7 days", o.weekMin, o.weeklyGoalMin)
        Spacer(Modifier.height(8.dp))
        Text("Consistency: focused on ${o.activeDays14} of the last 14 days", style = MaterialTheme.typography.labelLarge, color = Navy)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            val max = (o.last14.maxOrNull() ?: 0).coerceAtLeast(1)
            o.last14.forEach { m -> Box(Modifier.weight(1f).height(18.dp).clip(RoundedCornerShape(4.dp))
                .background(if (m == 0) LavenderMist else Lavender.copy(alpha = 0.3f + 0.7f * m / max))) }
        }
        Row { Text("14 days ago", style = MaterialTheme.typography.labelSmall, color = NavyFaint, modifier = Modifier.weight(1f)); Text("today", style = MaterialTheme.typography.labelSmall, color = NavyFaint) }
    }

    // B. distraction analysis
    val d = p.distraction
    SectionTitle("Distraction analysis")
    AttuneCard {
        KeyValueRow("Notifications evaluated during focus", "${d.receivedDuringFocus}")
        KeyValueRow("Classified as likely distracting", "${d.classifiedDistracting}")
        KeyValueRow("Held in the queue", "${d.held}")
        KeyValueRow("Kept visible for review", "${d.keptVisible}")
        KeyValueRow("Let through", "${d.allowed}")
        KeyValueRow("Opened from Attune during focus", "${d.openedDuringFocus}")
        Text("“Received” means Attune evaluated it; it doesn't mean it interrupted you. Opens from the shade aren't visible to Attune.", style = MaterialTheme.typography.labelMedium, color = NavyFaint)
        if (d.interruptionRateRecent != null || d.interruptionRatePrior != null) {
            Spacer(Modifier.height(6.dp))
            KeyValueRow("Opens per focus hour, last 7 days", d.interruptionRateRecent?.let { "%.1f".format(it) } ?: "too little focus time")
            KeyValueRow("…the 7 days before", d.interruptionRatePrior?.let { "%.1f".format(it) } ?: "too little focus time")
        }
        if (d.topCategories.isNotEmpty()) { Spacer(Modifier.height(8.dp)); Text("Most frequent categories", style = MaterialTheme.typography.labelLarge, color = Navy)
            RankedBars(d.topCategories.map { Triple(it.label, it.count, null) }, SeriesB) }
        if (d.topApps.isNotEmpty()) { Spacer(Modifier.height(8.dp)); Text("Most active apps during focus", style = MaterialTheme.typography.labelLarge, color = Navy)
            RankedBars(d.topApps.map { Triple(it.label, it.received, "${it.held} held · ${it.opened} opened") }) { i -> AppIcon(d.topApps[i].packageName, d.topApps[i].label, 28.dp); Spacer(Modifier.width(10.dp)) } }
    }

    // C. peak hours
    SectionTitle("Peak focus hours")
    AttuneCard {
        if (o.sessions < ProfileCalculator.MIN_SESSIONS_FOR_PATTERNS) Text("Needs ${ProfileCalculator.MIN_SESSIONS_FOR_PATTERNS} finished sessions (you have ${o.sessions}).", style = MaterialTheme.typography.bodySmall, color = NavySoft)
        else {
            p.peak?.let { pk -> Text("${ProfileCalculator.hourLabel(pk.startHour)}–${ProfileCalculator.hourLabel(pk.endHour)}", style = MaterialTheme.typography.headlineMedium, color = Navy)
                Text("${pk.sessions} sessions · ${(pk.completionRate * 100).roundToInt()}% completed · ${"%.1f".format(pk.interruptionsPerHour)} opens per hour", style = MaterialTheme.typography.bodySmall, color = NavySoft) }
                ?: Text("No 2-hour window has 2+ sessions with at least half completed yet.", style = MaterialTheme.typography.bodySmall, color = NavySoft)
            Spacer(Modifier.height(8.dp))
            val hourLabels = p.hours.map { AxisLabel(if (it.hour % 6 == 0) "${it.hour}h" else "", it.hour % 6 == 0) }
            Text("Completed focus by start hour", style = MaterialTheme.typography.labelLarge, color = Navy)
            BarChart(listOf(ChartSeries("Completed focus", SeriesA, p.hours.map { it.focusMin.toFloat() })), hourLabels, "min", height = 100.dp,
                description = "Completed focus minutes by hour of day")
            Spacer(Modifier.height(8.dp))
            Text("Opens during focus by hour", style = MaterialTheme.typography.labelLarge, color = Navy)
            BarChart(listOf(ChartSeries("Opens", SeriesC, p.hours.map { it.interruptions.toFloat() })), hourLabels, "opens", height = 80.dp,
                description = "Notifications opened from Attune during focus by hour of day")
            Text("Based on your recorded sessions only; this is not a biological measure of attention.", style = MaterialTheme.typography.labelMedium, color = NavyFaint)
        }
    }

    // D. energy
    SectionTitle("Energy")
    AttuneCard {
        val e = p.energy
        if (e.recent.isEmpty()) Text("No check-ins yet. They're optional: tap 1–5 on Home or before a session.", style = MaterialTheme.typography.bodySmall, color = NavySoft)
        else {
            Text("Average ${"%.1f".format(e.average ?: 0f)} / 5 over your last ${e.recent.size} check-ins", style = MaterialTheme.typography.labelLarge, color = Navy)
            LineChart(e.recent.map { it.score.toFloat() }, e.recent.mapIndexed { i, c -> AxisLabel(SimpleDateFormat("d/M", Locale.getDefault()).format(Date(c.timestampMs)), i == 0 || i == e.recent.lastIndex) },
                1f, 5f, "/ 5", yLabels = listOf("5", "3", "1"), description = "Recent energy check-ins")
            if (e.highEnergyCompletion != null && e.lowEnergyCompletion != null)
                Text("Completion after energy 4–5: ${(e.highEnergyCompletion * 100).roundToInt()}% (${e.highN}) · after 1–2: ${(e.lowEnergyCompletion * 100).roundToInt()}% (${e.lowN}).", style = MaterialTheme.typography.bodySmall, color = NavySoft)
            else Text("Comparing energy with session outcomes needs 2+ sessions after high and after low check-ins.", style = MaterialTheme.typography.bodySmall, color = NavySoft)
        }
        Text("Self-reported and non-medical. Attune doesn't infer or diagnose anything about your health.", style = MaterialTheme.typography.labelMedium, color = NavyFaint)
    }

    // E. risk distribution
    SectionTitle("Notification risk distribution")
    AttuneCard {
        RiskBar(p.risk)
        if (p.riskFrom != null && p.riskTo != null) Text("During focus · ${SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(p.riskFrom))} – ${SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(p.riskTo))}",
            style = MaterialTheme.typography.labelMedium, color = NavyFaint)
    }

    // F. insights, coach, schedule
    SectionTitle("Personalised insights")
    if (p.insights.isEmpty()) AttuneCard { Text("Not enough data for reliable observations yet. Try: a few more sessions, optional energy check-ins, and feedback on queued notifications.",
        style = MaterialTheme.typography.bodySmall, color = NavySoft) }
    else p.insights.forEach { InsightCard(Icons.Outlined.Insights, it) }
    if (p.coach.isNotEmpty()) { SectionTitle("Focus coach"); p.coach.forEach { InsightCard(Icons.Outlined.Lightbulb, it) } }
    if (p.schedule.isNotEmpty()) { SectionTitle("Suggested focus windows")
        AttuneCard { Text("Optional ideas from when your sessions tend to go well. Attune doesn't schedule anything for you.", style = MaterialTheme.typography.bodySmall, color = NavySoft)
            p.schedule.forEach { KeyValueRow(it.title, it.evidence.substringBefore(" sessions") + " sessions") } } }

    if (history.isNotEmpty()) {
        SectionTitle("Session timelines")
        AttuneCard { history.take(8).forEachIndexed { i, sp -> if (i > 0) HorizontalDivider(color = CardBorder); SessionRowItem(sp) { onOpenSession(sp.id) } } }
    }
    if (demo) Text("Demo profile — synthetic data.", style = MaterialTheme.typography.labelMedium, color = DemoAmber)
}

@Composable private fun MiniStat(value: String, label: String, modifier: Modifier) {
    Column(modifier) { Text(value, style = MaterialTheme.typography.titleLarge, color = Navy); Text(label, style = MaterialTheme.typography.labelMedium, color = NavySoft) }
}

@Composable private fun GoalLine(label: String, v: Int, goal: Int) {
    Spacer(Modifier.height(8.dp))
    Row { Text("$label: ${TimerMath.formatMinutes(v)} of ${TimerMath.formatMinutes(goal)}", style = MaterialTheme.typography.labelLarge, color = Navy, modifier = Modifier.weight(1f))
        if (v >= goal) Pill("Goal reached", LowRisk) }
    LinearProgressIndicator(progress = { (v.toFloat() / goal).coerceIn(0f, 1f) }, Modifier.fillMaxWidth().padding(top = 4.dp), color = Lavender, trackColor = LavenderSoft)
}

@Composable private fun InsightCard(icon: androidx.compose.ui.graphics.vector.ImageVector, i: Insight) {
    AttuneCard {
        Row(verticalAlignment = Alignment.Top) {
            Icon(icon, null, tint = Lavender, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(10.dp))
            Column { Text(i.title, style = MaterialTheme.typography.labelLarge, color = Navy); Text(i.evidence, style = MaterialTheme.typography.bodySmall, color = NavySoft) }
        }
    }
}
