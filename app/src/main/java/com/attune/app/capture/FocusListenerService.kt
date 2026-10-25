package com.attune.app.capture
import android.app.Notification
import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.attune.app.data.*
import com.attune.app.util.AppLabels
import com.attune.prediction.decision.Routing
import com.attune.prediction.policy.IncomingNotification
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/**
 * Sends each posted notification through [DecisionRepository] (rules → safety → on-device model → fallback).
 *  - DELIVER: left alone (only the decision metadata is recorded, during focus or when "observe outside focus" is on).
 *  - DEFER/FILTER: added to Attune's queue and, if enabled, Android is asked to remove the original. That removal is
 *    best-effort: the notification was already posted, may have been seen or heard, and cannot be restored by Attune.
 *  - REVIEW: the original stays visible and a copy is listed in the queue for review.
 * Notification content is never logged and never leaves the device.
 */
@AndroidEntryPoint
class FocusListenerService : NotificationListenerService() {
    @Inject lateinit var queue: QueueRepository
    @Inject lateinit var sessions: FocusSessionRepository
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var access: NotificationAccessMonitor
    @Inject lateinit var decisions: DecisionRepository
    @Inject lateinit var timeline: TimelineRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /** Keys Attune cancelled itself, so the matching removal callback isn't mistaken for the app withdrawing it. */
    private val selfCancelled = ConcurrentHashMap<String, Long>()

    override fun onListenerConnected() { access.onListenerConnected(true) }
    override fun onListenerDisconnected() {
        access.onListenerConnected(false)
        runCatching { requestRebind(ComponentName(this, FocusListenerService::class.java)) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        if (sbn.packageName == packageName) return
        val n = sbn.notification ?: return
        val extras = n.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.takeIf { it.isNotBlank() }
        val text = (extras?.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras?.getCharSequence(Notification.EXTRA_TEXT))?.toString()?.takeIf { it.isNotBlank() }
        val ongoing = (n.flags and Notification.FLAG_ONGOING_EVENT) != 0 || (n.flags and Notification.FLAG_FOREGROUND_SERVICE) != 0
        val summary = (n.flags and Notification.FLAG_GROUP_SUMMARY) != 0
        val incoming = IncomingNotification(sbn.packageName, title, text, n.category, ongoing, summary, sbn.isClearable)
        val key = sbn.key; val contentIntent = n.contentIntent; val postTime = sbn.postTime
        scope.launch {
            runCatching {
                val session = sessions.getActive()
                val phase = FocusSessionRepository.phaseOf(session)
                val label = AppLabels.resolve(this@FocusListenerService, sbn.packageName)
                val captured = CapturedNotification(sbn.packageName, label, title, text, postTime, key, n.category, summary)
                val (decision, record) = decisions.evaluateAndRecord(incoming, captured.dedupKey, label, postTime, phase, session?.id)
                if (record == null || session == null || record.updateCount > 1 && decision.routing == Routing.DELIVER) return@launch
                if (decision.routing == Routing.DELIVER) { timeline.log(session.id, TimelineType.ALLOWED, label, atMs = postTime); return@launch }
                val s = settings.current()
                val id = queue.ingest(captured, decision, record.id, session, contentIntent, s.groupRepeated) ?: return@launch
                decisions.attach(record.id, id)
                if (record.updateCount == 1) timeline.log(session.id, when (decision.routing) {
                    Routing.DEFER -> TimelineType.HELD; Routing.FILTER -> TimelineType.FILTERED; else -> TimelineType.REVIEW }, label, atMs = postTime)
                val hide = decision.routing == Routing.FILTER || (decision.routing == Routing.DEFER && s.suppressOriginals)
                if (hide) {
                    selfCancelled[key] = System.currentTimeMillis()
                    if (runCatching { cancelNotification(key) }.isSuccess) queue.markSuppressed(id) else selfCancelled.remove(key)
                }
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?, rankingMap: RankingMap?, reason: Int) {
        sbn ?: return
        if (selfCancelled.remove(sbn.key) != null) return
        if (reason == REASON_APP_CANCEL || reason == REASON_APP_CANCEL_ALL) scope.launch { runCatching { queue.markSourceRemoved(sbn.key) } }
        val cutoff = System.currentTimeMillis() - 60_000L
        selfCancelled.entries.removeIf { it.value < cutoff }
    }

    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}
