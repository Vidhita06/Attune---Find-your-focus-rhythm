package com.attune.core.data.db.dao;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.EntityUpsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.attune.core.data.db.Converters;
import com.attune.core.model.ActionType;
import com.attune.core.model.UserAction;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Float;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class UserActionDao_Impl implements UserActionDao {
  private final RoomDatabase __db;

  private final SharedSQLiteStatement __preparedStmtOfMarkReturned;

  private final SharedSQLiteStatement __preparedStmtOfSetSessionOutcome;

  private final SharedSQLiteStatement __preparedStmtOfDeleteDemo;

  private final SharedSQLiteStatement __preparedStmtOfDeleteReal;

  private final EntityUpsertionAdapter<UserAction> __upsertionAdapterOfUserAction;

  private final Converters __converters = new Converters();

  public UserActionDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__preparedStmtOfMarkReturned = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE user_actions SET returnedAtMs=? WHERE id=? AND returnedAtMs IS NULL";
        return _query;
      }
    };
    this.__preparedStmtOfSetSessionOutcome = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE user_actions SET sessionOutcome=? WHERE sessionId=?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteDemo = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM user_actions WHERE isDemo=1";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteReal = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM user_actions WHERE isDemo=0";
        return _query;
      }
    };
    this.__upsertionAdapterOfUserAction = new EntityUpsertionAdapter<UserAction>(new EntityInsertionAdapter<UserAction>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT INTO `user_actions` (`id`,`notificationId`,`predictionId`,`actionType`,`actionTimeMs`,`isDemo`,`sessionId`,`sessionState`,`category`,`launchAttempted`,`launchResult`,`launchedAtMs`,`returnedAtMs`,`predictedTimeAwayMin`,`predictedRecoveryMin`,`sessionOutcome`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final UserAction entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getNotificationId());
        if (entity.getPredictionId() == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.getPredictionId());
        }
        final String _tmp = __converters.fromAT(entity.getActionType());
        statement.bindString(4, _tmp);
        statement.bindLong(5, entity.getActionTimeMs());
        final int _tmp_1 = entity.isDemo() ? 1 : 0;
        statement.bindLong(6, _tmp_1);
        if (entity.getSessionId() == null) {
          statement.bindNull(7);
        } else {
          statement.bindString(7, entity.getSessionId());
        }
        if (entity.getSessionState() == null) {
          statement.bindNull(8);
        } else {
          statement.bindString(8, entity.getSessionState());
        }
        if (entity.getCategory() == null) {
          statement.bindNull(9);
        } else {
          statement.bindString(9, entity.getCategory());
        }
        final int _tmp_2 = entity.getLaunchAttempted() ? 1 : 0;
        statement.bindLong(10, _tmp_2);
        if (entity.getLaunchResult() == null) {
          statement.bindNull(11);
        } else {
          statement.bindString(11, entity.getLaunchResult());
        }
        if (entity.getLaunchedAtMs() == null) {
          statement.bindNull(12);
        } else {
          statement.bindLong(12, entity.getLaunchedAtMs());
        }
        if (entity.getReturnedAtMs() == null) {
          statement.bindNull(13);
        } else {
          statement.bindLong(13, entity.getReturnedAtMs());
        }
        if (entity.getPredictedTimeAwayMin() == null) {
          statement.bindNull(14);
        } else {
          statement.bindDouble(14, entity.getPredictedTimeAwayMin());
        }
        if (entity.getPredictedRecoveryMin() == null) {
          statement.bindNull(15);
        } else {
          statement.bindDouble(15, entity.getPredictedRecoveryMin());
        }
        if (entity.getSessionOutcome() == null) {
          statement.bindNull(16);
        } else {
          statement.bindString(16, entity.getSessionOutcome());
        }
      }
    }, new EntityDeletionOrUpdateAdapter<UserAction>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE `user_actions` SET `id` = ?,`notificationId` = ?,`predictionId` = ?,`actionType` = ?,`actionTimeMs` = ?,`isDemo` = ?,`sessionId` = ?,`sessionState` = ?,`category` = ?,`launchAttempted` = ?,`launchResult` = ?,`launchedAtMs` = ?,`returnedAtMs` = ?,`predictedTimeAwayMin` = ?,`predictedRecoveryMin` = ?,`sessionOutcome` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final UserAction entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getNotificationId());
        if (entity.getPredictionId() == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.getPredictionId());
        }
        final String _tmp = __converters.fromAT(entity.getActionType());
        statement.bindString(4, _tmp);
        statement.bindLong(5, entity.getActionTimeMs());
        final int _tmp_1 = entity.isDemo() ? 1 : 0;
        statement.bindLong(6, _tmp_1);
        if (entity.getSessionId() == null) {
          statement.bindNull(7);
        } else {
          statement.bindString(7, entity.getSessionId());
        }
        if (entity.getSessionState() == null) {
          statement.bindNull(8);
        } else {
          statement.bindString(8, entity.getSessionState());
        }
        if (entity.getCategory() == null) {
          statement.bindNull(9);
        } else {
          statement.bindString(9, entity.getCategory());
        }
        final int _tmp_2 = entity.getLaunchAttempted() ? 1 : 0;
        statement.bindLong(10, _tmp_2);
        if (entity.getLaunchResult() == null) {
          statement.bindNull(11);
        } else {
          statement.bindString(11, entity.getLaunchResult());
        }
        if (entity.getLaunchedAtMs() == null) {
          statement.bindNull(12);
        } else {
          statement.bindLong(12, entity.getLaunchedAtMs());
        }
        if (entity.getReturnedAtMs() == null) {
          statement.bindNull(13);
        } else {
          statement.bindLong(13, entity.getReturnedAtMs());
        }
        if (entity.getPredictedTimeAwayMin() == null) {
          statement.bindNull(14);
        } else {
          statement.bindDouble(14, entity.getPredictedTimeAwayMin());
        }
        if (entity.getPredictedRecoveryMin() == null) {
          statement.bindNull(15);
        } else {
          statement.bindDouble(15, entity.getPredictedRecoveryMin());
        }
        if (entity.getSessionOutcome() == null) {
          statement.bindNull(16);
        } else {
          statement.bindString(16, entity.getSessionOutcome());
        }
        statement.bindString(17, entity.getId());
      }
    });
  }

  @Override
  public Object markReturned(final String id, final long atMs,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfMarkReturned.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, atMs);
        _argIndex = 2;
        _stmt.bindString(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfMarkReturned.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object setSessionOutcome(final String sessionId, final String outcome,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfSetSessionOutcome.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, outcome);
        _argIndex = 2;
        _stmt.bindString(_argIndex, sessionId);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfSetSessionOutcome.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteDemo(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteDemo.acquire();
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteDemo.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteReal(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteReal.acquire();
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteReal.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object upsert(final UserAction a, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __upsertionAdapterOfUserAction.upsert(a);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<UserAction>> observeAll() {
    final String _sql = "SELECT * FROM user_actions ORDER BY actionTimeMs DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"user_actions"}, new Callable<List<UserAction>>() {
      @Override
      @NonNull
      public List<UserAction> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfNotificationId = CursorUtil.getColumnIndexOrThrow(_cursor, "notificationId");
          final int _cursorIndexOfPredictionId = CursorUtil.getColumnIndexOrThrow(_cursor, "predictionId");
          final int _cursorIndexOfActionType = CursorUtil.getColumnIndexOrThrow(_cursor, "actionType");
          final int _cursorIndexOfActionTimeMs = CursorUtil.getColumnIndexOrThrow(_cursor, "actionTimeMs");
          final int _cursorIndexOfIsDemo = CursorUtil.getColumnIndexOrThrow(_cursor, "isDemo");
          final int _cursorIndexOfSessionId = CursorUtil.getColumnIndexOrThrow(_cursor, "sessionId");
          final int _cursorIndexOfSessionState = CursorUtil.getColumnIndexOrThrow(_cursor, "sessionState");
          final int _cursorIndexOfCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "category");
          final int _cursorIndexOfLaunchAttempted = CursorUtil.getColumnIndexOrThrow(_cursor, "launchAttempted");
          final int _cursorIndexOfLaunchResult = CursorUtil.getColumnIndexOrThrow(_cursor, "launchResult");
          final int _cursorIndexOfLaunchedAtMs = CursorUtil.getColumnIndexOrThrow(_cursor, "launchedAtMs");
          final int _cursorIndexOfReturnedAtMs = CursorUtil.getColumnIndexOrThrow(_cursor, "returnedAtMs");
          final int _cursorIndexOfPredictedTimeAwayMin = CursorUtil.getColumnIndexOrThrow(_cursor, "predictedTimeAwayMin");
          final int _cursorIndexOfPredictedRecoveryMin = CursorUtil.getColumnIndexOrThrow(_cursor, "predictedRecoveryMin");
          final int _cursorIndexOfSessionOutcome = CursorUtil.getColumnIndexOrThrow(_cursor, "sessionOutcome");
          final List<UserAction> _result = new ArrayList<UserAction>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final UserAction _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpNotificationId;
            _tmpNotificationId = _cursor.getString(_cursorIndexOfNotificationId);
            final String _tmpPredictionId;
            if (_cursor.isNull(_cursorIndexOfPredictionId)) {
              _tmpPredictionId = null;
            } else {
              _tmpPredictionId = _cursor.getString(_cursorIndexOfPredictionId);
            }
            final ActionType _tmpActionType;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfActionType);
            _tmpActionType = __converters.toAT(_tmp);
            final long _tmpActionTimeMs;
            _tmpActionTimeMs = _cursor.getLong(_cursorIndexOfActionTimeMs);
            final boolean _tmpIsDemo;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsDemo);
            _tmpIsDemo = _tmp_1 != 0;
            final String _tmpSessionId;
            if (_cursor.isNull(_cursorIndexOfSessionId)) {
              _tmpSessionId = null;
            } else {
              _tmpSessionId = _cursor.getString(_cursorIndexOfSessionId);
            }
            final String _tmpSessionState;
            if (_cursor.isNull(_cursorIndexOfSessionState)) {
              _tmpSessionState = null;
            } else {
              _tmpSessionState = _cursor.getString(_cursorIndexOfSessionState);
            }
            final String _tmpCategory;
            if (_cursor.isNull(_cursorIndexOfCategory)) {
              _tmpCategory = null;
            } else {
              _tmpCategory = _cursor.getString(_cursorIndexOfCategory);
            }
            final boolean _tmpLaunchAttempted;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfLaunchAttempted);
            _tmpLaunchAttempted = _tmp_2 != 0;
            final String _tmpLaunchResult;
            if (_cursor.isNull(_cursorIndexOfLaunchResult)) {
              _tmpLaunchResult = null;
            } else {
              _tmpLaunchResult = _cursor.getString(_cursorIndexOfLaunchResult);
            }
            final Long _tmpLaunchedAtMs;
            if (_cursor.isNull(_cursorIndexOfLaunchedAtMs)) {
              _tmpLaunchedAtMs = null;
            } else {
              _tmpLaunchedAtMs = _cursor.getLong(_cursorIndexOfLaunchedAtMs);
            }
            final Long _tmpReturnedAtMs;
            if (_cursor.isNull(_cursorIndexOfReturnedAtMs)) {
              _tmpReturnedAtMs = null;
            } else {
              _tmpReturnedAtMs = _cursor.getLong(_cursorIndexOfReturnedAtMs);
            }
            final Float _tmpPredictedTimeAwayMin;
            if (_cursor.isNull(_cursorIndexOfPredictedTimeAwayMin)) {
              _tmpPredictedTimeAwayMin = null;
            } else {
              _tmpPredictedTimeAwayMin = _cursor.getFloat(_cursorIndexOfPredictedTimeAwayMin);
            }
            final Float _tmpPredictedRecoveryMin;
            if (_cursor.isNull(_cursorIndexOfPredictedRecoveryMin)) {
              _tmpPredictedRecoveryMin = null;
            } else {
              _tmpPredictedRecoveryMin = _cursor.getFloat(_cursorIndexOfPredictedRecoveryMin);
            }
            final String _tmpSessionOutcome;
            if (_cursor.isNull(_cursorIndexOfSessionOutcome)) {
              _tmpSessionOutcome = null;
            } else {
              _tmpSessionOutcome = _cursor.getString(_cursorIndexOfSessionOutcome);
            }
            _item = new UserAction(_tmpId,_tmpNotificationId,_tmpPredictionId,_tmpActionType,_tmpActionTimeMs,_tmpIsDemo,_tmpSessionId,_tmpSessionState,_tmpCategory,_tmpLaunchAttempted,_tmpLaunchResult,_tmpLaunchedAtMs,_tmpReturnedAtMs,_tmpPredictedTimeAwayMin,_tmpPredictedRecoveryMin,_tmpSessionOutcome);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<List<UserAction>> observeForNotification(final String id) {
    final String _sql = "SELECT * FROM user_actions WHERE notificationId=? ORDER BY actionTimeMs DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, id);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"user_actions"}, new Callable<List<UserAction>>() {
      @Override
      @NonNull
      public List<UserAction> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfNotificationId = CursorUtil.getColumnIndexOrThrow(_cursor, "notificationId");
          final int _cursorIndexOfPredictionId = CursorUtil.getColumnIndexOrThrow(_cursor, "predictionId");
          final int _cursorIndexOfActionType = CursorUtil.getColumnIndexOrThrow(_cursor, "actionType");
          final int _cursorIndexOfActionTimeMs = CursorUtil.getColumnIndexOrThrow(_cursor, "actionTimeMs");
          final int _cursorIndexOfIsDemo = CursorUtil.getColumnIndexOrThrow(_cursor, "isDemo");
          final int _cursorIndexOfSessionId = CursorUtil.getColumnIndexOrThrow(_cursor, "sessionId");
          final int _cursorIndexOfSessionState = CursorUtil.getColumnIndexOrThrow(_cursor, "sessionState");
          final int _cursorIndexOfCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "category");
          final int _cursorIndexOfLaunchAttempted = CursorUtil.getColumnIndexOrThrow(_cursor, "launchAttempted");
          final int _cursorIndexOfLaunchResult = CursorUtil.getColumnIndexOrThrow(_cursor, "launchResult");
          final int _cursorIndexOfLaunchedAtMs = CursorUtil.getColumnIndexOrThrow(_cursor, "launchedAtMs");
          final int _cursorIndexOfReturnedAtMs = CursorUtil.getColumnIndexOrThrow(_cursor, "returnedAtMs");
          final int _cursorIndexOfPredictedTimeAwayMin = CursorUtil.getColumnIndexOrThrow(_cursor, "predictedTimeAwayMin");
          final int _cursorIndexOfPredictedRecoveryMin = CursorUtil.getColumnIndexOrThrow(_cursor, "predictedRecoveryMin");
          final int _cursorIndexOfSessionOutcome = CursorUtil.getColumnIndexOrThrow(_cursor, "sessionOutcome");
          final List<UserAction> _result = new ArrayList<UserAction>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final UserAction _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpNotificationId;
            _tmpNotificationId = _cursor.getString(_cursorIndexOfNotificationId);
            final String _tmpPredictionId;
            if (_cursor.isNull(_cursorIndexOfPredictionId)) {
              _tmpPredictionId = null;
            } else {
              _tmpPredictionId = _cursor.getString(_cursorIndexOfPredictionId);
            }
            final ActionType _tmpActionType;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfActionType);
            _tmpActionType = __converters.toAT(_tmp);
            final long _tmpActionTimeMs;
            _tmpActionTimeMs = _cursor.getLong(_cursorIndexOfActionTimeMs);
            final boolean _tmpIsDemo;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsDemo);
            _tmpIsDemo = _tmp_1 != 0;
            final String _tmpSessionId;
            if (_cursor.isNull(_cursorIndexOfSessionId)) {
              _tmpSessionId = null;
            } else {
              _tmpSessionId = _cursor.getString(_cursorIndexOfSessionId);
            }
            final String _tmpSessionState;
            if (_cursor.isNull(_cursorIndexOfSessionState)) {
              _tmpSessionState = null;
            } else {
              _tmpSessionState = _cursor.getString(_cursorIndexOfSessionState);
            }
            final String _tmpCategory;
            if (_cursor.isNull(_cursorIndexOfCategory)) {
              _tmpCategory = null;
            } else {
              _tmpCategory = _cursor.getString(_cursorIndexOfCategory);
            }
            final boolean _tmpLaunchAttempted;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfLaunchAttempted);
            _tmpLaunchAttempted = _tmp_2 != 0;
            final String _tmpLaunchResult;
            if (_cursor.isNull(_cursorIndexOfLaunchResult)) {
              _tmpLaunchResult = null;
            } else {
              _tmpLaunchResult = _cursor.getString(_cursorIndexOfLaunchResult);
            }
            final Long _tmpLaunchedAtMs;
            if (_cursor.isNull(_cursorIndexOfLaunchedAtMs)) {
              _tmpLaunchedAtMs = null;
            } else {
              _tmpLaunchedAtMs = _cursor.getLong(_cursorIndexOfLaunchedAtMs);
            }
            final Long _tmpReturnedAtMs;
            if (_cursor.isNull(_cursorIndexOfReturnedAtMs)) {
              _tmpReturnedAtMs = null;
            } else {
              _tmpReturnedAtMs = _cursor.getLong(_cursorIndexOfReturnedAtMs);
            }
            final Float _tmpPredictedTimeAwayMin;
            if (_cursor.isNull(_cursorIndexOfPredictedTimeAwayMin)) {
              _tmpPredictedTimeAwayMin = null;
            } else {
              _tmpPredictedTimeAwayMin = _cursor.getFloat(_cursorIndexOfPredictedTimeAwayMin);
            }
            final Float _tmpPredictedRecoveryMin;
            if (_cursor.isNull(_cursorIndexOfPredictedRecoveryMin)) {
              _tmpPredictedRecoveryMin = null;
            } else {
              _tmpPredictedRecoveryMin = _cursor.getFloat(_cursorIndexOfPredictedRecoveryMin);
            }
            final String _tmpSessionOutcome;
            if (_cursor.isNull(_cursorIndexOfSessionOutcome)) {
              _tmpSessionOutcome = null;
            } else {
              _tmpSessionOutcome = _cursor.getString(_cursorIndexOfSessionOutcome);
            }
            _item = new UserAction(_tmpId,_tmpNotificationId,_tmpPredictionId,_tmpActionType,_tmpActionTimeMs,_tmpIsDemo,_tmpSessionId,_tmpSessionState,_tmpCategory,_tmpLaunchAttempted,_tmpLaunchResult,_tmpLaunchedAtMs,_tmpReturnedAtMs,_tmpPredictedTimeAwayMin,_tmpPredictedRecoveryMin,_tmpSessionOutcome);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object getById(final String id, final Continuation<? super UserAction> $completion) {
    final String _sql = "SELECT * FROM user_actions WHERE id=?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<UserAction>() {
      @Override
      @Nullable
      public UserAction call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfNotificationId = CursorUtil.getColumnIndexOrThrow(_cursor, "notificationId");
          final int _cursorIndexOfPredictionId = CursorUtil.getColumnIndexOrThrow(_cursor, "predictionId");
          final int _cursorIndexOfActionType = CursorUtil.getColumnIndexOrThrow(_cursor, "actionType");
          final int _cursorIndexOfActionTimeMs = CursorUtil.getColumnIndexOrThrow(_cursor, "actionTimeMs");
          final int _cursorIndexOfIsDemo = CursorUtil.getColumnIndexOrThrow(_cursor, "isDemo");
          final int _cursorIndexOfSessionId = CursorUtil.getColumnIndexOrThrow(_cursor, "sessionId");
          final int _cursorIndexOfSessionState = CursorUtil.getColumnIndexOrThrow(_cursor, "sessionState");
          final int _cursorIndexOfCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "category");
          final int _cursorIndexOfLaunchAttempted = CursorUtil.getColumnIndexOrThrow(_cursor, "launchAttempted");
          final int _cursorIndexOfLaunchResult = CursorUtil.getColumnIndexOrThrow(_cursor, "launchResult");
          final int _cursorIndexOfLaunchedAtMs = CursorUtil.getColumnIndexOrThrow(_cursor, "launchedAtMs");
          final int _cursorIndexOfReturnedAtMs = CursorUtil.getColumnIndexOrThrow(_cursor, "returnedAtMs");
          final int _cursorIndexOfPredictedTimeAwayMin = CursorUtil.getColumnIndexOrThrow(_cursor, "predictedTimeAwayMin");
          final int _cursorIndexOfPredictedRecoveryMin = CursorUtil.getColumnIndexOrThrow(_cursor, "predictedRecoveryMin");
          final int _cursorIndexOfSessionOutcome = CursorUtil.getColumnIndexOrThrow(_cursor, "sessionOutcome");
          final UserAction _result;
          if (_cursor.moveToFirst()) {
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpNotificationId;
            _tmpNotificationId = _cursor.getString(_cursorIndexOfNotificationId);
            final String _tmpPredictionId;
            if (_cursor.isNull(_cursorIndexOfPredictionId)) {
              _tmpPredictionId = null;
            } else {
              _tmpPredictionId = _cursor.getString(_cursorIndexOfPredictionId);
            }
            final ActionType _tmpActionType;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfActionType);
            _tmpActionType = __converters.toAT(_tmp);
            final long _tmpActionTimeMs;
            _tmpActionTimeMs = _cursor.getLong(_cursorIndexOfActionTimeMs);
            final boolean _tmpIsDemo;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsDemo);
            _tmpIsDemo = _tmp_1 != 0;
            final String _tmpSessionId;
            if (_cursor.isNull(_cursorIndexOfSessionId)) {
              _tmpSessionId = null;
            } else {
              _tmpSessionId = _cursor.getString(_cursorIndexOfSessionId);
            }
            final String _tmpSessionState;
            if (_cursor.isNull(_cursorIndexOfSessionState)) {
              _tmpSessionState = null;
            } else {
              _tmpSessionState = _cursor.getString(_cursorIndexOfSessionState);
            }
            final String _tmpCategory;
            if (_cursor.isNull(_cursorIndexOfCategory)) {
              _tmpCategory = null;
            } else {
              _tmpCategory = _cursor.getString(_cursorIndexOfCategory);
            }
            final boolean _tmpLaunchAttempted;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfLaunchAttempted);
            _tmpLaunchAttempted = _tmp_2 != 0;
            final String _tmpLaunchResult;
            if (_cursor.isNull(_cursorIndexOfLaunchResult)) {
              _tmpLaunchResult = null;
            } else {
              _tmpLaunchResult = _cursor.getString(_cursorIndexOfLaunchResult);
            }
            final Long _tmpLaunchedAtMs;
            if (_cursor.isNull(_cursorIndexOfLaunchedAtMs)) {
              _tmpLaunchedAtMs = null;
            } else {
              _tmpLaunchedAtMs = _cursor.getLong(_cursorIndexOfLaunchedAtMs);
            }
            final Long _tmpReturnedAtMs;
            if (_cursor.isNull(_cursorIndexOfReturnedAtMs)) {
              _tmpReturnedAtMs = null;
            } else {
              _tmpReturnedAtMs = _cursor.getLong(_cursorIndexOfReturnedAtMs);
            }
            final Float _tmpPredictedTimeAwayMin;
            if (_cursor.isNull(_cursorIndexOfPredictedTimeAwayMin)) {
              _tmpPredictedTimeAwayMin = null;
            } else {
              _tmpPredictedTimeAwayMin = _cursor.getFloat(_cursorIndexOfPredictedTimeAwayMin);
            }
            final Float _tmpPredictedRecoveryMin;
            if (_cursor.isNull(_cursorIndexOfPredictedRecoveryMin)) {
              _tmpPredictedRecoveryMin = null;
            } else {
              _tmpPredictedRecoveryMin = _cursor.getFloat(_cursorIndexOfPredictedRecoveryMin);
            }
            final String _tmpSessionOutcome;
            if (_cursor.isNull(_cursorIndexOfSessionOutcome)) {
              _tmpSessionOutcome = null;
            } else {
              _tmpSessionOutcome = _cursor.getString(_cursorIndexOfSessionOutcome);
            }
            _result = new UserAction(_tmpId,_tmpNotificationId,_tmpPredictionId,_tmpActionType,_tmpActionTimeMs,_tmpIsDemo,_tmpSessionId,_tmpSessionState,_tmpCategory,_tmpLaunchAttempted,_tmpLaunchResult,_tmpLaunchedAtMs,_tmpReturnedAtMs,_tmpPredictedTimeAwayMin,_tmpPredictedRecoveryMin,_tmpSessionOutcome);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object latestAwaitingReturn(final long sinceMs,
      final Continuation<? super UserAction> $completion) {
    final String _sql = "SELECT * FROM user_actions WHERE actionType='OPENED' AND launchedAtMs IS NOT NULL AND returnedAtMs IS NULL AND launchedAtMs>=? ORDER BY launchedAtMs DESC LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, sinceMs);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<UserAction>() {
      @Override
      @Nullable
      public UserAction call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfNotificationId = CursorUtil.getColumnIndexOrThrow(_cursor, "notificationId");
          final int _cursorIndexOfPredictionId = CursorUtil.getColumnIndexOrThrow(_cursor, "predictionId");
          final int _cursorIndexOfActionType = CursorUtil.getColumnIndexOrThrow(_cursor, "actionType");
          final int _cursorIndexOfActionTimeMs = CursorUtil.getColumnIndexOrThrow(_cursor, "actionTimeMs");
          final int _cursorIndexOfIsDemo = CursorUtil.getColumnIndexOrThrow(_cursor, "isDemo");
          final int _cursorIndexOfSessionId = CursorUtil.getColumnIndexOrThrow(_cursor, "sessionId");
          final int _cursorIndexOfSessionState = CursorUtil.getColumnIndexOrThrow(_cursor, "sessionState");
          final int _cursorIndexOfCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "category");
          final int _cursorIndexOfLaunchAttempted = CursorUtil.getColumnIndexOrThrow(_cursor, "launchAttempted");
          final int _cursorIndexOfLaunchResult = CursorUtil.getColumnIndexOrThrow(_cursor, "launchResult");
          final int _cursorIndexOfLaunchedAtMs = CursorUtil.getColumnIndexOrThrow(_cursor, "launchedAtMs");
          final int _cursorIndexOfReturnedAtMs = CursorUtil.getColumnIndexOrThrow(_cursor, "returnedAtMs");
          final int _cursorIndexOfPredictedTimeAwayMin = CursorUtil.getColumnIndexOrThrow(_cursor, "predictedTimeAwayMin");
          final int _cursorIndexOfPredictedRecoveryMin = CursorUtil.getColumnIndexOrThrow(_cursor, "predictedRecoveryMin");
          final int _cursorIndexOfSessionOutcome = CursorUtil.getColumnIndexOrThrow(_cursor, "sessionOutcome");
          final UserAction _result;
          if (_cursor.moveToFirst()) {
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpNotificationId;
            _tmpNotificationId = _cursor.getString(_cursorIndexOfNotificationId);
            final String _tmpPredictionId;
            if (_cursor.isNull(_cursorIndexOfPredictionId)) {
              _tmpPredictionId = null;
            } else {
              _tmpPredictionId = _cursor.getString(_cursorIndexOfPredictionId);
            }
            final ActionType _tmpActionType;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfActionType);
            _tmpActionType = __converters.toAT(_tmp);
            final long _tmpActionTimeMs;
            _tmpActionTimeMs = _cursor.getLong(_cursorIndexOfActionTimeMs);
            final boolean _tmpIsDemo;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsDemo);
            _tmpIsDemo = _tmp_1 != 0;
            final String _tmpSessionId;
            if (_cursor.isNull(_cursorIndexOfSessionId)) {
              _tmpSessionId = null;
            } else {
              _tmpSessionId = _cursor.getString(_cursorIndexOfSessionId);
            }
            final String _tmpSessionState;
            if (_cursor.isNull(_cursorIndexOfSessionState)) {
              _tmpSessionState = null;
            } else {
              _tmpSessionState = _cursor.getString(_cursorIndexOfSessionState);
            }
            final String _tmpCategory;
            if (_cursor.isNull(_cursorIndexOfCategory)) {
              _tmpCategory = null;
            } else {
              _tmpCategory = _cursor.getString(_cursorIndexOfCategory);
            }
            final boolean _tmpLaunchAttempted;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfLaunchAttempted);
            _tmpLaunchAttempted = _tmp_2 != 0;
            final String _tmpLaunchResult;
            if (_cursor.isNull(_cursorIndexOfLaunchResult)) {
              _tmpLaunchResult = null;
            } else {
              _tmpLaunchResult = _cursor.getString(_cursorIndexOfLaunchResult);
            }
            final Long _tmpLaunchedAtMs;
            if (_cursor.isNull(_cursorIndexOfLaunchedAtMs)) {
              _tmpLaunchedAtMs = null;
            } else {
              _tmpLaunchedAtMs = _cursor.getLong(_cursorIndexOfLaunchedAtMs);
            }
            final Long _tmpReturnedAtMs;
            if (_cursor.isNull(_cursorIndexOfReturnedAtMs)) {
              _tmpReturnedAtMs = null;
            } else {
              _tmpReturnedAtMs = _cursor.getLong(_cursorIndexOfReturnedAtMs);
            }
            final Float _tmpPredictedTimeAwayMin;
            if (_cursor.isNull(_cursorIndexOfPredictedTimeAwayMin)) {
              _tmpPredictedTimeAwayMin = null;
            } else {
              _tmpPredictedTimeAwayMin = _cursor.getFloat(_cursorIndexOfPredictedTimeAwayMin);
            }
            final Float _tmpPredictedRecoveryMin;
            if (_cursor.isNull(_cursorIndexOfPredictedRecoveryMin)) {
              _tmpPredictedRecoveryMin = null;
            } else {
              _tmpPredictedRecoveryMin = _cursor.getFloat(_cursorIndexOfPredictedRecoveryMin);
            }
            final String _tmpSessionOutcome;
            if (_cursor.isNull(_cursorIndexOfSessionOutcome)) {
              _tmpSessionOutcome = null;
            } else {
              _tmpSessionOutcome = _cursor.getString(_cursorIndexOfSessionOutcome);
            }
            _result = new UserAction(_tmpId,_tmpNotificationId,_tmpPredictionId,_tmpActionType,_tmpActionTimeMs,_tmpIsDemo,_tmpSessionId,_tmpSessionState,_tmpCategory,_tmpLaunchAttempted,_tmpLaunchResult,_tmpLaunchedAtMs,_tmpReturnedAtMs,_tmpPredictedTimeAwayMin,_tmpPredictedRecoveryMin,_tmpSessionOutcome);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
