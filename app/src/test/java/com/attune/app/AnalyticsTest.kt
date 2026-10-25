package com.attune.app

import com.attune.app.domain.*
import com.attune.core.model.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class AnalyticsTest {
    private val min = 60_000L
    private val ist = TimeZone.getTimeZone("Asia/Kolkata")
    private val ny = TimeZone.getTimeZone("America/New_York")

    private fun at(tz: TimeZone, y: Int, m: Int, d: Int, h: Int, mi: Int = 0) =
        Calendar.getInstance(tz).apply { clear(); set(y, m - 1, d, h, mi, 0) }.timeInMillis

    private fun session(id: String, start: Long, focused: Int, planned: Int = focused, state: SessionState = SessionState.COMPLETED, demo: Boolean = false) =
        FocusSession(id, start, start + planned * min, start + focused * min, state, null, demo, plannedDurationMin = planned)

    private fun decision(id: String, at: Long, pkg: String = "com.whatsapp", routing: String = "DEFER", risk: String = "HIGH", phase: String = "FOCUS",
                         notif: String? = null, demo: Boolean = false, label: String = "WhatsApp") =
        NotificationDecisionRecord(id, "sbn:$id", pkg, label, "messaging", at, at, "s", phase, routing, risk, "LOW", 0.1f, 0.1f, 0.8f, 0.1f, 0.8f, 0f,
            "MODEL", "MODEL|x", "m", false, "NONE", false, null, null, false, null, null, notif, demo, 1)

    private fun open(id: String, notif: String, at: Long, ok: Boolean = true, state: String = "FOCUS", demo: Boolean = false) =
        UserAction(id, notif, null, ActionType.OPENED, at, demo, "s", state, "messaging", true, if (ok) LaunchResults.APP_LAUNCHED else LaunchResults.FAILED)

    @Test fun dayWeekMonthRangesUseLocalCalendarBoundaries() {
        val now = at(ist, 2026, 10, 22, 15) // Thursday
        val day = ReportCalculator.range(Period.DAY, now, now, ist, Locale.UK)
        assertEquals(at(ist, 2026, 10, 22, 0), day.start); assertEquals(at(ist, 2026, 10, 23, 0), day.end); assertTrue(day.isCurrent)
        val week = ReportCalculator.range(Period.WEEK, now, now, ist, Locale.UK)
        assertEquals(at(ist, 2026, 10, 19, 0), week.start) // Monday
        assertEquals(at(ist, 2026, 10, 26, 0), week.end)
        val month = ReportCalculator.range(Period.MONTH, now, now, ist, Locale.UK)
        assertEquals(at(ist, 2026, 10, 1, 0), month.start); assertEquals(at(ist, 2026, 11, 1, 0), month.end)
        assertEquals(31, ReportCalculator.buckets(month, ist, Locale.UK).size)
        assertEquals(7, ReportCalculator.buckets(week, ist, Locale.UK).size)
        assertEquals(24, ReportCalculator.buckets(day, ist, Locale.UK).size)
        // previous month of Oct is Sep (30 days); a past period is not "current"
        val prev = ReportCalculator.shift(month, -1, now, ist, Locale.UK)
        assertEquals(at(ist, 2026, 9, 1, 0), prev.start); assertFalse(prev.isCurrent)
        assertEquals(30, ReportCalculator.buckets(prev, ist, Locale.UK).size)
    }

    @Test fun dstDayHas23HourlyBucketsAndSessionsLandOnTheirLocalDay() {
        val dstStart = at(ny, 2026, 3, 8, 12) // US DST begins 8 March 2026
        val day = ReportCalculator.range(Period.DAY, dstStart, dstStart, ny, Locale.US)
        assertEquals(23, ReportCalculator.buckets(day, ny, Locale.US).size)
        // A session at 23:30 local on 7 March belongs to 7 March, not 8 March (UTC would say otherwise)
        val late = session("a", at(ny, 2026, 3, 7, 23, 30), 25)
        val r = ReportCalculator.compute(Period.DAY, at(ny, 2026, 3, 7, 12), dstStart, listOf(late), emptyList(), emptyList(), emptyList(), ny, Locale.US)
        assertEquals(25, r.totals.focusMin); assertEquals(25, r.focusMin[23])
        val next = ReportCalculator.compute(Period.DAY, dstStart, dstStart, listOf(late), emptyList(), emptyList(), emptyList(), ny, Locale.US)
        assertEquals(0, next.totals.focusMin)
    }

    @Test fun aggregationCountsAppsRiskAndOpensWithoutDoubleCounting() {
        val now = at(ist, 2026, 10, 22, 20)
        val t = at(ist, 2026, 10, 22, 10)
        val sessions = listOf(session("s1", t, 25), session("s2", t + 120 * min, 10, 25, SessionState.ENDED_EARLY))
        val decisions = listOf(
            decision("d1", t + 5 * min, notif = "n1"), decision("d2", t + 6 * min, notif = "n2"),
            decision("d3", t + 7 * min, "com.instagram.android", "DEFER", "HIGH", label = "Instagram", notif = "n3"),
            decision("d4", t + 8 * min, routing = "DELIVER", risk = "LOW"), decision("d5", t + 9 * min, routing = "REVIEW", risk = "UNKNOWN", notif = "n5"),
            decision("d6", t + 300 * min, phase = "NO_SESSION", routing = "DELIVER", risk = "HIGH")) // observed outside focus: not "received during focus"
        val actions = listOf(open("a1", "n1", t + 20 * min, ok = false), open("a2", "n1", t + 21 * min), // failed attempt + retry = 1 open
            open("a3", "n3", t + 22 * min), UserAction("a4", "n2", null, ActionType.DELAYED, t + 23 * min, sessionState = "FOCUS"))
        val r = ReportCalculator.compute(Period.DAY, now, now, sessions, decisions, actions, emptyList(), ist, Locale.UK)
        val tt = r.totals
        assertEquals(35, tt.focusMin); assertEquals(2, tt.sessions); assertEquals(1, tt.completed); assertEquals(1, tt.endedEarly)
        assertEquals(5, tt.received); assertEquals(3, tt.held); assertEquals(1, tt.review); assertEquals(1, tt.allowed)
        assertEquals(2, tt.opened); assertEquals(2, tt.openedDuringFocus)
        assertEquals(mapOf("LOW" to 1, "MEDIUM" to 0, "HIGH" to 3, "UNKNOWN" to 1), r.risk)
        val wa = r.apps.first { it.packageName == "com.whatsapp" }
        assertEquals(4, wa.received); assertEquals(2, wa.held); assertEquals(1, wa.opened)
        assertEquals(1, r.apps.first { it.packageName == "com.instagram.android" }.opened)
        assertEquals(5, r.received.sum()); assertEquals(2, r.interruptions.sum())
        assertEquals(25, r.focusMin[10]); assertEquals(10, r.focusMin[12])
        assertFalse(ReportCalculator.shareText(r).contains("WhatsApp"))
    }

    @Test fun previousPeriodIsComparedOverTheSameElapsedTime() {
        val now = at(ist, 2026, 10, 21, 12) // Wednesday noon, week in progress
        val lastWeekEarly = session("p1", at(ist, 2026, 10, 13, 9), 30)  // last Tuesday — inside the comparable window
        val lastWeekLate = session("p2", at(ist, 2026, 10, 17, 9), 60)   // last Saturday — after the comparable point
        val r = ReportCalculator.compute(Period.WEEK, now, now, listOf(lastWeekEarly, lastWeekLate, session("c", at(ist, 2026, 10, 20, 9), 20)),
            emptyList(), emptyList(), emptyList(), ist, Locale.UK)
        assertEquals(20, r.totals.focusMin)
        assertEquals(30, r.previous!!.focusMin)
    }

    @Test fun emptyDataHasNoReportOrProfile() {
        val now = at(ist, 2026, 10, 22, 12)
        val r = ReportCalculator.compute(Period.MONTH, now, now, emptyList(), emptyList(), emptyList(), emptyList(), ist)
        assertFalse(r.hasData); assertNull(r.previous); assertTrue(r.apps.isEmpty())
        val p = ProfileCalculator.compute(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), 0, 0, now, ist)
        assertFalse(p.hasSessions); assertNull(p.score); assertNull(p.peak); assertTrue(p.insights.isEmpty()); assertTrue(p.coach.isEmpty())
        assertEquals(0, p.streak.current)
    }

    @Test fun demoAndRealRecordsAreNeverMixed() {
        val t = at(ist, 2026, 10, 22, 10)
        val sel = AnalyticsInput.select(listOf(session("r", t, 25), session("d", t, 40, demo = true)),
            listOf(decision("x", t), decision("y", t, demo = true)), listOf(open("a", "n", t), open("b", "n", t, demo = true)),
            listOf(EnergyCheckIn("e", EnergyLevel.LOW, t, score = 2), EnergyCheckIn("f", EnergyLevel.HIGH, t, isDemo = true, score = 5)), emptyList(), demo = false)
        assertEquals(listOf("r"), sel.sessions.map { it.id }); assertEquals(listOf("x"), sel.decisions.map { it.id })
        assertEquals(listOf("a"), sel.actions.map { it.id }); assertEquals(listOf("e"), sel.checkins.map { it.id })
        val demo = AnalyticsInput.select(sel.sessions + session("d", t, 40, demo = true), emptyList(), emptyList(), emptyList(), emptyList(), demo = true)
        assertEquals(listOf("d"), demo.sessions.map { it.id })
    }

    @Test fun streakCountsGoalDaysAndTodayNeverBreaksIt() {
        val now = at(ist, 2026, 10, 22, 9) // nothing yet today
        val ss = listOf(session("a", at(ist, 2026, 10, 19, 10), 30), session("b", at(ist, 2026, 10, 20, 10), 30), session("c", at(ist, 2026, 10, 21, 10), 30),
            session("old", at(ist, 2026, 10, 10, 10), 30))
        val s = ProfileCalculator.streak(ss, 0, now, ist)
        assertEquals(3, s.current); assertFalse(s.todayMet); assertEquals(3, s.best)
        val withGoal = ProfileCalculator.streak(ss, 45, now, ist) // 30 min < 45 min goal
        assertEquals(0, withGoal.current)
    }

    @Test fun focusScoreIsTransparentAndBounded() {
        val now = at(ist, 2026, 10, 22, 20)
        val ss = listOf(session("a", now - 3 * 3_600_000L, 50), session("b", now - 2 * 3_600_000L, 25, 50, SessionState.ENDED_EARLY))
        val sc = ProfileCalculator.focusScore(ss, listOf(open("o", "n", now - 2 * 3_600_000L)), 150, now, now - 7 * 86_400_000L)!!
        assertEquals((40f * 75 / 150).toInt(), sc.focusPart)
        assertEquals(15, sc.completionPart)
        assertEquals(sc.focusPart + sc.completionPart + sc.calmPart, sc.total)
        assertTrue(sc.total in 0..100); assertEquals(3, sc.explanation.size)
        assertNull(ProfileCalculator.focusScore(emptyList(), emptyList(), 0, now, 0))
    }

    @Test fun energyDurationSuggestionUsesOwnDataOnlyWhenThereIsEnough() {
        val now = at(ist, 2026, 10, 22, 20)
        val rule = ProfileCalculator.durationFor(2, emptyList(), emptyList(), 25)
        assertEquals(20, rule.minutes); assertTrue(rule.basis.startsWith("Rule of thumb"))
        assertEquals(40, ProfileCalculator.durationFor(5, emptyList(), emptyList(), 25).minutes)
        val s1 = session("a", now - 2 * 86_400_000L, 45); val s2 = session("b", now - 86_400_000L, 45)
        val ci = listOf(EnergyCheckIn("1", EnergyLevel.HIGH, s1.sessionStartMs - 5 * min, score = 4, phase = "BEFORE"),
            EnergyCheckIn("2", EnergyLevel.HIGH, s2.sessionStartMs - 5 * min, score = 5, phase = "BEFORE"))
        val own = ProfileCalculator.durationFor(4, listOf(s1, s2), ci, 25)
        assertEquals(45, own.minutes); assertTrue(own.basis.startsWith("Median of 2"))
    }

    @Test fun peakWindowNeedsEnoughSessions() {
        val now = at(ist, 2026, 10, 22, 20)
        val two = listOf(session("a", at(ist, 2026, 10, 20, 9), 30), session("b", at(ist, 2026, 10, 21, 9), 30))
        assertNull(ProfileCalculator.compute(two, emptyList(), emptyList(), emptyList(), emptyList(), 0, 0, now, ist).peak)
        val four = two + session("c", at(ist, 2026, 10, 19, 10), 40) + session("d", at(ist, 2026, 10, 18, 21), 10, 25, SessionState.ENDED_EARLY)
        val p = ProfileCalculator.compute(four, emptyList(), emptyList(), emptyList(), emptyList(), 0, 0, now, ist)
        assertEquals(9, p.peak!!.startHour); assertEquals(3, p.peak!!.sessions)
    }

    @Test fun timerStateSurvivesRestartBecauseItIsDerivedFromTimestamps() {
        // A session persisted as PAUSED 10 minutes in, with the app killed for an hour: still 10 minutes focused, not due.
        val start = at(ist, 2026, 10, 22, 9)
        val paused = FocusSession("p", start, start + 25 * min, null, SessionState.PAUSED, start + 10 * min, plannedDurationMin = 25, pauseCount = 1)
        val afterRestart = start + 70 * min
        assertEquals(10 * min, TimerMath.focusedMs(paused, afterRestart))
        assertEquals(15 * min, TimerMath.remainingMs(paused, afterRestart))
        assertFalse(TimerMath.isDue(paused, afterRestart))
    }
}
