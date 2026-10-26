package com.attune.app.data
import android.app.PendingIntent
import androidx.room.withTransaction
import com.attune.app.capture.CapturedNotification
import com.attune.app.domain.LaunchResults
import com.attune.app.domain.TimerMath
import com.attune.core.data.db.AttuneDatabase
import com.attune.core.data.db.dao.*
import com.attune.core.model.*
import com.attune.prediction.AttentionPredictor
import com.attune.prediction.model.*
import com.attune.prediction.decision.DecisionPolicy
import com.attune.prediction.decision.NotificationDecision
import com.attune.prediction.decision.Routing
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Calendar
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import com.attune.core.model.Prediction as PredictionRow
import com.attune.core.model.RiskLevel as RowRisk
import com.attune.prediction.model.Prediction as EnginePrediction

/** Result of saving a notification to the queue. [duplicate] = a repeated callback for an item already queued unchanged. */
data class Ingested(val id: String, val duplicate: Boolean)

sealed interface DecisionResult {
    data class Done(val action: ActionType, val launchResult: String?) : DecisionResult
    data class LaunchFailed(val message: String) : DecisionResult
    data class AlreadyHandled(val status: NotificationStatus) : DecisionResult
    data object NotFound : DecisionResult
    /** "Show now" posted a replacement notification (not the original). */
    data object Released : DecisionResult
    data class ReleaseFailed(val message: String) : DecisionResult
}

