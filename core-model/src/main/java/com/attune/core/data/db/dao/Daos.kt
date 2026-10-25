package com.attune.core.data.db.dao
import androidx.room.*
import com.attune.core.model.*
import kotlinx.coroutines.flow.Flow
@Dao interface UserProfileDao {
    @Query("SELECT * FROM user_profile WHERE id=1") fun observe(): Flow<UserProfile?>
    @Query("SELECT * FROM user_profile WHERE id=1") suspend fun get(): UserProfile?
    @Upsert suspend fun upsert(p: UserProfile)
}
@Dao interface FocusSessionDao {
    @Query("SELECT * FROM focus_sessions ORDER BY sessionStartMs DESC LIMIT 1") fun observeLatest(): Flow<FocusSession?>
    @Query("SELECT * FROM focus_sessions ORDER BY sessionStartMs DESC LIMIT 1") suspend fun getLatest(): FocusSession?
    @Query("SELECT * FROM focus_sessions WHERE isDemo=0 ORDER BY sessionStartMs DESC LIMIT 1") fun observeLatestReal(): Flow<FocusSession?>
    @Query("SELECT * FROM focus_sessions ORDER BY sessionStartMs DESC") fun observeAllIncDemo(): Flow<List<FocusSession>>
    @Query("SELECT * FROM focus_sessions WHERE isDemo=0 AND state IN ('FOCUS','PAUSED','BREAK') ORDER BY sessionStartMs DESC LIMIT 1") fun observeActive(): Flow<FocusSession?>
    @Query("SELECT * FROM focus_sessions WHERE isDemo=0 AND state IN ('FOCUS','PAUSED','BREAK') ORDER BY sessionStartMs DESC") suspend fun getAllActive(): List<FocusSession>
    @Query("SELECT * FROM focus_sessions WHERE id=:id") suspend fun getById(id: String): FocusSession?
    @Upsert suspend fun upsert(s: FocusSession)
    @Query("DELETE FROM focus_sessions WHERE isDemo=1") suspend fun deleteDemo()
    @Query("DELETE FROM focus_sessions WHERE isDemo=0 AND state NOT IN ('FOCUS','PAUSED','BREAK')") suspend fun deleteRealFinished()
}
@Dao interface QueuedNotificationDao {
    @Query("SELECT * FROM queued_notifications WHERE status='PENDING' ORDER BY postTimeMs ASC") fun observePending(): Flow<List<QueuedNotification>>
    @Query("SELECT * FROM queued_notifications ORDER BY postTimeMs DESC") fun observeAll(): Flow<List<QueuedNotification>>
    @Query("SELECT * FROM queued_notifications WHERE id=:id") suspend fun getById(id: String): QueuedNotification?
    @Query("SELECT * FROM queued_notifications WHERE id=:id") fun observeById(id: String): Flow<QueuedNotification?>
    @Upsert suspend fun upsert(n: QueuedNotification)
    @Query("UPDATE queued_notifications SET status=:status, statusChangedMs=:atMs WHERE id=:id") suspend fun updateStatus(id: String, status: NotificationStatus, atMs: Long = System.currentTimeMillis())
    @Query("SELECT * FROM queued_notifications WHERE dedupKey=:key LIMIT 1") suspend fun findByDedupKey(key: String): QueuedNotification?
    @Query("SELECT * FROM queued_notifications WHERE packageName=:pkg AND sessionId=:sessionId AND status='PENDING' AND isDemo=0 ORDER BY lastUpdatedMs DESC LIMIT 1") suspend fun findPendingFromPackage(pkg: String, sessionId: String): QueuedNotification?
    @Query("SELECT * FROM queued_notifications WHERE isDemo=0 ORDER BY postTimeMs DESC LIMIT 50") fun observeRecent(): Flow<List<QueuedNotification>>
    @Query("SELECT COUNT(*) FROM queued_notifications WHERE status='PENDING'") fun observePendingCount(): Flow<Int>
    @Query("DELETE FROM queued_notifications WHERE isDemo=1") suspend fun deleteDemo()
    @Query("DELETE FROM queued_notifications WHERE isDemo=0") suspend fun deleteReal()
    @Query("SELECT * FROM queued_notifications WHERE sessionId=:sessionId AND status IN ('PENDING','DELAYED') AND isDemo=0") suspend fun openForSession(sessionId: String): List<QueuedNotification>
    /** Privacy retention: drops title/preview of handled items once they are older than [beforeMs]. Pending/delayed items keep their text. */
    @Query("UPDATE queued_notifications SET title=NULL, previewText=NULL, textScrubbed=1 WHERE isDemo=0 AND textScrubbed=0 AND status NOT IN ('PENDING','DELAYED') AND COALESCE(statusChangedMs, postTimeMs) < :beforeMs")
    suspend fun scrubHandledTextBefore(beforeMs: Long): Int
    @Query("UPDATE queued_notifications SET title=NULL, previewText=NULL, textScrubbed=1 WHERE isDemo=0") suspend fun scrubAllRealText(): Int
    @Query("SELECT COUNT(*) FROM queued_notifications WHERE isDemo=0 AND textScrubbed=0 AND (title IS NOT NULL OR previewText IS NOT NULL)") fun observeRealWithText(): Flow<Int>
}
@Dao interface PredictionDao {
    @Query("SELECT * FROM predictions WHERE notificationId=:id ORDER BY createdAt DESC LIMIT 1") suspend fun getForNotification(id: String): Prediction?
    @Upsert suspend fun upsert(p: Prediction)
    @Query("DELETE FROM predictions WHERE isDemo=1") suspend fun deleteDemo()
    @Query("DELETE FROM predictions WHERE isDemo=0") suspend fun deleteReal()
}
@Dao interface UserActionDao {
    @Query("SELECT * FROM user_actions ORDER BY actionTimeMs DESC") fun observeAll(): Flow<List<UserAction>>
    @Query("SELECT * FROM user_actions WHERE notificationId=:id ORDER BY actionTimeMs DESC") fun observeForNotification(id: String): Flow<List<UserAction>>
    @Query("SELECT * FROM user_actions WHERE id=:id") suspend fun getById(id: String): UserAction?
    @Upsert suspend fun upsert(a: UserAction)
    @Query("SELECT * FROM user_actions WHERE actionType='OPENED' AND launchedAtMs IS NOT NULL AND returnedAtMs IS NULL AND launchedAtMs>=:sinceMs ORDER BY launchedAtMs DESC LIMIT 1") suspend fun latestAwaitingReturn(sinceMs: Long): UserAction?
    @Query("UPDATE user_actions SET returnedAtMs=:atMs WHERE id=:id AND returnedAtMs IS NULL") suspend fun markReturned(id: String, atMs: Long)
    @Query("UPDATE user_actions SET sessionOutcome=:outcome WHERE sessionId=:sessionId") suspend fun setSessionOutcome(sessionId: String, outcome: String)
    @Query("DELETE FROM user_actions WHERE isDemo=1") suspend fun deleteDemo()
    @Query("DELETE FROM user_actions WHERE isDemo=0") suspend fun deleteReal()
}
@Dao interface FeedbackDao {
    @Query("SELECT * FROM feedback WHERE isDemo=0 AND reportedTimeAwayMin IS NOT NULL") suspend fun getAllWithTimeAway(): List<Feedback>
    @Query("SELECT * FROM feedback WHERE isDemo=0") suspend fun getAll(): List<Feedback>
    @Query("SELECT * FROM feedback ORDER BY createdAt DESC") fun observeAll(): Flow<List<Feedback>>
    @Query("SELECT q.category AS category, f.reportedTimeAwayMin AS timeAway, f.reportedRecoveryMin AS recovery FROM feedback f JOIN queued_notifications q ON q.id=f.notificationId WHERE f.isDemo=:demo AND f.reportedTimeAwayMin IS NOT NULL AND f.reportedRecoveryMin IS NOT NULL")
    suspend fun observationsWithCategory(demo: Boolean): List<CategoryObservation>
    @Upsert suspend fun upsert(f: Feedback)
    @Query("DELETE FROM feedback WHERE isDemo=1") suspend fun deleteDemo()
    @Query("DELETE FROM feedback WHERE isDemo=0") suspend fun deleteReal()
}
@Dao interface AttentionProfileSummaryDao {
    @Query("SELECT * FROM attention_profile_summary") fun observeAll(): Flow<List<AttentionProfileSummary>>
    @Query("SELECT * FROM attention_profile_summary WHERE category=:cat") suspend fun getByCategory(cat: String): AttentionProfileSummary?
    @Upsert suspend fun upsert(s: AttentionProfileSummary)
    @Query("DELETE FROM attention_profile_summary") suspend fun deleteAll()
}
@Dao interface EnergyCheckInDao {
    @Query("SELECT * FROM energy_checkins ORDER BY timestampMs DESC") fun observeAll(): Flow<List<EnergyCheckIn>>
    @Query("SELECT * FROM energy_checkins WHERE id=:id") suspend fun getById(id: String): EnergyCheckIn?
    @Upsert suspend fun upsert(c: EnergyCheckIn)
    @Query("DELETE FROM energy_checkins WHERE isDemo=1") suspend fun deleteDemo()
    @Query("DELETE FROM energy_checkins WHERE isDemo=0") suspend fun deleteReal()
}

