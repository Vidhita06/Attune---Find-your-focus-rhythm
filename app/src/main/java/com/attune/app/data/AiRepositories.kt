package com.attune.app.data
import android.content.Context
import com.attune.app.util.AppCategoryMapper
import com.attune.core.data.db.dao.DecisionDao
import com.attune.core.data.db.dao.FeedbackEventDao
import com.attune.core.data.db.dao.UserRuleDao
import com.attune.core.model.*
import com.attune.prediction.classifier.LogRegClassifier
import com.attune.prediction.decision.*
import com.attune.prediction.model.SessionPhase
import com.attune.prediction.policy.IncomingNotification
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class ModelStatus(val loaded: Boolean, val version: String?, val sizeBytes: Long, val vocabulary: Int, val loadMs: Long, val error: String?)

/**
 * Loads the on-device classifier exported by ml/train.py (assets/models/attune_text_model.json). If the file is missing or
 * invalid the engine runs without a model and every decision falls back to category rules, labelled as such in the UI.
 */
@Singleton
class ModelManager @Inject constructor(@ApplicationContext private val ctx: Context) {
    private val loaded: Pair<NotificationDecisionEngine, ModelStatus> by lazy { load() }
    val engine: NotificationDecisionEngine get() = loaded.first
    val status: ModelStatus get() = loaded.second

    private fun load(): Pair<NotificationDecisionEngine, ModelStatus> {
        val t0 = System.nanoTime()
        return try {
            val bytes = ctx.assets.open(MODEL_ASSET).use { it.readBytes() }
            val c = LogRegClassifier.fromJson(String(bytes, Charsets.UTF_8))
            NotificationDecisionEngine(c) to ModelStatus(true, c.modelVersion, bytes.size.toLong(), c.vocabularySize, (System.nanoTime() - t0) / 1_000_000, null)
        } catch (e: Exception) {
            NotificationDecisionEngine(null) to ModelStatus(false, null, 0, 0, 0, e.message ?: e.javaClass.simpleName)
        }
    }

    /** Measures real inference time on this device (median of [runs] classifications, in microseconds). */
    fun measureLatencyMicros(runs: Int = 300): Double? {
        val e = engine; if (!e.modelAvailable) return null
        val samples = listOf("Flat 50% off ends tonight", "call me right now it's urgent", "Rahul liked your photo", "Assignment 3 due tomorrow 11:59 PM")
        val settings = DecisionSettings(true, com.attune.prediction.policy.PolicySettings(emptySet(), emptySet(), emptySet(), true, true, false))
        val times = (0 until runs).map { i ->
            val t0 = System.nanoTime()
            e.decide(DecisionInput(IncomingNotification("com.example", "Sample", samples[i % samples.size], null, false, false, true), "messaging", SessionPhase.FOCUS),
                settings, emptyList(), PersonalizationProfile.EMPTY, "com.attune.app")
            (System.nanoTime() - t0) / 1000.0
        }.drop(runs / 5).sorted()
        return times[times.size / 2]
    }

    fun evaluationJson(): String? = runCatching { ctx.assets.open(EVAL_ASSET).bufferedReader(Charsets.UTF_8).readText() }.getOrNull()

    companion object { const val MODEL_ASSET = "models/attune_text_model.json"; const val EVAL_ASSET = "models/evaluation.json" }
}

/** Explicit rules stored in Room. Allowed apps and priority contacts from earlier versions stay in DataStore and are merged in by the engine. */
@Singleton
class RulesRepository @Inject constructor(private val dao: UserRuleDao) {
    val rules: Flow<List<UserRuleRecord>> = dao.observeAll()
    suspend fun all(): List<UserRule> = dao.getAll().mapNotNull { it.toRule() }

    /** Adds a rule unless an identical one exists. Returns false for duplicates. */
    suspend fun add(kind: RuleKind, value: String, action: RuleAction, label: String, source: String = "MANUAL"): Boolean {
        val v = value.trim(); if (v.isEmpty()) return false
        if (dao.getAll().any { it.kind == kind.name && it.value.equals(v, true) && it.action == action.name }) return false
        dao.upsert(UserRuleRecord(UUID.randomUUID().toString(), kind.name, v, action.name, label, System.currentTimeMillis(), source))
        return true
    }
    suspend fun remove(id: String) = dao.delete(id)
    suspend fun clear() = dao.deleteAll()

