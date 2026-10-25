package com.attune.prediction

import com.attune.prediction.classifier.*
import com.attune.prediction.decision.*
import com.attune.prediction.model.SessionPhase
import com.attune.prediction.policy.IncomingNotification
import com.attune.prediction.policy.PolicySettings
import org.junit.Assert.*
import org.junit.Test

class DecisionEngineTest {
    private val real = NotificationDecisionEngine(ModelParityTest.model)
    private val policy = PolicySettings(setOf("messaging", "social", "short_video", "shopping", "promotion", "news", "email", "unknown"),
        emptySet(), emptySet(), allowCallsAndAlarms = true, allowOtp = true, filterPromotions = false)
    private val ai = DecisionSettings(aiMode = true, policy = policy)

    private fun n(pkg: String, title: String?, text: String?, cat: String? = null, ongoing: Boolean = false) =
        IncomingNotification(pkg, title, text, cat, ongoing, isGroupSummary = false, isClearable = true)
    private fun decide(pkg: String, pkgCat: String, title: String?, text: String?, engine: NotificationDecisionEngine = real, settings: DecisionSettings = ai,
                       rules: List<UserRule> = emptyList(), prof: PersonalizationProfile = PersonalizationProfile.EMPTY, phase: SessionPhase = SessionPhase.FOCUS, androidCat: String? = null) =
        engine.decide(DecisionInput(n(pkg, title, text, androidCat), pkgCat, phase, SenderKey.of(pkg, title)), settings, rules, prof, "com.attune.app")

    /** Deterministic fake model, so rule-ordering tests don't depend on trained weights. */
    private class Fake(val imp: DoubleArray, val urg: Double = 0.05, val dis: Double = 0.9, val act: Double = 0.1, val fail: Boolean = false) : NotificationClassifier {
        override val modelVersion = "fake"; override val thresholds = RoutingThresholds()
        override fun classify(title: String?, text: String?, category: String?): SignalProbabilities {
            if (fail) throw IllegalStateException("boom")
            return SignalProbabilities(imp, urg, dis, act, modelVersion)
        }
    }
    private val confidentLow = Fake(doubleArrayOf(0.9, 0.08, 0.02))
    private val confidentHigh = Fake(doubleArrayOf(0.02, 0.08, 0.9), urg = 0.9, dis = 0.1)

    @Test fun highImportanceDuringFocusIsDelivered() {
        val d = decide("com.whatsapp", "messaging", "Mom", "Nani is in the hospital, please call immediately")
        assertEquals(Routing.DELIVER, d.routing)
    }

    @Test fun lowImportanceDistractionIsDeferredWithHighRisk() {
        val d = decide("com.instagram.android", "social", "Instagram", "Priya liked your photo and 8 others posted today")
        assertEquals(Routing.DEFER, d.routing); assertEquals(FocusRisk.HIGH, d.risk)
        assertTrue(d.source == DecisionSource.MODEL)
        assertNotNull(d.signals)
    }

    @Test fun urgentMessageFromNormallyDistractingSenderIsNotDeferred() {
        // the same friend usually sends memes, but this message is urgent
        val casual = decide("com.whatsapp", "messaging", "Rahul", "lol look at this meme, so funny")
        val urgent = decide("com.whatsapp", "messaging", "Rahul", "bro I got into an accident near the metro station, please call me")
        assertNotEquals(Routing.DELIVER, casual.routing)
        assertEquals(Routing.DELIVER, urgent.routing)
    }

    @Test fun senderHistoryNeverOverridesUrgentContent() {
        val key = SenderKey.of("com.whatsapp", "Rahul")
        val prof = PersonalizationProfile.from(List(10) { FeedbackSignal(FeedbackKind.NOT_IMPORTANT, "com.whatsapp", "messaging", key) })
        val d = decide("com.whatsapp", "messaging", "Rahul", "I'm at the hospital, accident, call me now", prof = prof)
        assertEquals(Routing.DELIVER, d.routing); assertEquals(DecisionSource.SAFETY, d.source)
    }

