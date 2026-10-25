package com.attune.app.ui.insights
import androidx.lifecycle.ViewModel; import androidx.lifecycle.viewModelScope
import com.attune.app.data.*
import com.attune.app.domain.*
import com.attune.core.data.db.dao.UserActionDao
import com.attune.core.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class InsightsState(
    val loading: Boolean = true, val showDemo: Boolean = false, val demoAvailable: Boolean = false,
    val summary: InsightsSummary? = null, val profile: AttentionProfile? = null, val checkins: List<EnergyCheckIn> = emptyList(), val energyEnabled: Boolean = true
)

@HiltViewModel class InsightsViewModel @Inject constructor(
    sessions: FocusSessionRepository, queue: QueueRepository, actionDao: UserActionDao, energy: EnergyRepository, settings: SettingsRepository
) : ViewModel() {
    /** false = the user's real records, true = clearly-labelled demo sample. The two are never mixed. */
    val demoView = MutableStateFlow(false)
    val state: StateFlow<InsightsState> = combine(
        combine(sessions.all, queue.items, actionDao.observeAll(), energy.all) { a, b, c, d -> listOf(a, b, c, d) }, settings.settings, demoView
    ) { l, st, demo ->
        @Suppress("UNCHECKED_CAST") val ss = (l[0] as List<FocusSession>); @Suppress("UNCHECKED_CAST") val ns = (l[1] as List<QueuedNotification>)
        @Suppress("UNCHECKED_CAST") val acts = (l[2] as List<UserAction>); @Suppress("UNCHECKED_CAST") val es = (l[3] as List<EnergyCheckIn>)
        val demoAvail = st.demoEnabled && ss.any { it.isDemo }
        val d = demo && demoAvail
        val now = System.currentTimeMillis()
        val fs = ss.filter { it.isDemo == d }; val fn = ns.filter { it.isDemo == d }
        InsightsState(false, d, demoAvail, InsightsCalculator.compute(fs, fn, acts.filter { it.isDemo == d }, now),
            AttentionProfileCalculator.compute(fs, fn, now), es.filter { it.isDemo == d }.take(8), st.energyCheckInEnabled)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), InsightsState())
}
