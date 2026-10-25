package com.attune.app.domain

import com.attune.core.model.*
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

enum class Period { DAY, WEEK, MONTH }

/** [start, end) in epoch ms. [elapsedEnd] = min(now, end): the part of the range that has actually happened. */
data class DateRange(val period: Period, val start: Long, val end: Long, val elapsedEnd: Long, val label: String) {
    val isCurrent get() = elapsedEnd < end
    operator fun contains(t: Long) = t >= start && t < end
}

data class Bucket(val start: Long, val end: Long, val label: String, val showLabel: Boolean)

data class ReportTotals(
    val focusMin: Int, val sessions: Int, val completed: Int, val endedEarly: Int, val avgSessionMin: Int?,
    val received: Int, val held: Int, val review: Int, val allowed: Int, val opened: Int, val openedDuringFocus: Int
) {
    val completionRate get() = if (sessions > 0) completed.toFloat() / sessions else null
    val hasData get() = sessions > 0 || received > 0 || focusMin > 0
}

data class AppStat(val packageName: String, val label: String, val received: Int, val held: Int, val opened: Int)
data class SessionPoint(val startMs: Long, val focusedMin: Int, val plannedMin: Int, val state: SessionState, val id: String)

data class Report(
    val range: DateRange, val buckets: List<Bucket>,
    val focusMin: List<Int>, val received: List<Int>, val held: List<Int>, val opened: List<Int>, val interruptions: List<Int>,
    val energy: List<Float?>, val completion: List<Float?>,
    val totals: ReportTotals, val previous: ReportTotals?,
    val apps: List<AppStat>, val risk: Map<String, Int>, val sessions: List<SessionPoint>
) { val hasData get() = totals.hasData || energy.any { it != null } }

/**
 * Aggregates stored records into daily (hourly buckets), weekly (daily buckets) and monthly (daily buckets) reports.
 * Attribution rules, documented in docs/DATA_AND_PRIVACY.md:
 *  - focus minutes and sessions count in the bucket where the session STARTED;
 *  - "received" = notifications evaluated while focusing (decision log, deduplicated per Android notification);
 *  - "opened" = notifications opened from Attune (user actions); "interruptions" = opened during focus. Attune cannot see
 *    notifications you open directly from the shade, so this undercounts real interruptions.
 * All inputs must already be filtered to either real or demo records.
 */
