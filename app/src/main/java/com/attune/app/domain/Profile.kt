package com.attune.app.domain

import com.attune.app.util.AppCategoryMapper
import com.attune.core.model.*
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.roundToInt

data class FocusOverview(
    val totalFocusMin: Int, val sessions: Int, val completed: Int, val endedEarly: Int, val avgSessionMin: Int?,
    val todayMin: Int, val weekMin: Int, val dailyGoalMin: Int, val weeklyGoalMin: Int,
    /** Last 14 days, oldest first: minutes focused per day. */
    val last14: List<Int>, val activeDays14: Int
)

data class CountItem(val key: String, val label: String, val count: Int)

data class DistractionAnalysis(
    val receivedDuringFocus: Int, val classifiedDistracting: Int, val held: Int, val keptVisible: Int, val allowed: Int,
    val openedDuringFocus: Int, val topCategories: List<CountItem>, val topApps: List<AppStat>,
    /** Opens during focus per focus hour, last 7 days vs the 7 days before (null when there's too little focus time). */
    val interruptionRateRecent: Float?, val interruptionRatePrior: Float?
)

data class HourStat(val hour: Int, val focusMin: Int, val completedSessions: Int, val sessions: Int, val interruptions: Int)
data class PeakWindow(val startHour: Int, val endHour: Int, val focusMin: Int, val sessions: Int, val completionRate: Float, val interruptionsPerHour: Float)

data class EnergyAnalysis(val recent: List<EnergyCheckIn>, val average: Float?, val highEnergyCompletion: Float?, val lowEnergyCompletion: Float?, val highN: Int, val lowN: Int)

data class FocusScore(val total: Int, val focusPart: Int, val completionPart: Int, val calmPart: Int, val focusTargetMin: Int, val explanation: List<String>)
data class Streak(val current: Int, val best: Int, val todayMet: Boolean, val rule: String)
data class Insight(val title: String, val evidence: String)
data class DurationSuggestion(val minutes: Int, val basis: String)

data class AttentionProfileV2(
    val overview: FocusOverview, val distraction: DistractionAnalysis, val hours: List<HourStat>, val peak: PeakWindow?,
    val energy: EnergyAnalysis, val risk: Map<String, Int>, val riskFrom: Long?, val riskTo: Long?,
    val insights: List<Insight>, val coach: List<Insight>, val schedule: List<Insight>,
    val score: FocusScore?, val streak: Streak, val overrides: Int, val feedbackCount: Int
) {
    val hasSessions get() = overview.sessions > 0
}

/**
 * The Personal Attention Profile. Everything is computed from stored records; every insight states its evidence and is
 * only produced above a minimum amount of data. Nothing here measures attention or health directly — it describes
 * focus sessions and notification events that Attune recorded.
 */
object ProfileCalculator {
    const val DAY = 86_400_000L
    const val MIN_SESSIONS_FOR_PATTERNS = 3

