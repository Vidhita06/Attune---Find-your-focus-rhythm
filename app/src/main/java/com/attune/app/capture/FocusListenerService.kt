package com.attune.app.capture
import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.attune.app.BuildConfig
import com.attune.app.data.*
import com.attune.app.util.AppLabels
import com.attune.core.model.FocusSession
import com.attune.prediction.decision.NotificationDecision
import com.attune.prediction.model.SessionPhase
import com.attune.prediction.policy.IncomingNotification
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/**
 * Android entry point for notifications. It only extracts data and implements [ListenerOps] / [ProcessorPorts];
 * the order of operations (decide → save → cancel) lives in [NotificationProcessor] and is unit-tested.
 *
 * Android limits: this service sees a notification after it has been posted, so it may already have alerted.
 * cancelNotification() is best-effort and Attune cannot restore another app's notification afterwards.
 * Notification content is never logged; diagnostics contain package, decision and outcome only.
 */
@AndroidEntryPoint
class FocusListenerService : NotificationListenerService(), ListenerOps {
    @Inject lateinit var queue: QueueRepository
    @Inject lateinit var sessions: FocusSessionRepository
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var access: NotificationAccessMonitor
    @Inject lateinit var decisions: DecisionRepository
    @Inject lateinit var timelineRepo: TimelineRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /** Keys Attune cancelled itself, so the matching removal callback isn't mistaken for the app withdrawing it. */
    private val selfCancelled = ConcurrentHashMap<String, Long>()
    /** Completed by onNotificationRemoved when Android reports that a notification Attune asked to cancel is really gone. */
    private val pendingRemoval = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
    @Volatile private var lastCleanupMs = 0L
    @Volatile private var connected = false
    private val processor by lazy { NotificationProcessor(Ports(), this) }

    override val isConnected get() = connected

    override fun onListenerConnected() {
        connected = true; access.onListenerConnected(true); ListenerDiagnostics.onConnection(true); debug("listener connected")
        cleanupStoredText(force = true)
    }
    override fun onListenerDisconnected() {
        connected = false; access.onListenerConnected(false); ListenerDiagnostics.onConnection(false); debug("listener disconnected")
        runCatching { requestRebind(ComponentName(this, FocusListenerService::class.java)) }
    }

    override fun cancel(key: String) {
        check(connected) { "listener not connected" }
        selfCancelled[key] = System.currentTimeMillis()
        pendingRemoval[key] = CompletableDeferred()
        try { cancelNotification(key) } catch (e: Exception) { selfCancelled.remove(key); pendingRemoval.remove(key); throw e }
    }

    override suspend fun awaitRemoved(key: String): Boolean {
        val gone = pendingRemoval[key]?.let { withTimeoutOrNull(REMOVAL_TIMEOUT_MS) { it.await() } != null } ?: false
        pendingRemoval.remove(key)
        if (gone) return true
        // No callback arrived: it may still have been removed. Only the active list is authoritative.
        return connected && runCatching { (activeNotifications ?: emptyArray()).none { it.key == key } }.getOrDefault(false)
    }

