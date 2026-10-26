package com.attune.app.capture

import com.attune.core.model.FocusSession
import com.attune.core.model.NotificationDecisionRecord
import com.attune.core.model.SessionState
import com.attune.prediction.decision.DecisionPolicy
import com.attune.prediction.decision.NotificationDecision
import com.attune.prediction.decision.Routing
import com.attune.prediction.model.SessionPhase
import com.attune.prediction.policy.IncomingNotification
import java.util.concurrent.ConcurrentHashMap

/** Everything Attune needs from one posted StatusBarNotification, without Android types (so the sequencing is unit-testable). */
data class PostedNotification(
    val captured: CapturedNotification,
    val incoming: IncomingNotification,
    /** StatusBarNotification.groupKey, used to tidy up group summaries whose children were all held. */
    val groupKey: String?,
    val isOwnPackage: Boolean,
    /** Opaque handle to the notification's PendingIntent (kept so "Open now" can use the original target). */
    val contentIntent: Any? = null
)

data class ActiveNotification(val key: String, val isGroupSummary: Boolean, val clearable: Boolean, val ongoing: Boolean)

/** The NotificationListenerService operations Attune uses. Implemented by FocusListenerService; faked in tests. */
interface ListenerOps {
    /** True only between onListenerConnected and onListenerDisconnected. Listener APIs must not be called otherwise. */
    val isConnected: Boolean
    /** Requests removal of the notification with this StatusBarNotification key. Throws if the request fails. */
    fun cancel(key: String)
    /**
     * cancelNotification() is asynchronous and returns nothing. Suspends until Android reports that [key] was removed (or a short
     * timeout passes) and returns whether the removal was actually observed. Never claim a notification was removed without it.
     */
    suspend fun awaitRemoved(key: String): Boolean
    /** Currently active notifications of [packageName] in group [groupKey]. */
    fun activeInGroup(packageName: String, groupKey: String): List<ActiveNotification>
}

sealed interface SaveResult {
    data class Saved(val queueId: String, val duplicate: Boolean) : SaveResult
    data class Failed(val reason: String) : SaveResult
}

/** Data-layer operations the processor needs. Implemented with the real repositories in FocusListenerService. */
interface ProcessorPorts {
    suspend fun activeSession(): FocusSession?
    suspend fun evaluate(p: PostedNotification, phase: SessionPhase, session: FocusSession?): Pair<NotificationDecision, NotificationDecisionRecord?>
    suspend fun save(p: PostedNotification, d: NotificationDecision, decisionId: String, session: FocusSession, groupRepeated: Boolean): SaveResult
    suspend fun attach(decisionId: String, queueId: String)
    /** Called only once Android has confirmed the original notification is gone. */
    suspend fun markSuppressed(queueId: String)
    /** The original could not be (confirmed) removed: the queue item is flagged as still visible instead of looking "held". */
    suspend fun markStillVisible(queueId: String)
    suspend fun removalEnabled(): Boolean
    suspend fun groupRepeated(): Boolean
    suspend fun timeline(sessionId: String, type: String, appLabel: String, atMs: Long)
}

/** CONFIRMED = Android reported the removal. UNCONFIRMED = requested but never observed. ATTEMPTED = requested without waiting (group summaries). */
enum class CancelStatus { NOT_NEEDED, ATTEMPTED, CONFIRMED, UNCONFIRMED, SKIPPED, FAILED }

/**
 * Counts messages per conversation in a short window, in memory only (nothing is stored). Repeated callbacks and rank updates of the
 * same post (same key and post time) count once. Conversations are identified by the app's conversation id when it exposes one,
 * else by package + title; with neither, no burst is detected.
 */
