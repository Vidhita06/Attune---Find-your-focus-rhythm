# Notification routing fix: trace, causes and device checklist

## Traced code path (before the fix)

1. **`FocusListenerService.onNotificationPosted`**
   - Returned early for Attune's own package.
   - Extracted the title, text (big text first), category, flags, `sbn.key` and contentIntent, then launched a coroutine.
2. **Access and connection:** `onListenerConnected` and `onListenerDisconnected` only updated `NotificationAccessMonitor`. The service itself didn't track connection, so cancel calls were never gated on it.
3. **Focus state:** `FocusSessionRepository.getActive()` returns the newest real session in FOCUS, PAUSED or BREAK. Only FOCUS counts as "active".
4. **Decision:**
   - `DecisionRepository.evaluateAndRecord` calls `NotificationDecisionEngine.decide`, which runs rules → safety → model → fallback.
   - It writes one row to `notification_decisions` per `dedupKey` (`"sbn:" + sbn.key`).
5. **Queue:** `QueueRepository.ingest` upserts into `queued_notifications`. It returned `null` for a repeated callback with unchanged content.
6. **Cancel:**
   - Called `cancelNotification(key)` with the correct `sbn.key`, after `ingest` and only for DEFER (when "remove from shade" is on) or FILTER.
   - The result was treated as success if no exception was thrown.
7. **Removal callback:** keys Attune cancelled itself are ignored. App cancellations mark the queue item as "withdrawn".
8. **Queue UI:** `QueueRepository.items` reads the Room flow. Held items appear under "Held"; REVIEW items appear under "To review".

## Verified causes

| # | Cause | Effect |
|---|---|---|
| 1 | `ingest` returned `null` for a repeated callback with unchanged text, and the listener then did `?: return@launch` **before** reaching `cancelNotification` | Apps that re-post or refresh the same notification (same key, same text) put the original back in the shade and Attune never removed it again |
| 2 | Group summaries are correctly *not* evaluated, but nothing removed them after all their children were held | Bundles like "WhatsApp – 3 new messages" stayed visible, so it looked as if nothing was held |
| 3 | When the listener isn't bound, `cancelNotification` only logs a warning inside Android and returns normally | Attune recorded "suppressed" even though nothing was removed |
| 4 | Everything was inside `runCatching { }` with no reporting | Save, decision or cancel failures were invisible |
| 5 | AI Mode with the model missing or failing fell back to category rules | Messaging and email could be held without their text being read, contradicting "never silently hide possibly important messages" |
| 6 | **By design, not a bug:** very short (≤ 2 words), link-only and low-confidence messages are REVIEW, so the original is deliberately left visible | Many real chat notifications are short ("ok", "hi", a sticker), so they stay in the shade. The new diagnostics show this as `decision=REVIEW` |

`cancelNotification` was never called with the wrong key or before the save.

## Fix

- **`capture/NotificationProcessor.kt` (new, pure Kotlin, unit-tested)** fixes the order of operations:
  - ALLOW → never cancelled. REVIEW → saved, never cancelled.
  - HOLD/FILTER → saved first; the row is confirmed to exist (`QueueRepository.exists`); then removed with `cancel(sbn.key)` only if the listener is connected and the notification is clearable.
  - A repeated callback creates no new row but is removed again.
  - A group summary is removed only if Attune held a child of that group in the current session and no non-summary notification of the group is still active. Cancelling a summary also cancels its children in Android, so this check matters.
  - Every callback returns a privacy-safe `ProcessOutcome`. The processor never throws.
- **`capture/FocusListenerService.kt`**
  - Tracks `connected`; `cancel()` refuses to run while disconnected.
  - Implements `ListenerOps` with `cancelNotification` and `getActiveNotifications`.
  - Implements `ProcessorPorts` with the real repositories.
  - Never processes its own package, so Attune's summary notification can't loop.
  - Logs one line per callback in debug builds only: package, focus state, decision, saved, cancel outcome and reason. No content is logged.