    /** Privacy retention for text of items nobody opened; throttled, because this long-lived service is where it can run unattended. */
    private fun cleanupStoredText(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastCleanupMs < CLEANUP_INTERVAL_MS) return
        lastCleanupMs = now
        scope.launch { runCatching { queue.scrubOldText() } }
    }

    override fun activeInGroup(packageName: String, groupKey: String): List<ActiveNotification> =
        (activeNotifications ?: emptyArray()).filter { it.packageName == packageName && it.groupKey == groupKey }.map {
            ActiveNotification(it.key, (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0, it.isClearable,
                (it.notification.flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE)) != 0)
        }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val own = sbn.packageName == packageName // Attune's own timer/summary notifications: never processed (no recursion)
        if (own) return
        cleanupStoredText()
        ListenerDiagnostics.onCallback()
        val p = try { extract(sbn) } catch (e: Exception) { debug("${sbn.packageName} extraction failed (${e.javaClass.simpleName})"); return }
        scope.launch {
            val o = try { processor.process(p) } catch (e: Exception) {
                ProcessOutcome(System.currentTimeMillis(), sbn.packageName, p.captured.appLabel, "unknown", null, null, false, CancelStatus.SKIPPED, "Unexpected error (${e.javaClass.simpleName})")
            }
            ListenerDiagnostics.record(o); debug(ListenerDiagnostics.line(o))
        }
    }

    private fun extract(sbn: StatusBarNotification): PostedNotification {
        val n = sbn.notification
        val extras = n.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.takeIf { it.isNotBlank() }
        val text = (extras?.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras?.getCharSequence(Notification.EXTRA_TEXT))?.toString()?.takeIf { it.isNotBlank() }
            ?: latestMessageText(extras) // MessagingStyle apps may leave EXTRA_TEXT empty
        val ongoing = (n.flags and Notification.FLAG_ONGOING_EVENT) != 0 || (n.flags and Notification.FLAG_FOREGROUND_SERVICE) != 0
        val summary = (n.flags and Notification.FLAG_GROUP_SUMMARY) != 0
        val label = AppLabels.resolve(this, sbn.packageName)
        val conversationId = if (Build.VERSION.SDK_INT >= 26) n.shortcutId?.takeIf { it.isNotBlank() } else null
        return PostedNotification(CapturedNotification(sbn.packageName, label, title, text, sbn.postTime, sbn.key, n.category, summary),
            IncomingNotification(sbn.packageName, title, text, n.category, ongoing, summary, sbn.isClearable, conversationId, isPriorityConversation(sbn.key)),
            sbn.groupKey, false, n.contentIntent)
    }

    /**
     * Android 11+ lets the user mark a conversation as "priority"; that is a deliberate user choice and is honoured as an allow rule.
     * Returns false when the ranking is unavailable, the app doesn't use conversation shortcuts, or the OS is older than 11.
     */
    private fun isPriorityConversation(key: String): Boolean {
        if (Build.VERSION.SDK_INT < 30) return false
        return runCatching {
            val r = Ranking()
            currentRanking.getRanking(key, r) && r.isConversation && r.channel?.isImportantConversation == true
        }.getOrDefault(false)
    }

    @Suppress("DEPRECATION")
    private fun latestMessageText(extras: android.os.Bundle?): String? = runCatching {
        extras?.getParcelableArray(Notification.EXTRA_MESSAGES)?.lastOrNull()?.let { (it as? android.os.Bundle)?.getCharSequence("text")?.toString() }?.takeIf { it.isNotBlank() }
    }.getOrNull()

    private inner class Ports : ProcessorPorts {
        override suspend fun activeSession(): FocusSession? = sessions.getActive()
        override suspend fun evaluate(p: PostedNotification, phase: SessionPhase, session: FocusSession?) =
            decisions.evaluateAndRecord(p.incoming, p.captured.dedupKey, p.captured.appLabel, p.captured.postTimeMs, phase, session?.id)
        override suspend fun save(p: PostedNotification, d: NotificationDecision, decisionId: String, session: FocusSession, groupRepeated: Boolean): SaveResult {
            val r = queue.ingest(p.captured, d, decisionId, session, p.contentIntent as? PendingIntent, groupRepeated)
            // Confirm the row really exists before anything is cancelled.
            return if (queue.exists(r.id)) SaveResult.Saved(r.id, r.duplicate) else SaveResult.Failed("row not found after write")
        }
        override suspend fun attach(decisionId: String, queueId: String) = decisions.attach(decisionId, queueId)
        override suspend fun markSuppressed(queueId: String) { queue.markSuppressed(queueId) }
        override suspend fun markStillVisible(queueId: String) { queue.markStillVisible(queueId) }
        override suspend fun removalEnabled() = settings.current().suppressOriginals
        override suspend fun groupRepeated() = settings.current().groupRepeated
        override suspend fun timeline(sessionId: String, type: String, appLabel: String, atMs: Long) = timelineRepo.log(sessionId, type, appLabel, atMs = atMs)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?, rankingMap: RankingMap?, reason: Int) {
        sbn ?: return
        pendingRemoval.remove(sbn.key)?.complete(Unit)
        if (selfCancelled.remove(sbn.key) != null) return
        if (reason == REASON_APP_CANCEL || reason == REASON_APP_CANCEL_ALL) scope.launch { runCatching { queue.markSourceRemoved(sbn.key) } }
        val cutoff = System.currentTimeMillis() - 60_000L
        selfCancelled.entries.removeIf { it.value < cutoff }
    }

    private fun debug(msg: String) { if (BuildConfig.DEBUG) Log.d(TAG, msg) }

    override fun onDestroy() { connected = false; ListenerDiagnostics.onConnection(false); super.onDestroy(); scope.cancel() }

    companion object {
        const val TAG = "AttuneListener"
        const val REMOVAL_TIMEOUT_MS = 2_500L
        const val CLEANUP_INTERVAL_MS = 60 * 60_000L
    }
}
