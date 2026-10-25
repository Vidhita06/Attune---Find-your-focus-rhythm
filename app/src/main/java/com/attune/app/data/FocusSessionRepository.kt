package com.attune.app.data
import android.content.Context
import com.attune.app.domain.TimerMath
import com.attune.app.service.FocusTimerService
import com.attune.core.data.db.dao.FocusSessionDao
import com.attune.core.data.db.dao.UserActionDao
import com.attune.core.model.FocusSession
import com.attune.core.model.SessionState
import com.attune.prediction.model.SessionPhase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Single writer for focus sessions. Every transition is validated, so repeated taps cannot create duplicate sessions. */
@Singleton
class FocusSessionRepository @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val dao: FocusSessionDao,
    private val actionDao: UserActionDao,
    private val timeline: TimelineRepository,
    private val summary: FocusSummaryNotifier
) {
    private val mutex = Mutex()
    val active: Flow<FocusSession?> = dao.observeActive()
    val latest: Flow<FocusSession?> = dao.observeLatestReal()
    val all: Flow<List<FocusSession>> = dao.observeAllIncDemo()

    suspend fun getActive(): FocusSession? = dao.getAllActive().firstOrNull()

    suspend fun start(minutes: Int): FocusSession = mutex.withLock {
        getActive()?.let { return@withLock it }
        val now = System.currentTimeMillis(); val min = minutes.coerceIn(1, 180)
        FocusSession(UUID.randomUUID().toString(), now, now + min * 60_000L, state = SessionState.FOCUS, plannedDurationMin = min)
            .also { dao.upsert(it); timeline.log(it.id, TimelineType.START, "$min min planned", atMs = now) }
    }.also { FocusTimerService.start(ctx) }

    suspend fun pause(asBreak: Boolean = false) {
        val paused = mutex.withLock {
            val s = getActive() ?: return@withLock null
            if (s.state != SessionState.FOCUS) return@withLock null
            s.copy(state = if (asBreak) SessionState.BREAK else SessionState.PAUSED, breakStartMs = System.currentTimeMillis(), pauseCount = s.pauseCount + 1)
                .also { dao.upsert(it); timeline.log(it.id, if (asBreak) TimelineType.BREAK else TimelineType.PAUSE) }
        } ?: return
        runCatching { summary.onSessionStopped(paused, paused.state) }
    }

    suspend fun resume() = mutex.withLock {
        val s = getActive() ?: return@withLock
        if (s.state != SessionState.PAUSED && s.state != SessionState.BREAK) return@withLock
        val now = System.currentTimeMillis()
        val paused = s.totalPausedMs + (now - (s.breakStartMs ?: now)).coerceAtLeast(0)
        val r = s.copy(state = SessionState.FOCUS, breakStartMs = null, totalPausedMs = paused)
        dao.upsert(r.copy(plannedEndMs = TimerMath.dueAtMs(r)))
        timeline.log(s.id, TimelineType.RESUME, "after ${((now - (s.breakStartMs ?: now)) / 60_000).coerceAtLeast(0)} min")
    }.also { FocusTimerService.start(ctx) }

    /** Ends the session. It is COMPLETED only if the planned focus time was actually reached; otherwise ENDED_EARLY. */
    suspend fun end(): FocusSession? = mutex.withLock {
        val s = getActive() ?: return@withLock null
        val now = System.currentTimeMillis()
        if (TimerMath.isDue(s, now)) return@withLock finish(s, SessionState.COMPLETED, TimerMath.dueAtMs(s))
        val closed = closePause(s, now)
        val state = if (TimerMath.remainingMs(closed, now) <= 0) SessionState.COMPLETED else SessionState.ENDED_EARLY
        finish(closed, state, now)
    }?.also { runCatching { summary.onSessionStopped(it, it.state) } }

    /** Idempotent: completes a running session whose planned time has passed, using the exact due time. */
    suspend fun completeIfDue(): FocusSession? = mutex.withLock {
        val now = System.currentTimeMillis()
        var completed: FocusSession? = null
        dao.getAllActive().forEachIndexed { i, s ->
            when {
                TimerMath.isDue(s, now) -> { val f = finish(s, SessionState.COMPLETED, TimerMath.dueAtMs(s)); if (i == 0) completed = f }
                i > 0 -> finish(closePause(s, now), SessionState.ENDED_EARLY, now) // defensive: only one active session may exist
            }
        }
        completed
    }.also { c -> if (c != null) runCatching { summary.onSessionStopped(c, SessionState.COMPLETED) } }

    suspend fun recordInterruption(sessionId: String) = mutex.withLock {
        dao.getById(sessionId)?.let { if (it.state.isActive) dao.upsert(it.copy(interruptionCount = it.interruptionCount + 1)) }
    }

    private fun closePause(s: FocusSession, now: Long) =
        if (s.breakStartMs != null && (s.state == SessionState.PAUSED || s.state == SessionState.BREAK))
            s.copy(totalPausedMs = s.totalPausedMs + (now - s.breakStartMs!!).coerceAtLeast(0), breakStartMs = null, state = SessionState.FOCUS) else s

    private suspend fun finish(s: FocusSession, state: SessionState, endMs: Long): FocusSession {
        val f = s.copy(state = state, actualEndMs = endMs, breakStartMs = null)
        dao.upsert(f); actionDao.setSessionOutcome(s.id, state.name)
        timeline.log(s.id, if (state == SessionState.COMPLETED) TimelineType.COMPLETED else TimelineType.ENDED_EARLY,
            "${TimerMath.focusedMs(f, endMs) / 60_000} of ${TimerMath.plannedMinutes(f)} min focused", atMs = endMs)
        return f
    }

    companion object {
        fun phaseOf(s: FocusSession?) = when (s?.state) {
            SessionState.FOCUS -> SessionPhase.FOCUS
            SessionState.PAUSED, SessionState.BREAK -> SessionPhase.PAUSED_OR_BREAK
            else -> SessionPhase.NO_SESSION
        }
    }
}