- **`capture/ListenerDiagnostics.kt` (new):** in-memory ring buffer and counters, shown in **Profile → Notification access → Listener diagnostics**.
- **`QueueRepository.ingest`:** returns `Ingested(id, duplicate)` instead of `null`, and throws if the write fails.
- **`NotificationDecisionEngine`:** in AI Mode with no usable model, only clearly low-value categories are held (social, short video, games, shopping, promotions, entertainment, news). Everything else queued by category becomes REVIEW (kept visible). Rules mode (AI Mode off) is unchanged.

## Phase 1 additions (reliable routing, protection of important messages)

### Lifecycle now

posted → extract (title, text, MessagingStyle fallback, shortcut id, Android priority-conversation flag) → own package / group summary handling → read focus state → **burst count** → evaluate (rules → priority conversation → safety → model) → guard → persist to Room and confirm the row exists → `cancelNotification(sbn.key)` → **wait for Android's removal callback** → mark suppressed → diagnostics.

### What changed

- **Removal is confirmed, not assumed.** `cancelNotification()` is asynchronous and returns nothing. `ListenerOps.awaitRemoved()` waits up to 2.5 s for `onNotificationRemoved` (or finds the key gone from the active list). Only then does the queue item get `wasSuppressed = true` and the outcome become `CONFIRMED`. Otherwise the outcome is `UNCONFIRMED`, `FAILED` or `SKIPPED`, and the queue item is flagged `keptVisible` (it is a copy of something still in the shade). Diagnostics show "not confirmed" separately.
- **Possibly-important content is never held** (`NotificationDecisionEngine.guard`, applied to every outcome that would be HOLD, including your own hold rules; promotions filter rule excluded):
  - urgent/emergency wording, English and Hinglish (`call me now`, `urgent`, `asap`, `jaldi`, `abhi call karo`, `madad`, `chest pain`, `police`, …) → **REVIEW** (visible), not DELIVER, because a word is not proof. Ignored for shopping/promotion categories. This list is Kotlin-engine-only; it is not an ML feature and does not touch Python parity.
  - **burst**: 3 or more messages from one conversation within 2 minutes (counting each post once; repeated callbacks do not count) → REVIEW. Conversation = the app's shortcut id when exposed, else package + title; none → no burst detection.
  - empty or placeholder content ("Sensitive notification content hidden", "New message", "2 new messages") → REVIEW. Absence of content is never treated as a distraction. An explicit user hold rule for an app or category still applies to empty content.
- **Priority conversations (Android 11+):** `Ranking.isConversation && channel.isImportantConversation` is an allow rule. On older Android, apps without conversation shortcuts, or when the ranking can't be read, this is simply false and routing falls back to the normal rules.
- **Model failure is conservative.** If the model throws on a notification, every category is REVIEW. If the model file is missing, only short video, games, shopping, promotions, entertainment and news can be held. **Social was removed from that list** because direct messages arrive in social apps.
- **Links:** the model reads the text with URLs removed, so a link neither raises nor lowers importance. Link-only messages stay REVIEW. The feature extractor and trained weights are unchanged (the model has non-zero weights on the link features; this input change neutralises them without retraining).
- **Sender history** only biases model logits (max ±1.5, sender scope weight 0.5). It is never an allow rule and never overrides safety rules.
- **Group summaries:** unchanged rule (removed only if Attune held a child of that group and no non-summary notification remains). A summary is never queued.
- **Pause / resume / completion:** the focus state is read on every callback. Paused or inactive → nothing is held. A held item that the app later re-posts during focus is removed again; one re-posted after the session ended is left alone.
- **Updated content:** an update with the same key is re-evaluated. Changed text updates the same queue item; unchanged text is a duplicate (no new row, original removed again). If an update becomes important, it is delivered. The earlier queue item remains listed.
- **Failures:** decision failure, database failure or a disconnected listener leave the original visible and are recorded; important notifications (ALLOW) never depend on the database.

### Limits that remain

