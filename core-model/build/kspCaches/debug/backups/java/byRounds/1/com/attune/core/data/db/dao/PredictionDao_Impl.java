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
import com.attune.core.model.Prediction;
import com.attune.core.model.RiskLevel;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class PredictionDao_Impl implements PredictionDao {
  private final RoomDatabase __db;

  private final SharedSQLiteStatement __preparedStmtOfDeleteDemo;

  private final SharedSQLiteStatement __preparedStmtOfDeleteReal;

  private final EntityUpsertionAdapter<Prediction> __upsertionAdapterOfPrediction;

  private final Converters __converters = new Converters();

  public PredictionDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__preparedStmtOfDeleteDemo = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM predictions WHERE isDemo=1";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteReal = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM predictions WHERE isDemo=0";
        return _query;
      }
    };
    this.__upsertionAdapterOfPrediction = new EntityUpsertionAdapter<Prediction>(new EntityInsertionAdapter<Prediction>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT INTO `predictions` (`id`,`notificationId`,`risk`,`timeAwayMin`,`recoveryMin`,`explanation`,`confidence`,`observationCount`,`delayScenario`,`isEstimate`,`isDemo`,`createdAt`,`modelVersion`,`factorsSummary`,`sessionStateAtPrediction`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Prediction entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getNotificationId());
        final String _tmp = __converters.fromRL(entity.getRisk());
        statement.bindString(3, _tmp);
        statement.bindDouble(4, entity.getTimeAwayMin());
        statement.bindDouble(5, entity.getRecoveryMin());
        statement.bindString(6, entity.getExplanation());
        statement.bindDouble(7, entity.getConfidence());
        statement.bindLong(8, entity.getObservationCount());
        statement.bindString(9, entity.getDelayScenario());
        final int _tmp_1 = entity.isEstimate() ? 1 : 0;
        statement.bindLong(10, _tmp_1);
        final int _tmp_2 = entity.isDemo() ? 1 : 0;
        statement.bindLong(11, _tmp_2);
        statement.bindLong(12, entity.getCreatedAt());
        if (entity.getModelVersion() == null) {
          statement.bindNull(13);
        } else {
          statement.bindString(13, entity.getModelVersion());
        }
        if (entity.getFactorsSummary() == null) {
          statement.bindNull(14);
        } else {
          statement.bindString(14, entity.getFactorsSummary());
        }
        if (entity.getSessionStateAtPrediction() == null) {
          statement.bindNull(15);
        } else {
          statement.bindString(15, entity.getSessionStateAtPrediction());
        }
      }
    }, new EntityDeletionOrUpdateAdapter<Prediction>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE `predictions` SET `id` = ?,`notificationId` = ?,`risk` = ?,`timeAwayMin` = ?,`recoveryMin` = ?,`explanation` = ?,`confidence` = ?,`observationCount` = ?,`delayScenario` = ?,`isEstimate` = ?,`isDemo` = ?,`createdAt` = ?,`modelVersion` = ?,`factorsSummary` = ?,`sessionStateAtPrediction` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Prediction entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getNotificationId());
        final String _tmp = __converters.fromRL(entity.getRisk());
        statement.bindString(3, _tmp);
        statement.bindDouble(4, entity.getTimeAwayMin());
        statement.bindDouble(5, entity.getRecoveryMin());
        statement.bindString(6, entity.getExplanation());
        statement.bindDouble(7, entity.getConfidence());
        statement.bindLong(8, entity.getObservationCount());
        statement.bindString(9, entity.getDelayScenario());
        final int _tmp_1 = entity.isEstimate() ? 1 : 0;
        statement.bindLong(10, _tmp_1);
        final int _tmp_2 = entity.isDemo() ? 1 : 0;
        statement.bindLong(11, _tmp_2);
        statement.bindLong(12, entity.getCreatedAt());
        if (entity.getModelVersion() == null) {
          statement.bindNull(13);
        } else {
          statement.bindString(13, entity.getModelVersion());
        }
        if (entity.getFactorsSummary() == null) {
          statement.bindNull(14);
        } else {
          statement.bindString(14, entity.getFactorsSummary());
        }
        if (entity.getSessionStateAtPrediction() == null) {
          statement.bindNull(15);
        } else {
          statement.bindString(15, entity.getSessionStateAtPrediction());
        }
        statement.bindString(16, entity.getId());
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
  public Object upsert(final Prediction p, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __upsertionAdapterOfPrediction.upsert(p);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object getForNotification(final String id,
      final Continuation<? super Prediction> $completion) {
    final String _sql = "SELECT * FROM predictions WHERE notificationId=? ORDER BY createdAt DESC LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Prediction>() {
      @Override
      @Nullable
      public Prediction call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfNotificationId = CursorUtil.getColumnIndexOrThrow(_cursor, "notificationId");
          final int _cursorIndexOfRisk = CursorUtil.getColumnIndexOrThrow(_cursor, "risk");
          final int _cursorIndexOfTimeAwayMin = CursorUtil.getColumnIndexOrThrow(_cursor, "timeAwayMin");
          final int _cursorIndexOfRecoveryMin = CursorUtil.getColumnIndexOrThrow(_cursor, "recoveryMin");
          final int _cursorIndexOfExplanation = CursorUtil.getColumnIndexOrThrow(_cursor, "explanation");
          final int _cursorIndexOfConfidence = CursorUtil.getColumnIndexOrThrow(_cursor, "confidence");
          final int _cursorIndexOfObservationCount = CursorUtil.getColumnIndexOrThrow(_cursor, "observationCount");
          final int _cursorIndexOfDelayScenario = CursorUtil.getColumnIndexOrThrow(_cursor, "delayScenario");
          final int _cursorIndexOfIsEstimate = CursorUtil.getColumnIndexOrThrow(_cursor, "isEstimate");
          final int _cursorIndexOfIsDemo = CursorUtil.getColumnIndexOrThrow(_cursor, "isDemo");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfModelVersion = CursorUtil.getColumnIndexOrThrow(_cursor, "modelVersion");
          final int _cursorIndexOfFactorsSummary = CursorUtil.getColumnIndexOrThrow(_cursor, "factorsSummary");
          final int _cursorIndexOfSessionStateAtPrediction = CursorUtil.getColumnIndexOrThrow(_cursor, "sessionStateAtPrediction");
          final Prediction _result;
          if (_cursor.moveToFirst()) {
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpNotificationId;
            _tmpNotificationId = _cursor.getString(_cursorIndexOfNotificationId);
            final RiskLevel _tmpRisk;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfRisk);
            _tmpRisk = __converters.toRL(_tmp);
            final float _tmpTimeAwayMin;
            _tmpTimeAwayMin = _cursor.getFloat(_cursorIndexOfTimeAwayMin);
            final float _tmpRecoveryMin;
            _tmpRecoveryMin = _cursor.getFloat(_cursorIndexOfRecoveryMin);
            final String _tmpExplanation;
            _tmpExplanation = _cursor.getString(_cursorIndexOfExplanation);
            final float _tmpConfidence;
            _tmpConfidence = _cursor.getFloat(_cursorIndexOfConfidence);
            final int _tmpObservationCount;
            _tmpObservationCount = _cursor.getInt(_cursorIndexOfObservationCount);
            final String _tmpDelayScenario;
            _tmpDelayScenario = _cursor.getString(_cursorIndexOfDelayScenario);
            final boolean _tmpIsEstimate;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsEstimate);
            _tmpIsEstimate = _tmp_1 != 0;
            final boolean _tmpIsDemo;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsDemo);
            _tmpIsDemo = _tmp_2 != 0;
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final String _tmpModelVersion;
            if (_cursor.isNull(_cursorIndexOfModelVersion)) {
              _tmpModelVersion = null;
            } else {
              _tmpModelVersion = _cursor.getString(_cursorIndexOfModelVersion);
            }
            final String _tmpFactorsSummary;
            if (_cursor.isNull(_cursorIndexOfFactorsSummary)) {
              _tmpFactorsSummary = null;
            } else {
              _tmpFactorsSummary = _cursor.getString(_cursorIndexOfFactorsSummary);
            }
            final String _tmpSessionStateAtPrediction;
            if (_cursor.isNull(_cursorIndexOfSessionStateAtPrediction)) {
              _tmpSessionStateAtPrediction = null;
            } else {
              _tmpSessionStateAtPrediction = _cursor.getString(_cursorIndexOfSessionStateAtPrediction);
            }
            _result = new Prediction(_tmpId,_tmpNotificationId,_tmpRisk,_tmpTimeAwayMin,_tmpRecoveryMin,_tmpExplanation,_tmpConfidence,_tmpObservationCount,_tmpDelayScenario,_tmpIsEstimate,_tmpIsDemo,_tmpCreatedAt,_tmpModelVersion,_tmpFactorsSummary,_tmpSessionStateAtPrediction);
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
