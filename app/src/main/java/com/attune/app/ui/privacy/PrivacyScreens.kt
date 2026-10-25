package com.attune.app.ui.privacy
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.attune.app.data.*
import com.attune.app.ui.components.*
import com.attune.app.ui.theme.*
import com.attune.core.model.FeedbackEvent
import com.attune.core.model.SessionEvent
import com.attune.prediction.decision.FeedbackKind
import com.attune.prediction.decision.LearnedPreference
import com.attune.prediction.decision.PreferenceScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ------------------------------------------------------------------------------------------------- Privacy center

@HiltViewModel class PrivacyViewModel @Inject constructor(private val privacy: PrivacyRepository) : ViewModel() {
    val counts = privacy.counts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4); val messages: SharedFlow<String> = _messages
    fun run(label: String, block: suspend () -> Unit) = viewModelScope.launch { block(); _messages.emit("$label — done.") }
    val repo get() = privacy
}

@Composable fun PrivacyCenterScreen(onBack: () -> Unit, onOpenLearned: () -> Unit, vm: PrivacyViewModel = hiltViewModel()) {
    val c by vm.counts.collectAsState()
    val snackbar = LocalSnackbar.current
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    var confirm by remember { mutableStateOf<Triple<String, String, suspend () -> Unit>?>(null) }
    ScreenColumn {
        DetailTopBar("Privacy center", onBack)
        AttuneCard(container = LavenderMist) {
            Text("How Attune handles your notifications", style = MaterialTheme.typography.titleMedium, color = Navy)
            listOf(
                "Everything is processed on this phone. Attune has no account, server, analytics SDK or cloud AI; the app doesn't use the internet.",
                "The AI reads a notification's title and text in memory to decide. The decision log stores only app, category, time, scores and generic reasons — never message text.",
                "Held or review items keep their title and preview so you can read them in the queue; Attune deletes that text 24 hours after you handle an item.",
                "Senders are stored as a one-way hash plus a masked hint (e.g. R••••l) for learned preferences.",
                "Links are detected locally and never opened, fetched or checked online.",
                "Attune never writes notification content to system logs. Backups of Attune data are disabled.",
                "Demo data is labelled and stored separately; it never enters your reports or personalization."
            ).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = NavySoft, modifier = Modifier.padding(vertical = 2.dp)) }
        }
        SectionTitle("What's stored")
        AttuneCard {
            KeyValueRow("Decision records (metadata only)", "${c?.decisions ?: "…"}")
            KeyValueRow("Queue items still holding text", "${c?.queueWithText ?: "…"}")
            KeyValueRow("Feedback entries", "${c?.feedback ?: "…"}")
            KeyValueRow("Rules you created", "${c?.rules ?: "…"}")
        }
        SectionTitle("Delete")
        val r = vm.repo
        DeleteRow(Icons.Outlined.TextSnippet, "Remove stored notification text now", "Clears titles and previews from every real queue item. Decisions and stats stay.") {
            confirm = Triple("Remove notification text?", "Queue items will show “content removed”. This can't be undone.") { r.scrubNotificationText() } }
        DeleteRow(Icons.Outlined.Psychology, "Delete feedback & reset personalization", "Removes all feedback; learned adjustments disappear. Rules stay.") {
            confirm = Triple("Reset personalization?", "All feedback is deleted and the AI goes back to its default behaviour.") { r.deleteFeedbackAndPersonalization() } }
        TextButton(onClick = onOpenLearned) { Text("Review learned preferences one by one") }
        DeleteRow(Icons.Outlined.BarChart, "Delete activity & analytics", "Finished sessions, decision log, queue history, opens and time-cost reports. A running session is kept.") {
            confirm = Triple("Delete activity & analytics?", "Your reports and profile will start again from zero. Settings and demo data are kept.") { r.deleteActivityAndAnalytics() } }
        DeleteRow(Icons.Outlined.BatteryChargingFull, "Delete energy check-ins", "All your self-reported energy entries.") {
            confirm = Triple("Delete energy check-ins?", "This can't be undone.") { r.deleteEnergy() } }
        DeleteRow(Icons.Outlined.Rule, "Delete rules", "Keyword and category rules you or your feedback created. Allowed apps and priority contacts are managed under Rules.") {
            confirm = Triple("Delete rules?", "The AI and your category settings will decide instead.") { r.deleteRules() } }
        DeleteRow(Icons.Outlined.DeleteForever, "Delete everything I've recorded", "All of the above. Settings and demo samples are kept.", danger = true) {
            confirm = Triple("Delete all your data?", "Everything Attune recorded about your activity is removed from this phone. This can't be undone.") { r.deleteEverything() } }
    }
    confirm?.let { (title, body, action) -> AlertDialog(onDismissRequest = { confirm = null }, title = { Text(title) }, text = { Text(body) },
        confirmButton = { TextButton(onClick = { confirm = null; vm.run(title.removeSuffix("?"), action) }) { Text("Delete", color = HighRisk) } },
        dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } }) }
}