- Android shows the notification before the listener sees it. A held notification may already have made a sound, vibrated or appeared. Attune removes it afterwards, best-effort.
- Notifications posted while the listener is disconnected (revoked access, process killed by an aggressive battery manager) are never seen.
- Attune cannot restore another app's notification. There is **no release/restore action**. "Open now" opens the original content intent while Attune is running (kept in memory only); after a restart, or if the app withdrew the notification, it only opens the app. A replacement "reminder" notification was **not** implemented in this phase; it would not keep the original's actions, grouping or deep link.
- A conversation notified by an app that re-uses one notification per chat and does not expose a shortcut id is identified by its title.
- Classification is imperfect. REVIEW is the safe fallback, not a guarantee.

## Debug log (debug builds)

```
adb logcat -s AttuneListener
com.instagram.android focus=active decision=HOLD saved=true cancel=CONFIRMED (Held in queue; removal confirmed by Android)
com.whatsapp focus=active decision=REVIEW saved=true cancel=NOT_NEEDED (Uncertain or possibly important: kept visible and listed for review)
```

## Device test checklist

**Device verification: NOT TESTED.** No device or emulator was available; nothing below has been run on hardware.

**Preparation**
1. Install the new debug APK and grant notification access.
2. Open Profile → Notification access → Listener diagnostics → it should say **Connected**.
3. Start a focus session. Diagnostics should show Focus **Active**.

**Distracting notification**
1. From another account, like an Instagram post, or send a promotional email or a long casual WhatsApp message (more than 3 words).
2. Expected in diagnostics: `HOLD · saved yes · cancel confirmed`. If it shows `REVIEW`, the model wasn't confident enough and the cautious policy kept it visible. That is policy behaviour, not a cancellation failure. Note which text did this: it is real-world data the synthetic training set doesn't cover.
3. Expected on the phone: the notification disappears from the shade within about a second. It may still have buzzed or shown briefly; that is an Android limit.
4. Expected in Attune: it appears under Queue → Held.
5. Send a second message in the same chat → the chat's group summary also disappears if no message from that chat is still visible.

**Important notification**
1. Add a priority contact, then have them send any message → `ALLOW`. It stays in the shade and alerts normally. It is not in the queue.
2. Send "Please call me, it's an emergency" from any contact → `ALLOW` (safety rule). Same as above.
3. Receive an OTP SMS → `ALLOW`. It stays visible, and its preview is masked inside Attune.

**Uncertain notification**
- Send "hi" or a bare link → `REVIEW`. It stays in the shade and is also listed under Queue → To review.

**Paused or inactive**
1. Pause the session and send a distracting message → it stays visible. Diagnostics show `focus paused`.
2. End the session and repeat → nothing is processed (`focus inactive`).

**Duplicate and recursion**
1. Have an app update the same notification (e.g. another message in the same chat) → still one queue item, and the original is removed again.
2. When the session ends, Attune's summary notification appears and does **not** show up in diagnostics.

**Listener disconnected**
- Turn notification access off and on during focus → diagnostics briefly show Not connected. No crash. Holding resumes after reconnection.

**Failures**
- If diagnostics show `cancel failed` or `save failures > 0`, note the app and the reason shown. These contain no private content.


## Phase 1 device checklist (all NOT TESTED)

Record Android version, phone model and the messaging apps used. Behaviour differs by OEM.

