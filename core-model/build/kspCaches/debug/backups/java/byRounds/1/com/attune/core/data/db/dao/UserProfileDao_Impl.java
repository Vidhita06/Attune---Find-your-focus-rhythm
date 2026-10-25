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
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.attune.core.model.UserProfile;
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
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class UserProfileDao_Impl implements UserProfileDao {
  private final RoomDatabase __db;

  private final EntityUpsertionAdapter<UserProfile> __upsertionAdapterOfUserProfile;

  public UserProfileDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__upsertionAdapterOfUserProfile = new EntityUpsertionAdapter<UserProfile>(new EntityInsertionAdapter<UserProfile>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT INTO `user_profile` (`id`,`focusDurationMin`,`breakDurationMin`,`onboardingComplete`,`selectedAppPackages`,`createdAt`) VALUES (?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final UserProfile entity) {
        statement.bindLong(1, entity.getId());
        statement.bindLong(2, entity.getFocusDurationMin());
        statement.bindLong(3, entity.getBreakDurationMin());
        final int _tmp = entity.getOnboardingComplete() ? 1 : 0;
        statement.bindLong(4, _tmp);
        statement.bindString(5, entity.getSelectedAppPackages());
        statement.bindLong(6, entity.getCreatedAt());
      }
    }, new EntityDeletionOrUpdateAdapter<UserProfile>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE `user_profile` SET `id` = ?,`focusDurationMin` = ?,`breakDurationMin` = ?,`onboardingComplete` = ?,`selectedAppPackages` = ?,`createdAt` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final UserProfile entity) {
        statement.bindLong(1, entity.getId());
        statement.bindLong(2, entity.getFocusDurationMin());
        statement.bindLong(3, entity.getBreakDurationMin());
        final int _tmp = entity.getOnboardingComplete() ? 1 : 0;
        statement.bindLong(4, _tmp);
        statement.bindString(5, entity.getSelectedAppPackages());
        statement.bindLong(6, entity.getCreatedAt());
        statement.bindLong(7, entity.getId());
      }
    });
  }

  @Override
  public Object upsert(final UserProfile p, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __upsertionAdapterOfUserProfile.upsert(p);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<UserProfile> observe() {
    final String _sql = "SELECT * FROM user_profile WHERE id=1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"user_profile"}, new Callable<UserProfile>() {
      @Override
      @Nullable
      public UserProfile call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfFocusDurationMin = CursorUtil.getColumnIndexOrThrow(_cursor, "focusDurationMin");
          final int _cursorIndexOfBreakDurationMin = CursorUtil.getColumnIndexOrThrow(_cursor, "breakDurationMin");
          final int _cursorIndexOfOnboardingComplete = CursorUtil.getColumnIndexOrThrow(_cursor, "onboardingComplete");
          final int _cursorIndexOfSelectedAppPackages = CursorUtil.getColumnIndexOrThrow(_cursor, "selectedAppPackages");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final UserProfile _result;
          if (_cursor.moveToFirst()) {
            final int _tmpId;
            _tmpId = _cursor.getInt(_cursorIndexOfId);
            final int _tmpFocusDurationMin;
            _tmpFocusDurationMin = _cursor.getInt(_cursorIndexOfFocusDurationMin);
            final int _tmpBreakDurationMin;
            _tmpBreakDurationMin = _cursor.getInt(_cursorIndexOfBreakDurationMin);
            final boolean _tmpOnboardingComplete;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfOnboardingComplete);
            _tmpOnboardingComplete = _tmp != 0;
            final String _tmpSelectedAppPackages;
            _tmpSelectedAppPackages = _cursor.getString(_cursorIndexOfSelectedAppPackages);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            _result = new UserProfile(_tmpId,_tmpFocusDurationMin,_tmpBreakDurationMin,_tmpOnboardingComplete,_tmpSelectedAppPackages,_tmpCreatedAt);
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
  public Object get(final Continuation<? super UserProfile> $completion) {
    final String _sql = "SELECT * FROM user_profile WHERE id=1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<UserProfile>() {
      @Override
      @Nullable
      public UserProfile call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfFocusDurationMin = CursorUtil.getColumnIndexOrThrow(_cursor, "focusDurationMin");
          final int _cursorIndexOfBreakDurationMin = CursorUtil.getColumnIndexOrThrow(_cursor, "breakDurationMin");
          final int _cursorIndexOfOnboardingComplete = CursorUtil.getColumnIndexOrThrow(_cursor, "onboardingComplete");
          final int _cursorIndexOfSelectedAppPackages = CursorUtil.getColumnIndexOrThrow(_cursor, "selectedAppPackages");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final UserProfile _result;
          if (_cursor.moveToFirst()) {
            final int _tmpId;
            _tmpId = _cursor.getInt(_cursorIndexOfId);
            final int _tmpFocusDurationMin;
            _tmpFocusDurationMin = _cursor.getInt(_cursorIndexOfFocusDurationMin);
            final int _tmpBreakDurationMin;
            _tmpBreakDurationMin = _cursor.getInt(_cursorIndexOfBreakDurationMin);
            final boolean _tmpOnboardingComplete;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfOnboardingComplete);
            _tmpOnboardingComplete = _tmp != 0;
            final String _tmpSelectedAppPackages;
            _tmpSelectedAppPackages = _cursor.getString(_cursorIndexOfSelectedAppPackages);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            _result = new UserProfile(_tmpId,_tmpFocusDurationMin,_tmpBreakDurationMin,_tmpOnboardingComplete,_tmpSelectedAppPackages,_tmpCreatedAt);
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