    @Test fun priorityAppAndContactAreAlwaysDelivered() {
        val e = NotificationDecisionEngine(confidentLow)
        val app = decide("com.instagram.android", "social", "Instagram", "x liked your photo", e, ai.copy(policy = policy.copy(allowedPackages = setOf("com.instagram.android"))))
        assertEquals(Routing.DELIVER, app.routing); assertEquals(DecisionSource.USER_RULE, app.source); assertTrue(app.overrodeModel)
        val contact = decide("com.whatsapp", "messaging", "Mom", "haha look at this", e, ai.copy(policy = policy.copy(priorityContacts = setOf("Mom"))))
        assertEquals(Routing.DELIVER, contact.routing); assertEquals(DecisionSource.USER_RULE, contact.source)
    }

    @Test fun conflictingRulesAllowWins() {
        val rules = listOf(UserRule("1", RuleKind.CATEGORY, "social", RuleAction.DEFER, "Hold social"), UserRule("2", RuleKind.KEYWORD, "exam", RuleAction.ALLOW, "Keyword “exam”"))
        val d = decide("com.instagram.android", "social", "Class group", "exam hall changed to B204", NotificationDecisionEngine(confidentLow), rules = rules)
        assertEquals(Routing.DELIVER, d.routing); assertEquals("Matches your rule: Keyword “exam”", d.matchedRule)
    }

    @Test fun deferRuleHoldsButNotWhenContentLooksImportant() {
        val rules = listOf(UserRule("1", RuleKind.CATEGORY, "messaging", RuleAction.DEFER, "Hold messaging"))
        assertEquals(Routing.DEFER, decide("com.whatsapp", "messaging", "Aman", "what's up", NotificationDecisionEngine(confidentLow), rules = rules).routing)
        val important = decide("com.whatsapp", "messaging", "Aman", "need the signed form today", NotificationDecisionEngine(confidentHigh), rules = rules)
        assertEquals(Routing.REVIEW, important.routing)
    }

    @Test fun ambiguousShortMessageIsKeptVisible() {
        val d = decide("com.whatsapp", "messaging", "Mom", "call me")
        assertEquals(Routing.REVIEW, d.routing); assertEquals(FocusRisk.UNKNOWN, d.risk)
    }

    @Test fun lowConfidencePredictionIsReviewedNotDeferred() {
        val unsure = NotificationDecisionEngine(Fake(doubleArrayOf(0.45, 0.4, 0.15), dis = 0.6))
        val d = decide("com.whatsapp", "messaging", "Zoya", "regarding the thing we discussed earlier today", unsure)
        assertEquals(Routing.REVIEW, d.routing); assertEquals(FocusRisk.UNKNOWN, d.risk); assertEquals(DecisionSource.UNCERTAIN_FALLBACK, d.source)
    }

    @Test fun linkOnlyIsUnknownAndLinkIsNeverCalledSafe() {
        val d = decide("com.whatsapp", "messaging", "Dev", "https://bit.ly/3xYz9")
        assertEquals(Routing.REVIEW, d.routing); assertEquals(FocusRisk.UNKNOWN, d.risk)
        assertEquals(LinkSafety.UNKNOWN, d.link.safety)
        assertTrue(d.reasons.any { it.kind == ReasonKind.LINK })
    }

    @Test fun unknownUrlIsUnknownAndSuspiciousUrlIsCaution() {
        assertEquals(LinkSafety.UNKNOWN, LinkAnalyzer.assess("see https://docs.google.com/document/d/abc").safety)
        assertEquals(LinkSafety.UNKNOWN, LinkAnalyzer.assess("see example.org/page").safety)
        val c = LinkAnalyzer.assess("update KYC at http://hdfc-secure-verify.top/kyc/1")
        assertEquals(LinkSafety.CAUTION, c.safety); assertTrue("risky_tld" in c.signals); assertTrue("brand_lookalike" in c.signals)
        assertEquals(LinkSafety.CAUTION, LinkAnalyzer.assess("http://192.168.4.21/login").safety)
        assertEquals(LinkSafety.NONE, LinkAnalyzer.assess("no links here").safety)
    }

