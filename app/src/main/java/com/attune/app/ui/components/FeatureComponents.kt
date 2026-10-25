package com.attune.app.ui.components
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.attune.app.ui.theme.*
import com.attune.core.model.EnergyCheckIn
import com.attune.core.model.EnergyLevel
import com.attune.core.model.QueuedNotification

/**
 * Returns a function that asks for POST_NOTIFICATIONS (Android 13+) once, then runs [action] whatever the answer.
 * The permission only affects Attune's own timer notification; focus tracking works without it.
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

@OptIn(ExperimentalLayoutApi::class)
@Composable fun EnergyCheckInCard(latest: EnergyCheckIn?, suggestion: String?, onCheckIn: (EnergyLevel) -> Unit) {
    AttuneCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.BatteryChargingFull, null, tint = Lavender, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Energy check-in", style = MaterialTheme.typography.titleMedium, color = Navy, modifier = Modifier.weight(1f))
            Text("Optional", style = MaterialTheme.typography.labelMedium, color = NavyFaint)
        }
        Spacer(Modifier.height(4.dp))
        Text(latest?.let { "Last noted: ${label(it.level)} at ${clockTime(it.timestampMs)}" } ?: "How's your energy right now? This is self-reported and stays on your device.",
            style = MaterialTheme.typography.bodySmall, color = NavySoft)
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EnergyLevel.entries.forEach { l -> FilterChip(selected = latest?.level == l && System.currentTimeMillis() - latest.timestampMs < 3_600_000L, onClick = { onCheckIn(l) }, label = { Text(label(l)) }) }
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
fun label(l: EnergyLevel) = when (l) { EnergyLevel.LOW -> "Low"; EnergyLevel.MODERATE -> "Moderate"; EnergyLevel.HIGH -> "High" }

@Composable fun NotificationRow(n: QueuedNotification, onClick: () -> Unit, compact: Boolean = false) {
    Surface(onClick = onClick, color = androidx.compose.ui.graphics.Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
            AppIcon(n.packageName, n.appLabel.ifBlank { n.packageName.substringAfterLast('.') }, if (compact) 36.dp else 42.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(n.appLabel.ifBlank { n.packageName.substringAfterLast('.') }, style = MaterialTheme.typography.labelMedium, color = Lavender, modifier = Modifier.weight(1f, false))
                    Spacer(Modifier.width(6.dp))
                    Text("· ${relativeTime(maxOf(n.postTimeMs, n.lastUpdatedMs))}", style = MaterialTheme.typography.labelMedium, color = NavyFaint)
                }
                Text(n.title ?: "(No title)", style = MaterialTheme.typography.titleMedium, color = Navy, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(n.previewText ?: "No preview shared by the app", style = MaterialTheme.typography.bodySmall, color = if (n.previewText == null) NavyFaint else NavySoft, maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis)
                if (!compact) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusPill(n.status)
                        if (n.groupCount > 1) Pill("${n.groupCount} grouped", NavySoft)
                        if (n.isDemo) DemoBadge()
                        if (n.sourceRemoved) Pill("Withdrawn by app", NavyFaint)
                    }
                }
            }
            if (compact && n.isDemo) DemoBadge()
        }
    }
}
