package com.attune.app.ui.prediction
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.attune.app.domain.LaunchResults
import com.attune.app.ui.components.*
import com.attune.app.ui.theme.*
import com.attune.core.model.ActionType
import com.attune.core.model.NotificationDecisionRecord
import com.attune.core.model.NotificationStatus
import com.attune.prediction.PredictionEngine
import com.attune.prediction.decision.DecisionSource
import com.attune.prediction.decision.FeedbackKind
import com.attune.prediction.decision.ReasonKind
import com.attune.prediction.model.FactorSource
import com.attune.prediction.model.SessionPhase
import kotlin.math.roundToInt

private fun f(v: Float) = PredictionEngine.fmt(v)
private fun pct(v: Float?) = v?.let { "${(it * 100).roundToInt()}%" } ?: "—"

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable fun PredictionScreen(notificationId: String, onBack: () -> Unit, onFeedback: (String) -> Unit, vm: PredictionViewModel = hiltViewModel()) {
    val s by vm.state.collectAsState()
    val snackbar = LocalSnackbar.current
    var showSheet by remember { mutableStateOf(false) }
    var reveal by remember { mutableStateOf(false) }
    var showCost by remember { mutableStateOf(false) }
    LaunchedEffect(notificationId) { vm.load(notificationId) }
    LaunchedEffect(Unit) { vm.events.collect { e -> when (e) {
        is PredictionEvent.Message -> snackbar.showSnackbar(e.text)
        is PredictionEvent.OpenedGoFeedback -> onFeedback(e.id)
        PredictionEvent.Back -> onBack()
    } } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        DetailTopBar("Before you open", onBack)
        val n = s.notif
        if (s.notFound) { EmptyState(Icons.Outlined.SearchOff, "Not found", "This notification is no longer in the queue.", "Back to queue", onBack); return@Column }
        if (s.loading || n == null) { LoadingState(); return@Column }
        val masked = n.isSensitive && s.hideSensitive && !reveal

        // 1. The notification
        AttuneCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(n.packageName, n.appLabel, 44.dp); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(n.appLabel.ifBlank { n.packageName }, style = MaterialTheme.typography.labelLarge, color = Lavender)
                    Text("Received ${relativeTime(n.postTimeMs)}${if (n.groupCount > 1) " · ${n.groupCount} grouped" else ""}", style = MaterialTheme.typography.labelMedium, color = NavyFaint)
                }
                StatusPill(n.status)
            }
            Spacer(Modifier.height(10.dp))
            Text(if (masked) "Sensitive notification" else safeTitle(n, false), style = MaterialTheme.typography.titleLarge, color = Navy)
            Text(if (masked) "Hidden because it looks like a code, a payment or a security alert." else safePreview(n, false), style = MaterialTheme.typography.bodyMedium,
                color = if (n.previewText == null || masked) NavyFaint else NavySoft)
            if (masked) TextButton(onClick = { reveal = true }, contentPadding = PaddingValues(0.dp)) { Icon(Icons.Outlined.Visibility, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Show content") }
            if (n.isDemo || n.sourceRemoved || n.keptVisible) { Spacer(Modifier.height(8.dp)); FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (n.isDemo) DemoBadge("DEMO SAMPLE"); if (n.sourceRemoved) Pill("Withdrawn by app", NavyFaint); if (n.keptVisible) Pill("Original left visible", UnknownRisk) } }
        }

        // 2. The decision and why
        val ai = s.ai
        if (ai != null) DecisionCard(ai, s.feedbackGiven.isNotEmpty()) else InfoBanner(Icons.Outlined.Info, "No AI decision recorded",
            "This item was queued before AI decisions were stored (or by an older version). Category rules applied.")

        // 3. Actions
        if (n.status.isOpen) {
            if (s.failedAttempts > 0) InfoBanner(Icons.Outlined.ErrorOutline, "Last open attempt failed", "You can try again, delay it, or dismiss it.", BannerTone.WARNING)
            Button(onClick = vm::openNow, enabled = !s.inFlight, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)) {
                if (s.inFlight) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else { Icon(Icons.AutoMirrored.Filled.OpenInNew, null); Spacer(Modifier.width(8.dp)); Text("Open now") }
            }
            Text(if (n.isDemo) "Demo sample: opens the app if it's installed; otherwise it's only marked as opened."
                else if (s.hasOriginalTarget) "Opens the notification's own target when Android allows it. Links are never opened by Attune itself." else "The original target isn't available (e.g. after a restart); Attune will open the app instead.",
                style = MaterialTheme.typography.labelMedium, color = NavyFaint)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = vm::delay, enabled = !s.inFlight && n.status != NotificationStatus.DELAYED, modifier = Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Outlined.Schedule, null); Spacer(Modifier.width(6.dp)); Text(if (n.status == NotificationStatus.DELAYED) "Delayed" else "Delay") }
                OutlinedButton(onClick = vm::dismiss, enabled = !s.inFlight, modifier = Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Outlined.Close, null); Spacer(Modifier.width(6.dp)); Text("Dismiss") }
            }
        } else {
            val d = s.decision
            InfoBanner(Icons.Outlined.CheckCircle, when (n.status) { NotificationStatus.OPENED, NotificationStatus.REVIEWED -> "You opened this"; NotificationStatus.DISMISSED -> "You dismissed this"; else -> "Filtered by your settings" },
                d?.let { "Recorded at ${clockTime(it.actionTimeMs)}" + when (it.launchResult) {
                    LaunchResults.CONTENT_INTENT_SENT -> " · original notification target sent to Android"; LaunchResults.APP_LAUNCHED -> " · app launched"
                    LaunchResults.DEMO_NO_APP -> " · demo sample, nothing launched"; else -> "" } } ?: (n.ruleApplied ?: ""))
            if (d?.actionType == ActionType.OPENED && LaunchResults.succeeded(d.launchResult)) TextButton(onClick = { onFeedback(n.id) }) { Text("Report how long it took") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilledTonalButton(onClick = { vm.feedback(FeedbackKind.ACTUALLY_IMPORTANT) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Outlined.StarOutline, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Mark important") }
            FilledTonalButton(onClick = { showSheet = true }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Outlined.RateReview, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Correct it") }
        }

        // 4. Time-cost estimate (transparent baseline, separate from the AI decision)
        val e = s.estimate
        Surface(onClick = { showCost = !showCost }, shape = RoundedCornerShape(16.dp), color = LavenderMist, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.HourglassBottom, null, tint = Lavender, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Estimated time cost if you open it", style = MaterialTheme.typography.labelLarge, color = Navy)
                    Text(e?.let { "≈${f(it.timeAway)} min away + ≈${f(it.recovery)} min to refocus · baseline estimate" } ?: "Calculating…", style = MaterialTheme.typography.bodySmall, color = NavySoft)
                }
                Icon(if (showCost) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, tint = NavyFaint)
            }
        }
        AnimatedVisibility(showCost && e != null) { if (e != null) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (n.isDemo) DemoBadge("DEMO ESTIMATE") else Pill("BASELINE ESTIMATE", Lavender)
                RiskChip(e.risk)
                Pill(if (e.coldStart) "Cold start · ${e.observations} of 3 reports" else "Confidence: ${e.confidenceLabel} · ${e.observations} reports", NavySoft)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.height(IntrinsicSize.Min)) {
                MetricBlock("Time away", "≈${f(e.timeAway)} min", "Range ${f(e.tLow)}–${f(e.tHigh)} min in the other app", Modifier.weight(1f).fillMaxHeight())
                MetricBlock("Recovery", "≈${f(e.recovery)} min", "Range ${f(e.rLow)}–${f(e.rHigh)} min to get back into your task", Modifier.weight(1f).fillMaxHeight())
            }
            AttuneCard {
                Text(e.explanation, style = MaterialTheme.typography.bodySmall, color = NavySoft)
                e.factors.forEach { fv -> Row(Modifier.padding(vertical = 3.dp)) {
                    Icon(when (fv.source) { FactorSource.YOUR_DATA -> Icons.Outlined.Person; FactorSource.CONTEXT -> Icons.Outlined.Schedule; FactorSource.STARTING_ASSUMPTION -> Icons.Outlined.Tune },
                        null, tint = Lavender, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(8.dp))
                    Column { Text(fv.label, style = MaterialTheme.typography.labelLarge, color = Navy); Text(fv.detail, style = MaterialTheme.typography.bodySmall, color = NavySoft) } } }
                Spacer(Modifier.height(6.dp))
                val cf = s.counterfactual
                Text("What if I opened it immediately?", style = MaterialTheme.typography.labelLarge, color = Navy)
                if (cf != null) {
                    Text("Observed: ${cf.observed}", style = MaterialTheme.typography.bodySmall, color = NavySoft)
                    Text("Estimated alternative: ${cf.alternative}", style = MaterialTheme.typography.bodySmall, color = NavySoft)
                    Text(cf.uncertainty, style = MaterialTheme.typography.labelMedium, color = NavyFaint)
                } else {
                    Text(if (s.phase == SessionPhase.FOCUS) "Opening now: ≈${f(e.timeAway + e.recovery)} min of focus disrupted. Delaying: ≈0 min now." else "No session running, so no focus time is at stake.",
                        style = MaterialTheme.typography.bodySmall, color = NavySoft)
                    Text("Estimates from a transparent rule-based baseline (${e.modelVersion}), not the AI classifier. Not validated.", style = MaterialTheme.typography.labelMedium, color = NavyFaint)
                }
            }
        } }
        Text("AI decisions are predictions, not guarantees. Explicit rules and safety checks always come first.", style = MaterialTheme.typography.labelMedium, color = NavyFaint)
    }

    if (showSheet) ModalBottomSheet(onDismissRequest = { showSheet = false }, containerColor = WarmWhite) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text("Correct this prediction", style = MaterialTheme.typography.titleLarge, color = Navy)
            Text("Feedback adjusts future decisions a little at a time (it does not retrain the model). “Always” options create a rule you can remove later.",
                style = MaterialTheme.typography.bodySmall, color = NavySoft)
            Spacer(Modifier.height(8.dp))
            val n = s.notif
            val options = buildList {
                add(FeedbackKind.CORRECT to Icons.Outlined.ThumbUp)
                add(FeedbackKind.WRONGLY_DEFERRED to Icons.Outlined.NotificationImportant)
                add(FeedbackKind.ACTUALLY_IMPORTANT to Icons.Outlined.Star)
                add(FeedbackKind.NOT_IMPORTANT to Icons.Outlined.ThumbDown)
                if (s.decision?.actionType == ActionType.OPENED) add(FeedbackKind.OPENED_IMMEDIATELY to Icons.AutoMirrored.Outlined.OpenInNew)
                if (n?.status == NotificationStatus.DELAYED || n?.status == NotificationStatus.DISMISSED) add(FeedbackKind.DEFERRED_FINE to Icons.Outlined.Schedule)
                add(FeedbackKind.ALWAYS_ALLOW_APP to Icons.Outlined.Apps)
                if (n?.title != null && n.category == "messaging") add(FeedbackKind.ALWAYS_ALLOW_CONTACT to Icons.Outlined.Person)
                add(FeedbackKind.ALWAYS_ALLOW_CATEGORY to Icons.Outlined.Category)
                add(FeedbackKind.ALWAYS_DEFER_CATEGORY to Icons.Outlined.DoNotDisturbOn)
            }
            options.forEach { (k, icon) ->
                Surface(onClick = { showSheet = false; vm.feedback(k) }, color = WarmWhite, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(vertical = 10.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, null, tint = Lavender); Spacer(Modifier.width(14.dp))
                        Column { Text(k.label, style = MaterialTheme.typography.labelLarge, color = Navy)
                            Text(feedbackHelp(k), style = MaterialTheme.typography.bodySmall, color = NavySoft) }
                    }
                }
            }
        }
    }
}

