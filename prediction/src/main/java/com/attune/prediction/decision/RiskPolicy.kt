package com.attune.prediction.decision

import com.attune.prediction.classifier.LinkAnalyzer
import com.attune.prediction.classifier.RoutingThresholds
import com.attune.prediction.classifier.SafetyRules
import com.attune.prediction.classifier.TextFeaturizer

/** Why the model pipeline chose its routing; turned into user-facing reasons by the engine. */
enum class ModelBasis { STRONG_EVIDENCE, INSUFFICIENT_CONTENT, LINK_ONLY, IMPORTANT_OR_URGENT, LOW_CONFIDENCE, POSSIBLY_IMPORTANT, CONFIDENT_DEFER }

data class PolicyOutcome(val routing: Routing, val risk: FocusRisk, val basis: ModelBasis)

/**
 * Turns calibrated probabilities into a routing and a focus risk. Kotlin mirror of `route` in ml/attune_features.py
 * (thresholds are chosen on the validation split and shipped inside the model file).
 * Safety bias: anything that might be important or that the model is unsure about is never silently deferred; it is REVIEW.
 */
object RiskPolicy {
    fun route(s: Signals, t: RoutingThresholds, title: String?, text: String?, category: String?): PolicyOutcome {
        val words = TextFeaturizer.wordCount(text)
        val hasLink = LinkAnalyzer.hosts(text).isNotEmpty()
        if (SafetyRules.strongEvidence(title, text, category) != null)
            return PolicyOutcome(Routing.DELIVER, if (s.distracting < 0.5) FocusRisk.LOW else FocusRisk.MEDIUM, ModelBasis.STRONG_EVIDENCE)
        if (hasLink && words == 0) return PolicyOutcome(Routing.REVIEW, FocusRisk.UNKNOWN, ModelBasis.LINK_ONLY)
        if (!hasLink && words <= t.shortWords) return PolicyOutcome(Routing.REVIEW, FocusRisk.UNKNOWN, ModelBasis.INSUFFICIENT_CONTENT)
        if (s.pHigh >= t.allowImportant || s.urgent >= t.allowUrgent)
            return PolicyOutcome(Routing.DELIVER, if (s.distracting >= 0.5) FocusRisk.MEDIUM else FocusRisk.LOW, ModelBasis.IMPORTANT_OR_URGENT)
        if (s.confidence < t.minConfidence) return PolicyOutcome(Routing.REVIEW, FocusRisk.UNKNOWN, ModelBasis.LOW_CONFIDENCE)
        if (s.pHigh >= t.reviewImportant) return PolicyOutcome(Routing.REVIEW, FocusRisk.UNKNOWN, ModelBasis.POSSIBLY_IMPORTANT)
        val risk = when {
            s.distracting >= 0.5 -> FocusRisk.HIGH
            s.pMedium >= maxOf(s.pLow, s.pHigh) && s.actionRequired >= 0.5 -> FocusRisk.MEDIUM
            else -> FocusRisk.LOW
        }
        return PolicyOutcome(Routing.DEFER, risk, ModelBasis.CONFIDENT_DEFER)
    }

    /** Rules-mode risk: a coarse estimate from the app category only (no text analysis). */
    fun categoryRisk(category: String) = when (category) {
        "social", "short_video", "gaming", "shopping", "promotion", "entertainment" -> FocusRisk.HIGH
        "messaging", "email", "news", "unknown" -> FocusRisk.MEDIUM
        else -> FocusRisk.LOW
    }
}
