package com.attune.core.model
import androidx.room.*

/** FOCUS = timer running; PAUSED/BREAK = timer frozen; COMPLETED = planned time reached; ENDED_EARLY = user ended before planned time. */
enum class SessionState { IDLE, FOCUS, PAUSED, BREAK, COMPLETED, ENDED_EARLY;
    val isActive get() = this == FOCUS || this == PAUSED || this == BREAK
    val isFinished get() = this == COMPLETED || this == ENDED_EARLY
}
/** REVIEWED is a legacy (schema v1) value and is treated as OPENED. FILTERED = matched a user filter rule (e.g. promotions). */
enum class NotificationStatus { PENDING, REVIEWED, DELAYED, OPENED, DISMISSED, FILTERED, RELEASED;  // RELEASED = "Show now": a replacement notification was posted
    val isOpen get() = this == PENDING || this == DELAYED
}
enum class ActionType { OPENED, DELAYED, DISMISSED }
enum class RiskLevel { LOW, MED, HIGH }
enum class EnergyLevel { LOW, MODERATE, HIGH }

@Entity(tableName="user_profile")
data class UserProfile(@PrimaryKey val id:Int=1, val focusDurationMin:Int=45, val breakDurationMin:Int=10, val onboardingComplete:Boolean=false, val selectedAppPackages:String="[]", val createdAt:Long=System.currentTimeMillis())

/**
 * Timer state is derived from timestamps, never from a running UI.
 * breakStartMs = start of the current pause/break (null while focusing); totalPausedMs = sum of finished pauses.
 */
@Entity(tableName="focus_sessions")
data class FocusSession(
    @PrimaryKey val id:String, val sessionStartMs:Long, val plannedEndMs:Long, val actualEndMs:Long?=null,
    val state:SessionState=SessionState.IDLE, val breakStartMs:Long?=null, val isDemo:Boolean=false, val createdAt:Long=System.currentTimeMillis(),
    @ColumnInfo(defaultValue="0") val plannedDurationMin:Int=0,
    @ColumnInfo(defaultValue="0") val totalPausedMs:Long=0,
    @ColumnInfo(defaultValue="0") val pauseCount:Int=0,
    @ColumnInfo(defaultValue="0") val interruptionCount:Int=0
)

@Entity(tableName="queued_notifications", indices=[Index("dedupKey",unique=true)])
data class QueuedNotification(
    @PrimaryKey val id:String, val sessionId:String, val packageName:String, val category:String, val title:String?, val previewText:String?,
    val dedupKey:String, val postTimeMs:Long, val status:NotificationStatus=NotificationStatus.PENDING, val contentIntentData:String?=null,
    val isGroupSummary:Boolean=false, val isDemo:Boolean=false, val createdAt:Long=System.currentTimeMillis(),
    @ColumnInfo(defaultValue="") val appLabel:String="",
    val sbnKey:String?=null,
    @ColumnInfo(defaultValue="1") val groupCount:Int=1,
    @ColumnInfo(defaultValue="0") val lastUpdatedMs:Long=0,
    @ColumnInfo(defaultValue="0") val wasSuppressed:Boolean=false,
    @ColumnInfo(defaultValue="0") val sourceRemoved:Boolean=false,
    val ruleApplied:String?=null,
    val androidCategory:String?=null,
    val statusChangedMs:Long?=null,
    // v3: link to the decision record, risk shown in the list, privacy flags
    val decisionId:String?=null,
    val focusRisk:String?=null,
    @ColumnInfo(defaultValue="0") val isSensitive:Boolean=false,
    /** REVIEW routing: the original Android notification was left visible; this is a copy for review. */
    @ColumnInfo(defaultValue="0") val keptVisible:Boolean=false,
    /** Title/preview were removed after the retention window (see QueueRepository.scrubOldText). */
    @ColumnInfo(defaultValue="0") val textScrubbed:Boolean=false
)

@Entity(tableName="predictions")
data class Prediction(
    @PrimaryKey val id:String, val notificationId:String, val risk:RiskLevel, val timeAwayMin:Float, val recoveryMin:Float, val explanation:String,
    val confidence:Float, val observationCount:Int, val delayScenario:String, val isEstimate:Boolean=true, val isDemo:Boolean=false,
    val createdAt:Long=System.currentTimeMillis(),
    val modelVersion:String?=null,
    val factorsSummary:String?=null,
    val sessionStateAtPrediction:String?=null
)