@Singleton
class QueueRepository @Inject constructor(
    private val db: AttuneDatabase,
    private val notifDao: QueuedNotificationDao,
    private val actionDao: UserActionDao,
    private val predDao: PredictionDao,
    private val feedbackDao: FeedbackDao,
    private val profileDao: AttentionProfileSummaryDao,
    private val sessions: FocusSessionRepository,
    private val settings: SettingsRepository,
    private val launcher: NotificationLauncher,
    private val cache: LaunchIntentCache,
    private val predictor: AttentionPredictor,
    private val timeline: TimelineRepository,
    private val releaser: ReleaseNotifier
) {
    private val mutex = Mutex()
    /** sbn key -> grouped card id, so updates to a merged notification don't inflate the group count. */
    private val mergedKeys = ConcurrentHashMap<String, String>()

    /** Queue items respecting the demo toggle (demo rows are hidden, not deleted, when demo mode is off). */
    val items: Flow<List<QueuedNotification>> = combine(notifDao.observeAll(), settings.settings) { l, s -> visibleItems(l, s.demoEnabled) }
    fun observe(id: String) = notifDao.observeById(id)
    fun actionsFor(id: String) = actionDao.observeForNotification(id)

    /**
     * Stores a real notification the decision engine routed to DEFER, REVIEW or FILTER. Returns the queue item id, or null
     * Returns the queue item; a repeated callback with unchanged content is reported as a duplicate and not written again.
     * REVIEW items are copies: the original Android notification stays visible. Throws if the database write fails.
     */
    suspend fun ingest(c: CapturedNotification, decision: NotificationDecision, decisionId: String?, session: FocusSession, contentIntent: PendingIntent?, groupRepeated: Boolean): Ingested = mutex.withLock {
        val now = System.currentTimeMillis()
        val status = if (decision.routing == Routing.FILTER) NotificationStatus.FILTERED else NotificationStatus.PENDING
        val keptVisible = decision.routing == Routing.REVIEW
        val why = decision.reasons.firstOrNull()?.text
        // Data minimisation: sensitive content (codes, security alerts, money) is never written to the database. Routing needed the
        // text only in memory; the queue keeps package, category and the sensitivity marker.
        val sensitive = decision.sensitive
        val title = storable(sensitive, c.title)
        val preview = storable(sensitive, c.previewText)
        val existing = notifDao.findByDedupKey(c.dedupKey) ?: mergedKeys[c.sbnKey]?.let { notifDao.getById(it) }
        if (existing != null) {
            val same = existing.title == title && existing.previewText == preview
            return@withLock when {
                existing.status.isOpen && same -> { cache.put(existing.id, contentIntent); Ingested(existing.id, duplicate = true) } // repeated callback
                existing.status.isOpen -> Ingested(existing.copy(title = title, previewText = preview, lastUpdatedMs = now, sourceRemoved = false, textScrubbed = sensitive,
                    focusRisk = decision.risk.name, decisionId = decisionId ?: existing.decisionId, isSensitive = decision.sensitive, keptVisible = keptVisible, ruleApplied = why)
                    .also { notifDao.upsert(it); cache.put(it.id, contentIntent) }.id, false)
                else -> existing.copy(title = title, previewText = preview, lastUpdatedMs = now, postTimeMs = c.postTimeMs, status = status,
                    sessionId = session.id, groupCount = 1, sourceRemoved = false, statusChangedMs = now, ruleApplied = why, focusRisk = decision.risk.name,
                    decisionId = decisionId, isSensitive = sensitive, keptVisible = keptVisible, textScrubbed = sensitive)
                    .also { notifDao.upsert(it); cache.put(it.id, contentIntent) }.id.let { Ingested(it, false) }
            }
        }
        if (groupRepeated && decision.routing == Routing.DEFER && !decision.sensitive) {
            notifDao.findPendingFromPackage(c.packageName, session.id)?.takeIf { !it.keptVisible && !it.isSensitive }?.let { g ->
                val merged = g.copy(title = title ?: g.title, previewText = preview ?: g.previewText, groupCount = g.groupCount + 1,
                    lastUpdatedMs = now, sbnKey = c.sbnKey, sourceRemoved = false)
                notifDao.upsert(merged); mergedKeys[c.sbnKey] = g.id; cache.put(g.id, contentIntent)
                return@withLock Ingested(g.id, false)
            }
        }
        val item = QueuedNotification(UUID.randomUUID().toString(), session.id, c.packageName, decision.category, title, preview,
            c.dedupKey, c.postTimeMs, status, isGroupSummary = c.isGroupSummary, appLabel = c.appLabel, sbnKey = c.sbnKey,
            lastUpdatedMs = now, ruleApplied = why, androidCategory = c.androidCategory, statusChangedMs = if (status == NotificationStatus.FILTERED) now else null,
            decisionId = decisionId, focusRisk = decision.risk.name, isSensitive = sensitive, keptVisible = keptVisible, textScrubbed = sensitive)
        notifDao.upsert(item); cache.put(item.id, contentIntent)
        Ingested(item.id, false)
    }

    /**
     * Privacy retention (see docs/DATA_AND_PRIVACY.md): title/preview are removed [hours] after the item was handled, or — for items
     * nobody ever opened — [hours] after its focus session ended; as a backstop, anything older than [MAX_PENDING_TEXT_DAYS] days goes
     * regardless. Returns how many items were scrubbed.
     */
    suspend fun scrubOldText(hours: Int = TEXT_RETENTION_HOURS, nowMs: Long = System.currentTimeMillis()): Int {
        val cutoff = nowMs - hours * 3_600_000L
        return notifDao.scrubExpiredText(cutoff, nowMs - MAX_PENDING_TEXT_DAYS * 86_400_000L)
    }
    suspend fun scrubAllText() = notifDao.scrubAllRealText()
    val storedTextCount = notifDao.observeRealWithText()

    suspend fun exists(id: String) = notifDao.getById(id) != null

    /** The original notification could not be confirmed removed, so this item is a copy of something still in the shade. */
    suspend fun markStillVisible(id: String) = mutex.withLock { notifDao.getById(id)?.let { notifDao.upsert(it.copy(keptVisible = true, wasSuppressed = false)) } }

    suspend fun markSuppressed(id: String) = mutex.withLock { notifDao.getById(id)?.let { notifDao.upsert(it.copy(wasSuppressed = true)) } }

    /** The sending app withdrew its notification (e.g. read elsewhere). Keep the queue item but flag it. */
    suspend fun markSourceRemoved(sbnKey: String) = mutex.withLock {
        val n = notifDao.findByDedupKey("sbn:$sbnKey") ?: mergedKeys[sbnKey]?.let { notifDao.getById(it) } ?: return@withLock
        if (n.status.isOpen) notifDao.upsert(n.copy(sourceRemoved = true))
    }

    suspend fun insertDemo(n: QueuedNotification) = mutex.withLock { notifDao.upsert(n) }

    // ---------- predictions ----------

    suspend fun predict(n: QueuedNotification): EnginePrediction {
        val history = feedbackDao.observationsWithCategory(n.isDemo).map { UserObservation(it.category, it.timeAway, it.recovery) }
        val s = sessions.getActive(); val now = System.currentTimeMillis()
        val ctx = SessionContext(FocusSessionRepository.phaseOf(s),
            s?.let { TimerMath.focusedMs(it, now) / 60_000f } ?: 0f, s?.let { TimerMath.remainingMs(it, now) / 60_000f } ?: 0f)
        val p = predictor.predict(PredictionInput(NotificationContext(n.packageName, n.category, n.title, n.previewText, n.postTimeMs, n.groupCount),
            ctx, history, Calendar.getInstance().get(Calendar.HOUR_OF_DAY)))
        if (n.status.isOpen) predDao.upsert(PredictionRow("pred_${n.id}", n.id, RowRisk.valueOf(p.risk.name), p.timeAwayMin, p.recoveryMin, p.explanation,
            p.confidence, p.observationCount, p.delayScenario, true, n.isDemo, now, p.modelVersion,
            p.factors.joinToString("\n") { "${it.label}|${it.detail}|${it.source}" }, ctx.phase.name))
        return p
    }
    suspend fun storedPrediction(id: String) = predDao.getForNotification(id)

    // ---------- decisions ----------

    /**
     * "Show now". Posts a replacement notification from Attune and marks the item RELEASED. Idempotent: it runs under the queue mutex and
     * only for open items, so a double tap posts once; the notification id is derived from the item id, so even a repeat would replace
     * rather than add. It never touches the original (Android can't restore it) and never creates a queue row (the listener ignores
     * Attune's own notifications). Items whose original is still in the shade (REVIEW) are refused to avoid a duplicate.
     */
    suspend fun release(id: String): DecisionResult = mutex.withLock {
        val n = notifDao.getById(id) ?: return@withLock DecisionResult.NotFound
        if (!n.status.isOpen) return@withLock DecisionResult.AlreadyHandled(n.status)
        if (n.keptVisible && !n.sourceRemoved) return@withLock DecisionResult.ReleaseFailed("The original is still in your notifications, so there is nothing to show again.")
        if (!releaser.post(n)) return@withLock DecisionResult.ReleaseFailed("Attune couldn't post a notification. Check that notifications from Attune are allowed.")
        notifDao.updateStatus(id, NotificationStatus.RELEASED, System.currentTimeMillis())
        DecisionResult.Released
    }

    suspend fun openNow(id: String): DecisionResult = decide(id, ActionType.OPENED)
    suspend fun delay(id: String): DecisionResult = decide(id, ActionType.DELAYED)
    suspend fun dismiss(id: String): DecisionResult = decide(id, ActionType.DISMISSED)

    private suspend fun decide(id: String, type: ActionType): DecisionResult = mutex.withLock {
        val n = notifDao.getById(id) ?: return@withLock DecisionResult.NotFound
        val allowed = n.status.isOpen && !(type == ActionType.DELAYED && n.status == NotificationStatus.DELAYED)
        if (!allowed) return@withLock DecisionResult.AlreadyHandled(n.status)
        val session = sessions.getActive(); val phase = FocusSessionRepository.phaseOf(session)
        val pred = predDao.getForNotification(id)
        val now = System.currentTimeMillis()
        val launch = if (type == ActionType.OPENED) launcher.launch(n) else null
        val action = UserAction(UUID.randomUUID().toString(), id, pred?.id, type, now, n.isDemo, session?.id, phase.name, n.category,
            launchAttempted = type == ActionType.OPENED, launchResult = launch,
            launchedAtMs = if (LaunchResults.succeeded(launch)) now else null,
            predictedTimeAwayMin = pred?.timeAwayMin, predictedRecoveryMin = pred?.recoveryMin)
        if (launch == LaunchResults.FAILED) {
            actionDao.upsert(action) // the attempt is recorded; the item stays in the queue so it can be retried, delayed or dismissed
            return@withLock DecisionResult.LaunchFailed(
                if (n.sourceRemoved) "Couldn't open it — the app withdrew this notification. Open the app yourself if you still need it."
                else "Couldn't open ${n.appLabel.ifBlank { "the app" }}. It's still in your queue.")
        }
        val newStatus = when (type) { ActionType.OPENED -> NotificationStatus.OPENED; ActionType.DELAYED -> NotificationStatus.DELAYED; ActionType.DISMISSED -> NotificationStatus.DISMISSED }
        db.withTransaction {
            actionDao.upsert(action)
            notifDao.updateStatus(id, newStatus, now)
            val e = profileDao.getByCategory(n.category) ?: AttentionProfileSummary(n.category)
            profileDao.upsert(when (type) { ActionType.OPENED -> e.copy(openCount = e.openCount + 1); ActionType.DELAYED -> e.copy(delayCount = e.delayCount + 1)
                ActionType.DISMISSED -> e.copy(dismissCount = e.dismissCount + 1) }.copy(lastUpdatedMs = now))
        }
        if (type == ActionType.OPENED && phase == SessionPhase.FOCUS && session != null) sessions.recordInterruption(session.id)
        timeline.log(session?.id ?: n.sessionId.takeIf { it != "demo" }, when (type) { ActionType.OPENED -> TimelineType.OPENED; ActionType.DELAYED -> TimelineType.DELAYED; ActionType.DISMISSED -> TimelineType.DISMISSED },
            n.appLabel.ifBlank { null }, n.isDemo, now)
        DecisionResult.Done(type, launch)
    }

    /** Called when Attune returns to the foreground: closes the measured "time away" for the latest Open Now launch. */
    suspend fun onAppReturned() {
        val now = System.currentTimeMillis()
        val a = actionDao.latestAwaitingReturn(now - 3 * 3_600_000L) ?: return
        if (now - (a.launchedAtMs ?: now) >= 3_000) actionDao.markReturned(a.id, now)
    }

    suspend fun saveFeedback(notificationId: String, timeAway: Float?, recovery: Float?) = mutex.withLock {
        val n = notifDao.getById(notificationId) ?: return@withLock
        val action = actionDao.observeForNotification(notificationId).first().firstOrNull { it.actionType == ActionType.OPENED }
        feedbackDao.upsert(Feedback(UUID.randomUUID().toString(), notificationId, action?.id ?: "", timeAway, recovery, wasSkipped = timeAway == null, isDemo = n.isDemo))
        if (timeAway != null && recovery != null) {
            val e = profileDao.getByCategory(n.category) ?: AttentionProfileSummary(n.category); val cnt = e.observationCount + 1
            profileDao.upsert(e.copy(observationCount = cnt, avgTimeAwayMin = e.avgTimeAwayMin + (timeAway - e.avgTimeAwayMin) / cnt,
                avgRecoveryMin = e.avgRecoveryMin + (recovery - e.avgRecoveryMin) / cnt, lastUpdatedMs = System.currentTimeMillis()))
        }
    }
    fun feedback() = feedbackDao.observeAll()

    companion object {
        val TEXT_RETENTION_HOURS = DecisionPolicy().textRetentionHours; val MAX_PENDING_TEXT_DAYS = DecisionPolicy().maxPendingTextDays
        /** Sample rows are hidden unless demo mode was explicitly turned on; real recorded notifications are always shown. */
        fun visibleItems(all: List<QueuedNotification>, demoEnabled: Boolean) = if (demoEnabled) all else all.filter { !it.isDemo }
        /** What may be written to the database: nothing at all for sensitive notifications. */
        fun storable(sensitive: Boolean, text: String?): String? = if (sensitive) null else text
    }
}
