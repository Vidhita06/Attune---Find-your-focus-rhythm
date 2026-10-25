package com.attune.app.ui.profile
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.attune.app.BuildConfig
import com.attune.app.data.AccessStatus
import com.attune.app.data.Flag
import com.attune.app.domain.AttentionProfile
import com.attune.app.ui.components.*
import com.attune.app.ui.theme.*
import com.attune.app.util.AppCategoryMapper

@OptIn(ExperimentalLayoutApi::class)
@Composable fun ProfileScreen(onOpenRules: () -> Unit, onOpenAccessGuide: () -> Unit, vm: ProfileViewModel = hiltViewModel()) {
    val s by vm.state.collectAsState()
    val snackbar = LocalSnackbar.current
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    var confirmReset by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    if (s.loading) { LoadingState(); return }
    val st = s.settings

    ScreenColumn {
        ScreenHeader("Profile", "Your attention profile", "Patterns from your own sessions and decisions, plus your preferences.")
        if (s.demoAvailable) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!s.showDemo, { vm.demoView.value = false }, { Text("My profile") })
            FilterChip(s.showDemo, { vm.demoView.value = true }, { Text("Demo sample") })
        }
        s.profile?.let { ProfileCard(it, s.showDemo) }

        SectionTitle("Notification access")
        AttuneCard {
            val (label, color) = when (s.access) { AccessStatus.CONNECTED -> "Allowed · connected" to LowRisk; AccessStatus.GRANTED_WAITING -> "Allowed · waiting to connect" to MedRisk; AccessStatus.NOT_GRANTED -> "Not allowed" to HighRisk }
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Status", style = MaterialTheme.typography.bodyMedium, color = NavySoft, modifier = Modifier.weight(1f)); Pill(label, color) }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = vm::openAccessSettings) { Text("Open Android settings") }
                TextButton(onClick = onOpenAccessGuide) { Text("Guide & limits") }
            }
        }

        SectionTitle("Focus")
        AttuneCard {
            Text("Default focus length", style = MaterialTheme.typography.labelLarge, color = Navy)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(15, 25, 45, 60, 90).forEach { m -> FilterChip(st.defaultFocusMin == m, { vm.setFocus(m) }, { Text("$m min") }) } }
            Spacer(Modifier.height(8.dp))
            Text("Preferred break length", style = MaterialTheme.typography.labelLarge, color = Navy)
            Text("Used in break suggestions.", style = MaterialTheme.typography.bodySmall, color = NavySoft)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(5, 10, 15).forEach { m -> FilterChip(st.breakMin == m, { vm.setBreak(m) }, { Text("$m min") }) } }
            SwitchRow("Energy check-in", "Optional self-reported energy, used only for gentle break suggestions. Not a medical measure.", st.energyCheckInEnabled) { vm.setFlag(Flag.ENERGY, it) }
        }

        SectionTitle("Notification rules")
        AttuneCard(onClick = onOpenRules) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Queue, exceptions & grouping", style = MaterialTheme.typography.labelLarge, color = Navy)
                    Text("${st.queuedCategories.size} of ${AppCategoryMapper.ALL.size} categories queued · ${st.allowedPackages.size} allowed apps · ${st.priorityContacts.size} priority contacts · grouping ${if (st.groupRepeated) "on" else "off"}",
                        style = MaterialTheme.typography.bodySmall, color = NavySoft)
                }
                Icon(Icons.Outlined.ChevronRight, null, tint = NavyFaint)
            }
        }

        SectionTitle("Demo data")
        AttuneCard {
            SwitchRow("Show demo samples", "Labelled sample notifications and history for demonstrations. Kept separate from your real records.", st.demoEnabled) { vm.setFlag(Flag.DEMO, it) }
            TextButton(onClick = { confirmReset = true }, enabled = st.demoEnabled) { Icon(Icons.Outlined.Refresh, null); Spacer(Modifier.width(6.dp)); Text("Reset demo data") }
        }

        SectionTitle("Privacy")
        AttuneCard {
            Text("Everything stays on this device. Attune has no account, no server and no analytics. Notification titles and previews are stored only so you can review them in the queue; delete them any time below.",
                style = MaterialTheme.typography.bodySmall, color = NavySoft)
            TextButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.DeleteOutline, null, tint = HighRisk); Spacer(Modifier.width(6.dp)); Text("Delete my recorded activity", color = HighRisk) }
        }
        Text("Attune ${BuildConfig.VERSION_NAME} · prediction: transparent baseline (baseline-v1), not a trained model",
            style = MaterialTheme.typography.labelMedium, color = NavyFaint)
    }

    if (confirmReset) AlertDialog(onDismissRequest = { confirmReset = false }, title = { Text("Reset demo data?") },
        text = { Text("Removes all demo samples and recreates the original set. Your real sessions and decisions are not affected.") },
        confirmButton = { TextButton(onClick = { confirmReset = false; vm.resetDemo() }) { Text("Reset") } },
        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } })
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Delete your recorded activity?") },
        text = { Text("Deletes your finished sessions, queued notifications, decisions, reports and check-ins from this device. Settings, demo samples and any running session are kept. This can't be undone.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteReal() }) { Text("Delete", color = HighRisk) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } })
}

@Composable fun SwitchRow(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = Navy)
            Text(body, style = MaterialTheme.typography.bodySmall, color = NavySoft)
        }
        Switch(checked, onChange)
    }
}