    private fun startOfDay(t: Long, tz: TimeZone) = Calendar.getInstance(tz).apply { timeInMillis = t; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
    private fun hourOf(t: Long, tz: TimeZone) = Calendar.getInstance(tz).apply { timeInMillis = t }.get(Calendar.HOUR_OF_DAY)
    private fun dayStarts(today: Long, n: Int, tz: TimeZone): List<Long> {
        val c = Calendar.getInstance(tz).apply { timeInMillis = today }
        return (0 until n).map { val s = c.timeInMillis; c.add(Calendar.DAY_OF_YEAR, -1); s }.reversed()
    }
    private fun openedOk(a: UserAction) = a.actionType == ActionType.OPENED && (LaunchResults.succeeded(a.launchResult) || a.launchResult == LaunchResults.DEMO_NO_APP)
    private fun focusMin(s: FocusSession, now: Long) = (TimerMath.focusedMs(s, now) / 60_000L).toInt()
    fun hourLabel(h: Int): String = when { h == 0 -> "12 am"; h < 12 -> "$h am"; h == 12 -> "12 pm"; else -> "${h - 12} pm" }

    fun compute(sessions: List<FocusSession>, decisions: List<NotificationDecisionRecord>, actions: List<UserAction>, checkins: List<EnergyCheckIn>,
                feedback: List<FeedbackEvent>, dailyGoalMin: Int, weeklyGoalMin: Int, now: Long, tz: TimeZone = TimeZone.getDefault()): AttentionProfileV2 {
        val today = startOfDay(now, tz)
        val finished = sessions.filter { it.state.isFinished }
        val days14 = dayStarts(today, 14, tz)
        fun minutesOn(dayStart: Long): Int { val end = Calendar.getInstance(tz).apply { timeInMillis = dayStart; add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis
            return sessions.filter { it.sessionStartMs in dayStart until end }.sumOf { focusMin(it, now) } }
        val last14 = days14.map(::minutesOn)
        val weekStart = days14[7]
        val overview = FocusOverview(sessions.sumOf { focusMin(it, now) }, finished.size, finished.count { it.state == SessionState.COMPLETED },
            finished.count { it.state == SessionState.ENDED_EARLY }, if (finished.isEmpty()) null else finished.sumOf { focusMin(it, now) } / finished.size,
            last14.last(), sessions.filter { it.sessionStartMs >= weekStart }.sumOf { focusMin(it, now) }, dailyGoalMin, weeklyGoalMin, last14, last14.count { it > 0 })

        // B. distraction
        val inFocus = decisions.filter { it.phase == "FOCUS" }
        val opensFocus = actions.filter { openedOk(it) && it.sessionState == "FOCUS" }
        val openedById = opensFocus.groupingBy { it.notificationId }.eachCount()
        val topApps = inFocus.groupBy { it.packageName }.map { (p, l) -> AppStat(p, l.first().appLabel.ifBlank { p.substringAfterLast('.') }, l.size,
            l.count { it.routing == "DEFER" || it.routing == "FILTER" }, l.sumOf { d -> d.notificationId?.let { openedById[it] } ?: 0 }) }
            .sortedByDescending { it.received }.take(5)
        val topCats = inFocus.groupBy { it.category }.map { (c, l) -> CountItem(c, AppCategoryMapper.displayName(c), l.size) }.sortedByDescending { it.count }.take(5)
        fun rate(from: Long, to: Long): Float? {
            val mins = sessions.filter { it.sessionStartMs in from until to }.sumOf { focusMin(it, now) }
            if (mins < 30) return null
            return opensFocus.count { it.actionTimeMs in from until to } / (mins / 60f)
        }
        val distraction = DistractionAnalysis(inFocus.size, inFocus.count { (it.pDistracting ?: 0f) >= 0.5f || it.focusRisk == "HIGH" },
            inFocus.count { it.routing == "DEFER" || it.routing == "FILTER" }, inFocus.count { it.routing == "REVIEW" }, inFocus.count { it.routing == "DELIVER" },
            opensFocus.size, topCats, topApps, rate(today - 6 * DAY, now + 1), rate(today - 13 * DAY, today - 6 * DAY))

        // C. hours
        val hours = (0..23).map { h ->
            val hs = finished.filter { hourOf(it.sessionStartMs, tz) == h }
            HourStat(h, hs.filter { it.state == SessionState.COMPLETED }.sumOf { focusMin(it, now) }, hs.count { it.state == SessionState.COMPLETED }, hs.size,
                opensFocus.count { hourOf(it.actionTimeMs, tz) == h })
        }
        val peak = if (finished.size < MIN_SESSIONS_FOR_PATTERNS) null else (0..22).map { h ->
            val w = hours.subList(h, h + 2); val n = w.sumOf { it.sessions }; val fm = w.sumOf { it.focusMin }
            val allMin = finished.filter { hourOf(it.sessionStartMs, tz) in h..h + 1 }.sumOf { focusMin(it, now) }
            PeakWindow(h, h + 2, fm, n, if (n == 0) 0f else w.sumOf { it.completedSessions }.toFloat() / n, if (allMin == 0) 0f else w.sumOf { it.interruptions } / (allMin / 60f))
        }.filter { it.sessions >= 2 && it.completionRate >= 0.5f }.maxWithOrNull(compareBy<PeakWindow>({ it.focusMin }, { -it.interruptionsPerHour }))

        // D. energy
        val real = checkins.sortedByDescending { it.timestampMs }
        fun before(s: FocusSession) = checkins.filter { it.timestampMs in (s.sessionStartMs - 60 * 60_000L)..(s.sessionStartMs + 5 * 60_000L) && it.phase != "AFTER" }.maxByOrNull { it.timestampMs }
        val withEnergy = finished.mapNotNull { s -> before(s)?.let { s to it.score } }
        val hi = withEnergy.filter { it.second >= 4 }; val lo = withEnergy.filter { it.second <= 2 }
        fun comp(l: List<Pair<FocusSession, Int>>) = if (l.size < 2) null else l.count { it.first.state == SessionState.COMPLETED }.toFloat() / l.size
        val energy = EnergyAnalysis(real.take(14).reversed(), if (real.isEmpty()) null else real.take(14).map { it.score }.average().toFloat(), comp(hi), comp(lo), hi.size, lo.size)

        // E. risk
        val risk = listOf("LOW", "MEDIUM", "HIGH", "UNKNOWN").associateWith { k -> inFocus.count { it.focusRisk == k } }

        // score, streak, feedback
        val score = focusScore(sessions, actions, weeklyGoalMin, now, today - 6 * DAY)
        val streak = streak(sessions, dailyGoalMin, now, tz)
        val overrides = feedback.count { it.kind in setOf("WRONGLY_DEFERRED", "ACTUALLY_IMPORTANT", "NOT_IMPORTANT") }

        val insights = insights(hours, overview, distraction, peak, energy, finished, feedback, overrides, now, today)
        val coach = coach(finished, distraction, peak, energy, feedback, now)
        val schedule = schedule(hours, finished)
        return AttentionProfileV2(overview, distraction, hours, peak, energy, risk, inFocus.minOfOrNull { it.postedAtMs }, inFocus.maxOfOrNull { it.postedAtMs },
            insights, coach, schedule, score, streak, overrides, feedback.size)
    }

    /**
     * Weekly focus score (0–100), shown with its parts. It is a progress indicator built from recorded sessions, not a
     * measure of attention:  focus time vs target (40) + share of sessions completed as planned (30) + calm (30, fewer
     * notifications opened from Attune per focus hour; 3+/h scores 0).
     */
    fun focusScore(sessions: List<FocusSession>, actions: List<UserAction>, weeklyGoalMin: Int, now: Long, from: Long): FocusScore? {
        val ss = sessions.filter { it.sessionStartMs >= from }
        val fin = ss.filter { it.state.isFinished }
        if (fin.isEmpty()) return null
        val mins = ss.sumOf { focusMin(it, now) }
        val target = if (weeklyGoalMin > 0) weeklyGoalMin else 150
        val f = (40f * (mins.toFloat() / target).coerceAtMost(1f)).roundToInt()
        val c = (30f * fin.count { it.state == SessionState.COMPLETED } / fin.size).roundToInt()
        val opens = actions.count { openedOk(it) && it.sessionState == "FOCUS" && it.actionTimeMs >= from }
        val perHour = if (mins >= 10) opens / (mins / 60f) else 0f
        val calm = (30f * (1f - (perHour / 3f).coerceAtMost(1f))).roundToInt()
        return FocusScore(f + c + calm, f, c, calm, target, listOf(
            "Focus time: ${TimerMath.formatMinutes(mins)} of a ${TimerMath.formatMinutes(target)} target → $f/40",
            "Completed as planned: ${fin.count { it.state == SessionState.COMPLETED }} of ${fin.size} → $c/30",
            "Opened from Attune during focus: $opens (${"%.1f".format(perHour)} per hour) → $calm/30"))
    }

    /** A day counts when the daily goal is met (or, with no goal, when at least one session was completed). Today never breaks a streak. */
    fun streak(sessions: List<FocusSession>, dailyGoalMin: Int, now: Long, tz: TimeZone): Streak {
        val byDay = sessions.groupBy { startOfDay(it.sessionStartMs, tz) }
        fun met(day: Long) = byDay[day].orEmpty().let { l -> if (dailyGoalMin > 0) l.sumOf { focusMin(it, now) } >= dailyGoalMin else l.any { it.state == SessionState.COMPLETED } }
        val c = Calendar.getInstance(tz).apply { timeInMillis = startOfDay(now, tz) }
        val todayMet = met(c.timeInMillis)
        if (!todayMet) c.add(Calendar.DAY_OF_YEAR, -1)
        var cur = 0
        while (met(c.timeInMillis)) { cur++; c.add(Calendar.DAY_OF_YEAR, -1) }
        var best = 0; var run = 0; var prev: Long? = null
        for (d in byDay.keys.sorted()) {
            if (!met(d)) continue
            val expected = prev?.let { p -> Calendar.getInstance(tz).apply { timeInMillis = p; add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis }
            run = if (expected == d) run + 1 else 1; best = maxOf(best, run); prev = d
        }
        return Streak(cur, maxOf(best, cur), todayMet, if (dailyGoalMin > 0) "days reaching your ${TimerMath.formatMinutes(dailyGoalMin)} goal" else "days with a completed session")
    }

    private fun pct(f: Float) = "${(f * 100).roundToInt()}%"

    private fun insights(hours: List<HourStat>, o: FocusOverview, d: DistractionAnalysis, peak: PeakWindow?, e: EnergyAnalysis, finished: List<FocusSession>,
                         feedback: List<FeedbackEvent>, overrides: Int, now: Long, today: Long): List<Insight> = buildList {
        d.topApps.firstOrNull()?.takeIf { it.received >= 3 }?.let { add(Insight("${it.label} sends the most notifications during focus", "${it.received} of ${d.receivedDuringFocus} recorded during focus; ${it.held} held, ${it.opened} opened from Attune.")) }
        hours.maxByOrNull { it.interruptions }?.takeIf { it.interruptions >= 3 }?.let { h ->
            add(Insight("Most opens during focus happen around ${hourLabel(h.hour)}", "${h.interruptions} of ${hours.sumOf { it.interruptions }} notifications opened from Attune during focus were opened between ${hourLabel(h.hour)} and ${hourLabel((h.hour + 1) % 24)}."))
        }
        if (peak != null) add(Insight("${hourLabel(peak.startHour)}–${hourLabel(peak.endHour)} is your strongest window so far", "${peak.sessions} sessions started then, ${pct(peak.completionRate)} completed; ${TimerMath.formatMinutes(peak.focusMin)} of completed focus."))
        val recent = finished.filter { it.sessionStartMs >= today - 13 * DAY }.sortedBy { it.sessionStartMs }
        if (recent.size >= 6) {
            val half = recent.size / 2
            val a = recent.take(half).map { focusMin(it, now) }.average(); val b = recent.drop(half).map { focusMin(it, now) }.average()
            if (kotlin.math.abs(b - a) >= 5) add(Insight(if (b > a) "Your sessions are getting longer" else "Your sessions are getting shorter",
                "Average focused time went from ${a.roundToInt()} to ${b.roundToInt()} min across your last ${recent.size} sessions (14 days)."))
        }
        val r1 = d.interruptionRateRecent; val r0 = d.interruptionRatePrior
        if (r1 != null && r0 != null && kotlin.math.abs(r1 - r0) >= 0.3f) add(Insight(if (r1 < r0) "Fewer notifications opened during focus" else "More notifications opened during focus",
            "${"%.1f".format(r0)} → ${"%.1f".format(r1)} per focus hour (previous 7 days → last 7 days). Counts only opens from Attune."))
        if (feedback.size >= 5) add(Insight(if (overrides * 2 >= feedback.size) "You often correct the AI's decisions" else "The AI's decisions mostly match your feedback",
            "$overrides of ${feedback.size} feedback entries corrected a prediction. Corrections are applied as learned preferences."))
        if (e.highEnergyCompletion != null && e.lowEnergyCompletion != null && kotlin.math.abs(e.highEnergyCompletion - e.lowEnergyCompletion) >= 0.2f)
            add(Insight("Sessions after a high-energy check-in complete ${if (e.highEnergyCompletion > e.lowEnergyCompletion) "more" else "less"} often",
                "${pct(e.highEnergyCompletion)} completed (${e.highN} sessions, energy 4–5) vs ${pct(e.lowEnergyCompletion)} (${e.lowN} sessions, energy 1–2). Self-reported; a correlation, not a cause."))
        if (o.sessions in 1 until MIN_SESSIONS_FOR_PATTERNS) add(Insight("Patterns appear after ${MIN_SESSIONS_FOR_PATTERNS} sessions", "You've finished ${o.sessions}. Keep going to unlock peak hours and trends."))
    }

    private fun coach(finished: List<FocusSession>, d: DistractionAnalysis, peak: PeakWindow?, e: EnergyAnalysis, feedback: List<FeedbackEvent>, now: Long): List<Insight> = buildList {
        val last = finished.sortedByDescending { it.sessionStartMs }.take(8)
        if (last.size >= 4) {
            val early = last.count { it.state == SessionState.ENDED_EARLY }
            if (early * 2 >= last.size) {
                val med = last.map { focusMin(it, now) }.sorted()[last.size / 2]
                val sug = ((med + 2) / 5 * 5).coerceIn(10, 90)
                add(Insight("Try $sug-minute sessions", "$early of your last ${last.size} sessions ended early; you typically focus about $med min. Finishing shorter sessions builds momentum."))
            } else if (last.all { it.state == SessionState.COMPLETED } && last.size >= 5) {
                val planned = last.map { TimerMath.plannedMinutes(it) }.max()
                add(Insight("Ready for a longer session?", "Your last ${last.size} sessions all completed. Consider ${(planned + 10).coerceAtMost(120)} minutes next time — only if it feels right."))
            }
        }
        d.topApps.firstOrNull { it.opened >= 2 }?.let { add(Insight("Consider holding ${it.label} during focus", "You opened ${it.opened} of its ${it.received} notifications from Attune while focusing. A hold rule keeps it in the queue (urgent content still gets through).")) }
        val corrections = feedback.filter { it.kind == "WRONGLY_DEFERRED" || it.kind == "ACTUALLY_IMPORTANT" }.groupBy { it.appLabel }.maxByOrNull { it.value.size }
        if (corrections != null && corrections.value.size >= 3) add(Insight("Always allow ${corrections.key}?", "You marked ${corrections.value.size} of its notifications as important. An allow rule would let it through every time."))
        if (peak != null) add(Insight("Plan deep work for ${hourLabel(peak.startHour)}–${hourLabel(peak.endHour)}", "That window has your most completed focus time with ${"%.1f".format(peak.interruptionsPerHour)} opens per hour."))
        if (e.lowEnergyCompletion != null && e.highEnergyCompletion != null && e.lowEnergyCompletion + 0.2f <= e.highEnergyCompletion)
            add(Insight("On low-energy days, go shorter", "Sessions after an energy check-in of 1–2 completed ${pct(e.lowEnergyCompletion)} of the time vs ${pct(e.highEnergyCompletion)} at 4–5."))
    }

    private fun schedule(hours: List<HourStat>, finished: List<FocusSession>): List<Insight> {
        if (finished.size < MIN_SESSIONS_FOR_PATTERNS) return emptyList()
        return (0..21).map { h -> h to hours.subList(h, h + 3) }
            .map { (h, w) -> Triple(h, w.sumOf { it.sessions }, w.sumOf { it.completedSessions }) }
            .filter { it.second >= 2 && it.third.toFloat() / it.second >= 0.6f }
            .sortedWith(compareByDescending<Triple<Int, Int, Int>> { it.third }.thenBy { it.first })
            .fold(mutableListOf<Triple<Int, Int, Int>>()) { acc, t -> if (acc.none { kotlin.math.abs(it.first - t.first) < 3 }) acc += t; acc }.take(2)
            .map { (h, n, c) -> Insight("${hourLabel(h)} – ${hourLabel((h + 3) % 24)}", "$c of $n sessions started in this window were completed.") }
    }

    /**
     * Suggests a session length for a self-reported energy score: the median planned length of completed sessions that
     * started after a similar check-in (±1), or a documented rule of thumb when there are fewer than 2 such sessions.
     */
    fun durationFor(score: Int, sessions: List<FocusSession>, checkins: List<EnergyCheckIn>, defaultMin: Int): DurationSuggestion {
        val similar = sessions.filter { it.state == SessionState.COMPLETED }.filter { s ->
            checkins.any { c -> c.phase != "AFTER" && c.timestampMs in (s.sessionStartMs - 60 * 60_000L)..(s.sessionStartMs + 5 * 60_000L) && kotlin.math.abs(c.score - score) <= 1 }
        }
        if (similar.size >= 2) {
            val m = similar.map { TimerMath.plannedMinutes(it) }.sorted()[similar.size / 2]
            return DurationSuggestion(m, "Median of ${similar.size} completed sessions after a similar energy check-in")
        }
        val m = when { score <= 2 -> minOf(defaultMin, 20); score >= 4 -> minOf(defaultMin + 15, 90); else -> defaultMin }
        return DurationSuggestion(m, "Rule of thumb (not enough of your own data yet): shorter when energy is low, longer when high")
    }
}
