package com.attune.app.ui.profile
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.attune.app.BuildConfig
import com.attune.app.data.AccessStatus
import com.attune.app.data.AfterFocus
import com.attune.app.data.Flag
import com.attune.app.domain.TimerMath
import com.attune.app.ui.components.*
import com.attune.app.ui.theme.*
import com.attune.prediction.decision.OutsideFocus

/** Profile & settings hub. Each row leads to a screen that does real work; nothing here is decorative. */
@OptIn(ExperimentalLayoutApi::class)
@Composable fun ProfileScreen(onOpenRules: () -> Unit, onOpenAccessGuide: () -> Unit, onOpenAi: () -> Unit, onOpenPrivacy: () -> Unit, onOpenLearned: () -> Unit,
                              onOpenAttentionProfile: () -> Unit, vm: ProfileViewModel = hiltViewModel()) {
    val s by vm.state.collectAsState()
    val snackbar = LocalSnackbar.current
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    var confirmReset by remember { mutableStateOf(false) }
    if (s.loading) { LoadingState(); return }
    val st = s.settings

    ScreenColumn {
        ScreenHeader("Profile", "You & settings", "Your attention profile, AI Mode, rules and privacy — all on this device.")

        HubRow(Icons.Outlined.Insights, "Personal attention profile", if (s.realSessions == 0) "Starts after your first session" else "${s.realSessions} finished sessions · peak hours, distractions, energy", onOpenAttentionProfile)
        HubRow(Icons.Outlined.AutoAwesome, "AI Mode", when { !st.aiMode -> "Off · category rules decide"; s.model?.loaded != true -> "On · model unavailable, using rules"; else -> "On · ${s.model?.version}" }, onOpenAi,
            trailing = { Switch(st.aiMode, { vm.setFlag(Flag.AI_MODE, it) }) })
        HubRow(Icons.Outlined.Rule, "Notification rules & exceptions", "${s.ruleCount} rules, priority apps and contacts · Emergency bypass", onOpenRules)
        HubRow(Icons.Outlined.Psychology, "Learned preferences & feedback", if (s.learnedCount == 0) "Nothing learned yet" else "${s.learnedCount} learned adjustments · review or reset", onOpenLearned)
        HubRow(Icons.Outlined.PrivacyTip, "Privacy center", "What's stored, how it's processed, delete anything", onOpenPrivacy)
        HubRow(Icons.Outlined.NotificationsActive, "Notification access", when (s.access) { AccessStatus.CONNECTED -> "Allowed · connected"; AccessStatus.GRANTED_WAITING -> "Allowed · waiting to connect"; AccessStatus.NOT_GRANTED -> "Not allowed — tap for the guide" }, onOpenAccessGuide)

        SectionTitle("Focus & goals")
        AttuneCard {
            Text("Default focus length", style = MaterialTheme.typography.labelLarge, color = Navy)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(15, 25, 45, 60, 90).forEach { m -> FilterChip(st.defaultFocusMin == m, { vm.setFocus(m) }, { Text("$m min") }) } }
            Spacer(Modifier.height(8.dp))
            Text("Preferred break length", style = MaterialTheme.typography.labelLarge, color = Navy)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(5, 10, 15).forEach { m -> FilterChip(st.breakMin == m, { vm.setBreak(m) }, { Text("$m min") }) } }
            Spacer(Modifier.height(8.dp))
            Text("Daily focus goal (optional)", style = MaterialTheme.typography.labelLarge, color = Navy)
            Text("Used for streaks and progress. Missing a day never counts against you.", style = MaterialTheme.typography.bodySmall, color = NavySoft)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(0, 30, 60, 90, 120, 180).forEach { m -> FilterChip(st.dailyGoalMin == m, { vm.setDailyGoal(m) }, { Text(if (m == 0) "None" else TimerMath.formatMinutes(m)) }) } }
            Spacer(Modifier.height(8.dp))
            Text("Weekly focus goal (optional)", style = MaterialTheme.typography.labelLarge, color = Navy)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(0, 120, 300, 600, 900).forEach { m -> FilterChip(st.weeklyGoalMin == m, { vm.setWeeklyGoal(m) }, { Text(if (m == 0) "None" else TimerMath.formatMinutes(m)) }) } }
            SwitchRow("Focus coach", "Evidence-based tips from your own sessions on Home and in your profile.", st.coachEnabled) { vm.setFlag(Flag.COACH, it) }
            SwitchRow("Suggested focus windows", "Shows times when your sessions tend to go well. Nothing is scheduled automatically.", st.scheduleSuggestions) { vm.setFlag(Flag.SCHEDULE, it) }
            SwitchRow("Energy check-ins", "Optional 1–5 self-report, used for session-length suggestions and trends. Not a health measure.", st.energyCheckInEnabled) { vm.setFlag(Flag.ENERGY, it) }
        }

        SectionTitle("Notification handling")
        AttuneCard {
            Text("When a session ends or you take a break", style = MaterialTheme.typography.labelLarge, color = Navy)
            RadioRow("Send one summary notification", "“4 notifications waited for you” — tap to review. Android doesn't let Attune restore other apps' notifications.", st.afterFocus == AfterFocus.SUMMARY_NOTIFICATION) { vm.setAfterFocus(AfterFocus.SUMMARY_NOTIFICATION) }
            RadioRow("Keep them quietly in the queue", "Only the session-complete notice is shown.", st.afterFocus == AfterFocus.QUIET) { vm.setAfterFocus(AfterFocus.QUIET) }
            Spacer(Modifier.height(8.dp))
            Text("Outside focus sessions", style = MaterialTheme.typography.labelLarge, color = Navy)
            RadioRow("Leave notifications alone", "Attune does nothing outside focus (default).", st.outsideFocus == OutsideFocus.IGNORE) { vm.setOutsideFocus(OutsideFocus.IGNORE) }
            RadioRow("Observe for insights only", "Classify and record metadata (no text) for reports. Never holds anything.", st.outsideFocus == OutsideFocus.OBSERVE) { vm.setOutsideFocus(OutsideFocus.OBSERVE) }
            SwitchRow("Hide sensitive previews", "Codes, payments and security alerts are masked in Attune until you tap to show them.", st.hideSensitivePreviews) { vm.setFlag(Flag.HIDE_SENSITIVE, it) }
        }

        SectionTitle("Demo data")
        AttuneCard {
            SwitchRow("Show demo samples", "Labelled sample notifications and history, decided by the real on-device AI. Kept separate from your records and never used for your reports.", st.demoEnabled) { vm.setFlag(Flag.DEMO, it) }
            TextButton(onClick = { confirmReset = true }, enabled = st.demoEnabled) { Icon(Icons.Outlined.Refresh, null); Spacer(Modifier.width(6.dp)); Text("Reset demo data") }
        }
        Text("Attune ${BuildConfig.VERSION_NAME} · AI: ${s.model?.version ?: "not loaded"} (trained on synthetic data) · time-cost estimates: transparent baseline-v1",
            style = MaterialTheme.typography.labelMedium, color = NavyFaint)
    }

    if (confirmReset) AlertDialog(onDismissRequest = { confirmReset = false }, title = { Text("Reset demo data?") },
        text = { Text("Removes all demo samples and recreates the original set. Your real sessions and decisions are not affected.") },
        confirmButton = { TextButton(onClick = { confirmReset = false; vm.resetDemo() }) { Text("Reset") } },
        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } })
}

@Composable fun HubRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    AttuneCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(color = LavenderSoft, shape = MaterialTheme.shapes.medium) { Icon(icon, null, tint = LavenderDeep, modifier = Modifier.padding(8.dp).size(20.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.labelLarge, color = Navy)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = NavySoft)
            }
            if (trailing != null) trailing() else Icon(Icons.Outlined.ChevronRight, null, tint = NavyFaint)
        }
    }
}

@Composable fun RadioRow(title: String, body: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().selectable(selected, onClick = onClick, role = Role.RadioButton).padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        RadioButton(selected, null); Spacer(Modifier.width(8.dp))
        Column { Text(title, style = MaterialTheme.typography.bodyMedium, color = Navy); Text(body, style = MaterialTheme.typography.bodySmall, color = NavySoft) }
    }
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
