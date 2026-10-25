package com.attune.app.ui.components
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.attune.app.ui.theme.*
import com.attune.core.model.NotificationStatus
import com.attune.core.model.RiskLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val LocalSnackbar = staticCompositionLocalOf<SnackbarHostState> { error("No SnackbarHostState") }

@Composable fun ScreenColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
}

@Composable fun ScreenHeader(eyebrow: String?, title: String, subtitle: String? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            if (eyebrow != null) Text(eyebrow.uppercase(), style = MaterialTheme.typography.labelSmall, color = Lavender)
            Text(title, style = MaterialTheme.typography.headlineLarge, color = Navy)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = NavySoft)
        }
        trailing?.invoke()
    }
}

@Composable fun DetailTopBar(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Navy) }
        Text(title, style = MaterialTheme.typography.titleLarge, color = Navy)
    }
}

@Composable fun AttuneCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, container: Color = Color.White, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(20.dp); val border = BorderStroke(1.dp, CardBorder)
    val colors = CardDefaults.cardColors(containerColor = container)
    if (onClick != null) Card(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = shape, border = border, colors = colors, elevation = CardDefaults.cardElevation(0.dp)) { Column(Modifier.padding(16.dp), content = content) }
    else Card(modifier = modifier.fillMaxWidth(), shape = shape, border = border, colors = colors, elevation = CardDefaults.cardElevation(0.dp)) { Column(Modifier.padding(16.dp), content = content) }
}

@Composable fun SectionTitle(text: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = Navy, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 8.dp)) { Text(action) }
    }
}

@Composable fun StatTile(value: String, label: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, CardBorder), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(0.dp)) {
        Column(Modifier.padding(14.dp)) {
            Icon(icon, null, tint = Lavender, modifier = Modifier.size(18.dp))
            Spacer(Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, color = Navy, maxLines = 1)
            Text(label, style = MaterialTheme.typography.bodySmall, color = NavySoft, maxLines = 2)
        }
    }
}

@Composable fun Pill(text: String, color: Color, background: Color = color.copy(alpha = 0.12f), modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = background, shape = RoundedCornerShape(50)) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
    }
}

@Composable fun DemoBadge(text: String = "DEMO", modifier: Modifier = Modifier) = Pill(text, DemoAmber, DemoAmberBg, modifier)

@Composable fun StatusPill(status: NotificationStatus) {
    val (label, color) = when (status) {
        NotificationStatus.PENDING -> "Pending" to Lavender
        NotificationStatus.DELAYED -> "Delayed to break" to MedRisk
        NotificationStatus.OPENED, NotificationStatus.REVIEWED -> "Opened" to LowRisk
        NotificationStatus.DISMISSED -> "Dismissed" to NavyFaint
        NotificationStatus.FILTERED -> "Filtered" to NavyFaint
    }
    Pill(label, color)
}

fun riskColor(r: RiskLevel) = when (r) { RiskLevel.HIGH -> HighRisk; RiskLevel.MED -> MedRisk; RiskLevel.LOW -> LowRisk }
fun riskLabel(r: RiskLevel) = when (r) { RiskLevel.HIGH -> "High"; RiskLevel.MED -> "Medium"; RiskLevel.LOW -> "Low" }

@Composable fun RiskChip(risk: RiskLevel, modifier: Modifier = Modifier) = Pill("${riskLabel(risk)} time cost", riskColor(risk), modifier = modifier)

/** Focus risk from the decision engine: LOW / MEDIUM / HIGH / UNKNOWN. Always shown with an icon and a word, never colour alone. */
fun focusRiskColor(r: String?) = when (r) { "HIGH" -> HighRisk; "MEDIUM" -> MedRisk; "LOW" -> LowRisk; else -> UnknownRisk }
fun focusRiskLabel(r: String?) = when (r) { "HIGH" -> "High risk"; "MEDIUM" -> "Medium risk"; "LOW" -> "Low risk"; else -> "Unknown" }
fun focusRiskIcon(r: String?): ImageVector = when (r) {
    "HIGH" -> androidx.compose.material.icons.Icons.Outlined.Warning; "MEDIUM" -> androidx.compose.material.icons.Icons.Outlined.ErrorOutline
    "LOW" -> androidx.compose.material.icons.Icons.Outlined.CheckCircle; else -> androidx.compose.material.icons.Icons.Outlined.HelpOutline }

