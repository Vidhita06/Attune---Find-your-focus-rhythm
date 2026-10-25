package com.attune.app.ui.home
import androidx.lifecycle.ViewModel; import androidx.lifecycle.viewModelScope
import com.attune.app.data.*
import com.attune.app.domain.*
import com.attune.app.ui.tickerFlow
import com.attune.core.data.db.dao.UserActionDao
import com.attune.core.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*; import kotlinx.coroutines.launch
import java.text.SimpleDateFormat; import java.util.*; import javax.inject.Inject

data class HomeUiState(
    val loading: Boolean = true,
    val greeting: String = "", val date: String = "",
    val active: FocusSession? = null, val remainingMs: Long = 0, val progress: Float = 0f,
    val defaultFocusMin: Int = 25,
    val todayFocusMin: Int = 0, val todayCompleted: Int = 0, val dailyGoalMin: Int = 0,
    val pendingReal: Int = 0, val pendingDemo: Int = 0, val delayed: Int = 0, val heldToday: Int = 0,
    val preview: List<QueuedNotification> = emptyList(),
    val last7: List<Int> = emptyList(), val score: FocusScore? = null, val streak: Streak? = null,
    val tip: Insight? = null, val tipIsDemo: Boolean = false,
    val energyEnabled: Boolean = true, val latestCheckIn: EnergyCheckIn? = null, val suggestion: String? = null,
    val access: AccessStatus = AccessStatus.NOT_GRANTED, val demoEnabled: Boolean = false,
    val aiMode: Boolean = true, val modelLoaded: Boolean = true, val hideSensitive: Boolean = true
)

@HiltViewModel class HomeViewModel @Inject constructor(
    private val sessions: FocusSessionRepository, queue: QueueRepository, private val settings: SettingsRepository,
    private val energy: EnergyRepository, access: NotificationAccessMonitor, decisions: DecisionRepository, actionDao: UserActionDao,
    personalization: PersonalizationRepository, private val models: ModelManager
) : ViewModel() {
    private class Data(val all: List<FocusSession>, val items: List<QueuedNotification>, val s: AttuneSettings, val checkins: List<EnergyCheckIn>, val access: AccessStatus,
                       val decisions: List<NotificationDecisionRecord>, val actions: List<UserAction>, val feedback: List<FeedbackEvent>)
    private val data = combine(
        combine(sessions.all, queue.items, settings.settings, energy.all, access.status) { a, i, s, c, ac -> arrayOf<Any>(a, i, s, c, ac) },
        combine(decisions.all, actionDao.observeAll(), personalization.events) { d, a, f -> Triple(d, a, f) }
    ) { x, y ->
        @Suppress("UNCHECKED_CAST")
        Data(x[0] as List<FocusSession>, x[1] as List<QueuedNotification>, x[2] as AttuneSettings, x[3] as List<EnergyCheckIn>, x[4] as AccessStatus, y.first, y.second, y.third)
    }

    // The profile is recomputed only when data changes; the 1 s ticker only refreshes the timer.
    private val derived = data.map { d ->
        val now = System.currentTimeMillis()
        val real = d.all.filter { !it.isDemo }
        val prof = ProfileCalculator.compute(real, d.decisions.filter { !it.isDemo }, d.actions.filter { !it.isDemo }, d.checkins.filter { !it.isDemo },
            d.feedback.filter { !it.isDemo }, d.s.dailyGoalMin, d.s.weeklyGoalMin, now)
        val demoTip = if (prof.coach.isEmpty() && prof.insights.isEmpty() && d.s.demoEnabled && d.all.any { it.isDemo })
            ProfileCalculator.compute(d.all.filter { it.isDemo }, d.decisions.filter { it.isDemo }, d.actions.filter { it.isDemo }, d.checkins.filter { it.isDemo },
                d.feedback.filter { it.isDemo }, 0, 0, now).let { it.coach.firstOrNull() ?: it.insights.firstOrNull() } else null
        Triple(d, prof, demoTip)
    }

    val state: StateFlow<HomeUiState> = combine(derived, tickerFlow(1_000)) { (d, prof, demoTip), now ->
        val real = d.all.filter { !it.isDemo }; val active = real.firstOrNull { it.state.isActive }
        val todayStart = InsightsCalculator.startOfDay(Calendar.getInstance(), now)
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val latest = d.checkins.firstOrNull { !it.isDemo }
        val suggestion = if (!d.s.energyCheckInEnabled) null else BreakSuggester.suggest(latest, active, now) ?: latest?.takeIf { active == null && now - it.timestampMs < 3 * 3_600_000L }?.let {
            val sug = ProfileCalculator.durationFor(it.score, real, d.checkins.filter { c -> !c.isDemo }, d.s.defaultFocusMin)
            "Suggested session for energy ${it.score}/5: ${sug.minutes} min. ${sug.basis}."
        }
        HomeUiState(false,
            when { h < 5 -> "Good evening"; h < 12 -> "Good morning"; h < 17 -> "Good afternoon"; else -> "Good evening" },
            SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date(now)),
            active, active?.let { TimerMath.remainingMs(it, now) } ?: 0, active?.let { TimerMath.progress(it, now) } ?: 0f,
            d.s.defaultFocusMin, prof.overview.todayMin, real.count { it.sessionStartMs >= todayStart && it.state == SessionState.COMPLETED }, d.s.dailyGoalMin,
            d.items.count { !it.isDemo && it.status == NotificationStatus.PENDING }, d.items.count { it.isDemo && it.status == NotificationStatus.PENDING },
            d.items.count { it.status == NotificationStatus.DELAYED },
            d.decisions.count { !it.isDemo && it.phase == "FOCUS" && it.postedAtMs >= todayStart && (it.routing == "DEFER" || it.routing == "FILTER") },
            d.items.filter { it.status == NotificationStatus.PENDING }.sortedByDescending { it.postTimeMs }.take(3),
            prof.overview.last14.takeLast(7), prof.score, prof.streak.takeIf { real.isNotEmpty() },
            if (d.s.coachEnabled) prof.coach.firstOrNull() ?: prof.insights.firstOrNull() ?: demoTip else null, demoTip != null && prof.coach.isEmpty() && prof.insights.isEmpty(),
            d.s.energyCheckInEnabled, latest, suggestion, d.access, d.s.demoEnabled, d.s.aiMode, models.status.loaded, d.s.hideSensitivePreviews)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    fun startFocus(min: Int) = viewModelScope.launch { sessions.start(min) }
    fun pause() = viewModelScope.launch { sessions.pause() }
    fun resume() = viewModelScope.launch { sessions.resume() }
    fun checkIn(score: Int) = viewModelScope.launch { energy.checkIn(score, if (sessions.getActive() == null) "BEFORE" else null) }
    fun setAiMode(on: Boolean) = viewModelScope.launch { settings.setFlag(Flag.AI_MODE, on) }
}
