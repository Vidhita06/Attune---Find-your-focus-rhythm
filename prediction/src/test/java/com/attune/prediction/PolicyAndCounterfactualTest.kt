package com.attune.prediction

import com.attune.prediction.model.SessionPhase
import com.attune.prediction.policy.*
import org.junit.Assert.*
import org.junit.Test

class PolicyAndCounterfactualTest {
    private val settings = PolicySettings(
        queuedCategories = setOf("social", "messaging", "promotion", "unknown"),
        allowedPackages = setOf("com.bank"),
        priorityContacts = setOf("Mom"),
        allowCallsAndAlarms = true, allowOtp = true, filterPromotions = false
    )
    private fun n(pkg: String = "com.instagram.android", title: String? = "Alex liked your photo", text: String? = null, cat: String? = null, ongoing: Boolean = false, summary: Boolean = false) =
        IncomingNotification(pkg, title, text, cat, ongoing, summary, !ongoing)

    @Test fun socialIsQueued() = assertEquals(Verdict.QUEUE, NotificationPolicy.decide("com.attune.app", n(), "social", settings).verdict)
    @Test fun ownAppIgnored() = assertEquals(Verdict.IGNORE, NotificationPolicy.decide("com.attune.app", n(pkg = "com.attune.app"), "unknown", settings).verdict)
    @Test fun ongoingIgnored() = assertEquals(Verdict.IGNORE, NotificationPolicy.decide("x", n(ongoing = true), "social", settings).verdict)
    @Test fun groupSummaryIgnored() = assertEquals(Verdict.IGNORE, NotificationPolicy.decide("x", n(summary = true), "social", settings).verdict)
    @Test fun allowedAppPasses() = assertEquals(Verdict.ALLOW, NotificationPolicy.decide("x", n(pkg = "com.bank"), "finance", settings).verdict)
    @Test fun callsPass() = assertEquals(Verdict.ALLOW, NotificationPolicy.decide("x", n(cat = "call"), "messaging", settings).verdict)
    @Test fun priorityContactPasses() = assertEquals(Verdict.ALLOW, NotificationPolicy.decide("x", n(pkg = "com.whatsapp", title = "Mom"), "messaging", settings).verdict)
    @Test fun priorityContactNeedsWordMatch() = assertNull(NotificationPolicy.matchesPriorityContact("Momentum Gym", setOf("Mom")))
    @Test fun otpPasses() = assertEquals(Verdict.ALLOW, NotificationPolicy.decide("x", n(pkg = "com.sms", title = "Bank", text = "Your OTP is 482913"), "unknown", settings).verdict)
    @Test fun digitsAloneAreNotOtp() = assertFalse(NotificationPolicy.looksLikeOtp("Order 123456", "has shipped"))
    @Test fun otpDisabledQueues() = assertEquals(Verdict.QUEUE, NotificationPolicy.decide("x", n(pkg = "com.sms", text = "Your OTP is 482913"), "unknown", settings.copy(allowOtp = false)).verdict)
    @Test fun promoFilteredOnlyWhenEnabled() {
        assertEquals(Verdict.QUEUE, NotificationPolicy.decide("x", n(cat = "promo"), "shopping", settings).verdict)
        assertEquals(Verdict.FILTER, NotificationPolicy.decide("x", n(cat = "promo"), "shopping", settings.copy(filterPromotions = true)).verdict)
    }
    @Test fun unqueuedCategoryAllowed() = assertEquals(Verdict.ALLOW, NotificationPolicy.decide("x", n(), "health", settings).verdict)

    @Test fun delayDuringFocusEstimatesProtectedTime() {
        val r = CounterfactualEstimator.estimate(CounterfactualInput(Decision.DELAYED, SessionPhase.FOCUS, 10f, 5f, true, 0.1f))
        assertEquals(15f, r.estimatedMinutesProtected!!, 0.001f)
        assertTrue(r.uncertainty.contains("cannot show cause"))
    }
    @Test fun delayOutsideFocusProtectsNothing() {
        val r = CounterfactualEstimator.estimate(CounterfactualInput(Decision.DISMISSED, SessionPhase.NO_SESSION, 10f, 5f, true, 0.1f))
        assertEquals(0f, r.estimatedMinutesProtected!!, 0.001f)
    }
    @Test fun openedHasNoProtectedEstimateAndReportsMeasurement() {
        val r = CounterfactualEstimator.estimate(CounterfactualInput(Decision.OPENED, SessionPhase.FOCUS, 10f, 5f, false, 0.5f, measuredAwayMin = 7f))
        assertNull(r.estimatedMinutesProtected)
        assertTrue(r.observed.contains("upper bound"))
    }
}