    @Test fun otpAndSecurityAlertsAreDeliveredAndMarkedSensitive() {
        val otp = decide("com.google.android.apps.messaging", "messaging", "AX-HDFCBK", "482913 is your OTP for login. Do not share")
        assertEquals(Routing.DELIVER, otp.routing); assertEquals(Evidence.OTP, otp.evidence); assertTrue(otp.sensitive)
        val sec = decide("com.google.android.gms", "system", "Google", "New sign-in to your account from a Windows device")
        assertEquals(Routing.DELIVER, sec.routing); assertEquals(Evidence.SECURITY, sec.evidence)
        // with the OTP exception switched off, an OTP is no longer forced through by the safety rule
        val off = decide("com.google.android.apps.messaging", "messaging", "AX-HDFCBK", "482913 is your OTP for login", NotificationDecisionEngine(confidentLow), ai.copy(policy = policy.copy(allowOtp = false)))
        assertNotEquals(DecisionSource.SAFETY, off.source)
    }

    @Test fun pausedOrNoSessionNeverDefers() {
        val paused = decide("com.instagram.android", "social", "Instagram", "Priya liked your photo", phase = SessionPhase.PAUSED_OR_BREAK)
        assertEquals(Routing.DELIVER, paused.routing)
        assertEquals(Routing.IGNORE, decide("com.instagram.android", "social", "Instagram", "Priya liked your photo", phase = SessionPhase.NO_SESSION).routing)
        val observe = decide("com.instagram.android", "social", "Instagram", "Priya liked your photo", settings = ai.copy(outsideFocus = OutsideFocus.OBSERVE), phase = SessionPhase.NO_SESSION)
        assertEquals(Routing.DELIVER, observe.routing); assertNotNull(observe.signals)
    }

    @Test fun modelUnavailableOrFailingFallsBackToCategoryRules() {
        val none = decide("com.instagram.android", "social", "Instagram", "Priya liked your photo", NotificationDecisionEngine(null))
        assertEquals(Routing.DEFER, none.routing); assertEquals(DecisionSource.MODEL_UNAVAILABLE, none.source); assertNull(none.signals)
        val failing = decide("com.google.android.calendar", "productivity", "Calendar", "Standup in 5 minutes", NotificationDecisionEngine(Fake(doubleArrayOf(1.0, 0.0, 0.0), fail = true)))
        assertEquals(Routing.DELIVER, failing.routing); assertEquals(DecisionSource.MODEL_UNAVAILABLE, failing.source)
        assertTrue(failing.reasons.any { it.kind == ReasonKind.FALLBACK && "failed" in it.text })
    }

    @Test fun aiModeOffUsesCategoryRulesOnly() {
        val d = decide("com.whatsapp", "messaging", "Mom", "Nani is in the hospital, please call", settings = ai.copy(aiMode = false))
        assertEquals(Routing.DELIVER, d.routing); assertEquals(DecisionSource.SAFETY, d.source) // safety rules still apply
        val chat = decide("com.whatsapp", "messaging", "Mom", "did you eat lunch?", settings = ai.copy(aiMode = false))
        assertEquals(Routing.DEFER, chat.routing); assertEquals(DecisionSource.RULES_MODE, chat.source); assertNull(chat.signals)
    }

    @Test fun systemNotificationsAreIgnored() {
        assertEquals(Routing.IGNORE, real.decide(DecisionInput(n("com.spotify.music", "Song", "Playing", ongoing = true), "entertainment", SessionPhase.FOCUS), ai, emptyList(), PersonalizationProfile.EMPTY, "com.attune.app").routing)
        assertEquals(Routing.IGNORE, decide("com.attune.app", "unknown", "Attune", "Timer").routing)
    }

    @Test fun reasonsNeverContainMessageText() {
        val secret = "my secret plan for saturday involving zebras"
        val d = decide("com.whatsapp", "messaging", "Priya", secret)
        assertTrue(d.reasons.none { r -> secret.split(" ").filter { it.length > 4 }.any { it in r.text } })
    }
}
