package com.attune.app.ui.prediction
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.attune.app.domain.LaunchResults
import com.attune.app.ui.components.*
import com.attune.app.ui.theme.*
import com.attune.app.util.Quotes
import com.attune.core.model.ActionType
import com.attune.core.model.NotificationStatus
import com.attune.prediction.PredictionEngine
import com.attune.prediction.model.FactorSource
import com.attune.prediction.model.SessionPhase

private fun f(v: Float) = PredictionEngine.fmt(v)

@OptIn(ExperimentalLayoutApi::class)
@Composable fun PredictionScreen(notificationId: String, onBack: () -> Unit, onFeedback: (String) -> Unit, vm: PredictionViewModel = hiltViewModel()) {
    val s by vm.state.collectAsState()
    val snackbar = LocalSnackbar.current
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
            Text(n.title ?: "(No title)", style = MaterialTheme.typography.titleLarge, color = Navy)
            Text(n.previewText ?: "No preview shared by the app.", style = MaterialTheme.typography.bodyMedium, color = if (n.previewText == null) NavyFaint else NavySoft)
            if (n.isDemo || n.sourceRemoved) { Spacer(Modifier.height(8.dp)); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (n.isDemo) DemoBadge("DEMO SAMPLE"); if (n.sourceRemoved) Pill("Withdrawn by app", NavyFaint) } }
        }

        val e = s.estimate
        if (e == null) LoadingState() else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (n.isDemo) DemoBadge("DEMO ESTIMATE") else Pill("BASELINE ESTIMATE", Lavender)
                RiskChip(e.risk)
                Pill(if (e.coldStart) "Cold start · ${e.observations} of 3 reports" else "Confidence: ${e.confidenceLabel} · ${e.observations} reports", NavySoft)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.height(IntrinsicSize.Min)) {
                MetricBlock("Time away", "≈${f(e.timeAway)} min", "Range ${f(e.tLow)}–${f(e.tHigh)} min in the other app", Modifier.weight(1f).fillMaxHeight())
                MetricBlock("Recovery", "≈${f(e.recovery)} min", "Range ${f(e.rLow)}–${f(e.rHigh)} min to get back into your task", Modifier.weight(1f).fillMaxHeight())
            }
            AttuneCard {
                Text("Why this estimate", style = MaterialTheme.typography.titleMedium, color = Navy)
                Spacer(Modifier.height(4.dp))
                Text(e.explanation, style = MaterialTheme.typography.bodyMedium, color = NavySoft)
                if (e.factors.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    e.factors.forEach { fv -> Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                        Icon(when (fv.source) { FactorSource.YOUR_DATA -> Icons.Outlined.Person; FactorSource.CONTEXT -> Icons.Outlined.Schedule; FactorSource.STARTING_ASSUMPTION -> Icons.Outlined.Tune },
                            null, tint = Lavender, modifier = Modifier.size(16.dp).padding(top = 2.dp))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(fv.label, style = MaterialTheme.typography.labelLarge, color = Navy)
                            Text(fv.detail, style = MaterialTheme.typography.bodySmall, color = NavySoft)
                        }
                        Text(when (fv.source) { FactorSource.YOUR_DATA -> "Your data"; FactorSource.CONTEXT -> "Context"; FactorSource.STARTING_ASSUMPTION -> "Assumption" },
                            style = MaterialTheme.typography.labelSmall, color = NavyFaint)
                    } }
                }
                if (e.storedAtDecision) { Spacer(Modifier.height(6.dp)); Text("Showing the estimate recorded when you decided.", style = MaterialTheme.typography.labelMedium, color = NavyFaint) }
            }

            // Counterfactual: prospective before a decision, retrospective after.
            AttuneCard(container = LavenderMist) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Outlined.AltRoute, null, tint = Lavender, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                    Text("What if I opened it immediately?", style = MaterialTheme.typography.titleMedium, color = Navy)
                }
                Spacer(Modifier.height(6.dp))
                val cf = s.counterfactual
                if (cf != null) {
                    Text("Observed", style = MaterialTheme.typography.labelLarge, color = Navy); Text(cf.observed, style = MaterialTheme.typography.bodySmall, color = NavySoft)
                    Spacer(Modifier.height(6.dp))
                    Text("Estimated alternative", style = MaterialTheme.typography.labelLarge, color = Navy); Text(cf.alternative, style = MaterialTheme.typography.bodySmall, color = NavySoft)
                    cf.estimatedMinutesProtected?.takeIf { it > 0 }?.let { Spacer(Modifier.height(6.dp)); Pill("≈${f(it)} min of focus estimated protected", LowRisk) }
                    Spacer(Modifier.height(6.dp)); Text(cf.uncertainty, style = MaterialTheme.typography.labelMedium, color = NavyFaint)
                } else {
                    if (s.phase == SessionPhase.FOCUS) {
                        KeyValueRow("Open now", "≈${f(e.timeAway + e.recovery)} min of focus disrupted")
                        KeyValueRow("Delay until break", "≈0 min now, ≈${f(e.timeAway)} min later")
                    } else {
                        KeyValueRow("Open now", "≈${f(e.timeAway)} min away")
                        KeyValueRow("Focus at stake", "None — no session running")
                    }
                    Text(e.delayScenario, style = MaterialTheme.typography.bodySmall, color = NavySoft)
                    Spacer(Modifier.height(4.dp))
                    Text("Estimates, not facts. ${if (e.coldStart) "Low confidence until you've reported a few outcomes." else ""}", style = MaterialTheme.typography.labelMedium, color = NavyFaint)
                }
            }
        }

        val q = Quotes.forNotification(n.id); InspirationQuote(q.text, q.author)

        if (n.status.isOpen) {
            if (s.failedAttempts > 0) InfoBanner(Icons.Outlined.ErrorOutline, "Last open attempt failed", "You can try again, delay it, or dismiss it.", BannerTone.WARNING)
            Button(onClick = vm::openNow, enabled = !s.inFlight, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)) {
                if (s.inFlight) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else { Icon(Icons.AutoMirrored.Filled.OpenInNew, null); Spacer(Modifier.width(8.dp)); Text("Open now") }
            }
            Text(if (n.isDemo) "Demo sample: opens the app if it's installed; otherwise it's only marked as opened."
                else if (s.hasOriginalTarget) "Opens the notification's own target when Android allows it." else "The original target isn't available (e.g. after a restart); Attune will open the app instead.",
                style = MaterialTheme.typography.labelMedium, color = NavyFaint)
            OutlinedButton(onClick = vm::delay, enabled = !s.inFlight && n.status != NotificationStatus.DELAYED, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Outlined.Schedule, null); Spacer(Modifier.width(8.dp)); Text(if (n.status == NotificationStatus.DELAYED) "Already delayed until break" else "Delay until break")
            }
            TextButton(onClick = vm::dismiss, enabled = !s.inFlight, modifier = Modifier.fillMaxWidth()) { Text("Dismiss", color = NavySoft) }
        } else {
            val d = s.decision
            InfoBanner(Icons.Outlined.CheckCircle, when (n.status) { NotificationStatus.OPENED, NotificationStatus.REVIEWED -> "You opened this"; NotificationStatus.DISMISSED -> "You dismissed this"; else -> "Filtered by your settings" },
                d?.let { "Recorded at ${clockTime(it.actionTimeMs)}" + when (it.launchResult) {
                    LaunchResults.CONTENT_INTENT_SENT -> " · original notification target sent to Android"; LaunchResults.APP_LAUNCHED -> " · app launched"
                    LaunchResults.DEMO_NO_APP -> " · demo sample, nothing launched"; else -> "" } } ?: (n.ruleApplied ?: ""))
            if (d?.actionType == ActionType.OPENED && LaunchResults.succeeded(d.launchResult)) TextButton(onClick = { onFeedback(n.id) }) { Text("Report how long it took") }
        }
        Text("Attune uses a transparent baseline (${e?.modelVersion ?: "baseline-v1"}), not a trained model. Its accuracy has not been validated.",
            style = MaterialTheme.typography.labelMedium, color = NavyFaint)
    }
}
