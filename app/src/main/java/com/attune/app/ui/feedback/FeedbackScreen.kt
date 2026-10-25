package com.attune.app.ui.feedback
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.attune.app.ui.components.*
import com.attune.app.ui.theme.*
import com.attune.prediction.PredictionEngine
import kotlin.math.roundToInt

@Composable fun FeedbackScreen(notificationId: String, onDone: () -> Unit, vm: FeedbackViewModel = hiltViewModel()) {
    LaunchedEffect(notificationId) { vm.load(notificationId) }
    val s by vm.state.collectAsState()
    var t by remember { mutableFloatStateOf(5f) }; var r by remember { mutableFloatStateOf(2f) }
    var touched by remember { mutableStateOf(false) }
    LaunchedEffect(s.measuredMin) { if (!touched) s.measuredMin?.let { t = it.coerceIn(0f, 60f).roundToInt().toFloat() } }

    ScreenColumn {
        DetailTopBar("Reflection", onDone)
        ScreenHeader(null, "How did that go?", "Optional. Your answers stay on this device and let the baseline blend in your own history.")
        if (s.alreadySubmitted) InfoBanner(Icons.Outlined.Timer, "Already recorded", "You've answered for this notification. Saving again adds another report.")
        s.measuredMin?.let { InfoBanner(Icons.Outlined.Timer, "Measured: ≈${PredictionEngine.fmt(it)} min until you returned",
            "Time from Open Now until Attune was back on screen. It's an upper bound — you may have done other things too.") }
        AttuneCard {
            Text("Time away (minutes)", style = MaterialTheme.typography.titleMedium, color = Navy)
            s.predictedMin?.let { Text("Estimated beforehand: ≈${PredictionEngine.fmt(it)} min", style = MaterialTheme.typography.bodySmall, color = NavySoft) }
            Slider(t, { t = it; touched = true }, valueRange = 0f..60f, steps = 59)
            Text("${t.roundToInt()} min", style = MaterialTheme.typography.titleLarge, color = Lavender)
        }
        AttuneCard {
            Text("Time to get back on task (minutes)", style = MaterialTheme.typography.titleMedium, color = Navy)
            Text("Recovery is self-reported; Attune can't observe it.", style = MaterialTheme.typography.bodySmall, color = NavySoft)
            Slider(r, { r = it }, valueRange = 0f..30f, steps = 29)
            Text("${r.roundToInt()} min", style = MaterialTheme.typography.titleLarge, color = Lavender)
        }
        Button(onClick = { vm.submit(t.roundToInt().toFloat(), r.roundToInt().toFloat(), onDone) }, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) { Text("Save & continue") }
        TextButton(onClick = { vm.submit(null, null, onDone) }, Modifier.fillMaxWidth()) { Text("Skip", color = NavySoft) }
    }
}
