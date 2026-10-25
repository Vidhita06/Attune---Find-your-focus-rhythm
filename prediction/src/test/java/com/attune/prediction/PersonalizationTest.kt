package com.attune.prediction

import com.attune.prediction.classifier.RoutingThresholds
import com.attune.prediction.classifier.SignalProbabilities
import com.attune.prediction.decision.*
import org.junit.Assert.*
import org.junit.Test

class PersonalizationTest {
    private val wa = "com.whatsapp"
    private fun sig(kind: FeedbackKind, pkg: String = wa, cat: String = "messaging", sender: String? = null) = FeedbackSignal(kind, pkg, cat, sender)

    @Test fun singleFeedbackIsAModestShift() {
        val p = PersonalizationProfile.from(listOf(sig(FeedbackKind.ACTUALLY_IMPORTANT)))
        val b = p.biasFor(wa, "messaging", null)
        assertEquals(1.0 / 3 + 0.6 / 3, b.total, 1e-9)
        assertTrue(b.total < 0.6)
    }

    @Test fun consistentFeedbackGrowsButIsCapped() {
        val many = PersonalizationProfile.from(List(50) { sig(FeedbackKind.ACTUALLY_IMPORTANT, sender = "s1") })
        val b = many.biasFor(wa, "messaging", "s1")
        assertTrue(b.total > 1.0); assertTrue(b.total <= PersonalizationProfile.MAX_TOTAL + 1e-9)
    }

    @Test fun neutralAndRuleFeedbackDoNotChangeBias() {
        val p = PersonalizationProfile.from(listOf(sig(FeedbackKind.CORRECT), sig(FeedbackKind.ALWAYS_ALLOW_APP), sig(FeedbackKind.ALWAYS_DEFER_CATEGORY)))
        assertTrue(p.preferences.isEmpty()); assertTrue(p.biasFor(wa, "messaging", null).isZero)
    }

    @Test fun mixedFeedbackCancelsOut() {
        val p = PersonalizationProfile.from(listOf(sig(FeedbackKind.ACTUALLY_IMPORTANT), sig(FeedbackKind.NOT_IMPORTANT)))
        assertEquals(0.0, p.biasFor(wa, "messaging", null).total, 1e-9)
    }

    @Test fun feedbackChangesASubsequentBorderlineRecommendation() {
        // borderline: the model leans "medium", not urgent — normally held
        val probs = SignalProbabilities(doubleArrayOf(0.25, 0.68, 0.07), 0.1, 0.3, 0.6, "t")
        val t = RoutingThresholds()
        val before = RiskPolicy.route(NotificationDecisionEngine.applyBias(probs, 0.0), t, "Vikram", "please update the sprint board by end of day", "productivity")
        assertEquals(Routing.DEFER, before.routing)
        val prof = PersonalizationProfile.from(List(6) { sig(FeedbackKind.WRONGLY_DEFERRED, "com.slack", "productivity") })
        val bias = prof.biasFor("com.slack", "productivity", null).total
        val after = RiskPolicy.route(NotificationDecisionEngine.applyBias(probs, bias), t, "Vikram", "please update the sprint board by end of day", "productivity")
        assertNotEquals(Routing.DEFER, after.routing) // no longer silently held
    }

    @Test fun deletingFeedbackRemovesItsEffect() {
        val events = mutableListOf(sig(FeedbackKind.NOT_IMPORTANT), sig(FeedbackKind.NOT_IMPORTANT))
        assertTrue(PersonalizationProfile.from(events).biasFor(wa, "messaging", null).total < 0)
        events.clear() // reset = feedback deleted; the profile is always recomputed from stored feedback
        assertTrue(PersonalizationProfile.from(events).biasFor(wa, "messaging", null).isZero)
    }

    @Test fun senderKeyIsStableHashedAndHintIsMasked() {
        val k = SenderKey.of(wa, " Rahul ")
        assertEquals(k, SenderKey.of(wa, "rahul"))
        assertNotEquals(k, SenderKey.of("org.telegram.messenger", "Rahul"))
        assertFalse(k!!.contains("rahul", ignoreCase = true)); assertEquals(24, k.length)
        assertNull(SenderKey.of(wa, "  "))
        assertEquals("R••••l", SenderKey.hint("Rahul Sharmaal"))
    }
}
