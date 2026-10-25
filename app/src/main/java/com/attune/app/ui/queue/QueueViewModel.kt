package com.attune.app.ui.queue
import androidx.lifecycle.ViewModel; import androidx.lifecycle.viewModelScope
import com.attune.app.data.*
import com.attune.core.model.*
import com.attune.prediction.decision.Routing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*; import kotlinx.coroutines.launch
import javax.inject.Inject

enum class QueueTab { PENDING, REVIEW, DELAYED, HISTORY }
data class QueueUiState(
    val loading: Boolean = true, val pending: List<QueuedNotification> = emptyList(), val review: List<QueuedNotification> = emptyList(),
    val delayed: List<QueuedNotification> = emptyList(), val history: List<QueuedNotification> = emptyList(), val session: FocusSession? = null,
    val access: AccessStatus = AccessStatus.NOT_GRANTED, val demoEnabled: Boolean = false, val suppress: Boolean = true, val hideSensitive: Boolean = true
)

@HiltViewModel class QueueViewModel @Inject constructor(
    queue: QueueRepository, sessions: FocusSessionRepository, settings: SettingsRepository, access: NotificationAccessMonitor,
    private val demo: DemoDataRepository
) : ViewModel() {
    val tab = MutableStateFlow(QueueTab.PENDING)
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4); val messages: SharedFlow<String> = _messages
    val state: StateFlow<QueueUiState> = combine(queue.items, sessions.active, access.status, settings.settings) { items, s, ac, st ->
        val pending = items.filter { it.status == NotificationStatus.PENDING }.sortedByDescending { maxOf(it.postTimeMs, it.lastUpdatedMs) }
        QueueUiState(false, pending.filter { !it.keptVisible }, pending.filter { it.keptVisible },
            items.filter { it.status == NotificationStatus.DELAYED }.sortedByDescending { it.statusChangedMs ?: it.postTimeMs },
            items.filter { !it.status.isOpen }.sortedByDescending { it.statusChangedMs ?: it.postTimeMs }.take(60),
            s, ac, st.demoEnabled, st.suppressOriginals, st.hideSensitivePreviews)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), QueueUiState())

    fun addDemo() { viewModelScope.launch {
        val r = demo.injectLiveSample()
        when (r.routing) {
            Routing.DELIVER -> _messages.emit("Let through (would reach you now): ${r.reason}")
            Routing.REVIEW -> { tab.value = QueueTab.REVIEW; _messages.emit("Kept visible for review: ${r.reason}") }
            else -> { tab.value = QueueTab.PENDING; _messages.emit("Held: ${r.reason}") }
        }
    } }
}
