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
        assertEquals(LinkSafety.CAUTION, d.link.safety) // a shortener hides the destination; still never "safe"
        assertTrue(d.reasons.any { it.kind == ReasonKind.LINK })
    }

    @Test fun unknownUrlIsUnknownAndSuspiciousUrlIsCaution() {
        assertEquals(LinkSafety.UNKNOWN, LinkAnalyzer.assess("see https://docs.google.com/document/d/abc").safety)
        assertEquals(LinkSafety.UNKNOWN, LinkAnalyzer.assess("see example.org/page").safety)
        val c = LinkAnalyzer.assess("update KYC at http://hdfc-secure-verify.top/kyc/1")
        assertEquals(LinkSafety.HIGH_RISK, c.safety); assertTrue("risky_tld" in c.notes); assertTrue("brand_lookalike" in c.signals)
        assertEquals(LinkSafety.HIGH_RISK, LinkAnalyzer.assess("http://192.168.4.21/login").safety)
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
        val none = decide("com.amazon.mShop.android.shopping", "shopping", "Amazon", "Your wishlist item is on sale this weekend", NotificationDecisionEngine(null))
        assertEquals(Routing.DEFER, none.routing); assertEquals(DecisionSource.MODEL_UNAVAILABLE, none.source); assertNull(none.signals)
        val failing = decide("com.google.android.calendar", "productivity", "Calendar", "Standup in 5 minutes", NotificationDecisionEngine(Fake(doubleArrayOf(1.0, 0.0, 0.0), fail = true)))
        assertEquals(Routing.DELIVER, failing.routing); assertEquals(DecisionSource.MODEL_UNAVAILABLE, failing.source)
        assertTrue(failing.reasons.any { it.kind == ReasonKind.FALLBACK && "failed" in it.text })
    }

    @Test fun modelFailureKeepsPersonalCategoriesVisible() {
        val chat = decide("com.whatsapp", "messaging", "Rahul", "are you coming later", NotificationDecisionEngine(Fake(doubleArrayOf(1.0, 0.0, 0.0), fail = true)))
        assertEquals(Routing.REVIEW, chat.routing); assertEquals(DecisionSource.MODEL_UNAVAILABLE, chat.source); assertEquals(FocusRisk.UNKNOWN, chat.risk)
        assertEquals(Routing.REVIEW, decide("com.google.android.gm", "email", "Registrar", "documents needed", NotificationDecisionEngine(null)).routing)
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

    // ---- Phase 1: protection of possibly important messages ----

    private fun nx(title: String?, text: String?, burst: Int = 1, priority: Boolean = false, conv: String? = null) =
        IncomingNotification("com.whatsapp", title, text, "msg", false, false, true, conv, priority, burst)
    private fun decideX(n: IncomingNotification, engine: NotificationDecisionEngine = NotificationDecisionEngine(confidentLow), settings: DecisionSettings = ai,
                        rules: List<UserRule> = emptyList()) =
        engine.decide(DecisionInput(n, "messaging", SessionPhase.FOCUS, SenderKey.of(n.packageName, n.title)), settings, rules, PersonalizationProfile.EMPTY, "com.attune.app")

    @Test fun confidentDistractionIsStillHeld() {
        assertEquals(Routing.DEFER, decideX(nx("Group", "lol that meme is hilarious, send more of those later")).routing)
    }

    @Test fun priorityConversationIsAllowedEvenWhenModelSaysHold() {
        val d = decideX(nx("Rahul", "lol that meme is hilarious, send more of those later", priority = true))
        assertEquals(Routing.DELIVER, d.routing); assertEquals(DecisionSource.USER_RULE, d.source)
    }

    @Test fun missingConversationMetadataFallsBackToNormalRouting() {
        val d = decideX(nx("Rahul", "lol that meme is hilarious, send more of those later", priority = false, conv = null))
        assertEquals(Routing.DEFER, d.routing)
    }

    @Test fun possiblyUrgentWordingIsReviewedNotHeld() {
        for (t in listOf("call me now when you see this", "please call me asap", "urgent, need the file today", "jaldi call karo", "abhi call karo please", "madad chahiye jaldi")) {
            val d = decideX(nx("Rahul", t))
            assertEquals(t, Routing.REVIEW, d.routing); assertEquals(t, DecisionSource.SAFETY, d.source)
        }
    }

    @Test fun urgentWordInPromotionIsNotTreatedAsUrgent() {
        val d = decideX(n("com.amazon.mShop.android.shopping", "Amazon", "Urgent: sale ends tonight, hurry"), settings = ai).let {
            NotificationDecisionEngine(confidentLow).decide(DecisionInput(n("com.amazon.mShop.android.shopping", "Amazon", "Urgent: sale ends tonight, hurry"), "shopping", SessionPhase.FOCUS), ai, emptyList(), PersonalizationProfile.EMPTY, "com.attune.app") }
        assertEquals(Routing.DEFER, d.routing)
    }

    @Test fun userHoldRuleDoesNotHoldPossiblyUrgentText() {
        val rules = listOf(UserRule("r", RuleKind.APP, "com.whatsapp", RuleAction.DEFER, "Hold WhatsApp"))
        assertEquals(Routing.REVIEW, decideX(nx("Rahul", "call me now"), rules = rules).routing)
        assertEquals(Routing.DEFER, decideX(nx("Rahul", "see you at the cafe tomorrow evening"), rules = rules).routing)
    }

    @Test fun burstFromOneConversationIsReviewedNotHeld() {
        assertEquals(Routing.DEFER, decideX(nx("Rahul", "lol that meme is hilarious, send more of those later", burst = 2, conv = "c1")).routing)
        val d = decideX(nx("Rahul", "lol that meme is hilarious, send more of those later", burst = 3, conv = "c1"))
        assertEquals(Routing.REVIEW, d.routing)
    }

    @Test fun emptyOrRedactedContentIsNeverHeld() {
        val noModel = NotificationDecisionEngine(null)
        for (t in listOf(null, "", "   ", "Sensitive notification content hidden", "New message", "2 new messages")) {
            assertEquals("ai on: $t", Routing.REVIEW, decideX(nx("Rahul", t), noModel).routing)
            assertEquals("rules mode: $t", Routing.REVIEW, decideX(nx("Rahul", t), noModel, settings = ai.copy(aiMode = false)).routing)
            assertEquals("model: $t", Routing.REVIEW, decideX(nx("Rahul", t)).routing)
        }
        assertTrue(SafetyRules.isRedactedOrEmpty("Contents hidden")); assertFalse(SafetyRules.isRedactedOrEmpty("new message from the bank about your loan"))
    }

    @Test fun modelFailureNeverHoldsSocialOrMessagingButMayHoldPromotionsWhenModelMissing() {
        val failing = NotificationDecisionEngine(Fake(doubleArrayOf(1.0, 0.0, 0.0), fail = true))
        for (cat in listOf("social", "messaging", "shopping", "news"))
            assertEquals(cat, Routing.REVIEW, failing.decide(DecisionInput(n("p", "Title", "some longer text for the notification"), cat, SessionPhase.FOCUS), ai, emptyList(), PersonalizationProfile.EMPTY, "x").routing)
        assertEquals(Routing.REVIEW, NotificationDecisionEngine(null).decide(DecisionInput(n("p", "Title", "some longer text for the notification"), "social", SessionPhase.FOCUS), ai, emptyList(), PersonalizationProfile.EMPTY, "x").routing)
    }

    @Test fun linkDoesNotChangeWhatTheModelSees() {
        val seen = mutableListOf<String?>()
        val spy = object : NotificationClassifier {
            override val modelVersion = "spy"; override val thresholds = RoutingThresholds()
            override fun classify(title: String?, text: String?, category: String?): SignalProbabilities { seen += text; return SignalProbabilities(doubleArrayOf(0.9, 0.08, 0.02), 0.05, 0.9, 0.1, "spy") }
        }
        decideX(nx("Rahul", "check this out when you are free https://example.com/x?y=1"), NotificationDecisionEngine(spy))
        assertEquals("check this out when you are free", seen.single()?.trim())
        assertEquals(Routing.REVIEW, decideX(nx("Rahul", "https://example.com/x"), NotificationDecisionEngine(spy)).routing) // link-only stays visible
    }

    @Test fun senderKeyIsKeyedNotAPlainHash() {
        val k = SenderKey.of("com.whatsapp", "Rahul")!!
        assertEquals(k, SenderKey.of("com.whatsapp", " rahul "))
        val plain = java.security.MessageDigest.getInstance("SHA-256").digest("com.whatsapp|rahul".toByteArray()).take(12).joinToString("") { "%02x".format(it) }
        assertNotEquals(plain, k)
        try { SenderKey.install { null }; assertNull(SenderKey.of("com.whatsapp", "Rahul")) } finally { SenderKey.install(SenderHasher { d -> javax.crypto.Mac.getInstance("HmacSHA256").run { init(javax.crypto.spec.SecretKeySpec(ByteArray(32) { 7 }, "HmacSHA256")); doFinal(d) } }) }
    }

    // ---- Phase 2: hybrid policy, sender preferences, escalation, link independence ----

    private val meme = "lol that meme is hilarious, send more of those later"
    private fun withFlags(n: IncomingNotification, held: Boolean = false, cat: String? = null) = n.copy(previouslyHeld = held, androidCategory = cat ?: n.androidCategory)

    @Test fun senderPreferenceLevelsAreExplicitAndDistinct() {
        val e = NotificationDecisionEngine(confidentLow)
        fun rule(a: RuleAction) = listOf(UserRule("r", RuleKind.CONTACT, "Rahul", a, "Rahul"))
        assertEquals("normal", Routing.DEFER, decideX(nx("Rahul", meme), e).routing)
        assertEquals("always allow", Routing.DELIVER, decideX(nx("Rahul", meme), e, rules = rule(RuleAction.ALLOW)).routing)
        assertEquals("defer eligible distractions", Routing.DEFER, decideX(nx("Rahul", meme), e, rules = rule(RuleAction.DEFER)).routing)
        val pr = decideX(nx("Rahul", meme), e, rules = rule(RuleAction.PRIORITY))
        assertEquals("priority: kept visible, not delivered as an emergency", Routing.REVIEW, pr.routing)
        assertEquals(DecisionSource.USER_RULE, pr.source); assertNotEquals(DecisionSource.SAFETY, pr.source)
        assertEquals("other senders unaffected", Routing.DEFER, decideX(nx("Priya", meme), e, rules = rule(RuleAction.PRIORITY)).routing)
    }

    @Test fun priorityBeatsAHoldRuleForTheSameSender() {
        val rules = listOf(UserRule("a", RuleKind.CONTACT, "Rahul", RuleAction.DEFER, "d"), UserRule("b", RuleKind.CONTACT, "Rahul", RuleAction.PRIORITY, "p"))
        assertEquals(Routing.REVIEW, decideX(nx("Rahul", meme), rules = rules).routing)
    }

    @Test fun priorityContactsOrdinaryMessageIsNotAnEmergency() {
        val d = decideX(nx("Mom", "did you eat lunch? also the gas bill is due next week"), rules = listOf(UserRule("p", RuleKind.CONTACT, "Mom", RuleAction.PRIORITY, "Mom")))
        assertNull(d.evidence); assertNotEquals(DecisionSource.SAFETY, d.source); assertNotEquals(FocusRisk.HIGH, d.risk.takeIf { false })
        assertTrue(d.reasons.none { it.text.contains("emergency", true) })
    }

    @Test fun followUpToAJustHeldConversationIsReviewedSoonerThanABurst() {
        val e = NotificationDecisionEngine(confidentLow)
        assertEquals(Routing.DEFER, decideX(withFlags(nx("Rahul", meme, burst = 2)), e).routing)
        assertEquals(Routing.REVIEW, decideX(withFlags(nx("Rahul", meme, burst = 2), held = true), e).routing)
        assertEquals("first message is still held", Routing.DEFER, decideX(withFlags(nx("Rahul", meme, burst = 1), held = true), e).routing)
    }

    @Test fun reminderAndEventCategoriesAreNotHeldOnModelEvidence() {
        assertEquals(Routing.REVIEW, decideX(withFlags(nx("Calendar", "Team sync moved to the other room at some point today"), cat = "event")).routing)
    }

    @Test fun suspiciousLinkNeverChangesTheImportanceScores() {
        val plain = decide("com.whatsapp", "messaging", "Rahul", "please check the attendance sheet when you are free")
        val benign = decide("com.whatsapp", "messaging", "Rahul", "please check the attendance sheet when you are free https://docs.google.com/spreadsheets/d/1")
        val phish = decide("com.whatsapp", "messaging", "Rahul", "please check the attendance sheet when you are free http://hdfc-secure-verify.top/kyc/1")
        for (d in listOf(benign, phish)) {
            assertEquals(plain.signals!!.pHigh, d.signals!!.pHigh, 1e-12); assertEquals(plain.signals!!.urgent, d.signals!!.urgent, 1e-12)
            assertEquals(plain.signals!!.distracting, d.signals!!.distracting, 1e-12)
        }
        assertEquals(LinkSafety.HIGH_RISK, phish.link.safety); assertEquals(LinkSafety.UNKNOWN, benign.link.safety)
    }

    @Test fun highRiskLinkOnlyEverRaisesVisibilityNeverCausesAHold() {
        val e = NotificationDecisionEngine(confidentLow)
        val phish = decideX(nx("Bank Alerts", "your account needs attention http://hdfc-secure-verify.top/kyc/1"), e)
        assertEquals(Routing.REVIEW, phish.routing); assertEquals(LinkSafety.HIGH_RISK, phish.link.safety)
        // identical text, harmless link or none: the routing comes from the model alone
        assertEquals(Routing.DEFER, decideX(nx("Bank Alerts", "your account needs attention https://docs.google.com/x"), e).routing)
        // a caution link alone does not change a model decision either way
        assertEquals(Routing.DEFER, decideX(nx("Bank Alerts", "your account needs attention https://bit.ly/abc"), e).routing)
        val noPhish = decideX(nx("Rahul", "see you later at the cafe"), NotificationDecisionEngine(Fake(doubleArrayOf(0.02, 0.08, 0.9), urg = 0.9, dis = 0.1)))
        assertEquals(Routing.DELIVER, noPhish.routing)
        val important = decideX(nx("Prof", "assignment deadline moved to today 5pm http://hdfc-secure-verify.top/kyc/1"), NotificationDecisionEngine(Fake(doubleArrayOf(0.02, 0.08, 0.9), urg = 0.9, dis = 0.1)))
        assertEquals("a bad link doesn't make an important message hold-able", Routing.DELIVER, important.routing)
    }

    @Test fun fakeUrgencyInPromotionsIsNotTreatedAsImportant() {
        for (t in listOf("URGENT!! 50% off sale ends tonight, last chance", "Hurry, immediately claim your offer: flat 70% off", "ASAP: limited time deal, buy now"))
            assertFalse(t, SafetyRules.urgencyHint("Brand", t, "messaging"))
        val d = decide("com.amazon.mShop.android.shopping", "shopping", "Amazon", "URGENT!! 50% off sale ends tonight, last chance to buy")
        assertNotEquals(Routing.DELIVER, d.routing); assertNull(d.evidence)
    }

    @Test fun urgentWordsWithRealContextStillStayVisibleButAreNotCalledEmergencies() {
        val d = decideX(nx("Aman", "urgent: need the signed form before 3, call me"))
        assertEquals(Routing.REVIEW, d.routing); assertNull(d.evidence)
    }

    @Test fun importantMessageWithoutUrgencyKeywordsIsNotSilentlyHeld() {
        // no "urgent/asap/call" style words and unfamiliar phrasing: the real model plus REVIEW fallback must not hold it
        for (t in listOf("Your visa interview slot has been rescheduled to Thursday 9:30 at the consulate, carry the original documents",
            "The lab report shows your sample needs a repeat test, please book an appointment with Dr Mehta this week",
            "Aapka interview kal subah 9 baje hai, documents saath le aana"))
            assertNotEquals(t, Routing.DEFER, decide("com.whatsapp", "messaging", "Contact", t).routing)
    }

    @Test fun unknownIsNeverReadAsLowImportance() {
        val unknownSender = decide("com.example.newapp", "unknown", null, "your order has a problem, tap to fix", engine = NotificationDecisionEngine(null))
        assertNotEquals(Routing.DEFER, unknownSender.routing)
        assertNotEquals(Routing.DEFER, decideX(nx(null, null), NotificationDecisionEngine(Fake(doubleArrayOf(0.9, 0.08, 0.02)))).routing)
    }

    @Test fun aiModeOffStillKeepsSafetyProtections() {
        val off = ai.copy(aiMode = false)
        assertEquals(Routing.DELIVER, decide("com.whatsapp", "messaging", "Bank", "Your OTP is 482913", settings = off).routing)
        assertEquals(Routing.REVIEW, decideX(nx("Rahul", "call me now"), NotificationDecisionEngine(null), settings = off).routing)
        assertEquals(Routing.DEFER, decideX(nx("Rahul", meme), NotificationDecisionEngine(null), settings = off).routing) // the user's own category rules apply
    }

    @Test fun policyThresholdsLiveInOneConfigurableObject() {
        val strict = NotificationDecisionEngine(confidentLow, DecisionPolicy(burstReviewCount = 2))
        assertEquals(Routing.REVIEW, decideX(nx("Rahul", meme, burst = 2), strict).routing)
        val lax = NotificationDecisionEngine(confidentLow, DecisionPolicy(burstReviewCount = 5, reviewOnHighRiskLink = false))
        assertEquals(Routing.DEFER, decideX(nx("Rahul", meme, burst = 4), lax).routing)
        assertEquals(Routing.DEFER, decideX(nx("Rahul", "your account needs attention http://hdfc-secure-verify.top/kyc/1"), lax).routing)
        assertEquals(24, DecisionPolicy().textRetentionHours)
    }

    @Test fun senderFeedbackDoesNotSpreadFullyToTheWholeApp() {
        val withSender = PersonalizationProfile.from(listOf(FeedbackSignal(FeedbackKind.NOT_IMPORTANT, "com.whatsapp", "messaging", "s1")))
        val noSender = PersonalizationProfile.from(listOf(FeedbackSignal(FeedbackKind.NOT_IMPORTANT, "com.whatsapp", "messaging", null)))
        val otherChat = withSender.biasFor("com.whatsapp", "messaging", "s2").total
        assertTrue(Math.abs(otherChat) < Math.abs(noSender.biasFor("com.whatsapp", "messaging", "s2").total))
        assertTrue(Math.abs(withSender.biasFor("com.whatsapp", "messaging", "s1").total) > Math.abs(otherChat))
    }

    @Test fun clearingPersonalizationRestoresTheBaselineDecision() {
        val borderline = Fake(doubleArrayOf(0.25, 0.68, 0.07), 0.1, 0.3, 0.6)
        val e = NotificationDecisionEngine(borderline)
        val prof = PersonalizationProfile.from(List(6) { FeedbackSignal(FeedbackKind.WRONGLY_DEFERRED, "com.whatsapp", "messaging", SenderKey.of("com.whatsapp", "Rahul")) })
        fun run(p: PersonalizationProfile) = e.decide(DecisionInput(nx("Rahul", "please update the sprint board by end of day"), "messaging", SessionPhase.FOCUS, SenderKey.of("com.whatsapp", "Rahul")), ai, emptyList(), p, "x")
        val before = run(PersonalizationProfile.EMPTY)
        assertNotEquals(before.routing, run(prof).routing)       // feedback matters ...
        assertEquals(before.routing, run(PersonalizationProfile.from(emptyList())).routing) // ... and deleting it restores the baseline
        assertEquals(0.0, run(PersonalizationProfile.from(emptyList())).signals!!.personalBias, 0.0)
    }

    // ---- unsure model in a clearly low-value app is held; everything else stays visible ----

    private val unsureDistracting = Fake(doubleArrayOf(0.5, 0.45, 0.05), urg = 0.05, dis = 0.9)  // top-class 0.5 < min confidence 0.6

    @Test fun unsureButDistractingLowValueAppsAreHeld() {
        val d = decide("com.instagram.android", "social", "Instagram", "priya and 8 others posted new reels today", NotificationDecisionEngine(unsureDistracting))
        assertEquals(Routing.DEFER, d.routing)
        assertEquals(Routing.REVIEW, decide("com.instagram.android", "social", "Instagram", "priya and 8 others posted new reels today",
            NotificationDecisionEngine(unsureDistracting, DecisionPolicy(holdUnsureLowValue = false))).routing)
    }

    @Test fun unsureMessagingEmailAndUnknownAppsStayVisible() {
        for ((pkg, cat) in listOf("com.whatsapp" to "messaging", "com.google.android.gm" to "email", "com.example.x" to "unknown"))
            assertEquals(cat, Routing.REVIEW, decide(pkg, cat, "Someone", "are you free later to talk about the plan", NotificationDecisionEngine(unsureDistracting)).routing)
    }

    @Test fun unsureLowValueIsNotHeldWhenItMightBeImportantOrUrgent() {
        val mightMatter = Fake(doubleArrayOf(0.4, 0.4, 0.2), urg = 0.05, dis = 0.9) // P(high)=0.2 >= 0.15
        assertEquals(Routing.REVIEW, decide("com.instagram.android", "social", "Instagram", "priya sent you a message request", NotificationDecisionEngine(mightMatter)).routing)
        assertNotEquals(Routing.DEFER, decide("com.instagram.android", "social", "Instagram", "call me now your account", NotificationDecisionEngine(unsureDistracting)).routing)
    }
}
