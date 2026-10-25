package com.attune.app.service
import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.attune.app.MainActivity
import com.attune.app.data.FocusSessionRepository
import com.attune.app.domain.TimerMath
import com.attune.core.model.SessionState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

/**
 * Keeps an ongoing "focus in progress" notification and completes the session on time while the app is in the background.
 * It owns no timer state: everything is recomputed from the stored session, so if Android stops the service nothing is lost
 * and the session is reconciled the next time Attune opens.
 */
@AndroidEntryPoint
class FocusTimerService : Service() {
    @Inject lateinit var sessions: FocusSessionRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loop: Job? = null

    companion object {
        const val CHANNEL_ID = "attune_focus_channel"; const val DONE_CHANNEL_ID = "attune_session_channel"
        const val NOTIF_ID = 1001; const val DONE_NOTIF_ID = 1002
        fun start(ctx: Context) { runCatching { ContextCompat.startForegroundService(ctx, Intent(ctx, FocusTimerService::class.java)) } }
    }

    override fun onCreate() { super.onCreate(); createChannels() }
    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val ok = runCatching {
            val n = build("Focus session", "Running")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            else startForeground(NOTIF_ID, n)
        }.isSuccess
        if (!ok) { stopSelf(); return START_NOT_STICKY }
        if (loop?.isActive != true) loop = scope.launch {
            while (isActive) {
                sessions.completeIfDue() // the completion notification is posted by FocusSummaryNotifier
                val s = sessions.getActive()
                if (s == null) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); break }
                val now = System.currentTimeMillis(); val left = (TimerMath.remainingMs(s, now) + 59_999) / 60_000
                update(when (s.state) { SessionState.PAUSED -> "Paused · $left min left"; SessionState.BREAK -> "On a break · $left min left"; else -> "Focusing · $left min left" })
                delay(if (s.state == SessionState.FOCUS) (TimerMath.dueAtMs(s) - now).coerceIn(1_000L, 30_000L) else 30_000L)
            }
        }
        return START_NOT_STICKY
    }

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Focus timer", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(DONE_CHANNEL_ID, "Session complete", NotificationManager.IMPORTANCE_DEFAULT))
    }
    private fun contentIntent() = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    private fun build(title: String, text: String) = NotificationCompat.Builder(this, CHANNEL_ID).setContentTitle(title).setContentText(text)
        .setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentIntent(contentIntent()).setOngoing(true).setOnlyAlertOnce(true).setSilent(true).build()
    private fun canNotify() = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    private fun update(text: String) { if (canNotify()) runCatching { getSystemService(NotificationManager::class.java).notify(NOTIF_ID, build("Attune", text)) } }
    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}
