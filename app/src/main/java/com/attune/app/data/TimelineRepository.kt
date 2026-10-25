package com.attune.app.data
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.attune.app.MainActivity
import com.attune.core.data.db.dao.QueuedNotificationDao
import com.attune.core.data.db.dao.SessionEventDao
import com.attune.core.model.FocusSession
import com.attune.core.model.SessionEvent
import com.attune.core.model.SessionState
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Event types shown on a session's timeline. Details hold app names and outcomes only — never message text. */
object TimelineType {
    const val START = "START"; const val PAUSE = "PAUSE"; const val BREAK = "BREAK"; const val RESUME = "RESUME"
    const val COMPLETED = "COMPLETED"; const val ENDED_EARLY = "ENDED_EARLY"
    const val HELD = "HELD"; const val ALLOWED = "ALLOWED"; const val REVIEW = "REVIEW"; const val FILTERED = "FILTERED"
    const val OPENED = "OPENED"; const val DELAYED = "DELAYED"; const val DISMISSED = "DISMISSED"
    const val ENERGY = "ENERGY"
}

@Singleton
class TimelineRepository @Inject constructor(private val dao: SessionEventDao) {
    fun forSession(id: String) = dao.observeForSession(id)
    suspend fun log(sessionId: String?, type: String, detail: String? = null, demo: Boolean = false, atMs: Long = System.currentTimeMillis()) {
        if (sessionId == null) return
        runCatching { dao.upsert(SessionEvent(UUID.randomUUID().toString(), sessionId, atMs, type, detail, demo)) }
    }
}

/** Opens the app on a given tab (used by Attune's own notifications). */
object PendingNavigation {
    const val EXTRA_TAB = "attune_open_tab"
    val target = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
}

/**
 * Handles held notifications when a session ends, pauses or goes on a break, according to [AfterFocus]. Android doesn't let
 * Attune re-post another app's notification, so the "summary" is one Attune notification that opens the queue.
 */
@Singleton
class FocusSummaryNotifier @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val settings: SettingsRepository,
    private val queueDao: QueuedNotificationDao
) {
    suspend fun onSessionStopped(s: FocusSession, reason: SessionState) {
        val held = queueDao.openForSession(s.id).size
        val mode = settings.current().afterFocus
        val (title, text) = when (reason) {
            SessionState.COMPLETED -> "Focus session complete" to (if (held > 0) "$held notification${if (held == 1) "" else "s"} waited for you. Tap to review." else "Nicely done — nothing is waiting in your queue.")
            SessionState.ENDED_EARLY -> "Focus session ended" to "$held notification${if (held == 1) "" else "s"} waited for you. Tap to review."
            SessionState.BREAK, SessionState.PAUSED -> (if (reason == SessionState.BREAK) "Break time" else "Focus paused") to "$held notification${if (held == 1) " is" else "s are"} waiting. Tap to review."
            else -> return
        }
        // Completion is always announced; otherwise only when there is something to review and the user wants summaries.
        if (reason != SessionState.COMPLETED && (held == 0 || mode == AfterFocus.QUIET)) return
        if (reason == SessionState.COMPLETED && held > 0 && mode == AfterFocus.QUIET) { post(title, "Your queue is ready when you are.", false); return }
        post(title, text, held > 0)
    }

    private fun post(title: String, text: String, openQueue: Boolean) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Session updates", NotificationManager.IMPORTANCE_DEFAULT))
        val intent = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .apply { if (openQueue) putExtra(PendingNavigation.EXTRA_TAB, "queue") }
        val pi = PendingIntent.getActivity(ctx, 7, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, CHANNEL_ID).setContentTitle(title).setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentIntent(pi).setAutoCancel(true).build()
        runCatching { nm.notify(NOTIF_ID, n) }
    }

    companion object { const val CHANNEL_ID = "attune_session_channel"; const val NOTIF_ID = 1002 }
}
