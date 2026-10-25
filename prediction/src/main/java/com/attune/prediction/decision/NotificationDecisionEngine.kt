package com.attune.prediction.decision

import com.attune.prediction.classifier.*
import com.attune.prediction.model.SessionPhase
import com.attune.prediction.policy.NotificationPolicy
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * The single place where a notification's fate is decided. Priority order:
 *   0. system exclusions (Attune's own, ongoing, group summaries) and session phase
 *   1. explicit ALLOW rules (allowed apps, priority contacts, keyword/category allow rules, calls & alarms)
 *   2. strong evidence of urgent or security content (OTP, account alerts, emergencies, repeated calls)
 *   3. explicit DEFER rules — but never for content the model rates as clearly important (kept visible instead)
 *   4. the on-device model with feedback personalization (AI Mode), or category rules (AI Mode off / model unavailable)
 *   5. uncertain cases → REVIEW: the original stays visible and a copy is listed for review
 * No raw text leaves this function; reasons are generic and safe to store.
 */
class NotificationDecisionEngine(private val classifier: NotificationClassifier?) {
    val modelAvailable get() = classifier != null
    val modelVersion get() = classifier?.modelVersion

    fun decide(input: DecisionInput, settings: DecisionSettings, rules: List<UserRule>, personalization: PersonalizationProfile, ownPackage: String): NotificationDecision {
        val n = input.notification
        val cat = NotificationPolicy.categorize(input.packageCategory, n.androidCategory)
        val link = LinkAnalyzer.assess(n.text)
        val sensitive = SafetyRules.isSensitive(n.title, n.text, cat)
        fun base(routing: Routing, risk: FocusRisk, source: DecisionSource, reasons: List<DecisionReason>, signals: Signals? = null,
                 evidence: Evidence? = null, rule: String? = null, modelRouting: Routing? = null) =
            NotificationDecision(routing, risk, source, cat, reasons + linkReasons(link), signals, link, sensitive, evidence, rule, modelRouting, classifier?.modelVersion?.takeIf { signals != null })

        // 0. system exclusions
        when {
            n.packageName == ownPackage -> return base(Routing.IGNORE, FocusRisk.UNKNOWN, DecisionSource.SYSTEM, listOf(DecisionReason(ReasonKind.CONTEXT, "Attune's own notification")))
            n.isOngoing || !n.isClearable -> return base(Routing.IGNORE, FocusRisk.UNKNOWN, DecisionSource.SYSTEM, listOf(DecisionReason(ReasonKind.CONTEXT, "Ongoing notifications (music, navigation, calls) are left alone")))
            n.isGroupSummary -> return base(Routing.IGNORE, FocusRisk.UNKNOWN, DecisionSource.SYSTEM, listOf(DecisionReason(ReasonKind.CONTEXT, "Group summary; the individual notifications are evaluated instead")))
        }

        if (input.phase == SessionPhase.NO_SESSION && settings.outsideFocus == OutsideFocus.IGNORE)
            return base(Routing.IGNORE, FocusRisk.UNKNOWN, DecisionSource.NOT_IN_FOCUS, listOf(DecisionReason(ReasonKind.CONTEXT, "No focus session running")))

        // Model signals (computed even when a rule decides, so the UI can show whether a rule overrode the model).
        var modelError = false
        val signals: Signals? = if (settings.aiMode && classifier != null) {
            try {
                val p = classifier.classify(n.title, n.text, cat)
                applyBias(p, personalization.biasFor(n.packageName, cat, input.senderKey).total)
            } catch (e: Exception) { modelError = true; null }
        } else null
        val bias = if (signals != null) personalization.biasFor(n.packageName, cat, input.senderKey) else null
        val outcome = signals?.let { RiskPolicy.route(it, classifier!!.thresholds, n.title, n.text, cat) }
        val riskGuess = outcome?.risk ?: RiskPolicy.categoryRisk(cat)

        if (input.phase != SessionPhase.FOCUS) {
            val why = if (input.phase == SessionPhase.PAUSED_OR_BREAK) "Session paused — notifications reach you normally" else "No focus session — observed only, never held"
            return base(Routing.DELIVER, riskGuess, DecisionSource.NOT_IN_FOCUS, listOf(DecisionReason(ReasonKind.CONTEXT, why)), signals, modelRouting = outcome?.routing)
        }

        val p = settings.policy
        // 1. explicit ALLOW rules
        val allowRule: String? = when {
            n.packageName in p.allowedPackages -> "You allowed this app to interrupt focus"
            p.allowCallsAndAlarms && (n.androidCategory == "call" || n.androidCategory == "alarm") -> "Calls and alarms are allowed"
            NotificationPolicy.matchesPriorityContact(n.title, p.priorityContacts) != null -> "Matches a priority contact you added"
            else -> rules.firstOrNull { it.action == RuleAction.ALLOW && matches(it, n.packageName, cat, n.title, n.text) }?.let { "Matches your rule: ${it.label}" }
        }
        if (allowRule != null) return base(Routing.DELIVER, riskGuess, DecisionSource.USER_RULE,
            listOf(DecisionReason(ReasonKind.RULE, allowRule)), signals, rule = allowRule, modelRouting = outcome?.routing)

        // 2. strong evidence
        val ev = SafetyRules.strongEvidence(n.title, n.text, cat)?.takeIf { it != Evidence.OTP || p.allowOtp }
        if (ev != null) return base(Routing.DELIVER, if ((signals?.distracting ?: 0.0) >= 0.5) FocusRisk.MEDIUM else FocusRisk.LOW, DecisionSource.SAFETY,
            listOf(DecisionReason(ReasonKind.SAFETY, ev.label + " — always allowed through")), signals, ev, modelRouting = outcome?.routing)

        // 3. explicit DEFER rules (subject to the importance safeguard)
        val deferRule = rules.firstOrNull { it.action == RuleAction.DEFER && matches(it, n.packageName, cat, n.title, n.text) }
        if (deferRule != null) {
            val looksImportant = signals != null && (signals.pHigh >= classifier!!.thresholds.allowImportant || signals.urgent >= classifier.thresholds.allowUrgent)
            return if (looksImportant) base(Routing.REVIEW, riskGuess, DecisionSource.USER_RULE,
                listOf(DecisionReason(ReasonKind.RULE, "Your rule “${deferRule.label}” would hold this"),
                    DecisionReason(ReasonKind.SAFETY, "…but the content looks important, so it stays visible")) + modelReasons(signals!!, outcome!!, bias),
                signals, rule = deferRule.label, modelRouting = outcome.routing)
            else base(Routing.DEFER, riskGuess, DecisionSource.USER_RULE, listOf(DecisionReason(ReasonKind.RULE, "Matches your rule: ${deferRule.label}")),
                signals, rule = deferRule.label, modelRouting = outcome?.routing)
        }
        if (cat == "promotion" && p.filterPromotions) return base(Routing.FILTER, FocusRisk.HIGH, DecisionSource.USER_RULE,
            listOf(DecisionReason(ReasonKind.RULE, "Marked promotional by the app; filtered by your setting")), signals, rule = "Filter promotions", modelRouting = outcome?.routing)

        // 4. model (AI Mode) …
        if (signals != null && outcome != null) {
            val src = when {
                outcome.routing == Routing.REVIEW -> DecisionSource.UNCERTAIN_FALLBACK
                bias != null && !bias.isZero -> DecisionSource.PERSONALIZED_MODEL
                else -> DecisionSource.MODEL
            }
            return base(outcome.routing, outcome.risk, src, modelReasons(signals, outcome, bias), signals)
        }
        // … or category rules when AI Mode is off or the model failed
        val queued = cat in p.queuedCategories
        val src = if (settings.aiMode) DecisionSource.MODEL_UNAVAILABLE else DecisionSource.RULES_MODE
        val reasons = buildList {
            if (settings.aiMode) add(DecisionReason(ReasonKind.FALLBACK, if (modelError) "The AI model failed on this notification; category rules were used" else "The AI model isn't available; category rules were used"))
            add(DecisionReason(ReasonKind.RULE, if (queued) "${NotificationPolicy.displayCategory(cat)} is set to wait during focus" else "${NotificationPolicy.displayCategory(cat)} is not set to wait"))
            add(DecisionReason(ReasonKind.CONTEXT, "Risk estimated from the app category only"))
        }
        return base(if (queued) Routing.DEFER else Routing.DELIVER, RiskPolicy.categoryRisk(cat), src, reasons)
    }

    /** Words in this notification that pushed the model towards "important" (display only; never stored). */
    fun explainTerms(title: String?, text: String?, category: String?, n: Int = 4): List<String> =
        (classifier as? LogRegClassifier)?.topImportanceTerms(title, text, category, n) ?: emptyList()

    companion object {
        fun matches(r: UserRule, pkg: String, cat: String, title: String?, text: String?): Boolean = when (r.kind) {
            RuleKind.APP -> r.value == pkg
            RuleKind.CATEGORY -> r.value == cat
            RuleKind.CONTACT -> NotificationPolicy.matchesPriorityContact(title, setOf(r.value)) != null
            RuleKind.KEYWORD -> r.value.isNotBlank() && Regex("""(^|\W)${Regex.escape(r.value.trim().lowercase())}($|\W)""").containsMatchIn(((title ?: "") + " " + (text ?: "")).lowercase())
        }

        /** Shifts the importance logits by [bias] (towards "high" when positive) and distraction the other way at half strength. */
        fun applyBias(p: SignalProbabilities, bias: Double): Signals {
            var imp = p.importance
            var dis = p.distracting
            if (bias != 0.0) {
                val z = doubleArrayOf(ln(imp[0].coerceAtLeast(1e-9)) - bias, ln(imp[1].coerceAtLeast(1e-9)), ln(imp[2].coerceAtLeast(1e-9)) + bias)
                val m = z.max(); val e = z.map { exp(it - m) }; val t = e.sum()
                imp = DoubleArray(3) { e[it] / t }
                val dl = ln(dis.coerceIn(1e-9, 1 - 1e-9) / (1 - dis.coerceIn(1e-9, 1 - 1e-9))) - 0.5 * bias
                dis = 1 / (1 + exp(-dl))
            }
            val level = when (imp.indices.maxBy { imp[it] }) { 0 -> ImportanceLevel.LOW; 1 -> ImportanceLevel.MEDIUM; else -> ImportanceLevel.HIGH }
            return Signals(level, imp[0], imp[1], imp[2], p.urgent, dis, p.actionRequired, imp.max(), bias)
        }

        private fun pct(v: Double) = "${(v * 100).roundToInt()}%"

        fun modelReasons(s: Signals, o: PolicyOutcome, bias: PersonalBias?): List<DecisionReason> = buildList {
            when (o.basis) {
                ModelBasis.STRONG_EVIDENCE -> add(DecisionReason(ReasonKind.SAFETY, "Contains strong urgency or security evidence"))
                ModelBasis.LINK_ONLY -> add(DecisionReason(ReasonKind.FALLBACK, "Only a link — not enough to judge, so it stays visible for you to check"))
                ModelBasis.INSUFFICIENT_CONTENT -> add(DecisionReason(ReasonKind.FALLBACK, "Very short message — too little to judge, so it stays visible"))
                ModelBasis.LOW_CONFIDENCE -> add(DecisionReason(ReasonKind.FALLBACK, "The model isn't sure (top score ${pct(s.confidence)}) — kept visible rather than held"))
                ModelBasis.POSSIBLY_IMPORTANT -> add(DecisionReason(ReasonKind.FALLBACK, "Might be important (${pct(s.pHigh)}) — kept visible rather than held"))
                ModelBasis.IMPORTANT_OR_URGENT -> add(DecisionReason(ReasonKind.MODEL, if (s.urgent >= s.pHigh) "Reads as time-sensitive (${pct(s.urgent)})" else "Reads as important (${pct(s.pHigh)})"))
                ModelBasis.CONFIDENT_DEFER -> add(DecisionReason(ReasonKind.MODEL, "Reads as ${s.importance.name.lowercase()} importance (${pct(s.confidence)}) and not urgent"))
            }
            if (s.distracting >= 0.5) add(DecisionReason(ReasonKind.MODEL, "Likely to pull you into scrolling or chatting (${pct(s.distracting)})"))
            if (s.actionRequired >= 0.5) add(DecisionReason(ReasonKind.MODEL, "Seems to ask you to do something (${pct(s.actionRequired)})"))
            if (bias != null && !bias.isZero) add(DecisionReason(ReasonKind.PERSONAL,
                if (bias.total > 0) "Your earlier feedback raised its importance" else "Your earlier feedback lowered its importance"))
        }

        fun linkReasons(l: LinkAssessment): List<DecisionReason> = when (l.safety) {
            LinkSafety.NONE -> emptyList()
            LinkSafety.UNKNOWN -> listOf(DecisionReason(ReasonKind.LINK, "Contains a link Attune can't verify. Links are never opened or checked online"))
            LinkSafety.CAUTION -> l.signals.map { DecisionReason(ReasonKind.LINK, LinkAnalyzer.describe(it)) }
        }
    }
}
