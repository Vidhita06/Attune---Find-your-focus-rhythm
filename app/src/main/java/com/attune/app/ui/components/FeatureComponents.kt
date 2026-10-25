package com.attune.app.ui.components
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.attune.app.ui.theme.*
import com.attune.core.model.EnergyCheckIn
import com.attune.core.model.QueuedNotification

/**
 * Returns a function that asks for POST_NOTIFICATIONS (Android 13+) once, then runs [action] whatever the answer.
 * The permission only affects Attune's own notifications; focus tracking works without it.
 */
@Composable fun rememberWithNotificationPermission(): ((() -> Unit) -> Unit) {
    val ctx = LocalContext.current
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { pending?.invoke(); pending = null }
    return { action ->
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            pending = action; launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else action()
    }
}

val ENERGY_WORDS = listOf("Drained", "Low", "Okay", "Good", "Energised")
fun energyWord(score: Int) = ENERGY_WORDS[(score - 1).coerceIn(0, 4)]

/** Optional 1–5 self-reported energy. [phaseLabel] says whether it's before/after a session. Not a health measure. */
@Composable fun EnergyCheckInCard(latest: EnergyCheckIn?, suggestion: String?, onCheckIn: (Int) -> Unit, phaseLabel: String? = null) {
    AttuneCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.BatteryChargingFull, null, tint = Lavender, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(phaseLabel ?: "Energy check-in", style = MaterialTheme.typography.titleMedium, color = Navy, modifier = Modifier.weight(1f))
            Text("Optional", style = MaterialTheme.typography.labelMedium, color = NavyFaint)
        }
        Spacer(Modifier.height(4.dp))
        Text(latest?.let { "Last noted: ${it.score}/5 · ${energyWord(it.score)} at ${clockTime(it.timestampMs)}" } ?: "How's your energy, 1 to 5? Self-reported and stored only on this device.",
            style = MaterialTheme.typography.bodySmall, color = NavySoft)
        Spacer(Modifier.height(10.dp))
        val recent = latest?.takeIf { System.currentTimeMillis() - it.timestampMs < 3_600_000L }?.score
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            (1..5).forEach { v ->
                FilterChip(selected = recent == v, onClick = { onCheckIn(v) }, label = { Text("$v", Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center) },
                    modifier = Modifier.weight(1f).semantics { contentDescription = "Energy $v of 5, ${energyWord(v)}" })
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Text("Drained", style = MaterialTheme.typography.labelSmall, color = NavyFaint, modifier = Modifier.weight(1f))
            Text("Energised", style = MaterialTheme.typography.labelSmall, color = NavyFaint)
        }
        if (suggestion != null) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Outlined.Lightbulb, null, tint = MedRisk, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                Text(suggestion, style = MaterialTheme.typography.bodySmall, color = Navy)
            }
        }
    }
}

/** Text shown for a queue item, respecting privacy: sensitive previews are masked, scrubbed items say so. */
fun safeTitle(n: QueuedNotification, hideSensitive: Boolean) = when {
    n.textScrubbed -> "Content removed after 24 h"
    n.isSensitive && hideSensitive -> "Sensitive notification"
    else -> n.title ?: "(No title)"
}
fun safePreview(n: QueuedNotification, hideSensitive: Boolean) = when {
    n.textScrubbed -> "Attune keeps text only until a day after you handle it."
    n.isSensitive && hideSensitive -> "Preview hidden (code, money or security alert). Open it to view."
    else -> n.previewText ?: "No preview shared by the app"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun NotificationRow(n: QueuedNotification, onClick: () -> Unit, compact: Boolean = false, hideSensitive: Boolean = true) {
    Surface(onClick = onClick, color = androidx.compose.ui.graphics.Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
            AppIcon(n.packageName, n.appLabel.ifBlank { n.packageName.substringAfterLast('.') }, if (compact) 36.dp else 42.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(n.appLabel.ifBlank { n.packageName.substringAfterLast('.') }, style = MaterialTheme.typography.labelMedium, color = Lavender, modifier = Modifier.weight(1f, false))
                    Spacer(Modifier.width(6.dp))
                    Text("· ${relativeTime(maxOf(n.postTimeMs, n.lastUpdatedMs))}", style = MaterialTheme.typography.labelMedium, color = NavyFaint)
                    if (compact) { Spacer(Modifier.weight(1f)); FocusRiskChip(n.focusRisk) }
                }
                Text(safeTitle(n, hideSensitive), style = MaterialTheme.typography.titleMedium, color = Navy, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(safePreview(n, hideSensitive), style = MaterialTheme.typography.bodySmall, color = if (n.previewText == null || n.textScrubbed) NavyFaint else NavySoft,
                    maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis)
                if (!compact) {
                    n.ruleApplied?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = NavyFaint, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp)) }
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        FocusRiskChip(n.focusRisk)
                        StatusPill(n.status)
                        if (n.keptVisible) Pill("Kept visible · review", UnknownRisk)
                        if (n.groupCount > 1) Pill("${n.groupCount} grouped", NavySoft)
                        if (n.isDemo) DemoBadge()
                        if (n.sourceRemoved) Pill("Withdrawn by app", NavyFaint)
                    }
                }
            }
            if (compact && n.isDemo) { Spacer(Modifier.width(4.dp)); DemoBadge() }
        }
    }
}
