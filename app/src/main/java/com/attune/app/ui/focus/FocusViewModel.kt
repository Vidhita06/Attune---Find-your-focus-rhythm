package com.attune.app.ui.focus
import androidx.lifecycle.ViewModel; import androidx.lifecycle.viewModelScope
import com.attune.app.data.*
import com.attune.app.domain.BreakSuggester
import com.attune.app.domain.TimerMath
import com.attune.app.ui.tickerFlow
import com.attune.core.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*; import kotlinx.coroutines.launch
import javax.inject.Inject

data class FocusUiState(
    val loading: Boolean = true,
    val active: FocusSession? = null, val remainingMs: Long = 0, val focusedMs: Long = 0, val progress: Float = 0f,
    val lastFinished: FocusSession? = null, val lastFinishedQueued: Int = 0,
    val queuedThisSession: Int = 0, val defaultFocusMin: Int = 25, val breakMin: Int = 5,
    val demoEnabled: Boolean = false, val energyEnabled: Boolean = true, val latestCheckIn: EnergyCheckIn? = null, val suggestion: String? = null,
    val busy: Boolean = false
)

@HiltViewModel class FocusViewModel @Inject constructor(
    private val sessions: FocusSessionRepository, queue: QueueRepository, private val settings: SettingsRepository,
    private val energy: EnergyRepository, private val demo: DemoDataRepository
) : ViewModel() {
    private val busy = MutableStateFlow(false)
    private val events = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = events

    val state: StateFlow<FocusUiState> = combine(
        combine(sessions.all, queue.items, settings.settings, energy.all) { a, i, s, e -> arrayOf(a, i, s, e) },
        tickerFlow(1_000), busy
    ) { arr, now, b ->
        @Suppress("UNCHECKED_CAST") val all = (arr[0] as List<FocusSession>).filter { !it.isDemo }
        @Suppress("UNCHECKED_CAST") val items = arr[1] as List<QueuedNotification>
        val s = arr[2] as AttuneSettings
        @Suppress("UNCHECKED_CAST") val latest = (arr[3] as List<EnergyCheckIn>).firstOrNull { !it.isDemo }
        val active = all.firstOrNull { it.state.isActive }
        val last = all.filter { it.state.isFinished }.maxByOrNull { it.actualEndMs ?: 0 }
        FocusUiState(false, active, active?.let { TimerMath.remainingMs(it, now) } ?: 0, active?.let { TimerMath.focusedMs(it, now) } ?: 0,
            active?.let { TimerMath.progress(it, now) } ?: 0f, last, last?.let { l -> items.count { it.sessionId == l.id } } ?: 0,
            active?.let { a -> items.count { it.sessionId == a.id } } ?: 0, s.defaultFocusMin, s.breakMin, s.demoEnabled, s.energyCheckInEnabled, latest,
            if (s.energyCheckInEnabled) BreakSuggester.suggest(latest, active, now) else null, b)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FocusUiState())

    private fun run(block: suspend () -> Unit) { if (busy.value) return; busy.value = true; viewModelScope.launch { try { block() } finally { busy.value = false } } }
    fun start(min: Int) = run { sessions.start(min) }
    fun pause() = run { sessions.pause() }
    fun takeBreak() = run { sessions.pause(asBreak = true) }
    fun resume() = run { sessions.resume() }
    fun end() = run {
        val s = sessions.end()
        events.emit(when (s?.state) { SessionState.COMPLETED -> "Session completed and saved."; SessionState.ENDED_EARLY -> "Session saved as ended early."; else -> "No active session." })
    }
    fun sendDemoNotification() = run { demo.injectLiveSample(); events.emit("A demo notification was added to your queue.") }
    fun checkIn(level: EnergyLevel) = viewModelScope.launch { energy.checkIn(level) }
}
