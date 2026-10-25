package com.attune.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.attune.app.ui.theme.*
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Categorical series colours, validated for colour-vision deficiency (dataviz validator: all checks pass, light mode). */
val SeriesA = Lavender            // #6B5CA5
val SeriesB = Color(0xFF1A9E8F)   // teal
val SeriesC = Color(0xFFD9822B)   // amber — below 3:1 on white, so values are always printed as text too

data class ChartSeries(val name: String, val color: Color, val values: List<Float>)
data class AxisLabel(val text: String, val show: Boolean)

private fun niceMax(v: Float): Float {
    if (v <= 0f) return 1f
    val steps = listOf(1f, 2f, 5f, 10f, 15f, 20f, 25f, 30f, 50f, 60f, 100f, 120f, 150f, 200f, 300f, 500f, 1000f)
    return steps.firstOrNull { it >= v } ?: (ceil(v / 100f) * 100f)
}

private fun fmt(v: Float) = if (v == v.roundToInt().toFloat()) v.roundToInt().toString() else "%.1f".format(v)

/**
 * Vertical bars (one series) or grouped bars (2–3 series) sharing ONE y-axis. Tap a column to read its exact values.
 * Marks: thin bars, rounded data-ends anchored to the baseline, 2dp gaps, recessive grid.
 */
@Composable fun BarChart(series: List<ChartSeries>, labels: List<AxisLabel>, unit: String, modifier: Modifier = Modifier, height: Dp = 150.dp,
                         description: String = "") {
    val n = labels.size
    var selected by remember(series, labels) { mutableStateOf<Int?>(null) }
    val max = niceMax(series.flatMap { it.values }.maxOrNull() ?: 0f)
    Column(modifier.fillMaxWidth().semantics { contentDescription = description.ifBlank { series.joinToString { s -> "${s.name}: total ${fmt(s.values.sum())} $unit" } } }) {
        if (series.size > 1) Legend(series.map { it.name to it.color })
        Box(Modifier.fillMaxWidth().height(20.dp)) {
            val s = selected
            Text(if (s != null && s < n) "${labels[s].text.ifBlank { "#${s + 1}" }} · " + series.joinToString(" · ") { "${it.name.takeIf { series.size > 1 }?.let { n -> "$n " } ?: ""}${fmt(it.values.getOrElse(s) { 0f })} $unit" }
                 else "Tap a bar for exact values", style = MaterialTheme.typography.labelMedium, color = if (s != null) Navy else NavyFaint)
        }
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.height(height).padding(end = 4.dp), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.End) {
                Text(fmt(max), style = MaterialTheme.typography.labelSmall, color = NavyFaint)
                Text(fmt(max / 2), style = MaterialTheme.typography.labelSmall, color = NavyFaint)
                Text("0", style = MaterialTheme.typography.labelSmall, color = NavyFaint)
            }
            Canvas(Modifier.weight(1f).height(height).pointerInput(n) {
                detectTapGestures { o -> if (n > 0) { val i = (o.x / (size.width / n)).toInt().coerceIn(0, n - 1); selected = if (selected == i) null else i } }
            }) {
                val grid = CardBorder
                listOf(0f, 0.5f, 1f).forEach { f -> val y = size.height * (1 - f) + if (f == 0f) -0.5f else 0f
                    drawLine(grid, Offset(0f, y), Offset(size.width, y), 1.dp.toPx(), pathEffect = if (f == 0f) null else PathEffect.dashPathEffect(floatArrayOf(6f, 6f))) }
                if (n == 0) return@Canvas
                val slot = size.width / n
                val gap = 2.dp.toPx()
                val groupW = (slot * 0.7f).coerceAtMost(28.dp.toPx())
                val barW = ((groupW - gap * (series.size - 1)) / series.size).coerceAtLeast(1.5f)
                for (i in 0 until n) {
                    if (selected == i) drawRect(LavenderMist, Offset(slot * i, 0f), Size(slot, size.height))
                    series.forEachIndexed { k, s ->
                        val v = s.values.getOrElse(i) { 0f }
                        if (v <= 0f) return@forEachIndexed
                        val h = (v / max * size.height).coerceAtLeast(2.dp.toPx())
                        val x = slot * i + (slot - groupW) / 2 + k * (barW + gap)
                        val r = minOf(4.dp.toPx(), barW / 2)
                        val path = Path().apply { addRoundRect(RoundRect(x, size.height - h, x + barW, size.height, topLeftCornerRadius = CornerRadius(r), topRightCornerRadius = CornerRadius(r))) }
                        drawPath(path, s.color)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 28.dp)) {
            labels.forEach { l -> Text(if (l.show) l.text else "", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = NavyFaint, textAlign = TextAlign.Center, maxLines = 1, softWrap = false) }
        }
    }
}