object ReportCalculator {
    fun range(period: Period, anchorMs: Long, now: Long, tz: TimeZone = TimeZone.getDefault(), locale: Locale = Locale.getDefault()): DateRange {
        val c = Calendar.getInstance(tz, locale).apply { timeInMillis = anchorMs; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        when (period) {
            Period.DAY -> {}
            Period.WEEK -> { c.firstDayOfWeek = Calendar.MONDAY; while (c.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) c.add(Calendar.DAY_OF_YEAR, -1) }
            Period.MONTH -> c.set(Calendar.DAY_OF_MONTH, 1)
        }
        val start = c.timeInMillis
        when (period) { Period.DAY -> c.add(Calendar.DAY_OF_YEAR, 1); Period.WEEK -> c.add(Calendar.DAY_OF_YEAR, 7); Period.MONTH -> c.add(Calendar.MONTH, 1) }
        val end = c.timeInMillis
        val fmt = { p: String, t: Long -> java.text.SimpleDateFormat(p, locale).apply { timeZone = tz }.format(java.util.Date(t)) }
        val label = when (period) {
            Period.DAY -> fmt("EEE d MMM yyyy", start)
            Period.WEEK -> fmt("d MMM", start) + " – " + fmt("d MMM yyyy", end - 1)
            Period.MONTH -> fmt("MMMM yyyy", start)
        }
        return DateRange(period, start, end, now.coerceIn(start, end), label)
    }

    /** The range [steps] periods before/after [r] (negative = earlier). */
    fun shift(r: DateRange, steps: Int, now: Long, tz: TimeZone = TimeZone.getDefault(), locale: Locale = Locale.getDefault()): DateRange {
        val c = Calendar.getInstance(tz, locale).apply { timeInMillis = r.start + 3_600_000L * 12 }
        when (r.period) { Period.DAY -> c.add(Calendar.DAY_OF_YEAR, steps); Period.WEEK -> c.add(Calendar.DAY_OF_YEAR, 7 * steps); Period.MONTH -> c.add(Calendar.MONTH, steps) }
        return range(r.period, c.timeInMillis, now, tz, locale)
    }

    fun buckets(r: DateRange, tz: TimeZone = TimeZone.getDefault(), locale: Locale = Locale.getDefault()): List<Bucket> {
        val out = mutableListOf<Bucket>()
        val c = Calendar.getInstance(tz, locale).apply { timeInMillis = r.start }
        var i = 0
        while (c.timeInMillis < r.end) {
            val s = c.timeInMillis
            if (r.period == Period.DAY) c.add(Calendar.HOUR_OF_DAY, 1) else c.add(Calendar.DAY_OF_YEAR, 1)
            val label = when (r.period) {
                Period.DAY -> "${i}"
                Period.WEEK -> java.text.SimpleDateFormat("EEEEE", locale).apply { timeZone = tz }.format(java.util.Date(s))
                Period.MONTH -> "${i + 1}"
            }
            val show = when (r.period) { Period.DAY -> i % 6 == 0; Period.WEEK -> true; Period.MONTH -> i == 0 || (i + 1) % 5 == 0 }
            out += Bucket(s, c.timeInMillis, label, show); i++
        }
        return out
    }

    private fun openedOk(a: UserAction) = a.actionType == ActionType.OPENED && (LaunchResults.succeeded(a.launchResult) || a.launchResult == LaunchResults.DEMO_NO_APP)
    private fun held(r: NotificationDecisionRecord) = r.routing == "DEFER" || r.routing == "FILTER"

    fun totals(sessions: List<FocusSession>, decisions: List<NotificationDecisionRecord>, actions: List<UserAction>, from: Long, to: Long, now: Long): ReportTotals {
        val ss = sessions.filter { it.sessionStartMs in from until to }
        val finished = ss.filter { it.state.isFinished }
        val focus = ss.sumOf { TimerMath.focusedMs(it, now) / 60_000L }.toInt()
        val ds = decisions.filter { it.phase == "FOCUS" && it.postedAtMs in from until to }
        val opens = actions.filter { openedOk(it) && it.actionTimeMs in from until to }
        return ReportTotals(focus, finished.size, finished.count { it.state == SessionState.COMPLETED }, finished.count { it.state == SessionState.ENDED_EARLY },
            if (finished.isEmpty()) null else (finished.sumOf { TimerMath.focusedMs(it, now) / 60_000L } / finished.size).toInt(),
            ds.size, ds.count(::held), ds.count { it.routing == "REVIEW" }, ds.count { it.routing == "DELIVER" },
            opens.size, opens.count { it.sessionState == "FOCUS" })
    }

    fun compute(period: Period, anchorMs: Long, now: Long, sessions: List<FocusSession>, decisions: List<NotificationDecisionRecord>, actions: List<UserAction>,
                checkins: List<EnergyCheckIn>, tz: TimeZone = TimeZone.getDefault(), locale: Locale = Locale.getDefault()): Report {
        val r = range(period, anchorMs, now, tz, locale)
        val b = buckets(r, tz, locale)
        fun <T> perBucket(items: List<T>, time: (T) -> Long): List<List<T>> = b.map { k -> items.filter { time(it) >= k.start && time(it) < k.end } }
        val ss = perBucket(sessions, FocusSession::sessionStartMs)
        val ds = perBucket(decisions.filter { it.phase == "FOCUS" }, NotificationDecisionRecord::postedAtMs)
        val opens = perBucket(actions.filter(::openedOk), UserAction::actionTimeMs)
        val en = perBucket(checkins, EnergyCheckIn::timestampMs)
        val totals = totals(sessions, decisions, actions, r.start, r.end, now)
        val prev = shift(r, -1, now, tz, locale)
        val prevTotals = totals(sessions, decisions, actions, prev.start, prev.start + (r.elapsedEnd - r.start), now).takeIf { it.hasData }
        val inRange = decisions.filter { it.postedAtMs in r }
        val focusDecisions = inRange.filter { it.phase == "FOCUS" }
        val openedByApp = actions.filter { openedOk(it) && it.actionTimeMs in r }.groupingBy { it.notificationId }.eachCount()
        val apps = focusDecisions.groupBy { it.packageName }.map { (p, l) ->
            AppStat(p, l.first().appLabel.ifBlank { p.substringAfterLast('.') }, l.size, l.count(::held), l.sumOf { d -> d.notificationId?.let { openedByApp[it] } ?: 0 })
        }.sortedByDescending { it.received }
        return Report(r, b,
            ss.map { l -> l.sumOf { TimerMath.focusedMs(it, now) / 60_000L }.toInt() },
            ds.map { it.size }, ds.map { l -> l.count(::held) }, opens.map { it.size }, opens.map { l -> l.count { it.sessionState == "FOCUS" } },
            en.map { l -> if (l.isEmpty()) null else l.map { it.score }.average().toFloat() },
            ss.map { l -> l.filter { it.state.isFinished }.let { f -> if (f.isEmpty()) null else f.count { it.state == SessionState.COMPLETED }.toFloat() / f.size } },
            totals, prevTotals, apps,
            listOf("LOW", "MEDIUM", "HIGH", "UNKNOWN").associateWith { k -> focusDecisions.count { it.focusRisk == k } },
            sessions.filter { it.sessionStartMs in r && it.state.isFinished }.sortedBy { it.sessionStartMs }
                .map { SessionPoint(it.sessionStartMs, (TimerMath.focusedMs(it, now) / 60_000L).toInt(), TimerMath.plannedMinutes(it), it.state, it.id) })
    }

    /** Plain-text summary for sharing. Contains totals only — no notification titles, text or senders. */
    fun shareText(rep: Report): String {
        val t = rep.totals
        return buildString {
            appendLine("Attune ${rep.range.period.name.lowercase()} report · ${rep.range.label}${if (rep.range.isCurrent) " (so far)" else ""}")
            appendLine("Focus time: ${TimerMath.formatMinutes(t.focusMin)} across ${t.sessions} finished session${if (t.sessions == 1) "" else "s"}")
            t.completionRate?.let { appendLine("Completed as planned: ${(it * 100).roundToInt()}% (${t.completed} of ${t.sessions})") }
            appendLine("Notifications during focus: ${t.received} (held ${t.held}, kept visible for review ${t.review}, allowed ${t.allowed})")
            appendLine("Opened from Attune during focus: ${t.openedDuringFocus}")
            appendLine("Counts only; no notification content is included. Generated on-device by Attune.")
        }
    }
}

/** Selects either real or demo records — never a mix — for reports, the profile and coaching. */
data class AnalyticsInput(
    val sessions: List<FocusSession>, val decisions: List<NotificationDecisionRecord>, val actions: List<UserAction>,
    val checkins: List<EnergyCheckIn>, val feedback: List<FeedbackEvent>
) {
    companion object {
        fun select(sessions: List<FocusSession>, decisions: List<NotificationDecisionRecord>, actions: List<UserAction>,
                   checkins: List<EnergyCheckIn>, feedback: List<FeedbackEvent>, demo: Boolean) = AnalyticsInput(
            sessions.filter { it.isDemo == demo }, decisions.filter { it.isDemo == demo }, actions.filter { it.isDemo == demo },
            checkins.filter { it.isDemo == demo }, feedback.filter { it.isDemo == demo })
    }
}