@Composable private fun DeleteRow(icon: ImageVector, title: String, body: String, danger: Boolean = false, onClick: () -> Unit) {
    AttuneCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (danger) HighRisk else Lavender, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.labelLarge, color = if (danger) HighRisk else Navy); Text(body, style = MaterialTheme.typography.bodySmall, color = NavySoft) }
            Icon(Icons.Outlined.DeleteOutline, null, tint = NavyFaint)
        }
    }
}

// ------------------------------------------------------------------------------------------------- Learned preferences

@HiltViewModel class LearnedViewModel @Inject constructor(private val p: PersonalizationRepository) : ViewModel() {
    val learned = p.learned(false).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val events = p.events.map { l -> l.filter { !it.isDemo } }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun delete(pref: LearnedPreference) = viewModelScope.launch { p.deletePreference(pref) }
    fun deleteEvent(id: String) = viewModelScope.launch { p.deleteEvent(id) }
    fun reset() = viewModelScope.launch { p.resetAll() }
}

@Composable fun LearnedPreferencesScreen(onBack: () -> Unit, vm: LearnedViewModel = hiltViewModel()) {
    val learned by vm.learned.collectAsState(); val events by vm.events.collectAsState()
    var confirm by remember { mutableStateOf(false) }
    ScreenColumn {
        DetailTopBar("Learned preferences", onBack)
        InfoBanner(Icons.Outlined.Info, "Personalization, not retraining", "Your feedback nudges the model's importance score for an app, a type of notification or a sender. Each nudge is small and capped, sender history is weighted lowest, and rules and safety checks always come first. The model file itself never changes.")
        val l = learned
        when {
            l == null -> LoadingState()
            l.isEmpty() -> AttuneCard { EmptyState(Icons.Outlined.Psychology, "Nothing learned yet", "Use “Mark important” or “Correct it” on a notification. Adjustments appear here.") }
            else -> AttuneCard { l.forEachIndexed { i, v -> if (i > 0) HorizontalDivider(color = CardBorder)
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(v.label, style = MaterialTheme.typography.labelLarge, color = Navy)
                        Text("${when (v.pref.scope) { PreferenceScope.APP -> "App"; PreferenceScope.CATEGORY -> "Type"; PreferenceScope.SENDER -> "Sender" }} · " +
                            "${if (v.pref.bias > 0) "more" else "less"} important (${"%+.2f".format(v.pref.bias)}) from ${v.pref.events} feedback", style = MaterialTheme.typography.bodySmall, color = NavySoft)
                    }
                    IconButton(onClick = { vm.delete(v.pref) }) { Icon(Icons.Outlined.DeleteOutline, "Forget this preference", tint = NavySoft) }
                } } }
        }
        SectionTitle("Feedback history")
        if (events.isEmpty()) Text("No feedback yet.", style = MaterialTheme.typography.bodySmall, color = NavySoft)
        else AttuneCard { events.take(40).forEachIndexed { i, e -> if (i > 0) HorizontalDivider(color = CardBorder); FeedbackRow(e) { vm.deleteEvent(e.id) } } }
        if (events.isNotEmpty()) OutlinedButton(onClick = { confirm = true }, modifier = Modifier.fillMaxWidth()) { Text("Reset personalization", color = HighRisk) }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Reset personalization?") }, text = { Text("Deletes all your feedback. Rules you created stay until you remove them.") },
        confirmButton = { TextButton(onClick = { confirm = false; vm.reset() }) { Text("Reset", color = HighRisk) } }, dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } })
}

@Composable private fun FeedbackRow(e: FeedbackEvent, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        AppIcon(e.packageName, e.appLabel, 28.dp); Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(runCatching { FeedbackKind.valueOf(e.kind).label }.getOrDefault(e.kind), style = MaterialTheme.typography.labelLarge, color = Navy)
            Text("${e.appLabel}${e.senderHint?.let { " · $it" } ?: ""} · ${relativeTime(e.createdMs)}", style = MaterialTheme.typography.bodySmall, color = NavySoft)
        }
        IconButton(onClick = onDelete) { Icon(Icons.Outlined.Close, "Delete this feedback", tint = NavyFaint) }
    }
}

