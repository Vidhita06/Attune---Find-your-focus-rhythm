package com.attune.app

import com.attune.app.domain.*
import com.attune.core.model.*
import org.junit.Assert.*
import org.junit.Test

class DomainLogicTest {
    private val min = 60_000L
    private fun session(state: SessionState, start: Long = 0, planned: Int = 25, paused: Long = 0, breakStart: Long? = null, end: Long? = null, interruptions: Int = 0, demo: Boolean = false, id: String = "s") =
        FocusSession(id, start, start + planned * min, end, state, breakStart, demo, plannedDurationMin = planned, totalPausedMs = paused, interruptionCount = interruptions)

    @Test fun timerCountsDownFromTimestamps() {
        val s = session(SessionState.FOCUS)
        assertEquals(10 * min, TimerMath.focusedMs(s, 10 * min))
        assertEquals(15 * min, TimerMath.remainingMs(s, 10 * min))
        assertFalse(TimerMath.isDue(s, 24 * min)); assertTrue(TimerMath.isDue(s, 25 * min))
    }
    @Test fun pausedTimeIsExcluded() {
        val s = session(SessionState.PAUSED, paused = 5 * min, breakStart = 20 * min)
        // started 0, 5 min of earlier pauses, paused again at 20 -> focused 15 min regardless of how long the pause lasts
        assertEquals(15 * min, TimerMath.focusedMs(s, 40 * min))
        assertFalse(TimerMath.isDue(s, 999 * min))
        assertEquals(30 * min, TimerMath.dueAtMs(session(SessionState.FOCUS, paused = 5 * min)))
    }
    @Test fun focusedTimeCappedAtPlanned() = assertEquals(25 * min, TimerMath.focusedMs(session(SessionState.FOCUS), 90 * min))
    @Test fun legacyRowsDerivePlannedDuration() =
        assertEquals(45, TimerMath.plannedMinutes(FocusSession("x", 0, 45 * min, state = SessionState.COMPLETED)))

    @Test fun insightsCountsAndProtectedEstimate() {
        val now = InsightsCalculator.startOfDay(java.util.Calendar.getInstance(), System.currentTimeMillis()) + 12 * 60 * min
        val s1 = session(SessionState.COMPLETED, start = now - 60 * min, end = now - 35 * min, id = "a")
        val s2 = session(SessionState.ENDED_EARLY, start = now - 30 * min, end = now - 20 * min, id = "b")
        val n = listOf(
            QueuedNotification("n1", "a", "p", "social", "t", null, "k1", now, NotificationStatus.DELAYED),
            QueuedNotification("n2", "a", "p", "social", "t", null, "k2", now, NotificationStatus.OPENED),
            QueuedNotification("n3", "b", "q", "promotion", "t", null, "k3", now, NotificationStatus.FILTERED))
        val acts = listOf(
            UserAction("1", "n1", null, ActionType.DELAYED, now, sessionState = "FOCUS", predictedTimeAwayMin = 10f, predictedRecoveryMin = 5f),
            UserAction("2", "n1", null, ActionType.DISMISSED, now + 1, sessionState = "FOCUS", predictedTimeAwayMin = 10f, predictedRecoveryMin = 5f),
            UserAction("3", "n2", null, ActionType.OPENED, now, sessionState = "FOCUS", launchResult = LaunchResults.APP_LAUNCHED))
        val r = InsightsCalculator.compute(listOf(s1, s2), n, acts, now)
        assertEquals(35, r.totalFocusMin); assertEquals(2, r.sessions); assertEquals(1, r.completed); assertEquals(1, r.endedEarly)
        assertEquals(2, r.counts.queued); assertEquals(1, r.counts.filtered); assertEquals(1, r.counts.opened)
        assertEquals(1, r.opensDuringFocus)
        // first non-open decision per notification only: 10 + 5, not double-counted
        assertEquals(15f, r.protected!!.minutes, 0.001f); assertEquals(1, r.protected!!.decisions)
        assertEquals(7, r.week.size); assertEquals(35, r.week.last().minutes)
    }
    @Test fun emptyInsightsHaveNoData() = assertFalse(InsightsCalculator.compute(emptyList(), emptyList(), emptyList(), 0).hasData)

    @Test fun profileColdStartAndLongestUninterrupted() {
        val p0 = AttentionProfileCalculator.compute(emptyList(), emptyList(), 0)
        assertTrue(p0.isColdStart); assertNull(p0.longestUninterruptedMin); assertNull(p0.bestBucket)
        val ss = listOf(session(SessionState.COMPLETED, end = 25 * min, id = "a"), session(SessionState.COMPLETED, planned = 50, end = 50 * min, interruptions = 2, id = "b"),
            session(SessionState.ENDED_EARLY, end = 10 * min, id = "c"))
        val p = AttentionProfileCalculator.compute(ss, emptyList(), 100 * min)
        assertFalse(p.isColdStart); assertEquals(25, p.longestUninterruptedMin)
    }

    @Test fun breakSuggestionsAreGentleAndConditional() {
        val now = 100 * min
        val low = EnergyCheckIn("e", EnergyLevel.LOW, now - 10 * min)
        assertNotNull(BreakSuggester.suggest(low, session(SessionState.FOCUS, start = now - 30 * min, planned = 60), now))
        assertNull(BreakSuggester.suggest(low, session(SessionState.FOCUS, start = now - 5 * min, planned = 60), now))
        assertNull(BreakSuggester.suggest(EnergyCheckIn("e", EnergyLevel.LOW, now - 5 * 60 * min), null, now)) // stale check-in ignored
        assertNull(BreakSuggester.suggest(null, null, now))
    }
}