private fun feedbackHelp(k: FeedbackKind) = when (k) {
    FeedbackKind.CORRECT -> "Recorded for accuracy stats only."
    FeedbackKind.WRONGLY_DEFERRED -> "Raises importance for this app, type and sender a little."
    FeedbackKind.ACTUALLY_IMPORTANT -> "Raises importance for this app, type and sender a little."
    FeedbackKind.NOT_IMPORTANT -> "Lowers importance for this app, type and sender a little."
    FeedbackKind.OPENED_IMMEDIATELY -> "A weak signal that it mattered."
    FeedbackKind.DEFERRED_FINE -> "A weak signal that waiting was fine."
    FeedbackKind.ALWAYS_ALLOW_APP -> "Creates an explicit rule: this app always reaches you."
    FeedbackKind.ALWAYS_ALLOW_CONTACT -> "Adds this title as a priority contact."
    FeedbackKind.ALWAYS_ALLOW_CATEGORY -> "Creates an explicit rule for this category."
    FeedbackKind.ALWAYS_DEFER_CATEGORY -> "Holds this category during focus; urgent or security content still gets through."
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun DecisionCard(v: DecisionView, hasFeedback: Boolean) {
    val r = v.record
    val source = runCatching { DecisionSource.valueOf(r.source) }.getOrNull()
    AttuneCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Recommendation", style = MaterialTheme.typography.labelMedium, color = NavyFaint)
                Text(when (r.routing) { "DELIVER" -> "Let through"; "DEFER" -> "Hold until your break"; "REVIEW" -> "Kept visible — your call"; "FILTER" -> "Filtered"; else -> r.routing },
                    style = MaterialTheme.typography.titleLarge, color = Navy)
            }
            FocusRiskChip(r.focusRisk, large = true)
        }
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Pill("Decided by: ${source?.label ?: r.source}", LavenderDeep)
            r.modelVersion?.let { Pill(it, NavyFaint) }
            if (r.isDemo) DemoBadge("DEMO")
            if (hasFeedback) Pill("Feedback given", LowRisk)
        }
        if (r.overrodeModel) { Spacer(Modifier.height(8.dp)); InfoBanner(Icons.Outlined.Gavel, "A rule overrode the AI",
            "On its own, the model would have chosen: ${when (r.modelRouting) { "DELIVER" -> "let through"; "DEFER" -> "hold"; "REVIEW" -> "keep visible for review"; else -> r.modelRouting?.lowercase() ?: "—" }}.") }

        if (r.pImportant != null) {
            Spacer(Modifier.height(12.dp))
            Text("Signals", style = MaterialTheme.typography.titleMedium, color = Navy)
            Text("Separate scores from the on-device model; they are not combined into one number.", style = MaterialTheme.typography.bodySmall, color = NavyFaint)
            Spacer(Modifier.height(6.dp))
            SignalRow(Icons.Outlined.Star, "Importance", r.importance?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "—", r.pImportant, "chance it's high importance")
            SignalRow(Icons.Outlined.Alarm, "Urgency", if ((r.pUrgent ?: 0f) >= 0.5f) "Time-sensitive" else "Can wait", r.pUrgent, "chance it's urgent")
            SignalRow(Icons.Outlined.PhoneAndroid, "Distraction", if ((r.pDistracting ?: 0f) >= 0.5f) "Likely" else "Unlikely", r.pDistracting, "chance it pulls you away")
            SignalRow(Icons.Outlined.TaskAlt, "Action needed", if ((r.pAction ?: 0f) >= 0.5f) "Probably" else "Probably not", r.pAction, "chance it asks you to act")
            SignalRow(Icons.Outlined.Link, "Link", when (r.linkSafety) { "NONE" -> "No link"; "CAUTION" -> "Caution"; else -> "Can't verify" }, null,
                when (r.linkSafety) { "NONE" -> ""; "CAUTION" -> "local warning signs found — not opened"; else -> "unknown is not the same as safe" })
            Spacer(Modifier.height(4.dp))
            val c = r.confidence ?: 0f
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Model confidence", style = MaterialTheme.typography.labelLarge, color = Navy, modifier = Modifier.weight(1f))
                Text("${when { c >= 0.8f -> "Fairly sure"; c >= 0.6f -> "Somewhat sure"; else -> "Unsure" }} · ${pct(c)}", style = MaterialTheme.typography.labelLarge, color = Navy)
            }
            LinearProgressIndicator(progress = { c }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), color = Lavender, trackColor = LavenderSoft)
            Text("Calibrated on synthetic test data; a guide, never a guarantee.", style = MaterialTheme.typography.labelMedium, color = NavyFaint)
            if ((r.personalBias ?: 0f) != 0f) Text("Includes your feedback: importance shifted ${if ((r.personalBias ?: 0f) > 0) "up" else "down"} by ${"%.2f".format(kotlin.math.abs(r.personalBias ?: 0f))} (log-odds).",
                style = MaterialTheme.typography.labelMedium, color = NavySoft)
        }

        Spacer(Modifier.height(12.dp))
        Text("Why", style = MaterialTheme.typography.titleMedium, color = Navy)
        v.reasons.forEach { reason -> Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
            Icon(reasonIcon(reason.kind), null, tint = Lavender, modifier = Modifier.size(16.dp).padding(top = 2.dp)); Spacer(Modifier.width(8.dp))
            Text(reason.text, style = MaterialTheme.typography.bodySmall, color = NavySoft)
        } }
        if (v.terms.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text("Words that pointed towards “important”", style = MaterialTheme.typography.labelMedium, color = NavyFaint)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { v.terms.forEach { Pill(it, Lavender) } }
        }
    }
}

private fun reasonIcon(k: ReasonKind): ImageVector = when (k) {
    ReasonKind.RULE -> Icons.Outlined.Rule; ReasonKind.SAFETY -> Icons.Outlined.Shield; ReasonKind.MODEL -> Icons.Outlined.AutoAwesome
    ReasonKind.PERSONAL -> Icons.Outlined.Person; ReasonKind.CONTEXT -> Icons.Outlined.Schedule; ReasonKind.LINK -> Icons.Outlined.Link
    ReasonKind.FALLBACK -> Icons.Outlined.HelpOutline
}

@Composable private fun SignalRow(icon: ImageVector, label: String, value: String, p: Float?, sub: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Lavender, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = Navy)
            if (sub.isNotEmpty()) Text(if (p != null) "${pct(p)} $sub" else sub, style = MaterialTheme.typography.labelMedium, color = NavyFaint)
        }
        Text(value, style = MaterialTheme.typography.labelLarge, color = Navy)
    }
}
