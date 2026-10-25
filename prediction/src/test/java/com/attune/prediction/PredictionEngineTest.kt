package com.attune.prediction

import com.attune.prediction.model.*
import org.junit.Assert.*
import org.junit.Test

class PredictionEngineTest {
    private val priors = mapOf(
        "social" to CategoryPrior("social", RiskLevel.HIGH, 15f, 8f, ""),
        "messaging" to CategoryPrior("messaging", RiskLevel.LOW, 4f, 2f, ""),
        "unknown" to CategoryPrior("unknown", RiskLevel.MED, 8f, 4f, "")
    )
    private val engine = PredictionEngine(priors, k = 5)
    private fun input(cat: String, phase: SessionPhase = SessionPhase.FOCUS, elapsed: Float = 5f, hour: Int = 14, group: Int = 1, history: List<UserObservation> = emptyList()) =
        PredictionInput(NotificationContext("pkg", cat, "t", "x", 0L, group), SessionContext(phase, elapsed, 20f), history, hour)

    @Test fun coldStartUsesPriorExactly() {
        val p = engine.predict(input("social"))
        assertEquals(15f, p.timeAwayMin, 0.001f)
        assertEquals(8f, p.recoveryMin, 0.001f)
        assertTrue(p.isColdStart)
        assertEquals(RiskLevel.HIGH, p.risk)
        assertEquals("Cold start", p.confidenceLabel)
    }

    @Test fun isDeterministic() {
        assertEquals(engine.predict(input("social")), engine.predict(input("social")))
    }

    @Test fun timeAwayAndRecoveryAreIndependent() {
        val p = engine.predict(input("social", phase = SessionPhase.NO_SESSION))
        assertEquals(15f, p.timeAwayMin, 0.001f)
        assertEquals(8f * 0.4f, p.recoveryMin, 0.001f)
    }

    @Test fun historyBlendsTowardsPersonalMean() {
        val h = List(5) { UserObservation("social", 5f, 2f) }
        val p = engine.predict(input("social", history = h))
        assertEquals(0.5f * 5f + 0.5f * 15f, p.timeAwayMin, 0.001f)
        assertFalse(p.isColdStart)
        assertEquals(5, p.observationCount)
        assertTrue(p.factors.any { it.source == FactorSource.YOUR_DATA })
    }

    @Test fun otherCategoryHistoryIgnored() {
        val h = List(5) { UserObservation("messaging", 1f, 1f) }
        assertEquals(15f, engine.predict(input("social", history = h)).timeAwayMin, 0.001f)
    }

    @Test fun unknownCategoryFallsBack() {
        val p = engine.predict(input("not_a_category"))
        assertEquals(8f, p.timeAwayMin, 0.001f)
    }

    @Test fun riskThresholds() {
        assertEquals(RiskLevel.LOW, engine.predict(input("messaging")).risk)
        assertEquals(RiskLevel.MED, engine.predict(input("unknown")).risk)
    }

    @Test fun groupingAndDeepFocusAdjust() {
        val p = engine.predict(input("messaging", elapsed = 30f, group = 3))
        assertEquals(4f * 1.25f, p.timeAwayMin, 0.001f)
        assertEquals(2f * 1.25f, p.recoveryMin, 0.001f)
    }

    @Test fun rangeContainsEstimate() {
        val p = engine.predict(input("social"))
        assertTrue(p.timeAwayLow < p.timeAwayMin && p.timeAwayMin < p.timeAwayHigh)
        assertTrue(p.recoveryLow < p.recoveryMin && p.recoveryMin < p.recoveryHigh)
    }
}
