package com.attune.app.ui.home
import androidx.lifecycle.ViewModel; import androidx.lifecycle.viewModelScope
import com.attune.app.data.*
import com.attune.app.domain.*
import com.attune.app.ui.tickerFlow
import com.attune.core.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*; import kotlinx.coroutines.launch
import java.text.SimpleDateFormat; import java.util.*; import javax.inject.Inject

data class HomeUiState(
    val loading: Boolean = true,
    val greeting: String = "", val date: String = "",
    val active: FocusSession? = null, val remainingMs: Long = 0, val progress: Float = 0f,
    val defaultFocusMin: Int = 25,
    val todayFocusMin: Int = 0, val todayCompleted: Int = 0,
    val pendingReal: Int = 0, val pendingDemo: Int = 0, val delayed: Int = 0,
    val preview: List<QueuedNotification> = emptyList(),
    val insight: String = "", val insightIsDemo: Boolean = false,
    val energyEnabled: Boolean = true, val latestCheckIn: EnergyCheckIn? = null, val suggestion: String? = null,
    val access: AccessStatus = AccessStatus.NOT_GRANTED, val demoEnabled: Boolean = false
)

@HiltViewModel class HomeViewModel @Inject constructor(
    private val sessions: FocusSessionRepository, private val queue: QueueRepository, private val settings: SettingsRepository,
    private val energy: EnergyRepository, access: NotificationAccessMonitor
) : ViewModel() {
    private data class Data(val all: List<FocusSession>, val items: List<QueuedNotification>, val s: AttuneSettings, val checkins: List<EnergyCheckIn>, val access: AccessStatus)
    private val data = combine(sessions.all, queue.items, settings.settings, energy.all, access.status) { a, i, s, c, ac -> Data(a, i, s, c, ac) }

    val state: StateFlow<HomeUiState> = combine(data, tickerFlow(1_000)) { d, now ->
        val real = d.all.filter { !it.isDemo }; val active = real.firstOrNull { it.state.isActive }
        val realItems = d.items.filter { !it.isDemo }
        val summary = InsightsCalculator.compute(real, realItems, emptyList(), now)
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val latest = d.checkins.firstOrNull { !it.isDemo }
        val (insight, demo) = insight(real, realItems, d.items.filter { it.isDemo }, d.all.filter { it.isDemo }, d.s.demoEnabled, now)
        HomeUiState(false,
            when { h < 5 -> "Good evening"; h < 12 -> "Good morning"; h < 17 -> "Good afternoon"; else -> "Good evening" },
            SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date(now)),
            active, active?.let { TimerMath.remainingMs(it, now) } ?: 0, active?.let { TimerMath.progress(it, now) } ?: 0f,
            d.s.defaultFocusMin, summary.todayFocusMin, summary.todayCompleted,
            d.items.count { !it.isDemo && it.status == NotificationStatus.PENDING }, d.items.count { it.isDemo && it.status == NotificationStatus.PENDING },
            d.items.count { it.status == NotificationStatus.DELAYED },
            d.items.filter { it.status == NotificationStatus.PENDING }.take(3),
            insight, demo, d.s.energyCheckInEnabled, latest, if (d.s.energyCheckInEnabled) BreakSuggester.suggest(latest, active, now) else null,
            d.access, d.s.demoEnabled)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    private fun insight(real: List<FocusSession>, realItems: List<QueuedNotification>, demoItems: List<QueuedNotification>, demoSessions: List<FocusSession>, demoOn: Boolean, now: Long): Pair<String, Boolean> {
        val finished = real.filter { it.state.isFinished }
        if (finished.isNotEmpty() || realItems.isNotEmpty()) {
            val p = AttentionProfileCalculator.compute(real, realItems, now)
            return when {
                p.bestBucket != null -> "Your sessions in the ${p.bestBucket.name.lowercase()} have the best completion rate so far (${(p.bestBucket.completionRate * 100).toInt()}%)." to false
                p.topApps.isNotEmpty() -> "${p.topApps.first().label} has sent the most queued notifications (${p.topApps.first().count})." to false
                else -> "You've finished ${finished.size} session${if (finished.size == 1) "" else "s"} and completed ${finished.count { it.state == SessionState.COMPLETED }}. Keep going to unlock patterns." to false
            }
        }
        if (demoOn && demoSessions.isNotEmpty()) {
            val p = AttentionProfileCalculator.compute(demoSessions, demoItems, now)
            p.bestBucket?.let { return "Sample: ${it.name} sessions complete most often in the demo data (${(it.completionRate * 100).toInt()}%)." to true }
        }
        return "Complete your first focus session to start building your attention profile." to false
    }

    fun startFocus(min: Int) = viewModelScope.launch { sessions.start(min) }
    fun checkIn(level: EnergyLevel) = viewModelScope.launch { energy.checkIn(level) }
}
