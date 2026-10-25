package com.attune.app.capture
import android.app.Notification
import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.attune.app.data.FocusSessionRepository
import com.attune.app.data.NotificationAccessMonitor
import com.attune.app.data.QueueRepository
import com.attune.app.data.SettingsRepository
import com.attune.app.util.AppCategoryMapper
import com.attune.app.util.AppLabels
import com.attune.core.model.SessionState
import com.attune.prediction.policy.IncomingNotification
import com.attune.prediction.policy.NotificationPolicy
import com.attune.prediction.policy.Verdict
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/**
 * Queues eligible notifications while a focus session is running (not while paused/on break).
 * Notification content is never logged. Suppression uses cancelNotification(), which Android only honours for
 * clearable notifications; a cancelled notification cannot be re-posted by Attune.
 */
@AndroidEntryPoint
class FocusListenerService : NotificationListenerService() {
    @Inject lateinit var queue: QueueRepository
    @Inject lateinit var sessions: FocusSessionRepository
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var access: NotificationAccessMonitor
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
        val text = (extras?.getCharSequence(Notification.EXTRA_TEXT) ?: extras?.getCharSequence(Notification.EXTRA_BIG_TEXT))?.toString()?.takeIf { it.isNotBlank() }
        val ongoing = (n.flags and Notification.FLAG_ONGOING_EVENT) != 0 || (n.flags and Notification.FLAG_FOREGROUND_SERVICE) != 0
        val summary = (n.flags and Notification.FLAG_GROUP_SUMMARY) != 0
        val incoming = IncomingNotification(sbn.packageName, title, text, n.category, ongoing, summary, sbn.isClearable)
        val key = sbn.key; val contentIntent = n.contentIntent; val postTime = sbn.postTime
        scope.launch {
            runCatching {
                val session = sessions.getActive() ?: return@launch
                if (session.state != SessionState.FOCUS) return@launch
                val s = settings.current()
                val decision = NotificationPolicy.decide(packageName, incoming, AppCategoryMapper.getCategory(sbn.packageName), s.policy())
                if (decision.verdict != Verdict.QUEUE && decision.verdict != Verdict.FILTER) return@launch
                val captured = CapturedNotification(sbn.packageName, AppLabels.resolve(this@FocusListenerService, sbn.packageName), title, text, postTime, key, n.category, summary)
                val id = queue.ingest(captured, decision, session, contentIntent, s.groupRepeated) ?: return@launch
                if (s.suppressOriginals) {
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
        // prune stale self-cancel markers
        val cutoff = System.currentTimeMillis() - 60_000L
        selfCancelled.entries.removeIf { it.value < cutoff }
    }

    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}