class BurstTracker(private val windowMs: Long = DecisionPolicy().burstWindowMs, private val heldWindowMs: Long = DecisionPolicy().heldFollowUpWindowMs) {
    private val seen = HashMap<String, MutableList<Pair<String, Long>>>()
    private val held = HashMap<String, Long>()
    private fun convKey(p: PostedNotification) = (p.incoming.conversationId ?: p.incoming.title)?.let { "${p.captured.packageName}|$it" }
    /** Attune held a message from this conversation inside the follow-up window. */
    @Synchronized fun wasHeldRecently(p: PostedNotification, nowMs: Long): Boolean = convKey(p)?.let { k -> held[k]?.let { nowMs - it <= heldWindowMs } } ?: false
    @Synchronized fun markHeld(p: PostedNotification, nowMs: Long) { convKey(p)?.let { held[it] = nowMs }; if (held.size > 100) held.values.removeAll { nowMs - it > heldWindowMs } }
    @Synchronized fun record(p: PostedNotification, nowMs: Long): Int {
        val key = convKey(p) ?: return 1
        val list = seen.getOrPut(key) { mutableListOf() }
        list.removeAll { nowMs - it.second > windowMs }
        val stamp = "${p.captured.sbnKey}|${p.captured.postTimeMs}"
        if (list.none { it.first == stamp }) list += stamp to nowMs
        if (seen.size > 100) seen.values.removeAll { l -> l.all { nowMs - it.second > windowMs } }
        return list.size
    }
}

/** Diagnostic outcome of one callback. Holds the app name and package only — never title, text, sender or links. */
data class ProcessOutcome(
    val atMs: Long, val packageName: String, val appLabel: String, val focus: String, val routing: Routing?,
    val saved: Boolean?, val duplicate: Boolean, val cancel: CancelStatus, val reason: String, val summariesCancelled: Int = 0,
    /** The listener was not connected when a cancellation was due. */
    val listenerDown: Boolean = false
) {
    /** One privacy-safe state per callback, for the diagnostics screen and the debug log. Contains no notification content. */
    val state: HoldState get() = when {
        listenerDown -> HoldState.LISTENER_DISCONNECTED
        focus != "active" -> HoldState.FOCUS_INACTIVE
        saved == false -> HoldState.QUEUE_PERSIST_FAILED
        routing == Routing.DELIVER || routing == Routing.IGNORE || routing == null -> HoldState.ALLOWED
        routing == Routing.REVIEW -> HoldState.REVIEW_VISIBLE
        cancel == CancelStatus.CONFIRMED -> HoldState.CANCELLATION_CONFIRMED
        cancel == CancelStatus.FAILED -> HoldState.CANCELLATION_FAILED
        cancel == CancelStatus.UNCONFIRMED || cancel == CancelStatus.ATTEMPTED -> HoldState.CANCELLATION_ATTEMPTED
        else -> HoldState.HOLD_PERSISTED
    }
}

/**
 * What happened to one notification. HOLD_PERSISTED = saved to the queue but no cancellation was made (setting off / not clearable).
 * CANCELLATION_ATTEMPTED = requested but Android never confirmed removal. CANCELLATION_CONFIRMED = Android reported it gone.
 */
enum class HoldState { LISTENER_DISCONNECTED, FOCUS_INACTIVE, ALLOWED, REVIEW_VISIBLE, QUEUE_PERSIST_FAILED, HOLD_PERSISTED, CANCELLATION_ATTEMPTED, CANCELLATION_CONFIRMED, CANCELLATION_FAILED }

/**
 * Decides what to do with one posted notification and performs the steps in a safe order:
 *   evaluate → (HOLD/FILTER) save to the Room queue → only if the save succeeded and the listener is connected, cancel the
 *   original by its StatusBarNotification key → if no visible child of its group remains, also remove the group summary.
 * ALLOW and REVIEW never cancel anything. Every path returns a [ProcessOutcome] for diagnostics and never throws.
 */
