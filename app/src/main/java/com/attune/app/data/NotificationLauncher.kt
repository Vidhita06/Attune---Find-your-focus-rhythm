package com.attune.app.data
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.attune.app.domain.LaunchResults
import com.attune.core.model.QueuedNotification
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the original notification's content PendingIntent in memory (PendingIntents cannot be persisted).
 * After a process restart only the app-level fallback (the app's launcher screen) is available.
 */
@Singleton
class LaunchIntentCache @Inject constructor() {
    private val map = ConcurrentHashMap<String, PendingIntent>()
    fun put(itemId: String, pi: PendingIntent?) { if (pi != null) map[itemId] = pi }
    fun get(itemId: String): PendingIntent? = map[itemId]
    fun has(itemId: String) = map.containsKey(itemId)
}

@Singleton
class NotificationLauncher @Inject constructor(@ApplicationContext private val ctx: Context, private val cache: LaunchIntentCache) {
    /** Returns a [LaunchResults] code. "Sent" means Android accepted the request; it does not prove the content opened. */
    fun launch(n: QueuedNotification): String {
        cache.get(n.id)?.let { pi ->
            val ok = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    val opts = ActivityOptions.makeBasic().setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                    pi.send(ctx, 0, null, null, null, null, opts.toBundle())
                } else pi.send()
            }.isSuccess
            if (ok) return LaunchResults.CONTENT_INTENT_SENT
        }
        val intent = ctx.packageManager.getLaunchIntentForPackage(n.packageName)
        if (intent != null && runCatching { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return LaunchResults.APP_LAUNCHED
        return if (n.isDemo) LaunchResults.DEMO_NO_APP else LaunchResults.FAILED
    }
    fun hasOriginalTarget(id: String) = cache.has(id)
}
