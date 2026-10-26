package com.attune.core.data.db

import com.attune.core.data.db.dao.RetentionSql
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * Builds a real v2 database from the exported Room schema (schemas/.../2.json), fills it with data, runs the exact
 * v2 -> v3 migration SQL, then checks every table against schemas/.../3.json the way Room validates it
 * (column names, affinity, NOT NULL, default value, primary key, indices) and that existing rows survived.
 */
class MigrationTest {
    private val schemaDir = File("schemas/com.attune.core.data.db.AttuneDatabase")
    private fun schema(v: Int) = JsonParser.parseString(File(schemaDir, "$v.json").readText()).asJsonObject["database"].asJsonObject
    private fun JsonObject.entities() = this["entities"].asJsonArray.map { it.asJsonObject }

    private fun createV2(c: Connection) = createSchema(c, 2)
    private fun createSchema(c: Connection, version: Int) = c.createStatement().use { st ->
        for (e in schema(version).entities()) {
            val t = e["tableName"].asString
            st.execute(e["createSql"].asString.replace("\${TABLE_NAME}", t))
            e["indices"]?.asJsonArray?.forEach { st.execute(it.asJsonObject["createSql"].asString.replace("\${TABLE_NAME}", t)) }
        }
    }

    private fun Connection.exec(sql: String) = createStatement().use { it.execute(sql) }
    private fun Connection.long(sql: String): Long = createStatement().use { s -> s.executeQuery(sql).use { it.next(); it.getLong(1) } }
    private fun Connection.str(sql: String): String? = createStatement().use { s -> s.executeQuery(sql).use { it.next(); it.getString(1) } }

