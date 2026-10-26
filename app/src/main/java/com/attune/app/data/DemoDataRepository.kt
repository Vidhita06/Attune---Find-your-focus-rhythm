package com.attune.app.data
import android.content.Context
import androidx.room.withTransaction
import com.attune.core.data.db.AttuneDatabase
import com.attune.core.model.*
import com.attune.prediction.decision.Routing
import com.attune.prediction.decision.SenderKey
import com.attune.prediction.model.SessionPhase
import com.attune.prediction.policy.IncomingNotification
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class DemoInjectResult(val routing: Routing, val reason: String, val queueId: String?)

/**
 * Seeds clearly-labelled sample data (isDemo = true) from assets/demo_fixtures.json. Decisions for the samples are made by
 * the real decision engine, so demo mode shows what the AI actually does. Reset removes only demo rows and re-seeds.
 * Real records are never touched here.
 */
@Singleton
class DemoDataRepository @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val db: AttuneDatabase,
    private val settings: SettingsRepository,
    private val queue: QueueRepository,
    private val sessions: FocusSessionRepository,
    private val decisions: DecisionRepository,
    private val timeline: TimelineRepository
) {
    private val mutex = Mutex()
    private val fixtures by lazy { JSONObject(ctx.assets.open("demo_fixtures.json").bufferedReader(Charsets.UTF_8).readText()) }

    suspend fun ensureSeeded() = mutex.withLock {
        if (settings.current().demoEnabled && (!settings.isDemoSeeded() || settings.demoSeedVersion() < SEED_VERSION)) { deleteDemoRows(); seed() }
    }

    /** Demo mode is opt-in. When it is off, any sample rows left by an earlier build are deleted so they can't be mistaken for real data. */
    suspend fun purgeIfDisabled() = mutex.withLock {
        if (!settings.current().demoEnabled) { deleteDemoRows(); settings.setDemoSeeded(false) }
    }

    suspend fun reset() = mutex.withLock { deleteDemoRows(); seed() }

    private suspend fun deleteDemoRows() = db.withTransaction {
        db.queuedNotificationDao().deleteDemo(); db.focusSessionDao().deleteDemo(); db.userActionDao().deleteDemo()
        db.feedbackDao().deleteDemo(); db.predictionDao().deleteDemo(); db.energyCheckInDao().deleteDemo()
        db.decisionDao().deleteDemo(); db.feedbackEventDao().deleteDemo(); db.sessionEventDao().deleteDemo()
    }

    /** Sends one sample notification "arriving now" through the real decision engine, as if it had been posted. */
    suspend fun injectLiveSample(): DemoInjectResult {
        val live = fixtures.getJSONArray("live")
        val o = live.getJSONObject(Math.floorMod(settings.nextDemoLiveIndex(), live.length()))
        val now = System.currentTimeMillis(); val id = "demo_live_${UUID.randomUUID()}"
        val active = sessions.getActive()
        val phase = FocusSessionRepository.phaseOf(active)
        val n = IncomingNotification(o.getString("packageName"), o.str("title"), o.str("text"), null, false, false, true)
        // A demo sample is always evaluated as if focusing, so the AI's decision is visible even without a running session.
        val evalPhase = if (phase == SessionPhase.NO_SESSION) SessionPhase.FOCUS else phase
        val (d, rec) = decisions.evaluateAndRecord(n, "demo:$id", o.getString("appName"), now, evalPhase, active?.id, demo = true)
        val reason = d.reasons.firstOrNull()?.text ?: ""
        if (d.routing == Routing.DELIVER || rec == null) {
            timeline.log(active?.id, TimelineType.ALLOWED, o.getString("appName"), demo = true)
            return DemoInjectResult(d.routing, reason, null)
        }
        val item = QueuedNotification(id, active?.id ?: "demo", n.packageName, d.category, n.title, n.text, "demo:$id", now,
            if (d.routing == Routing.FILTER) NotificationStatus.FILTERED else NotificationStatus.PENDING, isDemo = true, appLabel = o.getString("appName"),
            lastUpdatedMs = now, ruleApplied = reason, decisionId = rec.id, focusRisk = d.risk.name, isSensitive = d.sensitive, keptVisible = d.routing == Routing.REVIEW)
        queue.insertDemo(item); decisions.attach(rec.id, id)
        timeline.log(active?.id, if (d.routing == Routing.DEFER) TimelineType.HELD else TimelineType.REVIEW, o.getString("appName"), demo = true)
        return DemoInjectResult(d.routing, reason, id)
    }

    private suspend fun seed() {
        val now = System.currentTimeMillis()
        val qDao = db.queuedNotificationDao(); val sDao = db.focusSessionDao(); val aDao = db.userActionDao()
        val fDao = db.feedbackDao(); val eDao = db.energyCheckInDao(); val pDao = db.predictionDao()
        val dDao = db.decisionDao(); val tDao = db.sessionEventDao()
        val priors = mapOf("social" to (15f to 8f), "short_video" to (22f to 10f), "messaging" to (4f to 2f), "email" to (8f to 5f), "promotion" to (5f to 3f))
        suspend fun decide(pkg: String, label: String, title: String?, text: String?, key: String, at: Long, sid: String?): Pair<com.attune.prediction.decision.NotificationDecision, NotificationDecisionRecord> {
            val d = decisions.evaluate(IncomingNotification(pkg, title, text, null, false, false, true), SessionPhase.FOCUS, demo = true, aiModeOverride = true)
            return d to DecisionRepository.toRecord(d, "dec_$key", "demo:$key", pkg, label, at, sid, SessionPhase.FOCUS, SenderKey.of(pkg, title), null, true, 1, at)
        }
        fun ev(sid: String, at: Long, type: String, detail: String?) = SessionEvent("ev_${UUID.randomUUID()}", sid, at, type, detail, true)

        // Decisions are computed before the transaction (they read settings/rules), then everything is written atomically.
        val queueRows = mutableListOf<Pair<QueuedNotification, NotificationDecisionRecord>>()
        fixtures.getJSONArray("queue").forEachObj { o ->
            val id = "demo_${o.getString("id")}"; val t = now - o.getInt("minutesAgo") * 60_000L
            val (d, rec) = decide(o.getString("packageName"), o.getString("appName"), o.str("title"), o.str("text"), id, t, null)
            queueRows += QueuedNotification(id, "demo", o.getString("packageName"), d.category, o.str("title"), o.str("text"),
                "demo:$id", t, isDemo = true, appLabel = o.getString("appName"), groupCount = o.optInt("group", 1), lastUpdatedMs = t,
                ruleApplied = d.reasons.firstOrNull()?.text, decisionId = rec.id, focusRisk = d.risk.name, isSensitive = d.sensitive,
                keptVisible = d.routing == Routing.REVIEW) to rec.copy(notificationId = id)
        }
        val sessionRows = mutableListOf<FocusSession>(); val events = mutableListOf<SessionEvent>()
        val sessionStart = mutableMapOf<String, Long>()
        fixtures.getJSONArray("sessions").forEachObj { o ->
            val start = at(now, o.getInt("daysAgo"), o.getInt("hour")); val planned = o.getInt("plannedMin"); val focused = o.getInt("focusedMin")
            val pausedMs = o.getInt("pauses") * 4 * 60_000L
            val id = "demo_${o.getString("id")}"; sessionStart[o.getString("id")] = start
            val state = SessionState.valueOf(o.getString("outcome")); val end = start + focused * 60_000L + pausedMs
            sessionRows += FocusSession(id, start, start + planned * 60_000L + pausedMs, end, state, isDemo = true, plannedDurationMin = planned,
                totalPausedMs = pausedMs, pauseCount = o.getInt("pauses"), interruptionCount = o.getInt("interruptions"))
            events += ev(id, start, TimelineType.START, "$planned min planned")
            if (o.getInt("pauses") > 0) { events += ev(id, start + 10 * 60_000L, TimelineType.PAUSE, null); events += ev(id, start + 14 * 60_000L, TimelineType.RESUME, "after 4 min") }
            events += ev(id, end, if (state == SessionState.COMPLETED) TimelineType.COMPLETED else TimelineType.ENDED_EARLY, "$focused of $planned min focused")
        }
        val historyRows = mutableListOf<Triple<QueuedNotification, NotificationDecisionRecord, JSONObject>>()
        fixtures.getJSONArray("history").forEachObj { o ->
            val id = "demo_${o.getString("id")}"; val sid = o.getString("session"); val t = (sessionStart[sid] ?: now) + 6 * 60_000L
            val action = ActionType.valueOf(o.getString("action"))
            val status = when (action) { ActionType.OPENED -> NotificationStatus.OPENED; ActionType.DELAYED -> NotificationStatus.DELAYED; ActionType.DISMISSED -> NotificationStatus.DISMISSED }
            val (d, rec) = decide(o.getString("packageName"), o.getString("appName"), o.str("title"), o.str("text"), id, t, "demo_$sid")
            historyRows += Triple(QueuedNotification(id, "demo_$sid", o.getString("packageName"), d.category, o.str("title"), o.str("text"), "demo:$id", t, status,
                isDemo = true, appLabel = o.getString("appName"), lastUpdatedMs = t, ruleApplied = d.reasons.firstOrNull()?.text, statusChangedMs = t + 60_000L,
                decisionId = rec.id, focusRisk = d.risk.name, isSensitive = d.sensitive, keptVisible = d.routing == Routing.REVIEW), rec.copy(notificationId = id), o)
            events += ev("demo_$sid", t, if (d.routing == Routing.REVIEW) TimelineType.REVIEW else TimelineType.HELD, o.getString("appName"))
            events += ev("demo_$sid", t + 60_000L, action.name, o.getString("appName"))
        }
        val allowedRows = mutableListOf<NotificationDecisionRecord>()
        fixtures.optJSONArray("allowed")?.forEachObj { o ->
            val sid = o.getString("session"); val t = (sessionStart[sid] ?: now) + 12 * 60_000L; val key = "demo_allowed_${sid}_${allowedRows.size}"
            val (d, rec) = decide(o.getString("packageName"), o.getString("appName"), o.str("title"), o.str("text"), key, t, "demo_$sid")
            allowedRows += rec
            events += ev("demo_$sid", t, when (d.routing) { Routing.DELIVER -> TimelineType.ALLOWED; Routing.REVIEW -> TimelineType.REVIEW; else -> TimelineType.HELD }, o.getString("appName"))
        }

        db.withTransaction {
            queueRows.forEach { (q, r) -> qDao.upsert(q); dDao.upsert(r) }
            sessionRows.forEach { sDao.upsert(it) }
            historyRows.forEach { (q, r, o) ->
                qDao.upsert(q); dDao.upsert(r)
                val action = ActionType.valueOf(o.getString("action")); val cat = o.getString("category"); val t = q.postTimeMs
                val (pt, pr) = priors[cat] ?: (8f to 4f)
                pDao.upsert(Prediction("pred_${q.id}", q.id, if (pt + pr >= 20) RiskLevel.HIGH else if (pt + pr >= 9) RiskLevel.MED else RiskLevel.LOW, pt, pr,
                    "Demo estimate", 0.1f, 0, "", true, true, t, "baseline-v1", null, "FOCUS"))
                val actionId = "demo_a_${o.getString("id")}"
                aDao.upsert(UserAction(actionId, q.id, "pred_${q.id}", action, t + 60_000L, true, q.sessionId, "FOCUS", cat,
                    launchAttempted = action == ActionType.OPENED, launchResult = if (action == ActionType.OPENED) "DEMO_NO_APP" else null,
                    predictedTimeAwayMin = pt, predictedRecoveryMin = pr))
                if (o.has("timeAway")) fDao.upsert(Feedback("demo_f_${o.getString("id")}", q.id, actionId, o.getDouble("timeAway").toFloat(), o.getDouble("recovery").toFloat(), isDemo = true, createdAt = t + 30 * 60_000L))
            }
            allowedRows.forEach { dDao.upsert(it) }
            events.forEach { tDao.upsert(it) }
            fixtures.getJSONArray("checkins").forEachObj { o ->
                val score = o.optInt("score", 3)
                eDao.upsert(EnergyCheckIn("demo_e_${o.getInt("daysAgo")}_${o.getInt("hour")}", EnergyCheckIn.levelFor(score), at(now, o.getInt("daysAgo"), o.getInt("hour")),
                    isDemo = true, score = score, phase = o.str("phase")))
            }
        }
        settings.setDemoSeeded(true); settings.setDemoSeedVersion(SEED_VERSION)
    }

    private fun at(now: Long, daysAgo: Int, hour: Int) = Calendar.getInstance().apply {
        timeInMillis = now; add(Calendar.DAY_OF_YEAR, -daysAgo); set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
    private fun JSONObject.str(k: String) = if (!has(k) || isNull(k)) null else optString(k).ifBlank { null }
    private inline fun org.json.JSONArray.forEachObj(f: (JSONObject) -> Unit) { for (i in 0 until length()) f(getJSONObject(i)) }

    companion object { const val SEED_VERSION = 2 }
}

@Singleton
class EnergyRepository @Inject constructor(private val db: AttuneDatabase, private val sessions: FocusSessionRepository, private val timeline: TimelineRepository) {
    val all = db.energyCheckInDao().observeAll()
    /** [score] 1–5 (self-reported, not a health measure). [phase] BEFORE/AFTER a session, or null. */
    suspend fun checkIn(score: Int, phase: String? = null) {
        val s = score.coerceIn(1, 5); val active = sessions.getActive()
        val sid = active?.id ?: if (phase == "AFTER") db.focusSessionDao().getLatest()?.takeIf { !it.isDemo }?.id else null
        db.energyCheckInDao().upsert(EnergyCheckIn(UUID.randomUUID().toString(), EnergyCheckIn.levelFor(s), System.currentTimeMillis(), sid, score = s,
            phase = phase ?: if (active != null) "DURING" else null))
        timeline.log(sid, TimelineType.ENERGY, "Energy $s/5")
    }
}

data class StoredDataCounts(val decisions: Int, val feedback: Int, val queueWithText: Int, val rules: Int)

/** Privacy Center actions. Each deletes only real (non-demo) data of one kind; settings are never touched. */
@Singleton
class PrivacyRepository @Inject constructor(private val db: AttuneDatabase, private val queue: QueueRepository) {
    val counts = combine(db.decisionDao().observeRealCount(), db.feedbackEventDao().observeAll(), queue.storedTextCount, db.userRuleDao().observeAll()) { d, f, q, r ->
        StoredDataCounts(d, f.count { !it.isDemo }, q, r.size)
    }

    /** Sessions (finished), decisions, actions, time-cost reports, queue history and timelines. A running session is kept. */
    suspend fun deleteActivityAndAnalytics() = db.withTransaction {
        db.queuedNotificationDao().deleteReal(); db.sessionEventDao().deleteRealFinished(); db.focusSessionDao().deleteRealFinished()
        db.userActionDao().deleteReal(); db.feedbackDao().deleteReal(); db.predictionDao().deleteReal(); db.attentionProfileDao().deleteAll()
        db.decisionDao().deleteReal()
    }
    suspend fun deleteFeedbackAndPersonalization() = db.feedbackEventDao().deleteReal()
    suspend fun deleteEnergy() = db.energyCheckInDao().deleteReal()
    suspend fun deleteRules() = db.userRuleDao().deleteAll()
    suspend fun scrubNotificationText() = queue.scrubAllText()
    suspend fun deleteEverything() { deleteActivityAndAnalytics(); deleteFeedbackAndPersonalization(); deleteEnergy(); deleteRules() }
}