@Dao interface DecisionDao {
    @Query("SELECT * FROM notification_decisions ORDER BY decidedAtMs DESC") fun observeAll(): Flow<List<NotificationDecisionRecord>>
    @Query("SELECT * FROM notification_decisions WHERE dedupKey=:key LIMIT 1") suspend fun findByDedupKey(key: String): NotificationDecisionRecord?
    @Query("SELECT * FROM notification_decisions WHERE id=:id") suspend fun getById(id: String): NotificationDecisionRecord?
    @Query("SELECT * FROM notification_decisions WHERE id=:id") fun observeById(id: String): Flow<NotificationDecisionRecord?>
    @Query("SELECT * FROM notification_decisions WHERE sessionId=:sessionId ORDER BY postedAtMs ASC") fun observeForSession(sessionId: String): Flow<List<NotificationDecisionRecord>>
    @Query("UPDATE notification_decisions SET notificationId=:notificationId WHERE id=:id") suspend fun attachNotification(id: String, notificationId: String)
    @Upsert suspend fun upsert(r: NotificationDecisionRecord)
    @Query("DELETE FROM notification_decisions WHERE isDemo=1") suspend fun deleteDemo()
    @Query("DELETE FROM notification_decisions WHERE isDemo=0") suspend fun deleteReal()
    @Query("SELECT COUNT(*) FROM notification_decisions WHERE isDemo=0") fun observeRealCount(): Flow<Int>
}
@Dao interface FeedbackEventDao {
    @Query("SELECT * FROM feedback_events ORDER BY createdMs DESC") fun observeAll(): Flow<List<FeedbackEvent>>
    @Query("SELECT * FROM feedback_events WHERE isDemo=0 ORDER BY createdMs DESC") suspend fun getAllReal(): List<FeedbackEvent>
    @Upsert suspend fun upsert(e: FeedbackEvent)
    @Query("DELETE FROM feedback_events WHERE id=:id") suspend fun delete(id: String)
    @Query("DELETE FROM feedback_events WHERE isDemo=0 AND kind IN (:kinds) AND ((:scope='APP' AND packageName=:key) OR (:scope='CATEGORY' AND category=:key) OR (:scope='SENDER' AND senderKey=:key))")
    suspend fun deleteForPreference(scope: String, key: String, kinds: List<String>)
    @Query("DELETE FROM feedback_events WHERE isDemo=0") suspend fun deleteReal()
    @Query("DELETE FROM feedback_events WHERE isDemo=1") suspend fun deleteDemo()
}
@Dao interface UserRuleDao {
    @Query("SELECT * FROM user_rules ORDER BY createdMs DESC") fun observeAll(): Flow<List<UserRuleRecord>>
    @Query("SELECT * FROM user_rules") suspend fun getAll(): List<UserRuleRecord>
    @Upsert suspend fun upsert(r: UserRuleRecord)
    @Query("DELETE FROM user_rules WHERE id=:id") suspend fun delete(id: String)
    @Query("DELETE FROM user_rules") suspend fun deleteAll()
}
@Dao interface SessionEventDao {
    @Query("SELECT * FROM session_events WHERE sessionId=:sessionId ORDER BY atMs ASC") fun observeForSession(sessionId: String): Flow<List<SessionEvent>>
    @Upsert suspend fun upsert(e: SessionEvent)
    @Query("DELETE FROM session_events WHERE isDemo=1") suspend fun deleteDemo()
    @Query("DELETE FROM session_events WHERE isDemo=0 AND sessionId NOT IN (SELECT id FROM focus_sessions WHERE state IN ('FOCUS','PAUSED','BREAK'))") suspend fun deleteRealFinished()
}
