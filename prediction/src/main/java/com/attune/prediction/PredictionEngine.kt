package com.attune.prediction
import com.attune.prediction.model.*
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Baseline estimator (NOT a trained model). Fully deterministic; see docs/PREDICTION_BASELINE.md.
 *
 * 1. Start from the category prior in priors.json (time away T0, recovery R0). These are starting assumptions, not measurements.
 * 2. Blend with the user's own self-reported observations for the same category: w = n / (n + k); T = w*mean + (1-w)*T0.
 * 3. Apply fixed, labelled context rules: bundled notifications (x1.1 / x1.25 time away), late evening (x1.15 time away),
 *    deep into a focus session (x1.25 recovery), no focus task running (x0.4 recovery).
 * 4. Risk from T + R: >= 20 min HIGH, >= 9 min MED, otherwise LOW.
 * 5. Confidence = w (clamped 0.1..0.9); cold start while n < [coldStartThreshold]. Range half-width shrinks from 60% to 20% as w grows.
 */
class PredictionEngine(private val priors:Map<String,CategoryPrior>, private val k:Int=5, private val coldStartThreshold:Int=3) : AttentionPredictor {
    override val modelVersion = "baseline-v1"

    override fun predict(input: PredictionInput): Prediction {
        val n = input.notification; val s = input.session
        val prior = priors[n.category] ?: priors["unknown"] ?: PriorLoader.getDefault()
        val factors = mutableListOf<PredictionFactor>()
        val catName = categoryName(n.category)
        factors += PredictionFactor("$catName starting point", "≈${fmt(prior.defaultTimeAwayMin)} min away, ≈${fmt(prior.defaultRecoveryMin)} min to refocus", FactorSource.STARTING_ASSUMPTION)

        val h = input.history.filter { it.category == n.category && !it.reportedTimeAwayMin.isNaN() && !it.reportedRecoveryMin.isNaN() }
        val cnt = h.size
        val w = if (cnt == 0) 0f else cnt.toFloat() / (cnt + k)
        var t = prior.defaultTimeAwayMin; var r = prior.defaultRecoveryMin
        if (cnt > 0) {
            val at = h.map { it.reportedTimeAwayMin }.average().toFloat()
            val ar = h.map { it.reportedRecoveryMin }.average().toFloat()
            t = w * at + (1 - w) * t; r = w * ar + (1 - w) * r
            factors += PredictionFactor("Your history", "$cnt past ${if (cnt == 1) "report" else "reports"} for $catName (avg ${fmt(at)} min away), weighted ${(w * 100).roundToInt()}%", FactorSource.YOUR_DATA)
        }
        when {
            n.groupCount >= 3 -> { t *= 1.25f; factors += PredictionFactor("${n.groupCount} bundled notifications", "Assumed +25% time away to read several items", FactorSource.CONTEXT) }
            n.groupCount == 2 -> { t *= 1.1f; factors += PredictionFactor("2 bundled notifications", "Assumed +10% time away", FactorSource.CONTEXT) }
        }
        if (input.hourOfDay >= 21 || input.hourOfDay < 5) {
            t *= 1.15f; factors += PredictionFactor("Late evening", "Starting assumption: +15% time away", FactorSource.STARTING_ASSUMPTION)
        }
        when (s.phase) {
            SessionPhase.FOCUS -> if (s.elapsedMinutes >= 20f) { r *= 1.25f; factors += PredictionFactor("${s.elapsedMinutes.roundToInt()} min into focus", "More task context to rebuild: +25% recovery", FactorSource.CONTEXT) }
                else factors += PredictionFactor("Focus session running", "Opening now would interrupt active work", FactorSource.CONTEXT)
            SessionPhase.PAUSED_OR_BREAK -> { r *= 0.4f; factors += PredictionFactor("On a break", "Less task context to rebuild: recovery x0.4", FactorSource.CONTEXT) }
            SessionPhase.NO_SESSION -> { r *= 0.4f; factors += PredictionFactor("No focus session", "No active task to return to: recovery x0.4", FactorSource.CONTEXT) }
        }
        t = max(0f, t); r = max(0f, r)
        val total = t + r
        val risk = when { total >= 20f -> RiskLevel.HIGH; total >= 9f -> RiskLevel.MED; else -> RiskLevel.LOW }
        val coldStart = cnt < coldStartThreshold
        val conf = if (cnt == 0) 0.1f else w.coerceIn(0.1f, 0.9f)
        val spread = 0.6f - 0.4f * w

        val basis = if (cnt == 0) "No personal data for $catName yet, so this uses a general starting assumption."
            else "Blends a starting assumption with $cnt of your own ${if (cnt == 1) "report" else "reports"} for $catName."
        val explanation = "$basis Opening it may take you away for about ${fmt(t)} min, and getting back into your task may take about ${fmt(r)} min more."
        val delay = when (s.phase) {
            SessionPhase.FOCUS -> if (s.remainingMinutes >= 1f) "Delaying keeps this out of your remaining ${s.remainingMinutes.roundToInt()} min of focus. You can review it on your break."
                else "Your session is almost over — waiting a moment costs little."
            SessionPhase.PAUSED_OR_BREAK -> "You're on a break, so this is a reasonable time to look."
            SessionPhase.NO_SESSION -> "No focus session is running; delaying mainly helps you batch notifications."
        }
        return Prediction(risk, t, r, explanation, conf, cnt, delay, true,
            t * (1 - spread), t * (1 + spread), r * (1 - spread), r * (1 + spread), factors, coldStart, modelVersion)
    }

    companion object {
        fun fmt(v: Float): String = if (v < 10f && (v * 2).roundToInt() % 2 == 1) String.format(java.util.Locale.US, "%.1f", (v * 2).roundToInt() / 2f) else v.roundToInt().toString()
        fun categoryName(c: String) = when (c) {
            "short_video" -> "short video"; "social" -> "social media"; "messaging" -> "messaging"; "email" -> "email"; "gaming" -> "gaming"
            "shopping" -> "shopping"; "news" -> "news"; "entertainment" -> "entertainment"; "productivity" -> "work tools"; "finance" -> "finance"
            "health" -> "health"; "promotion" -> "promotional"; else -> "uncategorised"
        }
    }
}
