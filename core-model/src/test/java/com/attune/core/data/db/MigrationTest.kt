package com.attune.core.data.db

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

    private fun createV2(c: Connection) = c.createStatement().use { st ->
        for (e in schema(2).entities()) {
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
}