/** Line with dots for sparse measurements (e.g. energy 1–5, completion %). Missing points break the line; fixed y-range. */
@Composable fun LineChart(values: List<Float?>, labels: List<AxisLabel>, yMin: Float, yMax: Float, unit: String, color: Color = SeriesA,
                          modifier: Modifier = Modifier, height: Dp = 120.dp, yLabels: List<String> = listOf(fmt(yMax), fmt((yMin + yMax) / 2), fmt(yMin)), description: String = "") {
    var selected by remember(values) { mutableStateOf<Int?>(null) }
    val n = labels.size
    Column(modifier.fillMaxWidth().semantics { contentDescription = description }) {
        Box(Modifier.fillMaxWidth().height(20.dp)) {
            val s = selected
            Text(if (s != null) "${labels[s].text} · ${values[s]?.let { "${fmt(it)} $unit" } ?: "no data"}" else "Tap a point for its value",
                style = MaterialTheme.typography.labelMedium, color = if (s != null) Navy else NavyFaint)
        }
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.height(height).padding(end = 4.dp), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.End) {
                yLabels.forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = NavyFaint) }
            }
            Canvas(Modifier.weight(1f).height(height).pointerInput(n) {
                detectTapGestures { o -> if (n > 0) { val i = (o.x / (size.width / n)).toInt().coerceIn(0, n - 1); selected = if (selected == i) null else i } }
            }) {
                listOf(0f, 0.5f, 1f).forEach { f -> val y = size.height * (1 - f)
                    drawLine(CardBorder, Offset(0f, y), Offset(size.width, y), 1.dp.toPx(), pathEffect = if (f == 0f) null else PathEffect.dashPathEffect(floatArrayOf(6f, 6f))) }
                if (n == 0) return@Canvas
                val slot = size.width / n
                fun pt(i: Int, v: Float) = Offset(slot * i + slot / 2, size.height * (1 - (v - yMin) / (yMax - yMin)).coerceIn(0f, 1f))
                var prev: Offset? = null
                values.forEachIndexed { i, v ->
                    if (v == null) { prev = null; return@forEachIndexed }
                    val p = pt(i, v); prev?.let { drawLine(color, it, p, 2.dp.toPx()) }; prev = p
                }
                values.forEachIndexed { i, v -> if (v != null) { val p = pt(i, v)
                    drawCircle(Color.White, 5.dp.toPx(), p); drawCircle(color, 4.dp.toPx(), p)
                    if (selected == i) drawCircle(color, 7.dp.toPx(), p, style = Stroke(2.dp.toPx())) } }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 28.dp)) {
            labels.forEach { l -> Text(if (l.show) l.text else "", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = NavyFaint, textAlign = TextAlign.Center, maxLines = 1, softWrap = false) }
        }
    }
}

@Composable fun Legend(items: List<Pair<String, Color>>) {
    Row(Modifier.padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items.forEach { (name, c) -> Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(c)); Spacer(Modifier.width(4.dp))
            Text(name, style = MaterialTheme.typography.labelMedium, color = NavySoft)
        } }
    }
}

data class Segment(val label: String, val count: Int, val color: Color)

/** Part-to-whole as one stacked bar (2dp surface gaps) plus a table of exact counts, so colour is never the only cue. */
@Composable fun StackedShareBar(segments: List<Segment>, modifier: Modifier = Modifier, icons: List<@Composable () -> Unit> = emptyList()) {
    val total = segments.sumOf { it.count }
    Column(modifier.fillMaxWidth().semantics { contentDescription = segments.joinToString { "${it.label} ${it.count}" } }) {
        Row(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            if (total == 0) Box(Modifier.fillMaxSize().background(LavenderMist))
            else segments.filter { it.count > 0 }.forEach { s -> Box(Modifier.weight(s.count.toFloat()).fillMaxHeight().background(s.color)) }
        }
        Spacer(Modifier.height(8.dp))
        segments.forEachIndexed { i, s ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(s.color)); Spacer(Modifier.width(8.dp))
                icons.getOrNull(i)?.invoke()
                Text(s.label, style = MaterialTheme.typography.bodyMedium, color = Navy, modifier = Modifier.weight(1f))
                Text("${s.count}", style = MaterialTheme.typography.labelLarge, color = Navy)
                Text(if (total > 0) "  ${(s.count * 100f / total).roundToInt()}%" else "", style = MaterialTheme.typography.labelMedium, color = NavyFaint, modifier = Modifier.width(44.dp), textAlign = TextAlign.End)
            }
        }
    }
}

/** Ranked horizontal bars with the exact value printed (e.g. app-wise counts). */
@Composable fun RankedBars(items: List<Triple<String, Int, String?>>, color: Color = SeriesA, leading: (@Composable (Int) -> Unit)? = null) {
    val max = (items.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
    items.forEachIndexed { i, (label, v, sub) ->
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            leading?.invoke(i)
            Column(Modifier.weight(1f)) {
                Row { Text(label, style = MaterialTheme.typography.bodyMedium, color = Navy, modifier = Modifier.weight(1f), maxLines = 1)
                    Text("$v", style = MaterialTheme.typography.labelLarge, color = Navy) }
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(LavenderMist)) {
                    Box(Modifier.fillMaxWidth(v.toFloat() / max).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(color))
                }
                if (sub != null) Text(sub, style = MaterialTheme.typography.labelMedium, color = NavyFaint)
            }
        }
    }
}
