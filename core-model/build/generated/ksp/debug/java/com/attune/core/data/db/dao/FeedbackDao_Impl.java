package com.attune.core.data.db.dao;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
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
import com.attune.core.model.CategoryObservation;
import com.attune.core.model.Feedback;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Float;
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
public final class FeedbackDao_Impl implements FeedbackDao {
  private final RoomDatabase __db;

  private final SharedSQLiteStatement __preparedStmtOfDeleteDemo;

  private final SharedSQLiteStatement __preparedStmtOfDeleteReal;

  private final EntityUpsertionAdapter<Feedback> __upsertionAdapterOfFeedback;

  public FeedbackDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__preparedStmtOfDeleteDemo = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM feedback WHERE isDemo=1";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteReal = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM feedback WHERE isDemo=0";
        return _query;
      }
    };
    this.__upsertionAdapterOfFeedback = new EntityUpsertionAdapter<Feedback>(new EntityInsertionAdapter<Feedback>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT INTO `feedback` (`id`,`notificationId`,`actionId`,`reportedTimeAwayMin`,`reportedRecoveryMin`,`wasSkipped`,`isDemo`,`createdAt`) VALUES (?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Feedback entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getNotificationId());
        statement.bindString(3, entity.getActionId());
        if (entity.getReportedTimeAwayMin() == null) {
          statement.bindNull(4);
        } else {
          statement.bindDouble(4, entity.getReportedTimeAwayMin());
        }
        if (entity.getReportedRecoveryMin() == null) {
          statement.bindNull(5);
        } else {
          statement.bindDouble(5, entity.getReportedRecoveryMin());
        }
        final int _tmp = entity.getWasSkipped() ? 1 : 0;
        statement.bindLong(6, _tmp);
        final int _tmp_1 = entity.isDemo() ? 1 : 0;
        statement.bindLong(7, _tmp_1);
        statement.bindLong(8, entity.getCreatedAt());
      }
    }, new EntityDeletionOrUpdateAdapter<Feedback>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE `feedback` SET `id` = ?,`notificationId` = ?,`actionId` = ?,`reportedTimeAwayMin` = ?,`reportedRecoveryMin` = ?,`wasSkipped` = ?,`isDemo` = ?,`createdAt` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Feedback entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getNotificationId());
        statement.bindString(3, entity.getActionId());
        if (entity.getReportedTimeAwayMin() == null) {
          statement.bindNull(4);
        } else {
          statement.bindDouble(4, entity.getReportedTimeAwayMin());
        }
        if (entity.getReportedRecoveryMin() == null) {
          statement.bindNull(5);
        } else {
          statement.bindDouble(5, entity.getReportedRecoveryMin());
        }
        final int _tmp = entity.getWasSkipped() ? 1 : 0;
        statement.bindLong(6, _tmp);
        final int _tmp_1 = entity.isDemo() ? 1 : 0;
        statement.bindLong(7, _tmp_1);
        statement.bindLong(8, entity.getCreatedAt());
        statement.bindString(9, entity.getId());
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
  public Object upsert(final Feedback f, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __upsertionAdapterOfFeedback.upsert(f);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object getAllWithTimeAway(final Continuation<? super List<Feedback>> $completion) {
    final String _sql = "SELECT * FROM feedback WHERE isDemo=0 AND reportedTimeAwayMin IS NOT NULL";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<Feedback>>() {
      @Override
      @NonNull
      public List<Feedback> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfNotificationId = CursorUtil.getColumnIndexOrThrow(_cursor, "notificationId");
          final int _cursorIndexOfActionId = CursorUtil.getColumnIndexOrThrow(_cursor, "actionId");
          final int _cursorIndexOfReportedTimeAwayMin = CursorUtil.getColumnIndexOrThrow(_cursor, "reportedTimeAwayMin");
          final int _cursorIndexOfReportedRecoveryMin = CursorUtil.getColumnIndexOrThrow(_cursor, "reportedRecoveryMin");
          final int _cursorIndexOfWasSkipped = CursorUtil.getColumnIndexOrThrow(_cursor, "wasSkipped");
          final int _cursorIndexOfIsDemo = CursorUtil.getColumnIndexOrThrow(_cursor, "isDemo");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final List<Feedback> _result = new ArrayList<Feedback>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Feedback _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpNotificationId;
            _tmpNotificationId = _cursor.getString(_cursorIndexOfNotificationId);
            final String _tmpActionId;
            _tmpActionId = _cursor.getString(_cursorIndexOfActionId);
            final Float _tmpReportedTimeAwayMin;
            if (_cursor.isNull(_cursorIndexOfReportedTimeAwayMin)) {
              _tmpReportedTimeAwayMin = null;
            } else {
              _tmpReportedTimeAwayMin = _cursor.getFloat(_cursorIndexOfReportedTimeAwayMin);
            }
            final Float _tmpReportedRecoveryMin;
            if (_cursor.isNull(_cursorIndexOfReportedRecoveryMin)) {
              _tmpReportedRecoveryMin = null;
            } else {
              _tmpReportedRecoveryMin = _cursor.getFloat(_cursorIndexOfReportedRecoveryMin);
            }
            final boolean _tmpWasSkipped;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfWasSkipped);
            _tmpWasSkipped = _tmp != 0;
            final boolean _tmpIsDemo;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsDemo);
            _tmpIsDemo = _tmp_1 != 0;
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            _item = new Feedback(_tmpId,_tmpNotificationId,_tmpActionId,_tmpReportedTimeAwayMin,_tmpReportedRecoveryMin,_tmpWasSkipped,_tmpIsDemo,_tmpCreatedAt);
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
  public Object getAll(final Continuation<? super List<Feedback>> $completion) {
    final String _sql = "SELECT * FROM feedback WHERE isDemo=0";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<Feedback>>() {
      @Override
      @NonNull
      public List<Feedback> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfNotificationId = CursorUtil.getColumnIndexOrThrow(_cursor, "notificationId");
          final int _cursorIndexOfActionId = CursorUtil.getColumnIndexOrThrow(_cursor, "actionId");
          final int _cursorIndexOfReportedTimeAwayMin = CursorUtil.getColumnIndexOrThrow(_cursor, "reportedTimeAwayMin");
          final int _cursorIndexOfReportedRecoveryMin = CursorUtil.getColumnIndexOrThrow(_cursor, "reportedRecoveryMin");
          final int _cursorIndexOfWasSkipped = CursorUtil.getColumnIndexOrThrow(_cursor, "wasSkipped");
          final int _cursorIndexOfIsDemo = CursorUtil.getColumnIndexOrThrow(_cursor, "isDemo");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final List<Feedback> _result = new ArrayList<Feedback>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Feedback _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpNotificationId;
            _tmpNotificationId = _cursor.getString(_cursorIndexOfNotificationId);
            final String _tmpActionId;
            _tmpActionId = _cursor.getString(_cursorIndexOfActionId);
            final Float _tmpReportedTimeAwayMin;
            if (_cursor.isNull(_cursorIndexOfReportedTimeAwayMin)) {
              _tmpReportedTimeAwayMin = null;
            } else {
              _tmpReportedTimeAwayMin = _cursor.getFloat(_cursorIndexOfReportedTimeAwayMin);
            }
            final Float _tmpReportedRecoveryMin;
            if (_cursor.isNull(_cursorIndexOfReportedRecoveryMin)) {
              _tmpReportedRecoveryMin = null;
            } else {
              _tmpReportedRecoveryMin = _cursor.getFloat(_cursorIndexOfReportedRecoveryMin);
            }
            final boolean _tmpWasSkipped;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfWasSkipped);
            _tmpWasSkipped = _tmp != 0;
            final boolean _tmpIsDemo;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsDemo);
            _tmpIsDemo = _tmp_1 != 0;
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            _item = new Feedback(_tmpId,_tmpNotificationId,_tmpActionId,_tmpReportedTimeAwayMin,_tmpReportedRecoveryMin,_tmpWasSkipped,_tmpIsDemo,_tmpCreatedAt);
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
  public Flow<List<Feedback>> observeAll() {
    final String _sql = "SELECT * FROM feedback ORDER BY createdAt DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"feedback"}, new Callable<List<Feedback>>() {
      @Override
      @NonNull
      public List<Feedback> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfNotificationId = CursorUtil.getColumnIndexOrThrow(_cursor, "notificationId");
          final int _cursorIndexOfActionId = CursorUtil.getColumnIndexOrThrow(_cursor, "actionId");
          final int _cursorIndexOfReportedTimeAwayMin = CursorUtil.getColumnIndexOrThrow(_cursor, "reportedTimeAwayMin");
          final int _cursorIndexOfReportedRecoveryMin = CursorUtil.getColumnIndexOrThrow(_cursor, "reportedRecoveryMin");
          final int _cursorIndexOfWasSkipped = CursorUtil.getColumnIndexOrThrow(_cursor, "wasSkipped");
          final int _cursorIndexOfIsDemo = CursorUtil.getColumnIndexOrThrow(_cursor, "isDemo");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final List<Feedback> _result = new ArrayList<Feedback>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Feedback _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpNotificationId;
            _tmpNotificationId = _cursor.getString(_cursorIndexOfNotificationId);
            final String _tmpActionId;
            _tmpActionId = _cursor.getString(_cursorIndexOfActionId);
            final Float _tmpReportedTimeAwayMin;
            if (_cursor.isNull(_cursorIndexOfReportedTimeAwayMin)) {
              _tmpReportedTimeAwayMin = null;
            } else {
              _tmpReportedTimeAwayMin = _cursor.getFloat(_cursorIndexOfReportedTimeAwayMin);
            }
            final Float _tmpReportedRecoveryMin;
            if (_cursor.isNull(_cursorIndexOfReportedRecoveryMin)) {
              _tmpReportedRecoveryMin = null;
            } else {
              _tmpReportedRecoveryMin = _cursor.getFloat(_cursorIndexOfReportedRecoveryMin);
            }
            final boolean _tmpWasSkipped;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfWasSkipped);
            _tmpWasSkipped = _tmp != 0;
            final boolean _tmpIsDemo;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsDemo);
            _tmpIsDemo = _tmp_1 != 0;
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            _item = new Feedback(_tmpId,_tmpNotificationId,_tmpActionId,_tmpReportedTimeAwayMin,_tmpReportedRecoveryMin,_tmpWasSkipped,_tmpIsDemo,_tmpCreatedAt);
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
  public Object observationsWithCategory(final boolean demo,
      final Continuation<? super List<CategoryObservation>> $completion) {
    final String _sql = "SELECT q.category AS category, f.reportedTimeAwayMin AS timeAway, f.reportedRecoveryMin AS recovery FROM feedback f JOIN queued_notifications q ON q.id=f.notificationId WHERE f.isDemo=? AND f.reportedTimeAwayMin IS NOT NULL AND f.reportedRecoveryMin IS NOT NULL";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    final int _tmp = demo ? 1 : 0;
    _statement.bindLong(_argIndex, _tmp);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<CategoryObservation>>() {
      @Override
      @NonNull
      public List<CategoryObservation> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfCategory = 0;
          final int _cursorIndexOfTimeAway = 1;
          final int _cursorIndexOfRecovery = 2;
          final List<CategoryObservation> _result = new ArrayList<CategoryObservation>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final CategoryObservation _item;
            final String _tmpCategory;
            _tmpCategory = _cursor.getString(_cursorIndexOfCategory);
            final float _tmpTimeAway;
            _tmpTimeAway = _cursor.getFloat(_cursorIndexOfTimeAway);
            final float _tmpRecovery;
            _tmpRecovery = _cursor.getFloat(_cursorIndexOfRecovery);
            _item = new CategoryObservation(_tmpCategory,_tmpTimeAway,_tmpRecovery);
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

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