/** One row per user decision. Observable outcome fields stay null when they were not observed. */
@Entity(tableName="user_actions")
data class UserAction(
    @PrimaryKey val id:String, val notificationId:String, val predictionId:String?, val actionType:ActionType,
    val actionTimeMs:Long=System.currentTimeMillis(), val isDemo:Boolean=false,
    val sessionId:String?=null,
    val sessionState:String?=null,
    val category:String?=null,
    @ColumnInfo(defaultValue="0") val launchAttempted:Boolean=false,
    val launchResult:String?=null,
    val launchedAtMs:Long?=null,
    val returnedAtMs:Long?=null,
    val predictedTimeAwayMin:Float?=null,
    val predictedRecoveryMin:Float?=null,
    val sessionOutcome:String?=null
)

@Entity(tableName="feedback")
data class Feedback(@PrimaryKey val id:String, val notificationId:String, val actionId:String, val reportedTimeAwayMin:Float?, val reportedRecoveryMin:Float?, val wasSkipped:Boolean=false, val isDemo:Boolean=false, val createdAt:Long=System.currentTimeMillis())

@Entity(tableName="attention_profile_summary", indices=[Index("category",unique=true)])
data class AttentionProfileSummary(@PrimaryKey val category:String, val observationCount:Int=0, val avgTimeAwayMin:Float=0f, val avgRecoveryMin:Float=0f, val openCount:Int=0, val delayCount:Int=0, val dismissCount:Int=0, val lastUpdatedMs:Long=System.currentTimeMillis())

/** Optional self-reported energy. v3 adds a 1–5 [score] (legacy rows: LOW=2, MODERATE=3, HIGH=4) and when it was recorded. */
@Entity(tableName="energy_checkins")
data class EnergyCheckIn(@PrimaryKey val id:String, val level:EnergyLevel, val timestampMs:Long, val sessionId:String?=null, @ColumnInfo(defaultValue="0") val isDemo:Boolean=false,
    @ColumnInfo(defaultValue="3") val score:Int=3,
    /** BEFORE / AFTER a session, or null for an anytime check-in. */
    val phase:String?=null) {
    companion object {
        fun levelFor(score:Int) = when { score <= 2 -> EnergyLevel.LOW; score >= 4 -> EnergyLevel.HIGH; else -> EnergyLevel.MODERATE }
    }
}

/**
 * One row per notification Attune evaluated (deduplicated by [dedupKey], so updates to the same Android notification
 * increment [updateCount] instead of adding rows). Stores only metadata, scores and generic reasons — never message text.
 */
@Entity(tableName="notification_decisions", indices=[Index(value=["dedupKey"], unique=true), Index("decidedAtMs"), Index("sessionId")])
data class NotificationDecisionRecord(
    @PrimaryKey val id:String, val dedupKey:String, val packageName:String, val appLabel:String, val category:String,
    val postedAtMs:Long, val decidedAtMs:Long, val sessionId:String?, val phase:String, val routing:String, val focusRisk:String,
    val importance:String?, val pImportant:Float?, val pUrgent:Float?, val pDistracting:Float?, val pAction:Float?, val confidence:Float?,
    val personalBias:Float?, val source:String, val reasons:String, val modelVersion:String?,
    val hasLink:Boolean, val linkSafety:String, val isSensitive:Boolean, val evidence:String?, val senderKey:String?,
    val overrodeModel:Boolean, val modelRouting:String?, val matchedRule:String?, val notificationId:String?,
    val isDemo:Boolean, val updateCount:Int
)

/** Explicit feedback on a decision. Personalization is recomputed from these rows, so deleting them removes their effect. */
@Entity(tableName="feedback_events", indices=[Index("createdMs")])
data class FeedbackEvent(
    @PrimaryKey val id:String, val createdMs:Long, val kind:String, val packageName:String, val appLabel:String, val category:String,
    val senderKey:String?, val senderHint:String?, val notificationId:String?, val decisionId:String?,
    val predictedRisk:String?, val predictedRouting:String?, val isDemo:Boolean
)

/** Explicit user rule (ALLOW or DEFER) for an app, contact, keyword or category. */
@Entity(tableName="user_rules")
data class UserRuleRecord(@PrimaryKey val id:String, val kind:String, val value:String, val action:String, val label:String, val createdMs:Long, val source:String)

/** Timeline entry for a focus session (start, pause, resume, notification held/allowed, opened…). [detail] never contains message text. */
@Entity(tableName="session_events", indices=[Index("sessionId")])
data class SessionEvent(@PrimaryKey val id:String, val sessionId:String, val atMs:Long, val type:String, val detail:String?, val isDemo:Boolean)

/** Query result: a self-reported observation joined with its notification category. */
data class CategoryObservation(val category:String, val timeAway:Float, val recovery:Float)