@Composable private fun ProfileCard(p: AttentionProfile, demo: Boolean) {
    AttuneCard(container = if (demo) DemoAmberBg else LavenderMist) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Attention profile", style = MaterialTheme.typography.titleMedium, color = Navy, modifier = Modifier.weight(1f))
            if (demo) DemoBadge("SAMPLE") else if (p.isColdStart) Pill("Cold start", NavySoft)
        }
        if (p.isColdStart && !demo) {
            Spacer(Modifier.height(4.dp))
            Text("Your profile fills in as you use Attune. So far: ${p.finishedSessions} of ${AttentionProfile.MIN_SESSIONS} sessions, ${p.decisions} of ${AttentionProfile.MIN_DECISIONS} decisions.",
                style = MaterialTheme.typography.bodySmall, color = NavySoft)
            Spacer(Modifier.height(6.dp))
            Text("Recorded locally: session start/end, planned vs focused time, pauses; for queued notifications the app, category, title/preview, time, and whether you opened, delayed or dismissed them; optional time-away reports and energy check-ins.",
                style = MaterialTheme.typography.bodySmall, color = NavySoft)
        }
        Spacer(Modifier.height(8.dp))
        KeyValueRow("Longest uninterrupted session", p.longestUninterruptedMin?.let { "$it min" } ?: "Not enough data")
        KeyValueRow("Best time of day", p.bestBucket?.let { "${it.name} (${(it.completionRate * 100).toInt()}% completed)" } ?: "Needs ${AttentionProfile.MIN_SESSIONS}+ sessions")
        if (p.buckets.isNotEmpty()) p.buckets.forEach { b -> KeyValueRow("  ${b.name}", "${b.sessions} sessions · avg ${b.avgFocusMin} min") }
        Spacer(Modifier.height(6.dp))
        Text("Apps with the most queued notifications", style = MaterialTheme.typography.labelLarge, color = Navy)
        if (p.topApps.isEmpty()) Text("None yet", style = MaterialTheme.typography.bodySmall, color = NavySoft)
        else p.topApps.forEach { a -> Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(a.packageName, a.label, 24.dp); Spacer(Modifier.width(8.dp))
            Text(a.label, style = MaterialTheme.typography.bodyMedium, color = Navy, modifier = Modifier.weight(1f)); Text("${a.count}", style = MaterialTheme.typography.labelLarge, color = Navy) } }
        Spacer(Modifier.height(6.dp))
        Text("Decisions by category (opened · delayed · dismissed)", style = MaterialTheme.typography.labelLarge, color = Navy)
        if (p.outcomes.isEmpty()) Text("None yet", style = MaterialTheme.typography.bodySmall, color = NavySoft)
        else p.outcomes.forEach { o -> KeyValueRow(AppCategoryMapper.displayName(o.category), "${o.opened} · ${o.delayed} · ${o.dismissed}") }
    }
}
