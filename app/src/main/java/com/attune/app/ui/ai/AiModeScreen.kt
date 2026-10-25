package com.attune.app.ui.ai
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.attune.app.data.*
import com.attune.app.ui.components.*
import com.attune.app.ui.profile.SwitchRow
import com.attune.app.ui.theme.*
import com.attune.prediction.decision.NotificationDecision
import com.attune.prediction.decision.Routing
import com.attune.prediction.model.SessionPhase
import com.attune.prediction.policy.IncomingNotification
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import kotlin.math.roundToInt

data class TryApp(val pkg: String, val label: String)
val TRY_APPS = listOf(TryApp("com.whatsapp", "WhatsApp"), TryApp("com.instagram.android", "Instagram"), TryApp("com.google.android.gm", "Gmail"),
    TryApp("com.slack", "Slack"), TryApp("com.google.android.apps.messaging", "Messages"), TryApp("com.amazon.mShop.android.shopping", "Amazon"))

@HiltViewModel class AiModeViewModel @Inject constructor(private val settings: SettingsRepository, private val models: ModelManager, private val decisions: DecisionRepository) : ViewModel() {
    val aiMode = settings.settings.map { it.aiMode }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val status = MutableStateFlow<ModelStatus?>(null)
    val evaluation = MutableStateFlow<JSONObject?>(null)
    val latencyUs = MutableStateFlow<Double?>(null)
    val result = MutableStateFlow<NotificationDecision?>(null)
    init { viewModelScope.launch(Dispatchers.Default) {
        status.value = models.status
        evaluation.value = models.evaluationJson()?.let { runCatching { JSONObject(it) }.getOrNull() }
    } }
    fun setAiMode(v: Boolean) = viewModelScope.launch { settings.setFlag(Flag.AI_MODE, v) }
    fun measure() = viewModelScope.launch { latencyUs.value = withContext(Dispatchers.Default) { models.measureLatencyMicros() } }
    /** Runs the real pipeline on typed text, as if it arrived during focus. Nothing is stored. */
    fun tryIt(app: TryApp, title: String, text: String) = viewModelScope.launch {
        result.value = withContext(Dispatchers.Default) {
            decisions.evaluate(IncomingNotification(app.pkg, title.ifBlank { null }, text.ifBlank { null }, null, false, false, true), SessionPhase.FOCUS)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun AiModeScreen(onBack: () -> Unit, onOpenRules: () -> Unit, vm: AiModeViewModel = hiltViewModel()) {
    val on by vm.aiMode.collectAsState(); val st by vm.status.collectAsState(); val ev by vm.evaluation.collectAsState()
    val lat by vm.latencyUs.collectAsState(); val res by vm.result.collectAsState()
    ScreenColumn {
        DetailTopBar("AI Mode", onBack)
        AttuneCard(container = LavenderMist) {
            SwitchRow("AI Mode", "When on, an on-device model reads each notification during focus and decides whether it can wait. When off, your category rules decide.", on, vm::setAiMode)
            val s = st
            when {
                s == null -> LoadingState()
                s.loaded -> { KeyValueRow("Model", s.version ?: "—"); KeyValueRow("Status", "Loaded on this device"); KeyValueRow("Size", "${s.sizeBytes / 1024} KB · ${s.vocabulary} features")
                    KeyValueRow("Load time", "${s.loadMs} ms"); KeyValueRow("Inference", lat?.let { "%.0f µs median (measured here)".format(it) } ?: "not measured yet")
                    TextButton(onClick = vm::measure, contentPadding = PaddingValues(0.dp)) { Text("Measure inference speed on this device") } }
                else -> InfoBanner(Icons.Outlined.ErrorOutline, "Model unavailable", "Attune is using category rules instead (${s.error}). Decisions say so on each notification.", BannerTone.WARNING)
            }
        }

        SectionTitle("How a decision is made")
        AttuneCard {
            listOf(
                Icons.Outlined.Rule to "1. Your rules first — allowed apps, priority contacts, keywords and categories you chose. Allow beats hold when rules conflict.",
                Icons.Outlined.Shield to "2. Safety — one-time codes, account-security alerts, emergencies and repeated missed calls always come through.",
                Icons.Outlined.AutoAwesome to "3. The model — separate scores for importance, urgency, distraction and whether action is needed. Sender alone never decides.",
                Icons.Outlined.Person to "4. Your feedback — small, capped adjustments per app, type and sender. Reviewable and resettable.",
                Icons.Outlined.HelpOutline to "5. When unsure — short, link-only or possibly important messages stay visible and are listed for review, never silently held.",
            ).forEach { (i, t) -> Row(Modifier.padding(vertical = 4.dp)) { Icon(i, null, tint = Lavender, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(10.dp)); Text(t, style = MaterialTheme.typography.bodySmall, color = NavySoft) } }
            TextButton(onClick = onOpenRules, contentPadding = PaddingValues(0.dp)) { Text("Edit rules & exceptions") }
        }

        SectionTitle("Try it")
        AttuneCard {
            var app by remember { mutableStateOf(TRY_APPS.first()) }
            var title by remember { mutableStateOf("Mom") }
            var text by remember { mutableStateOf("call me right now, it's urgent") }
            Text("Runs the real pipeline as if this arrived during focus. Nothing is stored or sent anywhere.", style = MaterialTheme.typography.bodySmall, color = NavySoft)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { TRY_APPS.forEach { a -> FilterChip(app == a, { app = a }, { Text(a.label) }) } }
            OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("Title / sender") }, singleLine = true)
            OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), label = { Text("Message") }, minLines = 2)
            Spacer(Modifier.height(8.dp))
            Button(onClick = { vm.tryIt(app, title, text) }, shape = RoundedCornerShape(14.dp)) { Icon(Icons.Outlined.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Evaluate") }
            res?.let { d ->
                Spacer(Modifier.height(10.dp)); HorizontalDivider(color = CardBorder); Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(when (d.routing) { Routing.DELIVER -> "Let through"; Routing.DEFER -> "Held until break"; Routing.REVIEW -> "Kept visible for review"; Routing.FILTER -> "Filtered"; Routing.IGNORE -> "Ignored" },
                        style = MaterialTheme.typography.titleMedium, color = Navy, modifier = Modifier.weight(1f))
                    FocusRiskChip(d.risk.name)
                }
                Text("Decided by: ${d.source.label}", style = MaterialTheme.typography.labelMedium, color = NavyFaint)
                d.signals?.let { s -> Text("Importance ${s.importance.name.lowercase()} (${(s.pHigh * 100).roundToInt()}% high) · urgent ${(s.urgent * 100).roundToInt()}% · distracting ${(s.distracting * 100).roundToInt()}% · action ${(s.actionRequired * 100).roundToInt()}% · confidence ${(s.confidence * 100).roundToInt()}%",
                    style = MaterialTheme.typography.bodySmall, color = NavySoft) }
                d.reasons.forEach { Text("• ${it.text}", style = MaterialTheme.typography.bodySmall, color = NavySoft) }
            }
        }

        SectionTitle("Evaluation report")
        val e = ev
        if (e == null) AttuneCard { Text("The evaluation file isn't bundled with this build.", style = MaterialTheme.typography.bodySmall, color = NavySoft) }
        else EvaluationCards(e, lat, st)

        SectionTitle("What AI Mode is — and isn't")
        AttuneCard {
            listOf("Implemented: an on-device text classifier (logistic regression over word and character features), trained by ml/train.py on a synthetic, template-generated dataset.",
                "Implemented: feedback-based personalization — small, capped adjustments. The model itself is not retrained on your phone.",
                "Not implemented: an on-device large language model, or any cloud AI. No notification text or link ever leaves your device.",
                "A future optional cloud feature would require explicit consent and a clear explanation of what is sent.",
                "Android limits: Attune sees notifications after they're posted. Removing one is best-effort, and Attune can't restore another app's notification.")
                .forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = NavySoft, modifier = Modifier.padding(vertical = 2.dp)) }
        }
    }
}