    @Test fun migrate2to3PreservesDataAndMatchesSchema() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { c ->
            createV2(c)
            c.exec("INSERT INTO focus_sessions (id, sessionStartMs, plannedEndMs, actualEndMs, state, breakStartMs, isDemo, createdAt, plannedDurationMin, totalPausedMs, pauseCount, interruptionCount) VALUES ('s1', 1000, 2000, 1900, 'COMPLETED', NULL, 0, 1000, 25, 0, 1, 2)")
            c.exec("INSERT INTO queued_notifications (id, sessionId, packageName, category, title, previewText, dedupKey, postTimeMs, status, contentIntentData, isGroupSummary, isDemo, createdAt, appLabel, sbnKey, groupCount, lastUpdatedMs, wasSuppressed, sourceRemoved, ruleApplied, androidCategory, statusChangedMs) VALUES ('n1', 's1', 'com.whatsapp', 'messaging', 'Mom', 'hello', 'sbn:k1', 1500, 'DELAYED', NULL, 0, 0, 1500, 'WhatsApp', 'k1', 1, 1500, 1, 0, 'Queued', NULL, 1600)")
            c.exec("INSERT INTO energy_checkins (id, level, timestampMs, sessionId, isDemo) VALUES ('e1', 'LOW', 10, NULL, 0), ('e2', 'HIGH', 20, 's1', 0), ('e3', 'MODERATE', 30, NULL, 1)")
            c.exec("INSERT INTO user_actions (id, notificationId, predictionId, actionType, actionTimeMs, isDemo, launchAttempted) VALUES ('a1', 'n1', NULL, 'DELAYED', 1600, 0, 0)")
            c.exec("INSERT INTO feedback (id, notificationId, actionId, reportedTimeAwayMin, reportedRecoveryMin, wasSkipped, isDemo, createdAt) VALUES ('f1', 'n1', 'a1', 5.0, 2.0, 0, 0, 1700)")

            Migrations.SQL_2_3.forEach { c.exec(it) }

            // existing data intact
            assertEquals(1, c.long("SELECT COUNT(*) FROM focus_sessions WHERE id='s1' AND state='COMPLETED' AND interruptionCount=2"))
            assertEquals(1, c.long("SELECT COUNT(*) FROM queued_notifications WHERE id='n1' AND title='Mom' AND status='DELAYED' AND keptVisible=0 AND textScrubbed=0 AND isSensitive=0 AND decisionId IS NULL"))
            assertEquals(1, c.long("SELECT COUNT(*) FROM user_actions")); assertEquals(1, c.long("SELECT COUNT(*) FROM feedback"))
            assertEquals(2, c.long("SELECT score FROM energy_checkins WHERE id='e1'"))
            assertEquals(4, c.long("SELECT score FROM energy_checkins WHERE id='e2'"))
            assertEquals(3, c.long("SELECT score FROM energy_checkins WHERE id='e3'"))
            assertNull(c.str("SELECT phase FROM energy_checkins WHERE id='e1'"))

            // schema equals the v3 schema Room expects
            for (e in schema(3).entities()) {
                val t = e["tableName"].asString
                val actual = c.createStatement().use { s -> s.executeQuery("PRAGMA table_info(`$t`)").use { rs ->
                    buildMap { while (rs.next()) put(rs.getString("name"), listOf(affinity(rs.getString("type")), rs.getInt("notnull") == 1, rs.getString("dflt_value")?.trim('\''), rs.getInt("pk"))) } } }
                val pk = e["primaryKey"].asJsonObject["columnNames"].asJsonArray.map { it.asString }
                val expected = e["fields"].asJsonArray.map { it.asJsonObject }.associate { f ->
                    val name = f["columnName"].asString
                    name to listOf(f["affinity"].asString, f["notNull"].asBoolean, f["defaultValue"]?.asString?.trim('\''), if (name in pk) pk.indexOf(name) + 1 else 0)
                }
                assertEquals("columns of $t", expected, actual)
                val idx = c.createStatement().use { s -> s.executeQuery("PRAGMA index_list(`$t`)").use { rs -> buildSet { while (rs.next()) add(rs.getString("name")) } } }
                e["indices"]?.asJsonArray?.forEach { assertTrue("index ${it.asJsonObject["name"].asString} on $t", it.asJsonObject["name"].asString in idx) }
            }
        }
    }

    @Test fun migrationSqlIsAdditiveOnly() {
        Migrations.SQL_2_3.forEach { sql ->
            val s = sql.uppercase()
            assertFalse("destructive statement: $sql", s.startsWith("DROP") || s.startsWith("DELETE") || " RENAME " in s)
        }
    }

    private fun affinity(type: String) = when (type.uppercase()) { "INTEGER" -> "INTEGER"; "REAL" -> "REAL"; "BLOB" -> "BLOB"; else -> "TEXT" }

    @Test fun migrate3to4ClearsLegacySenderKeysAndKeepsEverythingElse() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { c ->
            createSchema(c, 3)
            c.exec("INSERT INTO queued_notifications (id, sessionId, packageName, category, title, previewText, dedupKey, postTimeMs, status, isGroupSummary, isDemo, createdAt, appLabel, groupCount, lastUpdatedMs, wasSuppressed, sourceRemoved, isSensitive, keptVisible, textScrubbed) VALUES ('n1', 's1', 'com.whatsapp', 'messaging', 'Mom', 'hello', 'sbn:k1', 1500, 'PENDING', 0, 0, 1500, 'WhatsApp', 1, 1500, 1, 0, 0, 0, 0)")
            c.exec("INSERT INTO feedback_events (id, createdMs, kind, packageName, appLabel, category, senderKey, senderHint, notificationId, decisionId, predictedRisk, predictedRouting, isDemo) VALUES ('f1', 5, 'ACTUALLY_IMPORTANT', 'com.whatsapp', 'WhatsApp', 'messaging', 'aabbccddeeff001122334455', 'M•m', 'n1', NULL, 'LOW', 'DEFER', 0), ('f2', 6, 'NOT_IMPORTANT', 'com.whatsapp', 'WhatsApp', 'messaging', NULL, NULL, NULL, NULL, NULL, NULL, 0)")
            c.exec("INSERT INTO notification_decisions (id, dedupKey, packageName, appLabel, category, postedAtMs, decidedAtMs, sessionId, phase, routing, focusRisk, source, reasons, hasLink, linkSafety, isSensitive, senderKey, overrodeModel, isDemo, updateCount) VALUES ('d1', 'sbn:k1', 'com.whatsapp', 'WhatsApp', 'messaging', 1, 2, 's1', 'FOCUS', 'DEFER', 'LOW', 'MODEL', 'MODEL|x', 0, 'NONE', 0, 'aabbccddeeff001122334455', 0, 0, 1)")
            c.exec("INSERT INTO user_rules (id, kind, value, action, label, createdMs, source) VALUES ('r1', 'APP', 'com.x', 'ALLOW', 'x', 1, 'USER')")

            Migrations.SQL_3_4.forEach { c.exec(it) }

            assertEquals(0, c.long("SELECT COUNT(*) FROM feedback_events WHERE senderKey IS NOT NULL"))
            assertEquals(0, c.long("SELECT COUNT(*) FROM notification_decisions WHERE senderKey IS NOT NULL"))
            // nothing else is touched: rows, their other columns, and unrelated tables survive
            assertEquals(2, c.long("SELECT COUNT(*) FROM feedback_events")); assertEquals("M•m", c.str("SELECT senderHint FROM feedback_events WHERE id='f1'"))
            assertEquals("ACTUALLY_IMPORTANT", c.str("SELECT kind FROM feedback_events WHERE id='f1'"))
            assertEquals(1, c.long("SELECT COUNT(*) FROM notification_decisions WHERE id='d1' AND routing='DEFER'"))
            assertEquals(1, c.long("SELECT COUNT(*) FROM queued_notifications WHERE id='n1' AND title='Mom' AND previewText='hello'"))
            assertEquals(1, c.long("SELECT COUNT(*) FROM user_rules"))
            // the v4 schema has exactly the same tables and columns as v3 (the migration is data-only)
            assertEquals(schema(3).entities().map { it["tableName"].asString to it["fields"].toString() }, schema(4).entities().map { it["tableName"].asString to it["fields"].toString() })
        }
    }

    @Test fun migration3to4IsDataOnly() {
        Migrations.SQL_3_4.forEach { sql -> val s = sql.uppercase(); assertTrue(s.startsWith("UPDATE ") && "SENDERKEY = NULL" in s) }
    }

    @Test fun expiredTextIsRemovedForHandledAndNeverOpenedItems() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { c ->
            createSchema(c, 3)
            val h = 3_600_000L; val now = 1_000 * h; val cutoff = now - 24 * h; val hard = now - 7 * 24 * h
            fun session(id: String, state: String, end: Long?) = c.exec("INSERT INTO focus_sessions (id, sessionStartMs, plannedEndMs, actualEndMs, state, isDemo, createdAt) VALUES ('$id', 0, ${end ?: now}, ${end ?: "NULL"}, '$state', 0, 0)")
            fun item(id: String, session: String, status: String, post: Long, changed: String = "NULL", demo: Int = 0) = c.exec(
                "INSERT INTO queued_notifications (id, sessionId, packageName, category, title, previewText, dedupKey, postTimeMs, status, isGroupSummary, isDemo, createdAt, statusChangedMs, lastUpdatedMs) VALUES ('$id', '$session', 'p', 'messaging', 'T', 'secret', 'd$id', $post, '$status', 0, $demo, 0, $changed, $post)")
            session("old", "COMPLETED", cutoff - h); session("recent", "COMPLETED", now - h); session("live", "FOCUS", null)
            item("neverOpenedOld", "old", "PENDING", cutoff - 2 * h)       // pending, session ended > 24h ago -> expires
            item("delayedOld", "old", "DELAYED", cutoff - 2 * h)           // delayed -> expires too
            item("neverOpenedRecent", "recent", "PENDING", now - 2 * h)    // session ended 1h ago -> kept
            item("liveSession", "live", "PENDING", now - 3 * h)            // session still running -> kept
            item("handledOld", "recent", "DISMISSED", now - 50 * h, "${cutoff - h}") // handled > 24h ago -> expires
            item("handledRecent", "recent", "OPENED", now - 50 * h, "${now - h}")    // handled 1h ago -> kept
            item("orphanOld", "gone", "PENDING", cutoff - h)               // session row deleted -> expires
            item("orphanRecent", "gone", "PENDING", now - h)               // -> kept
            item("stuckSession", "live", "PENDING", hard - h)              // session never closed: backstop after 7 days
            item("demo", "old", "PENDING", cutoff - 2 * h, demo = 1)       // demo rows are not real data
            val sql = RetentionSql.SCRUB_EXPIRED.replace(":cutoffMs", "$cutoff").replace(":hardCutoffMs", "$hard")
            val n = c.createStatement().use { it.executeUpdate(sql) }
            val scrubbed = c.createStatement().use { s -> s.executeQuery("SELECT id FROM queued_notifications WHERE title IS NULL AND previewText IS NULL AND textScrubbed=1").use { rs -> buildSet { while (rs.next()) add(rs.getString(1)) } } }
            assertEquals(setOf("neverOpenedOld", "delayedOld", "handledOld", "orphanOld", "stuckSession"), scrubbed)
            assertEquals(5, n)
            assertEquals(5L, c.long("SELECT COUNT(*) FROM queued_notifications WHERE previewText='secret'"))
            assertEquals(10L, c.long("SELECT COUNT(*) FROM queued_notifications")) // rows (metadata) stay; only text goes
        }
    }

    @Test fun privacyDeletionsRemoveRealContentAndKeepDemoRows() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { c ->
            createSchema(c, 4)
            fun q(id: String, demo: Int) = c.exec("INSERT INTO queued_notifications (id, sessionId, packageName, category, title, previewText, dedupKey, postTimeMs, status, isGroupSummary, isDemo, createdAt) VALUES ('$id', 's', 'p', 'messaging', 'Mom', 'secret', 'd$id', 1, 'PENDING', 0, $demo, 0)")
            q("real", 0); q("demo", 1)
            c.exec("INSERT INTO feedback_events (id, createdMs, kind, packageName, appLabel, category, senderKey, senderHint, isDemo) VALUES ('fr', 1, 'NOT_IMPORTANT', 'p', 'P', 'messaging', 'abc', 'M•m', 0), ('fd', 1, 'NOT_IMPORTANT', 'p', 'P', 'messaging', 'abc', 'M•m', 1)")
            c.exec("INSERT INTO notification_decisions (id, dedupKey, packageName, appLabel, category, postedAtMs, decidedAtMs, phase, routing, focusRisk, source, reasons, hasLink, linkSafety, isSensitive, senderKey, overrodeModel, isDemo, updateCount) VALUES ('dr', 'k1', 'p', 'P', 'messaging', 1, 1, 'FOCUS', 'DEFER', 'LOW', 'MODEL', 'x', 0, 'NONE', 0, 'abc', 0, 0, 1), ('dd', 'k2', 'p', 'P', 'messaging', 1, 1, 'FOCUS', 'DEFER', 'LOW', 'MODEL', 'x', 0, 'NONE', 0, 'abc', 0, 1, 1)")

            c.exec(RetentionSql.SCRUB_ALL_REAL_TEXT) // "Remove notification text"
            assertEquals(0, c.long("SELECT COUNT(*) FROM queued_notifications WHERE isDemo=0 AND (title IS NOT NULL OR previewText IS NOT NULL)"))
            assertEquals(1, c.long("SELECT COUNT(*) FROM queued_notifications WHERE isDemo=0 AND textScrubbed=1"))
            assertEquals("secret", c.str("SELECT previewText FROM queued_notifications WHERE id='demo'"))

            c.exec(RetentionSql.DELETE_REAL_FEEDBACK_EVENTS) // "Delete learned preferences"
            assertEquals(0, c.long("SELECT COUNT(*) FROM feedback_events WHERE isDemo=0")); assertEquals(1, c.long("SELECT COUNT(*) FROM feedback_events WHERE isDemo=1"))

            c.exec(RetentionSql.DELETE_REAL_DECISIONS); c.exec(RetentionSql.DELETE_REAL_QUEUE) // "Delete activity"
            assertEquals(0, c.long("SELECT COUNT(*) FROM notification_decisions WHERE isDemo=0 OR (senderKey IS NOT NULL AND isDemo=0)"))
            assertEquals(0, c.long("SELECT COUNT(*) FROM queued_notifications WHERE isDemo=0"))
            assertEquals(1, c.long("SELECT COUNT(*) FROM queued_notifications WHERE isDemo=1")); assertEquals(1, c.long("SELECT COUNT(*) FROM notification_decisions WHERE isDemo=1"))
        }
    }
}
