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
import com.attune.core.model.AttentionProfileSummary;
import java.lang.Class;
import java.lang.Exception;
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
public final class AttentionProfileSummaryDao_Impl implements AttentionProfileSummaryDao {
  private final RoomDatabase __db;

  private final SharedSQLiteStatement __preparedStmtOfDeleteAll;

  private final EntityUpsertionAdapter<AttentionProfileSummary> __upsertionAdapterOfAttentionProfileSummary;

  public AttentionProfileSummaryDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__preparedStmtOfDeleteAll = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM attention_profile_summary";
        return _query;
      }
    };
    this.__upsertionAdapterOfAttentionProfileSummary = new EntityUpsertionAdapter<AttentionProfileSummary>(new EntityInsertionAdapter<AttentionProfileSummary>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT INTO `attention_profile_summary` (`category`,`observationCount`,`avgTimeAwayMin`,`avgRecoveryMin`,`openCount`,`delayCount`,`dismissCount`,`lastUpdatedMs`) VALUES (?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final AttentionProfileSummary entity) {
        statement.bindString(1, entity.getCategory());
        statement.bindLong(2, entity.getObservationCount());
        statement.bindDouble(3, entity.getAvgTimeAwayMin());
        statement.bindDouble(4, entity.getAvgRecoveryMin());
        statement.bindLong(5, entity.getOpenCount());
        statement.bindLong(6, entity.getDelayCount());
        statement.bindLong(7, entity.getDismissCount());
        statement.bindLong(8, entity.getLastUpdatedMs());
      }
    }, new EntityDeletionOrUpdateAdapter<AttentionProfileSummary>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE `attention_profile_summary` SET `category` = ?,`observationCount` = ?,`avgTimeAwayMin` = ?,`avgRecoveryMin` = ?,`openCount` = ?,`delayCount` = ?,`dismissCount` = ?,`lastUpdatedMs` = ? WHERE `category` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final AttentionProfileSummary entity) {
        statement.bindString(1, entity.getCategory());
        statement.bindLong(2, entity.getObservationCount());
        statement.bindDouble(3, entity.getAvgTimeAwayMin());
        statement.bindDouble(4, entity.getAvgRecoveryMin());
        statement.bindLong(5, entity.getOpenCount());
        statement.bindLong(6, entity.getDelayCount());
        statement.bindLong(7, entity.getDismissCount());
        statement.bindLong(8, entity.getLastUpdatedMs());
        statement.bindString(9, entity.getCategory());
      }
    });
  }

  @Override
  public Object deleteAll(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteAll.acquire();
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
          __preparedStmtOfDeleteAll.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object upsert(final AttentionProfileSummary s,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __upsertionAdapterOfAttentionProfileSummary.upsert(s);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<AttentionProfileSummary>> observeAll() {
    final String _sql = "SELECT * FROM attention_profile_summary";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"attention_profile_summary"}, new Callable<List<AttentionProfileSummary>>() {
      @Override
      @NonNull
      public List<AttentionProfileSummary> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "category");
          final int _cursorIndexOfObservationCount = CursorUtil.getColumnIndexOrThrow(_cursor, "observationCount");
          final int _cursorIndexOfAvgTimeAwayMin = CursorUtil.getColumnIndexOrThrow(_cursor, "avgTimeAwayMin");
          final int _cursorIndexOfAvgRecoveryMin = CursorUtil.getColumnIndexOrThrow(_cursor, "avgRecoveryMin");
          final int _cursorIndexOfOpenCount = CursorUtil.getColumnIndexOrThrow(_cursor, "openCount");
          final int _cursorIndexOfDelayCount = CursorUtil.getColumnIndexOrThrow(_cursor, "delayCount");
          final int _cursorIndexOfDismissCount = CursorUtil.getColumnIndexOrThrow(_cursor, "dismissCount");
          final int _cursorIndexOfLastUpdatedMs = CursorUtil.getColumnIndexOrThrow(_cursor, "lastUpdatedMs");
          final List<AttentionProfileSummary> _result = new ArrayList<AttentionProfileSummary>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final AttentionProfileSummary _item;
            final String _tmpCategory;
            _tmpCategory = _cursor.getString(_cursorIndexOfCategory);
            final int _tmpObservationCount;
            _tmpObservationCount = _cursor.getInt(_cursorIndexOfObservationCount);
            final float _tmpAvgTimeAwayMin;
            _tmpAvgTimeAwayMin = _cursor.getFloat(_cursorIndexOfAvgTimeAwayMin);
            final float _tmpAvgRecoveryMin;
            _tmpAvgRecoveryMin = _cursor.getFloat(_cursorIndexOfAvgRecoveryMin);
            final int _tmpOpenCount;
            _tmpOpenCount = _cursor.getInt(_cursorIndexOfOpenCount);
            final int _tmpDelayCount;
            _tmpDelayCount = _cursor.getInt(_cursorIndexOfDelayCount);
            final int _tmpDismissCount;
            _tmpDismissCount = _cursor.getInt(_cursorIndexOfDismissCount);
            final long _tmpLastUpdatedMs;
            _tmpLastUpdatedMs = _cursor.getLong(_cursorIndexOfLastUpdatedMs);
            _item = new AttentionProfileSummary(_tmpCategory,_tmpObservationCount,_tmpAvgTimeAwayMin,_tmpAvgRecoveryMin,_tmpOpenCount,_tmpDelayCount,_tmpDismissCount,_tmpLastUpdatedMs);
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
  public Object getByCategory(final String cat,
      final Continuation<? super AttentionProfileSummary> $completion) {
    final String _sql = "SELECT * FROM attention_profile_summary WHERE category=?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, cat);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<AttentionProfileSummary>() {
      @Override
      @Nullable
      public AttentionProfileSummary call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "category");
          final int _cursorIndexOfObservationCount = CursorUtil.getColumnIndexOrThrow(_cursor, "observationCount");
          final int _cursorIndexOfAvgTimeAwayMin = CursorUtil.getColumnIndexOrThrow(_cursor, "avgTimeAwayMin");
          final int _cursorIndexOfAvgRecoveryMin = CursorUtil.getColumnIndexOrThrow(_cursor, "avgRecoveryMin");
          final int _cursorIndexOfOpenCount = CursorUtil.getColumnIndexOrThrow(_cursor, "openCount");
          final int _cursorIndexOfDelayCount = CursorUtil.getColumnIndexOrThrow(_cursor, "delayCount");
          final int _cursorIndexOfDismissCount = CursorUtil.getColumnIndexOrThrow(_cursor, "dismissCount");
          final int _cursorIndexOfLastUpdatedMs = CursorUtil.getColumnIndexOrThrow(_cursor, "lastUpdatedMs");
          final AttentionProfileSummary _result;
          if (_cursor.moveToFirst()) {
            final String _tmpCategory;
            _tmpCategory = _cursor.getString(_cursorIndexOfCategory);
            final int _tmpObservationCount;
            _tmpObservationCount = _cursor.getInt(_cursorIndexOfObservationCount);
            final float _tmpAvgTimeAwayMin;
            _tmpAvgTimeAwayMin = _cursor.getFloat(_cursorIndexOfAvgTimeAwayMin);
            final float _tmpAvgRecoveryMin;
            _tmpAvgRecoveryMin = _cursor.getFloat(_cursorIndexOfAvgRecoveryMin);
            final int _tmpOpenCount;
            _tmpOpenCount = _cursor.getInt(_cursorIndexOfOpenCount);
            final int _tmpDelayCount;
            _tmpDelayCount = _cursor.getInt(_cursorIndexOfDelayCount);
            final int _tmpDismissCount;
            _tmpDismissCount = _cursor.getInt(_cursorIndexOfDismissCount);
            final long _tmpLastUpdatedMs;
            _tmpLastUpdatedMs = _cursor.getLong(_cursorIndexOfLastUpdatedMs);
            _result = new AttentionProfileSummary(_tmpCategory,_tmpObservationCount,_tmpAvgTimeAwayMin,_tmpAvgRecoveryMin,_tmpOpenCount,_tmpDelayCount,_tmpDismissCount,_tmpLastUpdatedMs);
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
