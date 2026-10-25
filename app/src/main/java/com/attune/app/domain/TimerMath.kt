package com.attune.app.domain
import com.attune.core.model.FocusSession
import com.attune.core.model.SessionState

/** All timer values are derived from stored timestamps so the timer survives navigation, recreation and process death. */
object TimerMath {
    fun plannedMinutes(s: FocusSession): Int =
        s.plannedDurationMin.takeIf { it > 0 } ?: ((s.plannedEndMs - s.sessionStartMs) / 60_000L).toInt().coerceAtLeast(1)
    fun plannedMs(s: FocusSession) = plannedMinutes(s) * 60_000L

    /** Milliseconds actually spent focusing (excludes pauses/breaks). */
    fun focusedMs(s: FocusSession, now: Long): Long {
        val end = s.actualEndMs ?: now
        val openPause = if ((s.state == SessionState.PAUSED || s.state == SessionState.BREAK) && s.breakStartMs != null) (end - s.breakStartMs!!).coerceAtLeast(0) else 0L
        return (end - s.sessionStartMs - s.totalPausedMs - openPause).coerceIn(0L, plannedMs(s))
    }
    fun remainingMs(s: FocusSession, now: Long) = (plannedMs(s) - focusedMs(s, now)).coerceAtLeast(0)
    fun progress(s: FocusSession, now: Long) = (focusedMs(s, now).toFloat() / plannedMs(s)).coerceIn(0f, 1f)
    /** Wall-clock time when a running session reaches its planned duration. */
    fun dueAtMs(s: FocusSession) = s.sessionStartMs + s.totalPausedMs + plannedMs(s)
    fun isDue(s: FocusSession, now: Long) = s.state == SessionState.FOCUS && now >= dueAtMs(s)

    fun formatClock(ms: Long): String { val t = ms / 1000; val h = t / 3600; val m = (t % 3600) / 60; val sec = t % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec) }
    fun formatMinutes(min: Int): String = if (min >= 60) "${min / 60}h ${min % 60}m" else "${min}m"
}