@Composable private fun EvaluationCards(e: JSONObject, lat: Double?, st: ModelStatus?) {
    fun num(o: JSONObject?, k: String) = o?.optDouble(k)?.takeIf { !it.isNaN() }
    fun p(v: Double?) = v?.let { "${(it * 100).roundToInt()}%" } ?: "—"
    InfoBanner(Icons.Outlined.Science, "Synthetic test data", e.optString("caveat"), BannerTone.DEMO)
    AttuneCard {
        val sizes = e.optJSONObject("dataset_sizes")
        Text("Dataset", style = MaterialTheme.typography.titleMedium, color = Navy)
        KeyValueRow("Rows (train / validation / test)", "${sizes?.optInt("train")} / ${sizes?.optInt("validation")} / ${sizes?.optInt("test")}")
        KeyValueRow("Templates (split by template)", "${e.optInt("templates")}")
        KeyValueRow("Model file", "${e.optLong("model_size_bytes") / 1024} KB")
        KeyValueRow("Inference on this device", lat?.let { "%.0f µs".format(it) } ?: "tap “Measure” above")
        KeyValueRow("Generated", e.optString("generated_at"))
    }
    val routing = e.optJSONObject("routing")
    val tp = routing?.optJSONObject("trained_pipeline"); val v1 = routing?.optJSONObject("v1_rule_policy_baseline")
    AttuneCard {
        Text("Decisions: trained pipeline vs old rules", style = MaterialTheme.typography.titleMedium, color = Navy)
        Text("On held-out test templates. Lower is better for the first two rows.", style = MaterialTheme.typography.bodySmall, color = NavySoft)
        Spacer(Modifier.height(6.dp))
        Row { Text("", Modifier.weight(1.6f)); Text("AI", Modifier.weight(0.7f), style = MaterialTheme.typography.labelLarge, color = Navy); Text("Rules", Modifier.weight(0.7f), style = MaterialTheme.typography.labelLarge, color = Navy) }
        listOf("false_deferral_rate_needs_attention" to "Important/urgent wrongly held", "ambiguous_silently_deferred_rate" to "Ambiguous silently held",
            "important_or_urgent_not_deferred_recall" to "Important/urgent reached you", "distractions_deferred_recall" to "Distractions held", "accuracy" to "Routing accuracy")
            .forEach { (k, label) -> Row(Modifier.padding(vertical = 3.dp)) {
                Text(label, Modifier.weight(1.6f), style = MaterialTheme.typography.bodySmall, color = NavySoft)
                Text(p(num(tp, k)), Modifier.weight(0.7f), style = MaterialTheme.typography.labelLarge, color = Navy)
                Text(p(num(v1, k)), Modifier.weight(0.7f), style = MaterialTheme.typography.labelLarge, color = NavySoft) } }
    }
    val heads = e.optJSONObject("heads")
    AttuneCard {
        Text("Signal heads (test split)", style = MaterialTheme.typography.titleMedium, color = Navy)
        Row { Text("", Modifier.weight(1.2f)); listOf("Acc.", "Macro-F1", "Baseline F1").forEach { Text(it, Modifier.weight(0.8f), style = MaterialTheme.typography.labelMedium, color = Navy) } }
        listOf("imp" to "Importance", "urg" to "Urgency", "dis" to "Distraction", "act" to "Action").forEach { (k, label) ->
            val h = heads?.optJSONObject(k); val m = h?.optJSONObject("model")
            Row(Modifier.padding(vertical = 3.dp)) {
                Text(label, Modifier.weight(1.2f), style = MaterialTheme.typography.bodySmall, color = NavySoft)
                Text(p(num(m, "accuracy")), Modifier.weight(0.8f), style = MaterialTheme.typography.labelLarge, color = Navy)
                Text(p(num(m, "macro_f1")), Modifier.weight(0.8f), style = MaterialTheme.typography.labelLarge, color = Navy)
                Text(p(num(h?.optJSONObject("majority_baseline"), "macro_f1")), Modifier.weight(0.8f), style = MaterialTheme.typography.labelLarge, color = NavySoft)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Importance per class", style = MaterialTheme.typography.labelLarge, color = Navy)
        val pc = heads?.optJSONObject("imp")?.optJSONObject("model")?.optJSONObject("per_class")
        Row { Text("", Modifier.weight(1.2f)); listOf("Precision", "Recall", "F1").forEach { Text(it, Modifier.weight(0.8f), style = MaterialTheme.typography.labelMedium, color = Navy) } }
        listOf("low", "medium", "high").forEach { c -> val o = pc?.optJSONObject(c)
            Row(Modifier.padding(vertical = 2.dp)) { Text(c, Modifier.weight(1.2f), style = MaterialTheme.typography.bodySmall, color = NavySoft)
                listOf("precision", "recall", "f1-score").forEach { Text(p(num(o, it)), Modifier.weight(0.8f), style = MaterialTheme.typography.labelLarge, color = Navy) } } }
        Spacer(Modifier.height(6.dp))
        val cal = heads?.optJSONObject("imp")?.optJSONObject("calibration")
        Text("Calibration (importance): expected calibration error ${num(cal, "ece_before")?.let { "%.3f".format(it) }} → ${num(cal, "ece_after")?.let { "%.3f".format(it) }} after temperature scaling (T = ${cal?.optDouble("temperature")}).",
            style = MaterialTheme.typography.labelMedium, color = NavyFaint)
        Text("Keyword baseline importance macro-F1: ${p(num(heads?.optJSONObject("imp")?.optJSONObject("keyword_baseline"), "macro_f1"))}.", style = MaterialTheme.typography.labelMedium, color = NavyFaint)
    }
}