    companion object {
        fun UserRuleRecord.toRule(): UserRule? = runCatching { UserRule(id, RuleKind.valueOf(kind), value, RuleAction.valueOf(action), label) }.getOrNull()
        fun labelFor(kind: RuleKind, value: String, action: RuleAction): String {
            val what = when (kind) { RuleKind.APP -> "App $value"; RuleKind.CONTACT -> "Contact “$value”"; RuleKind.KEYWORD -> "Keyword “$value”"; RuleKind.CATEGORY -> AppCategoryMapper.displayName(value) }
            return if (action == RuleAction.ALLOW) "Always allow · $what" else "Hold during focus · $what"
        }
    }
}

data class LearnedPreferenceView(val pref: LearnedPreference, val label: String)

/**
 * Feedback storage and the transparent personalization profile derived from it. Demo feedback only affects demo
 * samples, and real feedback only affects real notifications, so the two never mix.
 */
@Singleton
class PersonalizationRepository @Inject constructor(
    private val dao: FeedbackEventDao,
    private val rules: RulesRepository,
    private val settings: SettingsRepository
) {
    val events: Flow<List<FeedbackEvent>> = dao.observeAll()

    suspend fun profile(demo: Boolean): PersonalizationProfile =
        PersonalizationProfile.from(events.first().filter { it.isDemo == demo }.mapNotNull { it.signal() })

    fun learned(demo: Boolean = false): Flow<List<LearnedPreferenceView>> = events.map { all ->
        val evs = all.filter { it.isDemo == demo }
        PersonalizationProfile.from(evs.mapNotNull { it.signal() }).preferences.filter { it.bias != 0.0 }.map { p ->
            LearnedPreferenceView(p, when (p.scope) {
                PreferenceScope.APP -> evs.firstOrNull { it.packageName == p.key }?.appLabel ?: p.key
                PreferenceScope.CATEGORY -> AppCategoryMapper.displayName(p.key)
                PreferenceScope.SENDER -> evs.firstOrNull { it.senderKey == p.key }?.let { "${it.senderHint ?: "A sender"} in ${it.appLabel}" } ?: "A sender"
            })
        }.sortedByDescending { kotlin.math.abs(it.pref.bias) }
    }

    sealed interface Outcome { data class Saved(val message: String) : Outcome; data class NotPossible(val message: String) : Outcome }

    suspend fun record(kind: FeedbackKind, n: QueuedNotification, d: NotificationDecisionRecord?): Outcome {
        val now = System.currentTimeMillis()
        val ruleMsg: String? = when (kind) {
            FeedbackKind.ALWAYS_ALLOW_APP -> { settings.setAppAllowed(n.packageName, true); "${n.appLabel.ifBlank { "This app" }} will always be allowed during focus." }
            FeedbackKind.ALWAYS_ALLOW_CONTACT -> {
                val name = n.title?.takeIf { it.isNotBlank() } ?: return Outcome.NotPossible("The sender's name is no longer stored, so a contact rule can't be created.")
                settings.addContact(name); "Messages titled “$name” will always be allowed."
            }
            FeedbackKind.ALWAYS_ALLOW_CATEGORY -> { rules.add(RuleKind.CATEGORY, n.category, RuleAction.ALLOW, RulesRepository.labelFor(RuleKind.CATEGORY, n.category, RuleAction.ALLOW), "FEEDBACK")
                "${AppCategoryMapper.displayName(n.category)} will always be allowed during focus." }
            FeedbackKind.ALWAYS_DEFER_CATEGORY -> { rules.add(RuleKind.CATEGORY, n.category, RuleAction.DEFER, RulesRepository.labelFor(RuleKind.CATEGORY, n.category, RuleAction.DEFER), "FEEDBACK")
                "${AppCategoryMapper.displayName(n.category)} will be held during focus (urgent or security content still gets through)." }
            else -> null
        }
        dao.upsert(FeedbackEvent(UUID.randomUUID().toString(), now, kind.name, n.packageName, n.appLabel, n.category,
            d?.senderKey ?: SenderKey.of(n.packageName, n.title), SenderKey.hint(n.title), n.id, d?.id, d?.focusRisk ?: n.focusRisk, d?.routing, n.isDemo))
        return Outcome.Saved(ruleMsg ?: if (kind.importanceVote == 0.0) "Thanks — noted. Nothing about future decisions changes."
            else "Thanks — similar notifications will be weighted ${if (kind.importanceVote > 0) "as more" else "as less"} important. You can review this under Learned preferences.")
    }

    suspend fun deletePreference(p: LearnedPreference) =
        dao.deleteForPreference(p.scope.name, p.key, FeedbackKind.entries.filter { it.importanceVote != 0.0 }.map { it.name })
    suspend fun deleteEvent(id: String) = dao.delete(id)
    suspend fun resetAll() = dao.deleteReal()

    companion object {
        fun FeedbackEvent.signal(): FeedbackSignal? = runCatching { FeedbackSignal(FeedbackKind.valueOf(kind), packageName, category, senderKey) }.getOrNull()
    }
}

