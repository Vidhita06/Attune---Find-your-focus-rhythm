package com.attune.core.data.db.dao;

import android.database.Cursor;
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
import com.attune.core.data.db.Converters;
import com.attune.core.model.EnergyCheckIn;
import com.attune.core.model.EnergyLevel;
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
public final class EnergyCheckInDao_Impl implements EnergyCheckInDao {
  private final RoomDatabase __db;

  private final SharedSQLiteStatement __preparedStmtOfDeleteDemo;

  private final SharedSQLiteStatement __preparedStmtOfDeleteReal;

  private final EntityUpsertionAdapter<EnergyCheckIn> __upsertionAdapterOfEnergyCheckIn;

  private final Converters __converters = new Converters();

  public EnergyCheckInDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__preparedStmtOfDeleteDemo = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM energy_checkins WHERE isDemo=1";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteReal = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM energy_checkins WHERE isDemo=0";
        return _query;
      }
    };
    this.__upsertionAdapterOfEnergyCheckIn = new EntityUpsertionAdapter<EnergyCheckIn>(new EntityInsertionAdapter<EnergyCheckIn>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT INTO `energy_checkins` (`id`,`level`,`timestampMs`,`sessionId`,`isDemo`) VALUES (?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final EnergyCheckIn entity) {
        statement.bindString(1, entity.getId());
        final String _tmp = __converters.fromEL(entity.getLevel());
        statement.bindString(2, _tmp);
        statement.bindLong(3, entity.getTimestampMs());
        if (entity.getSessionId() == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, entity.getSessionId());
        }
        final int _tmp_1 = entity.isDemo() ? 1 : 0;
        statement.bindLong(5, _tmp_1);
      }
    }, new EntityDeletionOrUpdateAdapter<EnergyCheckIn>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE `energy_checkins` SET `id` = ?,`level` = ?,`timestampMs` = ?,`sessionId` = ?,`isDemo` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final EnergyCheckIn entity) {
        statement.bindString(1, entity.getId());
        final String _tmp = __converters.fromEL(entity.getLevel());
        statement.bindString(2, _tmp);
        statement.bindLong(3, entity.getTimestampMs());
        if (entity.getSessionId() == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, entity.getSessionId());
        }
        final int _tmp_1 = entity.isDemo() ? 1 : 0;
        statement.bindLong(5, _tmp_1);
        statement.bindString(6, entity.getId());
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
  public Object upsert(final EnergyCheckIn c, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __upsertionAdapterOfEnergyCheckIn.upsert(c);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<EnergyCheckIn>> observeAll() {
    final String _sql = "SELECT * FROM energy_checkins ORDER BY timestampMs DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"energy_checkins"}, new Callable<List<EnergyCheckIn>>() {
      @Override
      @NonNull
      public List<EnergyCheckIn> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfLevel = CursorUtil.getColumnIndexOrThrow(_cursor, "level");
          final int _cursorIndexOfTimestampMs = CursorUtil.getColumnIndexOrThrow(_cursor, "timestampMs");
          final int _cursorIndexOfSessionId = CursorUtil.getColumnIndexOrThrow(_cursor, "sessionId");
          final int _cursorIndexOfIsDemo = CursorUtil.getColumnIndexOrThrow(_cursor, "isDemo");
          final List<EnergyCheckIn> _result = new ArrayList<EnergyCheckIn>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final EnergyCheckIn _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final EnergyLevel _tmpLevel;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfLevel);
            _tmpLevel = __converters.toEL(_tmp);
            final long _tmpTimestampMs;
            _tmpTimestampMs = _cursor.getLong(_cursorIndexOfTimestampMs);
            final String _tmpSessionId;
            if (_cursor.isNull(_cursorIndexOfSessionId)) {
              _tmpSessionId = null;
            } else {
              _tmpSessionId = _cursor.getString(_cursorIndexOfSessionId);
            }
            final boolean _tmpIsDemo;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsDemo);
            _tmpIsDemo = _tmp_1 != 0;
            _item = new EnergyCheckIn(_tmpId,_tmpLevel,_tmpTimestampMs,_tmpSessionId,_tmpIsDemo);
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

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
