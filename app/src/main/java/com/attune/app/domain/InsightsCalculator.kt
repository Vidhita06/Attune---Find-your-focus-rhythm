package com.attune.app.domain
import com.attune.app.util.AppCategoryMapper
import com.attune.core.model.*
import java.util.Calendar
import kotlin.math.roundToInt

object LaunchResults {
    const val CONTENT_INTENT_SENT = "CONTENT_INTENT_SENT"   // the notification's own PendingIntent was sent
    const val APP_LAUNCHED = "APP_LAUNCHED"                 // fell back to the app's launcher activity
    const val FAILED = "FAILED"                             // nothing could be launched
    const val DEMO_NO_APP = "DEMO_NO_APP"                   // demo item and the sample app isn't installed
    fun succeeded(r: String?) = r == CONTENT_INTENT_SENT || r == APP_LAUNCHED
}

data class DayBar(val label: String, val minutes: Int, val isToday: Boolean)
data class SessionRow(val session: FocusSession, val focusedMin: Int, val plannedMin: Int, val queued: Int)
data class ProtectedEstimate(val minutes: Float, val decisions: Int, val coldStart: Boolean)
data class StatusCounts(val queued: Int, val pending: Int, val delayed: Int, val opened: Int, val dismissed: Int, val filtered: Int)

data class InsightsSummary(
    val totalFocusMin: Int, val sessions: Int, val completed: Int, val endedEarly: Int,
    val counts: StatusCounts, val opensDuringFocus: Int, val interruptionsPerHour: Float?, val queuedPerHour: Float?,
    val week: List<DayBar>, val history: List<SessionRow>, val protected: ProtectedEstimate?,
    val todayFocusMin: Int, val todayCompleted: Int
) { val hasData get() = sessions > 0 || counts.queued > 0 }

object InsightsCalculator {
    fun statusCounts(notifs: List<QueuedNotification>) = StatusCounts(
        queued = notifs.count { it.status != NotificationStatus.FILTERED },
        pending = notifs.count { it.status == NotificationStatus.PENDING },
        delayed = notifs.count { it.status == NotificationStatus.DELAYED },
        opened = notifs.count { it.status == NotificationStatus.OPENED || it.status == NotificationStatus.REVIEWED },
        dismissed = notifs.count { it.status == NotificationStatus.DISMISSED },
        filtered = notifs.count { it.status == NotificationStatus.FILTERED })

    fun compute(sessions: List<FocusSession>, notifs: List<QueuedNotification>, actions: List<UserAction>, now: Long, cal: Calendar = Calendar.getInstance()): InsightsSummary {
        val focusedMin = sessions.associate { it.id to (TimerMath.focusedMs(it, now) / 60_000L).toInt() }
        val total = focusedMin.values.sum()
        val opensInFocus = actions.count { it.actionType == ActionType.OPENED && it.sessionState == "FOCUS" && (LaunchResults.succeeded(it.launchResult) || it.launchResult == LaunchResults.DEMO_NO_APP) }
        val hours = total / 60f
        val queuedInSessions = notifs.count { n -> sessions.any { it.id == n.sessionId } }

        val todayStart = startOfDay(cal, now)
        val week = (6 downTo 0).map { back ->
            val start = todayStart - back * DAY; val end = start + DAY
            val c = Calendar.getInstance().apply { timeInMillis = start }
            val label = c.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, java.util.Locale.getDefault()) ?: ""
            DayBar(label, sessions.filter { it.sessionStartMs in start until end }.sumOf { focusedMin[it.id] ?: 0 }, back == 0)
        }
        val today = sessions.filter { it.sessionStartMs >= todayStart }

        val firstDecision = actions.filter { it.actionType != ActionType.OPENED }.groupBy { it.notificationId }.mapNotNull { (_, l) -> l.minByOrNull { it.actionTimeMs } }
        val protectedDecisions = firstDecision.filter { it.sessionState == "FOCUS" && it.predictedTimeAwayMin != null && it.predictedRecoveryMin != null }
        val protected = if (protectedDecisions.isEmpty()) null else ProtectedEstimate(
            protectedDecisions.sumOf { ((it.predictedTimeAwayMin ?: 0f) + (it.predictedRecoveryMin ?: 0f)).toDouble() }.toFloat(),
            protectedDecisions.size, coldStart = actions.size < 5)

        return InsightsSummary(
            totalFocusMin = total, sessions = sessions.count { it.state.isFinished }, completed = sessions.count { it.state == SessionState.COMPLETED },
            endedEarly = sessions.count { it.state == SessionState.ENDED_EARLY }, counts = statusCounts(notifs),
            opensDuringFocus = opensInFocus,
            interruptionsPerHour = if (total >= 10) opensInFocus / hours else null,
            queuedPerHour = if (total >= 10) queuedInSessions / hours else null,
            week = week,
            history = sessions.sortedByDescending { it.sessionStartMs }.map { s -> SessionRow(s, focusedMin[s.id] ?: 0, TimerMath.plannedMinutes(s), notifs.count { it.sessionId == s.id }) },
            protected = protected,
            todayFocusMin = today.sumOf { focusedMin[it.id] ?: 0 },
            todayCompleted = today.count { it.state == SessionState.COMPLETED })
    }

    const val DAY = 86_400_000L
    fun startOfDay(cal: Calendar, now: Long): Long = (cal.clone() as Calendar).apply {
        timeInMillis = now; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
}

