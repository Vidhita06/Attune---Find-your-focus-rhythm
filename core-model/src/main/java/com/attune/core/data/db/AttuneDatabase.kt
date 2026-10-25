package com.attune.core.data.db
import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.attune.core.data.db.dao.*
import com.attune.core.model.*
@Database(entities=[UserProfile::class,FocusSession::class,QueuedNotification::class,Prediction::class,UserAction::class,Feedback::class,AttentionProfileSummary::class,EnergyCheckIn::class], version=2, exportSchema=true)
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
    companion object {
        @Volatile private var INSTANCE: AttuneDatabase? = null
        fun getInstance(ctx: Context): AttuneDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(ctx.applicationContext, AttuneDatabase::class.java, "attune.db")
                .addMigrations(MIGRATION_1_2)
                .build().also { INSTANCE = it }
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
class Converters {
    @TypeConverter fun fromSS(v:SessionState)=v.name; @TypeConverter fun toSS(v:String)=runCatching{SessionState.valueOf(v)}.getOrDefault(SessionState.IDLE)
    @TypeConverter fun fromNS(v:NotificationStatus)=v.name; @TypeConverter fun toNS(v:String)=runCatching{NotificationStatus.valueOf(v)}.getOrDefault(NotificationStatus.PENDING)
    @TypeConverter fun fromAT(v:ActionType)=v.name; @TypeConverter fun toAT(v:String)=ActionType.valueOf(v)
    @TypeConverter fun fromRL(v:RiskLevel)=v.name; @TypeConverter fun toRL(v:String)=runCatching{RiskLevel.valueOf(v)}.getOrDefault(RiskLevel.MED)
    @TypeConverter fun fromEL(v:EnergyLevel)=v.name; @TypeConverter fun toEL(v:String)=runCatching{EnergyLevel.valueOf(v)}.getOrDefault(EnergyLevel.MODERATE)
}
