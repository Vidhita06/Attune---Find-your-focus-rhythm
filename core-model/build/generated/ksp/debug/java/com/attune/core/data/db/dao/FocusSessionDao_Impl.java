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
import com.attune.core.model.FocusSession;
import com.attune.core.model.SessionState;
import java.lang.Class;
import java.lang.Exception;
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
public final class FocusSessionDao_Impl implements FocusSessionDao {
  private final RoomDatabase __db;

  private final SharedSQLiteStatement __preparedStmtOfDeleteDemo;

  private final SharedSQLiteStatement __preparedStmtOfDeleteRealFinished;

  private final EntityUpsertionAdapter<FocusSession> __upsertionAdapterOfFocusSession;

  private final Converters __converters = new Converters();

  public FocusSessionDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__preparedStmtOfDeleteDemo = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM focus_sessions WHERE isDemo=1";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteRealFinished = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM focus_sessions WHERE isDemo=0 AND state NOT IN ('FOCUS','PAUSED','BREAK')";
        return _query;
      }
    };
    this.__upsertionAdapterOfFocusSession = new EntityUpsertionAdapter<FocusSession>(new EntityInsertionAdapter<FocusSession>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT INTO `focus_sessions` (`id`,`sessionStartMs`,`plannedEndMs`,`actualEndMs`,`state`,`breakStartMs`,`isDemo`,`createdAt`,`plannedDurationMin`,`totalPausedMs`,`pauseCount`,`interruptionCount`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final FocusSession entity) {
        statement.bindString(1, entity.getId());
        statement.bindLong(2, entity.getSessionStartMs());
        statement.bindLong(3, entity.getPlannedEndMs());
        if (entity.getActualEndMs() == null) {
          statement.bindNull(4);
        } else {
          statement.bindLong(4, entity.getActualEndMs());
        }
        final String _tmp = __converters.fromSS(entity.getState());
        statement.bindString(5, _tmp);
        if (entity.getBreakStartMs() == null) {
          statement.bindNull(6);
        } else {
          statement.bindLong(6, entity.getBreakStartMs());
        }
        final int _tmp_1 = entity.isDemo() ? 1 : 0;
        statement.bindLong(7, _tmp_1);
        statement.bindLong(8, entity.getCreatedAt());
        statement.bindLong(9, entity.getPlannedDurationMin());
        statement.bindLong(10, entity.getTotalPausedMs());
        statement.bindLong(11, entity.getPauseCount());
        statement.bindLong(12, entity.getInterruptionCount());
      }
    }, new EntityDeletionOrUpdateAdapter<FocusSession>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE `focus_sessions` SET `id` = ?,`sessionStartMs` = ?,`plannedEndMs` = ?,`actualEndMs` = ?,`state` = ?,`breakStartMs` = ?,`isDemo` = ?,`createdAt` = ?,`plannedDurationMin` = ?,`totalPausedMs` = ?,`pauseCount` = ?,`interruptionCount` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final FocusSession entity) {
        statement.bindString(1, entity.getId());
        statement.bindLong(2, entity.getSessionStartMs());
        statement.bindLong(3, entity.getPlannedEndMs());
        if (entity.getActualEndMs() == null) {
          statement.bindNull(4);
        } else {
          statement.bindLong(4, entity.getActualEndMs());
        }
        final String _tmp = __converters.fromSS(entity.getState());
        statement.bindString(5, _tmp);
        if (entity.getBreakStartMs() == null) {
          statement.bindNull(6);
        } else {
          statement.bindLong(6, entity.getBreakStartMs());
        }
        final int _tmp_1 = entity.isDemo() ? 1 : 0;
        statement.bindLong(7, _tmp_1);
        statement.bindLong(8, entity.getCreatedAt());
        statement.bindLong(9, entity.getPlannedDurationMin());
        statement.bindLong(10, entity.getTotalPausedMs());
        statement.bindLong(11, entity.getPauseCount());
        statement.bindLong(12, entity.getInterruptionCount());
        statement.bindString(13, entity.getId());
      }
    });
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
  public Object deleteRealFinished(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteRealFinished.acquire();
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
          __preparedStmtOfDeleteRealFinished.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object upsert(final FocusSession s, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __upsertionAdapterOfFocusSession.upsert(s);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<FocusSession> observeLatest() {
    final String _sql = "SELECT * FROM focus_sessions ORDER BY sessionStartMs DESC LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"focus_sessions"}, new Callable<FocusSession>() {
      @Override
      @Nullable
      public FocusSession call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSessionStartMs = CursorUtil.getColumnIndexOrThrow(_cursor, "sessionStartMs");
          final int _cursorIndexOfPlannedEndMs = CursorUtil.getColumnIndexOrThrow(_cursor, "plannedEndMs");
          final int _cursorIndexOfActualEndMs = CursorUtil.getColumnIndexOrThrow(_cursor, "actualEndMs");
          final int _cursorIndexOfState = CursorUtil.getColumnIndexOrThrow(_cursor, "state");
          final int _cursorIndexOfBreakStartMs = CursorUtil.getColumnIndexOrThrow(_cursor, "breakStartMs");
          final int _cursorIndexOfIsDemo = CursorUtil.getColumnIndexOrThrow(_cursor, "isDemo");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfPlannedDurationMin = CursorUtil.getColumnIndexOrThrow(_cursor, "plannedDurationMin");
          final int _cursorIndexOfTotalPausedMs = CursorUtil.getColumnIndexOrThrow(_cursor, "totalPausedMs");
          final int _cursorIndexOfPauseCount = CursorUtil.getColumnIndexOrThrow(_cursor, "pauseCount");
          final int _cursorIndexOfInterruptionCount = CursorUtil.getColumnIndexOrThrow(_cursor, "interruptionCount");
          final FocusSession _result;
          if (_cursor.moveToFirst()) {
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final long _tmpSessionStartMs;
            _tmpSessionStartMs = _cursor.getLong(_cursorIndexOfSessionStartMs);
            final long _tmpPlannedEndMs;
            _tmpPlannedEndMs = _cursor.getLong(_cursorIndexOfPlannedEndMs);
            final Long _tmpActualEndMs;
            if (_cursor.isNull(_cursorIndexOfActualEndMs)) {
              _tmpActualEndMs = null;
            } else {
              _tmpActualEndMs = _cursor.getLong(_cursorIndexOfActualEndMs);
            }
            final SessionState _tmpState;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfState);
            _tmpState = __converters.toSS(_tmp);
            final Long _tmpBreakStartMs;
            if (_cursor.isNull(_cursorIndexOfBreakStartMs)) {
              _tmpBreakStartMs = null;
            } else {
              _tmpBreakStartMs = _cursor.getLong(_cursorIndexOfBreakStartMs);
            }
            final boolean _tmpIsDemo;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsDemo);
            _tmpIsDemo = _tmp_1 != 0;
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final int _tmpPlannedDurationMin;
            _tmpPlannedDurationMin = _cursor.getInt(_cursorIndexOfPlannedDurationMin);
            final long _tmpTotalPausedMs;
            _tmpTotalPausedMs = _cursor.getLong(_cursorIndexOfTotalPausedMs);
            final int _tmpPauseCount;
            _tmpPauseCount = _cursor.getInt(_cursorIndexOfPauseCount);
            final int _tmpInterruptionCount;
            _tmpInterruptionCount = _cursor.getInt(_cursorIndexOfInterruptionCount);
            _result = new FocusSession(_tmpId,_tmpSessionStartMs,_tmpPlannedEndMs,_tmpActualEndMs,_tmpState,_tmpBreakStartMs,_tmpIsDemo,_tmpCreatedAt,_tmpPlannedDurationMin,_tmpTotalPausedMs,_tmpPauseCount,_tmpInterruptionCount);
          } else {
            _result = null;
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
  public Object getLatest(final Continuation<? super FocusSession> $completion) {
    final String _sql = "SELECT * FROM focus_sessions ORDER BY sessionStartMs DESC LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<FocusSession>() {
      @Override
      @Nullable
      public FocusSession call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSessionStartMs = CursorUtil.getColumnIndexOrThrow(_cursor, "sessionStartMs");
          final int _cursorIndexOfPlannedEndMs = CursorUtil.getColumnIndexOrThrow(_cursor, "plannedEndMs");
          final int _cursorIndexOfActualEndMs = CursorUtil.getColumnIndexOrThrow(_cursor, "actualEndMs");
          final int _cursorIndexOfState = CursorUtil.getColumnIndexOrThrow(_cursor, "state");
          final int _cursorIndexOfBreakStartMs = CursorUtil.getColumnIndexOrThrow(_cursor, "breakStartMs");
          final int _cursorIndexOfIsDemo = CursorUtil.getColumnIndexOrThrow(_cursor, "isDemo");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfPlannedDurationMin = CursorUtil.getColumnIndexOrThrow(_cursor, "plannedDurationMin");
          final int _cursorIndexOfTotalPausedMs = CursorUtil.getColumnIndexOrThrow(_cursor, "totalPausedMs");
          final int _cursorIndexOfPauseCount = CursorUtil.getColumnIndexOrThrow(_cursor, "pauseCount");
          final int _cursorIndexOfInterruptionCount = CursorUtil.getColumnIndexOrThrow(_cursor, "interruptionCount");
          final FocusSession _result;
          if (_cursor.moveToFirst()) {
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final long _tmpSessionStartMs;
            _tmpSessionStartMs = _cursor.getLong(_cursorIndexOfSessionStartMs);
            final long _tmpPlannedEndMs;
            _tmpPlannedEndMs = _cursor.getLong(_cursorIndexOfPlannedEndMs);
            final Long _tmpActualEndMs;
            if (_cursor.isNull(_cursorIndexOfActualEndMs)) {
              _tmpActualEndMs = null;
            } else {
              _tmpActualEndMs = _cursor.getLong(_cursorIndexOfActualEndMs);
            }
            final SessionState _tmpState;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfState);
            _tmpState = __converters.toSS(_tmp);
            final Long _tmpBreakStartMs;
            if (_cursor.isNull(_cursorIndexOfBreakStartMs)) {
              _tmpBreakStartMs = null;
            } else {
              _tmpBreakStartMs = _cursor.getLong(_cursorIndexOfBreakStartMs);
            }
            final boolean _tmpIsDemo;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsDemo);
            _tmpIsDemo = _tmp_1 != 0;
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final int _tmpPlannedDurationMin;
            _tmpPlannedDurationMin = _cursor.getInt(_cursorIndexOfPlannedDurationMin);
            final long _tmpTotalPausedMs;
            _tmpTotalPausedMs = _cursor.getLong(_cursorIndexOfTotalPausedMs);
            final int _tmpPauseCount;
            _tmpPauseCount = _cursor.getInt(_cursorIndexOfPauseCount);
            final int _tmpInterruptionCount;
            _tmpInterruptionCount = _cursor.getInt(_cursorIndexOfInterruptionCount);
            _result = new FocusSession(_tmpId,_tmpSessionStartMs,_tmpPlannedEndMs,_tmpActualEndMs,_tmpState,_tmpBreakStartMs,_tmpIsDemo,_tmpCreatedAt,_tmpPlannedDurationMin,_tmpTotalPausedMs,_tmpPauseCount,_tmpInterruptionCount);
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
  public Flow<FocusSession> observeLatestReal() {
    final String _sql = "SELECT * FROM focus_sessions WHERE isDemo=0 ORDER BY sessionStartMs DESC LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"focus_sessions"}, new Callable<FocusSession>() {
      @Override
      @Nullable
      public FocusSession call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSessionStartMs = CursorUtil.getColumnIndexOrThrow(_cursor, "sessionStartMs");
          final int _cursorIndexOfPlannedEndMs = CursorUtil.getColumnIndexOrThrow(_cursor, "plannedEndMs");
          final int _cursorIndexOfActualEndMs = CursorUtil.getColumnIndexOrThrow(_cursor, "actualEndMs");
          final int _cursorIndexOfState = CursorUtil.getColumnIndexOrThrow(_cursor, "state");
          final int _cursorIndexOfBreakStartMs = CursorUtil.getColumnIndexOrThrow(_cursor, "breakStartMs");
          final int _cursorIndexOfIsDemo = CursorUtil.getColumnIndexOrThrow(_cursor, "isDemo");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfPlannedDurationMin = CursorUtil.getColumnIndexOrThrow(_cursor, "plannedDurationMin");
          final int _cursorIndexOfTotalPausedMs = CursorUtil.getColumnIndexOrThrow(_cursor, "totalPausedMs");
          final int _cursorIndexOfPauseCount = CursorUtil.getColumnIndexOrThrow(_cursor, "pauseCount");
          final int _cursorIndexOfInterruptionCount = CursorUtil.getColumnIndexOrThrow(_cursor, "interruptionCount");
          final FocusSession _result;
          if (_cursor.moveToFirst()) {
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final long _tmpSessionStartMs;
            _tmpSessionStartMs = _cursor.getLong(_cursorIndexOfSessionStartMs);
            final long _tmpPlannedEndMs;
            _tmpPlannedEndMs = _cursor.getLong(_cursorIndexOfPlannedEndMs);
            final Long _tmpActualEndMs;
            if (_cursor.isNull(_cursorIndexOfActualEndMs)) {
              _tmpActualEndMs = null;
            } else {
              _tmpActualEndMs = _cursor.getLong(_cursorIndexOfActualEndMs);
            }
            final SessionState _tmpState;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfState);
            _tmpState = __converters.toSS(_tmp);
            final Long _tmpBreakStartMs;
            if (_cursor.isNull(_cursorIndexOfBreakStartMs)) {
              _tmpBreakStartMs = null;
            } else {
              _tmpBreakStartMs = _cursor.getLong(_cursorIndexOfBreakStartMs);
            }
            final boolean _tmpIsDemo;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsDemo);
            _tmpIsDemo = _tmp_1 != 0;
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final int _tmpPlannedDurationMin;
            _tmpPlannedDurationMin = _cursor.getInt(_cursorIndexOfPlannedDurationMin);
            final long _tmpTotalPausedMs;
            _tmpTotalPausedMs = _cursor.getLong(_cursorIndexOfTotalPausedMs);
            final int _tmpPauseCount;
            _tmpPauseCount = _cursor.getInt(_cursorIndexOfPauseCount);
            final int _tmpInterruptionCount;
            _tmpInterruptionCount = _cursor.getInt(_cursorIndexOfInterruptionCount);
            _result = new FocusSession(_tmpId,_tmpSessionStartMs,_tmpPlannedEndMs,_tmpActualEndMs,_tmpState,_tmpBreakStartMs,_tmpIsDemo,_tmpCreatedAt,_tmpPlannedDurationMin,_tmpTotalPausedMs,_tmpPauseCount,_tmpInterruptionCount);
          } else {
            _result = null;
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
  public Flow<List<FocusSession>> observeAllIncDemo() {
    final String _sql = "SELECT * FROM focus_sessions ORDER BY sessionStartMs DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"focus_sessions"}, new Callable<List<FocusSession>>() {
      @Override
      @NonNull
      public List<FocusSession> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSessionStartMs = CursorUtil.getColumnIndexOrThrow(_cursor, "sessionStartMs");
          final int _cursorIndexOfPlannedEndMs = CursorUtil.getColumnIndexOrThrow(_cursor, "plannedEndMs");
          final int _cursorIndexOfActualEndMs = CursorUtil.getColumnIndexOrThrow(_cursor, "actualEndMs");
          final int _cursorIndexOfState = CursorUtil.getColumnIndexOrThrow(_cursor, "state");
          final int _cursorIndexOfBreakStartMs = CursorUtil.getColumnIndexOrThrow(_cursor, "breakStartMs");
          final int _cursorIndexOfIsDemo = CursorUtil.getColumnIndexOrThrow(_cursor, "isDemo");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfPlannedDurationMin = CursorUtil.getColumnIndexOrThrow(_cursor, "plannedDurationMin");
          final int _cursorIndexOfTotalPausedMs = CursorUtil.getColumnIndexOrThrow(_cursor, "totalPausedMs");
          final int _cursorIndexOfPauseCount = CursorUtil.getColumnIndexOrThrow(_cursor, "pauseCount");
          final int _cursorIndexOfInterruptionCount = CursorUtil.getColumnIndexOrThrow(_cursor, "interruptionCount");
          final List<FocusSession> _result = new ArrayList<FocusSession>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final FocusSession _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final long _tmpSessionStartMs;
            _tmpSessionStartMs = _cursor.getLong(_cursorIndexOfSessionStartMs);
            final long _tmpPlannedEndMs;
            _tmpPlannedEndMs = _cursor.getLong(_cursorIndexOfPlannedEndMs);
            final Long _tmpActualEndMs;
            if (_cursor.isNull(_cursorIndexOfActualEndMs)) {
              _tmpActualEndMs = null;
            } else {
              _tmpActualEndMs = _cursor.getLong(_cursorIndexOfActualEndMs);
            }
            final SessionState _tmpState;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfState);
            _tmpState = __converters.toSS(_tmp);
            final Long _tmpBreakStartMs;
            if (_cursor.isNull(_cursorIndexOfBreakStartMs)) {
              _tmpBreakStartMs = null;
            } else {
              _tmpBreakStartMs = _cursor.getLong(_cursorIndexOfBreakStartMs);
            }
            final boolean _tmpIsDemo;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsDemo);
            _tmpIsDemo = _tmp_1 != 0;
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final int _tmpPlannedDurationMin;
            _tmpPlannedDurationMin = _cursor.getInt(_cursorIndexOfPlannedDurationMin);
            final long _tmpTotalPausedMs;
            _tmpTotalPausedMs = _cursor.getLong(_cursorIndexOfTotalPausedMs);
            final int _tmpPauseCount;
            _tmpPauseCount = _cursor.getInt(_cursorIndexOfPauseCount);
            final int _tmpInterruptionCount;
            _tmpInterruptionCount = _cursor.getInt(_cursorIndexOfInterruptionCount);
            _item = new FocusSession(_tmpId,_tmpSessionStartMs,_tmpPlannedEndMs,_tmpActualEndMs,_tmpState,_tmpBreakStartMs,_tmpIsDemo,_tmpCreatedAt,_tmpPlannedDurationMin,_tmpTotalPausedMs,_tmpPauseCount,_tmpInterruptionCount);
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
  public Flow<FocusSession> observeActive() {
    final String _sql = "SELECT * FROM focus_sessions WHERE isDemo=0 AND state IN ('FOCUS','PAUSED','BREAK') ORDER BY sessionStartMs DESC LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"focus_sessions"}, new Callable<FocusSession>() {
      @Override
      @Nullable
      public FocusSession call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSessionStartMs = CursorUtil.getColumnIndexOrThrow(_cursor, "sessionStartMs");
          final int _cursorIndexOfPlannedEndMs = CursorUtil.getColumnIndexOrThrow(_cursor, "plannedEndMs");
          final int _cursorIndexOfActualEndMs = CursorUtil.getColumnIndexOrThrow(_cursor, "actualEndMs");
          final int _cursorIndexOfState = CursorUtil.getColumnIndexOrThrow(_cursor, "state");
          final int _cursorIndexOfBreakStartMs = CursorUtil.getColumnIndexOrThrow(_cursor, "breakStartMs");
          final int _cursorIndexOfIsDemo = CursorUtil.getColumnIndexOrThrow(_cursor, "isDemo");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfPlannedDurationMin = CursorUtil.getColumnIndexOrThrow(_cursor, "plannedDurationMin");
          final int _cursorIndexOfTotalPausedMs = CursorUtil.getColumnIndexOrThrow(_cursor, "totalPausedMs");
          final int _cursorIndexOfPauseCount = CursorUtil.getColumnIndexOrThrow(_cursor, "pauseCount");
          final int _cursorIndexOfInterruptionCount = CursorUtil.getColumnIndexOrThrow(_cursor, "interruptionCount");
          final FocusSession _result;
          if (_cursor.moveToFirst()) {
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final long _tmpSessionStartMs;
            _tmpSessionStartMs = _cursor.getLong(_cursorIndexOfSessionStartMs);
            final long _tmpPlannedEndMs;
            _tmpPlannedEndMs = _cursor.getLong(_cursorIndexOfPlannedEndMs);
            final Long _tmpActualEndMs;
            if (_cursor.isNull(_cursorIndexOfActualEndMs)) {
              _tmpActualEndMs = null;
            } else {
              _tmpActualEndMs = _cursor.getLong(_cursorIndexOfActualEndMs);
            }
            final SessionState _tmpState;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfState);
            _tmpState = __converters.toSS(_tmp);
            final Long _tmpBreakStartMs;
            if (_cursor.isNull(_cursorIndexOfBreakStartMs)) {
              _tmpBreakStartMs = null;
            } else {
              _tmpBreakStartMs = _cursor.getLong(_cursorIndexOfBreakStartMs);
            }
            final boolean _tmpIsDemo;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsDemo);
            _tmpIsDemo = _tmp_1 != 0;
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final int _tmpPlannedDurationMin;
            _tmpPlannedDurationMin = _cursor.getInt(_cursorIndexOfPlannedDurationMin);
            final long _tmpTotalPausedMs;
            _tmpTotalPausedMs = _cursor.getLong(_cursorIndexOfTotalPausedMs);
            final int _tmpPauseCount;
            _tmpPauseCount = _cursor.getInt(_cursorIndexOfPauseCount);
            final int _tmpInterruptionCount;
            _tmpInterruptionCount = _cursor.getInt(_cursorIndexOfInterruptionCount);
            _result = new FocusSession(_tmpId,_tmpSessionStartMs,_tmpPlannedEndMs,_tmpActualEndMs,_tmpState,_tmpBreakStartMs,_tmpIsDemo,_tmpCreatedAt,_tmpPlannedDurationMin,_tmpTotalPausedMs,_tmpPauseCount,_tmpInterruptionCount);
          } else {
            _result = null;
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
  public Object getAllActive(final Continuation<? super List<FocusSession>> $completion) {
    final String _sql = "SELECT * FROM focus_sessions WHERE isDemo=0 AND state IN ('FOCUS','PAUSED','BREAK') ORDER BY sessionStartMs DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<FocusSession>>() {
      @Override
      @NonNull
      public List<FocusSession> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSessionStartMs = CursorUtil.getColumnIndexOrThrow(_cursor, "sessionStartMs");
          final int _cursorIndexOfPlannedEndMs = CursorUtil.getColumnIndexOrThrow(_cursor, "plannedEndMs");
          final int _cursorIndexOfActualEndMs = CursorUtil.getColumnIndexOrThrow(_cursor, "actualEndMs");
          final int _cursorIndexOfState = CursorUtil.getColumnIndexOrThrow(_cursor, "state");
          final int _cursorIndexOfBreakStartMs = CursorUtil.getColumnIndexOrThrow(_cursor, "breakStartMs");
          final int _cursorIndexOfIsDemo = CursorUtil.getColumnIndexOrThrow(_cursor, "isDemo");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfPlannedDurationMin = CursorUtil.getColumnIndexOrThrow(_cursor, "plannedDurationMin");
          final int _cursorIndexOfTotalPausedMs = CursorUtil.getColumnIndexOrThrow(_cursor, "totalPausedMs");
          final int _cursorIndexOfPauseCount = CursorUtil.getColumnIndexOrThrow(_cursor, "pauseCount");
          final int _cursorIndexOfInterruptionCount = CursorUtil.getColumnIndexOrThrow(_cursor, "interruptionCount");
          final List<FocusSession> _result = new ArrayList<FocusSession>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final FocusSession _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final long _tmpSessionStartMs;
            _tmpSessionStartMs = _cursor.getLong(_cursorIndexOfSessionStartMs);
            final long _tmpPlannedEndMs;
            _tmpPlannedEndMs = _cursor.getLong(_cursorIndexOfPlannedEndMs);
            final Long _tmpActualEndMs;
            if (_cursor.isNull(_cursorIndexOfActualEndMs)) {
              _tmpActualEndMs = null;
            } else {
              _tmpActualEndMs = _cursor.getLong(_cursorIndexOfActualEndMs);
            }
            final SessionState _tmpState;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfState);
            _tmpState = __converters.toSS(_tmp);
            final Long _tmpBreakStartMs;
            if (_cursor.isNull(_cursorIndexOfBreakStartMs)) {
              _tmpBreakStartMs = null;
            } else {
              _tmpBreakStartMs = _cursor.getLong(_cursorIndexOfBreakStartMs);
            }
            final boolean _tmpIsDemo;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsDemo);
            _tmpIsDemo = _tmp_1 != 0;
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final int _tmpPlannedDurationMin;
            _tmpPlannedDurationMin = _cursor.getInt(_cursorIndexOfPlannedDurationMin);
            final long _tmpTotalPausedMs;
            _tmpTotalPausedMs = _cursor.getLong(_cursorIndexOfTotalPausedMs);
            final int _tmpPauseCount;
            _tmpPauseCount = _cursor.getInt(_cursorIndexOfPauseCount);
            final int _tmpInterruptionCount;
            _tmpInterruptionCount = _cursor.getInt(_cursorIndexOfInterruptionCount);
            _item = new FocusSession(_tmpId,_tmpSessionStartMs,_tmpPlannedEndMs,_tmpActualEndMs,_tmpState,_tmpBreakStartMs,_tmpIsDemo,_tmpCreatedAt,_tmpPlannedDurationMin,_tmpTotalPausedMs,_tmpPauseCount,_tmpInterruptionCount);
            _result.add(_item);
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
  public Object getById(final String id, final Continuation<? super FocusSession> $completion) {
    final String _sql = "SELECT * FROM focus_sessions WHERE id=?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<FocusSession>() {
      @Override
      @Nullable
      public FocusSession call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSessionStartMs = CursorUtil.getColumnIndexOrThrow(_cursor, "sessionStartMs");
          final int _cursorIndexOfPlannedEndMs = CursorUtil.getColumnIndexOrThrow(_cursor, "plannedEndMs");
          final int _cursorIndexOfActualEndMs = CursorUtil.getColumnIndexOrThrow(_cursor, "actualEndMs");
          final int _cursorIndexOfState = CursorUtil.getColumnIndexOrThrow(_cursor, "state");
          final int _cursorIndexOfBreakStartMs = CursorUtil.getColumnIndexOrThrow(_cursor, "breakStartMs");
          final int _cursorIndexOfIsDemo = CursorUtil.getColumnIndexOrThrow(_cursor, "isDemo");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfPlannedDurationMin = CursorUtil.getColumnIndexOrThrow(_cursor, "plannedDurationMin");
          final int _cursorIndexOfTotalPausedMs = CursorUtil.getColumnIndexOrThrow(_cursor, "totalPausedMs");
          final int _cursorIndexOfPauseCount = CursorUtil.getColumnIndexOrThrow(_cursor, "pauseCount");
          final int _cursorIndexOfInterruptionCount = CursorUtil.getColumnIndexOrThrow(_cursor, "interruptionCount");
          final FocusSession _result;
          if (_cursor.moveToFirst()) {
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final long _tmpSessionStartMs;
            _tmpSessionStartMs = _cursor.getLong(_cursorIndexOfSessionStartMs);
            final long _tmpPlannedEndMs;
            _tmpPlannedEndMs = _cursor.getLong(_cursorIndexOfPlannedEndMs);
            final Long _tmpActualEndMs;
            if (_cursor.isNull(_cursorIndexOfActualEndMs)) {
              _tmpActualEndMs = null;
            } else {
              _tmpActualEndMs = _cursor.getLong(_cursorIndexOfActualEndMs);
            }
            final SessionState _tmpState;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfState);
            _tmpState = __converters.toSS(_tmp);
            final Long _tmpBreakStartMs;
            if (_cursor.isNull(_cursorIndexOfBreakStartMs)) {
              _tmpBreakStartMs = null;
            } else {
              _tmpBreakStartMs = _cursor.getLong(_cursorIndexOfBreakStartMs);
            }
            final boolean _tmpIsDemo;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsDemo);
            _tmpIsDemo = _tmp_1 != 0;
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final int _tmpPlannedDurationMin;
            _tmpPlannedDurationMin = _cursor.getInt(_cursorIndexOfPlannedDurationMin);
            final long _tmpTotalPausedMs;
            _tmpTotalPausedMs = _cursor.getLong(_cursorIndexOfTotalPausedMs);
            final int _tmpPauseCount;
            _tmpPauseCount = _cursor.getInt(_cursorIndexOfPauseCount);
            final int _tmpInterruptionCount;
            _tmpInterruptionCount = _cursor.getInt(_cursorIndexOfInterruptionCount);
            _result = new FocusSession(_tmpId,_tmpSessionStartMs,_tmpPlannedEndMs,_tmpActualEndMs,_tmpState,_tmpBreakStartMs,_tmpIsDemo,_tmpCreatedAt,_tmpPlannedDurationMin,_tmpTotalPausedMs,_tmpPauseCount,_tmpInterruptionCount);
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