data class AppCount(val packageName: String, val label: String, val count: Int)
data class CategoryOutcome(val category: String, val opened: Int, val delayed: Int, val dismissed: Int) { val total get() = opened + delayed + dismissed }
data class TimeBucketStat(val name: String, val sessions: Int, val completionRate: Float, val avgFocusMin: Int)

data class AttentionProfile(
    val finishedSessions: Int, val decisions: Int,
    val longestUninterruptedMin: Int?, val topApps: List<AppCount>, val outcomes: List<CategoryOutcome>,
    val buckets: List<TimeBucketStat>, val bestBucket: TimeBucketStat?
) {
    val isColdStart get() = finishedSessions < MIN_SESSIONS && decisions < MIN_DECISIONS
    companion object { const val MIN_SESSIONS = 3; const val MIN_DECISIONS = 3 }
}

object AttentionProfileCalculator {
    fun compute(sessions: List<FocusSession>, notifs: List<QueuedNotification>, now: Long): AttentionProfile {
        val finished = sessions.filter { it.state.isFinished }
        val longest = finished.filter { it.interruptionCount == 0 }.maxOfOrNull { (TimerMath.focusedMs(it, now) / 60_000L).toInt() }
        val top = notifs.filter { it.status != NotificationStatus.FILTERED }.groupBy { it.packageName }
            .map { (p, l) -> AppCount(p, l.first().appLabel.ifBlank { p.substringAfterLast('.') }, l.sumOf { it.groupCount }) }
            .sortedByDescending { it.count }.take(3)
        val decided = notifs.filter { it.status in setOf(NotificationStatus.OPENED, NotificationStatus.REVIEWED, NotificationStatus.DELAYED, NotificationStatus.DISMISSED) }
        val outcomes = decided.groupBy { it.category }.map { (c, l) -> CategoryOutcome(c,
            l.count { it.status == NotificationStatus.OPENED || it.status == NotificationStatus.REVIEWED },
            l.count { it.status == NotificationStatus.DELAYED }, l.count { it.status == NotificationStatus.DISMISSED }) }.sortedByDescending { it.total }
        val buckets = finished.groupBy { bucket(it.sessionStartMs) }.map { (name, l) ->
            TimeBucketStat(name, l.size, l.count { it.state == SessionState.COMPLETED }.toFloat() / l.size,
                l.map { TimerMath.focusedMs(it, now) / 60_000L }.average().roundToInt()) }
            .sortedBy { BUCKETS.indexOf(it.name) }
        val best = if (finished.size >= AttentionProfile.MIN_SESSIONS) buckets.filter { it.sessions >= 2 }.maxWithOrNull(compareBy({ it.completionRate }, { it.avgFocusMin })) else null
        return AttentionProfile(finished.size, decided.size, longest, top, outcomes, buckets, best)
    }
    val BUCKETS = listOf("Morning", "Afternoon", "Evening", "Night")
    fun bucket(ms: Long): String = when (Calendar.getInstance().apply { timeInMillis = ms }.get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> "Morning"; in 12..16 -> "Afternoon"; in 17..21 -> "Evening"; else -> "Night" }
    fun categoryName(c: String) = AppCategoryMapper.displayName(c)
}

/** Gentle, non-medical suggestions combining an optional self-reported energy level with session/break history. */
object BreakSuggester {
    fun suggest(latest: EnergyCheckIn?, active: FocusSession?, now: Long): String? {
        val recent = latest?.takeIf { now - it.timestampMs < 3 * 3_600_000L }?.level
        val focusedMin = active?.let { TimerMath.focusedMs(it, now) / 60_000L } ?: 0L
        val sinceBreakMin = active?.takeIf { it.state == SessionState.FOCUS }?.let {
            // time since the last pause ended is unknown precisely; when there were no pauses it's the whole session
            if (it.pauseCount == 0) focusedMin else null }
        return when {
            active != null && active.state != SessionState.FOCUS -> null
            recent == EnergyLevel.LOW && focusedMin >= 25 -> "You noted low energy and have focused for $focusedMin min. A short 5-minute break might help."
            (sinceBreakMin ?: 0) >= 90 -> "You've been focusing for over 90 minutes without a pause. A short break could be a good idea."
            active == null && recent == EnergyLevel.LOW -> "You noted low energy. A shorter session (15–25 min) may feel more manageable."
            active == null && recent == EnergyLevel.HIGH -> "You noted high energy — a good moment for a longer session, if you'd like."
            else -> null
        }
    }
}
