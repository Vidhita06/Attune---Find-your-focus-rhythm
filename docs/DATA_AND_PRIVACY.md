# Data, migrations and privacy

## Where data lives

Everything stays on the device:
- Room database `attune.db`
- DataStore `attune_settings` for settings, allowed apps and priority contacts

The app has no network code, analytics SDK, backend or API keys.

## Backup and device transfer (Phase 1)

- `android:allowBackup="false"`.
- `android:dataExtractionRules` (Android 12+) excludes the `root`, `file`, `database`, `sharedpref` and `external` domains from **both** `cloud-backup` and `device-transfer` (`res/xml/data_extraction_rules.xml`). Before this phase only `attune.db` was excluded from device transfer; the DataStore settings file (priority-contact names, allowed apps) was not.
- `android:fullBackupContent` (Android 6–11) points to `res/xml/backup_rules.xml`, which excludes every domain.
- The sender-id key lives in Android Keystore and is never exported or backed up.
- **Verified:** the rules are in the built APK (see the build section of the Phase 1 report).
- **Not verified:** an actual backup/restore or device-to-device transfer on a device (NOT TESTED). Android may apply OEM-specific behaviour.

## Schema v3 (migration 2 → 3, additive only)

| Table | New in v3 | Contents |
|---|---|---|
| `notification_decisions` | yes | One row per evaluated notification, keyed by `dedupKey` (unique), so updates increment `updateCount` instead of adding rows. It holds: app, category, time, phase, routing, focus risk, probabilities, confidence, personal bias, source, generic reasons, link verdict, sensitivity, hashed sender, rule override and queue link. **No title or text.** |
| `feedback_events` | yes | Feedback kind, app, category, hashed sender plus masked hint, and the prediction at the time |
| `user_rules` | yes | ALLOW and DEFER rules for app, contact, keyword and category, with their source (manual or feedback) |
| `session_events` | yes | Timeline entries: start, pause, resume, end, held, allowed, review, opened and energy. The detail field holds only an app name or numbers |
| `queued_notifications` | +5 columns | `decisionId`, `focusRisk`, `isSensitive`, `keptVisible`, `textScrubbed` |
| `energy_checkins` | +2 columns | `score` (1–5, backfilled LOW→2, MODERATE→3, HIGH→4) and `phase` (BEFORE / AFTER / DURING) |

**About the migration:**
- `MIGRATION_2_3` only runs `ALTER TABLE … ADD COLUMN`, `CREATE TABLE/INDEX IF NOT EXISTS` and one `UPDATE` backfill.
- No destructive fallback is configured.
- The SQL lives in `Migrations.SQL_2_3`.
- **Tested:** `core-model/src/test/.../MigrationTest.kt` builds a real v2 SQLite database from `schemas/.../2.json` with sample rows, runs the migration, and checks that:
  - every table matches `3.json` in columns, types, NOT NULL, defaults, primary keys and indices;
  - all rows survived;
  - the energy backfill is correct.
- **Not tested:** an on-device upgrade from an installed v2 APK has not been done (see HUMAN_TASKS).

## Schema v4 (migration 3 → 4, data only)

No table or column changes. `MIGRATION_3_4` (`Migrations.SQL_3_4`) sets `senderKey = NULL` in `notification_decisions` and `feedback_events`, because earlier ids were an unsalted SHA-256 of `package|name` that can be guessed from a contact list. Names were never stored, so old ids cannot be re-derived.
- **Kept:** every row, queue item, rule, app- and category-level learned preference, feedback event.
- **Lost:** *sender-level* learned preferences (the lowest-weighted scope, 0.5). They start again from zero as new feedback arrives.
- New ids are `HMAC-SHA256(per-install key, package|name)`, truncated. The key is generated in Android Keystore (`KeystoreSenderHasher`). If Keystore is unusable, no sender id is produced and sender-level personalization is off; there is no weaker fallback.
- **Tested:** `MigrationTest` (v3 schema built from `3.json`, rows inserted, SQL run, rows verified, v4 schema equal to v3).

## Notification text retention