1. **Access:** enable notification access; Listener diagnostics says *Connected*. Revoke and re-grant: it shows *Not connected* then *Connected*, with no crash.
2. **Distracting:** during focus, a long casual chat message or a social "liked your post" is `HOLD · saved · cancel confirmed`, disappears from the shade and appears under Held. Note whether it buzzed first (expected, Android limit).
3. **Important:** a priority contact, a call, an alarm and an OTP SMS stay in the shade and are not queued.
4. **Priority conversation (Android 11+):** mark a WhatsApp/Messages chat as *Priority* in system settings; its messages are `ALLOW` with reason "priority conversation". Unmark it: normal routing. On an app without conversation shortcuts nothing changes.
5. **Ambiguous:** "ok", "hi", an emoji, a bare link: `REVIEW`, still visible, listed under *To review*.
6. **Urgent wording:** "call me now", "urgent", "jaldi call karo" from a non-priority contact: `REVIEW`, still visible.
7. **Redacted:** turn on *Hide sensitive notification content* on the lock screen and receive a message while locked: `REVIEW`, not held.
8. **Repeated messages:** send 3+ messages in one chat within two minutes: the first ones are held, the third and later are `REVIEW` and stay visible.
9. **Updates / duplicates:** one chat updating a single notification keeps one queue item and the original is removed again each time.
10. **Groups:** several held messages from one app: the group summary disappears only when no unheld notification of that group remains.
11. **Pause / resume / end:** pause (nothing held), resume (held again), end (normal; Attune's own summary is not processed).
12. **Cancellation failure:** hard to force. If diagnostics ever show *cancel failures* or *not confirmed* above 0, note the app/ROM; the queue item is then marked kept-visible.
13. **Queue persistence and expiry:** hold a message, end the session, do not open Attune. After 24 h (or by setting the clock forward) open the app or reconnect the listener: the item shows "content removed". Sensitive items (finance wording) never show text.
14. **Privacy center:** *Remove stored text*, *Delete feedback*, *Delete activity* each clear what they say; learned preferences disappear.
15. **Backup / transfer:** `adb shell bmgr` / a device-to-device transfer test, if possible, to confirm nothing from Attune arrives on the new device.
16. **Upgrade:** install over a previous (v3) build with data: queue and rules survive; sender-level learned preferences are reset (documented).
17. **Battery:** exempt Attune from battery optimisation on aggressive OEMs, otherwise the listener may be killed and notifications missed.

## Phase 2 additions

- **Show now (release):** Queue item -> *Show now (new notification)*. Posts a replacement notification from Attune (`ReleaseNotifier`), sets the item to `RELEASED`. It is **not** the original: no reply/actions/grouping, and it opens the original target only while Attune's PendingIntent cache is alive. Idempotent (queue mutex, open items only, notification id derived from the item id, so a repeat replaces rather than adds). Attune's own notifications are ignored by the listener, so no loop and no new queue row. Refused for REVIEW items whose original is still visible, and when Attune's notifications are blocked. Not unit-tested (needs Android); NOT TESTED on a device.
- **Follow-up escalation:** if Attune held a message from a conversation in the last 5 minutes (memory only, never stored), the next message within 2 minutes is REVIEW instead of HOLD. Three messages in 2 minutes is a burst (REVIEW).
- **Sender levels, links, thresholds:** see `docs/DECISION_POLICY.md`.
- **Diagnostics states:** `Allowed`, `Held - removal confirmed`, `Held - removal not confirmed`, `Cancel failed`, `Listener disconnected` (cancel skipped), `Queue save failed`, `Review - kept visible`. No titles, text, codes or URLs are logged (`diagnosticsDescribeEveryOutcomeWithoutContent`).

### Phase 2 device checklist additions (NOT TESTED)

1. Hold a message, open it in Attune, tap *Show now*: one replacement notification appears, the item moves to Handled as "Shown again", tapping Show now again is impossible, and no new queue item appears.
2. Deny Attune's notification permission, tap *Show now*: a message explains it could not post; the item stays held.
3. Mark a sender *Priority* and *Hold* in Settings -> Senders; check the Queue "To review" vs "Held" behaviour.
4. Send a message containing a phishing-looking link (e.g. `http://hdfc-secure-verify.top/kyc`): REVIEW, the original stays visible, the item shows hostname + "High risk" + reasons. Send a `bit.ly` link: "Caution - hides the destination". Tap nothing: Attune never opens links.
5. Two quick messages from one chat: the second is REVIEW (follow-up); wait 6 minutes: held again.

## Real-time suppression fix (round 3)

### What was found by code trace (confirmed by reading code and by the automated tests, NOT by running on a phone)

The listener path itself was sound: session state is read fresh from Room on every callback (`FocusSessionRepository.getActive()`), the original is cancelled only after the queue row exists, the SBN key is used, and removal is confirmed from Android's callback. The defects that explain "my notifications still show and open from the shade" were:

1. **Sample data was on by default.** `DEMO_MODE_DEFAULT` was `true`, so every fresh install seeded fake queue items, sessions and history and showed them as normal data. The queue could look populated by something other than real notifications. Now `false`; sample data is opt-in (Profile -> Show demo samples), and leftover sample rows are deleted at start while it is off.
2. **Most real notifications were routed to REVIEW, and REVIEW leaves the original untouched and tappable by design.** A confident HOLD needs the model to be sure (top-class confidence >= 0.60, P(high importance) < 0.15). Real notification wording differs from the synthetic training templates, so many distracting notifications from social/video/shopping apps fall below that bar. Now, in clearly low-value categories only (social, short video, games, shopping, promotions, entertainment, news), a model that is merely *unsure* but reads the text as distracting (>= 0.5) with no sign of importance holds it (`DecisionPolicy.holdUnsureLowValue`). Messaging, email, calls and unknown apps are never affected; urgent wording, bursts, priority senders and high-risk links still turn a HOLD into REVIEW.
3. **There was no way to tell which case you were in.** Each callback now gets one privacy-safe state: `LISTENER_DISCONNECTED`, `FOCUS_INACTIVE`, `ALLOWED`, `REVIEW_VISIBLE`, `QUEUE_PERSIST_FAILED`, `HOLD_PERSISTED`, `CANCELLATION_ATTEMPTED` (requested, not confirmed), `CANCELLATION_CONFIRMED`, `CANCELLATION_FAILED`, plus a counter of callbacks received from Android. If "Connected" shows 0 callbacks while other apps post notifications, Android is not delivering them (see Android settings below).

Still true, and not fixable in Kotlin: Android posts the notification before the listener sees it, so it may flash or make a sound first; `cancelNotification` removes it afterwards, and apps may re-post it. Ongoing/non-clearable notifications cannot be removed.

### Verify on your phone (NOT TESTED by me)

1. Settings -> Notifications -> Device & app notifications (name varies) -> **Attune: allowed**. Also check battery: Attune **Unrestricted / not optimised**.
2. Open Attune -> Profile -> Notification access -> Listener diagnostics. It must say **Connected**.
3. Start a focus session. Diagnostics: Focus **Active**.
4. From a second device, trigger a *long, casual* Instagram/YouTube/shopping-app notification. Expect the notification to vanish within ~1 s, diagnostics state `CANCELLATION_CONFIRMED`, and the item under Queue -> Held.
5. Trigger an important one (call, priority contact, "OTP is 123456"): it stays; state `ALLOWED`.
6. Send "hi": it stays; `REVIEW_VISIBLE` (intended).
7. Pause the session, repeat 4: it stays; `FOCUS_INACTIVE`. Resume: held again. End: normal.
8. Turn notification access off then on: `LISTENER_DISCONNECTED` then Connected.
9. If a held-type notification stays visible, read its state: `CANCELLATION_ATTEMPTED/FAILED` = Android or the app refused removal; `REVIEW_VISIBLE` = policy kept it; `HOLD_PERSISTED` = "Hide queued notifications from the shade" is off or the notification is not clearable; callbacks = 0 = access not delivering.

### Optional stronger suppression: Do Not Disturb (assessed, NOT implemented)

Only Do Not Disturb (or the app's own channel settings) can stop the first sound/heads-up, because it acts before delivery. A listener cannot. Using it would need the "Do Not Disturb access" special permission (`ACCESS_NOTIFICATION_POLICY`), which the user must grant in system settings, and a clearly explained, opt-in toggle that sets and then restores the filter around a session. It would also silence calls/alarms unless allowed through DND's own rules. Attune never changes DND by itself; this is left as a separate, explicit feature.
