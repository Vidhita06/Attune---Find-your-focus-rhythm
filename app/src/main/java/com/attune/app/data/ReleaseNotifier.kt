package com.attune.app.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.attune.app.MainActivity
import com.attune.core.model.QueuedNotification
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Show now": posts a REPLACEMENT notification from Attune for a held item. Android cannot restore the original, so the
 * replacement is Attune's own: it has no actions, grouping, reply buttons or per-app channel of the original, and its tap opens the
 * original target only while that PendingIntent is still in memory (otherwise Attune). The listener ignores Attune's own
 * package, so a replacement can never be re-queued. Text is shown only if it is still stored and not sensitive.
 */
@Singleton
class ReleaseNotifier @Inject constructor(@ApplicationContext private val ctx: Context, private val cache: LaunchIntentCache) {
    /** True only if Android accepted the notification. False when Attune's notifications are blocked or posting failed. */
    fun post(n: QueuedNotification): Boolean {
        val nm = NotificationManagerCompat.from(ctx)
        if (!nm.areNotificationsEnabled()) return false
        if (android.os.Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Released from queue", NotificationManager.IMPORTANCE_DEFAULT))
        val tap = cache.get(n.id) ?: PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val app = n.appLabel.ifBlank { "an app" }
        val showText = !n.isSensitive && !n.textScrubbed
        val title = if (showText && !n.title.isNullOrBlank()) "${n.title} · $app" else "Held message from $app"
        val body = if (showText && !n.previewText.isNullOrBlank()) n.previewText!! else "Details aren't stored for this message. Open $app to see it."
        val b = NotificationCompat.Builder(ctx, CHANNEL_ID).setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle(title).setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body)).setSubText("Released by Attune").setContentIntent(tap).setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
        return try { nm.notify(TAG, n.id.hashCode(), b.build()); true } catch (e: SecurityException) { false }
    }

    private companion object { const val CHANNEL_ID = "attune_released"; const val TAG = "attune.release" }
}
