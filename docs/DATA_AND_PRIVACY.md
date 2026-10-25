# Data, migrations and privacy

## Where data lives

Everything stays on the device:
- Room database `attune.db`
- DataStore `attune_settings` for settings, allowed apps and priority contacts

`android:allowBackup="false"` is set. The app has no network code, analytics SDK, backend or API keys.

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

## Notification text retention

| Data | What is kept |
|---|---|
| Decision log | No text, ever |
| Queue items (held or review) | Title and preview, so you can read them in Attune. Removed 24 hours after the item is handled (checked each time the app resumes), or immediately from the Privacy center |
| Pending and delayed items | Text kept until handled |
| Senders | SHA-256 of package + normalised title, truncated, plus a masked hint like `R••••l` |
| "Words that suggested importance" | Computed on screen from the stored preview; never persisted; never computed for sensitive items |
| Shared report summary | Counts only |
| Logging | Attune contains no `Log.*` calls and never logs notification content |

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
