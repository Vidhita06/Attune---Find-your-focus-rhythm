package com.attune.core.data.db
import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.attune.core.data.db.dao.*
import com.attune.core.model.*
@Database(entities=[UserProfile::class,FocusSession::class,QueuedNotification::class,Prediction::class,UserAction::class,Feedback::class,AttentionProfileSummary::class,EnergyCheckIn::class,
    NotificationDecisionRecord::class,FeedbackEvent::class,UserRuleRecord::class,SessionEvent::class], version=3, exportSchema=true)
@TypeConverters(Converters::class)
abstract class AttuneDatabase : RoomDatabase() {
    abstract fun userProfileDao(): UserProfileDao
    abstract fun focusSessionDao(): FocusSessionDao
    abstract fun queuedNotificationDao(): QueuedNotificationDao
    abstract fun predictionDao(): PredictionDao
    abstract fun userActionDao(): UserActionDao
    abstract fun feedbackDao(): FeedbackDao
    abstract fun attentionProfileDao(): AttentionProfileSummaryDao
    abstract fun energyCheckInDao(): EnergyCheckInDao
    abstract fun decisionDao(): DecisionDao
    abstract fun feedbackEventDao(): FeedbackEventDao
    abstract fun userRuleDao(): UserRuleDao
    abstract fun sessionEventDao(): SessionEventDao
    companion object {
        @Volatile private var INSTANCE: AttuneDatabase? = null
        fun getInstance(ctx: Context): AttuneDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(ctx.applicationContext, AttuneDatabase::class.java, "attune.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build().also { INSTANCE = it }
        }

        /**
         * v2 -> v3: additive only. Adds the decision log, feedback events, user rules and session timeline tables, a 1–5
         * energy score (backfilled from the old 3-level value) and queue privacy/risk columns. Nothing is dropped or rewritten.
         * The statements live in [Migrations.SQL_2_3] so the JVM migration test runs exactly the same SQL.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) { Migrations.SQL_2_3.forEach(db::execSQL) }
        }

        /** v1 -> v2: additive only. Adds timer/pause bookkeeping, queue metadata, outcome fields and energy check-ins. No data is dropped. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf(
                    "ALTER TABLE focus_sessions ADD COLUMN plannedDurationMin INTEGER NOT NULL DEFAULT 0",
                    "ALTER TABLE focus_sessions ADD COLUMN totalPausedMs INTEGER NOT NULL DEFAULT 0",
                    "ALTER TABLE focus_sessions ADD COLUMN pauseCount INTEGER NOT NULL DEFAULT 0",
                    "ALTER TABLE focus_sessions ADD COLUMN interruptionCount INTEGER NOT NULL DEFAULT 0",
                    "UPDATE focus_sessions SET plannedDurationMin = CAST((plannedEndMs - sessionStartMs) / 60000 AS INTEGER)",
                    "ALTER TABLE queued_notifications ADD COLUMN appLabel TEXT NOT NULL DEFAULT ''",
                    "ALTER TABLE queued_notifications ADD COLUMN sbnKey TEXT",
                    "ALTER TABLE queued_notifications ADD COLUMN groupCount INTEGER NOT NULL DEFAULT 1",
                    "ALTER TABLE queued_notifications ADD COLUMN lastUpdatedMs INTEGER NOT NULL DEFAULT 0",
                    "ALTER TABLE queued_notifications ADD COLUMN wasSuppressed INTEGER NOT NULL DEFAULT 0",
                    "ALTER TABLE queued_notifications ADD COLUMN sourceRemoved INTEGER NOT NULL DEFAULT 0",
                    "ALTER TABLE queued_notifications ADD COLUMN ruleApplied TEXT",
                    "ALTER TABLE queued_notifications ADD COLUMN androidCategory TEXT",
                    "ALTER TABLE queued_notifications ADD COLUMN statusChangedMs INTEGER",
                    "UPDATE queued_notifications SET lastUpdatedMs = postTimeMs",
                    "ALTER TABLE predictions ADD COLUMN modelVersion TEXT",
                    "ALTER TABLE predictions ADD COLUMN factorsSummary TEXT",
                    "ALTER TABLE predictions ADD COLUMN sessionStateAtPrediction TEXT",
                    "ALTER TABLE user_actions ADD COLUMN sessionId TEXT",
                    "ALTER TABLE user_actions ADD COLUMN sessionState TEXT",
                    "ALTER TABLE user_actions ADD COLUMN category TEXT",
                    "ALTER TABLE user_actions ADD COLUMN launchAttempted INTEGER NOT NULL DEFAULT 0",
                    "ALTER TABLE user_actions ADD COLUMN launchResult TEXT",
                    "ALTER TABLE user_actions ADD COLUMN launchedAtMs INTEGER",
                    "ALTER TABLE user_actions ADD COLUMN returnedAtMs INTEGER",
                    "ALTER TABLE user_actions ADD COLUMN predictedTimeAwayMin REAL",
                    "ALTER TABLE user_actions ADD COLUMN predictedRecoveryMin REAL",
                    "ALTER TABLE user_actions ADD COLUMN sessionOutcome TEXT",
                    "CREATE TABLE IF NOT EXISTS `energy_checkins` (`id` TEXT NOT NULL, `level` TEXT NOT NULL, `timestampMs` INTEGER NOT NULL, `sessionId` TEXT, `isDemo` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`id`))"
                ).forEach(db::execSQL)
            }
        }
    }
}
object Migrations {
    val SQL_2_3 = listOf(
        "ALTER TABLE queued_notifications ADD COLUMN decisionId TEXT",
        "ALTER TABLE queued_notifications ADD COLUMN focusRisk TEXT",
        "ALTER TABLE queued_notifications ADD COLUMN isSensitive INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE queued_notifications ADD COLUMN keptVisible INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE queued_notifications ADD COLUMN textScrubbed INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE energy_checkins ADD COLUMN score INTEGER NOT NULL DEFAULT 3",
        "ALTER TABLE energy_checkins ADD COLUMN phase TEXT",
        "UPDATE energy_checkins SET score = CASE level WHEN 'LOW' THEN 2 WHEN 'HIGH' THEN 4 ELSE 3 END",
        "CREATE TABLE IF NOT EXISTS `notification_decisions` (`id` TEXT NOT NULL, `dedupKey` TEXT NOT NULL, `packageName` TEXT NOT NULL, `appLabel` TEXT NOT NULL, `category` TEXT NOT NULL, `postedAtMs` INTEGER NOT NULL, `decidedAtMs` INTEGER NOT NULL, `sessionId` TEXT, `phase` TEXT NOT NULL, `routing` TEXT NOT NULL, `focusRisk` TEXT NOT NULL, `importance` TEXT, `pImportant` REAL, `pUrgent` REAL, `pDistracting` REAL, `pAction` REAL, `confidence` REAL, `personalBias` REAL, `source` TEXT NOT NULL, `reasons` TEXT NOT NULL, `modelVersion` TEXT, `hasLink` INTEGER NOT NULL, `linkSafety` TEXT NOT NULL, `isSensitive` INTEGER NOT NULL, `evidence` TEXT, `senderKey` TEXT, `overrodeModel` INTEGER NOT NULL, `modelRouting` TEXT, `matchedRule` TEXT, `notificationId` TEXT, `isDemo` INTEGER NOT NULL, `updateCount` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_notification_decisions_dedupKey` ON `notification_decisions` (`dedupKey`)",
        "CREATE INDEX IF NOT EXISTS `index_notification_decisions_decidedAtMs` ON `notification_decisions` (`decidedAtMs`)",
        "CREATE INDEX IF NOT EXISTS `index_notification_decisions_sessionId` ON `notification_decisions` (`sessionId`)",
        "CREATE TABLE IF NOT EXISTS `feedback_events` (`id` TEXT NOT NULL, `createdMs` INTEGER NOT NULL, `kind` TEXT NOT NULL, `packageName` TEXT NOT NULL, `appLabel` TEXT NOT NULL, `category` TEXT NOT NULL, `senderKey` TEXT, `senderHint` TEXT, `notificationId` TEXT, `decisionId` TEXT, `predictedRisk` TEXT, `predictedRouting` TEXT, `isDemo` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE INDEX IF NOT EXISTS `index_feedback_events_createdMs` ON `feedback_events` (`createdMs`)",
        "CREATE TABLE IF NOT EXISTS `user_rules` (`id` TEXT NOT NULL, `kind` TEXT NOT NULL, `value` TEXT NOT NULL, `action` TEXT NOT NULL, `label` TEXT NOT NULL, `createdMs` INTEGER NOT NULL, `source` TEXT NOT NULL, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `session_events` (`id` TEXT NOT NULL, `sessionId` TEXT NOT NULL, `atMs` INTEGER NOT NULL, `type` TEXT NOT NULL, `detail` TEXT, `isDemo` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE INDEX IF NOT EXISTS `index_session_events_sessionId` ON `session_events` (`sessionId`)"
    )
}

class Converters {
    @TypeConverter fun fromSS(v:SessionState)=v.name; @TypeConverter fun toSS(v:String)=runCatching{SessionState.valueOf(v)}.getOrDefault(SessionState.IDLE)
    @TypeConverter fun fromNS(v:NotificationStatus)=v.name; @TypeConverter fun toNS(v:String)=runCatching{NotificationStatus.valueOf(v)}.getOrDefault(NotificationStatus.PENDING)
    @TypeConverter fun fromAT(v:ActionType)=v.name; @TypeConverter fun toAT(v:String)=ActionType.valueOf(v)
    @TypeConverter fun fromRL(v:RiskLevel)=v.name; @TypeConverter fun toRL(v:String)=runCatching{RiskLevel.valueOf(v)}.getOrDefault(RiskLevel.MED)
    @TypeConverter fun fromEL(v:EnergyLevel)=v.name; @TypeConverter fun toEL(v:String)=runCatching{EnergyLevel.valueOf(v)}.getOrDefault(EnergyLevel.MODERATE)
}
