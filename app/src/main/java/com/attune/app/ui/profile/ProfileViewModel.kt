package com.attune.app.ui.profile
import androidx.lifecycle.ViewModel; import androidx.lifecycle.viewModelScope
import com.attune.app.data.*
import com.attune.prediction.decision.OutsideFocus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*; import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileState(
    val loading: Boolean = true, val settings: AttuneSettings = AttuneSettings(), val access: AccessStatus = AccessStatus.NOT_GRANTED,
    val model: ModelStatus? = null, val learnedCount: Int = 0, val ruleCount: Int = 0, val realSessions: Int = 0
)

@HiltViewModel class ProfileViewModel @Inject constructor(
    sessions: FocusSessionRepository, private val settings: SettingsRepository, private val accessMonitor: NotificationAccessMonitor,
    private val demo: DemoDataRepository, private val models: ModelManager, personalization: PersonalizationRepository, rules: RulesRepository
) : ViewModel() {
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4); val messages: SharedFlow<String> = _messages
    val state: StateFlow<ProfileState> = combine(settings.settings, accessMonitor.status, personalization.learned(false), rules.rules, sessions.all) { st, ac, learned, rl, ss ->
        ProfileState(false, st, ac, models.status, learned.size, rl.size + st.allowedPackages.size + st.priorityContacts.size, ss.count { !it.isDemo && it.state.isFinished })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProfileState())

    fun openAccessSettings() { if (!accessMonitor.openSettings()) _messages.tryEmit("Couldn't open Android settings on this device.") }
    fun setFocus(m: Int) = viewModelScope.launch { settings.setFocusMin(m) }
    fun setBreak(m: Int) = viewModelScope.launch { settings.setBreakMin(m) }
    fun setDailyGoal(m: Int) = viewModelScope.launch { settings.setDailyGoal(m) }
    fun setWeeklyGoal(m: Int) = viewModelScope.launch { settings.setWeeklyGoal(m) }
    fun setAfterFocus(v: AfterFocus) = viewModelScope.launch { settings.setAfterFocus(v) }
    fun setOutsideFocus(v: OutsideFocus) = viewModelScope.launch { settings.setOutsideFocus(v) }
    fun setFlag(f: Flag, v: Boolean) = viewModelScope.launch {
        settings.setFlag(f, v)
        if (f == Flag.DEMO && v) demo.ensureSeeded()
    }
    fun resetDemo() = viewModelScope.launch { demo.reset(); _messages.emit("Demo data reset. Your real records were not touched.") }
}
