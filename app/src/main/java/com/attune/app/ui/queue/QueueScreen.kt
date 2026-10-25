package com.attune.app.ui.queue
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.attune.app.data.AccessStatus
import com.attune.app.ui.components.*
import com.attune.app.ui.theme.*
import com.attune.app.util.Quotes
import com.attune.core.model.SessionState

@Composable fun QueueScreen(onOpenPrediction: (String) -> Unit, onSetupAccess: () -> Unit, onOpenFocus: () -> Unit, vm: QueueViewModel = hiltViewModel()) {
    val s by vm.state.collectAsState()
    val tab by vm.tab.collectAsState()
    val snackbar = LocalSnackbar.current
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    if (s.loading) { LoadingState(); return }
    val inFocus = s.session?.state == SessionState.FOCUS
    ScreenColumn {
        ScreenHeader("Smart queue", "Notification queue", when {
            inFocus -> "You're focusing. Likely distractions wait here."
            s.session != null -> "Break time — a good moment to review what's waiting."
            else -> "Review what arrived while you focused." })

        when (s.access) {
            AccessStatus.NOT_GRANTED -> InfoBanner(Icons.Outlined.NotificationsOff, "Real notifications aren't being handled",
                "Allow notification access so Attune can hold distractions during focus. Demo samples work without it.", BannerTone.WARNING, "Set up access", onSetupAccess)
            AccessStatus.GRANTED_WAITING -> InfoBanner(Icons.Outlined.HourglassTop, "Waiting for Android to connect",
                "Access is allowed but the listener hasn't connected yet. Reopening Attune usually fixes this.", BannerTone.INFO, "Troubleshoot", onSetupAccess)
            AccessStatus.CONNECTED -> if (s.session == null) InfoBanner(Icons.Outlined.Info, "Holding happens during focus",
                "Outside a focus session, notifications reach you normally.", BannerTone.INFO, "Start focus", onOpenFocus)
        }

        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(tab == QueueTab.PENDING, { vm.tab.value = QueueTab.PENDING }, { Text("Held · ${s.pending.size}") })
            FilterChip(tab == QueueTab.REVIEW, { vm.tab.value = QueueTab.REVIEW }, { Text("To review · ${s.review.size}") })
            FilterChip(tab == QueueTab.DELAYED, { vm.tab.value = QueueTab.DELAYED }, { Text("Delayed · ${s.delayed.size}") })
            FilterChip(tab == QueueTab.HISTORY, { vm.tab.value = QueueTab.HISTORY }, { Text("Handled") })
        }

        val list = when (tab) { QueueTab.PENDING -> s.pending; QueueTab.REVIEW -> s.review; QueueTab.DELAYED -> s.delayed; QueueTab.HISTORY -> s.history }
        when (tab) {
            QueueTab.REVIEW -> if (list.isNotEmpty()) Text("Attune wasn't sure about these, or they might be important, so the originals were left in your notification shade. Tell Attune what they were to improve future decisions.",
                style = MaterialTheme.typography.bodySmall, color = NavySoft)
            QueueTab.DELAYED -> if (list.isNotEmpty()) Text(if (inFocus) "These wait until your break. The original Android notification may not reappear." else "Break time: review these now, or leave them for later.",
                style = MaterialTheme.typography.bodySmall, color = NavySoft)
            QueueTab.PENDING -> if (list.isNotEmpty() && s.suppress) Text("Held items were removed from your notification shade where Android allowed it. Open one to see why it was held.",
                style = MaterialTheme.typography.bodySmall, color = NavySoft)
            else -> {}
        }

        if (list.isEmpty()) AttuneCard {
            when (tab) {
                QueueTab.PENDING -> EmptyState(Icons.Outlined.Inbox, "All clear", if (s.demoEnabled) "Nothing held. Send a labelled demo notification through the real AI pipeline to try it." else "Nothing held. Likely distractions that arrive during focus appear here.",
                    if (s.demoEnabled) "Send demo notification" else null, if (s.demoEnabled) vm::addDemo else null)
                QueueTab.REVIEW -> EmptyState(Icons.Outlined.HelpOutline, "Nothing to review", "Messages Attune isn't sure about stay visible and are listed here.")
                QueueTab.DELAYED -> EmptyState(Icons.Outlined.Schedule, "Nothing delayed", "Use “Delay until break” on a notification to save it for later.")
                QueueTab.HISTORY -> EmptyState(Icons.Outlined.History, "Nothing handled yet", "Opened, dismissed and filtered notifications are listed here.")
            }
        } else AttuneCard {
            list.forEachIndexed { i, n ->
                if (i > 0) HorizontalDivider(color = CardBorder)
                NotificationRow(n, { onOpenPrediction(n.id) }, hideSensitive = s.hideSensitive)
            }
        }
        if (s.demoEnabled && list.isNotEmpty() && tab != QueueTab.HISTORY) TextButton(onClick = vm::addDemo) { Icon(Icons.Outlined.AddAlert, null); Spacer(Modifier.width(6.dp)); Text("Send demo notification") }
        val q = Quotes.forBreak(); InspirationQuote(q.text, q.author)
    }
}
