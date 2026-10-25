package com.attune.app.ui.profile
import androidx.lifecycle.ViewModel; import androidx.lifecycle.viewModelScope
import com.attune.app.data.*
import com.attune.app.domain.AttentionProfile
import com.attune.app.domain.AttentionProfileCalculator
import com.attune.core.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*; import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileState(
    val loading: Boolean = true, val settings: AttuneSettings = AttuneSettings(), val access: AccessStatus = AccessStatus.NOT_GRANTED,
    val profile: AttentionProfile? = null, val showDemo: Boolean = false, val demoAvailable: Boolean = false
)

@HiltViewModel class ProfileViewModel @Inject constructor(
    sessions: FocusSessionRepository, queue: QueueRepository, private val settings: SettingsRepository,
    private val accessMonitor: NotificationAccessMonitor, private val demo: DemoDataRepository
) : ViewModel() {
    val demoView = MutableStateFlow(false)
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4); val messages: SharedFlow<String> = _messages
    val state: StateFlow<ProfileState> = combine(sessions.all, queue.items, settings.settings, accessMonitor.status, demoView) { ss, ns, st, ac, dv ->
        val avail = st.demoEnabled && ss.any { it.isDemo }; val d = dv && avail
        ProfileState(false, st, ac, AttentionProfileCalculator.compute(ss.filter { it.isDemo == d }, ns.filter { it.isDemo == d }, System.currentTimeMillis()), d, avail)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProfileState())

    fun openAccessSettings() { if (!accessMonitor.openSettings()) _messages.tryEmit("Couldn't open Android settings on this device.") }
    fun setFocus(m: Int) = viewModelScope.launch { settings.setFocusMin(m) }
    fun setBreak(m: Int) = viewModelScope.launch { settings.setBreakMin(m) }
    fun setFlag(f: Flag, v: Boolean) = viewModelScope.launch {
        settings.setFlag(f, v)
        if (f == Flag.DEMO && v) demo.ensureSeeded()
        if (f == Flag.DEMO && !v) demoView.value = false
    }
    fun resetDemo() = viewModelScope.launch { demo.reset(); _messages.emit("Demo data reset. Your real records were not touched.") }
    fun deleteReal() = viewModelScope.launch { demo.deleteRealActivity(); _messages.emit("Your recorded activity was deleted from this device.") }
}
