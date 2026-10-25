package com.attune.core.data.db;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import com.attune.core.data.db.dao.AttentionProfileSummaryDao;
import com.attune.core.data.db.dao.AttentionProfileSummaryDao_Impl;
import com.attune.core.data.db.dao.EnergyCheckInDao;
import com.attune.core.data.db.dao.EnergyCheckInDao_Impl;
import com.attune.core.data.db.dao.FeedbackDao;
import com.attune.core.data.db.dao.FeedbackDao_Impl;
import com.attune.core.data.db.dao.FocusSessionDao;
import com.attune.core.data.db.dao.FocusSessionDao_Impl;
import com.attune.core.data.db.dao.PredictionDao;
import com.attune.core.data.db.dao.PredictionDao_Impl;
import com.attune.core.data.db.dao.QueuedNotificationDao;
import com.attune.core.data.db.dao.QueuedNotificationDao_Impl;
import com.attune.core.data.db.dao.UserActionDao;
import com.attune.core.data.db.dao.UserActionDao_Impl;
import com.attune.core.data.db.dao.UserProfileDao;
import com.attune.core.data.db.dao.UserProfileDao_Impl;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class AttuneDatabase_Impl extends AttuneDatabase {
  private volatile UserProfileDao _userProfileDao;

  private volatile FocusSessionDao _focusSessionDao;

  private volatile QueuedNotificationDao _queuedNotificationDao;

  private volatile PredictionDao _predictionDao;

  private volatile UserActionDao _userActionDao;

  private volatile FeedbackDao _feedbackDao;

  private volatile AttentionProfileSummaryDao _attentionProfileSummaryDao;

  private volatile EnergyCheckInDao _energyCheckInDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(2) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `user_profile` (`id` INTEGER NOT NULL, `focusDurationMin` INTEGER NOT NULL, `breakDurationMin` INTEGER NOT NULL, `onboardingComplete` INTEGER NOT NULL, `selectedAppPackages` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `focus_sessions` (`id` TEXT NOT NULL, `sessionStartMs` INTEGER NOT NULL, `plannedEndMs` INTEGER NOT NULL, `actualEndMs` INTEGER, `state` TEXT NOT NULL, `breakStartMs` INTEGER, `isDemo` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `plannedDurationMin` INTEGER NOT NULL DEFAULT 0, `totalPausedMs` INTEGER NOT NULL DEFAULT 0, `pauseCount` INTEGER NOT NULL DEFAULT 0, `interruptionCount` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`id`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `queued_notifications` (`id` TEXT NOT NULL, `sessionId` TEXT NOT NULL, `packageName` TEXT NOT NULL, `category` TEXT NOT NULL, `title` TEXT, `previewText` TEXT, `dedupKey` TEXT NOT NULL, `postTimeMs` INTEGER NOT NULL, `status` TEXT NOT NULL, `contentIntentData` TEXT, `isGroupSummary` INTEGER NOT NULL, `isDemo` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `appLabel` TEXT NOT NULL DEFAULT '', `sbnKey` TEXT, `groupCount` INTEGER NOT NULL DEFAULT 1, `lastUpdatedMs` INTEGER NOT NULL DEFAULT 0, `wasSuppressed` INTEGER NOT NULL DEFAULT 0, `sourceRemoved` INTEGER NOT NULL DEFAULT 0, `ruleApplied` TEXT, `androidCategory` TEXT, `statusChangedMs` INTEGER, PRIMARY KEY(`id`))");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_queued_notifications_dedupKey` ON `queued_notifications` (`dedupKey`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `predictions` (`id` TEXT NOT NULL, `notificationId` TEXT NOT NULL, `risk` TEXT NOT NULL, `timeAwayMin` REAL NOT NULL, `recoveryMin` REAL NOT NULL, `explanation` TEXT NOT NULL, `confidence` REAL NOT NULL, `observationCount` INTEGER NOT NULL, `delayScenario` TEXT NOT NULL, `isEstimate` INTEGER NOT NULL, `isDemo` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `modelVersion` TEXT, `factorsSummary` TEXT, `sessionStateAtPrediction` TEXT, PRIMARY KEY(`id`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `user_actions` (`id` TEXT NOT NULL, `notificationId` TEXT NOT NULL, `predictionId` TEXT, `actionType` TEXT NOT NULL, `actionTimeMs` INTEGER NOT NULL, `isDemo` INTEGER NOT NULL, `sessionId` TEXT, `sessionState` TEXT, `category` TEXT, `launchAttempted` INTEGER NOT NULL DEFAULT 0, `launchResult` TEXT, `launchedAtMs` INTEGER, `returnedAtMs` INTEGER, `predictedTimeAwayMin` REAL, `predictedRecoveryMin` REAL, `sessionOutcome` TEXT, PRIMARY KEY(`id`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `feedback` (`id` TEXT NOT NULL, `notificationId` TEXT NOT NULL, `actionId` TEXT NOT NULL, `reportedTimeAwayMin` REAL, `reportedRecoveryMin` REAL, `wasSkipped` INTEGER NOT NULL, `isDemo` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `attention_profile_summary` (`category` TEXT NOT NULL, `observationCount` INTEGER NOT NULL, `avgTimeAwayMin` REAL NOT NULL, `avgRecoveryMin` REAL NOT NULL, `openCount` INTEGER NOT NULL, `delayCount` INTEGER NOT NULL, `dismissCount` INTEGER NOT NULL, `lastUpdatedMs` INTEGER NOT NULL, PRIMARY KEY(`category`))");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_attention_profile_summary_category` ON `attention_profile_summary` (`category`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `energy_checkins` (`id` TEXT NOT NULL, `level` TEXT NOT NULL, `timestampMs` INTEGER NOT NULL, `sessionId` TEXT, `isDemo` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`id`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'c0f57e141c9aa5a7f3534d31abaf5305')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `user_profile`");
        db.execSQL("DROP TABLE IF EXISTS `focus_sessions`");
        db.execSQL("DROP TABLE IF EXISTS `queued_notifications`");
        db.execSQL("DROP TABLE IF EXISTS `predictions`");
        db.execSQL("DROP TABLE IF EXISTS `user_actions`");
        db.execSQL("DROP TABLE IF EXISTS `feedback`");
        db.execSQL("DROP TABLE IF EXISTS `attention_profile_summary`");
        db.execSQL("DROP TABLE IF EXISTS `energy_checkins`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsUserProfile = new HashMap<String, TableInfo.Column>(6);
        _columnsUserProfile.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUserProfile.put("focusDurationMin", new TableInfo.Column("focusDurationMin", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUserProfile.put("breakDurationMin", new TableInfo.Column("breakDurationMin", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUserProfile.put("onboardingComplete", new TableInfo.Column("onboardingComplete", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUserProfile.put("selectedAppPackages", new TableInfo.Column("selectedAppPackages", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUserProfile.put("createdAt", new TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysUserProfile = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesUserProfile = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoUserProfile = new TableInfo("user_profile", _columnsUserProfile, _foreignKeysUserProfile, _indicesUserProfile);
        final TableInfo _existingUserProfile = TableInfo.read(db, "user_profile");
        if (!_infoUserProfile.equals(_existingUserProfile)) {
          return new RoomOpenHelper.ValidationResult(false, "user_profile(com.attune.core.model.UserProfile).\n"
                  + " Expected:\n" + _infoUserProfile + "\n"
                  + " Found:\n" + _existingUserProfile);
        }
        final HashMap<String, TableInfo.Column> _columnsFocusSessions = new HashMap<String, TableInfo.Column>(12);
        _columnsFocusSessions.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsFocusSessions.put("sessionStartMs", new TableInfo.Column("sessionStartMs", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsFocusSessions.put("plannedEndMs", new TableInfo.Column("plannedEndMs", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsFocusSessions.put("actualEndMs", new TableInfo.Column("actualEndMs", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsFocusSessions.put("state", new TableInfo.Column("state", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsFocusSessions.put("breakStartMs", new TableInfo.Column("breakStartMs", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsFocusSessions.put("isDemo", new TableInfo.Column("isDemo", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsFocusSessions.put("createdAt", new TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsFocusSessions.put("plannedDurationMin", new TableInfo.Column("plannedDurationMin", "INTEGER", true, 0, "0", TableInfo.CREATED_FROM_ENTITY));
        _columnsFocusSessions.put("totalPausedMs", new TableInfo.Column("totalPausedMs", "INTEGER", true, 0, "0", TableInfo.CREATED_FROM_ENTITY));
        _columnsFocusSessions.put("pauseCount", new TableInfo.Column("pauseCount", "INTEGER", true, 0, "0", TableInfo.CREATED_FROM_ENTITY));
        _columnsFocusSessions.put("interruptionCount", new TableInfo.Column("interruptionCount", "INTEGER", true, 0, "0", TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysFocusSessions = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesFocusSessions = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoFocusSessions = new TableInfo("focus_sessions", _columnsFocusSessions, _foreignKeysFocusSessions, _indicesFocusSessions);
        final TableInfo _existingFocusSessions = TableInfo.read(db, "focus_sessions");
        if (!_infoFocusSessions.equals(_existingFocusSessions)) {
          return new RoomOpenHelper.ValidationResult(false, "focus_sessions(com.attune.core.model.FocusSession).\n"
                  + " Expected:\n" + _infoFocusSessions + "\n"
                  + " Found:\n" + _existingFocusSessions);
        }
        final HashMap<String, TableInfo.Column> _columnsQueuedNotifications = new HashMap<String, TableInfo.Column>(22);
        _columnsQueuedNotifications.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsQueuedNotifications.put("sessionId", new TableInfo.Column("sessionId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsQueuedNotifications.put("packageName", new TableInfo.Column("packageName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsQueuedNotifications.put("category", new TableInfo.Column("category", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsQueuedNotifications.put("title", new TableInfo.Column("title", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsQueuedNotifications.put("previewText", new TableInfo.Column("previewText", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsQueuedNotifications.put("dedupKey", new TableInfo.Column("dedupKey", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsQueuedNotifications.put("postTimeMs", new TableInfo.Column("postTimeMs", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsQueuedNotifications.put("status", new TableInfo.Column("status", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsQueuedNotifications.put("contentIntentData", new TableInfo.Column("contentIntentData", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsQueuedNotifications.put("isGroupSummary", new TableInfo.Column("isGroupSummary", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsQueuedNotifications.put("isDemo", new TableInfo.Column("isDemo", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsQueuedNotifications.put("createdAt", new TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsQueuedNotifications.put("appLabel", new TableInfo.Column("appLabel", "TEXT", true, 0, "''", TableInfo.CREATED_FROM_ENTITY));
        _columnsQueuedNotifications.put("sbnKey", new TableInfo.Column("sbnKey", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsQueuedNotifications.put("groupCount", new TableInfo.Column("groupCount", "INTEGER", true, 0, "1", TableInfo.CREATED_FROM_ENTITY));
        _columnsQueuedNotifications.put("lastUpdatedMs", new TableInfo.Column("lastUpdatedMs", "INTEGER", true, 0, "0", TableInfo.CREATED_FROM_ENTITY));
        _columnsQueuedNotifications.put("wasSuppressed", new TableInfo.Column("wasSuppressed", "INTEGER", true, 0, "0", TableInfo.CREATED_FROM_ENTITY));
        _columnsQueuedNotifications.put("sourceRemoved", new TableInfo.Column("sourceRemoved", "INTEGER", true, 0, "0", TableInfo.CREATED_FROM_ENTITY));
        _columnsQueuedNotifications.put("ruleApplied", new TableInfo.Column("ruleApplied", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsQueuedNotifications.put("androidCategory", new TableInfo.Column("androidCategory", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsQueuedNotifications.put("statusChangedMs", new TableInfo.Column("statusChangedMs", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysQueuedNotifications = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesQueuedNotifications = new HashSet<TableInfo.Index>(1);
        _indicesQueuedNotifications.add(new TableInfo.Index("index_queued_notifications_dedupKey", true, Arrays.asList("dedupKey"), Arrays.asList("ASC")));
        final TableInfo _infoQueuedNotifications = new TableInfo("queued_notifications", _columnsQueuedNotifications, _foreignKeysQueuedNotifications, _indicesQueuedNotifications);
        final TableInfo _existingQueuedNotifications = TableInfo.read(db, "queued_notifications");
        if (!_infoQueuedNotifications.equals(_existingQueuedNotifications)) {
          return new RoomOpenHelper.ValidationResult(false, "queued_notifications(com.attune.core.model.QueuedNotification).\n"
                  + " Expected:\n" + _infoQueuedNotifications + "\n"
                  + " Found:\n" + _existingQueuedNotifications);
        }
        final HashMap<String, TableInfo.Column> _columnsPredictions = new HashMap<String, TableInfo.Column>(15);
        _columnsPredictions.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPredictions.put("notificationId", new TableInfo.Column("notificationId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPredictions.put("risk", new TableInfo.Column("risk", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPredictions.put("timeAwayMin", new TableInfo.Column("timeAwayMin", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPredictions.put("recoveryMin", new TableInfo.Column("recoveryMin", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPredictions.put("explanation", new TableInfo.Column("explanation", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPredictions.put("confidence", new TableInfo.Column("confidence", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPredictions.put("observationCount", new TableInfo.Column("observationCount", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPredictions.put("delayScenario", new TableInfo.Column("delayScenario", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPredictions.put("isEstimate", new TableInfo.Column("isEstimate", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPredictions.put("isDemo", new TableInfo.Column("isDemo", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPredictions.put("createdAt", new TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPredictions.put("modelVersion", new TableInfo.Column("modelVersion", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPredictions.put("factorsSummary", new TableInfo.Column("factorsSummary", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPredictions.put("sessionStateAtPrediction", new TableInfo.Column("sessionStateAtPrediction", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysPredictions = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesPredictions = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoPredictions = new TableInfo("predictions", _columnsPredictions, _foreignKeysPredictions, _indicesPredictions);
        final TableInfo _existingPredictions = TableInfo.read(db, "predictions");
        if (!_infoPredictions.equals(_existingPredictions)) {
          return new RoomOpenHelper.ValidationResult(false, "predictions(com.attune.core.model.Prediction).\n"
                  + " Expected:\n" + _infoPredictions + "\n"
                  + " Found:\n" + _existingPredictions);
        }
        final HashMap<String, TableInfo.Column> _columnsUserActions = new HashMap<String, TableInfo.Column>(16);
        _columnsUserActions.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUserActions.put("notificationId", new TableInfo.Column("notificationId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUserActions.put("predictionId", new TableInfo.Column("predictionId", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUserActions.put("actionType", new TableInfo.Column("actionType", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUserActions.put("actionTimeMs", new TableInfo.Column("actionTimeMs", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUserActions.put("isDemo", new TableInfo.Column("isDemo", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUserActions.put("sessionId", new TableInfo.Column("sessionId", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUserActions.put("sessionState", new TableInfo.Column("sessionState", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUserActions.put("category", new TableInfo.Column("category", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUserActions.put("launchAttempted", new TableInfo.Column("launchAttempted", "INTEGER", true, 0, "0", TableInfo.CREATED_FROM_ENTITY));
        _columnsUserActions.put("launchResult", new TableInfo.Column("launchResult", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUserActions.put("launchedAtMs", new TableInfo.Column("launchedAtMs", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUserActions.put("returnedAtMs", new TableInfo.Column("returnedAtMs", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUserActions.put("predictedTimeAwayMin", new TableInfo.Column("predictedTimeAwayMin", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUserActions.put("predictedRecoveryMin", new TableInfo.Column("predictedRecoveryMin", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUserActions.put("sessionOutcome", new TableInfo.Column("sessionOutcome", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysUserActions = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesUserActions = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoUserActions = new TableInfo("user_actions", _columnsUserActions, _foreignKeysUserActions, _indicesUserActions);
        final TableInfo _existingUserActions = TableInfo.read(db, "user_actions");
        if (!_infoUserActions.equals(_existingUserActions)) {
          return new RoomOpenHelper.ValidationResult(false, "user_actions(com.attune.core.model.UserAction).\n"
                  + " Expected:\n" + _infoUserActions + "\n"
                  + " Found:\n" + _existingUserActions);
        }
        final HashMap<String, TableInfo.Column> _columnsFeedback = new HashMap<String, TableInfo.Column>(8);
        _columnsFeedback.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsFeedback.put("notificationId", new TableInfo.Column("notificationId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsFeedback.put("actionId", new TableInfo.Column("actionId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsFeedback.put("reportedTimeAwayMin", new TableInfo.Column("reportedTimeAwayMin", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsFeedback.put("reportedRecoveryMin", new TableInfo.Column("reportedRecoveryMin", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsFeedback.put("wasSkipped", new TableInfo.Column("wasSkipped", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsFeedback.put("isDemo", new TableInfo.Column("isDemo", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsFeedback.put("createdAt", new TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysFeedback = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesFeedback = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoFeedback = new TableInfo("feedback", _columnsFeedback, _foreignKeysFeedback, _indicesFeedback);
        final TableInfo _existingFeedback = TableInfo.read(db, "feedback");
        if (!_infoFeedback.equals(_existingFeedback)) {
          return new RoomOpenHelper.ValidationResult(false, "feedback(com.attune.core.model.Feedback).\n"
                  + " Expected:\n" + _infoFeedback + "\n"
                  + " Found:\n" + _existingFeedback);
        }
        final HashMap<String, TableInfo.Column> _columnsAttentionProfileSummary = new HashMap<String, TableInfo.Column>(8);
        _columnsAttentionProfileSummary.put("category", new TableInfo.Column("category", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAttentionProfileSummary.put("observationCount", new TableInfo.Column("observationCount", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAttentionProfileSummary.put("avgTimeAwayMin", new TableInfo.Column("avgTimeAwayMin", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAttentionProfileSummary.put("avgRecoveryMin", new TableInfo.Column("avgRecoveryMin", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAttentionProfileSummary.put("openCount", new TableInfo.Column("openCount", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAttentionProfileSummary.put("delayCount", new TableInfo.Column("delayCount", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAttentionProfileSummary.put("dismissCount", new TableInfo.Column("dismissCount", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAttentionProfileSummary.put("lastUpdatedMs", new TableInfo.Column("lastUpdatedMs", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysAttentionProfileSummary = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesAttentionProfileSummary = new HashSet<TableInfo.Index>(1);
        _indicesAttentionProfileSummary.add(new TableInfo.Index("index_attention_profile_summary_category", true, Arrays.asList("category"), Arrays.asList("ASC")));
        final TableInfo _infoAttentionProfileSummary = new TableInfo("attention_profile_summary", _columnsAttentionProfileSummary, _foreignKeysAttentionProfileSummary, _indicesAttentionProfileSummary);
        final TableInfo _existingAttentionProfileSummary = TableInfo.read(db, "attention_profile_summary");
        if (!_infoAttentionProfileSummary.equals(_existingAttentionProfileSummary)) {
          return new RoomOpenHelper.ValidationResult(false, "attention_profile_summary(com.attune.core.model.AttentionProfileSummary).\n"
                  + " Expected:\n" + _infoAttentionProfileSummary + "\n"
                  + " Found:\n" + _existingAttentionProfileSummary);
        }
        final HashMap<String, TableInfo.Column> _columnsEnergyCheckins = new HashMap<String, TableInfo.Column>(5);
        _columnsEnergyCheckins.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEnergyCheckins.put("level", new TableInfo.Column("level", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEnergyCheckins.put("timestampMs", new TableInfo.Column("timestampMs", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEnergyCheckins.put("sessionId", new TableInfo.Column("sessionId", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEnergyCheckins.put("isDemo", new TableInfo.Column("isDemo", "INTEGER", true, 0, "0", TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysEnergyCheckins = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesEnergyCheckins = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoEnergyCheckins = new TableInfo("energy_checkins", _columnsEnergyCheckins, _foreignKeysEnergyCheckins, _indicesEnergyCheckins);
        final TableInfo _existingEnergyCheckins = TableInfo.read(db, "energy_checkins");
        if (!_infoEnergyCheckins.equals(_existingEnergyCheckins)) {
          return new RoomOpenHelper.ValidationResult(false, "energy_checkins(com.attune.core.model.EnergyCheckIn).\n"
                  + " Expected:\n" + _infoEnergyCheckins + "\n"
                  + " Found:\n" + _existingEnergyCheckins);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "c0f57e141c9aa5a7f3534d31abaf5305", "2d1fc6a4cbd61bbbc63c60dc26ef4942");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "user_profile","focus_sessions","queued_notifications","predictions","user_actions","feedback","attention_profile_summary","energy_checkins");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    try {
      super.beginTransaction();
      _db.execSQL("DELETE FROM `user_profile`");
      _db.execSQL("DELETE FROM `focus_sessions`");
      _db.execSQL("DELETE FROM `queued_notifications`");
      _db.execSQL("DELETE FROM `predictions`");
      _db.execSQL("DELETE FROM `user_actions`");
      _db.execSQL("DELETE FROM `feedback`");
      _db.execSQL("DELETE FROM `attention_profile_summary`");
      _db.execSQL("DELETE FROM `energy_checkins`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(UserProfileDao.class, UserProfileDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(FocusSessionDao.class, FocusSessionDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(QueuedNotificationDao.class, QueuedNotificationDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(PredictionDao.class, PredictionDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(UserActionDao.class, UserActionDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(FeedbackDao.class, FeedbackDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(AttentionProfileSummaryDao.class, AttentionProfileSummaryDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(EnergyCheckInDao.class, EnergyCheckInDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public UserProfileDao userProfileDao() {
    if (_userProfileDao != null) {
      return _userProfileDao;
    } else {
      synchronized(this) {
        if(_userProfileDao == null) {
          _userProfileDao = new UserProfileDao_Impl(this);
        }
        return _userProfileDao;
      }
    }
  }

  @Override
  public FocusSessionDao focusSessionDao() {
    if (_focusSessionDao != null) {
      return _focusSessionDao;
    } else {
      synchronized(this) {
        if(_focusSessionDao == null) {
          _focusSessionDao = new FocusSessionDao_Impl(this);
        }
        return _focusSessionDao;
      }
    }
  }

  @Override
  public QueuedNotificationDao queuedNotificationDao() {
    if (_queuedNotificationDao != null) {
      return _queuedNotificationDao;
    } else {
      synchronized(this) {
        if(_queuedNotificationDao == null) {
          _queuedNotificationDao = new QueuedNotificationDao_Impl(this);
        }
        return _queuedNotificationDao;
      }
    }
  }

  @Override
  public PredictionDao predictionDao() {
    if (_predictionDao != null) {
      return _predictionDao;
    } else {
      synchronized(this) {
        if(_predictionDao == null) {
          _predictionDao = new PredictionDao_Impl(this);
        }
        return _predictionDao;
      }
    }
  }

  @Override
  public UserActionDao userActionDao() {
    if (_userActionDao != null) {
      return _userActionDao;
    } else {
      synchronized(this) {
        if(_userActionDao == null) {
          _userActionDao = new UserActionDao_Impl(this);
        }
        return _userActionDao;
      }
    }
  }

  @Override
  public FeedbackDao feedbackDao() {
    if (_feedbackDao != null) {
      return _feedbackDao;
    } else {
      synchronized(this) {
        if(_feedbackDao == null) {
          _feedbackDao = new FeedbackDao_Impl(this);
        }
        return _feedbackDao;
      }
    }
  }

  @Override
  public AttentionProfileSummaryDao attentionProfileDao() {
    if (_attentionProfileSummaryDao != null) {
      return _attentionProfileSummaryDao;
    } else {
      synchronized(this) {
        if(_attentionProfileSummaryDao == null) {
          _attentionProfileSummaryDao = new AttentionProfileSummaryDao_Impl(this);
        }
        return _attentionProfileSummaryDao;
      }
    }
  }

  @Override
  public EnergyCheckInDao energyCheckInDao() {
    if (_energyCheckInDao != null) {
      return _energyCheckInDao;
    } else {
      synchronized(this) {
        if(_energyCheckInDao == null) {
          _energyCheckInDao = new EnergyCheckInDao_Impl(this);
        }
        return _energyCheckInDao;
      }
    }
  }
}
