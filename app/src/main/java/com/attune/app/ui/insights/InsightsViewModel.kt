package com.attune.app.ui.insights
import androidx.lifecycle.ViewModel; import androidx.lifecycle.viewModelScope
import com.attune.app.data.*
import com.attune.app.domain.*
import com.attune.core.data.db.dao.UserActionDao
import com.attune.core.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import javax.inject.Inject

enum class InsightsTab { REPORTS, PROFILE }

data class InsightsState(
    val loading: Boolean = true, val showDemo: Boolean = false, val demoAvailable: Boolean = false,
    val report: Report? = null, val profile: AttentionProfileV2? = null, val history: List<SessionPoint> = emptyList(),
    val energyEnabled: Boolean = true, val canGoForward: Boolean = false
)

@HiltViewModel class InsightsViewModel @Inject constructor(
    sessions: FocusSessionRepository, decisions: DecisionRepository, actionDao: UserActionDao, energy: EnergyRepository,
    personalization: PersonalizationRepository, settings: SettingsRepository
) : ViewModel() {
    /** false = the user's real records, true = clearly-labelled demo sample. The two are never mixed. */
    val demoView = MutableStateFlow(false)
    val tab = MutableStateFlow(InsightsTab.REPORTS)
    val period = MutableStateFlow(Period.WEEK)
    /** 0 = current period, -1 = previous, … */
    val offset = MutableStateFlow(0)

    private val records = combine(sessions.all, decisions.all, actionDao.observeAll(), energy.all, personalization.events) { a, b, c, d, e -> arrayOf<Any>(a, b, c, d, e) }
    private val view = combine(period, offset, demoView) { p, o, d -> Triple(p, o, d) }

    val state: StateFlow<InsightsState> = combine(records, settings.settings, view) { r, st, (p, off, demo) ->
        @Suppress("UNCHECKED_CAST") val ss = r[0] as List<FocusSession>
        @Suppress("UNCHECKED_CAST") val ds = r[1] as List<NotificationDecisionRecord>
        @Suppress("UNCHECKED_CAST") val acts = r[2] as List<UserAction>
        @Suppress("UNCHECKED_CAST") val es = r[3] as List<EnergyCheckIn>
        @Suppress("UNCHECKED_CAST") val fb = r[4] as List<FeedbackEvent>
        val demoAvail = st.demoEnabled && ss.any { it.isDemo }
        val d = demo && demoAvail
        val now = System.currentTimeMillis()
        val sel = AnalyticsInput.select(ss, ds, acts, es, fb, d)
        val fs = sel.sessions; val fd = sel.decisions; val fa = sel.actions; val fe = sel.checkins; val ff = sel.feedback
        val base = ReportCalculator.range(p, now, now)
        val anchor = ReportCalculator.shift(base, off, now).start + 1
        InsightsState(false, d, demoAvail,
            ReportCalculator.compute(p, anchor, now, fs, fd, fa, fe),
            ProfileCalculator.compute(fs, fd, fa, fe, ff, if (d) 0 else st.dailyGoalMin, if (d) 0 else st.weeklyGoalMin, now),
            fs.filter { it.state.isFinished }.sortedByDescending { it.sessionStartMs }.take(20)
                .map { SessionPoint(it.sessionStartMs, (TimerMath.focusedMs(it, now) / 60_000L).toInt(), TimerMath.plannedMinutes(it), it.state, it.id) },
            st.energyCheckInEnabled, off < 0)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), InsightsState())

    fun setPeriod(p: Period) { period.value = p; offset.value = 0 }
    fun step(delta: Int) { offset.value = (offset.value + delta).coerceAtMost(0) }
}

/** Lets other screens open Insights on a specific tab (e.g. Profile → "Personal attention profile"). */
object InsightsNav { val request = MutableStateFlow<InsightsTab?>(null) }
