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
import com.attune.core.model.NotificationStatus;
import com.attune.core.model.QueuedNotification;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Integer;
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
public final class QueuedNotificationDao_Impl implements QueuedNotificationDao {
  private final RoomDatabase __db;

  private final SharedSQLiteStatement __preparedStmtOfUpdateStatus;

  private final Converters __converters = new Converters();

  private final SharedSQLiteStatement __preparedStmtOfDeleteDemo;

  private final SharedSQLiteStatement __preparedStmtOfDeleteReal;

  private final EntityUpsertionAdapter<QueuedNotification> __upsertionAdapterOfQueuedNotification;

  public QueuedNotificationDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__preparedStmtOfUpdateStatus = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE queued_notifications SET status=?, statusChangedMs=? WHERE id=?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteDemo = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM queued_notifications WHERE isDemo=1";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteReal = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM queued_notifications WHERE isDemo=0";
        return _query;
      }
    };
    this.__upsertionAdapterOfQueuedNotification = new EntityUpsertionAdapter<QueuedNotification>(new EntityInsertionAdapter<QueuedNotification>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT INTO `queued_notifications` (`id`,`sessionId`,`packageName`,`category`,`title`,`previewText`,`dedupKey`,`postTimeMs`,`status`,`contentIntentData`,`isGroupSummary`,`isDemo`,`createdAt`,`appLabel`,`sbnKey`,`groupCount`,`lastUpdatedMs`,`wasSuppressed`,`sourceRemoved`,`ruleApplied`,`androidCategory`,`statusChangedMs`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final QueuedNotification entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getSessionId());
        statement.bindString(3, entity.getPackageName());
        statement.bindString(4, entity.getCategory());
        if (entity.getTitle() == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.getTitle());
        }
        if (entity.getPreviewText() == null) {
          statement.bindNull(6);
        } else {
          statement.bindString(6, entity.getPreviewText());
        }
        statement.bindString(7, entity.getDedupKey());
        statement.bindLong(8, entity.getPostTimeMs());
        final String _tmp = __converters.fromNS(entity.getStatus());
        statement.bindString(9, _tmp);
        if (entity.getContentIntentData() == null) {
          statement.bindNull(10);
        } else {
          statement.bindString(10, entity.getContentIntentData());
        }
        final int _tmp_1 = entity.isGroupSummary() ? 1 : 0;
        statement.bindLong(11, _tmp_1);
        final int _tmp_2 = entity.isDemo() ? 1 : 0;
        statement.bindLong(12, _tmp_2);
        statement.bindLong(13, entity.getCreatedAt());
        statement.bindString(14, entity.getAppLabel());
        if (entity.getSbnKey() == null) {
          statement.bindNull(15);
        } else {
          statement.bindString(15, entity.getSbnKey());
        }
        statement.bindLong(16, entity.getGroupCount());
        statement.bindLong(17, entity.getLastUpdatedMs());
        final int _tmp_3 = entity.getWasSuppressed() ? 1 : 0;
        statement.bindLong(18, _tmp_3);
        final int _tmp_4 = entity.getSourceRemoved() ? 1 : 0;
        statement.bindLong(19, _tmp_4);
        if (entity.getRuleApplied() == null) {
          statement.bindNull(20);
        } else {
          statement.bindString(20, entity.getRuleApplied());
        }
        if (entity.getAndroidCategory() == null) {
          statement.bindNull(21);
        } else {
          statement.bindString(21, entity.getAndroidCategory());
        }
        if (entity.getStatusChangedMs() == null) {
          statement.bindNull(22);
        } else {
          statement.bindLong(22, entity.getStatusChangedMs());
        }
      }
    }, new EntityDeletionOrUpdateAdapter<QueuedNotification>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE `queued_notifications` SET `id` = ?,`sessionId` = ?,`packageName` = ?,`category` = ?,`title` = ?,`previewText` = ?,`dedupKey` = ?,`postTimeMs` = ?,`status` = ?,`contentIntentData` = ?,`isGroupSummary` = ?,`isDemo` = ?,`createdAt` = ?,`appLabel` = ?,`sbnKey` = ?,`groupCount` = ?,`lastUpdatedMs` = ?,`wasSuppressed` = ?,`sourceRemoved` = ?,`ruleApplied` = ?,`androidCategory` = ?,`statusChangedMs` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final QueuedNotification entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getSessionId());
        statement.bindString(3, entity.getPackageName());
        statement.bindString(4, entity.getCategory());
        if (entity.getTitle() == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.getTitle());
        }
        if (entity.getPreviewText() == null) {
          statement.bindNull(6);
        } else {
          statement.bindString(6, entity.getPreviewText());
        }
        statement.bindString(7, entity.getDedupKey());
        statement.bindLong(8, entity.getPostTimeMs());
        final String _tmp = __converters.fromNS(entity.getStatus());
        statement.bindString(9, _tmp);
        if (entity.getContentIntentData() == null) {
          statement.bindNull(10);
        } else {
          statement.bindString(10, entity.getContentIntentData());
        }
        final int _tmp_1 = entity.isGroupSummary() ? 1 : 0;
        statement.bindLong(11, _tmp_1);
        final int _tmp_2 = entity.isDemo() ? 1 : 0;
        statement.bindLong(12, _tmp_2);
        statement.bindLong(13, entity.getCreatedAt());
        statement.bindString(14, entity.getAppLabel());
        if (entity.getSbnKey() == null) {
          statement.bindNull(15);
        } else {
          statement.bindString(15, entity.getSbnKey());
        }
        statement.bindLong(16, entity.getGroupCount());
        statement.bindLong(17, entity.getLastUpdatedMs());
        final int _tmp_3 = entity.getWasSuppressed() ? 1 : 0;
        statement.bindLong(18, _tmp_3);
        final int _tmp_4 = entity.getSourceRemoved() ? 1 : 0;
        statement.bindLong(19, _tmp_4);
        if (entity.getRuleApplied() == null) {
          statement.bindNull(20);
        } else {
          statement.bindString(20, entity.getRuleApplied());
        }
        if (entity.getAndroidCategory() == null) {
          statement.bindNull(21);
        } else {
          statement.bindString(21, entity.getAndroidCategory());
        }
        if (entity.getStatusChangedMs() == null) {
          statement.bindNull(22);
        } else {
          statement.bindLong(22, entity.getStatusChangedMs());
        }
        statement.bindString(23, entity.getId());
      }
    });
  }

  @Override
  public Object updateStatus(final String id, final NotificationStatus status, final long atMs,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateStatus.acquire();
        int _argIndex = 1;
        final String _tmp = __converters.fromNS(status);
        _stmt.bindString(_argIndex, _tmp);
        _argIndex = 2;
        _stmt.bindLong(_argIndex, atMs);
        _argIndex = 3;
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
          __preparedStmtOfUpdateStatus.release(_stmt);
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
  public Object upsert(final QueuedNotification n, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __upsertionAdapterOfQueuedNotification.upsert(n);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<QueuedNotification>> observePending() {
    final String _sql = "SELECT * FROM queued_notifications WHERE status='PENDING' ORDER BY postTimeMs ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"queued_notifications"}, new Callable<List<QueuedNotification>>() {
      @Override
      @NonNull
      public List<QueuedNotification> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSessionId = CursorUtil.getColumnIndexOrThrow(_cursor, "sessionId");
          final int _cursorIndexOfPackageName = CursorUtil.getColumnIndexOrThrow(_cursor, "packageName");
          final int _cursorIndexOfCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "category");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfPreviewText = CursorUtil.getColumnIndexOrThrow(_cursor, "previewText");
          final int _cursorIndexOfDedupKey = CursorUtil.getColumnIndexOrThrow(_cursor, "dedupKey");
          final int _cursorIndexOfPostTimeMs = CursorUtil.getColumnIndexOrThrow(_cursor, "postTimeMs");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfContentIntentData = CursorUtil.getColumnIndexOrThrow(_cursor, "contentIntentData");
          final int _cursorIndexOfIsGroupSummary = CursorUtil.getColumnIndexOrThrow(_cursor, "isGroupSummary");
          final int _cursorIndexOfIsDemo = CursorUtil.getColumnIndexOrThrow(_cursor, "isDemo");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfAppLabel = CursorUtil.getColumnIndexOrThrow(_cursor, "appLabel");
          final int _cursorIndexOfSbnKey = CursorUtil.getColumnIndexOrThrow(_cursor, "sbnKey");
          final int _cursorIndexOfGroupCount = CursorUtil.getColumnIndexOrThrow(_cursor, "groupCount");
          final int _cursorIndexOfLastUpdatedMs = CursorUtil.getColumnIndexOrThrow(_cursor, "lastUpdatedMs");
          final int _cursorIndexOfWasSuppressed = CursorUtil.getColumnIndexOrThrow(_cursor, "wasSuppressed");
          final int _cursorIndexOfSourceRemoved = CursorUtil.getColumnIndexOrThrow(_cursor, "sourceRemoved");
          final int _cursorIndexOfRuleApplied = CursorUtil.getColumnIndexOrThrow(_cursor, "ruleApplied");
          final int _cursorIndexOfAndroidCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "androidCategory");
          final int _cursorIndexOfStatusChangedMs = CursorUtil.getColumnIndexOrThrow(_cursor, "statusChangedMs");
          final List<QueuedNotification> _result = new ArrayList<QueuedNotification>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final QueuedNotification _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpSessionId;
            _tmpSessionId = _cursor.getString(_cursorIndexOfSessionId);
            final String _tmpPackageName;
            _tmpPackageName = _cursor.getString(_cursorIndexOfPackageName);
            final String _tmpCategory;
            _tmpCategory = _cursor.getString(_cursorIndexOfCategory);
            final String _tmpTitle;
            if (_cursor.isNull(_cursorIndexOfTitle)) {
              _tmpTitle = null;
            } else {
              _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            }
            final String _tmpPreviewText;
            if (_cursor.isNull(_cursorIndexOfPreviewText)) {
              _tmpPreviewText = null;
            } else {
              _tmpPreviewText = _cursor.getString(_cursorIndexOfPreviewText);
            }
            final String _tmpDedupKey;
            _tmpDedupKey = _cursor.getString(_cursorIndexOfDedupKey);
            final long _tmpPostTimeMs;
            _tmpPostTimeMs = _cursor.getLong(_cursorIndexOfPostTimeMs);
            final NotificationStatus _tmpStatus;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfStatus);
            _tmpStatus = __converters.toNS(_tmp);
            final String _tmpContentIntentData;
            if (_cursor.isNull(_cursorIndexOfContentIntentData)) {
              _tmpContentIntentData = null;
            } else {
              _tmpContentIntentData = _cursor.getString(_cursorIndexOfContentIntentData);
            }
            final boolean _tmpIsGroupSummary;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsGroupSummary);
            _tmpIsGroupSummary = _tmp_1 != 0;
            final boolean _tmpIsDemo;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsDemo);
            _tmpIsDemo = _tmp_2 != 0;
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final String _tmpAppLabel;
            _tmpAppLabel = _cursor.getString(_cursorIndexOfAppLabel);
            final String _tmpSbnKey;
            if (_cursor.isNull(_cursorIndexOfSbnKey)) {
              _tmpSbnKey = null;
            } else {
              _tmpSbnKey = _cursor.getString(_cursorIndexOfSbnKey);
            }
            final int _tmpGroupCount;
            _tmpGroupCount = _cursor.getInt(_cursorIndexOfGroupCount);
            final long _tmpLastUpdatedMs;
            _tmpLastUpdatedMs = _cursor.getLong(_cursorIndexOfLastUpdatedMs);
            final boolean _tmpWasSuppressed;
            final int _tmp_3;
            _tmp_3 = _cursor.getInt(_cursorIndexOfWasSuppressed);
            _tmpWasSuppressed = _tmp_3 != 0;
            final boolean _tmpSourceRemoved;
            final int _tmp_4;
            _tmp_4 = _cursor.getInt(_cursorIndexOfSourceRemoved);
            _tmpSourceRemoved = _tmp_4 != 0;
            final String _tmpRuleApplied;
            if (_cursor.isNull(_cursorIndexOfRuleApplied)) {
              _tmpRuleApplied = null;
            } else {
              _tmpRuleApplied = _cursor.getString(_cursorIndexOfRuleApplied);
            }
            final String _tmpAndroidCategory;
            if (_cursor.isNull(_cursorIndexOfAndroidCategory)) {
              _tmpAndroidCategory = null;
            } else {
              _tmpAndroidCategory = _cursor.getString(_cursorIndexOfAndroidCategory);
            }
            final Long _tmpStatusChangedMs;
            if (_cursor.isNull(_cursorIndexOfStatusChangedMs)) {
              _tmpStatusChangedMs = null;
            } else {
              _tmpStatusChangedMs = _cursor.getLong(_cursorIndexOfStatusChangedMs);
            }
            _item = new QueuedNotification(_tmpId,_tmpSessionId,_tmpPackageName,_tmpCategory,_tmpTitle,_tmpPreviewText,_tmpDedupKey,_tmpPostTimeMs,_tmpStatus,_tmpContentIntentData,_tmpIsGroupSummary,_tmpIsDemo,_tmpCreatedAt,_tmpAppLabel,_tmpSbnKey,_tmpGroupCount,_tmpLastUpdatedMs,_tmpWasSuppressed,_tmpSourceRemoved,_tmpRuleApplied,_tmpAndroidCategory,_tmpStatusChangedMs);
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
  public Flow<List<QueuedNotification>> observeAll() {
    final String _sql = "SELECT * FROM queued_notifications ORDER BY postTimeMs DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"queued_notifications"}, new Callable<List<QueuedNotification>>() {
      @Override
      @NonNull
      public List<QueuedNotification> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSessionId = CursorUtil.getColumnIndexOrThrow(_cursor, "sessionId");
          final int _cursorIndexOfPackageName = CursorUtil.getColumnIndexOrThrow(_cursor, "packageName");
          final int _cursorIndexOfCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "category");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfPreviewText = CursorUtil.getColumnIndexOrThrow(_cursor, "previewText");
          final int _cursorIndexOfDedupKey = CursorUtil.getColumnIndexOrThrow(_cursor, "dedupKey");
          final int _cursorIndexOfPostTimeMs = CursorUtil.getColumnIndexOrThrow(_cursor, "postTimeMs");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfContentIntentData = CursorUtil.getColumnIndexOrThrow(_cursor, "contentIntentData");
          final int _cursorIndexOfIsGroupSummary = CursorUtil.getColumnIndexOrThrow(_cursor, "isGroupSummary");
          final int _cursorIndexOfIsDemo = CursorUtil.getColumnIndexOrThrow(_cursor, "isDemo");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfAppLabel = CursorUtil.getColumnIndexOrThrow(_cursor, "appLabel");
          final int _cursorIndexOfSbnKey = CursorUtil.getColumnIndexOrThrow(_cursor, "sbnKey");
          final int _cursorIndexOfGroupCount = CursorUtil.getColumnIndexOrThrow(_cursor, "groupCount");
          final int _cursorIndexOfLastUpdatedMs = CursorUtil.getColumnIndexOrThrow(_cursor, "lastUpdatedMs");
          final int _cursorIndexOfWasSuppressed = CursorUtil.getColumnIndexOrThrow(_cursor, "wasSuppressed");
          final int _cursorIndexOfSourceRemoved = CursorUtil.getColumnIndexOrThrow(_cursor, "sourceRemoved");
          final int _cursorIndexOfRuleApplied = CursorUtil.getColumnIndexOrThrow(_cursor, "ruleApplied");
          final int _cursorIndexOfAndroidCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "androidCategory");
          final int _cursorIndexOfStatusChangedMs = CursorUtil.getColumnIndexOrThrow(_cursor, "statusChangedMs");
          final List<QueuedNotification> _result = new ArrayList<QueuedNotification>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final QueuedNotification _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpSessionId;
            _tmpSessionId = _cursor.getString(_cursorIndexOfSessionId);
            final String _tmpPackageName;
            _tmpPackageName = _cursor.getString(_cursorIndexOfPackageName);
            final String _tmpCategory;
            _tmpCategory = _cursor.getString(_cursorIndexOfCategory);
            final String _tmpTitle;
            if (_cursor.isNull(_cursorIndexOfTitle)) {
              _tmpTitle = null;
            } else {
              _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            }
            final String _tmpPreviewText;
            if (_cursor.isNull(_cursorIndexOfPreviewText)) {
              _tmpPreviewText = null;
            } else {
              _tmpPreviewText = _cursor.getString(_cursorIndexOfPreviewText);
            }
            final String _tmpDedupKey;
            _tmpDedupKey = _cursor.getString(_cursorIndexOfDedupKey);
            final long _tmpPostTimeMs;
            _tmpPostTimeMs = _cursor.getLong(_cursorIndexOfPostTimeMs);
            final NotificationStatus _tmpStatus;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfStatus);
            _tmpStatus = __converters.toNS(_tmp);
            final String _tmpContentIntentData;
            if (_cursor.isNull(_cursorIndexOfContentIntentData)) {
              _tmpContentIntentData = null;
            } else {
              _tmpContentIntentData = _cursor.getString(_cursorIndexOfContentIntentData);
            }
            final boolean _tmpIsGroupSummary;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsGroupSummary);
            _tmpIsGroupSummary = _tmp_1 != 0;
            final boolean _tmpIsDemo;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsDemo);
            _tmpIsDemo = _tmp_2 != 0;
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final String _tmpAppLabel;
            _tmpAppLabel = _cursor.getString(_cursorIndexOfAppLabel);
            final String _tmpSbnKey;
            if (_cursor.isNull(_cursorIndexOfSbnKey)) {
              _tmpSbnKey = null;
            } else {
              _tmpSbnKey = _cursor.getString(_cursorIndexOfSbnKey);
            }
            final int _tmpGroupCount;
            _tmpGroupCount = _cursor.getInt(_cursorIndexOfGroupCount);
            final long _tmpLastUpdatedMs;
            _tmpLastUpdatedMs = _cursor.getLong(_cursorIndexOfLastUpdatedMs);
            final boolean _tmpWasSuppressed;
            final int _tmp_3;
            _tmp_3 = _cursor.getInt(_cursorIndexOfWasSuppressed);
            _tmpWasSuppressed = _tmp_3 != 0;
            final boolean _tmpSourceRemoved;
            final int _tmp_4;
            _tmp_4 = _cursor.getInt(_cursorIndexOfSourceRemoved);
            _tmpSourceRemoved = _tmp_4 != 0;
            final String _tmpRuleApplied;
            if (_cursor.isNull(_cursorIndexOfRuleApplied)) {
              _tmpRuleApplied = null;
            } else {
              _tmpRuleApplied = _cursor.getString(_cursorIndexOfRuleApplied);
            }
            final String _tmpAndroidCategory;
            if (_cursor.isNull(_cursorIndexOfAndroidCategory)) {
              _tmpAndroidCategory = null;
            } else {
              _tmpAndroidCategory = _cursor.getString(_cursorIndexOfAndroidCategory);
            }
            final Long _tmpStatusChangedMs;
            if (_cursor.isNull(_cursorIndexOfStatusChangedMs)) {
              _tmpStatusChangedMs = null;
            } else {
              _tmpStatusChangedMs = _cursor.getLong(_cursorIndexOfStatusChangedMs);
            }
            _item = new QueuedNotification(_tmpId,_tmpSessionId,_tmpPackageName,_tmpCategory,_tmpTitle,_tmpPreviewText,_tmpDedupKey,_tmpPostTimeMs,_tmpStatus,_tmpContentIntentData,_tmpIsGroupSummary,_tmpIsDemo,_tmpCreatedAt,_tmpAppLabel,_tmpSbnKey,_tmpGroupCount,_tmpLastUpdatedMs,_tmpWasSuppressed,_tmpSourceRemoved,_tmpRuleApplied,_tmpAndroidCategory,_tmpStatusChangedMs);
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
  public Object getById(final String id,
      final Continuation<? super QueuedNotification> $completion) {
    final String _sql = "SELECT * FROM queued_notifications WHERE id=?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<QueuedNotification>() {
      @Override
      @Nullable
      public QueuedNotification call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSessionId = CursorUtil.getColumnIndexOrThrow(_cursor, "sessionId");
          final int _cursorIndexOfPackageName = CursorUtil.getColumnIndexOrThrow(_cursor, "packageName");
          final int _cursorIndexOfCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "category");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfPreviewText = CursorUtil.getColumnIndexOrThrow(_cursor, "previewText");
          final int _cursorIndexOfDedupKey = CursorUtil.getColumnIndexOrThrow(_cursor, "dedupKey");
          final int _cursorIndexOfPostTimeMs = CursorUtil.getColumnIndexOrThrow(_cursor, "postTimeMs");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfContentIntentData = CursorUtil.getColumnIndexOrThrow(_cursor, "contentIntentData");
          final int _cursorIndexOfIsGroupSummary = CursorUtil.getColumnIndexOrThrow(_cursor, "isGroupSummary");
          final int _cursorIndexOfIsDemo = CursorUtil.getColumnIndexOrThrow(_cursor, "isDemo");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfAppLabel = CursorUtil.getColumnIndexOrThrow(_cursor, "appLabel");
          final int _cursorIndexOfSbnKey = CursorUtil.getColumnIndexOrThrow(_cursor, "sbnKey");
          final int _cursorIndexOfGroupCount = CursorUtil.getColumnIndexOrThrow(_cursor, "groupCount");
          final int _cursorIndexOfLastUpdatedMs = CursorUtil.getColumnIndexOrThrow(_cursor, "lastUpdatedMs");
          final int _cursorIndexOfWasSuppressed = CursorUtil.getColumnIndexOrThrow(_cursor, "wasSuppressed");
          final int _cursorIndexOfSourceRemoved = CursorUtil.getColumnIndexOrThrow(_cursor, "sourceRemoved");
          final int _cursorIndexOfRuleApplied = CursorUtil.getColumnIndexOrThrow(_cursor, "ruleApplied");
          final int _cursorIndexOfAndroidCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "androidCategory");
          final int _cursorIndexOfStatusChangedMs = CursorUtil.getColumnIndexOrThrow(_cursor, "statusChangedMs");
          final QueuedNotification _result;
          if (_cursor.moveToFirst()) {
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpSessionId;
            _tmpSessionId = _cursor.getString(_cursorIndexOfSessionId);
            final String _tmpPackageName;
            _tmpPackageName = _cursor.getString(_cursorIndexOfPackageName);
            final String _tmpCategory;
            _tmpCategory = _cursor.getString(_cursorIndexOfCategory);
            final String _tmpTitle;
            if (_cursor.isNull(_cursorIndexOfTitle)) {
              _tmpTitle = null;
            } else {
              _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            }
            final String _tmpPreviewText;
            if (_cursor.isNull(_cursorIndexOfPreviewText)) {
              _tmpPreviewText = null;
            } else {
              _tmpPreviewText = _cursor.getString(_cursorIndexOfPreviewText);
            }
            final String _tmpDedupKey;
            _tmpDedupKey = _cursor.getString(_cursorIndexOfDedupKey);
            final long _tmpPostTimeMs;
            _tmpPostTimeMs = _cursor.getLong(_cursorIndexOfPostTimeMs);
            final NotificationStatus _tmpStatus;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfStatus);
            _tmpStatus = __converters.toNS(_tmp);
            final String _tmpContentIntentData;
            if (_cursor.isNull(_cursorIndexOfContentIntentData)) {
              _tmpContentIntentData = null;
            } else {
              _tmpContentIntentData = _cursor.getString(_cursorIndexOfContentIntentData);
            }
            final boolean _tmpIsGroupSummary;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsGroupSummary);
            _tmpIsGroupSummary = _tmp_1 != 0;
            final boolean _tmpIsDemo;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsDemo);
            _tmpIsDemo = _tmp_2 != 0;
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final String _tmpAppLabel;
            _tmpAppLabel = _cursor.getString(_cursorIndexOfAppLabel);
            final String _tmpSbnKey;
            if (_cursor.isNull(_cursorIndexOfSbnKey)) {
              _tmpSbnKey = null;
            } else {
              _tmpSbnKey = _cursor.getString(_cursorIndexOfSbnKey);
            }
            final int _tmpGroupCount;
            _tmpGroupCount = _cursor.getInt(_cursorIndexOfGroupCount);
            final long _tmpLastUpdatedMs;
            _tmpLastUpdatedMs = _cursor.getLong(_cursorIndexOfLastUpdatedMs);
            final boolean _tmpWasSuppressed;
            final int _tmp_3;
            _tmp_3 = _cursor.getInt(_cursorIndexOfWasSuppressed);
            _tmpWasSuppressed = _tmp_3 != 0;
            final boolean _tmpSourceRemoved;
            final int _tmp_4;
            _tmp_4 = _cursor.getInt(_cursorIndexOfSourceRemoved);
            _tmpSourceRemoved = _tmp_4 != 0;
            final String _tmpRuleApplied;
            if (_cursor.isNull(_cursorIndexOfRuleApplied)) {
              _tmpRuleApplied = null;
            } else {
              _tmpRuleApplied = _cursor.getString(_cursorIndexOfRuleApplied);
            }
            final String _tmpAndroidCategory;
            if (_cursor.isNull(_cursorIndexOfAndroidCategory)) {
              _tmpAndroidCategory = null;
            } else {
              _tmpAndroidCategory = _cursor.getString(_cursorIndexOfAndroidCategory);
            }
            final Long _tmpStatusChangedMs;
            if (_cursor.isNull(_cursorIndexOfStatusChangedMs)) {
              _tmpStatusChangedMs = null;
            } else {
              _tmpStatusChangedMs = _cursor.getLong(_cursorIndexOfStatusChangedMs);
            }
            _result = new QueuedNotification(_tmpId,_tmpSessionId,_tmpPackageName,_tmpCategory,_tmpTitle,_tmpPreviewText,_tmpDedupKey,_tmpPostTimeMs,_tmpStatus,_tmpContentIntentData,_tmpIsGroupSummary,_tmpIsDemo,_tmpCreatedAt,_tmpAppLabel,_tmpSbnKey,_tmpGroupCount,_tmpLastUpdatedMs,_tmpWasSuppressed,_tmpSourceRemoved,_tmpRuleApplied,_tmpAndroidCategory,_tmpStatusChangedMs);
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
  public Flow<QueuedNotification> observeById(final String id) {
    final String _sql = "SELECT * FROM queued_notifications WHERE id=?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, id);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"queued_notifications"}, new Callable<QueuedNotification>() {
      @Override
      @Nullable
      public QueuedNotification call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSessionId = CursorUtil.getColumnIndexOrThrow(_cursor, "sessionId");
          final int _cursorIndexOfPackageName = CursorUtil.getColumnIndexOrThrow(_cursor, "packageName");
          final int _cursorIndexOfCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "category");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfPreviewText = CursorUtil.getColumnIndexOrThrow(_cursor, "previewText");
          final int _cursorIndexOfDedupKey = CursorUtil.getColumnIndexOrThrow(_cursor, "dedupKey");
          final int _cursorIndexOfPostTimeMs = CursorUtil.getColumnIndexOrThrow(_cursor, "postTimeMs");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfContentIntentData = CursorUtil.getColumnIndexOrThrow(_cursor, "contentIntentData");
          final int _cursorIndexOfIsGroupSummary = CursorUtil.getColumnIndexOrThrow(_cursor, "isGroupSummary");
          final int _cursorIndexOfIsDemo = CursorUtil.getColumnIndexOrThrow(_cursor, "isDemo");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfAppLabel = CursorUtil.getColumnIndexOrThrow(_cursor, "appLabel");
          final int _cursorIndexOfSbnKey = CursorUtil.getColumnIndexOrThrow(_cursor, "sbnKey");
          final int _cursorIndexOfGroupCount = CursorUtil.getColumnIndexOrThrow(_cursor, "groupCount");
          final int _cursorIndexOfLastUpdatedMs = CursorUtil.getColumnIndexOrThrow(_cursor, "lastUpdatedMs");
          final int _cursorIndexOfWasSuppressed = CursorUtil.getColumnIndexOrThrow(_cursor, "wasSuppressed");
          final int _cursorIndexOfSourceRemoved = CursorUtil.getColumnIndexOrThrow(_cursor, "sourceRemoved");
          final int _cursorIndexOfRuleApplied = CursorUtil.getColumnIndexOrThrow(_cursor, "ruleApplied");
          final int _cursorIndexOfAndroidCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "androidCategory");
          final int _cursorIndexOfStatusChangedMs = CursorUtil.getColumnIndexOrThrow(_cursor, "statusChangedMs");
          final QueuedNotification _result;
          if (_cursor.moveToFirst()) {
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpSessionId;
            _tmpSessionId = _cursor.getString(_cursorIndexOfSessionId);
            final String _tmpPackageName;
            _tmpPackageName = _cursor.getString(_cursorIndexOfPackageName);
            final String _tmpCategory;
            _tmpCategory = _cursor.getString(_cursorIndexOfCategory);
            final String _tmpTitle;
            if (_cursor.isNull(_cursorIndexOfTitle)) {
              _tmpTitle = null;
            } else {
              _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            }
            final String _tmpPreviewText;
            if (_cursor.isNull(_cursorIndexOfPreviewText)) {
              _tmpPreviewText = null;
            } else {
              _tmpPreviewText = _cursor.getString(_cursorIndexOfPreviewText);
            }
            final String _tmpDedupKey;
            _tmpDedupKey = _cursor.getString(_cursorIndexOfDedupKey);
            final long _tmpPostTimeMs;
            _tmpPostTimeMs = _cursor.getLong(_cursorIndexOfPostTimeMs);
            final NotificationStatus _tmpStatus;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfStatus);
            _tmpStatus = __converters.toNS(_tmp);
            final String _tmpContentIntentData;
            if (_cursor.isNull(_cursorIndexOfContentIntentData)) {
              _tmpContentIntentData = null;
            } else {
              _tmpContentIntentData = _cursor.getString(_cursorIndexOfContentIntentData);
            }
            final boolean _tmpIsGroupSummary;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsGroupSummary);
            _tmpIsGroupSummary = _tmp_1 != 0;
            final boolean _tmpIsDemo;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsDemo);
            _tmpIsDemo = _tmp_2 != 0;
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final String _tmpAppLabel;
            _tmpAppLabel = _cursor.getString(_cursorIndexOfAppLabel);
            final String _tmpSbnKey;
            if (_cursor.isNull(_cursorIndexOfSbnKey)) {
              _tmpSbnKey = null;
            } else {
              _tmpSbnKey = _cursor.getString(_cursorIndexOfSbnKey);
            }
            final int _tmpGroupCount;
            _tmpGroupCount = _cursor.getInt(_cursorIndexOfGroupCount);
            final long _tmpLastUpdatedMs;
            _tmpLastUpdatedMs = _cursor.getLong(_cursorIndexOfLastUpdatedMs);
            final boolean _tmpWasSuppressed;
            final int _tmp_3;
            _tmp_3 = _cursor.getInt(_cursorIndexOfWasSuppressed);
            _tmpWasSuppressed = _tmp_3 != 0;
            final boolean _tmpSourceRemoved;
            final int _tmp_4;
            _tmp_4 = _cursor.getInt(_cursorIndexOfSourceRemoved);
            _tmpSourceRemoved = _tmp_4 != 0;
            final String _tmpRuleApplied;
            if (_cursor.isNull(_cursorIndexOfRuleApplied)) {
              _tmpRuleApplied = null;
            } else {
              _tmpRuleApplied = _cursor.getString(_cursorIndexOfRuleApplied);
            }
            final String _tmpAndroidCategory;
            if (_cursor.isNull(_cursorIndexOfAndroidCategory)) {
              _tmpAndroidCategory = null;
            } else {
              _tmpAndroidCategory = _cursor.getString(_cursorIndexOfAndroidCategory);
            }
            final Long _tmpStatusChangedMs;
            if (_cursor.isNull(_cursorIndexOfStatusChangedMs)) {
              _tmpStatusChangedMs = null;
            } else {
              _tmpStatusChangedMs = _cursor.getLong(_cursorIndexOfStatusChangedMs);
            }
            _result = new QueuedNotification(_tmpId,_tmpSessionId,_tmpPackageName,_tmpCategory,_tmpTitle,_tmpPreviewText,_tmpDedupKey,_tmpPostTimeMs,_tmpStatus,_tmpContentIntentData,_tmpIsGroupSummary,_tmpIsDemo,_tmpCreatedAt,_tmpAppLabel,_tmpSbnKey,_tmpGroupCount,_tmpLastUpdatedMs,_tmpWasSuppressed,_tmpSourceRemoved,_tmpRuleApplied,_tmpAndroidCategory,_tmpStatusChangedMs);
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
  public Object findByDedupKey(final String key,
      final Continuation<? super QueuedNotification> $completion) {
    final String _sql = "SELECT * FROM queued_notifications WHERE dedupKey=? LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, key);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<QueuedNotification>() {
      @Override
      @Nullable
      public QueuedNotification call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSessionId = CursorUtil.getColumnIndexOrThrow(_cursor, "sessionId");
          final int _cursorIndexOfPackageName = CursorUtil.getColumnIndexOrThrow(_cursor, "packageName");
          final int _cursorIndexOfCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "category");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfPreviewText = CursorUtil.getColumnIndexOrThrow(_cursor, "previewText");
          final int _cursorIndexOfDedupKey = CursorUtil.getColumnIndexOrThrow(_cursor, "dedupKey");
          final int _cursorIndexOfPostTimeMs = CursorUtil.getColumnIndexOrThrow(_cursor, "postTimeMs");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfContentIntentData = CursorUtil.getColumnIndexOrThrow(_cursor, "contentIntentData");
          final int _cursorIndexOfIsGroupSummary = CursorUtil.getColumnIndexOrThrow(_cursor, "isGroupSummary");
          final int _cursorIndexOfIsDemo = CursorUtil.getColumnIndexOrThrow(_cursor, "isDemo");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfAppLabel = CursorUtil.getColumnIndexOrThrow(_cursor, "appLabel");
          final int _cursorIndexOfSbnKey = CursorUtil.getColumnIndexOrThrow(_cursor, "sbnKey");
          final int _cursorIndexOfGroupCount = CursorUtil.getColumnIndexOrThrow(_cursor, "groupCount");
          final int _cursorIndexOfLastUpdatedMs = CursorUtil.getColumnIndexOrThrow(_cursor, "lastUpdatedMs");
          final int _cursorIndexOfWasSuppressed = CursorUtil.getColumnIndexOrThrow(_cursor, "wasSuppressed");
          final int _cursorIndexOfSourceRemoved = CursorUtil.getColumnIndexOrThrow(_cursor, "sourceRemoved");
          final int _cursorIndexOfRuleApplied = CursorUtil.getColumnIndexOrThrow(_cursor, "ruleApplied");
          final int _cursorIndexOfAndroidCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "androidCategory");
          final int _cursorIndexOfStatusChangedMs = CursorUtil.getColumnIndexOrThrow(_cursor, "statusChangedMs");
          final QueuedNotification _result;
          if (_cursor.moveToFirst()) {
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpSessionId;
            _tmpSessionId = _cursor.getString(_cursorIndexOfSessionId);
            final String _tmpPackageName;
            _tmpPackageName = _cursor.getString(_cursorIndexOfPackageName);
            final String _tmpCategory;
            _tmpCategory = _cursor.getString(_cursorIndexOfCategory);
            final String _tmpTitle;
            if (_cursor.isNull(_cursorIndexOfTitle)) {
              _tmpTitle = null;
            } else {
              _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            }
            final String _tmpPreviewText;
            if (_cursor.isNull(_cursorIndexOfPreviewText)) {
              _tmpPreviewText = null;
            } else {
              _tmpPreviewText = _cursor.getString(_cursorIndexOfPreviewText);
            }
            final String _tmpDedupKey;
            _tmpDedupKey = _cursor.getString(_cursorIndexOfDedupKey);
            final long _tmpPostTimeMs;
            _tmpPostTimeMs = _cursor.getLong(_cursorIndexOfPostTimeMs);
            final NotificationStatus _tmpStatus;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfStatus);
            _tmpStatus = __converters.toNS(_tmp);
            final String _tmpContentIntentData;
            if (_cursor.isNull(_cursorIndexOfContentIntentData)) {
              _tmpContentIntentData = null;
            } else {
              _tmpContentIntentData = _cursor.getString(_cursorIndexOfContentIntentData);
            }
            final boolean _tmpIsGroupSummary;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsGroupSummary);
            _tmpIsGroupSummary = _tmp_1 != 0;
            final boolean _tmpIsDemo;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsDemo);
            _tmpIsDemo = _tmp_2 != 0;
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final String _tmpAppLabel;
            _tmpAppLabel = _cursor.getString(_cursorIndexOfAppLabel);
            final String _tmpSbnKey;
            if (_cursor.isNull(_cursorIndexOfSbnKey)) {
              _tmpSbnKey = null;
            } else {
              _tmpSbnKey = _cursor.getString(_cursorIndexOfSbnKey);
            }
            final int _tmpGroupCount;
            _tmpGroupCount = _cursor.getInt(_cursorIndexOfGroupCount);
            final long _tmpLastUpdatedMs;
            _tmpLastUpdatedMs = _cursor.getLong(_cursorIndexOfLastUpdatedMs);
            final boolean _tmpWasSuppressed;
            final int _tmp_3;
            _tmp_3 = _cursor.getInt(_cursorIndexOfWasSuppressed);
            _tmpWasSuppressed = _tmp_3 != 0;
            final boolean _tmpSourceRemoved;
            final int _tmp_4;
            _tmp_4 = _cursor.getInt(_cursorIndexOfSourceRemoved);
            _tmpSourceRemoved = _tmp_4 != 0;
            final String _tmpRuleApplied;
            if (_cursor.isNull(_cursorIndexOfRuleApplied)) {
              _tmpRuleApplied = null;
            } else {
              _tmpRuleApplied = _cursor.getString(_cursorIndexOfRuleApplied);
            }
            final String _tmpAndroidCategory;
            if (_cursor.isNull(_cursorIndexOfAndroidCategory)) {
              _tmpAndroidCategory = null;
            } else {
              _tmpAndroidCategory = _cursor.getString(_cursorIndexOfAndroidCategory);
            }
            final Long _tmpStatusChangedMs;
            if (_cursor.isNull(_cursorIndexOfStatusChangedMs)) {
              _tmpStatusChangedMs = null;
            } else {
              _tmpStatusChangedMs = _cursor.getLong(_cursorIndexOfStatusChangedMs);
            }
            _result = new QueuedNotification(_tmpId,_tmpSessionId,_tmpPackageName,_tmpCategory,_tmpTitle,_tmpPreviewText,_tmpDedupKey,_tmpPostTimeMs,_tmpStatus,_tmpContentIntentData,_tmpIsGroupSummary,_tmpIsDemo,_tmpCreatedAt,_tmpAppLabel,_tmpSbnKey,_tmpGroupCount,_tmpLastUpdatedMs,_tmpWasSuppressed,_tmpSourceRemoved,_tmpRuleApplied,_tmpAndroidCategory,_tmpStatusChangedMs);
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
  public Object findPendingFromPackage(final String pkg, final String sessionId,
      final Continuation<? super QueuedNotification> $completion) {
    final String _sql = "SELECT * FROM queued_notifications WHERE packageName=? AND sessionId=? AND status='PENDING' AND isDemo=0 ORDER BY lastUpdatedMs DESC LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindString(_argIndex, pkg);
    _argIndex = 2;
    _statement.bindString(_argIndex, sessionId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<QueuedNotification>() {
      @Override
      @Nullable
      public QueuedNotification call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSessionId = CursorUtil.getColumnIndexOrThrow(_cursor, "sessionId");
          final int _cursorIndexOfPackageName = CursorUtil.getColumnIndexOrThrow(_cursor, "packageName");
          final int _cursorIndexOfCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "category");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfPreviewText = CursorUtil.getColumnIndexOrThrow(_cursor, "previewText");
          final int _cursorIndexOfDedupKey = CursorUtil.getColumnIndexOrThrow(_cursor, "dedupKey");
          final int _cursorIndexOfPostTimeMs = CursorUtil.getColumnIndexOrThrow(_cursor, "postTimeMs");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfContentIntentData = CursorUtil.getColumnIndexOrThrow(_cursor, "contentIntentData");
          final int _cursorIndexOfIsGroupSummary = CursorUtil.getColumnIndexOrThrow(_cursor, "isGroupSummary");
          final int _cursorIndexOfIsDemo = CursorUtil.getColumnIndexOrThrow(_cursor, "isDemo");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfAppLabel = CursorUtil.getColumnIndexOrThrow(_cursor, "appLabel");
          final int _cursorIndexOfSbnKey = CursorUtil.getColumnIndexOrThrow(_cursor, "sbnKey");
          final int _cursorIndexOfGroupCount = CursorUtil.getColumnIndexOrThrow(_cursor, "groupCount");
          final int _cursorIndexOfLastUpdatedMs = CursorUtil.getColumnIndexOrThrow(_cursor, "lastUpdatedMs");
          final int _cursorIndexOfWasSuppressed = CursorUtil.getColumnIndexOrThrow(_cursor, "wasSuppressed");
          final int _cursorIndexOfSourceRemoved = CursorUtil.getColumnIndexOrThrow(_cursor, "sourceRemoved");
          final int _cursorIndexOfRuleApplied = CursorUtil.getColumnIndexOrThrow(_cursor, "ruleApplied");
          final int _cursorIndexOfAndroidCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "androidCategory");
          final int _cursorIndexOfStatusChangedMs = CursorUtil.getColumnIndexOrThrow(_cursor, "statusChangedMs");
          final QueuedNotification _result;
          if (_cursor.moveToFirst()) {
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpSessionId;
            _tmpSessionId = _cursor.getString(_cursorIndexOfSessionId);
            final String _tmpPackageName;
            _tmpPackageName = _cursor.getString(_cursorIndexOfPackageName);
            final String _tmpCategory;
            _tmpCategory = _cursor.getString(_cursorIndexOfCategory);
            final String _tmpTitle;
            if (_cursor.isNull(_cursorIndexOfTitle)) {
              _tmpTitle = null;
            } else {
              _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            }
            final String _tmpPreviewText;
            if (_cursor.isNull(_cursorIndexOfPreviewText)) {
              _tmpPreviewText = null;
            } else {
              _tmpPreviewText = _cursor.getString(_cursorIndexOfPreviewText);
            }
            final String _tmpDedupKey;
            _tmpDedupKey = _cursor.getString(_cursorIndexOfDedupKey);
            final long _tmpPostTimeMs;
            _tmpPostTimeMs = _cursor.getLong(_cursorIndexOfPostTimeMs);
            final NotificationStatus _tmpStatus;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfStatus);
            _tmpStatus = __converters.toNS(_tmp);
            final String _tmpContentIntentData;
            if (_cursor.isNull(_cursorIndexOfContentIntentData)) {
              _tmpContentIntentData = null;
            } else {
              _tmpContentIntentData = _cursor.getString(_cursorIndexOfContentIntentData);
            }
            final boolean _tmpIsGroupSummary;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsGroupSummary);
            _tmpIsGroupSummary = _tmp_1 != 0;
            final boolean _tmpIsDemo;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsDemo);
            _tmpIsDemo = _tmp_2 != 0;
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final String _tmpAppLabel;
            _tmpAppLabel = _cursor.getString(_cursorIndexOfAppLabel);
            final String _tmpSbnKey;
            if (_cursor.isNull(_cursorIndexOfSbnKey)) {
              _tmpSbnKey = null;
            } else {
              _tmpSbnKey = _cursor.getString(_cursorIndexOfSbnKey);
            }
            final int _tmpGroupCount;
            _tmpGroupCount = _cursor.getInt(_cursorIndexOfGroupCount);
            final long _tmpLastUpdatedMs;
            _tmpLastUpdatedMs = _cursor.getLong(_cursorIndexOfLastUpdatedMs);
            final boolean _tmpWasSuppressed;
            final int _tmp_3;
            _tmp_3 = _cursor.getInt(_cursorIndexOfWasSuppressed);
            _tmpWasSuppressed = _tmp_3 != 0;
            final boolean _tmpSourceRemoved;
            final int _tmp_4;
            _tmp_4 = _cursor.getInt(_cursorIndexOfSourceRemoved);
            _tmpSourceRemoved = _tmp_4 != 0;
            final String _tmpRuleApplied;
            if (_cursor.isNull(_cursorIndexOfRuleApplied)) {
              _tmpRuleApplied = null;
            } else {
              _tmpRuleApplied = _cursor.getString(_cursorIndexOfRuleApplied);
            }
            final String _tmpAndroidCategory;
            if (_cursor.isNull(_cursorIndexOfAndroidCategory)) {
              _tmpAndroidCategory = null;
            } else {
              _tmpAndroidCategory = _cursor.getString(_cursorIndexOfAndroidCategory);
            }
            final Long _tmpStatusChangedMs;
            if (_cursor.isNull(_cursorIndexOfStatusChangedMs)) {
              _tmpStatusChangedMs = null;
            } else {
              _tmpStatusChangedMs = _cursor.getLong(_cursorIndexOfStatusChangedMs);
            }
            _result = new QueuedNotification(_tmpId,_tmpSessionId,_tmpPackageName,_tmpCategory,_tmpTitle,_tmpPreviewText,_tmpDedupKey,_tmpPostTimeMs,_tmpStatus,_tmpContentIntentData,_tmpIsGroupSummary,_tmpIsDemo,_tmpCreatedAt,_tmpAppLabel,_tmpSbnKey,_tmpGroupCount,_tmpLastUpdatedMs,_tmpWasSuppressed,_tmpSourceRemoved,_tmpRuleApplied,_tmpAndroidCategory,_tmpStatusChangedMs);
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
  public Flow<List<QueuedNotification>> observeRecent() {
    final String _sql = "SELECT * FROM queued_notifications WHERE isDemo=0 ORDER BY postTimeMs DESC LIMIT 50";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"queued_notifications"}, new Callable<List<QueuedNotification>>() {
      @Override
      @NonNull
      public List<QueuedNotification> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSessionId = CursorUtil.getColumnIndexOrThrow(_cursor, "sessionId");
          final int _cursorIndexOfPackageName = CursorUtil.getColumnIndexOrThrow(_cursor, "packageName");
          final int _cursorIndexOfCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "category");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfPreviewText = CursorUtil.getColumnIndexOrThrow(_cursor, "previewText");
          final int _cursorIndexOfDedupKey = CursorUtil.getColumnIndexOrThrow(_cursor, "dedupKey");
          final int _cursorIndexOfPostTimeMs = CursorUtil.getColumnIndexOrThrow(_cursor, "postTimeMs");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfContentIntentData = CursorUtil.getColumnIndexOrThrow(_cursor, "contentIntentData");
          final int _cursorIndexOfIsGroupSummary = CursorUtil.getColumnIndexOrThrow(_cursor, "isGroupSummary");
          final int _cursorIndexOfIsDemo = CursorUtil.getColumnIndexOrThrow(_cursor, "isDemo");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfAppLabel = CursorUtil.getColumnIndexOrThrow(_cursor, "appLabel");
          final int _cursorIndexOfSbnKey = CursorUtil.getColumnIndexOrThrow(_cursor, "sbnKey");
          final int _cursorIndexOfGroupCount = CursorUtil.getColumnIndexOrThrow(_cursor, "groupCount");
          final int _cursorIndexOfLastUpdatedMs = CursorUtil.getColumnIndexOrThrow(_cursor, "lastUpdatedMs");
          final int _cursorIndexOfWasSuppressed = CursorUtil.getColumnIndexOrThrow(_cursor, "wasSuppressed");
          final int _cursorIndexOfSourceRemoved = CursorUtil.getColumnIndexOrThrow(_cursor, "sourceRemoved");
          final int _cursorIndexOfRuleApplied = CursorUtil.getColumnIndexOrThrow(_cursor, "ruleApplied");
          final int _cursorIndexOfAndroidCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "androidCategory");
          final int _cursorIndexOfStatusChangedMs = CursorUtil.getColumnIndexOrThrow(_cursor, "statusChangedMs");
          final List<QueuedNotification> _result = new ArrayList<QueuedNotification>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final QueuedNotification _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpSessionId;
            _tmpSessionId = _cursor.getString(_cursorIndexOfSessionId);
            final String _tmpPackageName;
            _tmpPackageName = _cursor.getString(_cursorIndexOfPackageName);
            final String _tmpCategory;
            _tmpCategory = _cursor.getString(_cursorIndexOfCategory);
            final String _tmpTitle;
            if (_cursor.isNull(_cursorIndexOfTitle)) {
              _tmpTitle = null;
            } else {
              _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            }
            final String _tmpPreviewText;
            if (_cursor.isNull(_cursorIndexOfPreviewText)) {
              _tmpPreviewText = null;
            } else {
              _tmpPreviewText = _cursor.getString(_cursorIndexOfPreviewText);
            }
            final String _tmpDedupKey;
            _tmpDedupKey = _cursor.getString(_cursorIndexOfDedupKey);
            final long _tmpPostTimeMs;
            _tmpPostTimeMs = _cursor.getLong(_cursorIndexOfPostTimeMs);
            final NotificationStatus _tmpStatus;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfStatus);
            _tmpStatus = __converters.toNS(_tmp);
            final String _tmpContentIntentData;
            if (_cursor.isNull(_cursorIndexOfContentIntentData)) {
              _tmpContentIntentData = null;
            } else {
              _tmpContentIntentData = _cursor.getString(_cursorIndexOfContentIntentData);
            }
            final boolean _tmpIsGroupSummary;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsGroupSummary);
            _tmpIsGroupSummary = _tmp_1 != 0;
            final boolean _tmpIsDemo;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsDemo);
            _tmpIsDemo = _tmp_2 != 0;
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final String _tmpAppLabel;
            _tmpAppLabel = _cursor.getString(_cursorIndexOfAppLabel);
            final String _tmpSbnKey;
            if (_cursor.isNull(_cursorIndexOfSbnKey)) {
              _tmpSbnKey = null;
            } else {
              _tmpSbnKey = _cursor.getString(_cursorIndexOfSbnKey);
            }
            final int _tmpGroupCount;
            _tmpGroupCount = _cursor.getInt(_cursorIndexOfGroupCount);
            final long _tmpLastUpdatedMs;
            _tmpLastUpdatedMs = _cursor.getLong(_cursorIndexOfLastUpdatedMs);
            final boolean _tmpWasSuppressed;
            final int _tmp_3;
            _tmp_3 = _cursor.getInt(_cursorIndexOfWasSuppressed);
            _tmpWasSuppressed = _tmp_3 != 0;
            final boolean _tmpSourceRemoved;
            final int _tmp_4;
            _tmp_4 = _cursor.getInt(_cursorIndexOfSourceRemoved);
            _tmpSourceRemoved = _tmp_4 != 0;
            final String _tmpRuleApplied;
            if (_cursor.isNull(_cursorIndexOfRuleApplied)) {
              _tmpRuleApplied = null;
            } else {
              _tmpRuleApplied = _cursor.getString(_cursorIndexOfRuleApplied);
            }
            final String _tmpAndroidCategory;
            if (_cursor.isNull(_cursorIndexOfAndroidCategory)) {
              _tmpAndroidCategory = null;
            } else {
              _tmpAndroidCategory = _cursor.getString(_cursorIndexOfAndroidCategory);
            }
            final Long _tmpStatusChangedMs;
            if (_cursor.isNull(_cursorIndexOfStatusChangedMs)) {
              _tmpStatusChangedMs = null;
            } else {
              _tmpStatusChangedMs = _cursor.getLong(_cursorIndexOfStatusChangedMs);
            }
            _item = new QueuedNotification(_tmpId,_tmpSessionId,_tmpPackageName,_tmpCategory,_tmpTitle,_tmpPreviewText,_tmpDedupKey,_tmpPostTimeMs,_tmpStatus,_tmpContentIntentData,_tmpIsGroupSummary,_tmpIsDemo,_tmpCreatedAt,_tmpAppLabel,_tmpSbnKey,_tmpGroupCount,_tmpLastUpdatedMs,_tmpWasSuppressed,_tmpSourceRemoved,_tmpRuleApplied,_tmpAndroidCategory,_tmpStatusChangedMs);
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
  public Flow<Integer> observePendingCount() {
    final String _sql = "SELECT COUNT(*) FROM queued_notifications WHERE status='PENDING'";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"queued_notifications"}, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
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