class NotificationProcessor(private val ports: ProcessorPorts, private val ops: ListenerOps, private val now: () -> Long = System::currentTimeMillis,
                            policy: DecisionPolicy = DecisionPolicy()) {
    /** "package|groupKey" of groups in which Attune has held a child during the current session. */
    private val heldGroups = ConcurrentHashMap<String, String>()
    private val bursts = BurstTracker(policy.burstWindowMs, policy.heldFollowUpWindowMs)

    suspend fun process(posted: PostedNotification): ProcessOutcome {
        val burst = if (posted.isOwnPackage || posted.incoming.isGroupSummary) 1 else bursts.record(posted, now())
        val prevHeld = !posted.isOwnPackage && !posted.incoming.isGroupSummary && bursts.wasHeldRecently(posted, now())
        val p = if (burst > 1 || prevHeld) posted.copy(incoming = posted.incoming.copy(burstCount = burst, previouslyHeld = prevHeld)) else posted
        val c = p.captured
        fun out(focus: String, routing: Routing?, saved: Boolean?, cancel: CancelStatus, reason: String, dup: Boolean = false, summaries: Int = 0, down: Boolean = false) =
            ProcessOutcome(now(), c.packageName, c.appLabel, focus, routing, saved, dup, cancel, reason, summaries, down)

        if (p.isOwnPackage) return out("—", Routing.IGNORE, null, CancelStatus.NOT_NEEDED, "Attune's own notification (never processed)")
        val session = try { ports.activeSession() } catch (e: Exception) { return out("unknown", null, null, CancelStatus.SKIPPED, "Couldn't read focus state (${e.javaClass.simpleName})") }
        val focus = when (session?.state) { SessionState.FOCUS -> "active"; SessionState.PAUSED, SessionState.BREAK -> "paused"; else -> "inactive" }
        val phase = if (session?.state == SessionState.FOCUS) SessionPhase.FOCUS else if (session != null) SessionPhase.PAUSED_OR_BREAK else SessionPhase.NO_SESSION

        if (p.incoming.isGroupSummary) return handleSummary(p, session, focus) { f, r, s, cs, why, n -> out(f, r, s, cs, why, summaries = n) }

        val (d, rec) = try { ports.evaluate(p, phase, session) } catch (e: Exception) {
            return out(focus, null, null, CancelStatus.SKIPPED, "Decision failed (${e.javaClass.simpleName}); original left visible")
        }
        if (rec == null || d.routing == Routing.IGNORE) return out(focus, d.routing, null, CancelStatus.NOT_NEEDED, d.reasons.firstOrNull()?.text ?: "Not evaluated")
        if (session == null || phase != SessionPhase.FOCUS) return out(focus, d.routing, null, CancelStatus.NOT_NEEDED, "Focus $focus — normal behaviour")

        if (d.routing == Routing.DELIVER) {
            if (rec.updateCount == 1) runCatching { ports.timeline(session.id, "ALLOWED", c.appLabel, c.postTimeMs) }
            return out(focus, d.routing, null, CancelStatus.NOT_NEEDED, "Allowed: ${d.reasons.firstOrNull()?.text ?: d.source.label}")
        }

        // REVIEW, DEFER and FILTER are saved to the queue first.
        val saved = try { ports.save(p, d, rec.id, session, ports.groupRepeated()) } catch (e: Exception) { SaveResult.Failed(e.javaClass.simpleName) }
        if (saved is SaveResult.Failed) return out(focus, d.routing, false, if (d.routing == Routing.REVIEW) CancelStatus.NOT_NEEDED else CancelStatus.SKIPPED,
            "Queue save failed (${saved.reason}); original left visible")
        saved as SaveResult.Saved
        runCatching { ports.attach(rec.id, saved.queueId) }
        if (!saved.duplicate && rec.updateCount == 1) runCatching {
            ports.timeline(session.id, when (d.routing) { Routing.DEFER -> "HELD"; Routing.FILTER -> "FILTERED"; else -> "REVIEW" }, c.appLabel, c.postTimeMs)
        }
        if (d.routing == Routing.REVIEW) return out(focus, d.routing, true, CancelStatus.NOT_NEEDED, "Uncertain or possibly important: kept visible and listed for review", saved.duplicate)

        // HOLD / FILTER: remove the original only now that the queue item exists.
        val removal = d.routing == Routing.FILTER || runCatching { ports.removalEnabled() }.getOrDefault(false)
        val skip = when {
            !removal -> "Removal from the shade is turned off in settings"
            !p.incoming.isClearable || p.incoming.isOngoing -> "Not clearable/ongoing; Android won't allow removal"
            !ops.isConnected -> "Listener not connected; cancel not attempted"
            else -> null
        }
        if (skip != null) { runCatching { ports.markStillVisible(saved.queueId) }; return out(focus, d.routing, true, CancelStatus.SKIPPED, skip, saved.duplicate, down = !ops.isConnected) }
        try { ops.cancel(c.sbnKey) } catch (e: Exception) {
            runCatching { ports.markStillVisible(saved.queueId) }
            return out(focus, d.routing, true, CancelStatus.FAILED, "cancelNotification failed (${e.javaClass.simpleName}); original may still be visible", saved.duplicate)
        }
        // cancelNotification() only *requests* removal. The item counts as suppressed once Android reports the removal.
        val removed = try { ops.awaitRemoved(c.sbnKey) } catch (e: Exception) { false }
        if (!removed) {
            runCatching { ports.markStillVisible(saved.queueId) }
            return out(focus, d.routing, true, CancelStatus.UNCONFIRMED, "Removal was requested but Android did not confirm it; the original may still be visible", saved.duplicate)
        }
        runCatching { ports.markSuppressed(saved.queueId) }
        bursts.markHeld(p, now())
        var summaries = 0
        if (p.groupKey != null) {
            heldGroups["${c.packageName}|${p.groupKey}"] = session.id
            summaries = cancelOrphanSummary(c.packageName, p.groupKey, exclude = c.sbnKey)
        }
        return out(focus, d.routing, true, CancelStatus.CONFIRMED, if (saved.duplicate) "Repeated callback: no new queue item; original removed again" else "Held in queue; removal confirmed by Android", saved.duplicate, summaries)
    }

    /** A group summary is never queued itself. During focus it is removed only if Attune held this group's children and none is still visible. */
    private suspend fun handleSummary(p: PostedNotification, session: FocusSession?, focus: String,
                                      out: (String, Routing?, Boolean?, CancelStatus, String, Int) -> ProcessOutcome): ProcessOutcome {
        val c = p.captured
        if (session?.state != SessionState.FOCUS || p.groupKey == null) return out(focus, Routing.IGNORE, null, CancelStatus.NOT_NEEDED, "Group summary; children are evaluated instead", 0)
        if (heldGroups["${c.packageName}|${p.groupKey}"] != session.id) return out(focus, Routing.IGNORE, null, CancelStatus.NOT_NEEDED, "Group summary; no held children", 0)
        if (!ops.isConnected) return out(focus, Routing.IGNORE, null, CancelStatus.SKIPPED, "Listener not connected; summary left", 0)
        val n = cancelOrphanSummary(c.packageName, p.groupKey, exclude = null)
        return out(focus, Routing.IGNORE, null, if (n > 0) CancelStatus.ATTEMPTED else CancelStatus.NOT_NEEDED,
            if (n > 0) "Group summary removed: all its notifications are held" else "Group summary kept: a notification in it is still visible", n)
    }

    private fun cancelOrphanSummary(pkg: String, groupKey: String, exclude: String?): Int {
        if (!ops.isConnected) return 0
        val active = runCatching { ops.activeInGroup(pkg, groupKey) }.getOrDefault(emptyList()).filter { it.key != exclude }
        if (active.any { !it.isGroupSummary }) return 0 // something in the group is still visible (e.g. an important message)
        return active.filter { it.isGroupSummary && it.clearable && !it.ongoing }.count { s -> runCatching { ops.cancel(s.key) }.isSuccess }
    }
}
