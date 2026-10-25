package com.attune.app.ui.queue
import androidx.compose.foundation.layout.*
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

@OptIn(ExperimentalLayoutApi::class)
@Composable fun QueueScreen(onOpenPrediction: (String) -> Unit, onSetupAccess: () -> Unit, onOpenFocus: () -> Unit, vm: QueueViewModel = hiltViewModel()) {
    val s by vm.state.collectAsState()
    val tab by vm.tab.collectAsState()
    if (s.loading) { LoadingState(); return }
    val inFocus = s.session?.state == SessionState.FOCUS
    ScreenColumn {
        ScreenHeader("Smart queue", "Notification queue", when {
            inFocus -> "You're focusing. New notifications wait here."
            s.session != null -> "Break time — a good moment to review what's waiting."
            else -> "Review what arrived while you focused." })

        when (s.access) {
            AccessStatus.NOT_GRANTED -> InfoBanner(Icons.Outlined.NotificationsOff, "Real notifications aren't being queued",
                "Allow notification access to queue real notifications during focus. Demo samples work without it.", BannerTone.WARNING, "Set up access", onSetupAccess)
            AccessStatus.GRANTED_WAITING -> InfoBanner(Icons.Outlined.HourglassTop, "Waiting for Android to connect",
                "Access is allowed but the listener hasn't connected yet. Reopening Attune usually fixes this.", BannerTone.INFO, "Troubleshoot", onSetupAccess)
            AccessStatus.CONNECTED -> if (s.session == null) InfoBanner(Icons.Outlined.Info, "Queueing happens during focus",
                "Outside a focus session, notifications reach you normally.", BannerTone.INFO, "Start focus", onOpenFocus)
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(tab == QueueTab.PENDING, { vm.tab.value = QueueTab.PENDING }, { Text("Pending · ${s.pending.size}") })
            FilterChip(tab == QueueTab.DELAYED, { vm.tab.value = QueueTab.DELAYED }, { Text("Delayed · ${s.delayed.size}") })
            FilterChip(tab == QueueTab.HISTORY, { vm.tab.value = QueueTab.HISTORY }, { Text("Reviewed") })
        }

        val list = when (tab) { QueueTab.PENDING -> s.pending; QueueTab.DELAYED -> s.delayed; QueueTab.HISTORY -> s.history }
        if (tab == QueueTab.DELAYED && list.isNotEmpty()) Text(
            if (inFocus) "These wait until your break. Attune keeps them here; the original Android notification may not reappear."
            else "Break time: review these now, or leave them for later.", style = MaterialTheme.typography.bodySmall, color = NavySoft)

        if (list.isEmpty()) AttuneCard {
            when (tab) {
                QueueTab.PENDING -> EmptyState(Icons.Outlined.Inbox, "All clear", if (s.demoEnabled) "Nothing pending. Add a labelled demo notification to try the flow." else "Nothing pending. Notifications queued during focus appear here.",
                    if (s.demoEnabled) "Add demo notification" else null, if (s.demoEnabled) vm::addDemo else null)
                QueueTab.DELAYED -> EmptyState(Icons.Outlined.Schedule, "Nothing delayed", "Use “Delay until break” on a notification to save it for later.")
                QueueTab.HISTORY -> EmptyState(Icons.Outlined.History, "No reviewed notifications yet", "Opened, dismissed and filtered notifications are listed here.")
            }
        } else AttuneCard {
            list.forEachIndexed { i, n ->
                if (i > 0) HorizontalDivider(color = CardBorder)
                NotificationRow(n, { onOpenPrediction(n.id) })
            }
        }
        if (s.demoEnabled && list.isNotEmpty() && tab == QueueTab.PENDING) TextButton(onClick = vm::addDemo) { Icon(Icons.Outlined.AddAlert, null); Spacer(Modifier.width(6.dp)); Text("Add demo notification") }
        val q = Quotes.forBreak(); InspirationQuote(q.text, q.author)
    }
}
