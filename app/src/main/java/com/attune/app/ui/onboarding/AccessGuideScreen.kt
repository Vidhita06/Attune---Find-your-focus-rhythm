package com.attune.app.ui.onboarding
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.attune.app.data.AccessStatus
import com.attune.app.data.NotificationAccessMonitor
import com.attune.app.ui.components.*
import com.attune.app.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel class AccessViewModel @Inject constructor(val monitor: NotificationAccessMonitor) : ViewModel()

@Composable fun AccessGuideScreen(onBack: () -> Unit, vm: AccessViewModel = hiltViewModel()) {
    val status by vm.monitor.status.collectAsState()
    val snackbar = LocalSnackbar.current
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(failed) { if (failed) { snackbar.showSnackbar("Couldn't open settings. Open Settings › Notifications › Device & app notifications manually."); failed = false } }
    ScreenColumn {
        DetailTopBar("Notification access", onBack)
        when (status) {
            AccessStatus.CONNECTED -> InfoBanner(Icons.Outlined.CheckCircle, "Access allowed and connected", "Attune will queue eligible notifications during focus sessions.")
            AccessStatus.GRANTED_WAITING -> InfoBanner(Icons.Outlined.HourglassTop, "Allowed, waiting for Android", "The listener hasn't connected yet. Try closing and reopening Attune, or toggling access off and on.", BannerTone.INFO)
            AccessStatus.NOT_GRANTED -> InfoBanner(Icons.Outlined.NotificationsOff, "Access not allowed", "Without access Attune can't see notifications. Focus sessions, Insights and demo samples still work.", BannerTone.WARNING)
        }
        AttuneCard {
            Text("Set up", style = MaterialTheme.typography.titleMedium, color = Navy)
            Step(1, "Tap the button below to open Android's notification access page.")
            Step(2, "Find Attune and switch it on. Android will show a warning — this access lets Attune read notifications on your device.")
            Step(3, "If the switch is greyed out (Android 13+ for apps installed outside the Play Store): open App info › ⋮ menu › “Allow restricted settings”, then try again.")
            Step(4, "Come back to Attune. The status above updates automatically.")
            Spacer(Modifier.height(8.dp))
            Button(onClick = { if (!vm.monitor.openSettings()) failed = true }, Modifier.fillMaxWidth()) { Text("Open notification access settings") }
            TextButton(onClick = { if (!vm.monitor.openAppDetails()) failed = true }) { Text("Open Attune's App info") }
        }
        AttuneCard {
            Text("What Attune can and can't do", style = MaterialTheme.typography.titleMedium, color = Navy)
            Limit("Only notifications posted while a focus session is running (not paused) are queued.")
            Limit("Some apps don't share text in their notifications; those show “No preview”.")
            Limit("Ongoing notifications (music, navigation, calls in progress) are never hidden.")
            Limit("Hiding a notification removes it from the shade. Attune cannot restore it there — you review it in Attune's queue instead.")
            Limit("“Open now” uses the notification's own link while Attune is running. After a restart, or if the app withdrew it, Attune can only open the app's main screen.")
            Limit("Some phone makers stop background services to save battery. If queueing stops, exempt Attune from battery optimisation.")
            Limit("Categories and urgency are inferred from app and Android metadata, which varies by app. Your rules decide the rest.")
        }
    }
}
@Composable private fun Step(n: Int, text: String) { Row(Modifier.padding(vertical = 4.dp)) { Pill("$n", Lavender); Spacer(Modifier.width(8.dp)); Text(text, style = MaterialTheme.typography.bodySmall, color = NavySoft) } }
@Composable private fun Limit(text: String) { Row(Modifier.padding(vertical = 3.dp)) { Text("•", color = Lavender); Spacer(Modifier.width(8.dp)); Text(text, style = MaterialTheme.typography.bodySmall, color = NavySoft) } }