| Data | What is kept |
|---|---|
| Decision log | No text, ever |
| Sensitive notifications (one-time codes, security alerts, finance/money wording) | **No title or preview is written to the database**, only app, category and the sensitivity marker (`isSensitive`, shown as "content removed"). The text is used in memory to route the notification and then dropped |
| Queue items (held or review) | Title and preview, so you can read them in Attune. "Show now" posts a replacement notification with that text, only while it is still stored and not sensitive; the replacement is owned by Android's notification system like any notification |
| Handled items (opened / dismissed) | Text removed 24 hours after the action |
| Pending / delayed items nobody opened | Text removed 24 hours after the **focus session ended** (ended or completed). If the session row no longer exists, 24 hours after the last update. Backstop: anything not updated for 7 days, whatever the session state |
| Retention settings | `DecisionPolicy.textRetentionHours` (24) and `maxPendingTextDays` (7); a code-level setting, no UI |
| When cleanup runs | When the app is opened, when the notification listener connects, and at most once an hour while the listener receives notifications. It does not run while the process is dead and the listener is unbound; it runs on the next start. The row (app, category, time) stays; only title and preview go |
| Senders | Keyed HMAC (see schema v4) plus a masked hint like `R••••l` (first and last character) |
| In-memory only | Burst counters (per conversation, 2-minute window), pending-removal bookkeeping, listener diagnostics. Never persisted |
| "Words that suggested importance" | Computed on screen from the stored preview; never persisted; never computed for sensitive items |
| Shared report summary | Counts only |
| Logging | The only log call is a debug-build line in the listener: package, focus state, routing, save and cancel outcome and a generic reason. No titles, text, codes, senders or URLs |
| Not covered | Priority-contact names and keyword rules you type are stored as you typed them (DataStore / Room `user_rules`) so they can be matched. They are excluded from backup and transfer but not hashed; *Delete rules* removes the Room rules, and contacts are removed in Settings |

## Analytics definitions (reports and profile)

- **Focus time and sessions:** attributed to the bucket where the session *started*, in the device's local timezone. Weeks start on Monday.
- **Received during focus:** decision rows with phase FOCUS. Each Android notification counts once.
- **Held:** DEFER plus FILTER.
- **Kept visible:** REVIEW.
- **Let through:** DELIVER.
- **Opened / interruptions:**
  - Counts successful opens from Attune (failed launch attempts are excluded), and interruptions are those during FOCUS.
  - Opens made from the shade can't be observed, so these are lower bounds.
- **Comparison with the previous period:** an in-progress period is compared with the *same elapsed span* of the previous one, never with a full period.
- **Energy:** self-reported, 1–5, non-medical. Comparisons with session outcomes are only shown with at least 2 sessions in each group, and are labelled as correlations.
- **Focus score:** a weekly progress indicator, not a measure of attention, built from three parts that are always shown:
  - focus time against a target: up to 40 points
  - share of sessions completed as planned: up to 30 points
  - "calm", which is fewer opens per focus hour: up to 30 points
- **Streaks:**
  - With a daily goal, a day counts if the goal was met. Without one, a day counts if at least one session was completed.
  - Today never breaks a streak.

## Demo data

- Demo rows carry `isDemo = 1` in every table.
- They are shown only when demo mode is on, always with DEMO badges.
- `AnalyticsInput.select(..., demo)` is used so reports and the profile never mix demo and real rows.
- Demo feedback personalizes only demo samples.
- *Reset demo data* deletes only demo rows.
- Demo decisions are produced by the real decision engine, so demo mode shows the AI's actual behaviour.
- Demo data is never used for model training or evaluation. The model is trained only on `ml/data/notifications.csv`.

## Privacy center actions

Each action affects real data only. Settings and demo data are never touched.
- Remove stored notification text now.
- Delete feedback and reset personalization (or delete preferences one by one under *Learned preferences*).
- Delete activity and analytics: finished sessions, decisions, queue history, opens, time-cost reports and timelines. A running session is kept.
- Delete energy check-ins.
- Delete rules.
- Delete everything.

## Permissions

| Permission | Why |
|---|---|
| Notification access (`BIND_NOTIFICATION_LISTENER_SERVICE`, granted by the user in Settings) | Read and hold notifications during focus |
| `POST_NOTIFICATIONS` | The timer notification and the session summary |
| `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_SPECIAL_USE` | Keep the focus timer accurate in the background |
| Launcher `<queries>` | List apps for the allow-list, and open the app when "Open now" falls back |
