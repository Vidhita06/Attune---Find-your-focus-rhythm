package com.attune.app.ui.prediction
import androidx.lifecycle.ViewModel; import androidx.lifecycle.viewModelScope
import com.attune.app.data.*
import com.attune.app.domain.LaunchResults
import com.attune.core.model.*
import com.attune.prediction.*
import com.attune.prediction.model.FactorSource
import com.attune.prediction.model.SessionPhase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*; import kotlinx.coroutines.launch
import javax.inject.Inject

data class FactorView(val label: String, val detail: String, val source: FactorSource)
/** Display model shared by live baseline estimates and estimates stored at decision time. */
data class EstimateView(
    val risk: RiskLevel, val timeAway: Float, val recovery: Float, val tLow: Float, val tHigh: Float, val rLow: Float, val rHigh: Float,
    val explanation: String, val factors: List<FactorView>, val coldStart: Boolean, val confidence: Float, val observations: Int,
    val delayScenario: String, val modelVersion: String, val storedAtDecision: Boolean
) { val confidenceLabel get() = when { coldStart -> "Cold start"; confidence >= 0.6f -> "Higher"; confidence >= 0.35f -> "Medium"; else -> "Low" } }

sealed interface PredictionEvent { data class Message(val text: String) : PredictionEvent; data class OpenedGoFeedback(val id: String) : PredictionEvent; data object Back : PredictionEvent }

/** The AI/rules decision for this notification, as shown on Before You Open. */
data class DecisionView(val record: NotificationDecisionRecord, val reasons: List<com.attune.prediction.decision.DecisionReason>, val terms: List<String>)

data class PredState(
    val loading: Boolean = true, val notFound: Boolean = false, val notif: QueuedNotification? = null, val estimate: EstimateView? = null,
    val phase: SessionPhase = SessionPhase.NO_SESSION, val decision: UserAction? = null, val counterfactual: CounterfactualResult? = null,
    val failedAttempts: Int = 0, val hasOriginalTarget: Boolean = false, val inFlight: Boolean = false,
    val ai: DecisionView? = null, val feedbackGiven: List<FeedbackEvent> = emptyList(), val hideSensitive: Boolean = true
)

