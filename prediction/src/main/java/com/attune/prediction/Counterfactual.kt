package com.attune.prediction
import com.attune.prediction.model.SessionPhase
import kotlin.math.roundToInt

enum class Decision { OPENED, DELAYED, DISMISSED }

data class CounterfactualInput(
    val decision: Decision,
    val phaseAtDecision: SessionPhase,
    val predictedTimeAwayMin: Float,
    val predictedRecoveryMin: Float,
    val isColdStart: Boolean,
    val confidence: Float,
    /** Minutes between launching the other app and returning to Attune, if measured. An upper bound on time away. */
    val measuredAwayMin: Float? = null,
    /** User's own report, if given. */
    val reportedAwayMin: Float? = null,
    /** COMPLETED / ENDED_EARLY / null while running. */
    val sessionOutcome: String? = null
)

data class CounterfactualResult(
    val observed: String,
    val alternative: String,
    /** Estimated in-session minutes avoided by not opening immediately; null when not applicable. */
    val estimatedMinutesProtected: Float?,
    val uncertainty: String
)

/**
 * "What might have happened if I had opened this immediately?" Compares the recorded decision with the baseline's
 * estimate of the alternative. Results are estimates, never facts, and a single event is not causal evidence.
 */
object CounterfactualEstimator {
    fun estimate(i: CounterfactualInput): CounterfactualResult {
        val t = i.predictedTimeAwayMin; val r = i.predictedRecoveryMin
        val outcome = when (i.sessionOutcome) { "COMPLETED" -> " The session went on to complete."; "ENDED_EARLY" -> " The session was ended early."; else -> "" }
        val uncertainty = (if (i.isColdStart) "Low confidence: based on general starting assumptions, not your history. " else "Confidence ${(i.confidence * 100).roundToInt()}% from your past reports. ") +
            "This is a baseline estimate of an outcome that did not happen; one decision cannot show cause and effect."
        return when (i.decision) {
            Decision.DELAYED, Decision.DISMISSED -> {
                val verb = if (i.decision == Decision.DELAYED) "delayed" else "dismissed"
                if (i.phaseAtDecision == SessionPhase.FOCUS) CounterfactualResult(
                    "You $verb it during focus.$outcome",
                    "Had you opened it immediately, the baseline estimates ≈${fmt(t)} min away plus ≈${fmt(r)} min to refocus.",
                    t + r, uncertainty)
                else CounterfactualResult(
                    "You $verb it while no focus session was running.$outcome",
                    "No focus time was at stake, so no in-session interruption is estimated to have been avoided.",
                    0f, uncertainty)
            }
            Decision.OPENED -> {
                val obs = when {
                    i.reportedAwayMin != null -> "You opened it and reported ≈${fmt(i.reportedAwayMin)} min away (estimate was ≈${fmt(t)} min)."
                    i.measuredAwayMin != null -> "You opened it and came back to Attune after ≈${fmt(i.measuredAwayMin)} min (upper bound; estimate was ≈${fmt(t)} min)."
                    else -> "You opened it. Time away was not measured."
                }
                val alt = if (i.phaseAtDecision == SessionPhase.FOCUS)
                    "Had you delayed it until your break, the estimated in-session interruption would have been near 0 min, with ≈${fmt(t)} min spent on it later instead."
                else "You were not in a focus session, so delaying would mainly have changed when you read it."
                CounterfactualResult(obs + outcome, alt, null, uncertainty)
            }
        }
    }
    private fun fmt(v: Float) = PredictionEngine.fmt(v)
}
