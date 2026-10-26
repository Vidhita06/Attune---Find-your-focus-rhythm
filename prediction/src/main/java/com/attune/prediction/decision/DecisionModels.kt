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
/**
 * Per-sender / per-rule behaviour, chosen explicitly by the user:
 *  ALLOW    = "Always allow": reaches you during focus (safety rules still run first for everything else).
 *  PRIORITY = "Priority sender": never held on model evidence; ordinary messages stay visible for review, nothing is called an emergency.
 *  DEFER    = "Defer eligible distractions": held when the model is confident and no safety signal applies.
 *  (no rule) = Normal.
 */
enum class RuleAction { ALLOW, PRIORITY, DEFER }
data class UserRule(val id: String, val kind: RuleKind, val value: String, val action: RuleAction, val label: String = value)

enum class OutsideFocus { IGNORE, OBSERVE }

/**
 * Every tunable of the routing decision in one place (model thresholds ship inside the model file and are chosen on its validation
 * split; see RoutingThresholds). The defaults are conservative: when evidence is thin the answer is REVIEW, which keeps the original visible.
 */
data class DecisionPolicy(
    /** This many messages (including the current one) from one conversation inside [burstWindowMs] turn a HOLD into REVIEW. */
    val burstReviewCount: Int = 3,
    val burstWindowMs: Long = 120_000L,
    /** If Attune held a message from this conversation within this time, the next one needs only [followUpReviewCount] messages to be reviewed. */
    val heldFollowUpWindowMs: Long = 300_000L,
    val followUpReviewCount: Int = 2,
    /** A HIGH_RISK link turns a HOLD into REVIEW so the warning is seen. It never turns anything into HOLD and never changes importance scores. */
    val reviewOnHighRiskLink: Boolean = true,
    /**
     * In clearly low-value app categories (social feed, short video, games, shopping, promotions, entertainment, news) a model that is merely
     * unsure (low top-class confidence) holds the notification when it still reads as distracting and shows no sign of being important
     * (P(high importance) below the model's review threshold). Messaging, email, calls and unknown apps are never affected.
     */
    val holdUnsureLowValue: Boolean = true,
    /** Android notification categories that mean a time-bound item; never held on model evidence alone. */
    val reviewAndroidCategories: Set<String> = setOf("reminder", "event"),
    /** Plain-language retention for stored notification text. */
    val textRetentionHours: Int = 24,
    val maxPendingTextDays: Int = 7
)

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