@HiltViewModel class PredictionViewModel @Inject constructor(
    private val queue: QueueRepository, private val sessions: FocusSessionRepository, private val launcher: NotificationLauncher,
    private val decisions: DecisionRepository, private val personalization: PersonalizationRepository, private val models: ModelManager,
    settings: SettingsRepository
) : ViewModel() {
    private val aiView = MutableStateFlow<DecisionView?>(null)
    private val extras = combine(aiView, personalization.events, settings.settings) { a, f, st -> Triple(a, f, st.hideSensitivePreviews) }
    private val id = MutableStateFlow<String?>(null)
    private val inFlight = MutableStateFlow(false)
    private val estimate = MutableStateFlow<EstimateView?>(null)
    private val _events = MutableSharedFlow<PredictionEvent>(extraBufferCapacity = 4); val events: SharedFlow<PredictionEvent> = _events

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val state: StateFlow<PredState> = id.filterNotNull().flatMapLatest { nid ->
        combine(queue.observe(nid), queue.actionsFor(nid), queue.feedback().map { l -> l.filter { it.notificationId == nid } }, sessions.active,
            combine(estimate, inFlight, extras) { e, f, x -> Triple(e, f, x) }) { n, actions, fb, active, efx ->
            val ef = efx.first to efx.second; val (ai, fbEvents, hide) = efx.third
            if (n == null) return@combine PredState(loading = false, notFound = true)
            val decision = actions.firstOrNull { it.actionType != ActionType.OPENED || LaunchResults.succeeded(it.launchResult) || it.launchResult == LaunchResults.DEMO_NO_APP }
            val e = ef.first
            val cf = if (decision != null && e != null) {
                val launched = decision.launchedAtMs; val returned = decision.returnedAtMs
                CounterfactualEstimator.estimate(CounterfactualInput(Decision.valueOf(decision.actionType.name),
                    runCatching { SessionPhase.valueOf(decision.sessionState ?: "") }.getOrDefault(SessionPhase.NO_SESSION),
                    decision.predictedTimeAwayMin ?: e.timeAway, decision.predictedRecoveryMin ?: e.recovery, e.coldStart, e.confidence,
                    if (launched != null && returned != null) (returned - launched) / 60_000f else null,
                    fb.firstOrNull { it.reportedTimeAwayMin != null }?.reportedTimeAwayMin, decision.sessionOutcome))
            } else null
            PredState(false, false, n, e, FocusSessionRepository.phaseOf(active), decision, cf,
                actions.count { it.launchResult == LaunchResults.FAILED }, launcher.hasOriginalTarget(nid), ef.second,
                ai, fbEvents.filter { it.notificationId == nid }, hide)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PredState())

    fun load(nid: String) {
        if (id.value == nid) return
        id.value = nid
        viewModelScope.launch {
            val n = queue.observe(nid).first() ?: return@launch
            estimate.value = if (n.status.isOpen) live(n) else queue.storedPrediction(nid)?.let(::stored) ?: live(n)
            aiView.value = n.decisionId?.let { decisions.get(it) }?.let { r ->
                // Explanation words are computed on the fly from the stored preview (never persisted) and skipped for sensitive items.
                val terms = if (n.isSensitive || n.textScrubbed) emptyList() else models.engine.explainTerms(n.title, n.previewText, n.category)
                DecisionView(r, DecisionRepository.reasonsOf(r), terms)
            }
        }
    }

    private suspend fun live(n: QueuedNotification): EstimateView { val p = queue.predict(n)
        return EstimateView(RiskLevel.valueOf(p.risk.name), p.timeAwayMin, p.recoveryMin, p.timeAwayLow, p.timeAwayHigh, p.recoveryLow, p.recoveryHigh,
            p.explanation, p.factors.map { FactorView(it.label, it.detail, it.source) }, p.isColdStart, p.confidence, p.observationCount, p.delayScenario, p.modelVersion, false) }

    private fun stored(r: com.attune.core.model.Prediction): EstimateView {
        val w = r.observationCount.toFloat() / (r.observationCount + 5); val sp = 0.6f - 0.4f * w
        val factors = r.factorsSummary?.lines()?.mapNotNull { l -> l.split("|").takeIf { it.size == 3 }?.let { FactorView(it[0], it[1], runCatching { FactorSource.valueOf(it[2]) }.getOrDefault(FactorSource.CONTEXT)) } } ?: emptyList()
        return EstimateView(r.risk, r.timeAwayMin, r.recoveryMin, r.timeAwayMin * (1 - sp), r.timeAwayMin * (1 + sp), r.recoveryMin * (1 - sp), r.recoveryMin * (1 + sp),
            r.explanation, factors, r.observationCount < 3, r.confidence, r.observationCount, r.delayScenario, r.modelVersion ?: "baseline-v1", true)
    }

    private fun act(block: suspend (String) -> DecisionResult) {
        val nid = id.value ?: return
        if (!inFlight.compareAndSet(false, true)) return  // ignore repeated taps
        viewModelScope.launch {
            try {
                val ev = when (val r = block(nid)) {
                    is DecisionResult.Done -> when (r.action) {
                        ActionType.OPENED -> if (LaunchResults.succeeded(r.launchResult)) PredictionEvent.OpenedGoFeedback(nid)
                            else PredictionEvent.Message("Marked as opened. This is a demo sample and its app isn't installed, so nothing was launched.")
                        ActionType.DELAYED -> PredictionEvent.Back
                        ActionType.DISMISSED -> PredictionEvent.Back
                    }
                    is DecisionResult.LaunchFailed -> PredictionEvent.Message(r.message)
                    is DecisionResult.AlreadyHandled -> PredictionEvent.Message("Already ${r.status.name.lowercase()} — nothing changed.")
                    DecisionResult.NotFound -> PredictionEvent.Message("This notification is no longer in the queue.")
                }
                if (ev is PredictionEvent.Back) _events.emit(PredictionEvent.Message(if (queue.observe(nid).first()?.status == NotificationStatus.DELAYED) "Delayed until your break." else "Dismissed."))
                _events.emit(ev)
            } finally { inFlight.value = false }
        }
    }
    fun feedback(kind: com.attune.prediction.decision.FeedbackKind) {
        val nid = id.value ?: return
        viewModelScope.launch {
            val n = queue.observe(nid).first() ?: return@launch
            val msg = when (val r = personalization.record(kind, n, aiView.value?.record)) {
                is PersonalizationRepository.Outcome.Saved -> r.message; is PersonalizationRepository.Outcome.NotPossible -> r.message }
            _events.emit(PredictionEvent.Message(msg))
        }
    }
    fun openNow() = act { queue.openNow(it) }
    fun delay() = act { queue.delay(it) }
    fun dismiss() = act { queue.dismiss(it) }
}
