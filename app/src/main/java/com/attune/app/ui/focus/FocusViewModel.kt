package com.attune.app.ui.focus
import androidx.lifecycle.ViewModel; import androidx.lifecycle.viewModelScope
import com.attune.app.data.*
import com.attune.app.domain.*
import com.attune.app.ui.tickerFlow
import com.attune.core.model.*
import com.attune.prediction.decision.Routing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*; import kotlinx.coroutines.launch
import javax.inject.Inject

data class FocusUiState(
    val loading: Boolean = true,
    val active: FocusSession? = null, val remainingMs: Long = 0, val focusedMs: Long = 0, val progress: Float = 0f,
    val lastFinished: FocusSession? = null, val lastFinishedQueued: Int = 0,
    val queuedThisSession: Int = 0, val allowedThisSession: Int = 0, val reviewThisSession: Int = 0,
    val defaultFocusMin: Int = 25, val breakMin: Int = 5,
    val demoEnabled: Boolean = false, val energyEnabled: Boolean = true, val latestCheckIn: EnergyCheckIn? = null, val suggestion: String? = null,
    val durationSuggestion: DurationSuggestion? = null, val aiMode: Boolean = true, val modelLoaded: Boolean = true,
    val access: AccessStatus = AccessStatus.NOT_GRANTED, val suppress: Boolean = true, val afterFocus: AfterFocus = AfterFocus.SUMMARY_NOTIFICATION,
    val todayMin: Int = 0, val dailyGoalMin: Int = 0, val busy: Boolean = false
)

@HiltViewModel class FocusViewModel @Inject constructor(
    private val sessions: FocusSessionRepository, queue: QueueRepository, private val settings: SettingsRepository,
    private val energy: EnergyRepository, private val demo: DemoDataRepository, decisions: DecisionRepository, access: NotificationAccessMonitor,
    private val models: ModelManager
) : ViewModel() {
    private val busy = MutableStateFlow(false)
    private val events = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = events

    val state: StateFlow<FocusUiState> = combine(
        combine(sessions.all, queue.items, settings.settings, energy.all, decisions.all) { a, i, s, e, d -> arrayOf<Any>(a, i, s, e, d) },
        tickerFlow(1_000), busy, access.status
    ) { arr, now, b, ac ->
        @Suppress("UNCHECKED_CAST") val all = (arr[0] as List<FocusSession>).filter { !it.isDemo }
        @Suppress("UNCHECKED_CAST") val items = arr[1] as List<QueuedNotification>
        val s = arr[2] as AttuneSettings
        @Suppress("UNCHECKED_CAST") val checkins = (arr[3] as List<EnergyCheckIn>).filter { !it.isDemo }
        @Suppress("UNCHECKED_CAST") val decs = arr[4] as List<NotificationDecisionRecord>
        val latest = checkins.firstOrNull()
        val active = all.firstOrNull { it.state.isActive }
        val last = all.filter { it.state.isFinished }.maxByOrNull { it.actualEndMs ?: 0 }
        val sd = active?.let { a -> decs.filter { it.sessionId == a.id } } ?: emptyList()
        val recentEnergy = latest?.takeIf { now - it.timestampMs < 3 * 3_600_000L }
        val todayStart = InsightsCalculator.startOfDay(java.util.Calendar.getInstance(), now)
        FocusUiState(false, active, active?.let { TimerMath.remainingMs(it, now) } ?: 0, active?.let { TimerMath.focusedMs(it, now) } ?: 0,
            active?.let { TimerMath.progress(it, now) } ?: 0f, last, last?.let { l -> items.count { it.sessionId == l.id } } ?: 0,
            active?.let { a -> items.count { it.sessionId == a.id && !it.keptVisible } } ?: 0,
            sd.count { it.routing == Routing.DELIVER.name }, sd.count { it.routing == Routing.REVIEW.name },
            s.defaultFocusMin, s.breakMin, s.demoEnabled, s.energyCheckInEnabled, latest,
            if (s.energyCheckInEnabled) BreakSuggester.suggest(latest, active, now) else null,
            recentEnergy?.let { ProfileCalculator.durationFor(it.score, all, checkins, s.defaultFocusMin) },
            s.aiMode, models.status.loaded, ac, s.suppressOriginals, s.afterFocus,
            all.filter { it.sessionStartMs >= todayStart }.sumOf { (TimerMath.focusedMs(it, now) / 60_000L).toInt() }, s.dailyGoalMin, b)
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
    fun sendDemoNotification() = run {
        val r = demo.injectLiveSample()
        events.emit(when (r.routing) {
            Routing.DELIVER -> "Demo sample let through: ${r.reason}"
            Routing.REVIEW -> "Demo sample kept visible for review: ${r.reason}"
            Routing.FILTER -> "Demo sample filtered: ${r.reason}"
            else -> "Demo sample held in your queue: ${r.reason}"
        })
    }
    fun checkIn(score: Int, phase: String?) = viewModelScope.launch { energy.checkIn(score, phase) }
}