/**
 * Evaluates notifications with the decision engine and keeps the decision log (one row per Android notification key).
 * Only metadata, scores and generic reasons are stored — never the notification's text.
 */
@Singleton
class DecisionRepository @Inject constructor(
    private val models: ModelManager,
    private val rules: RulesRepository,
    private val personalization: PersonalizationRepository,
    private val settings: SettingsRepository,
    private val dao: DecisionDao,
    @ApplicationContext private val ctx: Context
) {
    private val mutex = Mutex()
    val all: Flow<List<NotificationDecisionRecord>> = dao.observeAll()
    fun observe(id: String) = dao.observeById(id)
    suspend fun get(id: String) = dao.getById(id)
    fun forSession(id: String) = dao.observeForSession(id)

    /** Pure evaluation without storing anything (used by the AI Mode playground and for demo samples before insertion). */
    suspend fun evaluate(n: IncomingNotification, phase: SessionPhase, demo: Boolean = false, aiModeOverride: Boolean? = null): NotificationDecision {
        val s = settings.current()
        val ds = s.decisionSettings().let { if (aiModeOverride != null) it.copy(aiMode = aiModeOverride) else it }
        return models.engine.decide(DecisionInput(n, AppCategoryMapper.getCategory(n.packageName), phase, SenderKey.of(n.packageName, n.title)),
            ds, rules.all(), personalization.profile(demo), ctx.packageName)
    }

    /** Evaluates and records the decision. Repeated callbacks for the same notification update the existing row. */
    suspend fun evaluateAndRecord(n: IncomingNotification, dedupKey: String, appLabel: String, postedAtMs: Long, phase: SessionPhase, sessionId: String?, demo: Boolean = false):
            Pair<NotificationDecision, NotificationDecisionRecord?> {
        val d = evaluate(n, phase, demo)
        if (d.routing == Routing.IGNORE) return d to null
        return mutex.withLock {
            val existing = dao.findByDedupKey(dedupKey)
            val rec = toRecord(d, existing?.id ?: UUID.randomUUID().toString(), dedupKey, n.packageName, appLabel, existing?.postedAtMs ?: postedAtMs,
                sessionId, phase, SenderKey.of(n.packageName, n.title), existing?.notificationId, demo, (existing?.updateCount ?: 0) + 1)
            dao.upsert(rec)
            d to rec
        }
    }

    suspend fun attach(decisionId: String, notificationId: String) = dao.attachNotification(decisionId, notificationId)

    companion object {
        fun toRecord(d: NotificationDecision, id: String, dedupKey: String, pkg: String, appLabel: String, postedAt: Long, sessionId: String?, phase: SessionPhase,
                     senderKey: String?, notificationId: String?, demo: Boolean, updates: Int, now: Long = System.currentTimeMillis()) =
            NotificationDecisionRecord(id, dedupKey, pkg, appLabel, d.category, postedAt, now, sessionId, phase.name, d.routing.name, d.risk.name,
                d.signals?.importance?.name, d.signals?.pHigh?.toFloat(), d.signals?.urgent?.toFloat(), d.signals?.distracting?.toFloat(), d.signals?.actionRequired?.toFloat(),
                d.signals?.confidence?.toFloat(), d.signals?.personalBias?.toFloat(), d.source.name, d.reasons.joinToString("\n") { "${it.kind}|${it.text}" },
                d.modelVersion, d.link.hasLink, d.link.safety.name, d.sensitive, d.evidence?.name, senderKey, d.overrodeModel, d.modelRouting?.name, d.matchedRule,
                notificationId, demo, updates)

        fun reasonsOf(r: NotificationDecisionRecord): List<DecisionReason> = r.reasons.lines().mapNotNull { l ->
            val i = l.indexOf('|'); if (i <= 0) null else runCatching { DecisionReason(ReasonKind.valueOf(l.substring(0, i)), l.substring(i + 1)) }.getOrNull()
        }
    }
}