@Composable fun FocusRiskChip(risk: String?, modifier: Modifier = Modifier, large: Boolean = false) {
    val c = focusRiskColor(risk)
    Surface(modifier = modifier, color = c.copy(alpha = 0.12f), shape = RoundedCornerShape(50)) {
        Row(Modifier.padding(horizontal = if (large) 12.dp else 8.dp, vertical = if (large) 6.dp else 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(focusRiskIcon(risk), null, tint = c, modifier = Modifier.size(if (large) 16.dp else 12.dp)); Spacer(Modifier.width(4.dp))
            Text(focusRiskLabel(risk), style = if (large) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelSmall, color = c, maxLines = 1)
        }
    }
}

@Composable fun EmptyState(icon: ImageVector, title: String, body: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(LavenderSoft), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Lavender) }
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = Navy, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = NavySoft, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) { Spacer(Modifier.height(12.dp)); FilledTonalButton(onClick = onAction) { Text(actionLabel) } }
    }
}

enum class BannerTone { INFO, WARNING, DEMO }
@Composable fun InfoBanner(icon: ImageVector, title: String, body: String, tone: BannerTone = BannerTone.INFO, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    val (bg, fg) = when (tone) { BannerTone.INFO -> LavenderMist to Lavender; BannerTone.WARNING -> Color(0xFFFFF1EE) to HighRisk; BannerTone.DEMO -> DemoAmberBg to DemoAmber }
    Surface(color = bg, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.labelLarge, color = Navy)
                Text(body, style = MaterialTheme.typography.bodySmall, color = NavySoft)
                if (actionLabel != null && onAction != null) TextButton(onClick = onAction, contentPadding = PaddingValues(0.dp)) { Text(actionLabel, color = fg) }
            }
        }
    }
}

@Composable fun InspirationQuote(text: String, author: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("“$text”", style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic), color = NavySoft, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text("— $author", style = MaterialTheme.typography.labelMedium, color = NavyFaint, textAlign = TextAlign.Center)
    }
}

@Composable fun AppIcon(packageName: String, label: String, size: Dp = 40.dp) {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, packageName) {
        value = withContext(Dispatchers.IO) { runCatching { ctx.packageManager.getApplicationIcon(packageName).toBitmap(96, 96).asImageBitmap() }.getOrNull() }
    }
    val b = bmp
    if (b != null) Image(b, null, Modifier.size(size).clip(RoundedCornerShape(size / 4)))
    else Box(Modifier.size(size).clip(RoundedCornerShape(size / 4)).background(LavenderSoft), contentAlignment = Alignment.Center) {
        Text(label.take(1).uppercase().ifBlank { "?" }, style = MaterialTheme.typography.titleMedium, color = LavenderDeep)
    }
}

@Composable fun MetricBlock(label: String, value: String, sub: String, modifier: Modifier = Modifier) {
    Surface(modifier, color = LavenderMist, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = NavySoft)
            Text(value, style = MaterialTheme.typography.headlineMedium, color = Navy)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = NavySoft, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable fun LoadingState() { Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Lavender) } }

@Composable fun KeyValueRow(key: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(key, style = MaterialTheme.typography.bodyMedium, color = NavySoft, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelLarge, color = Navy, textAlign = TextAlign.End)
    }
}

fun relativeTime(ms: Long, now: Long = System.currentTimeMillis()): String {
    val d = (now - ms).coerceAtLeast(0) / 60_000
    return when { d < 1 -> "just now"; d < 60 -> "$d min ago"; d < 24 * 60 -> "${d / 60} h ago"
        else -> java.text.SimpleDateFormat("EEE d MMM, h:mm a", java.util.Locale.getDefault()).format(java.util.Date(ms)) }
}
fun clockTime(ms: Long): String = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(java.util.Date(ms))
