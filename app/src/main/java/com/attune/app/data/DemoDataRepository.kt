package com.attune.app.data
import android.content.Context
import androidx.room.withTransaction
import com.attune.core.data.db.AttuneDatabase
import com.attune.core.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Seeds clearly-labelled sample data (isDemo = true) from assets/demo_fixtures.json. Seeding happens once; reset removes
 * only demo rows and re-seeds. Real records are never touched here.
 */
@Singleton
class DemoDataRepository @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val db: AttuneDatabase,
    private val settings: SettingsRepository,
    private val queue: QueueRepository,
    private val sessions: FocusSessionRepository
) {
    private val mutex = Mutex()
    private val fixtures by lazy { JSONObject(ctx.assets.open("demo_fixtures.json").bufferedReader(Charsets.UTF_8).readText()) }

    suspend fun ensureSeeded() = mutex.withLock {
        if (settings.current().demoEnabled && !settings.isDemoSeeded()) { seed(); settings.setDemoSeeded(true) }
    }

    suspend fun reset() = mutex.withLock {
        db.withTransaction {
            db.queuedNotificationDao().deleteDemo(); db.focusSessionDao().deleteDemo(); db.userActionDao().deleteDemo()
            db.feedbackDao().deleteDemo(); db.predictionDao().deleteDemo(); db.energyCheckInDao().deleteDemo()
        }
        seed(); settings.setDemoSeeded(true)
    }

    /** Deletes only the user's real recorded activity (not settings, not demo rows, not a running session). */
    suspend fun deleteRealActivity() = mutex.withLock {
        db.withTransaction {
            db.queuedNotificationDao().deleteReal(); db.focusSessionDao().deleteRealFinished(); db.userActionDao().deleteReal()
            db.feedbackDao().deleteReal(); db.predictionDao().deleteReal(); db.energyCheckInDao().deleteReal(); db.attentionProfileDao().deleteAll()
        }
    }

    /** Adds one sample notification "arriving now", attached to the running session if there is one. */
    suspend fun injectLiveSample(): String {
        val live = fixtures.getJSONArray("live")
        val o = live.getJSONObject(Math.floorMod(settings.nextDemoLiveIndex(), live.length()))
        val now = System.currentTimeMillis(); val id = "demo_live_${UUID.randomUUID()}"
        queue.insertDemo(QueuedNotification(id, sessions.getActive()?.id ?: "demo", o.getString("packageName"), o.getString("category"),
            o.str("title"), o.str("text"), "demo:$id", now, isDemo = true, appLabel = o.getString("appName"), lastUpdatedMs = now, ruleApplied = "Demo sample"))
        return id
    }

    private suspend fun seed() {
        val now = System.currentTimeMillis()
        val qDao = db.queuedNotificationDao(); val sDao = db.focusSessionDao(); val aDao = db.userActionDao()
        val fDao = db.feedbackDao(); val eDao = db.energyCheckInDao(); val pDao = db.predictionDao()
        val priors = mapOf("social" to (15f to 8f), "short_video" to (22f to 10f), "messaging" to (4f to 2f), "email" to (8f to 5f), "promotion" to (5f to 3f))
        db.withTransaction {
            fixtures.getJSONArray("queue").forEachObj { o ->
                val id = "demo_${o.getString("id")}"; val t = now - o.getInt("minutesAgo") * 60_000L
                qDao.upsert(QueuedNotification(id, "demo", o.getString("packageName"), o.getString("category"), o.str("title"), o.str("text"),
                    "demo:$id", t, isDemo = true, appLabel = o.getString("appName"), groupCount = o.optInt("group", 1), lastUpdatedMs = t, ruleApplied = "Demo sample"))
            }
            val sessionStart = mutableMapOf<String, Long>()
            fixtures.getJSONArray("sessions").forEachObj { o ->
                val start = at(now, o.getInt("daysAgo"), o.getInt("hour")); val planned = o.getInt("plannedMin"); val focused = o.getInt("focusedMin")
                val pausedMs = o.getInt("pauses") * 4 * 60_000L
                val id = "demo_${o.getString("id")}"; sessionStart[o.getString("id")] = start
                sDao.upsert(FocusSession(id, start, start + planned * 60_000L + pausedMs, start + focused * 60_000L + pausedMs,
                    SessionState.valueOf(o.getString("outcome")), isDemo = true, plannedDurationMin = planned, totalPausedMs = pausedMs,
                    pauseCount = o.getInt("pauses"), interruptionCount = o.getInt("interruptions")))
            }
            fixtures.getJSONArray("history").forEachObj { o ->
                val id = "demo_${o.getString("id")}"; val sid = o.getString("session"); val t = (sessionStart[sid] ?: now) + 6 * 60_000L
                val action = ActionType.valueOf(o.getString("action")); val cat = o.getString("category")
                val status = when (action) { ActionType.OPENED -> NotificationStatus.OPENED; ActionType.DELAYED -> NotificationStatus.DELAYED; ActionType.DISMISSED -> NotificationStatus.DISMISSED }
                qDao.upsert(QueuedNotification(id, "demo_$sid", o.getString("packageName"), cat, o.str("title"), o.str("text"), "demo:$id", t, status,
                    isDemo = true, appLabel = o.getString("appName"), lastUpdatedMs = t, ruleApplied = "Demo sample", statusChangedMs = t + 60_000L))
                val (pt, pr) = priors[cat] ?: (8f to 4f)
                pDao.upsert(Prediction("pred_$id", id, if (pt + pr >= 20) RiskLevel.HIGH else if (pt + pr >= 9) RiskLevel.MED else RiskLevel.LOW, pt, pr,
                    "Demo estimate", 0.1f, 0, "", true, true, t, "baseline-v1", null, "FOCUS"))
                val actionId = "demo_a_${o.getString("id")}"
                aDao.upsert(UserAction(actionId, id, "pred_$id", action, t + 60_000L, true, "demo_$sid", "FOCUS", cat,
                    launchAttempted = action == ActionType.OPENED, launchResult = if (action == ActionType.OPENED) "DEMO_NO_APP" else null,
                    predictedTimeAwayMin = pt, predictedRecoveryMin = pr))
                if (o.has("timeAway")) fDao.upsert(Feedback("demo_f_${o.getString("id")}", id, actionId, o.getDouble("timeAway").toFloat(), o.getDouble("recovery").toFloat(), isDemo = true, createdAt = t + 30 * 60_000L))
            }
            fixtures.getJSONArray("checkins").forEachObj { o ->
                eDao.upsert(EnergyCheckIn("demo_e_${o.getInt("daysAgo")}_${o.getInt("hour")}", EnergyLevel.valueOf(o.getString("level")), at(now, o.getInt("daysAgo"), o.getInt("hour")), isDemo = true))
            }
        }
    }

    private fun at(now: Long, daysAgo: Int, hour: Int) = Calendar.getInstance().apply {
        timeInMillis = now; add(Calendar.DAY_OF_YEAR, -daysAgo); set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
    private fun JSONObject.str(k: String) = if (isNull(k)) null else optString(k).ifBlank { null }
    private inline fun org.json.JSONArray.forEachObj(f: (JSONObject) -> Unit) { for (i in 0 until length()) f(getJSONObject(i)) }
}

@Singleton
class EnergyRepository @Inject constructor(private val db: AttuneDatabase, private val sessions: FocusSessionRepository) {
    val all = db.energyCheckInDao().observeAll()
    suspend fun checkIn(level: EnergyLevel) {
        db.energyCheckInDao().upsert(EnergyCheckIn(UUID.randomUUID().toString(), level, System.currentTimeMillis(), sessions.getActive()?.id))
    }
}