// ------------------------------------------------------------------------------------------------- Session timeline

@HiltViewModel class TimelineViewModel @Inject constructor(private val timeline: TimelineRepository, private val sessions: FocusSessionRepository) : ViewModel() {
    private val id = MutableStateFlow<String?>(null)
    @OptIn(ExperimentalCoroutinesApi::class)
    val events: StateFlow<List<SessionEvent>?> = id.filterNotNull().flatMapLatest { timeline.forSession(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    @OptIn(ExperimentalCoroutinesApi::class)
    val session = id.filterNotNull().flatMapLatest { sid -> sessions.all.map { l -> l.firstOrNull { it.id == sid } } }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    fun load(s: String) { id.value = s }
}

@Composable fun SessionTimelineScreen(sessionId: String, onBack: () -> Unit, vm: TimelineViewModel = hiltViewModel()) {
    LaunchedEffect(sessionId) { vm.load(sessionId) }
    val ev by vm.events.collectAsState(); val s by vm.session.collectAsState()
    ScreenColumn {
        DetailTopBar("Session timeline", onBack)
        s?.let { ss -> Text("${relativeTime(ss.sessionStartMs)} · ${com.attune.app.domain.TimerMath.plannedMinutes(ss)} min planned${if (ss.isDemo) " · demo" else ""}",
            style = MaterialTheme.typography.bodyMedium, color = NavySoft) }
        val list = ev
        when {
            list == null -> LoadingState()
            list.isEmpty() -> AttuneCard { EmptyState(Icons.Outlined.Timeline, "No events recorded", "Sessions from before this version have no timeline. New sessions record starts, pauses, held and allowed notifications, and your actions.") }
            else -> AttuneCard { list.forEachIndexed { i, e ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                    Text(clockTime(e.atMs), style = MaterialTheme.typography.labelMedium, color = NavyFaint, modifier = Modifier.width(64.dp))
                    val (icon, color) = timelineStyle(e.type)
                    Icon(icon, null, tint = color, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(timelineLabel(e.type), style = MaterialTheme.typography.labelLarge, color = Navy)
                        e.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = NavySoft) }
                    }
                    if (e.isDemo) DemoBadge()
                }
                if (i < list.lastIndex) HorizontalDivider(color = CardBorder)
            } }
        }
        Text("Shows events Attune observed: notifications it evaluated, your actions in Attune, and timer changes. App names only — no message content.",
            style = MaterialTheme.typography.labelMedium, color = NavyFaint)
    }
}

private fun timelineLabel(t: String) = when (t) {
    TimelineType.START -> "Focus started"; TimelineType.PAUSE -> "Paused"; TimelineType.BREAK -> "Break started"; TimelineType.RESUME -> "Resumed"
    TimelineType.COMPLETED -> "Completed"; TimelineType.ENDED_EARLY -> "Ended early"; TimelineType.HELD -> "Notification held"; TimelineType.ALLOWED -> "Notification let through"
    TimelineType.REVIEW -> "Kept visible for review"; TimelineType.FILTERED -> "Notification filtered"; TimelineType.OPENED -> "You opened a notification"
    TimelineType.DELAYED -> "You delayed a notification"; TimelineType.DISMISSED -> "You dismissed a notification"; TimelineType.ENERGY -> "Energy check-in"; else -> t
}
private fun timelineStyle(t: String): Pair<ImageVector, androidx.compose.ui.graphics.Color> = when (t) {
    TimelineType.START, TimelineType.RESUME -> Icons.Outlined.PlayCircle to Lavender
    TimelineType.PAUSE, TimelineType.BREAK -> Icons.Outlined.PauseCircle to MedRisk
    TimelineType.COMPLETED -> Icons.Outlined.TaskAlt to LowRisk
    TimelineType.ENDED_EARLY -> Icons.Outlined.StopCircle to MedRisk
    TimelineType.HELD, TimelineType.FILTERED -> Icons.Outlined.Shield to Lavender
    TimelineType.ALLOWED -> Icons.Outlined.NotificationsActive to LowRisk
    TimelineType.REVIEW -> Icons.Outlined.HelpOutline to UnknownRisk
    TimelineType.OPENED -> Icons.Outlined.TouchApp to HighRisk
    TimelineType.ENERGY -> Icons.Outlined.BatteryChargingFull to Lavender
    else -> Icons.Outlined.Circle to NavyFaint
}
