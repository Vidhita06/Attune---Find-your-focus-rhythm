package com.attune.app.ui.feedback
import androidx.lifecycle.ViewModel; import androidx.lifecycle.viewModelScope
import com.attune.app.data.QueueRepository
import com.attune.core.model.ActionType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*; import kotlinx.coroutines.launch
import javax.inject.Inject

data class FeedbackState(val appLabel: String = "", val measuredMin: Float? = null, val predictedMin: Float? = null, val alreadySubmitted: Boolean = false)

@HiltViewModel class FeedbackViewModel @Inject constructor(private val queue: QueueRepository) : ViewModel() {
    private val id = MutableStateFlow<String?>(null)
    private var saving = false
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<FeedbackState> = id.filterNotNull().flatMapLatest { nid ->
        combine(queue.observe(nid), queue.actionsFor(nid), queue.feedback()) { n, actions, fb ->
            val open = actions.firstOrNull { it.actionType == ActionType.OPENED && it.launchedAtMs != null }
            val measured = open?.let { a -> a.returnedAtMs?.let { (it - a.launchedAtMs!!) / 60_000f } }
            FeedbackState(n?.appLabel ?: "", measured, open?.predictedTimeAwayMin, fb.any { it.notificationId == nid })
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FeedbackState())

    fun load(nid: String) { id.value = nid }
    fun submit(t: Float?, r: Float?, done: () -> Unit) {
        val nid = id.value ?: return; if (saving) return; saving = true
        viewModelScope.launch { queue.saveFeedback(nid, t, r); done() }
    }
}
