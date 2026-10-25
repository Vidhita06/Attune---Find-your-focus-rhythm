package com.attune.prediction.decision

import com.attune.prediction.classifier.Evidence
import com.attune.prediction.classifier.LinkAssessment
import com.attune.prediction.model.SessionPhase
import com.attune.prediction.policy.IncomingNotification
import com.attune.prediction.policy.PolicySettings

/** User-facing focus risk: how likely this notification is to pull you away from focus for little benefit. */
enum class FocusRisk { LOW, MEDIUM, HIGH, UNKNOWN }

/**
 * DELIVER = left alone (it reaches you). DEFER = queued; Attune asks Android to remove the original (best-effort).
 * REVIEW = uncertain or possibly important: the original stays visible and a copy is listed in the queue.
 * FILTER = queued as filtered (promotions rule). IGNORE = not evaluated (ongoing, Attune's own, outside focus when not observing).
 */
enum class Routing { DELIVER, DEFER, REVIEW, FILTER, IGNORE }

enum class DecisionSource(val label: String) {
    SYSTEM("System"), USER_RULE("Your rule"), SAFETY("Safety rule"), MODEL("AI model"), PERSONALIZED_MODEL("AI model + your feedback"),
    UNCERTAIN_FALLBACK("Uncertain → kept visible"), RULES_MODE("Category rules (AI Mode off)"),
    MODEL_UNAVAILABLE("Category rules (model unavailable)"), NOT_IN_FOCUS("No active focus")
}

enum class ReasonKind { RULE, SAFETY, MODEL, PERSONAL, CONTEXT, LINK, FALLBACK }
data class DecisionReason(val kind: ReasonKind, val text: String)

enum class ImportanceLevel { LOW, MEDIUM, HIGH }

/** Model outputs after personalization, kept separate so importance, urgency, distraction and links are never merged into one number. */
data class Signals(
    val importance: ImportanceLevel, val pLow: Double, val pMedium: Double, val pHigh: Double,
    val urgent: Double, val distracting: Double, val actionRequired: Double, val confidence: Double, val personalBias: Double
)

data class NotificationDecision(
    val routing: Routing,
    val risk: FocusRisk,
    val source: DecisionSource,
    val category: String,
    val reasons: List<DecisionReason>,
    val signals: Signals?,
    val link: LinkAssessment,
    val sensitive: Boolean,
    val evidence: Evidence?,
    val matchedRule: String? = null,
    /** What the model pipeline alone would have done; differs from [routing] when a rule or safety check overrode it. */
    val modelRouting: Routing? = null,
    val modelVersion: String? = null
) {
    val overrodeModel get() = modelRouting != null && modelRouting != routing && (source == DecisionSource.USER_RULE || source == DecisionSource.SAFETY)
}

enum class RuleKind { APP, CONTACT, KEYWORD, CATEGORY }
enum class RuleAction { ALLOW, DEFER }
data class UserRule(val id: String, val kind: RuleKind, val value: String, val action: RuleAction, val label: String = value)

enum class OutsideFocus { IGNORE, OBSERVE }

data class DecisionSettings(
    val aiMode: Boolean,
    val policy: PolicySettings,
    val outsideFocus: OutsideFocus = OutsideFocus.IGNORE
)

data class DecisionInput(
    val notification: IncomingNotification,
    /** Category from the app-package lookup ("unknown" if not known). */
    val packageCategory: String,
    val phase: SessionPhase,
    /** Hashed sender identity (see SenderKey), never the raw name. */
    val senderKey: String? = null
)
